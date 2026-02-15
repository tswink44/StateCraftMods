package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of cities the player is a resident of
 */
public class RequestMyCitiesPacket {

    public RequestMyCitiesPacket() {
    }

    public RequestMyCitiesPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

