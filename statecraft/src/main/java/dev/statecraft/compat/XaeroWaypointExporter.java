package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.TerritorySnapshot.Territory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

public final class XaeroWaypointExporter {
    private XaeroWaypointExporter() {}

    /**
     * Explicitly exports nearby city reference points into a caller-selected StateCraft-owned directory.
     * Does not locate or modify Xaero's data. Points use the center of a visible city chunk; Y=64
     * is a placeholder because the snapshot contains neither heights nor canonical city centers.
     *
     * @return the dimension-specific UTF-8 file, replacing this directory's previous export for that dimension
     */
    public static Path exportXaero(TerritorySnapshot snapshot, Path destinationDirectory) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(destinationDirectory, "destinationDirectory");
        String contents = serialize(snapshot);
        Path directory = destinationDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve("statecraft-xaero-" + MapText.dimensionFileToken(snapshot.dimension()) + ".txt");
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to replace a symbolic-link waypoint export.");
        }
        Path pending = directory.resolve(".statecraft-waypoints-" + UUID.randomUUID() + ".writing");
        boolean created = false;
        try {
            try (var writer = Files.newBufferedWriter(pending, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                writer.write(contents);
            }
            try {
                Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (created) {
                Files.deleteIfExists(pending);
            }
        }
        return target;
    }

    private static String serialize(TerritorySnapshot snapshot) {
        Map<City, List<Territory>> cities = new TreeMap<>(Comparator.comparing(City::nationId)
                .thenComparing(City::stateId).thenComparing(City::cityId));
        for (Territory territory : TerritoryMapGeometry.visibleTerritories(snapshot)) {
            if (territory.cityId() == null || territory.cityId().isBlank()) {
                continue;
            }
            City city = new City(Objects.requireNonNullElse(territory.nationId(), ""),
                    Objects.requireNonNullElse(territory.stateId(), ""), territory.cityId());
            cities.computeIfAbsent(city, ignored -> new ArrayList<>()).add(territory);
        }
        StringBuilder contents = new StringBuilder("# StateCraft nearby-city waypoint export\n")
                .append("# Dimension: ").append(snapshot.dimension()).append('\n')
                .append("# Visible claims only; reference chunk centers, not city capitals. Y=64 is unknown height.\n")
                .append("# Import waypoint lines into the matching world AND dimension; do not use as teleport targets.\n");
        for (var entry : cities.entrySet()) {
            City city = entry.getKey();
            Territory center = representative(entry.getValue());
            String id = MapText.fingerprint(snapshot.dimension(), city.nationId(), city.stateId(), city.cityId())
                    .substring(0, 16);
            String name = MapText.waypointName(center.cityName()) + " [SC-" + id + "]";
            contents.append("waypoint:").append(name).append(":SC:")
                    .append(center.x() * 16 + 8).append(":64:").append(center.z() * 16 + 8)
                    .append(":11:false:0:gui.xaero_default:false:0\n");
        }
        return contents.toString();
    }

    private static Territory representative(List<Territory> territories) {
        long sumX = territories.stream().mapToLong(Territory::x).sum();
        long sumZ = territories.stream().mapToLong(Territory::z).sum();
        long size = territories.size();
        return territories.stream().min(Comparator
                .comparingLong((Territory territory) -> {
                    long dx = territory.x() * size - sumX;
                    long dz = territory.z() * size - sumZ;
                    return dx * dx + dz * dz;
                })
                .thenComparingInt(Territory::x).thenComparingInt(Territory::z)).orElseThrow();
    }

    private record City(String nationId, String stateId, String cityId) {}
}
