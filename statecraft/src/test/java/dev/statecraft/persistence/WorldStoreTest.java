package dev.statecraft.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldStoreTest {
    @TempDir Path world;

    public static final class Data {
        public Map<String, Long> balances = new LinkedHashMap<>();
    }

    @Test
    void snapshotPersistsAllModulesAndPreservesAnAbsentEconomy() throws IOException {
        WorldStore store = new WorldStore(world);
        Data core = store.load("governance", Data.class, Data::new);
        Data economy = store.load("economy", Data.class, Data::new);
        core.balances.put("government", 1L);
        economy.balances.put("player", 1999L);
        assertTrue(store.save());
        assertFalse(store.save());
        WorldStore standalone = new WorldStore(world);
        standalone.load("governance", Data.class, Data::new).balances.put("government", 2L);
        standalone.save();
        WorldStore both = new WorldStore(world);
        assertEquals(2L, both.load("governance", Data.class, Data::new).balances.get("government"));
        assertEquals(1999L, both.load("economy", Data.class, Data::new).balances.get("player"));
        assertTrue(Files.readString(world.resolve("statecraft").resolve("world.json")).contains("\n  \"schemaVersion\": 1"));
    }

    @Test
    void previousSnapshotAndManualBackupRemainRecoverable() throws IOException {
        WorldStore store = new WorldStore(world);
        Data data = store.load("governance", Data.class, Data::new);
        data.balances.put("a", 10L);
        store.save();
        data.balances.put("a", 20L);
        store.save();
        assertTrue(Files.readString(world.resolve("statecraft").resolve("world.previous.json")).contains("\"a\": 10"));
        Path backup = store.backup();
        assertTrue(Files.readString(backup).contains("\"a\": 20"));
        assertEquals(2, store.revision());
    }

    @Test
    void corruptOrMissingPrimaryIsNeverSilentlyReset() throws IOException {
        WorldStore store = new WorldStore(world);
        Data data = store.load("governance", Data.class, Data::new);
        store.save();
        data.balances.put("a", 1L);
        store.save();
        Path primary = world.resolve("statecraft").resolve("world.json");
        Files.writeString(primary, "{broken");
        assertThrows(IOException.class, () -> new WorldStore(world));
        assertEquals("{broken", Files.readString(primary));
        Files.delete(primary);
        assertThrows(IOException.class, () -> new WorldStore(world));
    }

    @Test
    void futureSchemaAndMalformedSectionsFailWithoutMutation() throws IOException {
        Path directory = world.resolve("statecraft");
        Files.createDirectories(directory);
        Path file = directory.resolve("world.json");
        String future = "{\"schemaVersion\":2,\"revision\":1,\"sections\":{}}";
        Files.writeString(file, future);
        assertThrows(IOException.class, () -> new WorldStore(world));
        assertEquals(future, Files.readString(file));
        Files.writeString(file, "{\"schemaVersion\":1,\"revision\":1,\"sections\":{\"governance\":null}}");
        WorldStore store = new WorldStore(world);
        assertThrows(IOException.class, () -> store.load("governance", Data.class, Data::new));
    }

    @Test
    void compatibleLegacyFilesAreWrappedWithoutDeletingOriginals() throws IOException {
        Path directory = world.resolve("statecraft");
        Files.createDirectories(directory);
        Path legacy = directory.resolve("government.json");
        Files.writeString(legacy, "{\"balances\":{\"legacy\":4}}");
        WorldStore store = new WorldStore(world);
        assertTrue(store.migrated());
        assertEquals(4L, store.load("governance", Data.class, Data::new).balances.get("legacy"));
        store.save();
        assertTrue(Files.exists(legacy));
        assertFalse(store.migrated());
        assertEquals(4L, new WorldStore(world).load("governance", Data.class, Data::new).balances.get("legacy"));
    }
}
