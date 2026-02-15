package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request current laws/policies for a nation
 */
public class RequestNationLawsPacket {
    private final String nationName;

    public RequestNationLawsPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestNationLawsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 64);
    }

    public String getNationName() { return nationName; }
}

