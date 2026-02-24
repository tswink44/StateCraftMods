package com.statecraft.data;

import com.statecraft.StateCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Automatic hourly backup of all StateCraft and StateCraft Economy data files.
 *
 * Backed up files:
 *   - world/data/statecraft_nations.dat
 *   - world/data/statecraft_economy_economy.dat
 *   - world/statecraft_mail.json
 *   - world/statecraft_valuations.json
 *
 * Backups are stored in: world/statecraft_backups/YYYY-MM-DD_HH-MM-SS/
 * Old backups beyond the retention limit are automatically pruned.
 */
public class BackupManager {

    private static final BackupManager INSTANCE = new BackupManager();

    /** How often to run a backup, in ticks (1 hour = 72000 ticks) */
    private static final long BACKUP_INTERVAL_TICKS = 72_000L;

    /** Maximum number of backup snapshots to keep */
    private static final int MAX_BACKUPS = 24; // 24 hours of hourly backups

    private static final String BACKUP_DIR = "statecraft_backups";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Files to back up, relative to the world root directory */
    private static final String[] BACKUP_FILES = {
        "data/statecraft_nations.dat",
        "data/statecraft_economy_economy.dat",
        "statecraft_mail.json",
        "statecraft_valuations.json"
    };

    private long tickCounter = 0;
    private boolean enabled = true;

    private BackupManager() {}

    public static BackupManager getInstance() {
        return INSTANCE;
    }

    /**
     * Called every server tick. Runs a backup when the interval has elapsed.
     */
    public void tick(MinecraftServer server) {
        if (!enabled) return;

        tickCounter++;
        if (tickCounter >= BACKUP_INTERVAL_TICKS) {
            tickCounter = 0;
            runBackup(server);
        }
    }

    /**
     * Run a backup immediately. Safe to call from any context.
     * @return true if the backup succeeded
     */
    public boolean runBackup(MinecraftServer server) {
        try {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            Path backupRoot = worldDir.resolve(BACKUP_DIR);
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            Path snapshotDir = backupRoot.resolve(timestamp);

            Files.createDirectories(snapshotDir);

            int filesCopied = 0;
            for (String relativePath : BACKUP_FILES) {
                Path source = worldDir.resolve(relativePath);
                if (Files.exists(source)) {
                    // Preserve subdirectory structure inside the snapshot
                    Path target = snapshotDir.resolve(relativePath);
                    Files.createDirectories(target.getParent());
                    // Copy with a buffer to avoid locking issues with .dat files
                    copyFileSafe(source, target);
                    filesCopied++;
                }
            }

            if (filesCopied > 0) {
                StateCraft.LOGGER.info("[Backup] Created backup '{}' ({} files)", timestamp, filesCopied);
            } else {
                StateCraft.LOGGER.warn("[Backup] No data files found to back up");
                // Remove empty snapshot directory
                Files.deleteIfExists(snapshotDir);
                return false;
            }

            // Prune old backups
            pruneOldBackups(backupRoot);

            return true;
        } catch (Exception e) {
            StateCraft.LOGGER.error("[Backup] Failed to create backup: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Copy a file safely, buffering through memory to minimise lock time on the source.
     */
    private void copyFileSafe(Path source, Path target) throws IOException {
        byte[] data;
        try (InputStream in = Files.newInputStream(source, StandardOpenOption.READ)) {
            data = in.readAllBytes();
        }
        try (OutputStream out = Files.newOutputStream(target,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(data);
        }
    }

    /**
     * Delete the oldest backup snapshots when we exceed MAX_BACKUPS.
     */
    private void pruneOldBackups(Path backupRoot) {
        try (Stream<Path> dirs = Files.list(backupRoot)) {
            List<Path> snapshots = dirs
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();

            int toRemove = snapshots.size() - MAX_BACKUPS;
            if (toRemove <= 0) return;

            for (int i = 0; i < toRemove; i++) {
                Path old = snapshots.get(i);
                deleteDirectory(old);
                StateCraft.LOGGER.info("[Backup] Pruned old backup '{}'", old.getFileName());
            }
        } catch (IOException e) {
            StateCraft.LOGGER.warn("[Backup] Failed to prune old backups: {}", e.getMessage());
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        StateCraft.LOGGER.warn("[Backup] Could not delete {}: {}", p, e.getMessage());
                    }
                });
        }
    }

    /**
     * Reset the tick counter (e.g. on world load so the first backup
     * happens one full interval after server start, not immediately).
     */
    public void reset() {
        tickCounter = 0;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getTicksUntilNextBackup() {
        return BACKUP_INTERVAL_TICKS - tickCounter;
    }
}

