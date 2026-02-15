package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client requests election data for their nation
 */
public class RequestElectionDataPacket {

    public RequestElectionDataPacket() {
    }

    public RequestElectionDataPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

