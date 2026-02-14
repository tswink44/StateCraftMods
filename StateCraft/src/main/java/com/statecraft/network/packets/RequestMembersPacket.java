package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of members for a nation
 */
public class RequestMembersPacket {
    private final String nationName;

    public RequestMembersPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestMembersPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
    }

    public String getNationName() {
        return nationName;
    }
}

