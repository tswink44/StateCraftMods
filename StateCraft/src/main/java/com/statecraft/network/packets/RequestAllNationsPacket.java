package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of all nations
 */
public class RequestAllNationsPacket {

    public RequestAllNationsPacket() {
    }

    public RequestAllNationsPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

