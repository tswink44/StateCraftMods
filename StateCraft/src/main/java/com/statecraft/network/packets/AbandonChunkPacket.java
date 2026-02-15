package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to abandon a privately owned chunk
 * This returns the chunk to city (government) ownership
 */
public class AbandonChunkPacket {
    private final int chunkX;
    private final int chunkZ;

    public AbandonChunkPacket(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public AbandonChunkPacket(FriendlyByteBuf buf) {
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

