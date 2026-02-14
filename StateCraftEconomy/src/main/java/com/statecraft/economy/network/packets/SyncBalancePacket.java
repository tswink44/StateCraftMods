package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client: Sync player balance
 */
public class SyncBalancePacket {
    private final double balance;
    private final double nationBalance; // -1 if not in nation

    public SyncBalancePacket(double balance, double nationBalance) {
        this.balance = balance;
        this.nationBalance = nationBalance;
    }

    public SyncBalancePacket(FriendlyByteBuf buf) {
        this.balance = buf.readDouble();
        this.nationBalance = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(balance);
        buf.writeDouble(nationBalance);
    }

    public double getBalance() {
        return balance;
    }

    public double getNationBalance() {
        return nationBalance;
    }
}

