package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to transfer money to another player
 */
public class TransferPacket {
    private final String targetPlayer;
    private final double amount;

    public TransferPacket(String targetPlayer, double amount) {
        this.targetPlayer = targetPlayer;
        this.amount = amount;
    }

    public TransferPacket(FriendlyByteBuf buf) {
        this.targetPlayer = buf.readUtf(64);
        this.amount = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetPlayer, 64);
        buf.writeDouble(amount);
    }

    public String getTargetPlayer() {
        return targetPlayer;
    }

    public double getAmount() {
        return amount;
    }
}

