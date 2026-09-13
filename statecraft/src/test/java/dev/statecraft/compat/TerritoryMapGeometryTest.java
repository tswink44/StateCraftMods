package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.TerritorySnapshot.Territory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryMapGeometryTest {
    @Test
    void usesWholeChunkEdgesAndCounterclockwiseSoutheastFirstCorners() {
        var regions = TerritoryMapGeometry.regions(new TerritorySnapshot("minecraft:overworld", -1, -2, 0,
                List.of(claim(-1, -2, "owner", 0xff336699))));

        assertEquals(1, regions.size());
        assertEquals(List.of(new TerritoryMapGeometry.Point(0, -16), new TerritoryMapGeometry.Point(0, -32),
                new TerritoryMapGeometry.Point(-16, -32), new TerritoryMapGeometry.Point(-16, -16)),
                regions.get(0).corners());
        assertEquals(0x336699, regions.get(0).color());
    }

    @Test
    void mergesAdjacentIdenticalClaimsIntoRectangles() {
        var regions = TerritoryMapGeometry.regions(snapshot("minecraft:overworld",
                claim(1, 1), claim(0, 1), claim(1, 0), claim(0, 0)));

        assertEquals(1, regions.size());
        var region = regions.get(0);
        assertEquals(0, region.minX());
        assertEquals(0, region.minZ());
        assertEquals(32, region.maxX());
        assertEquals(32, region.maxZ());
        assertEquals("City", region.label());
        assertTrue(region.description().contains("Owner: Alice"));
    }

    @Test
    void leavesHolesUnclaimedRatherThanDrawingABoundingBoxOverThem() {
        List<Territory> claims = new ArrayList<>();
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                if (x != 1 || z != 1) {
                    claims.add(claim(x, z));
                }
            }
        }
        var regions = TerritoryMapGeometry.regions(new TerritorySnapshot("minecraft:overworld", 0, 0, 8, claims));

        assertEquals(8 * 16 * 16, regions.stream()
                .mapToInt(region -> (region.maxX() - region.minX()) * (region.maxZ() - region.minZ())).sum());
        assertTrue(regions.stream().noneMatch(region -> region.minX() <= 24 && region.maxX() > 24
                && region.minZ() <= 24 && region.maxZ() > 24));
    }

    @Test
    void neverMergesAcrossDifferentOwnersOrColors() {
        var regions = TerritoryMapGeometry.regions(snapshot("minecraft:overworld",
                claim(0, 0, "first", 0x336699), claim(1, 0, "second", 0x336699),
                claim(2, 0, "second", 0x993366)));

        assertEquals(3, regions.size());
        assertEquals(3, regions.stream().map(TerritoryMapGeometry.Region::id).distinct().count());
    }

    @Test
    void boundsAndDeduplicatesSnapshotCellsWithoutLoadingChunks() {
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:overworld", 0, 0, 0,
                List.of(claim(0, 0), claim(0, 0), claim(1, 0), claim(Integer.MAX_VALUE, 0)));

        assertEquals(1, TerritoryMapGeometry.visibleTerritories(snapshot).size());
        assertEquals(1, TerritoryMapGeometry.regions(snapshot).size());
    }

    @Test
    void dimensionScopedIdsAreStableAndDifferentForTheSameCoordinates() {
        var overworld = TerritoryMapGeometry.regions(snapshot("minecraft:overworld", claim(0, 0)));
        var same = TerritoryMapGeometry.regions(snapshot("minecraft:overworld", claim(0, 0)));
        var nether = TerritoryMapGeometry.regions(snapshot("minecraft:the_nether", claim(0, 0)));

        assertEquals(overworld, same);
        assertNotEquals(overworld.get(0).id(), nether.get(0).id());
        assertEquals("minecraft:the_nether", nether.get(0).dimension());
    }

    @Test
    void maximumWindowRemainsBoundedAndEmptySnapshotProducesNoRegions() {
        List<Territory> claims = new ArrayList<>();
        for (int z = -8; z <= 8; z++) {
            for (int x = -8; x <= 8; x++) {
                claims.add(claim(x, z));
            }
        }
        var regions = TerritoryMapGeometry.regions(new TerritorySnapshot("minecraft:overworld", 0, 0, 8, claims));

        assertEquals(1, regions.size());
        assertEquals(-128, regions.get(0).minX());
        assertEquals(144, regions.get(0).maxX());
        assertEquals(-128, regions.get(0).minZ());
        assertEquals(144, regions.get(0).maxZ());
        assertTrue(TerritoryMapGeometry.regions(TerritorySnapshot.EMPTY).isEmpty());
    }

    @Test
    void overlayLabelsHideIdentifiersButDifferentOwnersStillKeepTheirOwnBoundaries() {
        String first = "11111111-2222-3333-4444-555555555555";
        String second = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var snapshot = snapshot("minecraft:overworld",
                new Territory(0, 0, first, first, first, "Nation", "State", "City", "player:" + first, 1, 0, "Alice"),
                new Territory(1, 0, first, first, first, "Nation", "State", "City", "player:" + second, 1, 0, "Alice"));
        var regions = TerritoryMapGeometry.regions(snapshot);
        assertEquals(2, regions.size());
        for (var region : regions) {
            assertTrue(region.description().contains("Alice"));
            assertFalse(region.description().contains(first));
            assertFalse(region.description().contains(second));
            assertFalse(region.label().contains(first));
        }
    }

    private static Territory claim(int x, int z) {
        return claim(x, z, "owner", 0x336699);
    }

    private static Territory claim(int x, int z, String owner, int color) {
        return new Territory(x, z, "nation", "state", "city", "Nation", "State", "City", owner, color, 0, "Alice");
    }

    private static TerritorySnapshot snapshot(String dimension, Territory... territories) {
        return new TerritorySnapshot(dimension, 0, 0, 8, List.of(territories));
    }
}
