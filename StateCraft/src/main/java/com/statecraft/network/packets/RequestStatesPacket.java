package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of states for a nation
 */
public class RequestStatesPacket {
    private final String nationName;

    public RequestStatesPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestStatesPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
    }

    public String getNationName() {
        return nationName;
    }
}

