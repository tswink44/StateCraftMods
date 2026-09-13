package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.TerritorySnapshot.Territory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryOverlaySessionTest {
    private final RecordingRenderer renderer = new RecordingRenderer();
    private final TerritoryOverlaySession session = new TerritoryOverlaySession(renderer);

    @Test
    void waitsForMappingAndUsesLatestSnapshot() throws Exception {
        session.update(snapshot("minecraft:overworld", 0));
        session.update(snapshot("minecraft:overworld", 1));
        assertTrue(renderer.visible.isEmpty());

        session.mappingStarted("minecraft:overworld");

        assertEquals(1, renderer.visible.size());
        assertEquals(16, renderer.visible.get(0).minX());
    }

    @Test
    void replacementClearsOverlaysNoLongerInTheSnapshot() throws Exception {
        session.mappingStarted("minecraft:overworld");
        session.update(snapshot("minecraft:overworld", 0));
        String oldId = renderer.visible.get(0).id();

        session.update(snapshot("minecraft:overworld", 5));

        assertEquals(1, renderer.visible.size());
        assertNotEquals(oldId, renderer.visible.get(0).id());
        assertEquals(80, renderer.visible.get(0).minX());
    }

    @Test
    void switchingDimensionsRemovesOldOverlaysAndWaitsForMatchingSnapshot() throws Exception {
        session.mappingStarted("minecraft:overworld");
        session.update(snapshot("minecraft:overworld", 0));

        session.mappingStarted("minecraft:the_nether");
        assertTrue(renderer.visible.isEmpty());
        session.displayUpdated("minecraft:overworld");
        assertTrue(renderer.visible.isEmpty());

        session.update(snapshot("minecraft:the_nether", 2));
        assertEquals(1, renderer.visible.size());
        assertEquals("minecraft:the_nether", renderer.visible.get(0).dimension());
    }

    @Test
    void mappingStopDiscardsSnapshotSoAResetCannotResurrectStaleTerritories() throws Exception {
        session.mappingStarted("minecraft:overworld");
        session.update(snapshot("minecraft:overworld", 0));

        session.mappingStopped();
        assertTrue(renderer.visible.isEmpty());
        session.displayUpdated("minecraft:overworld");
        session.mappingStarted("minecraft:overworld");
        assertTrue(renderer.visible.isEmpty());

        session.update(snapshot("minecraft:overworld", 3));
        assertEquals(1, renderer.visible.size());
    }

    @Test
    void emptyLogoutEventClearsEveryDimensionAndNeverRestoresOldDataOnDisplayUpdate() throws Exception {
        session.mappingStarted("minecraft:the_nether");
        session.update(snapshot("minecraft:the_nether", 0));
        assertFalse(renderer.visible.isEmpty());

        session.update(TerritorySnapshot.EMPTY);
        session.displayUpdated("minecraft:the_nether");
        session.mappingStopped();
        session.mappingStarted("minecraft:overworld");

        assertTrue(renderer.visible.isEmpty());
    }

    @Test
    void displayUpdatesResubmitOnlyTheCurrentDimensionSnapshot() throws Exception {
        session.mappingStarted("minecraft:overworld");
        session.update(snapshot("minecraft:overworld", 0));
        List<TerritoryMapGeometry.Region> expected = List.copyOf(renderer.visible);
        renderer.visible.clear();

        session.displayUpdated("minecraft:the_nether");
        assertTrue(renderer.visible.isEmpty());
        session.displayUpdated("minecraft:overworld");
        assertEquals(expected, renderer.visible);
    }

    private static TerritorySnapshot snapshot(String dimension, int x) {
        return new TerritorySnapshot(dimension, x, 0, 0,
                List.of(new Territory(x, 0, "nation", "state", "city", "Nation", "State", "City",
                        "owner", 0x336699, 0)));
    }

    private static final class RecordingRenderer implements TerritoryOverlaySession.Renderer {
        private final List<TerritoryMapGeometry.Region> visible = new ArrayList<>();

        @Override
        public void clear() {
            visible.clear();
        }

        @Override
        public void show(List<TerritoryMapGeometry.Region> regions) {
            visible.addAll(regions);
        }
    }
}
