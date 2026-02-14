package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Request detailed nation information
 */
public class RequestNationDetailsPacket {
    private final String nationName;

    public RequestNationDetailsPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestNationDetailsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(24);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 24);
    }

    public String getNationName() {
        return nationName;
    }
}

