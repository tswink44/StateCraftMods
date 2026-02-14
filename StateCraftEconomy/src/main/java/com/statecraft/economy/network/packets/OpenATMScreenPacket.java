package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Server -> Client packet to open the ATM screen
 */
public class OpenATMScreenPacket {
    private final UUID bankId;

    public OpenATMScreenPacket(UUID bankId) {
        this.bankId = bankId;
    }

    public OpenATMScreenPacket(FriendlyByteBuf buf) {
        this.bankId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(bankId);
    }

    public UUID getBankId() {
        return bankId;
    }
}

