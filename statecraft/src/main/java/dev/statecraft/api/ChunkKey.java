package dev.statecraft.api;

import java.util.Objects;

public record ChunkKey(String dimension, int x, int z) {
    public ChunkKey {
        Objects.requireNonNull(dimension);
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new UserError("Invalid dimension identifier.");
        }
        if (x < -1_875_000 || x > 1_875_000 || z < -1_875_000 || z > 1_875_000) {
            throw new UserError("Chunk coordinates are outside the world border.");
        }
    }

    public static ChunkKey parse(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3) {
            throw new UserError("Use a chunk key such as minecraft:overworld|0|0.");
        }
        try {
            return new ChunkKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            throw new UserError("Chunk coordinates must be whole numbers.");
        }
    }

    @Override
    public String toString() {
        return dimension + "|" + x + "|" + z;
    }

    public boolean adjacent(ChunkKey other) {
        return dimension.equals(other.dimension)
                && Math.abs((long) x - other.x) + Math.abs((long) z - other.z) == 1;
    }
}
