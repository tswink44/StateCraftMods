package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.TerritorySnapshot.Territory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

final class TerritoryMapGeometry {
    private static final int MAX_CHUNK_COORDINATE = 1_875_000;

    private TerritoryMapGeometry() {}

    static List<Territory> visibleTerritories(TerritorySnapshot snapshot) {
        return List.copyOf(cells(snapshot).values());
    }

    private static TreeMap<Cell, Territory> cells(TerritorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        TreeMap<Cell, Territory> result = new TreeMap<>();
        for (Territory territory : snapshot.territories()) {
            if (Math.abs((long) territory.x() - snapshot.centerX()) <= snapshot.radius()
                    && Math.abs((long) territory.z() - snapshot.centerZ()) <= snapshot.radius()
                    && Math.abs((long) territory.x()) <= MAX_CHUNK_COORDINATE
                    && Math.abs((long) territory.z()) <= MAX_CHUNK_COORDINATE) {
                result.put(new Cell(territory.x(), territory.z()), territory);
            }
        }
        return result;
    }

    static List<Region> regions(TerritorySnapshot snapshot) {
        TreeMap<Cell, Territory> remaining = cells(snapshot);
        List<Region> regions = new ArrayList<>();
        String dimensionId = MapText.fingerprint(snapshot.dimension());
        while (!remaining.isEmpty()) {
            var first = remaining.firstEntry();
            Cell start = first.getKey();
            Territory territory = first.getValue();
            Style style = Style.of(territory);
            int east = start.x();
            while (matches(remaining, east + 1, start.z(), style)) {
                east++;
            }
            int south = start.z();
            while (rowMatches(remaining, start.x(), east, south + 1, style)) {
                south++;
            }
            for (int z = start.z(); z <= south; z++) {
                for (int x = start.x(); x <= east; x++) {
                    remaining.remove(new Cell(x, z));
                }
            }
            String id = "territory-" + dimensionId + "-" + start.x() + "-" + start.z()
                    + "-" + east + "-" + south;
            regions.add(new Region(id, snapshot.dimension(), start.x() * 16, start.z() * 16,
                    (east + 1) * 16, (south + 1) * 16, territory.color() & 0xffffff,
                    MapText.label(territory), MapText.description(territory)));
        }
        return List.copyOf(regions);
    }

    private static boolean rowMatches(TreeMap<Cell, Territory> cells, int west, int east, int z, Style style) {
        for (int x = west; x <= east; x++) {
            if (!matches(cells, x, z, style)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(TreeMap<Cell, Territory> cells, int x, int z, Style style) {
        Territory territory = cells.get(new Cell(x, z));
        return territory != null && style.equals(Style.of(territory));
    }

    record Point(int x, int z) {}

    record Region(String id, String dimension, int minX, int minZ, int maxX, int maxZ,
                  int color, String label, String description) {
        List<Point> corners() {
            // Counterclockwise in JourneyMap's X-right/Z-down map plane.
            return List.of(new Point(maxX, maxZ), new Point(maxX, minZ),
                    new Point(minX, minZ), new Point(minX, maxZ));
        }
    }

    private record Cell(int x, int z) implements Comparable<Cell> {
        @Override
        public int compareTo(Cell other) {
            int row = Integer.compare(z, other.z);
            return row != 0 ? row : Integer.compare(x, other.x);
        }
    }

    private record Style(String nationId, String stateId, String cityId, String nationName,
                         String stateName, String cityName, String owner, int color) {
        static Style of(Territory territory) {
            return new Style(territory.nationId(), territory.stateId(), territory.cityId(),
                    territory.nationName(), territory.stateName(), territory.cityName(),
                    territory.ownerAccount(), territory.color() & 0xffffff);
        }
    }
}
