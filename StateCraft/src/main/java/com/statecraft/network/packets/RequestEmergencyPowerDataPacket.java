package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request current emergency power data for executive actions screen.
 */
public class RequestEmergencyPowerDataPacket {

    private final String nationName;

    public RequestEmergencyPowerDataPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestEmergencyPowerDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
    }

    public String getNationName() { return nationName; }
}

