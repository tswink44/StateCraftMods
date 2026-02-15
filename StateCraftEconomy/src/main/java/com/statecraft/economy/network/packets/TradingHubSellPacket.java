package com.statecraft.economy.network.packets;

import com.statecraft.economy.block.entity.TradingHubBlockEntity;
import com.statecraft.economy.core.EconomyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to perform a sale at a Trading Hub
 */
public class TradingHubSellPacket {
    private final BlockPos pos;

    public TradingHubSellPacket(BlockPos pos) {
        this.pos = pos;
    }

    public TradingHubSellPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Verify player is close enough to the block
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                player.sendSystemMessage(Component.literal("§cYou are too far from the Trading Hub!"));
                return;
            }

            // Get the block entity
            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof TradingHubBlockEntity tradingHub)) {
                player.sendSystemMessage(Component.literal("§cInvalid Trading Hub!"));
                return;
            }

            // Perform the sale
            double valueBefore = tradingHub.calculateSellValue();
            boolean success = tradingHub.sellItems(player);

            if (success) {
                // Get current balance for feedback
                double newBalance = EconomyManager.getInstance().getBalance(player.getUUID());
                player.sendSystemMessage(Component.literal(
                    String.format("§aSold items for $%.2f! New balance: $%.2f", valueBefore, newBalance)));
            } else {
                player.sendSystemMessage(Component.literal("§cCould not sell items!"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

