package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of all privately owned chunks in a nation
 * Used by the Eminent Domain screen in the legislature
 */
public class RequestNationPrivateChunksPacket {
    private final String nationName;

    public RequestNationPrivateChunksPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestNationPrivateChunksPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
    }

    public String getNationName() { return nationName; }
}

