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
            if (StateCraftEconomy.isStateCraftLoaded()) {
                // Get government accounts player has admin access to
                var govAccounts = StateCraftIntegration.getPlayerAdminAccounts(player);
                accounts.addAll(govAccounts);
            }

            NetworkHandler.sendToPlayer(new SyncAccountsPacket(accounts), player);
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

        // Get valuation (improvement score)
        String dimension = player.level().dimension().location().toString();
        int valuation = com.statecraft.economy.valuation.ImprovementTracker.getInstance()
            .getScoreNoScan(chunkX, chunkZ, dimension);

        // Calculate estimated tax
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
}

