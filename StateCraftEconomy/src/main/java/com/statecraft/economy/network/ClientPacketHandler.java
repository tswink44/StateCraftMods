package com.statecraft.economy.network;

import com.statecraft.economy.client.screen.AccountActivityScreen;
import com.statecraft.economy.client.screen.ATMScreen;
import com.statecraft.economy.client.screen.ChunkMarketScreen;
import com.statecraft.economy.client.screen.MarketplaceScreen;
import com.statecraft.economy.client.screen.SimpleATMScreen;
import com.statecraft.economy.network.packets.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Handles packets received on the client
 */
public class ClientPacketHandler {

    // Cached balance for GUI display
    private static double cachedBalance = 0;
    private static double cachedNationBalance = -1;

    // Cached available accounts
    private static List<SyncAccountsPacket.AccountInfo> cachedAccounts = new ArrayList<>();

    // Cached transfer recipients
    private static String cachedRecipientType = "";
    private static List<SyncTransferRecipientsPacket.RecipientInfo> cachedRecipients = new ArrayList<>();

    public static void handleSyncBalance(SyncBalancePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedBalance = packet.getBalance();
            cachedNationBalance = packet.getNationBalance();

            // Update ATM screen if open
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ATMScreen atmScreen) {
                atmScreen.setBalance(cachedBalance);
            } else if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateBalance(cachedBalance);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleTransactionResult(TransactionResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String prefix = packet.isSuccess() ? "§a" : "§c";
                mc.player.sendSystemMessage(Component.literal(prefix + packet.getMessage()));
            }
            cachedBalance = packet.getNewBalance();

