package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to withdraw currency
 */
public class WithdrawPacket {
    private final double amount;
    private final String currencyType; // Which coin type to receive

    public WithdrawPacket(double amount, String currencyType) {
        this.amount = amount;
        this.currencyType = currencyType;
    }

    public WithdrawPacket(FriendlyByteBuf buf) {
        this.amount = buf.readDouble();
        this.currencyType = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(amount);
        buf.writeUtf(currencyType, 64);
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrencyType() {
        return currencyType;
    }
}

