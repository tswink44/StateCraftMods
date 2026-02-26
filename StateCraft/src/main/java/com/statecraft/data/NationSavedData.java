package com.statecraft.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.statecraft.StateCraft;
import com.statecraft.company.CompanyManager;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.*;
import com.statecraft.legislature.LegislatureManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Persists all nation data as JSON files in the world's statecraft/ directory.
 * Data is human-readable and independent from Minecraft's binary SavedData system.
 *
 * Migration: If the JSON file doesn't exist but the legacy .dat file does,
 * data is loaded from .dat and immediately saved as JSON.
 */
public class NationSavedData {
    private static final String JSON_DIR = "statecraft";
    private static final String JSON_FILE = "nations.json";
    private static final String LEGACY_DAT_DIR = "data";
    private static final String LEGACY_DAT_FILE = StateCraft.MOD_ID + "_nations.dat";

    private static NationSavedData instance;
    private boolean dirty = false;
    private Path savePath;

    private NationSavedData() {}

    public static NationSavedData getInstance() {
        if (instance == null) {
            instance = new NationSavedData();
        }
        return instance;
    }

    /**
     * Initialize and load data from JSON (or migrate from legacy NBT).
     */
    public static NationSavedData init(MinecraftServer server) {
        NationSavedData data = getInstance();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        data.savePath = worldDir.resolve(JSON_DIR).resolve(JSON_FILE);
        data.loadData(worldDir);
        return data;
    }

    /**
     * Backward-compatible entry point used by existing callers.
     * Delegates to init() on first call, returns cached instance thereafter.
     */
    public static NationSavedData get(ServerLevel level) {
        if (instance == null || instance.savePath == null) {
            return init(level.getServer());
        }
        return instance;
    }

    // ======================== Load ========================

    private void loadData(Path worldDir) {
        if (Files.exists(savePath)) {
            loadFromJson();
        } else {
            // Try legacy .dat migration
            Path legacyPath = worldDir.resolve(LEGACY_DAT_DIR).resolve(LEGACY_DAT_FILE);
            if (Files.exists(legacyPath)) {
                StateCraft.LOGGER.info("Migrating legacy NBT data to JSON...");
                loadFromLegacyDat(legacyPath);
                // Immediately save as JSON
                saveToJson();
                // Rename the old .dat so we don't re-migrate
                try {
                    Files.move(legacyPath, legacyPath.resolveSibling(LEGACY_DAT_FILE + ".migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
                    StateCraft.LOGGER.info("Legacy .dat file renamed to {}.migrated", LEGACY_DAT_FILE);
                } catch (IOException e) {
                    StateCraft.LOGGER.warn("Could not rename legacy .dat file: {}", e.getMessage());
                }
            } else {
                StateCraft.LOGGER.info("No existing StateCraft data found, starting fresh");
            }
        }
    }

    private void loadFromJson() {
        try {
            String json = Files.readString(savePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            CompoundTag tag = NbtJsonConverter.fromJson(root);
            loadFromNbt(tag);
            StateCraft.LOGGER.info("Loaded StateCraft data from JSON");
        } catch (Exception e) {
            StateCraft.LOGGER.error("Failed to load StateCraft data from JSON!", e);
        }
    }

    private void loadFromLegacyDat(Path datPath) {
        try (InputStream is = Files.newInputStream(datPath)) {
            CompoundTag root = NbtIo.readCompressed(is);
            // SavedData wraps content inside a "data" key
            CompoundTag tag = root.contains("data") ? root.getCompound("data") : root;
            loadFromNbt(tag);
            StateCraft.LOGGER.info("Loaded StateCraft data from legacy .dat file");
        } catch (Exception e) {
            StateCraft.LOGGER.error("Failed to load legacy .dat file!", e);
        }
    }

    /**
     * Core NBT loading logic — shared by both JSON and legacy paths.
     */
    private void loadFromNbt(CompoundTag tag) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        manager.clear();

        // Load all nations
        if (tag.contains("nations")) {
            ListTag nationsList = tag.getList("nations", Tag.TAG_COMPOUND);
            for (int i = 0; i < nationsList.size(); i++) {
                CompoundTag nationTag = nationsList.getCompound(i);
                Nation nation = Nation.load(nationTag);

                // Load chunk data with dimension context
                loadChunksForNation(nation, nationTag);

                manager.loadNation(nation);
            }
        }

        // Load invitations
        if (tag.contains("invitations")) {
            InvitationManager.getInstance().load(tag.getCompound("invitations"));
        }

        // Load election data
        if (tag.contains("elections")) {
            ElectionManager.getInstance().load(tag.getCompound("elections"));
        }

        // Load legislature data
        if (tag.contains("legislature")) {
            LegislatureManager.getInstance().load(tag.getCompound("legislature"));
            StateCraft.LOGGER.info("Loaded legislature data");
        }

        // Load contract data
        if (tag.contains("contracts")) {
            ContractManager.getInstance().load(tag.getCompound("contracts"));
            StateCraft.LOGGER.info("Loaded contract data");
        }

        // Load company data
        if (tag.contains("companies")) {
            CompanyManager.getInstance().load(tag.getCompound("companies"));
            StateCraft.LOGGER.info("Loaded company data");
        }

        // Load shareholder vote data
        if (tag.contains("shareholderVotes")) {
            com.statecraft.company.ShareholderVoteManager.getInstance().load(tag.getCompound("shareholderVotes"));
            StateCraft.LOGGER.info("Loaded shareholder vote data");
        }

        StateCraft.LOGGER.info("Loaded {} nations with {} total claimed chunks",
            manager.getTotalNationCount(), manager.getTotalClaimedChunks());

        // Run orphan cleanup to detect and remove stale/orphaned data
        manager.runOrphanCleanup();
    }

    private static void loadChunksForNation(Nation nation, CompoundTag nationTag) {
        if (!nationTag.contains("states")) return;

        ListTag statesList = nationTag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < statesList.size(); i++) {
            CompoundTag stateTag = statesList.getCompound(i);
            State state = nation.getStateByName(stateTag.getString("name"));
            if (state == null) continue;

            if (!stateTag.contains("cities")) continue;

            ListTag citiesList = stateTag.getList("cities", Tag.TAG_COMPOUND);
            for (int j = 0; j < citiesList.size(); j++) {
                CompoundTag cityTag = citiesList.getCompound(j);
                City city = state.getCityByName(cityTag.getString("name"));
                if (city == null) continue;

                if (!cityTag.contains("chunks")) continue;

                ListTag chunksList = cityTag.getList("chunks", Tag.TAG_COMPOUND);
                for (int k = 0; k < chunksList.size(); k++) {
                    CompoundTag chunkTag = chunksList.getCompound(k);
                    String dimensionStr = chunkTag.getString("dimension");
                    ResourceKey<Level> dimension = ResourceKey.create(
                        Registries.DIMENSION,
                        new ResourceLocation(dimensionStr)
                    );
                    ClaimedChunk chunk = ClaimedChunk.load(chunkTag, dimension);
                    city.loadChunk(chunk);
                }
            }
        }
    }

    // ======================== Save ========================

    /**
     * Save all data to JSON.
     */
    public void saveToJson() {
        if (savePath == null) return;

        try {
            CompoundTag tag = saveToNbt();
            JsonObject json = NbtJsonConverter.toJson(tag);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonStr = gson.toJson(json);

            // Atomic write: write to temp file first, then rename
            Files.createDirectories(savePath.getParent());
            Path tmpPath = savePath.resolveSibling(JSON_FILE + ".tmp");
            Files.writeString(tmpPath, jsonStr);
            try {
                Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpPath, savePath, StandardCopyOption.REPLACE_EXISTING);
            }

            clearDirty();
            StateCraft.LOGGER.debug("Saved StateCraft data to JSON");
        } catch (IOException e) {
            StateCraft.LOGGER.error("Failed to save StateCraft data to JSON!", e);
        }
    }

