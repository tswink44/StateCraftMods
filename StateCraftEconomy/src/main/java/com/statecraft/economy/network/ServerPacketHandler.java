package com.statecraft.economy.network;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.gui.ATMMenu;
import com.statecraft.economy.integration.StateCraftIntegration;
import com.statecraft.economy.item.ModItems;
import com.statecraft.economy.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Handles packets received on the server
 */
public class ServerPacketHandler {

    public static void handleDeposit(DepositPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Deposit is now handled via ATMTransactionPacket with inventory scanning
            // This old packet handler is kept for backward compatibility but does nothing
            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                "Please use the deposit button in the ATM menu",
                EconomyManager.getInstance().getBalance(player.getUUID())), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleWithdraw(WithdrawPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            EconomyManager manager = EconomyManager.getInstance();
            double amount = packet.getAmount();
            String currencyType = packet.getCurrencyType();

            // Get the item to give
            Item coinItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(currencyType));
            if (coinItem == null) {
                coinItem = ModItems.BILL_100.get(); // Default to $100 bills
            }

            double coinValue = manager.getCurrencyValue(coinItem);
            if (coinValue <= 0) {
                NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                    "Invalid currency type", manager.getBalance(player.getUUID())), player);
                return;
            }

            // Calculate how many coins to give
            int coinCount = (int) (amount / coinValue);
            if (coinCount <= 0) {
                NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                    "Amount too small for this currency", manager.getBalance(player.getUUID())), player);
                return;
            }

            double actualAmount = coinCount * coinValue;

            // Try to withdraw
            TransactionResult result = manager.withdraw(player.getUUID(), actualAmount, "ATM withdrawal");

            if (result.isSuccess()) {
                // Give coins to player
                while (coinCount > 0) {
                    int stackSize = Math.min(coinCount, 64);
                    ItemStack coins = new ItemStack(coinItem, stackSize);
                    if (!player.getInventory().add(coins)) {
                        player.drop(coins, false);
                    }
                    coinCount -= stackSize;
                }
            }

            NetworkHandler.sendToPlayer(new TransactionResultPacket(result.isSuccess(),
                result.getMessage(), result.getNewBalance()), player);
            NetworkHandler.sendToPlayer(new SyncBalancePacket(result.getNewBalance(), getNationBalance(player)), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleTransfer(TransferPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            EconomyManager manager = EconomyManager.getInstance();
            String targetName = packet.getTargetPlayer();
            double amount = packet.getAmount();

            // Find target player UUID
            ServerPlayer targetPlayer = player.server.getPlayerList().getPlayerByName(targetName);
            UUID targetId = null;

            if (targetPlayer != null) {
                targetId = targetPlayer.getUUID();
            } else {
                // Try to find from cache
                var profile = player.server.getProfileCache().get(targetName);
                if (profile.isPresent()) {
                    targetId = profile.get().getId();
                }
            }

            if (targetId == null) {
                NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                    "Player not found: " + targetName, manager.getBalance(player.getUUID())), player);
                return;
            }

            TransactionResult result = manager.transfer(player.getUUID(), targetId, amount,
                "Transfer to " + targetName);

            NetworkHandler.sendToPlayer(new TransactionResultPacket(result.isSuccess(),
                result.getMessage(), result.getNewBalance()), player);
            NetworkHandler.sendToPlayer(new SyncBalancePacket(result.getNewBalance(), getNationBalance(player)), player);

            // Notify recipient if online
            if (targetPlayer != null && result.isSuccess()) {
                double targetBalance = manager.getBalance(targetId);
                NetworkHandler.sendToPlayer(new TransactionResultPacket(true,
                    "Received " + manager.formatCurrency(amount) + " from " + player.getName().getString(),
                    targetBalance), targetPlayer);
                NetworkHandler.sendToPlayer(new SyncBalancePacket(targetBalance, getNationBalance(targetPlayer)), targetPlayer);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestBalance(RequestBalancePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            double balance = EconomyManager.getInstance().getBalance(player.getUUID());
            NetworkHandler.sendToPlayer(new SyncBalancePacket(balance, getNationBalance(player)), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestAccounts(RequestAccountsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            EconomyManager manager = EconomyManager.getInstance();
            java.util.List<SyncAccountsPacket.AccountInfo> accounts = new java.util.ArrayList<>();

            // Always add personal account
            accounts.add(new SyncAccountsPacket.AccountInfo(
                "PERSONAL",
                player.getName().getString(),
                player.getUUID().toString(),
                manager.getBalance(player.getUUID())
            ));

            // Check StateCraft integration for government accounts
            try {
                if (StateCraftEconomy.isStateCraftLoaded()) {
                    var govAccounts = StateCraftIntegration.getPlayerAdminAccounts(player);
                    accounts.addAll(govAccounts);
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error getting government accounts for {}: {}", player.getName().getString(), e.getMessage());
            }

            // Add company accounts the player can manage (founder or officer)
            try {
                var companyManager = com.statecraft.company.CompanyManager.getInstance();
                for (var company : companyManager.getPlayerManagedCompanies(player.getUUID())) {
                    double companyBalance = manager.getCompanyBalance(company.getId());
                    accounts.add(new SyncAccountsPacket.AccountInfo(
                        "COMPANY",
                        company.getName(),
                        company.getId().toString(),
                        companyBalance
                    ));
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error getting company accounts for {}: {}", player.getName().getString(), e.getMessage());
            }

            // Add bank deposit accounts (banks where the player is a member)
            try {
                var companyManager = com.statecraft.company.CompanyManager.getInstance();
                var bankManager = com.statecraft.economy.company.BankManager.getInstance();
                for (var bank : bankManager.getPlayerBanks(player.getUUID())) {
                    var company = companyManager.getCompany(bank.getCompanyId());
                    String bankName = company != null ? company.getName() : "Bank";
                    double depositorBalance = bank.getDepositorBalance(player.getUUID());
                    accounts.add(new SyncAccountsPacket.AccountInfo(
                        "BANK_DEPOSIT",
                        bankName,
                        bank.getCompanyId().toString(),
                        depositorBalance
                    ));
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error getting bank deposit accounts for {}: {}", player.getName().getString(), e.getMessage());
            }

            // Build bank list for ATM bank selection dropdown
            java.util.List<SyncAccountsPacket.BankInfo> bankList = new java.util.ArrayList<>();
            try {
                for (var bank : manager.getBankRegistry().getAllBanks()) {
                    bankList.add(new SyncAccountsPacket.BankInfo(
                        bank.getId().toString(),
                        bank.getName(),
                        bank.getDisplayName(),
                        bank.getInterestRate(),
                        bank.getWithdrawalFee(),
                        bank.getTransferFee(),
                        bank.allowsLoans(),
                        bank.getColor()
                    ));
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error building bank list: {}", e.getMessage());
            }

            NetworkHandler.sendToPlayer(new SyncAccountsPacket(accounts, bankList), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleNationTreasury(NationTreasuryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!StateCraftEconomy.isStateCraftLoaded()) {
                NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                    "StateCraft mod not loaded", EconomyManager.getInstance().getBalance(player.getUUID())), player);
                return;
            }

            StateCraftIntegration.handleNationTreasury(player, packet.getAction(), packet.getAmount());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleRequestTransferRecipients(RequestTransferRecipientsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            java.util.List<SyncTransferRecipientsPacket.RecipientInfo> recipients = new java.util.ArrayList<>();
            String typeName = packet.getRecipientType().name();

            switch (packet.getRecipientType()) {
                case PLAYER -> {
                    // Get all players that have ever logged in (from user cache)
                    var profileCache = player.server.getProfileCache();
                    // Get all online players
                    for (ServerPlayer onlinePlayer : player.server.getPlayerList().getPlayers()) {
                        if (!onlinePlayer.getUUID().equals(player.getUUID())) {
                            recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(
                                onlinePlayer.getName().getString(),
                                onlinePlayer.getUUID().toString()
                            ));
                        }
                    }
                    // Also include offline players from usercache
                    try {
                        // Use reflection to access the profile cache entries
                        var usercacheFile = new java.io.File(player.server.getServerDirectory(), "usercache.json");
                        if (usercacheFile.exists()) {
                            String content = java.nio.file.Files.readString(usercacheFile.toPath());
                            var json = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
                            for (var element : json) {
                                var obj = element.getAsJsonObject();
                                String name = obj.get("name").getAsString();
                                String uuid = obj.get("uuid").getAsString();
                                // Don't add duplicates or self
                                if (!uuid.equals(player.getUUID().toString())) {
                                    boolean alreadyAdded = recipients.stream()
                                        .anyMatch(r -> r.id().equals(uuid));
                                    if (!alreadyAdded) {
                                        recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(name, uuid));
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        StateCraftEconomy.LOGGER.debug("Error reading usercache: {}", e.getMessage());
                    }
                }
                case NATION -> {
                    // Get all nations from StateCraft
                    if (StateCraftEconomy.isStateCraftLoaded()) {
                        var nations = StateCraftIntegration.getAllNations();
                        for (var nation : nations) {
                            recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(
                                nation.name(), nation.id()
                            ));
                        }
                    }
                }
                case STATE -> {
                    // Get all states from StateCraft
                    if (StateCraftEconomy.isStateCraftLoaded()) {
                        var states = StateCraftIntegration.getAllStates();
                        for (var state : states) {
                            recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(
                                state.name(), state.id()
                            ));
                        }
                    }
                }
                case CITY -> {
                    // Get all cities from StateCraft
                    if (StateCraftEconomy.isStateCraftLoaded()) {
                        var cities = StateCraftIntegration.getAllCities();
                        for (var city : cities) {
                            recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(
                                city.name(), city.id()
                            ));
                        }
                    }
                }
                case COMPANY -> {
                    // Get all companies
                    try {
                        var companyManager = com.statecraft.company.CompanyManager.getInstance();
                        for (var company : companyManager.getAllCompanies()) {
                            recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(
                                company.getName(), company.getId().toString()
                            ));
                        }
                    } catch (Exception e) {
                        StateCraftEconomy.LOGGER.debug("Error getting companies for transfer: {}", e.getMessage());
                    }
                }
                case BANK_DEPOSIT -> {
                    // Get banks where the player is a member (can deposit)
                    try {
                        var companyManager = com.statecraft.company.CompanyManager.getInstance();
                        var bankManager = com.statecraft.economy.company.BankManager.getInstance();
                        for (var bank : bankManager.getPlayerBanks(player.getUUID())) {
                            var company = companyManager.getCompany(bank.getCompanyId());
                            String bankName = company != null ? company.getName() : "Bank";
                            recipients.add(new SyncTransferRecipientsPacket.RecipientInfo(
                                bankName, bank.getCompanyId().toString()
                            ));
                        }
                    } catch (Exception e) {
                        StateCraftEconomy.LOGGER.debug("Error getting bank accounts for transfer: {}", e.getMessage());
                    }
                }
            }

            NetworkHandler.sendToPlayer(new SyncTransferRecipientsPacket(typeName, recipients), player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleChunkMarket(ChunkMarketPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            int chunkX = packet.getChunkX();
            int chunkZ = packet.getChunkZ();

            switch (packet.getAction()) {
                case REQUEST_INFO -> sendChunkMarketInfo(player, chunkX, chunkZ);
                case LIST_FOR_SALE -> {
                    var result = com.statecraft.economy.core.ChunkMarketManager.getInstance()
                        .listChunkForSale(player, chunkX, chunkZ, packet.getPrice());
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(result.success(),
                        result.message(), EconomyManager.getInstance().getBalance(player.getUUID())), player);
                    if (result.success()) {
                        sendChunkMarketInfo(player, chunkX, chunkZ);
                    }
                }
                case REMOVE_FROM_SALE -> {
                    var result = com.statecraft.economy.core.ChunkMarketManager.getInstance()
                        .removeChunkFromSale(player, chunkX, chunkZ);
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(result.success(),
                        result.message(), EconomyManager.getInstance().getBalance(player.getUUID())), player);
                    if (result.success()) {
                        sendChunkMarketInfo(player, chunkX, chunkZ);
                    }
                }
                case PURCHASE -> {
                    var result = com.statecraft.economy.core.ChunkMarketManager.getInstance()
                        .purchaseChunk(player, chunkX, chunkZ);
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(result.success(),
                        result.message(), EconomyManager.getInstance().getBalance(player.getUUID())), player);
                    if (result.success()) {
                        sendChunkMarketInfo(player, chunkX, chunkZ);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void sendChunkMarketInfo(ServerPlayer player, int chunkX, int chunkZ) {
        if (!StateCraftEconomy.isStateCraftLoaded()) {
            NetworkHandler.sendToPlayer(new SyncChunkMarketInfoPacket(chunkX, chunkZ), player);
            return;
        }

        var chunkInfo = StateCraftIntegration.getChunkInfo(chunkX, chunkZ, player.level().dimension());
        if (chunkInfo == null) {
            // Unclaimed chunk
            NetworkHandler.sendToPlayer(new SyncChunkMarketInfoPacket(chunkX, chunkZ), player);
            return;
        }

        // Get names for display
        String sellerName = "";
        String ownerName = "";
        String cityName = "";

        if (chunkInfo.sellerId() != null) {
            var profile = player.server.getProfileCache().get(chunkInfo.sellerId());
            sellerName = profile.map(p -> p.getName()).orElse("Unknown");
        }

        if (chunkInfo.isPrivatelyOwned() && chunkInfo.ownerId() != null) {
            var profile = player.server.getProfileCache().get(chunkInfo.ownerId());
            ownerName = profile.map(p -> p.getName()).orElse("Unknown");
        }

        // Get city name
        cityName = StateCraftIntegration.getCityName(chunkInfo.cityId());

        // Check permissions
        boolean canListForSale = false;
        boolean canBuy = false;
        UUID playerId = player.getUUID();

        if (chunkInfo.isPrivatelyOwned()) {
            // Private chunk - owner can list, others can buy if for sale
            canListForSale = playerId.equals(chunkInfo.ownerId());
            canBuy = chunkInfo.isForSale() && !playerId.equals(chunkInfo.ownerId());
        } else {
            // Government chunk - officials can list, anyone can buy if for sale
            canListForSale = StateCraftIntegration.canManageChunk(player, chunkX, chunkZ);
            canBuy = chunkInfo.isForSale() && !playerId.equals(chunkInfo.sellerId());
        }

        // Get actual chunk valuation from the valuation system
        String dimension = player.level().dimension().location().toString();
        com.statecraft.economy.valuation.ChunkValuation chunkValuation =
            com.statecraft.economy.valuation.ChunkValuationManager.getInstance()
                .getValuation(chunkX, chunkZ, dimension);
        double valuation = chunkValuation.getTotalValue();

        // Calculate estimated tax using actual valuation
        double estimatedTax = 0;
        double cityTaxRate = StateCraftIntegration.getChunkCityTaxRate(player.getServer(), chunkX, chunkZ, dimension);
        if (cityTaxRate > 0 && valuation > 0) {
            // Tax = valuation * rate (rate is stored as percentage, e.g., 5 = 5%)
            estimatedTax = valuation * (cityTaxRate / 100.0);
        }

        NetworkHandler.sendToPlayer(new SyncChunkMarketInfoPacket(
            chunkX, chunkZ,
            true, // isClaimed
            chunkInfo.isForSale(),
            chunkInfo.salePrice(),
            sellerName,
            ownerName,
            cityName,
            chunkInfo.isPrivatelyOwned(),
            canListForSale,
            canBuy,
            valuation,
            estimatedTax
        ), player);
    }

    private static double getNationBalance(ServerPlayer player) {
        if (!StateCraftEconomy.isStateCraftLoaded()) {
            return -1;
        }
        return StateCraftIntegration.getNationBalance(player);
    }

    public static void handleRequestChunkValuation(RequestChunkValuationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            int chunkX = packet.getChunkX();
            int chunkZ = packet.getChunkZ();
            String dimension = player.level().dimension().location().toString();

            // Get valuation from manager
            com.statecraft.economy.valuation.ChunkValuation valuation =
                com.statecraft.economy.valuation.ChunkValuationManager.getInstance()
                    .getValuation(chunkX, chunkZ, dimension);

            // Get city tax rate for this chunk
            double cityTaxRate = StateCraftIntegration.getChunkCityTaxRate(
                player.getServer(), chunkX, chunkZ, dimension);

            // Send to client
            NetworkHandler.sendToPlayer(new SyncChunkValuationPacket(
                chunkX, chunkZ, dimension,
                valuation.getBaseValue(),
                valuation.getLocationMultiplier(),
                valuation.getDistanceFromSpawn(),
                valuation.getBiomeMultiplier(),
                valuation.getBiomeName(),
                valuation.getDemandMultiplier(),
                valuation.getNearbyClaims(),
                valuation.getGovernmentMultiplier(),
                valuation.getImprovementMultiplier(),
                valuation.getImprovementScore(),
                valuation.getTotalValue(),
                cityTaxRate
            ), player);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle request for account activity/transaction history
     */
    public static void handleRequestAccountActivity(RequestAccountActivityPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            EconomyManager manager = EconomyManager.getInstance();
            String accountType = packet.getAccountType();
            String accountId = packet.getAccountId();
            UUID accountUUID;

            try {
                accountUUID = UUID.fromString(accountId);
            } catch (IllegalArgumentException e) {
                return; // Invalid UUID, ignore
            }

            // Permission check: players can always view their own account
            // For government accounts, verify they have admin access
            String accountName = "";

            switch (accountType) {
                case "PERSONAL" -> {
                    if (!accountUUID.equals(player.getUUID())) {
                        return; // Can't view other player's activity
                    }
                    accountName = player.getName().getString();
                }
                case "NATION", "STATE", "CITY" -> {
                    if (!StateCraftEconomy.isStateCraftLoaded()) return;

                    // Verify player has admin access to this government account
                    var govAccounts = StateCraftIntegration.getPlayerAdminAccounts(player);
                    boolean hasAccess = govAccounts.stream()
                        .anyMatch(a -> a.type().equals(accountType) && a.id().equals(accountId));

                    if (!hasAccess) {
                        // Also allow members to view (read-only) - check membership
                        boolean isMember = StateCraftIntegration.isPlayerMemberOfEntity(player, accountType, accountUUID);
                        if (!isMember) {
                            return; // No access
                        }
                    }

                    // Get account name
                    accountName = govAccounts.stream()
                        .filter(a -> a.type().equals(accountType) && a.id().equals(accountId))
                        .map(SyncAccountsPacket.AccountInfo::name)
                        .findFirst()
                        .orElse(StateCraftIntegration.getEntityName(accountType, accountUUID));
                }
                case "COMPANY" -> {
                    // Verify player is founder or officer of the company
                    try {
                        var companyManager = com.statecraft.company.CompanyManager.getInstance();
                        var company = companyManager.getCompany(accountUUID);
                        if (company == null) return;
                        if (!company.canManage(player.getUUID())) return; // Only officers/founder can view
                        accountName = company.getName();
                    } catch (Exception e) {
                        return;
                    }
                }
                case "BANK_DEPOSIT" -> {
                    // Verify player is a member of this bank
                    try {
                        var bankMgr = com.statecraft.economy.company.BankManager.getInstance();
                        var bank = bankMgr.getBank(accountUUID);
                        if (bank == null || !bank.isMember(player.getUUID())) return;
                        var companyManager = com.statecraft.company.CompanyManager.getInstance();
                        var company = companyManager.getCompany(accountUUID);
                        accountName = company != null ? company.getName() : "Bank";
                    } catch (Exception e) {
                        return;
                    }
                }
                default -> {
                    return; // Unknown account type
                }
            }

            // Get transaction history for this account
            java.util.List<com.statecraft.economy.core.Transaction> transactions = manager.getTransactionHistory(accountUUID);

            // Get current account balance to compute running balances.
            // Transactions are newest-first, so we start from the current balance
            // and work backwards: each entry's running balance is the balance AFTER
            // that transaction occurred.
            double currentBalance;
            switch (accountType) {
                case "PERSONAL" -> currentBalance = manager.getBalance(accountUUID);
                case "NATION" -> currentBalance = manager.getNationTreasuryBalance(accountUUID);
                case "STATE" -> currentBalance = manager.getGovernmentBalance("state", accountUUID);
                case "CITY" -> currentBalance = manager.getGovernmentBalance("city", accountUUID);
                case "COMPANY" -> currentBalance = manager.getCompanyBalance(accountUUID);
                case "BANK_DEPOSIT" -> {
                    var bank = com.statecraft.economy.company.BankManager.getInstance().getBank(accountUUID);
                    currentBalance = bank != null ? bank.getDepositorBalance(player.getUUID()) : 0;
                }
                default -> currentBalance = 0;
            }

            // Convert to activity entries with running balance
            java.util.List<SyncAccountActivityPacket.ActivityEntry> entries = new java.util.ArrayList<>();
            boolean isGovAccount = !"PERSONAL".equals(accountType);

            // Track the running balance as we walk newest-to-oldest
            double runningBal = currentBalance;

            for (com.statecraft.economy.core.Transaction tx : transactions) {
                String initiatorName = tx.getInitiatorName() != null ? tx.getInitiatorName() : "";

                // For personal accounts, if no explicit initiator, it was the player themselves
                if (initiatorName.isEmpty() && "PERSONAL".equals(accountType)) {
                    initiatorName = player.getName().getString();
                }

                // Determine if this transaction is incoming (positive) or outgoing (negative)
                // For government accounts, TAX and DEPOSIT are incoming revenue;
                // TRANSFER_OUT, WITHDRAWAL are outgoing.
                boolean incoming;
                if (isGovAccount) {
                    incoming = switch (tx.getType()) {
                        case TAX, DEPOSIT, TRANSFER_IN, SALE, IMPORT_TARIFF -> true;
                        case WITHDRAWAL, TRANSFER_OUT, FEE, PURCHASE, NATION_DEPOSIT, MARKETPLACE_PURCHASE -> false;
                        default -> tx.isIncoming();
                    };
                } else {
                    incoming = tx.isIncoming();
                }

                // The running balance at this point is the balance AFTER this transaction
                double balanceAfter = runningBal;

                entries.add(new SyncAccountActivityPacket.ActivityEntry(
                    tx.getType().name(),
                    tx.getAmount(),
                    tx.getDescription(),
                    tx.getTimestamp(),
                    initiatorName,
                    incoming,
                    balanceAfter
                ));

                // Walk backwards: undo this transaction to get the balance before it
                if (incoming) {
                    runningBal -= tx.getAmount();
                } else {
                    runningBal += tx.getAmount();
                }
            }

            NetworkHandler.sendToPlayer(new SyncAccountActivityPacket(accountType, accountName, accountId, entries), player);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle request for marketplace listings
     */
    public static void handleRequestMarketListings(RequestMarketListingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.statecraft.economy.network.packets.MarketplaceActionPacket.sendListingsToPlayer(
                player, packet.getSearchQuery(), packet.isMyListingsOnly());
        });
        ctx.get().setPacketHandled(true);
    }

    // ==================== Stock Market Handlers ====================

    /**
     * Handle request for stock market listings
     */
    public static void handleRequestStockListings(RequestStockListingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.statecraft.economy.stockmarket.StockMarketManager stockManager =
                com.statecraft.economy.stockmarket.StockMarketManager.getInstance();
            com.statecraft.company.CompanyManager companyManager =
                com.statecraft.company.CompanyManager.getInstance();

            UUID playerId = player.getUUID();

            // Get listings based on filter
            java.util.List<com.statecraft.economy.stockmarket.ShareListing> listings;
            if (packet.isMyListingsOnly()) {
                listings = stockManager.getAllListingsForSeller(playerId);
            } else {
                // Filter by company name if provided
                String filter = packet.getCompanyFilter();
                java.util.List<com.statecraft.economy.stockmarket.ShareListing> all = stockManager.getActiveListings(null);
                if (filter != null && !filter.isEmpty()) {
                    String lowerFilter = filter.toLowerCase();
                    listings = all.stream()
                        .filter(l -> l.getCompanyName().toLowerCase().contains(lowerFilter))
                        .collect(java.util.stream.Collectors.toList());
                } else {
                    listings = all;
                }
            }

            // Convert to packet entries
            java.util.List<SyncStockListingsPacket.ListingEntry> entries = new java.util.ArrayList<>();
            for (com.statecraft.economy.stockmarket.ShareListing listing : listings) {
                entries.add(new SyncStockListingsPacket.ListingEntry(
                    listing.getId().toString(),
                    listing.getSellerName(),
                    listing.getCompanyName(),
                    listing.getCompanyId().toString(),
                    listing.getQuantity(),
                    listing.getPricePerShare(),
                    listing.getListedTime(),
                    listing.getStatus().name(),
                    listing.getSellerId().equals(playerId)
                ));
            }

            // Build player's share info for the Sell tab
            java.util.List<SyncStockListingsPacket.CompanyShareInfo> playerShares = new java.util.ArrayList<>();
            for (com.statecraft.company.Company company : companyManager.getAllCompanies()) {
                int owned = company.getShareCount(playerId);
                if (owned > 0) {
                    int listed = stockManager.getActiveListingsForSeller(playerId).stream()
                        .filter(l -> l.getCompanyId().equals(company.getId()))
                        .mapToInt(com.statecraft.economy.stockmarket.ShareListing::getQuantity)
                        .sum();
                    playerShares.add(new SyncStockListingsPacket.CompanyShareInfo(
                        company.getId().toString(),
                        company.getName(),
                        owned,
                        company.getTotalShares(),
                        listed
                    ));
                }
            }

            NetworkHandler.sendToPlayer(
                new SyncStockListingsPacket(entries, packet.isMyListingsOnly(), playerShares),
                player);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle stock market actions (buy, sell, cancel)
     */
    public static void handleStockMarketAction(StockMarketActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.statecraft.economy.stockmarket.StockMarketManager stockManager =
                com.statecraft.economy.stockmarket.StockMarketManager.getInstance();
            com.statecraft.company.CompanyManager companyManager =
                com.statecraft.company.CompanyManager.getInstance();
            String result;

            switch (packet.getAction()) {
                case BUY -> {
                    UUID listingId = UUID.fromString(packet.getListingId());
                    result = stockManager.purchaseShares(
                        listingId, player.getUUID(), player.getName().getString(),
                        packet.getQuantity(), player.server);
                }
                case SELL -> {
                    UUID companyId = UUID.fromString(packet.getCompanyId());
                    com.statecraft.economy.stockmarket.ShareListing listing = stockManager.createListing(
                        player.getUUID(), player.getName().getString(),
                        companyId, packet.getQuantity(), packet.getPrice());
                    if (listing != null) {
                        result = "§aListed " + packet.getQuantity() + " shares at $" +
                                 String.format("%.2f", packet.getPrice()) + " per share.";
                    } else {
                        result = "§cFailed to create listing. Check you own enough unlisted shares.";
                    }
                }
                case CANCEL -> {
                    UUID listingId = UUID.fromString(packet.getListingId());
                    result = stockManager.cancelListing(listingId, player.getUUID());
                }
                default -> result = "§cUnknown action.";
            }

            // Send result message
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result));

            // Refresh listings for the player (inline instead of nested handler call)
            UUID playerId = player.getUUID();
            java.util.List<com.statecraft.economy.stockmarket.ShareListing> listings = stockManager.getActiveListings(null);
            java.util.List<SyncStockListingsPacket.ListingEntry> entries = new java.util.ArrayList<>();
            for (com.statecraft.economy.stockmarket.ShareListing l : listings) {
                entries.add(new SyncStockListingsPacket.ListingEntry(
                    l.getId().toString(), l.getSellerName(), l.getCompanyName(),
                    l.getCompanyId().toString(), l.getQuantity(), l.getPricePerShare(),
                    l.getListedTime(), l.getStatus().name(), l.getSellerId().equals(playerId)));
            }

            java.util.List<SyncStockListingsPacket.CompanyShareInfo> playerShares = new java.util.ArrayList<>();
            for (com.statecraft.company.Company company : companyManager.getAllCompanies()) {
                int owned = company.getShareCount(playerId);
                if (owned > 0) {
                    int listed = stockManager.getActiveListingsForSeller(playerId).stream()
                        .filter(l -> l.getCompanyId().equals(company.getId()))
                        .mapToInt(com.statecraft.economy.stockmarket.ShareListing::getQuantity)
                        .sum();
                    playerShares.add(new SyncStockListingsPacket.CompanyShareInfo(
                        company.getId().toString(), company.getName(),
                        owned, company.getTotalShares(), listed));
                }
            }

            NetworkHandler.sendToPlayer(
                new SyncStockListingsPacket(entries, false, playerShares), player);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Handle request for tax report data
     */
    public static void handleRequestTaxReport(RequestTaxReportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            UUID playerId = player.getUUID();
            EconomyManager ecoManager = EconomyManager.getInstance();
            com.statecraft.economy.core.TaxationManager taxManager = com.statecraft.economy.core.TaxationManager.getInstance();

            if (!StateCraftIntegration.isInitialized()) {
                // Send empty report
                NetworkHandler.sendToPlayer(new SyncTaxReportPacket(
                    new SyncTaxReportPacket.TaxReportData(0, 0, 0, 0, 0, "N/A", "N/A", java.util.List.of())
                ), player);
                return;
            }

            // Collect all chunk data for this player
            java.util.List<com.statecraft.economy.core.TaxationManager.ChunkTaxInfo> allTaxable =
                StateCraftIntegration.getAllTaxableChunks(player.getServer());
            com.statecraft.economy.valuation.ChunkValuationManager valuationManager =
                com.statecraft.economy.valuation.ChunkValuationManager.getInstance();

            // Group chunks by nation -> state -> city
            java.util.Map<String, NationTaxBuilder> nationBuilders = new java.util.LinkedHashMap<>();
            int totalChunks = 0;
            double grandTotalValue = 0;
            double grandTotalTax = 0;

            for (com.statecraft.economy.core.TaxationManager.ChunkTaxInfo chunk : allTaxable) {
                if (!playerId.equals(chunk.getOwnerId())) continue;

                totalChunks++;

                // Get valuation details
                com.statecraft.economy.valuation.ChunkValuation valuation = valuationManager.getValuation(
                    chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension());
                double chunkValue = valuation.getTotalValue();
                double taxRate = taxManager.getTaxRateForCity(chunk.getCityId());
                double estimatedTax = chunkValue * taxRate;

                grandTotalValue += chunkValue;
                grandTotalTax += estimatedTax;

                // Get hierarchy names
                String cityName = StateCraftIntegration.getCityName(chunk.getCityId());
                String stateName = StateCraftIntegration.getStateName(StateCraftIntegration.getStateIdForCity(chunk.getCityId()));
                String nationName = StateCraftIntegration.getNationName(StateCraftIntegration.getNationIdForCity(chunk.getCityId()));

                if (cityName == null || cityName.isEmpty()) cityName = "Unknown City";
                if (stateName == null || stateName.isEmpty()) stateName = "Unknown State";
                if (nationName == null || nationName.isEmpty()) nationName = "Unknown Nation";

                // Capture as effectively final for lambda use
                final String finalNationName = nationName;
                final String finalStateName = stateName;
                final String finalCityName = cityName;
                final double finalTaxRate = taxRate;

                // Build hierarchy
                NationTaxBuilder nation = nationBuilders.computeIfAbsent(finalNationName, k -> new NationTaxBuilder(finalNationName));
                StateTaxBuilder state = nation.states.computeIfAbsent(finalStateName, k -> new StateTaxBuilder(finalStateName));
                CityTaxBuilder city = state.cities.computeIfAbsent(finalCityName, k -> new CityTaxBuilder(finalCityName, finalTaxRate));

                city.chunks.add(new SyncTaxReportPacket.ChunkData(
                    chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension(),
                    valuation.getBaseValue(),
                    valuation.getLocationMultiplier(),
                    valuation.getBiomeMultiplier(),
                    valuation.getDemandMultiplier(),
                    valuation.getGovernmentMultiplier(),
                    valuation.getImprovementMultiplier(),
                    valuation.getImprovementScore(),
                    chunkValue, estimatedTax
                ));
                city.totalValue += chunkValue;
                city.totalTax += estimatedTax;
                state.totalValue += chunkValue;
                state.totalTax += estimatedTax;
                nation.totalValue += chunkValue;
                nation.totalTax += estimatedTax;
            }

            // Build final data structure
            java.util.List<SyncTaxReportPacket.NationData> nations = new java.util.ArrayList<>();
            for (NationTaxBuilder nb : nationBuilders.values()) {
                java.util.List<SyncTaxReportPacket.StateData> states = new java.util.ArrayList<>();
                for (StateTaxBuilder sb : nb.states.values()) {
                    java.util.List<SyncTaxReportPacket.CityData> cities = new java.util.ArrayList<>();
                    for (CityTaxBuilder cb : sb.cities.values()) {
                        cities.add(new SyncTaxReportPacket.CityData(cb.name, cb.taxRate, cb.totalValue, cb.totalTax, cb.chunks));
                    }
                    states.add(new SyncTaxReportPacket.StateData(sb.name, sb.totalValue, sb.totalTax, cities));
                }
                nations.add(new SyncTaxReportPacket.NationData(nb.name, nb.totalValue, nb.totalTax, states));
            }

            // Calculate period info
            double balance = ecoManager.getBalance(playerId);
            int periodsAffordable = grandTotalTax > 0 ? (int) Math.floor(balance / grandTotalTax) : Integer.MAX_VALUE;

            long periodTicks = taxManager.getTaxPeriodTicks();
            long periodMinutes = (periodTicks / 20) / 60;
            long periodHours = periodMinutes / 60;
            String taxPeriod = periodHours > 0
                ? periodHours + "h " + (periodMinutes % 60) + "m"
                : periodMinutes + " minutes";

            long ticksUntilNext = taxManager.getTicksUntilNextCollection(player.getServer());
            long nextMinutes = (ticksUntilNext / 20) / 60;
            long nextSeconds = (ticksUntilNext / 20) % 60;
            String nextCollection = nextMinutes + "m " + nextSeconds + "s";

            SyncTaxReportPacket.TaxReportData data = new SyncTaxReportPacket.TaxReportData(
                totalChunks, grandTotalValue, grandTotalTax, balance,
                periodsAffordable, taxPeriod, nextCollection, nations
            );

            NetworkHandler.sendToPlayer(new SyncTaxReportPacket(data), player);
        });
        ctx.get().setPacketHandled(true);
    }

    // Helper classes for building tax report data
    private static class NationTaxBuilder {
        String name;
        java.util.Map<String, StateTaxBuilder> states = new java.util.LinkedHashMap<>();
        double totalValue = 0;
        double totalTax = 0;
        NationTaxBuilder(String name) { this.name = name; }
    }

    private static class StateTaxBuilder {
        String name;
        java.util.Map<String, CityTaxBuilder> cities = new java.util.LinkedHashMap<>();
        double totalValue = 0;
        double totalTax = 0;
        StateTaxBuilder(String name) { this.name = name; }
    }

    private static class CityTaxBuilder {
        String name;
        double taxRate;
        java.util.List<SyncTaxReportPacket.ChunkData> chunks = new java.util.ArrayList<>();
        double totalValue = 0;
        double totalTax = 0;
        CityTaxBuilder(String name, double taxRate) { this.name = name; this.taxRate = taxRate; }
    }
}

