package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Request current player's nation data for the main menu
 */
public class RequestNationDataPacket {

    public RequestNationDataPacket() {
    }

    public RequestNationDataPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

