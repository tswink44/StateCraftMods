package dev.statecraft.client.state;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientMapSelectionTest {
    @Test
    void everyCellAtEitherWorldCornerCanBeInspectedWithoutThrowingAndOnlySupportedCellsSelect() {
        for (int sign : List.of(-1, 1)) {
            var snapshot = new TerritorySnapshot("minecraft:overworld", sign * ChunkKey.MAX_COORDINATE,
                    sign * ChunkKey.MAX_COORDINATE, 8, List.of());
            int supported = 0;
            for (int column = 0; column < 17; column++) {
                for (int row = 0; row < 17; row++) {
                    var selection = MapSelection.cell(snapshot, column, row);
                    if (selection.isPresent()) {
                        supported++;
                        assertTrue(MapSelection.supported(selection.get().x(), selection.get().z()));
                    }
                }
            }
            assertEquals(81, supported);
        }
    }

    @Test
    void unsupportedCellsAndPixelsOutsideTheGridCannotConstructChunkKeys() {
        var snapshot = new TerritorySnapshot("minecraft:overworld", ChunkKey.MAX_COORDINATE, 0, 8, List.of());
        assertTrue(MapSelection.cell(snapshot, 9, 8).isEmpty());
        assertTrue(MapSelection.cell(snapshot, -1, 0).isEmpty());
        assertTrue(MapSelection.cell(snapshot, 17, 0).isEmpty());
        assertTrue(MapSelection.cell(snapshot, 0, 17).isEmpty());
        assertEquals(ChunkKey.MAX_COORDINATE, MapSelection.cell(snapshot, 8, 8).orElseThrow().x());
        assertFalse(MapSelection.supported(Long.MAX_VALUE, 0));
        assertFalse(MapSelection.supported(0, Long.MIN_VALUE));
    }

    @Test
    void validCentralMapsRetainAllCellsAndUnsupportedExportTargetsAreExcluded() {
        var normal = new TerritorySnapshot("minecraft:overworld", 0, 0, 8, List.of());
        for (int column = 0; column < 17; column++) {
            for (int row = 0; row < 17; row++) assertTrue(MapSelection.cell(normal, column, row).isPresent());
        }
        var valid = territory(ChunkKey.MAX_COORDINATE);
        var invalid = territory(ChunkKey.MAX_COORDINATE + 1);
        var edge = new TerritorySnapshot("minecraft:overworld", ChunkKey.MAX_COORDINATE, 0, 8, List.of(valid, invalid));
        assertEquals(List.of(valid), MapSelection.exportable(edge).territories());
    }

    @Test
    void supportedWildernessProducesTheExactTypedClaimTargetButOutOfRangeCellsCannot() {
        var wilderness = new TerritorySnapshot("minecraft:overworld", ChunkKey.MAX_COORDINATE, 12, 8, List.of());
        EntityRef target = MapSelection.cell(wilderness, 8, 8).map(MapSelection::reference).orElseThrow();
        assertEquals(new EntityRef("statecraft", EntityRef.Kind.CLAIM,
                "minecraft:overworld|" + ChunkKey.MAX_COORDINATE + "|12"), target);
        assertEquals("statecraft:detail", UiQuery.detail(target).page());
        assertEquals(target, UiQuery.detail(target).entity());
        assertTrue(MapSelection.cell(wilderness, 9, 8).map(MapSelection::reference).isEmpty());
    }

    private static TerritorySnapshot.Territory territory(int x) {
        return new TerritorySnapshot.Territory(x, 0, "nation", "state", "city",
                "Nation", "State", "City", "owner", 0x337755, 0);
    }
}
