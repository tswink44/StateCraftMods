package dev.statecraft.client.state;

import dev.statecraft.api.ChunkKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimMapLayoutTest {
    @Test
    void negativeBlockPositionsUseFloorNotTruncation() {
        assertEquals(-1, ClaimMapLayout.chunkCoordinate(-0.01));
        assertEquals(-1, ClaimMapLayout.chunkCoordinate(-16));
        assertEquals(-2, ClaimMapLayout.chunkCoordinate(-16.01));
        assertEquals(0, ClaimMapLayout.chunkCoordinate(15.99));
        assertEquals(1, ClaimMapLayout.chunkCoordinate(16));
        assertThrows(IllegalArgumentException.class, () -> ClaimMapLayout.chunkCoordinate(Double.NaN));
    }

    @Test
    void pixelEdgesAreExclusiveAndCenterUsesThePlayerRatherThanTheSnapshotCenter() {
        var layout = ClaimMapLayout.fit(320, 240, 3);
        var map = layout.map();
        assertEquals(new ChunkKey("minecraft:overworld", -5, -10),
                layout.atPixel("minecraft:overworld", -2, -7, map.x(), map.y()).orElseThrow());
        assertEquals(new ChunkKey("minecraft:overworld", -2, -7),
                layout.atPixel("minecraft:overworld", -2, -7, map.x() + map.width() / 2.0, map.y() + map.height() / 2.0).orElseThrow());
        assertTrue(layout.atPixel("minecraft:overworld", 0, 0, map.right(), map.y()).isEmpty());
        assertTrue(layout.atPixel("minecraft:overworld", 0, 0, map.x() - 0.01, map.y()).isEmpty());
        assertTrue(layout.atPixel("minecraft:overworld", 0, 0, map.x(), map.bottom()).isEmpty());
        assertEquals(-4, layout.atPixel("minecraft:overworld", -2, -7,
                map.x() + layout.cellSize(), map.y()).orElseThrow().x());
    }

    @Test
    void supportedWorldCornersNeverConstructInvalidChunkKeys() {
        for (int sign : new int[] {-1, 1}) {
            var layout = ClaimMapLayout.fit(320, 240, 8);
            int valid = 0;
            for (int row = 0; row < layout.diameter(); row++) {
                for (int column = 0; column < layout.diameter(); column++) {
                    if (layout.atCell("minecraft:overworld", sign * ChunkKey.MAX_COORDINATE,
                            sign * ChunkKey.MAX_COORDINATE, column, row).isPresent()) valid++;
                }
            }
            assertEquals(81, valid);
        }
    }

    @Test
    void controlsAndMapFitWithoutOverlapAtMinimumAndLargerGuiSizesForEveryZoom() {
        for (int[] size : new int[][] {{320, 240}, {426, 240}, {640, 360}, {854, 480}, {1920, 1080}}) {
            for (int radius = 3; radius <= 8; radius++) {
                var layout = ClaimMapLayout.fit(size[0], size[1], radius);
                assertTrue(layout.cellSize() >= 6);
                assertTrue(layout.map().bottom() < layout.captionY());
                assertTrue(layout.captionY() + 9 < layout.target().y());
                assertTrue(layout.northY() + 9 < layout.map().y());
                for (var control : layout.controls()) {
                    assertTrue(control.x() >= 0 && control.y() >= 0);
                    assertTrue(control.right() <= size[0] && control.bottom() <= size[1]);
                    assertFalse(overlap(control, layout.map()));
                    for (var other : layout.controls()) if (control != other) assertFalse(overlap(control, other));
                }
            }
        }
        assertEquals(3, ClaimMapLayout.fit(320, 240, 0).radius());
        assertEquals(8, ClaimMapLayout.fit(320, 240, 100).radius());
    }

    private static boolean overlap(ClaimMapLayout.Rect a, ClaimMapLayout.Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
