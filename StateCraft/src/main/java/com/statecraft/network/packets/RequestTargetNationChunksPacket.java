package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request all chunks belonging to a target nation.
 * Used by the PeaceTermsScreen to display selectable chunks for peace treaty demands.
 */
public class RequestTargetNationChunksPacket {

    private final String targetNationName;

    public RequestTargetNationChunksPacket(String targetNationName) {
        this.targetNationName = targetNationName;
    }

    public RequestTargetNationChunksPacket(FriendlyByteBuf buf) {
        this.targetNationName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetNationName);
    }

    public String getTargetNationName() { return targetNationName; }
}

