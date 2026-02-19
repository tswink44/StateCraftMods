package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of all privately owned chunks in a nation
 * Used by the Eminent Domain screen
 */
public class SyncNationPrivateChunksPacket {
    private final List<PrivateChunkEntry> chunks;

    public SyncNationPrivateChunksPacket(List<PrivateChunkEntry> chunks) {
        this.chunks = chunks;
    }

    public SyncNationPrivateChunksPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new PrivateChunkEntry(
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readDouble()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(chunks.size());
        for (PrivateChunkEntry chunk : chunks) {
            buf.writeInt(chunk.chunkX);
            buf.writeInt(chunk.chunkZ);
            buf.writeUtf(chunk.dimension, 128);
            buf.writeUtf(chunk.ownerName, 64);
            buf.writeUtf(chunk.cityName, 64);
            buf.writeUtf(chunk.stateName, 64);
            buf.writeUtf(chunk.formattedValuation, 64);
            buf.writeDouble(chunk.valuation);
        }
    }

    public List<PrivateChunkEntry> getChunks() { return chunks; }

    public static class PrivateChunkEntry {
        public final int chunkX;
        public final int chunkZ;
        public final String dimension;
        public final String ownerName;
        public final String cityName;
        public final String stateName;
        public final String formattedValuation;
        public final double valuation;

        public PrivateChunkEntry(int chunkX, int chunkZ, String dimension, String ownerName,
                                  String cityName, String stateName, String formattedValuation,
                                  double valuation) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.ownerName = ownerName;
            this.cityName = cityName;
            this.stateName = stateName;
            this.formattedValuation = formattedValuation;
            this.valuation = valuation;
        }
    }
}

