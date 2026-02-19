package com.statecraft.economy.core;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.data.EconomySavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for the economy system
 * Handles accounts, transactions, and currency
 */
public class EconomyManager {
    private static EconomyManager instance;

    // Bank registry for managing multiple banks
    private final BankRegistry bankRegistry = new BankRegistry();

    // Player accounts: UUID -> balance
    private final Map<UUID, BankAccount> playerAccounts = new ConcurrentHashMap<>();

    // Nation treasuries: nationId -> balance (when StateCraft is loaded)
    private final Map<UUID, BankAccount> nationTreasuries = new ConcurrentHashMap<>();

    // State treasuries: stateId -> balance
    private final Map<UUID, BankAccount> stateTreasuries = new ConcurrentHashMap<>();

    // City treasuries: cityId -> balance
    private final Map<UUID, BankAccount> cityTreasuries = new ConcurrentHashMap<>();

    // Cached currency values: itemId -> value
    private Map<String, Double> currencyValues = new HashMap<>();

    // Transaction history (limited)
    private final Map<UUID, List<Transaction>> transactionHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_PER_PLAYER = 50;

    private boolean dirty = false;

    private EconomyManager() {
        reloadCurrencyConfig();
    }

    public static EconomyManager getInstance() {
        if (instance == null) {
            instance = new EconomyManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Currency Configuration ====================

    public void reloadCurrencyConfig() {
        currencyValues.clear();
        List<? extends String> items = EconomyConfig.CURRENCY_ITEMS.get();

        for (String entry : items) {
            String[] parts = entry.split("=");
            if (parts.length == 2) {
                try {
                    String itemId = parts[0].trim();
                    double value = Double.parseDouble(parts[1].trim());
                    currencyValues.put(itemId, value);
                    StateCraftEconomy.LOGGER.debug("Registered currency: {} = {}", itemId, value);
                } catch (NumberFormatException e) {
                    StateCraftEconomy.LOGGER.warn("Invalid currency config entry: {}", entry);
                }
            }
        }

        StateCraftEconomy.LOGGER.info("Loaded {} currency types", currencyValues.size());
    }

    public double getCurrencyValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return 0;

        Double value = currencyValues.get(itemId.toString());
        return value != null ? value * stack.getCount() : 0;
    }

    public double getCurrencyValue(Item item) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId == null) return 0;

