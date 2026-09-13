package dev.statecraft.api;

import java.util.Objects;
import java.util.UUID;

public record Actor(UUID id, String name, boolean admin, String dimension, int chunkX, int chunkZ) {
    public Actor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(dimension);
    }

    public String account() {
        return "player:" + id;
    }

    public String chunkKey() {
        return new ChunkKey(dimension, chunkX, chunkZ).toString();
    }
}
