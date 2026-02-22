package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of all chunks in a city
 */
public class SyncCityChunksPacket {
    private final String cityName;
    private final List<ChunkEntry> chunks;

    public SyncCityChunksPacket(String cityName, List<ChunkEntry> chunks) {
        this.cityName = cityName;
        this.chunks = chunks;
    }

    public SyncCityChunksPacket(FriendlyByteBuf buf) {
        this.cityName = buf.readUtf(64);
        int count = buf.readVarInt();
        this.chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new ChunkEntry(
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(128),
                buf.readUtf(32),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readDouble()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cityName, 64);
        buf.writeVarInt(chunks.size());
        for (ChunkEntry chunk : chunks) {
            buf.writeInt(chunk.x);
            buf.writeInt(chunk.z);
            buf.writeUtf(chunk.dimension, 128);
            buf.writeUtf(chunk.ownershipType, 32);
            buf.writeUtf(chunk.ownerName, 64);
            buf.writeBoolean(chunk.forSale);
            buf.writeDouble(chunk.salePrice);
        }
    }

    public String getCityName() { return cityName; }
    public List<ChunkEntry> getChunks() { return chunks; }

    public static class ChunkEntry {
        public final int x;
        public final int z;
        public final String dimension;
        public final String ownershipType; // "HIERARCHY", "PLAYER", "COMPANY"
        public final String ownerName;     // Player name, Company name, or "City"
        public final boolean forSale;
        public final double salePrice;

        public ChunkEntry(int x, int z, String dimension, String ownershipType, String ownerName,
                           boolean forSale, double salePrice) {
            this.x = x;
            this.z = z;
            this.dimension = dimension;
            this.ownershipType = ownershipType;
            this.ownerName = ownerName;
            this.forSale = forSale;
            this.salePrice = salePrice;
        }
    }
}

