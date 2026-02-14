package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request details about a specific state
 */
public class RequestStateDetailsPacket {
    private final String nationName;
    private final String stateName;

    public RequestStateDetailsPacket(String nationName, String stateName) {
        this.nationName = nationName;
        this.stateName = stateName;
    }

    public RequestStateDetailsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(64);
        this.stateName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 64);
        buf.writeUtf(stateName, 64);
    }

    public String getNationName() {
        return nationName;
    }

    public String getStateName() {
        return stateName;
    }
}