    /**
     * Core NBT save logic — builds the full CompoundTag from all managers.
     */
    @Nonnull
    private CompoundTag saveToNbt() {
        CompoundTag tag = new CompoundTag();
        ChunkClaimManager manager = ChunkClaimManager.getInstance();

        // Save all nations
        ListTag nationsList = new ListTag();
        for (Nation nation : manager.getAllNations()) {
            nationsList.add(nation.save());
        }
        tag.put("nations", nationsList);

        // Save invitations
        tag.put("invitations", InvitationManager.getInstance().save());

        // Save election data
        tag.put("elections", ElectionManager.getInstance().save());

        // Save legislature data
        tag.put("legislature", LegislatureManager.getInstance().save());

        // Save contract data
        tag.put("contracts", ContractManager.getInstance().save());

        // Save company data
        tag.put("companies", CompanyManager.getInstance().save());

        // Save shareholder vote data
        tag.put("shareholderVotes", com.statecraft.company.ShareholderVoteManager.getInstance().save());

        manager.clearDirty();
        InvitationManager.getInstance().clearDirty();
        ElectionManager.getInstance().clearDirty();
        LegislatureManager.getInstance().clearDirty();
        ContractManager.getInstance().clearDirty();
        CompanyManager.getInstance().clearDirty();
        com.statecraft.company.ShareholderVoteManager.getInstance().clearDirty();

        StateCraft.LOGGER.debug("Saved {} nations", manager.getTotalNationCount());

        return tag;
    }

    // ======================== Dirty tracking ========================

    public void markForSave() {
        this.dirty = true;
    }

    public boolean isDirtyCheck() {
        return dirty ||
            ChunkClaimManager.getInstance().isDirty() ||
            InvitationManager.getInstance().isDirty() ||
            LegislatureManager.getInstance().isDirty() ||
            ContractManager.getInstance().isDirty() ||
            CompanyManager.getInstance().isDirty();
    }

    private void clearDirty() {
        this.dirty = false;
    }

    /**
     * Save only if dirty.
     */
    public void saveIfDirty() {
        if (isDirtyCheck()) {
            saveToJson();
        }
    }

    /**
     * Reset the singleton (called on server stop).
     */
    public static void resetInstance() {
        instance = null;
    }
}
