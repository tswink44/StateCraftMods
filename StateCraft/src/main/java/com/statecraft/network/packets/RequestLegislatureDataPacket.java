package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request legislature data for a nation
 */
public class RequestLegislatureDataPacket {
    private final String nationName;

    public RequestLegislatureDataPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestLegislatureDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
    }

    public String getNationName() {
        return nationName;
    }
}

