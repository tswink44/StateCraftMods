package dev.statecraft.api;

import java.util.List;

public record TerritorySnapshot(String dimension, int centerX, int centerZ, int radius, List<Territory> territories) {
    public static final int MAX_RADIUS = 8;
    public static final int MAX_TERRITORIES = (MAX_RADIUS * 2 + 1) * (MAX_RADIUS * 2 + 1);
    public static final TerritorySnapshot EMPTY = new TerritorySnapshot("minecraft:overworld", 0, 0, 0, List.of());

    public TerritorySnapshot {
        new ChunkKey(dimension, centerX, centerZ);
        if (radius < 0 || radius > MAX_RADIUS || territories.size() > MAX_TERRITORIES) {
            throw new IllegalArgumentException("Invalid territory snapshot bounds.");
        }
        territories = List.copyOf(territories);
    }

    public record Territory(int x, int z, String nationId, String stateId, String cityId,
                            String nationName, String stateName, String cityName,
                            String ownerAccount, int color, int improvements) {}
}
