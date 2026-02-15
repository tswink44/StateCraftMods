package com.statecraft.economy.valuation;

import com.google.gson.*;
import com.statecraft.economy.StateCraftEconomy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks "improvement scores" per chunk as a proxy for block scanning.
 * Listens to block place/break events and adjusts scores accordingly.
 * This is O(1) per block interaction vs O(n) full chunk scans.
 */
public class ImprovementTracker {

    private static ImprovementTracker instance;

    // Map of chunk key -> improvement score
    private final Map<String, Integer> improvementScores = new ConcurrentHashMap<>();

    // Block categories and their score values
    private static final Set<Block> NATURAL_BLOCKS = Set.of(
        Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.DEEPSLATE,
        Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND, Blocks.CLAY,
        Blocks.WATER, Blocks.LAVA, Blocks.BEDROCK, Blocks.NETHERRACK,
        Blocks.END_STONE, Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR,
        Blocks.SNOW, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE,
        Blocks.PODZOL, Blocks.MYCELIUM, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
        Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.MOSS_BLOCK,
        Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE, Blocks.CALCITE,
        Blocks.TUFF, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE,
        Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.BASALT, Blocks.BLACKSTONE,
        Blocks.MAGMA_BLOCK, Blocks.GLOWSTONE, Blocks.OBSIDIAN
    );

    // Higher value blocks get more points
    private static final Map<Block, Integer> SPECIAL_BLOCK_SCORES = new HashMap<>();

    static {
        // Tier 3 - Advanced (5 points)
        SPECIAL_BLOCK_SCORES.put(Blocks.IRON_BLOCK, 5);
        SPECIAL_BLOCK_SCORES.put(Blocks.COPPER_BLOCK, 5);
        SPECIAL_BLOCK_SCORES.put(Blocks.LAPIS_BLOCK, 5);
        SPECIAL_BLOCK_SCORES.put(Blocks.REDSTONE_BLOCK, 5);

        // Tier 4 - Luxury (10 points)
        SPECIAL_BLOCK_SCORES.put(Blocks.GOLD_BLOCK, 10);
        SPECIAL_BLOCK_SCORES.put(Blocks.QUARTZ_BLOCK, 10);
        SPECIAL_BLOCK_SCORES.put(Blocks.PRISMARINE, 10);
        SPECIAL_BLOCK_SCORES.put(Blocks.SEA_LANTERN, 10);

        // Tier 5 - Premium (25 points)
        SPECIAL_BLOCK_SCORES.put(Blocks.DIAMOND_BLOCK, 25);
        SPECIAL_BLOCK_SCORES.put(Blocks.EMERALD_BLOCK, 25);
        SPECIAL_BLOCK_SCORES.put(Blocks.NETHERITE_BLOCK, 50);
        SPECIAL_BLOCK_SCORES.put(Blocks.BEACON, 50);
        SPECIAL_BLOCK_SCORES.put(Blocks.CONDUIT, 50);
    }

    private Path savePath;
    private boolean dirty = false;
    private MinecraftServer server;

    // Set to track chunks we've already scanned (to avoid repeated scans)
    private final Set<String> scannedChunks = ConcurrentHashMap.newKeySet();

    private ImprovementTracker() {}

    public static ImprovementTracker getInstance() {
        if (instance == null) {
            instance = new ImprovementTracker();
        }
        return instance;
    }

    /**
     * Initialize with save path
     */
    public void init(MinecraftServer server) {
        this.server = server;
        savePath = server.getWorldPath(LevelResource.ROOT).resolve("statecraft_improvements.json");
        load();
    }

    /**
     * Get the improvement score for a chunk.
     * If the chunk hasn't been scanned yet and server is available, scan it first.
     */
    public int getScore(int chunkX, int chunkZ, String dimension) {
        String key = ChunkValuation.makeKey(chunkX, chunkZ, dimension);

        // If we don't have a score and haven't scanned this chunk yet, scan it now
        if (!improvementScores.containsKey(key) && !scannedChunks.contains(key) && server != null) {
            scannedChunks.add(key); // Mark as scanned to avoid repeated scans
            return scanChunk(server, chunkX, chunkZ, dimension);
        }

        return improvementScores.getOrDefault(key, 0);
    }

    /**
     * Get the improvement score without triggering a scan
     */
    public int getScoreNoScan(int chunkX, int chunkZ, String dimension) {
        String key = ChunkValuation.makeKey(chunkX, chunkZ, dimension);
        return improvementScores.getOrDefault(key, 0);
    }

