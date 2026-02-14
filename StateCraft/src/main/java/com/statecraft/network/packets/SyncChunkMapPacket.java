package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> Client packet to sync chunk map data
 */
public class SyncChunkMapPacket {
    private final int playerX;
    private final int playerZ;
    private final String playerNation;
    private final Map<Long, ChunkInfo> chunks;

    public SyncChunkMapPacket(int playerX, int playerZ, String playerNation, Map<Long, ChunkInfo> chunks) {
        this.playerX = playerX;
        this.playerZ = playerZ;
        this.playerNation = playerNation != null ? playerNation : "";
        this.chunks = chunks;
    }

    public SyncChunkMapPacket(FriendlyByteBuf buf) {
        this.playerX = buf.readInt();
        this.playerZ = buf.readInt();
        this.playerNation = buf.readUtf(24);

        int count = buf.readInt();
        this.chunks = new HashMap<>();
        for (int i = 0; i < count; i++) {
            long key = buf.readLong();
            ChunkInfo info = new ChunkInfo(
                buf.readUtf(24),
                buf.readUtf(24),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
            );
            chunks.put(key, info);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(playerX);
        buf.writeInt(playerZ);
        buf.writeUtf(playerNation, 24);

        buf.writeInt(chunks.size());
        for (Map.Entry<Long, ChunkInfo> entry : chunks.entrySet()) {
            buf.writeLong(entry.getKey());
            ChunkInfo info = entry.getValue();
            buf.writeUtf(info.nationName, 24);
            buf.writeUtf(info.cityName, 24);
            buf.writeBoolean(info.isPlayerNation);
            buf.writeBoolean(info.isAlly);
            buf.writeBoolean(info.isEnemy);
            buf.writeBoolean(info.canManage);
        }
    }

    public int getPlayerX() { return playerX; }
    public int getPlayerZ() { return playerZ; }
    public String getPlayerNation() { return playerNation; }
    public Map<Long, ChunkInfo> getChunks() { return chunks; }

    public static class ChunkInfo {
        public final String nationName;
        public final String cityName;
        public final boolean isPlayerNation;
        public final boolean isAlly;
        public final boolean isEnemy;
        public final boolean canManage;

        public ChunkInfo(String nationName, String cityName, boolean isPlayerNation,
                         boolean isAlly, boolean isEnemy, boolean canManage) {
            this.nationName = nationName;
            this.cityName = cityName;
            this.isPlayerNation = isPlayerNation;
            this.isAlly = isAlly;
            this.isEnemy = isEnemy;
            this.canManage = canManage;
        }
    }
}

