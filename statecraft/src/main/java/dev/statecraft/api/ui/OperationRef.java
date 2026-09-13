package dev.statecraft.api.ui;

import java.util.Objects;
import java.util.UUID;

public record OperationRef(UUID world, UUID id) {
    private static final UUID ZERO = new UUID(0, 0);
    public static final OperationRef NONE = new OperationRef(ZERO, ZERO);

    public OperationRef {
        Objects.requireNonNull(world);
        Objects.requireNonNull(id);
        if (world.equals(ZERO) != id.equals(ZERO)) throw new IllegalArgumentException("Invalid operation reference.");
    }

    public boolean present() { return !id.equals(ZERO); }
}
