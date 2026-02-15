package com.statecraft.economy.network;

import com.statecraft.economy.client.screen.ATMScreen;
import com.statecraft.economy.client.screen.ChunkMarketScreen;
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
                    packet.canBuy()
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
                packet.getImprovementValue(), packet.getImprovementScore(),
                packet.getTotalValue()
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
        public final double improvementValue;
        public final int improvementScore;
        public final double totalValue;

        public ChunkValuationData(int chunkX, int chunkZ, String dimension,
                                  double baseValue, double locationMultiplier, double distanceFromSpawn,
                                  double biomeMultiplier, String biomeName,
                                  double demandMultiplier, int nearbyClaims,
                                  double governmentMultiplier,
                                  double improvementValue, int improvementScore,
                                  double totalValue) {
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
            this.improvementValue = improvementValue;
            this.improvementScore = improvementScore;
            this.totalValue = totalValue;
        }
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
}

