package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to request available accounts for the ATM
 */
public class RequestAccountsPacket {

    public RequestAccountsPacket() {
    }

    public RequestAccountsPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

