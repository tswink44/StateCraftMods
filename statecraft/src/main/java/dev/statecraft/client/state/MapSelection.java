package dev.statecraft.client.state;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.EntityRef;
import java.util.Optional;

public final class MapSelection {
    private MapSelection() {}

    public static boolean supported(long x, long z) {
        return x >= -ChunkKey.MAX_COORDINATE && x <= ChunkKey.MAX_COORDINATE
                && z >= -ChunkKey.MAX_COORDINATE && z <= ChunkKey.MAX_COORDINATE;
    }

    public static Optional<ChunkKey> cell(TerritorySnapshot snapshot, int column, int row) {
        int diameter = snapshot.radius() * 2 + 1;
        if (column < 0 || row < 0 || column >= diameter || row >= diameter) return Optional.empty();
        long x = (long) snapshot.centerX() - snapshot.radius() + column;
        long z = (long) snapshot.centerZ() - snapshot.radius() + row;
        if (!supported(x, z)) return Optional.empty();
        try {
            return Optional.of(new ChunkKey(snapshot.dimension(), (int) x, (int) z));
        } catch (UserError invalidDimension) {
            return Optional.empty();
        }
    }

    public static EntityRef reference(ChunkKey chunk) {
        return new EntityRef("statecraft", EntityRef.Kind.CLAIM, chunk.toString());
    }

    public static TerritorySnapshot exportable(TerritorySnapshot snapshot) {
        return new TerritorySnapshot(snapshot.dimension(), snapshot.centerX(), snapshot.centerZ(), snapshot.radius(),
                snapshot.territories().stream().filter(t -> supported(t.x(), t.z())).toList());
    }
}
