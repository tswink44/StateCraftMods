package com.statecraft.economy.block.entity;

import com.statecraft.economy.config.ItemValueConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.gui.TradingHubMenu;
import com.statecraft.economy.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.statecraft.economy.StateCraftEconomy;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Block entity for Trading Hub - handles inventory and selling logic
 * Features:
 * - Chest-like inventory (27 slots) for bulk selling
 * - Configurable profit destination (ATM deposit or currency items)
 * - Profit splitting between multiple players
 * - Owner-locked settings (only owner and admins can change)
 */
public class TradingHubBlockEntity extends BlockEntity implements MenuProvider {

    // Chest-like inventory (27 slots, same as single chest)
    public static final int INVENTORY_SIZE = 27;

    private final ItemStackHandler itemHandler = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // On client side, always accept - the server will validate.
            // Client may not have the item values JSON in multiplayer.
            if (level != null && level.isClientSide) return true;
            // Only accept items that have a sell value
            return ItemValueConfig.canSell(stack);
        }
    };

    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    // Owner tracking
    private UUID ownerUUID;
    private String ownerName = "";
    private UUID cityId;

    // Settings
    private boolean depositToATM = true; // true = deposit to bank, false = leave as currency items
    private List<ProfitShare> profitShares = new ArrayList<>(); // Profit splitting configuration

    // Statistics tracking
    private long totalSold = 0;
    private double totalEarnings = 0;

    /**
     * Represents a profit share entry
     */
    public static class ProfitShare {
        public final UUID playerUUID;  // Player UUID or Company UUID
        public String playerName;      // Player name or Company name
        public double percentage;      // 0.0 to 1.0
        public final boolean isCompany; // true if this share goes to a company treasury

        public ProfitShare(UUID playerUUID, String playerName, double percentage) {
            this(playerUUID, playerName, percentage, false);
        }

        public ProfitShare(UUID playerUUID, String playerName, double percentage, boolean isCompany) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.percentage = Math.max(0.0, Math.min(1.0, percentage));
            this.isCompany = isCompany;
        }

        public CompoundTag toNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", playerUUID);
            NBTUtils.putSanitizedString(tag, "name", playerName);
            tag.putDouble("percentage", percentage);
            tag.putBoolean("isCompany", isCompany);
            return tag;
        }

        public static ProfitShare fromNBT(CompoundTag tag) {
            UUID uuid = tag.getUUID("uuid");
            String name = tag.getString("name");
            double percentage = tag.getDouble("percentage");
            boolean isCompany = tag.getBoolean("isCompany");
            return new ProfitShare(uuid, name, percentage, isCompany);
        }
    }

    public TradingHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADING_HUB.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Trading Hub");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TradingHubMenu(containerId, playerInventory, this);
    }

    /**
     * Set the owner of this trading hub
     */
    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
        this.ownerName = player.getName().getString();

        // Resolve the city this block is placed in
        if (level != null && !level.isClientSide) {
            this.cityId = resolveCityId();
        }

        // Default profit share: 100% to owner
        profitShares.clear();
        profitShares.add(new ProfitShare(ownerUUID, ownerName, 1.0));

        setChanged();
    }

    /**
     * Resolve the city ID for this block's position via StateCraft integration.
     */
    private UUID resolveCityId() {
        if (level == null) return null;
        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            ChunkPos chunkPos = new ChunkPos(worldPosition);
            var getChunk = managerClass.getMethod("getClaimedChunk",
                ChunkPos.class, level.dimension().getClass());
            Object chunk = getChunk.invoke(manager, chunkPos, level.dimension());
            if (chunk != null) {
                return (UUID) chunk.getClass().getMethod("getCityId").invoke(chunk);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not resolve city for trading hub block: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if this block is placed in a claimed city chunk.
     */
    public boolean isInCity() {
        return cityId != null;
    }

    /**
     * Check if a player can modify settings
     */
    public boolean canModifySettings(Player player) {
        if (ownerUUID == null) return true; // Unclaimed, anyone can claim
        if (player.getUUID().equals(ownerUUID)) return true; // Owner
        if (player.hasPermissions(2)) return true; // Server admin (op level 2+)
        return false;
    }

    /**
     * Check if a player is the owner
     */
    public boolean isOwner(Player player) {
        return ownerUUID != null && player.getUUID().equals(ownerUUID);
    }

    /**
     * Get the item handler for the inventory
     */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * Get all items in the inventory
     */
    public List<ItemStack> getInventoryContents() {
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    /**
     * Calculate the total value of all items in the inventory
     */
    public double calculateTotalSellValue() {
        double total = 0;
        boolean isClient = level != null && level.isClientSide;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                // On client side, the item value registry may not be loaded (dedicated server).
                // Still try to get the value — it will work for the host but may return 0 for remote clients.
                if (isClient || ItemValueConfig.canSell(stack)) {
                    double value = ItemValueConfig.getStackValue(stack);
                    total += value;
                }
            }
        }

        // Apply base tax rate from config
        double taxRate = ItemValueConfig.SELL_TAX_RATE.get();
        return total * (1.0 - taxRate);
    }

    /**
     * Calculate the total tax amount for all items
     * Uses the full sales tax calculation including city/state/nation taxes
     */
    public double calculateTotalTaxAmount() {
        double grossValue = 0;
        boolean isClient = level != null && level.isClientSide;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (isClient || ItemValueConfig.canSell(stack)) {
                    grossValue += ItemValueConfig.getStackValue(stack);
                }
            }
        }

        if (grossValue <= 0) return 0;

        // Use the same tax calculation as sellAllItems for accurate preview
        SalesTaxInfo taxInfo = calculateSalesTax(grossValue);
        return taxInfo.totalTax;
    }

    /**
     * Count total sellable items in inventory
     */
    public int countSellableItems() {
        int count = 0;
        boolean isClient = level != null && level.isClientSide;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (isClient || ItemValueConfig.canSell(stack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /**
     * Sell all items in the inventory
     * @param player The player initiating the sale
     * @return The total amount earned, or -1 if sale failed
     */
    public double sellAllItems(Player player) {
        // Calculate gross value (before tax)
        double grossValue = 0;
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty() && ItemValueConfig.canSell(stack)) {
                grossValue += ItemValueConfig.getStackValue(stack);
            }
        }

        if (grossValue <= 0) return -1;

        // Look up the city where this trading hub is located
        SalesTaxInfo taxInfo = calculateSalesTax(grossValue);

        double taxAmount = taxInfo.totalTax;
        double netValue = grossValue - taxAmount;

        int itemsSold = 0;

        // Remove all sellable items
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty() && ItemValueConfig.canSell(stack)) {
                itemsSold += stack.getCount();
                itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        if (itemsSold == 0) return -1;

        // Distribute the tax to city/state/nation treasuries
        distributeSalesTax(taxInfo);

        // Distribute profit according to shares (sends mail notifications)
        distributeProfit(grossValue, taxInfo, netValue, itemsSold, player);

        // Track statistics
        totalSold += itemsSold;
        totalEarnings += netValue;

        setChanged();
        return netValue;
    }

    /**
     * Information about sales tax calculation and distribution
     */
    private static class SalesTaxInfo {
        double totalTax;
        double taxRate;
        double cityShare;
        double stateShare;
        double nationShare;
        UUID cityId;
        UUID stateId;
        UUID nationId;
        String cityName = "Unknown";
        String stateName = "Unknown";
        String nationName = "Unknown";

        // For sanity check reporting when rates exceed 100%
        boolean ratesWereCapped = false;
        double requestedNationRate = 0;
        double requestedStateRate = 0;
        double requestedCityRate = 0;
        double effectiveNationRate = 0;
        double effectiveStateRate = 0;
        double effectiveCityRate = 0;
    }

    /**
     * Calculate sales tax based on independent rates at city, state, and nation levels.
     * Each level takes their own percentage from the gross sale value.
     *
     * SANITY CHECK: If total tax rates exceed 100%, priority order is:
     * 1. Nation Sales Tax (deducted first)
     * 2. State Sales Tax (deducted second)
     * 3. City Sales Tax (deducted last, may be reduced or zero if cap reached)
     *
     * Uses reflection to access StateCraft classes since they're in a different module.
     */
    private SalesTaxInfo calculateSalesTax(double grossValue) {
        SalesTaxInfo info = new SalesTaxInfo();

        // Default to config-based tax if no city found
        double defaultTaxRate = ItemValueConfig.SELL_TAX_RATE.get();
        info.taxRate = defaultTaxRate;
        info.totalTax = grossValue * defaultTaxRate;
        info.cityShare = info.totalTax;
        info.stateShare = 0;
        info.nationShare = 0;

        if (level == null || worldPosition == null) {
            return info;
        }

        try {
            // Use reflection to access StateCraft classes
            Class<?> managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);

            // Find the city this trading hub is in
            ChunkPos chunkPos = new ChunkPos(worldPosition);
            Object chunk = managerClass.getMethod("getClaimedChunk", ChunkPos.class, level.dimension().getClass())
                .invoke(manager, chunkPos, level.dimension());

            if (chunk == null) {
                // Not in claimed territory - use default tax rate, no distribution
                return info;
            }

            // Get city from chunk
            UUID cityId = (UUID) chunk.getClass().getMethod("getCityId").invoke(chunk);
            Object city = managerClass.getMethod("getCity", UUID.class).invoke(manager, cityId);
            if (city == null) {
                return info;
            }

            info.cityId = cityId;
            info.cityName = (String) city.getClass().getMethod("getName").invoke(city);

            // Get raw tax rates from each level
            double cityTaxRate = (double) city.getClass().getMethod("getSalesTaxRate").invoke(city);
            double stateTaxRate = 0;
            double nationTaxRate = 0;

            // Get state info
            UUID stateId = (UUID) city.getClass().getMethod("getStateId").invoke(city);
            Object state = managerClass.getMethod("getState", UUID.class).invoke(manager, stateId);
            if (state != null) {
                info.stateId = stateId;
                info.stateName = (String) state.getClass().getMethod("getName").invoke(state);
                stateTaxRate = (double) state.getClass().getMethod("getSalesTaxRate").invoke(state);

                // Get nation info
                UUID nationId = (UUID) state.getClass().getMethod("getNationId").invoke(state);
                Object nation = managerClass.getMethod("getNation", UUID.class).invoke(manager, nationId);
                if (nation != null) {
                    info.nationId = nationId;
                    info.nationName = (String) nation.getClass().getMethod("getName").invoke(nation);
                    nationTaxRate = (double) nation.getClass().getMethod("getSalesTaxRate").invoke(nation);
                }
            }

            // SANITY CHECK: Apply priority ordering if total exceeds 100%
            // Priority: Nation > State > City
            double remainingTaxCapacity = 1.0; // 100% maximum

            // 1. Nation gets their full rate (up to remaining capacity)
            double effectiveNationRate = Math.min(nationTaxRate, remainingTaxCapacity);
            remainingTaxCapacity -= effectiveNationRate;

            // 2. State gets their rate (up to remaining capacity)
            double effectiveStateRate = Math.min(stateTaxRate, remainingTaxCapacity);
            remainingTaxCapacity -= effectiveStateRate;

            // 3. City gets whatever is left (up to remaining capacity)
            double effectiveCityRate = Math.min(cityTaxRate, remainingTaxCapacity);

            // Log if rates were capped
            double totalRequestedRate = nationTaxRate + stateTaxRate + cityTaxRate;
            if (totalRequestedRate > 1.0) {
                com.statecraft.economy.StateCraftEconomy.LOGGER.info(
                    "Sales tax rates exceeded 100% (total: {:.1f}%). Applied priority: Nation {:.1f}% -> State {:.1f}% -> City {:.1f}%",
                    totalRequestedRate * 100, effectiveNationRate * 100, effectiveStateRate * 100, effectiveCityRate * 100);

                // Store original requested rates for reporting
                info.requestedNationRate = nationTaxRate;
                info.requestedStateRate = stateTaxRate;
                info.requestedCityRate = cityTaxRate;
                info.ratesWereCapped = true;
            }

            // Calculate actual tax amounts using effective rates
            info.nationShare = grossValue * effectiveNationRate;
            info.stateShare = grossValue * effectiveStateRate;
            info.cityShare = grossValue * effectiveCityRate;

            // Calculate total tax and effective combined rate
            info.totalTax = info.nationShare + info.stateShare + info.cityShare;
            info.taxRate = (effectiveNationRate + effectiveStateRate + effectiveCityRate);

            // Store effective rates for reporting
            info.effectiveNationRate = effectiveNationRate;
            info.effectiveStateRate = effectiveStateRate;
            info.effectiveCityRate = effectiveCityRate;

        } catch (Exception e) {
            com.statecraft.economy.StateCraftEconomy.LOGGER.warn("Error calculating sales tax: {}", e.getMessage());
        }

        return info;
    }

    /**
     * Distribute sales tax to city, state, and nation treasuries
     */
    private void distributeSalesTax(SalesTaxInfo taxInfo) {
        if (taxInfo.totalTax <= 0) return;

        EconomyManager econ = EconomyManager.getInstance();

        // Deposit to city treasury
        if (taxInfo.cityId != null && taxInfo.cityShare > 0) {
            econ.getOrCreateCityTreasury(taxInfo.cityId).add(taxInfo.cityShare);
            com.statecraft.economy.StateCraftEconomy.LOGGER.debug(
                "Sales tax: ${:.2f} to city {}", taxInfo.cityShare, taxInfo.cityName);
        }

        // Deposit to state treasury
        if (taxInfo.stateId != null && taxInfo.stateShare > 0) {
            econ.getOrCreateStateTreasury(taxInfo.stateId).add(taxInfo.stateShare);
            com.statecraft.economy.StateCraftEconomy.LOGGER.debug(
                "Sales tax pass-through: ${:.2f} to state {}", taxInfo.stateShare, taxInfo.stateName);
        }

        // Deposit to nation treasury
        if (taxInfo.nationId != null && taxInfo.nationShare > 0) {
            econ.getOrCreateNationTreasury(taxInfo.nationId).add(taxInfo.nationShare);
            com.statecraft.economy.StateCraftEconomy.LOGGER.debug(
                "Sales tax pass-through: ${:.2f} to nation {}", taxInfo.nationShare, taxInfo.nationName);
        }

        econ.markDirty();

        // Send tax reports to government officials
        sendGovernmentTaxReports(taxInfo);
    }

    /**
     * Send tax collection reports to government officials (mayors, governors, nation leaders)
     */
    private void sendGovernmentTaxReports(SalesTaxInfo taxInfo) {
        String locationStr = String.format("(%d, %d, %d)", worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

        try {
            // Use reflection to access StateCraft classes
            Class<?> managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            Object mailManager = mailManagerClass.getMethod("getInstance").invoke(null);
            Class<?> mailTypeClass = Class.forName("com.statecraft.mail.Mail$MailType");
            Object taxMailType = Enum.valueOf((Class<Enum>) mailTypeClass, "TAX_REPORT");

            // Build base tax report content
            StringBuilder baseReport = new StringBuilder();
            baseReport.append("=== Sales Tax Collection Report ===\n\n");
            baseReport.append("Location: ").append(locationStr).append("\n");
            baseReport.append(String.format("Total Tax Collected: $%.2f\n\n", taxInfo.totalTax));

            if (taxInfo.ratesWereCapped) {
                baseReport.append("*** TAX RATE ADJUSTMENT APPLIED ***\n");
                baseReport.append(String.format("Combined rates exceeded 100%% (requested: %.1f%%).\n",
                    (taxInfo.requestedNationRate + taxInfo.requestedStateRate + taxInfo.requestedCityRate) * 100));
                baseReport.append("Priority applied: Nation > State > City.\n\n");
            }

            // Send report to city mayor
            if (taxInfo.cityId != null && taxInfo.cityShare > 0) {
                Object city = managerClass.getMethod("getCity", UUID.class).invoke(manager, taxInfo.cityId);
                if (city != null) {
                    UUID mayorId = (UUID) city.getClass().getMethod("getMayorId").invoke(city);
                    if (mayorId != null) {
                        StringBuilder cityReport = new StringBuilder(baseReport);
                        cityReport.append("=== City Tax Revenue ===\n");
                        cityReport.append(String.format("City: %s\n", taxInfo.cityName));
                        cityReport.append(String.format("Tax Rate: %.1f%%", taxInfo.effectiveCityRate * 100));
                        if (taxInfo.ratesWereCapped && taxInfo.effectiveCityRate < taxInfo.requestedCityRate) {
                            cityReport.append(String.format(" (requested: %.1f%%, reduced due to rate cap)",
                                taxInfo.requestedCityRate * 100));
                        }
                        cityReport.append("\n");
                        cityReport.append(String.format("Revenue Collected: $%.2f\n", taxInfo.cityShare));
                        cityReport.append("\nFunds have been deposited to the city treasury.");

                        mailManagerClass.getMethod("sendSystemMail", UUID.class, mailTypeClass, String.class, String.class)
                            .invoke(mailManager, mayorId, taxMailType, "Sales Tax Revenue Report - " + taxInfo.cityName, cityReport.toString());
                    }
                }
            }

            // Send report to state governor
            if (taxInfo.stateId != null && taxInfo.stateShare > 0) {
                Object state = managerClass.getMethod("getState", UUID.class).invoke(manager, taxInfo.stateId);
                if (state != null) {
                    UUID governorId = (UUID) state.getClass().getMethod("getGovernorId").invoke(state);
                    if (governorId != null) {
                        StringBuilder stateReport = new StringBuilder(baseReport);
                        stateReport.append("=== State Tax Revenue ===\n");
                        stateReport.append(String.format("State: %s\n", taxInfo.stateName));
                        stateReport.append(String.format("Tax Rate: %.1f%%", taxInfo.effectiveStateRate * 100));
                        if (taxInfo.ratesWereCapped && taxInfo.effectiveStateRate < taxInfo.requestedStateRate) {
                            stateReport.append(String.format(" (requested: %.1f%%, reduced due to rate cap)",
                                taxInfo.requestedStateRate * 100));
                        }
                        stateReport.append("\n");
                        stateReport.append(String.format("Revenue Collected: $%.2f\n", taxInfo.stateShare));
                        stateReport.append(String.format("\nSale occurred in: %s (City)\n", taxInfo.cityName));
                        stateReport.append("\nFunds have been deposited to the state treasury.");

                        mailManagerClass.getMethod("sendSystemMail", UUID.class, mailTypeClass, String.class, String.class)
                            .invoke(mailManager, governorId, taxMailType, "Sales Tax Revenue Report - " + taxInfo.stateName, stateReport.toString());
                    }
                }
            }

            // Send report to nation leader
            if (taxInfo.nationId != null && taxInfo.nationShare > 0) {
                Object nation = managerClass.getMethod("getNation", UUID.class).invoke(manager, taxInfo.nationId);
                if (nation != null) {
                    UUID leaderId = (UUID) nation.getClass().getMethod("getLeaderId").invoke(nation);
                    if (leaderId != null) {
                        StringBuilder nationReport = new StringBuilder(baseReport);
                        nationReport.append("=== Nation Tax Revenue ===\n");
                        nationReport.append(String.format("Nation: %s\n", taxInfo.nationName));
                        nationReport.append(String.format("Tax Rate: %.1f%%", taxInfo.effectiveNationRate * 100));
                        if (taxInfo.ratesWereCapped && taxInfo.effectiveNationRate < taxInfo.requestedNationRate) {
                            nationReport.append(String.format(" (requested: %.1f%%, reduced due to rate cap)",
                                taxInfo.requestedNationRate * 100));
                        }
                        nationReport.append("\n");
                        nationReport.append(String.format("Revenue Collected: $%.2f\n", taxInfo.nationShare));
                        nationReport.append(String.format("\nSale occurred in: %s (State) > %s (City)\n",
                            taxInfo.stateName, taxInfo.cityName));
                        nationReport.append("\nFunds have been deposited to the nation treasury.");

                        mailManagerClass.getMethod("sendSystemMail", UUID.class, mailTypeClass, String.class, String.class)
                            .invoke(mailManager, leaderId, taxMailType, "Sales Tax Revenue Report - " + taxInfo.nationName, nationReport.toString());
                    }
                }
            }

        } catch (Exception e) {
            // Mail system or StateCraft might not be available, log but don't fail
            com.statecraft.economy.StateCraftEconomy.LOGGER.warn("Failed to send government tax reports: {}", e.getMessage());
        }
    }

    /**
     * Distribute profit according to configured shares and send mail notifications
     */
    private void distributeProfit(double grossValue, SalesTaxInfo taxInfo, double netValue, int itemsSold, Player sellingPlayer) {
        String sellerName = sellingPlayer.getName().getString();
        String locationStr = String.format("(%d, %d, %d)", worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

        if (profitShares.isEmpty()) {
            // No shares configured, give all to selling player
            if (depositToATM) {
                EconomyManager.getInstance().deposit(sellingPlayer.getUUID(), netValue, "Trading Hub sale");
            } else {
                giveCurrencyItems(sellingPlayer, netValue);
            }

            // Send mail to seller
            sendSaleMail(sellingPlayer.getUUID(), sellerName, grossValue, taxInfo, netValue,
                100.0, netValue, itemsSold, locationStr);
            return;
        }

        // Normalize percentages (in case they don't sum to 1.0)
        double totalPercentage = profitShares.stream().mapToDouble(s -> s.percentage).sum();
        if (totalPercentage <= 0) totalPercentage = 1.0;

        for (ProfitShare share : profitShares) {
            double sharePercentage = (share.percentage / totalPercentage) * 100.0;
            double shareValue = netValue * (share.percentage / totalPercentage);
            if (shareValue <= 0) continue;

            if (share.isCompany) {
                // Company share — deposit to company treasury
                com.statecraft.economy.core.EconomyManager.TransactionResult result =
                    com.statecraft.economy.core.EconomyManager.getInstance().depositToCompanyTreasury(
                        share.playerUUID, shareValue,
                        "Trading Hub profit share (" + (int)sharePercentage + "%)");
                com.statecraft.economy.StateCraftEconomy.LOGGER.info(
                    "Trading Hub company deposit: {} -> {} (${}) success={}",
                    share.playerName, share.playerUUID, String.format("%.2f", shareValue), result.isSuccess());
            } else if (depositToATM) {
                EconomyManager.getInstance().deposit(share.playerUUID, shareValue,
                    "Trading Hub profit share (" + (int)sharePercentage + "%)");
            } else {
                // For non-ATM mode, only give items to the selling player
                // Other shares will be deposited to their ATM regardless
                if (share.playerUUID.equals(sellingPlayer.getUUID())) {
                    giveCurrencyItems(sellingPlayer, shareValue);
                } else {
                    // Can't give items to offline players, deposit to ATM
                    EconomyManager.getInstance().deposit(share.playerUUID, shareValue,
                        "Trading Hub profit share (" + (int)sharePercentage + "%)");
                }
            }

            // Send mail notification to this share recipient (skip for companies)
            if (!share.isCompany) {
                sendSaleMail(share.playerUUID, share.playerName, grossValue, taxInfo, netValue,
                    sharePercentage, shareValue, itemsSold, locationStr);
            }
        }
    }

    /**
     * Send sale confirmation mail to a profit share recipient
     */
    private void sendSaleMail(UUID recipientId, String recipientName, double grossValue, SalesTaxInfo taxInfo,
                               double netValue, double sharePercentage, double shareAmount,
                               int itemsSold, String location) {
        String subject = "Trading Hub Sale Confirmation";

        StringBuilder body = new StringBuilder();
        body.append("A sale has been completed at your Trading Hub.\n\n");
        body.append("Location: ").append(location).append("\n");
        body.append("Items Sold: ").append(itemsSold).append("\n\n");
        body.append("=== Sale Breakdown ===\n");
        body.append(String.format("Gross Value: $%.2f\n", grossValue));
        body.append(String.format("Sales Tax: $%.2f (%.1f%%)\n", taxInfo.totalTax, taxInfo.taxRate * 100));

        // Show tax distribution breakdown
        if (taxInfo.cityId != null) {
            if (taxInfo.nationShare > 0) {
                body.append(String.format("  - %s (Nation): $%.2f", taxInfo.nationName, taxInfo.nationShare));
                if (taxInfo.ratesWereCapped && taxInfo.effectiveNationRate < taxInfo.requestedNationRate) {
                    body.append(String.format(" [capped from %.1f%% to %.1f%%]",
                        taxInfo.requestedNationRate * 100, taxInfo.effectiveNationRate * 100));
                }
                body.append("\n");
            }
            if (taxInfo.stateShare > 0) {
                body.append(String.format("  - %s (State): $%.2f", taxInfo.stateName, taxInfo.stateShare));
                if (taxInfo.ratesWereCapped && taxInfo.effectiveStateRate < taxInfo.requestedStateRate) {
                    body.append(String.format(" [capped from %.1f%% to %.1f%%]",
                        taxInfo.requestedStateRate * 100, taxInfo.effectiveStateRate * 100));
                }
                body.append("\n");
            }
            body.append(String.format("  - %s (City): $%.2f", taxInfo.cityName, taxInfo.cityShare));
            if (taxInfo.ratesWereCapped && taxInfo.effectiveCityRate < taxInfo.requestedCityRate) {
                body.append(String.format(" [capped from %.1f%% to %.1f%%]",
                    taxInfo.requestedCityRate * 100, taxInfo.effectiveCityRate * 100));
            }
            body.append("\n");
        }

        // Warn about rate capping if it occurred
        if (taxInfo.ratesWereCapped) {
            body.append("\n*** TAX RATE ADJUSTMENT NOTICE ***\n");
            body.append(String.format("Combined tax rates exceeded 100%% (requested: %.1f%%).\n",
                (taxInfo.requestedNationRate + taxInfo.requestedStateRate + taxInfo.requestedCityRate) * 100));
            body.append("Tax collection was prioritized: Nation > State > City.\n");
        }

        body.append(String.format("\nNet Value: $%.2f\n\n", netValue));
        body.append("=== Your Share ===\n");
        body.append(String.format("Share Percentage: %.1f%%\n", sharePercentage));
        body.append(String.format("Amount Deposited: $%.2f\n", shareAmount));

        if (depositToATM) {
            body.append("\nFunds have been deposited to your bank account.");
        } else {
            body.append("\nFunds were provided as currency items.");
        }

        try {
            // Use reflection to access MailManager from StateCraft
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            Object mailManager = mailManagerClass.getMethod("getInstance").invoke(null);

            // Get the MailType enum value
            Class<?> mailTypeClass = Class.forName("com.statecraft.mail.Mail$MailType");
            Object mailType = Enum.valueOf((Class<Enum>) mailTypeClass, "TRADING_HUB_SALE");

            mailManagerClass.getMethod("sendSystemMail", UUID.class, mailTypeClass, String.class, String.class)
                .invoke(mailManager, recipientId, mailType, subject, body.toString());
        } catch (Exception e) {
            // Mail system might not be available, log but don't fail the sale
            com.statecraft.economy.StateCraftEconomy.LOGGER.warn("Failed to send trading hub sale mail to {}: {}",
                recipientName, e.getMessage());
        }
    }

    /**
     * Give currency items to a player
     */
    private void giveCurrencyItems(Player player, double amount) {
        List<ItemStack> items = EconomyManager.getInstance().convertToItems(amount);
        for (ItemStack item : items) {
            if (!player.getInventory().add(item)) {
                // Drop items if inventory is full
                player.drop(item, false);
            }
        }
    }

    /**
     * Drop all contents when block is broken
     */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // Settings getters and setters
    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public boolean isDepositToATM() { return depositToATM; }
    public List<ProfitShare> getProfitShares() { return new ArrayList<>(profitShares); }

    public void setDepositToATM(boolean depositToATM) {
        this.depositToATM = depositToATM;
        markDirtyAndSync();
    }

    public void setProfitShares(List<ProfitShare> shares) {
        this.profitShares = new ArrayList<>(shares);
        markDirtyAndSync();
    }

    /**
     * Mark the block entity as changed and ensure data is saved
     */
    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            // Mark the chunk as needing save
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void addProfitShare(UUID playerUUID, String playerName, double percentage) {
        // Remove existing share for this player if any
        profitShares.removeIf(s -> s.playerUUID.equals(playerUUID));
        profitShares.add(new ProfitShare(playerUUID, playerName, percentage));
        setChanged();
    }

    public void removeProfitShare(UUID playerUUID) {
        profitShares.removeIf(s -> s.playerUUID.equals(playerUUID));
        setChanged();
    }

    // Capability handling
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    // Network sync for client
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    // NBT Serialization
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", itemHandler.serializeNBT());
        tag.putLong("totalSold", totalSold);
        tag.putDouble("totalEarnings", totalEarnings);

        // Save owner
        if (ownerUUID != null) {
            tag.putUUID("owner", ownerUUID);
            NBTUtils.putSanitizedString(tag, "ownerName", ownerName);
        }
        if (cityId != null) {
            tag.putUUID("cityId", cityId);
        }

        // Save settings
        tag.putBoolean("depositToATM", depositToATM);

        // Save profit shares
        ListTag sharesList = new ListTag();
        for (ProfitShare share : profitShares) {
            sharesList.add(share.toNBT());
        }
        tag.put("profitShares", sharesList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("inventory")) {
            CompoundTag inventoryTag = tag.getCompound("inventory");

            // Check if we need to migrate from old format (1 slot) to new format (27 slots)
            int savedSize = inventoryTag.contains("Size") ? inventoryTag.getInt("Size") : 1;

            if (savedSize < INVENTORY_SIZE) {
                // Old format - migrate items to new larger inventory
                // First load into a temporary handler
                ItemStackHandler tempHandler = new ItemStackHandler(savedSize);
                tempHandler.deserializeNBT(inventoryTag);

                // Copy items from temp handler to our proper sized handler
                for (int i = 0; i < savedSize && i < INVENTORY_SIZE; i++) {
                    itemHandler.setStackInSlot(i, tempHandler.getStackInSlot(i));
                }
            } else {
                // New format or same size - load normally
                itemHandler.deserializeNBT(inventoryTag);
            }
        }
        totalSold = tag.getLong("totalSold");
        totalEarnings = tag.getDouble("totalEarnings");

        // Load owner
        if (tag.hasUUID("owner")) {
            ownerUUID = tag.getUUID("owner");
            ownerName = tag.getString("ownerName");
        }
        if (tag.contains("cityId")) {
            cityId = tag.getUUID("cityId");
        }

        // Load settings
        depositToATM = tag.getBoolean("depositToATM");
        if (!tag.contains("depositToATM")) {
            depositToATM = true; // Default
        }

        // Load profit shares
        profitShares.clear();
        if (tag.contains("profitShares")) {
            ListTag sharesList = tag.getList("profitShares", Tag.TAG_COMPOUND);
            for (int i = 0; i < sharesList.size(); i++) {
                profitShares.add(ProfitShare.fromNBT(sharesList.getCompound(i)));
            }
        }
    }

    // Statistics getters
    public long getTotalSold() { return totalSold; }
    public double getTotalEarnings() { return totalEarnings; }
}

