package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Nation treasury operations
 */
public class NationTreasuryPacket {

    public enum Action {
        DEPOSIT,
        WITHDRAW,
        VIEW
    }

    private final Action action;
    private final double amount;

    public NationTreasuryPacket(Action action, double amount) {
        this.action = action;
        this.amount = amount;
    }

    public NationTreasuryPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.amount = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeDouble(amount);
    }

    public Action getAction() {
        return action;
    }

    public double getAmount() {
        return amount;
    }
}

