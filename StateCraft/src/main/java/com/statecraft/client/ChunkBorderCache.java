package com.statecraft.client;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestChunkBordersPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for chunk claim information
 * Used for rendering borders without constant server requests
 */
public class ChunkBorderCache {

    // Cache of chunk claim info, keyed by chunk position (packed long)
    private static final Map<Long, ChunkClaimInfo> cache = new ConcurrentHashMap<>();

    // Last player chunk position for refresh detection
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;

    // Refresh interval (ticks)
    private static final int REFRESH_INTERVAL = 40; // 2 seconds
    private static int ticksSinceRefresh = 0;

    // Cache radius (chunks)
    private static final int CACHE_RADIUS = 6;

    /**
     * Called every client tick to check if cache needs refresh
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int currentChunkX = mc.player.chunkPosition().x;
        int currentChunkZ = mc.player.chunkPosition().z;

        ticksSinceRefresh++;

        // Check if player moved to a new chunk or refresh interval passed
        boolean playerMoved = currentChunkX != lastChunkX || currentChunkZ != lastChunkZ;
        boolean needsRefresh = ticksSinceRefresh >= REFRESH_INTERVAL;

        if (playerMoved || needsRefresh) {
            lastChunkX = currentChunkX;
            lastChunkZ = currentChunkZ;
            ticksSinceRefresh = 0;

            // Request updated chunk data from server
            requestChunkData();

            // Clean up chunks too far away
            if (playerMoved) {
                cleanupDistantChunks(currentChunkX, currentChunkZ);
            }
        }
    }

    private static void requestChunkData() {
        NetworkHandler.sendToServer(new RequestChunkBordersPacket(CACHE_RADIUS));
    }

    private static void cleanupDistantChunks(int centerX, int centerZ) {
        int maxDistance = CACHE_RADIUS + 2;
        cache.entrySet().removeIf(entry -> {
            long key = entry.getKey();
            int x = (int) (key & 0xFFFFFFFFL);
            int z = (int) ((key >> 32) & 0xFFFFFFFFL);
            // Handle sign extension for negative coords
            if (x > Integer.MAX_VALUE / 2) x -= Integer.MAX_VALUE;
            if (z > Integer.MAX_VALUE / 2) z -= Integer.MAX_VALUE;

            return Math.abs(x - centerX) > maxDistance || Math.abs(z - centerZ) > maxDistance;
        });
    }

    /**
     * Get claim info for a chunk
     */
    public static ChunkClaimInfo getChunkInfo(int chunkX, int chunkZ) {
        return cache.get(chunkKey(chunkX, chunkZ));
    }

    /**
     * Update cache with data from server - clears the specified area first
     */
    public static void updateCache(Map<Long, ChunkClaimInfo> newData, int centerX, int centerZ, int radius) {
        // First, remove all chunks in the update area (so unclaimed chunks get removed)
        clearArea(centerX, centerZ, radius);
        // Then add only the claimed chunks
        cache.putAll(newData);
    }

    /**
     * Clear all cached chunks in the specified area
     */
    private static void clearArea(int centerX, int centerZ, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                cache.remove(chunkKey(centerX + dx, centerZ + dz));
            }
        }
    }

    /**
     * Update a single chunk in the cache
     */
    public static void updateChunk(int chunkX, int chunkZ, ChunkClaimInfo info) {
        if (info == null || !info.isClaimed()) {
            cache.remove(chunkKey(chunkX, chunkZ));
        } else {
            cache.put(chunkKey(chunkX, chunkZ), info);
        }
    }

    /**
     * Clear the entire cache (e.g., on dimension change)
     */
    public static void clear() {
        cache.clear();
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }

    private static long chunkKey(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    /**
     * Information about a claimed chunk
     */
    public static class ChunkClaimInfo {
        private final boolean claimed;
        private final boolean own;
        private final boolean ally;
        private final boolean enemy;
        private final String nationName;
        private final String stateName;
        private final String cityName;

        public ChunkClaimInfo(boolean claimed, boolean own, boolean ally, boolean enemy,
                              String nationName, String stateName, String cityName) {
            this.claimed = claimed;
            this.own = own;
            this.ally = ally;
            this.enemy = enemy;
            this.nationName = nationName;
            this.stateName = stateName;
            this.cityName = cityName;
        }

        // Unclaimed chunk
        public static ChunkClaimInfo unclaimed() {
            return new ChunkClaimInfo(false, false, false, false, null, null, null);
        }

        public boolean isClaimed() { return claimed; }
        public boolean isOwn() { return own; }
        public boolean isAlly() { return ally; }
        public boolean isEnemy() { return enemy; }
        public String getNationName() { return nationName; }
        public String getStateName() { return stateName; }
        public String getCityName() { return cityName; }
    }
}

