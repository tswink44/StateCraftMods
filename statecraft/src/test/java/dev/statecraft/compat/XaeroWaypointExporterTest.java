package dev.statecraft.compat;

import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.TerritorySnapshot.Territory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class XaeroWaypointExporterTest {
    @TempDir(factory = ProjectDirectoryFactory.class)
    Path directory;

    @Test
    void exportsNegativeChunkCenterInVerifiedTwelveFieldFormat() throws IOException {
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:overworld", -2, 3, 0,
                List.of(city(-2, 3, "harbor", "Harbor")));

        Path output = XaeroWaypointExporter.exportXaero(snapshot, directory);
        String[] fields = waypointLines(output).get(0).split(":", -1);

        assertEquals(directory.toAbsolutePath().normalize(), output.getParent());
        assertEquals(12, fields.length);
        assertEquals("waypoint", fields[0]);
        assertTrue(fields[1].matches("Harbor \\[SC-[0-9a-f]{16}]"));
        assertEquals("SC", fields[2]);
        assertEquals("-24", fields[3]);
        assertEquals("64", fields[4]);
        assertEquals("56", fields[5]);
        assertArrayEquals(new String[]{"11", "false", "0", "gui.xaero_default", "false", "0"},
                java.util.Arrays.copyOfRange(fields, 6, 12));
    }

    @Test
    void sanitizesLabelsAndNeverInterpretsCityIdsAsPathsOrRecords() throws IOException {
        String name = "Port:\r\nwaypoint:injected\u2028name\t\u202e\u00a7c\u6e2f\ud800";
        TerritorySnapshot snapshot = snapshot("minecraft:overworld",
                city(0, 0, "../../bad:id\nwaypoint:bad", name));

        Path output = XaeroWaypointExporter.exportXaero(snapshot, directory);
        List<String> lines = waypointLines(output);

        assertEquals(1, lines.size());
        String[] fields = lines.get(0).split(":", -1);
        assertEquals(12, fields.length);
        assertTrue(fields[1].startsWith("Port_ waypoint_injected name "));
        assertTrue(fields[1].chars().allMatch(character -> character >= 32 && character < 127));
        assertFalse(Files.readString(output).contains("../../bad"));
        try (var children = Files.list(directory)) {
            assertEquals(List.of(output), children.toList());
        }
    }

    @Test
    void dimensionNamesCannotTraverseOrCollideAfterSanitizing() throws IOException {
        Path first = XaeroWaypointExporter.exportXaero(snapshot("mod:../../world/nested",
                city(0, 0, "c", "City")), directory);
        Path second = XaeroWaypointExporter.exportXaero(snapshot("mod:.._.._world_nested",
                city(0, 0, "c", "City")), directory);

        assertNotEquals(first, second);
        for (Path output : List.of(first, second)) {
            assertEquals(directory.toAbsolutePath().normalize(), output.getParent());
            assertTrue(output.getFileName().toString().matches("[a-z0-9_-]+\\.txt"));
            assertFalse(output.getFileName().toString().contains(".."));
        }
        assertTrue(Files.readString(first).contains("# Dimension: mod:../../world/nested\n"));
    }

    @Test
    void veryLongDimensionsStillProduceBoundedFileNames() throws IOException {
        Path output = XaeroWaypointExporter.exportXaero(snapshot("mod:" + "deep/".repeat(100) + "world",
                city(0, 0, "c", "City")), directory);

        assertTrue(output.getFileName().toString().length() < 150);
        assertEquals(1, waypointLines(output).size());
    }

    @Test
    void selectsTheClaimedChunkNearestTheVisibleCityCentroid() throws IOException {
        TerritorySnapshot snapshot = snapshot("minecraft:overworld",
                city(2, 0, "c", "City"), city(0, 0, "c", "City"), city(1, 0, "c", "City"));

        List<String> lines = waypointLines(XaeroWaypointExporter.exportXaero(snapshot, directory));

        assertEquals(1, lines.size());
        String[] fields = lines.get(0).split(":");
        assertEquals("24", fields[3]);
        assertEquals("8", fields[5]);
    }

    @Test
    void equalDistanceUsesDeterministicChunkCoordinatesAndExportIsReproducible() throws IOException {
        Territory left = city(-1, 0, "c", "City");
        Territory right = city(1, 0, "c", "City");
        Path first = XaeroWaypointExporter.exportXaero(snapshot("minecraft:overworld", right, left), directory);
        String original = Files.readString(first);

        Path second = XaeroWaypointExporter.exportXaero(snapshot("minecraft:overworld", left, right), directory);

        assertEquals(first, second);
        assertEquals(original, Files.readString(second));
        assertEquals("-8", waypointLines(second).get(0).split(":")[3]);
    }

    @Test
    void differentRawCityIdsRemainDistinctEvenWithIdenticalSanitizedNames() throws IOException {
        Path output = XaeroWaypointExporter.exportXaero(snapshot("minecraft:overworld",
                city(0, 0, "a:b", "Same"), city(1, 0, "a_b", "Same")), directory);
        List<String> names = waypointLines(output).stream().map(line -> line.split(":")[1]).toList();

        assertEquals(2, names.size());
        assertNotEquals(names.get(0), names.get(1));
    }

    @Test
    void claimsWithoutACityAndOutsideTheSnapshotAreNotExported() throws IOException {
        TerritorySnapshot snapshot = new TerritorySnapshot("minecraft:overworld", 0, 0, 0,
                List.of(city(0, 0, "", "Not a city"), city(1, 0, "far", "Outside")));

        assertTrue(waypointLines(XaeroWaypointExporter.exportXaero(snapshot, directory)).isEmpty());
    }

    @Test
    void reexportReplacesStaleWaypointsAndEmptySnapshotClearsThisDimensionExport() throws IOException {
        Path first = XaeroWaypointExporter.exportXaero(snapshot("minecraft:overworld",
                city(0, 0, "old", "Old")), directory);
        Path replacement = XaeroWaypointExporter.exportXaero(snapshot("minecraft:overworld",
                city(1, 0, "new", "New")), directory);

        assertEquals(first, replacement);
        assertEquals(1, waypointLines(replacement).size());
        assertFalse(Files.readString(replacement).contains("waypoint:Old"));

        Path empty = XaeroWaypointExporter.exportXaero(TerritorySnapshot.EMPTY, directory);
        assertEquals(first, empty);
        assertTrue(waypointLines(empty).isEmpty());
        try (var children = Files.list(directory)) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void exportsForOtherDimensionsAreNotOverwritten() throws IOException {
        Path nether = XaeroWaypointExporter.exportXaero(snapshot("minecraft:the_nether",
                city(0, 0, "n", "Nether")), directory);
        String original = Files.readString(nether);

        XaeroWaypointExporter.exportXaero(TerritorySnapshot.EMPTY, directory);

        assertEquals(original, Files.readString(nether));
    }

    @Test
    void refusesSymbolicLinkOutputWithoutModifyingItsTarget() throws IOException {
        TerritorySnapshot snapshot = snapshot("minecraft:overworld", city(0, 0, "c", "City"));
        Path exports = directory.resolve("exports");
        Path output = XaeroWaypointExporter.exportXaero(snapshot, exports);
        Path unrelated = directory.resolve("unrelated.txt");
        Files.writeString(unrelated, "unchanged");
        Files.delete(output);
        try {
            Files.createSymbolicLink(output, unrelated.toAbsolutePath());
        } catch (IOException | UnsupportedOperationException unavailable) {
            assumeTrue(false, "Symbolic links are not available for this test account.");
            return;
        }

        assertThrows(IOException.class, () -> XaeroWaypointExporter.exportXaero(snapshot, exports));
        assertEquals("unchanged", Files.readString(unrelated));
        assertTrue(Files.isSymbolicLink(output));
    }

    private static Territory city(int x, int z, String id, String name) {
        return new Territory(x, z, "nation", "state", id, "Nation", "State", name,
                "player:owner", 0xff336699, 0);
    }

    private static TerritorySnapshot snapshot(String dimension, Territory... territories) {
        return new TerritorySnapshot(dimension, 0, 0, 8, List.of(territories));
    }

    private static List<String> waypointLines(Path file) throws IOException {
        return Files.readAllLines(file).stream().filter(line -> line.startsWith("waypoint:")).toList();
    }

    public static final class ProjectDirectoryFactory implements TempDirFactory {
        @Override
        public Path createTempDirectory(AnnotatedElementContext elementContext, ExtensionContext extensionContext)
                throws IOException {
            Path root = Files.createDirectories(Path.of("build", "statecraft-compat-tests"));
            return Files.createDirectory(root.resolve(UUID.randomUUID().toString())).toAbsolutePath();
        }
    }
}
