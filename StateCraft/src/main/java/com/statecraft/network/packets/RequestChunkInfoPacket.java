package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Request detailed chunk info from server
 */
public class RequestChunkInfoPacket {
    private final int chunkX;
    private final int chunkZ;

    public RequestChunkInfoPacket(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public RequestChunkInfoPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }
}

