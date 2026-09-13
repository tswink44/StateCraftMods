package dev.statecraft.client.state;

import dev.statecraft.api.ui.OperationRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientPendingReferenceStoreTest {
    private static Path directory() { return Path.of("build", "client-state-tests", UUID.randomUUID().toString()); }
    private static void clean(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }

    @Test
    void diskPersistenceContainsOnlyVersionScopeAndOperationIdsNeverPrivateRequests() throws IOException {
        Path directory = directory();
        try {
            UiScope scope = new UiScope(UUID.randomUUID(), UUID.randomUUID());
            OperationRef operation = new OperationRef(scope.world(), UUID.randomUUID());
            PendingReferenceStore store = new PendingReferenceStore(directory);
            store.write(scope, List.of(operation));
            String text = Files.readString(store.path(scope));
            assertEquals(List.of("STATECRAFT_PENDING_V1", scope.world().toString(), scope.player().toString(), operation.id().toString()),
                    text.lines().toList());
            assertFalse(text.contains("command"));
            assertFalse(text.contains("mail"));
            assertFalse(text.contains("values"));
            assertEquals(List.of(operation), store.read(scope));
            assertFalse(Files.exists(store.path(scope).resolveSibling(scope.player() + ".pending.new")));
        } finally { clean(directory); }
    }

    @Test
    void anotherWorldOrPlayerCannotLoadOrEraseThisScopesReferences() throws IOException {
        Path directory = directory();
        try {
            UiScope scope = new UiScope(UUID.randomUUID(), UUID.randomUUID());
            UiScope player = new UiScope(scope.world(), UUID.randomUUID());
            UiScope world = new UiScope(UUID.randomUUID(), scope.player());
            PendingReferenceStore store = new PendingReferenceStore(directory);
            OperationRef reference = new OperationRef(scope.world(), UUID.randomUUID());
            store.write(scope, List.of(reference));
            assertTrue(store.read(player).isEmpty());
            assertTrue(store.read(world).isEmpty());
            store.write(player, List.of());
            assertEquals(List.of(reference), store.read(scope));
            assertThrows(IllegalArgumentException.class, () -> store.write(world, List.of(reference)));
            store.write(scope, List.of());
            assertFalse(Files.exists(store.path(scope)));
        } finally { clean(directory); }
    }

    @Test
    void malformedOrRelocatedRecoveryFilesFailClosedWithoutBeingOverwritten() throws IOException {
        Path directory = directory();
        try {
            UiScope scope = new UiScope(UUID.randomUUID(), UUID.randomUUID());
            PendingReferenceStore store = new PendingReferenceStore(directory);
            store.write(scope, List.of(new OperationRef(scope.world(), UUID.randomUUID())));
            Path file = store.path(scope);
            String original = Files.readString(file);
            String malformed = original.replace(scope.player().toString(), UUID.randomUUID().toString());
            Files.writeString(file, malformed);
            assertThrows(IOException.class, () -> store.read(scope));
            assertEquals(malformed, Files.readString(file));
            Files.writeString(file, original + "1-1-1-1-1\n");
            assertThrows(IOException.class, () -> store.read(scope));
            Files.writeString(file, "x".repeat(16_385));
            assertThrows(IOException.class, () -> store.read(scope));
        } finally { clean(directory); }
    }
}
