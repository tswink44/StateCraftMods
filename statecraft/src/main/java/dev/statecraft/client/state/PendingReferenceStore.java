package dev.statecraft.client.state;

import dev.statecraft.api.ui.OperationRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PendingReferenceStore {
    private static final String HEADER = "STATECRAFT_PENDING_V1";
    private static final long MAX_BYTES = 16_384;
    private final Path directory;

    public PendingReferenceStore(Path directory) { this.directory = directory; }
    public Path path(UiScope scope) {
        return directory.resolve(scope.world().toString()).resolve(scope.player() + ".pending");
    }
    public List<OperationRef> read(UiScope scope) throws IOException {
        Path file = path(scope);
        if (!Files.exists(file)) return List.of();
        if (Files.size(file) > MAX_BYTES) throw new IOException("Recovery reference file is too large.");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() < 3 || !HEADER.equals(lines.get(0)) || !scope.world().toString().equals(lines.get(1))
                || !scope.player().toString().equals(lines.get(2)) || lines.size() > PendingOperations.MAX_PENDING + 3) {
            throw new IOException("Recovery reference file has an invalid scope or format.");
        }
        var operations = new ArrayList<OperationRef>();
        try {
            for (int i = 3; i < lines.size(); i++) {
                String value = lines.get(i);
                UUID id = UUID.fromString(value);
                if (!id.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID.");
                OperationRef ref = new OperationRef(scope.world(), id);
                if (!ref.present() || operations.contains(ref)) throw new IllegalArgumentException("Duplicate reference.");
                operations.add(ref);
            }
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Recovery reference file contains an invalid operation.", invalid);
        }
        return List.copyOf(operations);
    }

    public void write(UiScope scope, List<OperationRef> operations) throws IOException {
        if (operations.size() > PendingOperations.MAX_PENDING
                || operations.stream().anyMatch(ref -> !ref.present() || !scope.world().equals(ref.world()))) {
            throw new IllegalArgumentException("Invalid pending references.");
        }
        Path file = path(scope);
        if (operations.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        StringBuilder data = new StringBuilder(HEADER).append('\n').append(scope.world()).append('\n')
                .append(scope.player()).append('\n');
        operations.stream().distinct().map(ref -> ref.id().toString()).sorted()
                .forEach(id -> data.append(id).append('\n'));
        Path replacement = file.resolveSibling(file.getFileName() + ".new");
        try {
            try (FileChannel channel = FileChannel.open(replacement, StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(data.toString());
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(replacement, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(replacement, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(replacement);
        }
    }
}
