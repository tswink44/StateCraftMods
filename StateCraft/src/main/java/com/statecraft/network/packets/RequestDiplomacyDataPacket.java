package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request diplomacy data for the player's nation.
 */
public class RequestDiplomacyDataPacket {

    private final String nationName;

    public RequestDiplomacyDataPacket(String nationName) {
        this.nationName = nationName;
    }

    public RequestDiplomacyDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
    }

    public String getNationName() { return nationName; }
}

