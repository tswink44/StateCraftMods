package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Request chunk map data for the surrounding area
 */
public class RequestChunkMapPacket {
    private final int size;

    public RequestChunkMapPacket(int size) {
        this.size = size;
    }

    public RequestChunkMapPacket(FriendlyByteBuf buf) {
        this.size = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(size);
    }

    public int getSize() {
        return size;
    }
}

