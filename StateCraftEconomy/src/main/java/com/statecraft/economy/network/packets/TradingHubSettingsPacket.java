package com.statecraft.economy.network.packets;

import com.statecraft.economy.block.entity.TradingHubBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Packet for updating Trading Hub settings
 * Client -> Server
 */
public class TradingHubSettingsPacket {
    private final BlockPos pos;
    private final boolean depositToATM;
    private final List<ShareData> shares;

    /**
     * Data for a profit share entry
     */
    public static class ShareData {
        public final String playerName;
        public final double percentage;
        public final boolean isCompany;

        public ShareData(String playerName, double percentage) {
            this(playerName, percentage, false);
        }

        public ShareData(String playerName, double percentage, boolean isCompany) {
            this.playerName = playerName;
            this.percentage = percentage;
            this.isCompany = isCompany;
        }
    }

    public TradingHubSettingsPacket(BlockPos pos, boolean depositToATM, List<ShareData> shares) {
        this.pos = pos;
        this.depositToATM = depositToATM;
        this.shares = shares;
    }

    public TradingHubSettingsPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.depositToATM = buf.readBoolean();

        int shareCount = buf.readVarInt();
        this.shares = new ArrayList<>(shareCount);
        for (int i = 0; i < shareCount; i++) {
            String name = buf.readUtf(64);
            double percentage = buf.readDouble();
            boolean isCompany = buf.readBoolean();
            shares.add(new ShareData(name, percentage, isCompany));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(depositToATM);

        buf.writeVarInt(shares.size());
        for (ShareData share : shares) {
            buf.writeUtf(share.playerName, 64);
            buf.writeDouble(share.percentage);
            buf.writeBoolean(share.isCompany);
        }
    }

    public static void handle(TradingHubSettingsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(packet.pos);

            if (!(be instanceof TradingHubBlockEntity tradingHub)) {
                return;
            }

            // Check if player can modify settings
            if (!tradingHub.canModifySettings(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c[Trading Hub] You don't have permission to modify settings."));
                return;
            }

            // Update deposit mode
            tradingHub.setDepositToATM(packet.depositToATM);

            // Update profit shares
            List<TradingHubBlockEntity.ProfitShare> newShares = new ArrayList<>();

            for (ShareData shareData : packet.shares) {
                if (shareData.isCompany) {
                    // Resolve company by name
                    com.statecraft.company.Company company =
                        com.statecraft.company.CompanyManager.getInstance().getCompanyByName(shareData.playerName);
                    if (company != null) {
                        newShares.add(new TradingHubBlockEntity.ProfitShare(
                            company.getId(),
                            company.getName(),
                            shareData.percentage,
                            true
                        ));
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[Trading Hub] Company '" + shareData.playerName + "' not found."));
                    }
                } else {
                    // Try to resolve player UUID from name
                    UUID playerUUID = resolvePlayerUUID(player.getServer(), shareData.playerName);

                    if (playerUUID != null) {
                        newShares.add(new TradingHubBlockEntity.ProfitShare(
                            playerUUID,
                            shareData.playerName,
                            shareData.percentage
                        ));
                    } else {
                        // Player not found - use name-based UUID as fallback
                        // This allows setting up shares for offline players
                        playerUUID = UUID.nameUUIDFromBytes(shareData.playerName.toLowerCase().getBytes());
                        newShares.add(new TradingHubBlockEntity.ProfitShare(
                            playerUUID,
                            shareData.playerName,
                            shareData.percentage
                        ));

                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§e[Trading Hub] Warning: Player '" + shareData.playerName +
                            "' not found. Share will activate when they join."));
                    }
                }
            }

            tradingHub.setProfitShares(newShares);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a[Trading Hub] Settings saved successfully."));
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Try to resolve a player UUID from their name
     */
    private static UUID resolvePlayerUUID(net.minecraft.server.MinecraftServer server, String playerName) {
        // First check online players
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUUID();
        }

        // Check user cache for offline players
        var profile = server.getProfileCache().get(playerName);
        if (profile.isPresent()) {
            return profile.get().getId();
        }

        return null;
    }
}

