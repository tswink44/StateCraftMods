package dev.statecraft.client.state;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainTileCacheTest {
    @Test
    void onlyFourSurfaceSamplesCanBeReservedPerFrameAndFreshTilesAreReused() {
        var cache = new TerrainTileCache<Integer, Integer>(ignored -> {});
        cache.beginFrame("world/overworld");
        for (int i = 0; i < 4; i++) {
            assertTrue(cache.reserveUpdate(i, 100));
            cache.put(i, i, 100);
        }
        assertFalse(cache.reserveUpdate(4, 100));
        cache.beginFrame("world/overworld");
        assertFalse(cache.reserveUpdate(0, 101));
        assertTrue(cache.reserveUpdate(4, 101));
        assertTrue(cache.reserveUpdate(0, 100 + TerrainTileCache.REFRESH_MILLIS));
    }

    @Test
    void capacityEvictionReleasesLeastRecentlyUsedTextures() {
        var released = new ArrayList<Integer>();
        var cache = new TerrainTileCache<Integer, Integer>(released::add);
        cache.beginFrame("world/overworld");
        for (int i = 0; i < 289; i++) cache.put(i, i, 0);
        assertEquals(0, cache.get(0));
        cache.put(289, 289, 0);
        assertEquals(289, cache.size());
        assertEquals(List.of(1), released);
        assertNull(cache.get(1));
    }

    @Test
    void replacementUnloadDimensionAndDisconnectReleaseEachTextureExactlyOnce() {
        var released = new ArrayList<Integer>();
        var cache = new TerrainTileCache<Integer, Integer>(released::add);
        cache.beginFrame("world/overworld");
        cache.put(0, 10, 0);
        cache.put(0, 20, 100);
        cache.discard(0);
        cache.discard(0);
        cache.put(1, 30, 100);
        cache.beginFrame("world/nether");
        assertEquals(0, cache.size());
        cache.put(1, 40, 100);
        cache.beginFrame("other-world/nether");
        cache.put(1, 50, 100);
        cache.close();
        cache.close();
        cache.beginFrame("world/overworld");
        assertFalse(cache.reserveUpdate(0, 200));
        cache.put(2, 60, 200);
        assertEquals(List.of(10, 20, 30, 40, 50, 60), released);
        assertEquals(0, cache.size());
    }

    @Test
    void failedSamplesAreBoundedAndDoNotRetryEveryFrame() {
        var cache = new TerrainTileCache<Integer, Integer>(ignored -> fail("No texture to release"));
        cache.beginFrame("world/overworld");
        assertTrue(cache.reserveUpdate(0, 0));
        cache.put(0, null, 0);
        cache.beginFrame("world/overworld");
        assertNull(cache.get(0));
        assertTrue(cache.contains(0));
        assertFalse(cache.reserveUpdate(0, 1));
        assertTrue(cache.reserveUpdate(0, TerrainTileCache.REFRESH_MILLIS));
        cache.close();
    }

    @Test
    void staleCentralTilesCannotStarveTheRestOfTheMapEvenAtOneFramePerSecond() {
        var sampled = new java.util.HashSet<Integer>();
        var cache = new TerrainTileCache<Integer, Integer>(ignored -> {});
        for (int frame = 0; frame < 74; frame++) {
            cache.beginFrame("world/overworld");
            for (int tile = 0; tile < 289; tile++) {
                if (cache.reserveUpdate(tile, frame * 1000L)) {
                    sampled.add(tile);
                    cache.put(tile, tile, frame * 1000L);
                }
            }
        }
        assertEquals(289, sampled.size());
    }

    @Test
    void movingTheMapDropsObsoleteQueuedTilesRatherThanBlockingNewOnes() {
        var cache = new TerrainTileCache<Integer, Integer>(ignored -> {});
        cache.beginFrame("world/overworld");
        for (int i = 0; i < 289; i++) if (cache.reserveUpdate(i, 0)) cache.put(i, i, 0);
        for (int frame = 1; frame <= 2; frame++) {
            cache.beginFrame("world/overworld");
            for (int i = 300; i < 589; i++) if (cache.reserveUpdate(i, frame)) cache.put(i, i, frame);
        }
        assertNotNull(cache.get(300));
    }
}
