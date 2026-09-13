package dev.statecraft.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BoundaryEdges {
    public enum Mode {
        OFF("Off"), CHUNK("Chunks"), CITY("Cities"), STATE("States"), NATION("Nations");
        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return label; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public record Edge(int x1, int z1, int x2, int z2, int color) {}
    private record Coordinate(int x, int z) {}
    private record Segment(int x1, int z1, int x2, int z2) {}

    private BoundaryEdges() {}

    public static List<Edge> of(TerritorySnapshot snapshot, Mode mode) {
        if (mode == Mode.OFF) {
            return List.of();
        }
        Map<Coordinate, TerritorySnapshot.Territory> chunks = new LinkedHashMap<>();
        snapshot.territories().stream().sorted(java.util.Comparator.comparingInt(TerritorySnapshot.Territory::x)
                        .thenComparingInt(TerritorySnapshot.Territory::z))
                .forEach(t -> chunks.put(new Coordinate(t.x(), t.z()), t));
        Map<Segment, Edge> edges = new LinkedHashMap<>();
        for (TerritorySnapshot.Territory territory : chunks.values()) {
            int x = territory.x() * 16;
            int z = territory.z() * 16;
            if (boundaryAt(snapshot, chunks, territory, territory.x() - 1, territory.z(), mode)) {
                add(edges, x, z, x, z + 16, territory.color());
            }
            if (boundaryAt(snapshot, chunks, territory, territory.x() + 1, territory.z(), mode)) {
                add(edges, x + 16, z, x + 16, z + 16, territory.color());
            }
            if (boundaryAt(snapshot, chunks, territory, territory.x(), territory.z() - 1, mode)) {
                add(edges, x, z, x + 16, z, territory.color());
            }
            if (boundaryAt(snapshot, chunks, territory, territory.x(), territory.z() + 1, mode)) {
                add(edges, x, z + 16, x + 16, z + 16, territory.color());
            }
        }
        return new ArrayList<>(edges.values());
    }

    private static void add(Map<Segment, Edge> edges, int x1, int z1, int x2, int z2, int color) {
        edges.putIfAbsent(new Segment(x1, z1, x2, z2), new Edge(x1, z1, x2, z2, color));
    }

    private static boolean boundaryAt(TerritorySnapshot snapshot, Map<Coordinate, TerritorySnapshot.Territory> chunks,
                                      TerritorySnapshot.Territory territory, int x, int z, Mode mode) {
        if (Math.abs((long) x - snapshot.centerX()) > snapshot.radius()
                || Math.abs((long) z - snapshot.centerZ()) > snapshot.radius()) {
            return false;
        }
        return boundary(territory, chunks.get(new Coordinate(x, z)), mode);
    }

    private static boolean boundary(TerritorySnapshot.Territory first, TerritorySnapshot.Territory other, Mode mode) {
        if (other == null || mode == Mode.CHUNK) {
            return true;
        }
        return switch (mode) {
            case CITY -> !first.cityId().equals(other.cityId());
            case STATE -> !first.stateId().equals(other.stateId());
            case NATION -> !first.nationId().equals(other.nationId());
            default -> false;
        };
    }
}
