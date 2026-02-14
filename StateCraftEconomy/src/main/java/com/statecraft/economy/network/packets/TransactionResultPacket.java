package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client: Result of a transaction
 */
public class TransactionResultPacket {
    private final boolean success;
    private final String message;
    private final double newBalance;

    public TransactionResultPacket(boolean success, String message, double newBalance) {
        this.success = success;
        this.message = message;
        this.newBalance = newBalance;
    }

    public TransactionResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.message = buf.readUtf(256);
        this.newBalance = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(message, 256);
        buf.writeDouble(newBalance);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public double getNewBalance() {
        return newBalance;
    }
}

