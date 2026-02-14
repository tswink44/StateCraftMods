package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> Client sync of chunk border data for rendering
 */
public class SyncChunkBordersPacket {
    private final Map<Long, ChunkBorderInfo> chunks;
    private final int centerX;
    private final int centerZ;
    private final int radius;

    public SyncChunkBordersPacket(Map<Long, ChunkBorderInfo> chunks, int centerX, int centerZ, int radius) {
        this.chunks = chunks;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public SyncChunkBordersPacket(FriendlyByteBuf buf) {
        this.centerX = buf.readInt();
        this.centerZ = buf.readInt();
        this.radius = buf.readInt();
        int count = buf.readInt();
        this.chunks = new HashMap<>();
        for (int i = 0; i < count; i++) {
            long key = buf.readLong();
            ChunkBorderInfo info = new ChunkBorderInfo(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(24),
                buf.readUtf(24),
                buf.readUtf(24)
            );
            chunks.put(key, info);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(centerX);
        buf.writeInt(centerZ);
        buf.writeInt(radius);
        buf.writeInt(chunks.size());
        for (Map.Entry<Long, ChunkBorderInfo> entry : chunks.entrySet()) {
            buf.writeLong(entry.getKey());
            ChunkBorderInfo info = entry.getValue();
            buf.writeBoolean(info.own);
            buf.writeBoolean(info.ally);
            buf.writeBoolean(info.enemy);
            buf.writeUtf(info.nationName != null ? info.nationName : "", 24);
            buf.writeUtf(info.stateName != null ? info.stateName : "", 24);
            buf.writeUtf(info.cityName != null ? info.cityName : "", 24);
        }
    }

    public Map<Long, ChunkBorderInfo> getChunks() {
        return chunks;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getRadius() {
        return radius;
    }

    public static class ChunkBorderInfo {
        public final boolean own;
        public final boolean ally;
        public final boolean enemy;
        public final String nationName;
        public final String stateName;
        public final String cityName;

        public ChunkBorderInfo(boolean own, boolean ally, boolean enemy, String nationName, String stateName, String cityName) {
            this.own = own;
            this.ally = ally;
            this.enemy = enemy;
            this.nationName = nationName;
            this.stateName = stateName;
            this.cityName = cityName;
        }
    }
}

