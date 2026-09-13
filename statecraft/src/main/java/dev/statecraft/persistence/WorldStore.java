package dev.statecraft.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class WorldStore {
    public static final int SCHEMA_VERSION = 1;
    private static final long MAX_FILE_BYTES = 256L * 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final Path directory;
    private final Path snapshot;
    private final Path previous;
    private final Runnable prepareSnapshot;
    private final Map<String, Object> live = new LinkedHashMap<>();
    private JsonObject sections = new JsonObject();
    private long revision;
    private boolean migrated;

    public WorldStore(Path worldDirectory) throws IOException {
        this(worldDirectory, () -> {});
    }

    public WorldStore(Path worldDirectory, Runnable prepareSnapshot) throws IOException {
        this.prepareSnapshot = java.util.Objects.requireNonNull(prepareSnapshot);
        directory = worldDirectory.resolve("statecraft");
        snapshot = directory.resolve("world.json");
        previous = directory.resolve("world.previous.json");
        Files.createDirectories(directory);
        if (Files.exists(snapshot)) {
            readSnapshot();
        } else if (Files.exists(previous)) {
            throw new IOException("StateCraft world.json is missing but a previous snapshot exists. "
                    + "Restore world.previous.json to world.json before starting; refusing to reset the world.");
        } else {
            migrateLegacySection("governance", directory.resolve("government.json"));
            migrateLegacySection("economy", directory.resolve("economy.json"));
        }
    }

    private void readSnapshot() throws IOException {
        JsonObject root = readObject(snapshot);
        try {
            if (!root.has("schemaVersion")
                    || root.get("schemaVersion").getAsBigDecimal().intValueExact() != SCHEMA_VERSION) {
                throw new IOException("Unsupported StateCraft snapshot schema in " + snapshot
                        + ". Keep the file intact and use the matching mod version.");
            }
            if (!root.has("sections") || !root.get("sections").isJsonObject()) {
                throw new IOException("Missing StateCraft data sections in " + snapshot);
            }
            sections = root.getAsJsonObject("sections").deepCopy();
            revision = root.get("revision").getAsBigDecimal().longValueExact();
            if (revision < 0 || revision == Long.MAX_VALUE) {
                throw new IOException("Invalid StateCraft snapshot revision.");
            }
        } catch (IllegalStateException | NullPointerException | NumberFormatException | ArithmeticException e) {
            throw new IOException("Invalid StateCraft snapshot header; restore a verified backup: " + snapshot, e);
        }
    }

    private void migrateLegacySection(String key, Path legacy) throws IOException {
        if (Files.exists(legacy)) {
            sections.add(key, readObject(legacy));
            migrated = true;
        }
    }

    private static JsonObject readObject(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("StateCraft data exceeds the 256 MiB safety limit: " + file);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IOException("Expected a JSON object in " + file);
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Cannot read StateCraft data " + file
                    + ". The file was not overwritten; restore a verified backup.", e);
        }
    }

    public synchronized <T> T load(String key, Class<T> type, Supplier<T> empty) throws IOException {
        if (!key.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid persistence section: " + key);
        }
        if (live.containsKey(key)) {
            return type.cast(live.get(key));
        }
        T data;
        try {
            if (sections.has(key) && !sections.get(key).isJsonObject()) {
                throw new IOException("Invalid StateCraft section '" + key + "'; refusing to reset it.");
            }
            data = sections.has(key) ? GSON.fromJson(sections.get(key), type) : empty.get();
            if (data == null) {
                throw new IOException("StateCraft section '" + key + "' is null.");
            }
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Cannot load StateCraft section '" + key + "'; data was not overwritten.", e);
        }
        live.put(key, data);
        return data;
    }

    public synchronized boolean save() throws IOException {
        prepareSnapshot.run();
        JsonObject nextSections = new JsonObject();
        // Stored JSON sections are immutable; live models replace their entries below.
        sections.entrySet().forEach(entry -> nextSections.add(entry.getKey(), entry.getValue()));
        live.forEach((key, value) -> nextSections.add(key, GSON.toJsonTree(value)));
        if (!migrated && Files.exists(snapshot) && nextSections.equals(sections)) {
            return false;
        }
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("revision", Math.addExact(revision, 1));
        root.addProperty("savedAt", Instant.now().toString());
        root.add("sections", nextSections);
        byte[] bytes = (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("StateCraft snapshot exceeds the 256 MiB safety limit.");
        }
        Path temporary = directory.resolve("world-" + UUID.randomUUID() + ".tmp");
        Path previousTemporary = directory.resolve("previous-" + UUID.randomUUID() + ".tmp");
        try {
            writeDurably(temporary, bytes);
            if (Files.exists(snapshot)) {
                Files.copy(snapshot, previousTemporary);
                replace(previousTemporary, previous);
            }
            replace(temporary, snapshot);
            sections = nextSections;
            revision++;
            migrated = false;
        } finally {
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(previousTemporary);
        }
        return true;
    }

    public synchronized Path backup() throws IOException {
        save();
        Path backups = directory.resolve("backups");
        Files.createDirectories(backups);
        Path target = backups.resolve("world-" + BACKUP_TIME.format(Instant.now())
                + "-" + revision + ".json");
        if (!Files.exists(target)) {
            Files.copy(snapshot, target);
        }
        try (Stream<Path> stream = Files.list(backups)) {
            List<Path> files = stream.filter(p -> p.getFileName().toString()
                            .matches("world-[0-9]{8}-[0-9]{6}-[0-9]{3}-[0-9]+\\.json"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed()).toList();
            for (Path old : files.stream().skip(10).toList()) {
                Files.delete(old);
            }
        }
        return target;
    }

    public synchronized Path backupSnapshot(String label) throws IOException {
        if (label == null || !label.matches("[a-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Invalid migration backup label.");
        }
        Path backups = directory.resolve("backups");
        Files.createDirectories(backups);
        Path target = backups.resolve("before-" + label + "-" + BACKUP_TIME.format(Instant.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".json");
        Files.copy(snapshot, target);
        return target;
    }

    public static void writeDurably(Path target, byte[] bytes) throws IOException {
        try (FileChannel file = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
    }

    public static void replace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path directory() {
        return directory;
    }

    public long revision() {
        return revision;
    }

    public boolean migrated() {
        return migrated;
    }
}