            // Update ATM screen if open
            if (mc.screen instanceof ATMScreen atmScreen) {
                atmScreen.setBalance(cachedBalance);
                atmScreen.setStatusMessage(packet.getMessage(), 100);
            } else if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateBalance(cachedBalance);
                simpleAtm.showTransactionResult(packet.isSuccess(), packet.getMessage());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenATMScreen(OpenATMScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new SimpleATMScreen(packet.getBankId()));
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncAccounts(SyncAccountsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedAccounts = new ArrayList<>(packet.getAccounts());

            // Update client-side BankRegistry with server's bank list
            if (!packet.getBanks().isEmpty()) {
                var registry = com.statecraft.economy.core.EconomyManager.getInstance().getBankRegistry();
                for (var bankInfo : packet.getBanks()) {
                    try {
                        java.util.UUID bankId = java.util.UUID.fromString(bankInfo.id());
                        if (!registry.bankExists(bankId)) {
                            var bank = new com.statecraft.economy.core.Bank(bankId, bankInfo.name(), bankInfo.displayName());
                            bank.setInterestRate(bankInfo.interestRate());
                            bank.setWithdrawalFee(bankInfo.withdrawalFee());
                            bank.setTransferFee(bankInfo.transferFee());
                            bank.setAllowsLoans(bankInfo.allowsLoans());
                            bank.setColor(bankInfo.color());
                            registry.registerBank(bank);
                        } else {
                            // Update existing entry
                            var bank = registry.getBank(bankId);
                            bank.setName(bankInfo.name());
                            bank.setDisplayName(bankInfo.displayName());
                            bank.setInterestRate(bankInfo.interestRate());
                            bank.setWithdrawalFee(bankInfo.withdrawalFee());
                            bank.setTransferFee(bankInfo.transferFee());
                            bank.setAllowsLoans(bankInfo.allowsLoans());
                            bank.setColor(bankInfo.color());
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Update ATM screen if open
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateAvailableAccounts(cachedAccounts);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncTransferRecipients(SyncTransferRecipientsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            cachedRecipientType = packet.getRecipientType();
            cachedRecipients = new ArrayList<>(packet.getRecipients());

            // Update ATM screen if open
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof SimpleATMScreen simpleAtm) {
                simpleAtm.updateTransferRecipients(cachedRecipientType, cachedRecipients);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncChunkMarketInfo(SyncChunkMarketInfoPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ChunkMarketScreen marketScreen) {
                marketScreen.updateMarketInfo(
                    packet.isClaimed(),
                    packet.isForSale(),
                    packet.getSalePrice(),
                    packet.getSellerName(),
                    packet.getOwnerName(),
                    packet.getCityName(),
                    packet.isPrivatelyOwned(),
                    packet.canListForSale(),
                    packet.canBuy(),
                    packet.getValuation(),
                    packet.getEstimatedTax()
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // Cached valuation data for GUI
    private static ChunkValuationData cachedValuation = null;

    public static void handleSyncChunkValuation(SyncChunkValuationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Cache the valuation data
            cachedValuation = new ChunkValuationData(
                packet.getChunkX(), packet.getChunkZ(), packet.getDimension(),
                packet.getBaseValue(), packet.getLocationMultiplier(), packet.getDistanceFromSpawn(),
                packet.getBiomeMultiplier(), packet.getBiomeName(),
                packet.getDemandMultiplier(), packet.getNearbyClaims(),
                packet.getGovernmentMultiplier(),
                packet.getImprovementMultiplier(), packet.getImprovementScore(),
                packet.getTotalValue(), packet.getCityTaxRate()
            );
        });
        ctx.get().setPacketHandled(true);
    }

    public static ChunkValuationData getCachedValuation() {
        return cachedValuation;
    }

    public static void clearCachedValuation() {
        cachedValuation = null;
    }

    /**
     * Data class for cached chunk valuation
     */
    public static class ChunkValuationData {
        public final int chunkX, chunkZ;
        public final String dimension;
        public final double baseValue;
        public final double locationMultiplier;
        public final double distanceFromSpawn;
        public final double biomeMultiplier;
        public final String biomeName;
        public final double demandMultiplier;
        public final int nearbyClaims;
        public final double governmentMultiplier;
        public final double improvementMultiplier;
        public final int improvementScore;
        public final double totalValue;
        public final double cityTaxRate;

        public ChunkValuationData(int chunkX, int chunkZ, String dimension,
                                  double baseValue, double locationMultiplier, double distanceFromSpawn,
                                  double biomeMultiplier, String biomeName,
                                  double demandMultiplier, int nearbyClaims,
                                  double governmentMultiplier,
                                  double improvementMultiplier, int improvementScore,
                                  double totalValue, double cityTaxRate) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.baseValue = baseValue;
            this.locationMultiplier = locationMultiplier;
            this.distanceFromSpawn = distanceFromSpawn;
            this.biomeMultiplier = biomeMultiplier;
            this.biomeName = biomeName;
            this.demandMultiplier = demandMultiplier;
            this.nearbyClaims = nearbyClaims;
            this.governmentMultiplier = governmentMultiplier;
            this.improvementMultiplier = improvementMultiplier;
            this.improvementScore = improvementScore;
            this.totalValue = totalValue;
            this.cityTaxRate = cityTaxRate;
        }
    }

    public static void handleSyncAccountActivity(SyncAccountActivityPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            // Open the AccountActivityScreen with the received data
            mc.setScreen(new AccountActivityScreen(
                packet.getAccountType(),
                packet.getAccountName(),
                packet.getAccountId(),
                packet.getEntries()
            ));
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenMarketplaceScreen(OpenMarketplaceScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new MarketplaceScreen());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncMarketListings(SyncMarketListingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MarketplaceScreen marketScreen) {
                marketScreen.updateListings(packet.getEntries(), packet.isMyListingsView());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static double getCachedBalance() {
        return cachedBalance;
    }

    public static double getCachedNationBalance() {
        return cachedNationBalance;
    }

    public static List<SyncAccountsPacket.AccountInfo> getCachedAccounts() {
        return cachedAccounts;
    }

    public static String getCachedRecipientType() {
        return cachedRecipientType;
    }

    public static List<SyncTransferRecipientsPacket.RecipientInfo> getCachedRecipients() {
        return cachedRecipients;
    }

    public static void handleOpenStockMarketScreen(OpenStockMarketScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new com.statecraft.economy.client.screen.StockMarketScreen());
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncStockListings(SyncStockListingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof com.statecraft.economy.client.screen.StockMarketScreen stockScreen) {
                stockScreen.updateListings(packet.getEntries(), packet.isMyListingsView(), packet.getPlayerShares());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void handleSyncItemValues(SyncItemValuesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            com.statecraft.economy.config.ItemValueRegistry.getInstance().receiveSyncedValues(packet.getItemValues());
        });
        ctx.get().setPacketHandled(true);
    }
}

