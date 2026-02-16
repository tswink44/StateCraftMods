package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request contract data for a nation
 */
public class RequestContractsPacket {

    private final String nationName;

    public RequestContractsPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestContractsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
    }

    public String getNationName() { return nationName; }
}

