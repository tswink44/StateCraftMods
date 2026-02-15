package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of states the player is a citizen of
 */
public class RequestMyStatesPacket {

    public RequestMyStatesPacket() {
    }

    public RequestMyStatesPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

