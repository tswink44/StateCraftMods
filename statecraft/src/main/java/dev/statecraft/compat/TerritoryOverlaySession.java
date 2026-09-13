package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot;

import java.util.List;
import java.util.Objects;

final class TerritoryOverlaySession {
    interface Renderer {
        void clear() throws Exception;

        void show(List<TerritoryMapGeometry.Region> regions) throws Exception;
    }

    private final Renderer renderer;
    private TerritorySnapshot snapshot = TerritorySnapshot.EMPTY;
    private String mappingDimension;

    TerritoryOverlaySession(Renderer renderer) {
        this.renderer = Objects.requireNonNull(renderer);
    }

    void update(TerritorySnapshot snapshot) throws Exception {
        this.snapshot = Objects.requireNonNull(snapshot);
        redraw();
    }

    void mappingStarted(String dimension) throws Exception {
        mappingDimension = dimension;
        if (!snapshot.dimension().equals(dimension)) {
            snapshot = TerritorySnapshot.EMPTY;
        }
        redraw();
    }

    void mappingStopped() throws Exception {
        mappingDimension = null;
        snapshot = TerritorySnapshot.EMPTY;
        renderer.clear();
    }

    void displayUpdated(String dimension) throws Exception {
        if (Objects.equals(mappingDimension, dimension)) {
            redraw();
        }
    }

    private void redraw() throws Exception {
        renderer.clear();
        if (snapshot.dimension().equals(mappingDimension)) {
            List<TerritoryMapGeometry.Region> regions = TerritoryMapGeometry.regions(snapshot);
            if (!regions.isEmpty()) {
                renderer.show(regions);
            }
        }
    }
}
