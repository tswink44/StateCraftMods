package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet requesting chunk valuation data
 */
public class RequestChunkValuationPacket {

    private final int chunkX;
    private final int chunkZ;

    public RequestChunkValuationPacket(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public RequestChunkValuationPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
    }

    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
}

