package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server request for chunk border data
 */
public class RequestChunkBordersPacket {
    private final int radius;

    public RequestChunkBordersPacket(int radius) {
        this.radius = radius;
    }

    public RequestChunkBordersPacket(FriendlyByteBuf buf) {
        this.radius = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(radius);
    }

    public int getRadius() {
        return radius;
    }
}