    /**
     * Scan a chunk and calculate its improvement score based on existing blocks.
     * This is an O(n) operation where n = number of blocks in the chunk.
     * Should be used sparingly, e.g., when first claiming a chunk or on admin command.
     *
     * @param server The Minecraft server
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param dimension Dimension string
     * @return The calculated improvement score
     */
    public int scanChunk(MinecraftServer server, int chunkX, int chunkZ, String dimension) {
        // Find the correct level
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(dimension)) {
                level = l;
                break;
            }
        }

        if (level == null) {
            StateCraftEconomy.LOGGER.warn("Could not find level for dimension: {}", dimension);
            return 0;
        }

        int score = 0;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // Calculate block coordinates for the chunk
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        // Scan all blocks in the chunk
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();

                    int blockScore = getBlockScore(block);
                    if (blockScore > 0) {
                        score += blockScore;
                    }
                }
            }
        }

        // Update the cached score
        String key = ChunkValuation.makeKey(chunkX, chunkZ, dimension);
        if (score > 0) {
            improvementScores.put(key, score);
        } else {
            improvementScores.remove(key);
        }
        dirty = true;

        StateCraftEconomy.LOGGER.info("Scanned chunk ({}, {}) in {}: improvement score = {}",
            chunkX, chunkZ, dimension, score);

        // Mark valuation as dirty so it gets recalculated
        ChunkValuationManager.getInstance().markDirty(chunkX, chunkZ, dimension);

        return score;
    }

    /**
     * Scan a chunk and update its score (convenience method for ServerLevel)
     */
    public int scanChunk(ServerLevel level, int chunkX, int chunkZ) {
        return scanChunk(level.getServer(), chunkX, chunkZ, level.dimension().location().toString());
    }

    /**
     * Set the improvement score for a chunk (used when loading)
     */
    public void setScore(int chunkX, int chunkZ, String dimension, int score) {
        String key = ChunkValuation.makeKey(chunkX, chunkZ, dimension);
        if (score <= 0) {
            improvementScores.remove(key);
        } else {
            improvementScores.put(key, score);
        }
        dirty = true;
    }

    /**
     * Calculate the score value for a block
     */
    private int getBlockScore(Block block) {
        // Natural blocks don't count
        if (NATURAL_BLOCKS.contains(block)) {
            return 0;
        }

        // Check for special high-value blocks
        if (SPECIAL_BLOCK_SCORES.containsKey(block)) {
            return SPECIAL_BLOCK_SCORES.get(block);
        }

        // Default: 1 point for any non-natural block
        return 1;
    }

    /**
     * Handle block placement
     */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);
        String dimension = level.dimension().location().toString();
        String key = ChunkValuation.makeKey(chunkPos.x, chunkPos.z, dimension);

        Block block = event.getPlacedBlock().getBlock();
        int score = getBlockScore(block);

        if (score > 0) {
            improvementScores.merge(key, score, Integer::sum);
            dirty = true;

            // Mark valuation as dirty
            ChunkValuationManager.getInstance().markDirty(chunkPos.x, chunkPos.z, dimension);
        }
    }

    /**
     * Handle block breaking
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        ChunkPos chunkPos = new ChunkPos(pos);
        String dimension = level.dimension().location().toString();
        String key = ChunkValuation.makeKey(chunkPos.x, chunkPos.z, dimension);

        Block block = event.getState().getBlock();
        int score = getBlockScore(block);

        if (score > 0) {
            improvementScores.computeIfPresent(key, (k, v) -> {
                int newVal = v - score;
                return newVal <= 0 ? null : newVal;
            });
            dirty = true;

            // Mark valuation as dirty
            ChunkValuationManager.getInstance().markDirty(chunkPos.x, chunkPos.z, dimension);
        }
    }

    /**
     * Save to file
     */
    public void save() {
        if (!dirty || savePath == null) return;

        try {
            JsonObject root = new JsonObject();
            JsonObject scores = new JsonObject();

            for (Map.Entry<String, Integer> entry : improvementScores.entrySet()) {
                scores.addProperty(entry.getKey(), entry.getValue());
            }

            root.add("scores", scores);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(savePath, gson.toJson(root));

            dirty = false;
            StateCraftEconomy.LOGGER.debug("Saved improvement scores for {} chunks", improvementScores.size());
        } catch (IOException e) {
            StateCraftEconomy.LOGGER.error("Failed to save improvement scores", e);
        }
    }

    /**
     * Load from file
     */
    public void load() {
        if (savePath == null || !Files.exists(savePath)) {
            StateCraftEconomy.LOGGER.info("No improvement data found, starting fresh");
            return;
        }

        try {
            String json = Files.readString(savePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("scores")) {
                JsonObject scores = root.getAsJsonObject("scores");
                improvementScores.clear();
                scannedChunks.clear();

                for (Map.Entry<String, JsonElement> entry : scores.entrySet()) {
                    improvementScores.put(entry.getKey(), entry.getValue().getAsInt());
                    scannedChunks.add(entry.getKey()); // Mark as already scanned
                }
            }

            StateCraftEconomy.LOGGER.info("Loaded improvement scores for {} chunks", improvementScores.size());
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Failed to load improvement scores", e);
        }
    }

    public boolean isDirty() {
        return dirty;
    }
}