        Double value = currencyValues.get(itemId.toString());
        return value != null ? value : 0;
    }

    public boolean isCurrency(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null && currencyValues.containsKey(itemId.toString());
    }

    public Map<String, Double> getAllCurrencies() {
        return Collections.unmodifiableMap(currencyValues);
    }

    /**
     * Convert a money amount into physical currency items
     * Returns a list of ItemStacks to give to the player
     */
    public List<net.minecraft.world.item.ItemStack> convertToItems(double amount) {
        List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();

        // Sort currency by value descending (highest value first)
        List<Map.Entry<String, Double>> sortedCurrency = new ArrayList<>(currencyValues.entrySet());
        sortedCurrency.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double remaining = amount;

        for (Map.Entry<String, Double> entry : sortedCurrency) {
            if (remaining < entry.getValue()) continue;

            String itemId = entry.getKey();
            double itemValue = entry.getValue();

            // Calculate how many of this item we need
            int count = (int) (remaining / itemValue);
            if (count > 0) {
                // Get the item from registry
                net.minecraft.resources.ResourceLocation resLoc = new net.minecraft.resources.ResourceLocation(itemId);
                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(resLoc);

                if (item != null) {
                    // Split into stacks (max 64 per stack)
                    int totalCount = count;
                    while (totalCount > 0) {
                        int stackSize = Math.min(totalCount, 64);
                        items.add(new net.minecraft.world.item.ItemStack(item, stackSize));
                        totalCount -= stackSize;
                    }

                    // Update remaining amount
                    remaining -= (count * itemValue);
                }
            }
        }

        return items;
    }

    // ==================== Bank Management ====================

    public BankRegistry getBankRegistry() {
        return bankRegistry;
    }

    // ==================== Account Management ====================

    public BankAccount getOrCreateAccount(UUID playerId) {
        return playerAccounts.computeIfAbsent(playerId, id -> {
            double startingBalance = EconomyConfig.STARTING_BALANCE.get();
            BankAccount account = new BankAccount(id, BankAccount.AccountType.PLAYER, startingBalance);
            // Set default bank ID if not already set
            if (account.getBankId() == null) {
                account.setBankId(bankRegistry.getDefaultBank().getId());
            }
            dirty = true;
            return account;
        });
    }

    @Nullable
    public BankAccount getAccount(UUID playerId) {
        return playerAccounts.get(playerId);
    }

    public double getBalance(UUID playerId) {
        BankAccount account = getOrCreateAccount(playerId);
        return account.getBalance();
    }

    public boolean hasAccount(UUID playerId) {
        return playerAccounts.containsKey(playerId);
    }

    // ==================== Transactions ====================

    public TransactionResult deposit(UUID playerId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        BankAccount account = getOrCreateAccount(playerId);

        double maxBalance = EconomyConfig.MAX_BALANCE.get();
        if (maxBalance > 0 && account.getBalance() + amount > maxBalance) {
            return new TransactionResult(false, "Would exceed maximum balance", 0);
        }

        account.add(amount);
        recordTransaction(playerId, Transaction.Type.DEPOSIT, amount, null, description);
        dirty = true;

        return new TransactionResult(true, "Deposited " + formatCurrency(amount), account.getBalance());
    }

    public TransactionResult withdraw(UUID playerId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        BankAccount account = getOrCreateAccount(playerId);

        if (account.getBalance() < amount) {
            return new TransactionResult(false, "Insufficient funds", account.getBalance());
        }

        account.subtract(amount);
        recordTransaction(playerId, Transaction.Type.WITHDRAWAL, amount, null, description);
        dirty = true;

        return new TransactionResult(true, "Withdrew " + formatCurrency(amount), account.getBalance());
    }

    /**
     * Force a withdrawal even if it results in a negative balance.
     * Used for mandatory payments like taxes.
     */
    public TransactionResult forceWithdraw(UUID playerId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        BankAccount account = getOrCreateAccount(playerId);
        account.subtract(amount);
        recordTransaction(playerId, Transaction.Type.WITHDRAWAL, amount, null, "[FORCED] " + description);
        dirty = true;

        return new TransactionResult(true, "Force withdrew " + formatCurrency(amount), account.getBalance());
    }

    // ==================== Inventory Currency Methods ====================

    /**
     * Calculate the total value of currency items in a player's inventory
     * @param player The player whose inventory to check
     * @return The total value of currency items
     */
    public double getInventoryCurrencyValue(net.minecraft.world.entity.player.Player player) {
        double total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            total += getCurrencyValue(stack);
        }
        return total;
    }

    /**
     * Get a player's total available balance (bank account + inventory currency)
     * @param player The player to check
     * @return Total available balance
     */
    public double getTotalAvailableBalance(net.minecraft.world.entity.player.Player player) {
        double bankBalance = getBalance(player.getUUID());
        double inventoryValue = getInventoryCurrencyValue(player);
        return bankBalance + inventoryValue;
    }

    /**
     * Withdraw from a player, using bank first then inventory currency if needed
     * @param player The player to withdraw from
     * @param amount The amount to withdraw
     * @param description Transaction description
     * @return Transaction result
     */
    public TransactionResult withdrawSmart(net.minecraft.world.entity.player.Player player, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        double bankBalance = getBalance(player.getUUID());
        double inventoryValue = getInventoryCurrencyValue(player);
        double totalAvailable = bankBalance + inventoryValue;

        if (totalAvailable < amount) {
            return new TransactionResult(false, "Insufficient funds. Bank: " + formatCurrency(bankBalance) +
                ", Inventory: " + formatCurrency(inventoryValue) + ", Need: " + formatCurrency(amount), bankBalance);
        }

        double remainingToWithdraw = amount;

        // First, take from bank
        if (bankBalance > 0) {
            double fromBank = Math.min(bankBalance, remainingToWithdraw);
            BankAccount account = getOrCreateAccount(player.getUUID());
            account.subtract(fromBank);
            remainingToWithdraw -= fromBank;
            dirty = true;
        }

        // If still need more, take from inventory
        if (remainingToWithdraw > 0.001) { // Small epsilon for floating point
            remainingToWithdraw = withdrawCurrencyFromInventory(player, remainingToWithdraw);
        }

        // Record the transaction
        recordTransaction(player.getUUID(), Transaction.Type.WITHDRAWAL, amount, null, description);

        double newBankBalance = getBalance(player.getUUID());
        return new TransactionResult(true, "Withdrew " + formatCurrency(amount), newBankBalance);
    }

    /**
     * Remove currency items from player inventory up to specified value
     * @param player The player
     * @param amount The value to remove
     * @return The remaining amount that couldn't be removed (0 if successful)
     */
    private double withdrawCurrencyFromInventory(net.minecraft.world.entity.player.Player player, double amount) {
        double remaining = amount;

        // Sort by value descending - prefer removing higher value bills first
        List<Map.Entry<String, Double>> sortedCurrency = new ArrayList<>(currencyValues.entrySet());
        sortedCurrency.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0.001; i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            net.minecraft.resources.ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId == null) continue;

            Double itemValue = currencyValues.get(itemId.toString());
            if (itemValue == null || itemValue <= 0) continue;

            // Calculate how many items we need to remove
            int itemsToRemove = (int) Math.ceil(remaining / itemValue);
            itemsToRemove = Math.min(itemsToRemove, stack.getCount());

            if (itemsToRemove > 0) {
                double valueRemoved = itemsToRemove * itemValue;
                stack.shrink(itemsToRemove);
                remaining -= valueRemoved;

                // If we removed too much, we need to give change
                if (remaining < 0) {
                    double change = -remaining;
                    // Deposit the change to their bank account
                    BankAccount account = getOrCreateAccount(player.getUUID());
                    account.add(change);
                    dirty = true;
                    remaining = 0;
                }
            }
        }

        return Math.max(0, remaining);
    }

    public TransactionResult transfer(UUID fromId, UUID toId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        if (fromId.equals(toId)) {
            return new TransactionResult(false, "Cannot transfer to yourself", 0);
        }

        BankAccount fromAccount = getOrCreateAccount(fromId);
        BankAccount toAccount = getOrCreateAccount(toId);

        // Calculate fee
        double feePercent = EconomyConfig.TRANSFER_FEE_PERCENT.get();
        double fee = amount * feePercent;
        double totalDeducted = amount + fee;

        if (fromAccount.getBalance() < totalDeducted) {
            return new TransactionResult(false, "Insufficient funds (including " + formatCurrency(fee) + " fee)", fromAccount.getBalance());
        }

        double maxBalance = EconomyConfig.MAX_BALANCE.get();
        if (maxBalance > 0 && toAccount.getBalance() + amount > maxBalance) {
            return new TransactionResult(false, "Recipient would exceed maximum balance", 0);
        }

        fromAccount.subtract(totalDeducted);
        toAccount.add(amount);

        recordTransaction(fromId, Transaction.Type.TRANSFER_OUT, amount, toId, description);
        recordTransaction(toId, Transaction.Type.TRANSFER_IN, amount, fromId, description);

        if (fee > 0) {
            recordTransaction(fromId, Transaction.Type.FEE, fee, null, "Transfer fee");
        }

        dirty = true;

        String message = fee > 0
            ? "Transferred " + formatCurrency(amount) + " (fee: " + formatCurrency(fee) + ")"
            : "Transferred " + formatCurrency(amount);
        return new TransactionResult(true, message, fromAccount.getBalance());
    }

    // ==================== Nation Treasury (StateCraft Integration) ====================

    public BankAccount getOrCreateNationTreasury(UUID nationId) {
        return nationTreasuries.computeIfAbsent(nationId, id -> {
            BankAccount account = new BankAccount(id, BankAccount.AccountType.NATION, 0);
            // Set default bank ID if not already set
            if (account.getBankId() == null) {
                account.setBankId(bankRegistry.getDefaultBank().getId());
            }
            dirty = true;
            return account;
        });
    }

    public double getNationBalance(UUID nationId) {
        BankAccount treasury = getOrCreateNationTreasury(nationId);
        return treasury.getBalance();
    }

    /**
     * Alias for getNationBalance for clarity
     */
    public double getNationTreasuryBalance(UUID nationId) {
        return getNationBalance(nationId);
    }

    /**
     * Withdraw directly from nation treasury (no player involved)
     * Used for government contract payments, etc.
     */
    public TransactionResult withdrawFromNationTreasury(UUID nationId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        BankAccount nationTreasury = getOrCreateNationTreasury(nationId);
        if (nationTreasury.getBalance() < amount) {
            return new TransactionResult(false, "Insufficient funds in nation treasury", nationTreasury.getBalance());
        }

        nationTreasury.subtract(amount);
        dirty = true;

        return new TransactionResult(true, "Withdrew " + formatCurrency(amount) + " from nation treasury", nationTreasury.getBalance());
    }

    /**
     * Deposit directly to nation treasury (no player involved)
     * Used for tax collection, contract payments, etc.
     */
    public TransactionResult depositToNationTreasury(UUID nationId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        BankAccount nationTreasury = getOrCreateNationTreasury(nationId);
        nationTreasury.add(amount);
        dirty = true;

        return new TransactionResult(true, "Deposited " + formatCurrency(amount) + " to nation treasury", nationTreasury.getBalance());
    }

    /**
     * Get balance for any government entity (state or city)
     * @param type "state" or "city"
     * @param entityId The UUID of the state or city
     * @return The balance, or 0 if not found
     */
    public double getGovernmentBalance(String type, UUID entityId) {
        if ("state".equalsIgnoreCase(type)) {
            BankAccount treasury = getOrCreateStateTreasury(entityId);
            return treasury.getBalance();
        } else if ("city".equalsIgnoreCase(type)) {
            BankAccount treasury = getOrCreateCityTreasury(entityId);
            return treasury.getBalance();
        }
        return 0;
    }

    /**
     * Get city balance
     */
    public double getCityBalance(UUID cityId) {
        BankAccount treasury = getOrCreateCityTreasury(cityId);
        return treasury.getBalance();
    }

    public TransactionResult depositToNation(UUID nationId, UUID playerId, double amount, String description) {
        if (amount <= 0) {
            return new TransactionResult(false, "Amount must be positive", 0);
        }

        BankAccount playerAccount = getOrCreateAccount(playerId);
        if (playerAccount.getBalance() < amount) {
            return new TransactionResult(false, "Insufficient funds", playerAccount.getBalance());
        }

        BankAccount nationTreasury = getOrCreateNationTreasury(nationId);

        playerAccount.subtract(amount);
        nationTreasury.add(amount);

        recordTransaction(playerId, Transaction.Type.NATION_DEPOSIT, amount, nationId, description);
        dirty = true;

        return new TransactionResult(true, "Deposited " + formatCurrency(amount) + " to nation treasury", playerAccount.getBalance());
    }

    public TransactionResult withdrawFromNation(UUID nationId, UUID playerId, double amount, String description) {
        BankAccount nationTreasury = nationTreasuries.get(nationId);
        if (nationTreasury == null || nationTreasury.getBalance() < amount) {
            return new TransactionResult(false, "Insufficient funds in nation treasury", 0);
        }

        BankAccount playerAccount = getOrCreateAccount(playerId);

        nationTreasury.subtract(amount);
        playerAccount.add(amount);

        recordTransaction(playerId, Transaction.Type.NATION_WITHDRAWAL, amount, nationId, description);
        dirty = true;

        return new TransactionResult(true, "Withdrew " + formatCurrency(amount) + " from nation treasury", nationTreasury.getBalance());
    }

    // ==================== State Treasury ====================

    public BankAccount getOrCreateStateTreasury(UUID stateId) {
        return stateTreasuries.computeIfAbsent(stateId, id -> {
            BankAccount account = new BankAccount(id, BankAccount.AccountType.STATE, 0);
            // Set default bank ID if not already set
            if (account.getBankId() == null) {
                account.setBankId(bankRegistry.getDefaultBank().getId());
            }
            dirty = true;
            return account;
        });
    }

    public double getStateBalance(UUID stateId) {
        BankAccount treasury = getOrCreateStateTreasury(stateId);
        return treasury.getBalance();
    }

    // ==================== City Treasury ====================

    public BankAccount getOrCreateCityTreasury(UUID cityId) {
        return cityTreasuries.computeIfAbsent(cityId, id -> {
            BankAccount account = new BankAccount(id, BankAccount.AccountType.CITY, 0);
            // Set default bank ID if not already set
            if (account.getBankId() == null) {
                account.setBankId(bankRegistry.getDefaultBank().getId());
            }
            dirty = true;
            return account;
        });
    }

    // ==================== Transaction History ====================

    private void recordTransaction(UUID playerId, Transaction.Type type, double amount, UUID otherId, String description) {
        List<Transaction> history = transactionHistory.computeIfAbsent(playerId, k -> new ArrayList<>());

        Transaction tx = new Transaction(type, amount, otherId, description, System.currentTimeMillis());
        history.add(0, tx); // Add at beginning

        // Trim history
        while (history.size() > MAX_HISTORY_PER_PLAYER) {
            history.remove(history.size() - 1);
        }
    }

    public List<Transaction> getTransactionHistory(UUID playerId) {
        return transactionHistory.getOrDefault(playerId, Collections.emptyList());
    }

    // ==================== Utilities ====================

    public String formatCurrency(double amount) {
        if (amount >= 1000000) {
            return String.format("$%.2fM", amount / 1000000);
        } else if (amount >= 1000) {
            return String.format("$%.2fK", amount / 1000);
        } else {
            return String.format("$%.2f", amount);
        }
    }

    // ==================== Persistence ====================

    public void loadFromData(EconomySavedData data) {
        playerAccounts.clear();
        nationTreasuries.clear();
        stateTreasuries.clear();
        cityTreasuries.clear();
        transactionHistory.clear();

        playerAccounts.putAll(data.getPlayerAccounts());
        nationTreasuries.putAll(data.getNationTreasuries());
        stateTreasuries.putAll(data.getStateTreasuries());
        cityTreasuries.putAll(data.getCityTreasuries());

        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded {} player accounts, {} nation treasuries, {} state treasuries, {} city treasuries",
            playerAccounts.size(), nationTreasuries.size(), stateTreasuries.size(), cityTreasuries.size());
    }

    public Map<UUID, BankAccount> getPlayerAccounts() {
        return playerAccounts;
    }

    public Map<UUID, BankAccount> getNationTreasuries() {
        return nationTreasuries;
    }

    public Map<UUID, BankAccount> getStateTreasuries() {
        return stateTreasuries;
    }

    public Map<UUID, BankAccount> getCityTreasuries() {
        return cityTreasuries;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }
}

