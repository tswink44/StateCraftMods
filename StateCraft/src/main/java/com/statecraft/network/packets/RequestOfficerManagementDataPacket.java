package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of citizens for officer appointment
 */
public class RequestOfficerManagementDataPacket {
    private final String nationName;

    public RequestOfficerManagementDataPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestOfficerManagementDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
    }

    public String getNationName() {
        return nationName;
    }
}

