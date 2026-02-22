package com.statecraft.client.integration;

import com.statecraft.client.ChunkBorderCache;

import javax.annotation.Nullable;

/**
 * Provides claim data for minimap integrations.
 * This is the public API that minimap mods (or other mods) can call to query
 * StateCraft territory information for any chunk.
 *
 * All methods are safe to call from the client thread.
 *
 * <h3>Usage for external mod developers:</h3>
 * <pre>{@code
 * // Check if a chunk is claimed
 * MinimapDataProvider.ChunkTerritoryInfo info = MinimapDataProvider.getTerritoryInfo(chunkX, chunkZ);
 * if (info != null && info.isClaimed()) {
 *     String nationName = info.nationName();
 *     String cityName = info.cityName();
 *     int color = info.overlayColor();
 * }
 * }</pre>
 */
public class MinimapDataProvider {

    // Overlay colors (ARGB) matching StateCraft's territory coloring
    public static final int COLOR_OWN_FILL = 0x404CAF50;       // Green, 25% opacity
    public static final int COLOR_ALLY_FILL = 0x402196F3;       // Blue, 25% opacity
    public static final int COLOR_ENEMY_FILL = 0x40F44336;      // Red, 25% opacity
    public static final int COLOR_OTHER_FILL = 0x40FF9800;      // Orange, 25% opacity

    public static final int COLOR_OWN_BORDER = 0xC04CAF50;      // Green, 75% opacity
    public static final int COLOR_ALLY_BORDER = 0xC02196F3;     // Blue, 75% opacity
    public static final int COLOR_ENEMY_BORDER = 0xC0F44336;    // Red, 75% opacity
    public static final int COLOR_OTHER_BORDER = 0xC0FF9800;    // Orange, 75% opacity

    /**
     * Get territory information for a specific chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return territory info, or null if the chunk is unclaimed or not in cache
     */
    @Nullable
    public static ChunkTerritoryInfo getTerritoryInfo(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        if (info == null || !info.isClaimed()) {
            return null;
        }
        return new ChunkTerritoryInfo(info);
    }

    /**
     * Check if a chunk is claimed territory.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return true if the chunk is claimed
     */
    public static boolean isClaimed(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        return info != null && info.isClaimed();
    }

    /**
     * Get the overlay fill color (ARGB) for a chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return ARGB color, or 0 if unclaimed
     */
    public static int getOverlayFillColor(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        if (info == null || !info.isClaimed()) return 0;
        return getFillColor(info);
    }

    /**
     * Get the overlay border color (ARGB) for a chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return ARGB color, or 0 if unclaimed
     */
    public static int getOverlayBorderColor(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        if (info == null || !info.isClaimed()) return 0;
        return getBorderColor(info);
    }

    /**
     * Get the nation name for a chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return nation name, or null if unclaimed
     */
    @Nullable
    public static String getNationName(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        if (info == null || !info.isClaimed()) return null;
        return info.getNationName();
    }

    /**
     * Get a formatted display label for a chunk's territory.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return formatted label like "NationName / StateName / CityName", or null if unclaimed
     */
    @Nullable
    public static String getTerritoryLabel(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        if (info == null || !info.isClaimed()) return null;
        return buildLabel(info);
    }

    /**
     * Get the diplomatic relationship type for a chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return relationship type, or UNCLAIMED if not claimed
     */
    public static RelationshipType getRelationship(int chunkX, int chunkZ) {
        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        if (info == null || !info.isClaimed()) return RelationshipType.UNCLAIMED;
        if (info.isOwn()) return RelationshipType.OWN;
        if (info.isAlly()) return RelationshipType.ALLY;
        if (info.isEnemy()) return RelationshipType.ENEMY;
        return RelationshipType.NEUTRAL;
    }

    // ==================== Internal Helpers ====================

    private static int getFillColor(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return COLOR_OWN_FILL;
        if (info.isAlly()) return COLOR_ALLY_FILL;
        if (info.isEnemy()) return COLOR_ENEMY_FILL;
        return COLOR_OTHER_FILL;
    }

    private static int getBorderColor(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return COLOR_OWN_BORDER;
        if (info.isAlly()) return COLOR_ALLY_BORDER;
        if (info.isEnemy()) return COLOR_ENEMY_BORDER;
        return COLOR_OTHER_BORDER;
    }

    private static String buildLabel(ChunkBorderCache.ChunkClaimInfo info) {
        StringBuilder label = new StringBuilder();
        if (info.getNationName() != null) {
            label.append(info.getNationName());
        }
        if (info.getStateName() != null && !info.getStateName().isEmpty()) {
            label.append(" / ").append(info.getStateName());
        }
        if (info.getCityName() != null && !info.getCityName().isEmpty()) {
            label.append(" / ").append(info.getCityName());
        }
        return label.toString();
    }

    // ==================== Data Types ====================

    /**
     * Relationship types for territory coloring
     */
    public enum RelationshipType {
        OWN,        // Player's own nation
        ALLY,       // Allied nation
        ENEMY,      // Enemy nation
        NEUTRAL,    // Other nation (neutral)
        UNCLAIMED   // Not claimed
    }

    /**
     * Territory information for a single chunk.
     * Immutable data class returned by {@link #getTerritoryInfo(int, int)}.
     */
    public static class ChunkTerritoryInfo {
        private final String nationName;
        private final String stateName;
        private final String cityName;
        private final boolean own;
        private final boolean ally;
        private final boolean enemy;

        ChunkTerritoryInfo(ChunkBorderCache.ChunkClaimInfo info) {
            this.nationName = info.getNationName();
            this.stateName = info.getStateName();
            this.cityName = info.getCityName();
            this.own = info.isOwn();
            this.ally = info.isAlly();
            this.enemy = info.isEnemy();
        }

        public boolean isClaimed() { return true; }
        public String nationName() { return nationName; }
        public String stateName() { return stateName; }
        public String cityName() { return cityName; }
        public boolean isOwn() { return own; }
        public boolean isAlly() { return ally; }
        public boolean isEnemy() { return enemy; }

        /** Get overlay fill color (ARGB) for this territory */
        public int overlayFillColor() {
            if (own) return COLOR_OWN_FILL;
            if (ally) return COLOR_ALLY_FILL;
            if (enemy) return COLOR_ENEMY_FILL;
            return COLOR_OTHER_FILL;
        }

        /** Get overlay border color (ARGB) for this territory */
        public int overlayBorderColor() {
            if (own) return COLOR_OWN_BORDER;
            if (ally) return COLOR_ALLY_BORDER;
            if (enemy) return COLOR_ENEMY_BORDER;
            return COLOR_OTHER_BORDER;
        }

        /** Get the diplomatic relationship type */
        public RelationshipType relationship() {
            if (own) return RelationshipType.OWN;
            if (ally) return RelationshipType.ALLY;
            if (enemy) return RelationshipType.ENEMY;
            return RelationshipType.NEUTRAL;
        }

        /** Get a formatted display label: "Nation / State / City" */
        public String label() {
            return buildLabel(new ChunkBorderCache.ChunkClaimInfo(
                true, own, ally, enemy, nationName, stateName, cityName));
        }
    }
}

