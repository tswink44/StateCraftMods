package com.statecraft.economy.valuation;

import com.google.gson.*;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for chunk valuations.
 * Calculates and caches chunk values with JSON file persistence.
 */
public class ChunkValuationManager {

    private static ChunkValuationManager instance;

    // Cache of valuations
    private final Map<String, ChunkValuation> valuationCache = new ConcurrentHashMap<>();

    // Dirty chunks that need recalculation
    private final Set<String> dirtyChunks = ConcurrentHashMap.newKeySet();

    // Tick counters
    private long lastRecalcTick = 0;
    private long lastSaveTick = 0;

    private MinecraftServer server;
    private Path savePath;
    private boolean cacheLoaded = false;

    private ChunkValuationManager() {}

    public static ChunkValuationManager getInstance() {
        if (instance == null) {
            instance = new ChunkValuationManager();
        }
        return instance;
    }

    /**
     * Initialize with server
     */
    public void init(MinecraftServer server) {
        this.server = server;
        this.savePath = server.getWorldPath(LevelResource.ROOT).resolve("statecraft_valuations.json");
        loadCache();
        StateCraftEconomy.LOGGER.info("ChunkValuationManager initialized");
    }

    /**
     * Tick handler for periodic recalculation and saving
     */
    public void tick(MinecraftServer server) {
        if (this.server == null) {
            this.server = server;
        }

        long currentTick = server.overworld().getGameTime();

        // Recalculate dirty chunks periodically
        if (currentTick - lastRecalcTick >= ValuationConfig.getCacheRecalculationInterval()) {
            recalculateDirtyChunks();
            lastRecalcTick = currentTick;
        }

        // Save periodically
        if (currentTick - lastSaveTick >= ValuationConfig.getCacheSaveInterval()) {
            saveCache();
            lastSaveTick = currentTick;
        }
    }

    /**
     * Get valuation for a chunk (from cache or calculate)
     */
    public ChunkValuation getValuation(int chunkX, int chunkZ, String dimension) {
        String key = ChunkValuation.makeKey(chunkX, chunkZ, dimension);

        ChunkValuation valuation = valuationCache.get(key);

        if (valuation == null || valuation.isDirty()) {
            valuation = calculateValuation(chunkX, chunkZ, dimension);
            valuationCache.put(key, valuation);
            dirtyChunks.remove(key);
        }

        return valuation;
    }

    /**
     * Mark a chunk as needing recalculation
     */
    public void markDirty(int chunkX, int chunkZ, String dimension) {
        String key = ChunkValuation.makeKey(chunkX, chunkZ, dimension);
        ChunkValuation valuation = valuationCache.get(key);
        if (valuation != null) {
            valuation.setDirty(true);
        }
        dirtyChunks.add(key);
    }

    /**
     * Mark all cached valuations as dirty (e.g., when legislation changes base values)
     */
    public void markAllDirty() {
        for (ChunkValuation valuation : valuationCache.values()) {
            valuation.setDirty(true);
            dirtyChunks.add(ChunkValuation.makeKey(valuation.getChunkX(), valuation.getChunkZ(), valuation.getDimension()));
        }
        StateCraftEconomy.LOGGER.info("Marked {} chunk valuations as dirty for recalculation", valuationCache.size());
    }

    /**
     * Calculate a fresh valuation for a chunk
     */
    private ChunkValuation calculateValuation(int chunkX, int chunkZ, String dimension) {
        ChunkValuation valuation = new ChunkValuation(chunkX, chunkZ, dimension);

        // 1. Base value - use nation's setting if in a nation, otherwise use config default
        double baseValue = ValuationConfig.getBaseChunkValue(); // Default from config
        if (server != null) {
            double nationBaseValue = StateCraftIntegration.getNationBaseChunkValue(server, chunkX, chunkZ, dimension);
            if (nationBaseValue > 0) {
                baseValue = nationBaseValue; // Use nation's legislated base value
            }
        }
        valuation.setBaseValue(baseValue);

        // 2. Location multiplier
        double locationMult = calculateLocationMultiplier(chunkX, chunkZ);
        valuation.setLocationMultiplier(locationMult);

        // 3. Biome multiplier
        BiomeResult biomeResult = calculateBiomeMultiplier(chunkX, chunkZ, dimension);
        valuation.setBiomeMultiplier(biomeResult.multiplier);
        valuation.setBiomeName(biomeResult.biomeName);

        // 4. Demand multiplier
        if (ValuationConfig.isDemandEnabled()) {
            DemandResult demandResult = calculateDemandMultiplier(chunkX, chunkZ, dimension);
            valuation.setDemandMultiplier(demandResult.multiplier);
            valuation.setNearbyClaims(demandResult.nearbyClaims);
        }

        // 5. Government multiplier (from City/State/Nation)
        double govMult = calculateGovernmentMultiplier(chunkX, chunkZ, dimension);
        valuation.setGovernmentMultiplier(govMult);

        // 6. Improvement value
        if (ValuationConfig.isImprovementsEnabled()) {
            int score = ImprovementTracker.getInstance().getScore(chunkX, chunkZ, dimension);
            double improvementValue = score * ValuationConfig.getImprovementValuePerScore();
            valuation.setImprovementScore(score);
            valuation.setImprovementValue(improvementValue);
        }

        // Calculate total
        valuation.recalculateTotal();

        return valuation;
    }

    /**
     * Calculate location multiplier based on distance from spawn
     * Formula: max(min, maxMult - (distance / threshold))
     */
    private double calculateLocationMultiplier(int chunkX, int chunkZ) {
        // Distance in blocks from spawn (0,0)
        double distance = Math.sqrt(Math.pow(chunkX * 16.0, 2) + Math.pow(chunkZ * 16.0, 2));

        double maxThreshold = ValuationConfig.getMaxDistanceThreshold();
        double minMult = ValuationConfig.getMinLocationMultiplier();
        double maxMult = ValuationConfig.getMaxLocationMultiplier();

        // Linear interpolation from max to min as distance increases
        double ratio = Math.min(1.0, distance / maxThreshold);
        double multiplier = maxMult - (ratio * (maxMult - minMult));

        return Math.max(minMult, multiplier);
    }

    /**
     * Calculate biome multiplier by checking the chunk's primary biome at surface level
     */
    private BiomeResult calculateBiomeMultiplier(int chunkX, int chunkZ, String dimension) {
        if (server == null) {
            return new BiomeResult(1.0, "Unknown");
        }

        // Get the appropriate level
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(dimension)) {
                level = l;
                break;
            }
        }

        if (level == null) {
            return new BiomeResult(1.0, "Unknown");
        }

        // Get center X/Z of chunk
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;

        // Get the surface height at the center of the chunk using heightmap
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, centerX, centerZ);

        // Sample biome at surface level (add 1 to be above the surface block)
        BlockPos surfacePos = new BlockPos(centerX, surfaceY + 1, centerZ);
        Holder<Biome> biomeHolder = level.getBiome(surfacePos);

        String biomeName = biomeHolder.unwrapKey()
            .map(key -> key.location().toString())
            .orElse("unknown");

        double multiplier = BiomeValueMap.getMultiplier(biomeName);

        return new BiomeResult(multiplier, biomeName);
    }

    /**
     * Calculate demand multiplier based on nearby claims
     */
    private DemandResult calculateDemandMultiplier(int chunkX, int chunkZ, String dimension) {
        int radius = ValuationConfig.getDemandRadius();
        int threshold = ValuationConfig.getDemandThreshold();
        double maxBonus = ValuationConfig.getDemandMaxBonus();

        // Count nearby claimed chunks via integration
        int nearbyClaims = StateCraftIntegration.countNearbyClaimedChunks(
            server, chunkX, chunkZ, dimension, radius);

        // Calculate multiplier: 1.0 + (claims/threshold) * maxBonus, capped at 1.0 + maxBonus
        double bonus = Math.min(maxBonus, ((double) nearbyClaims / threshold) * maxBonus);
        double multiplier = 1.0 + bonus;

        return new DemandResult(multiplier, nearbyClaims);
    }

    /**
     * Calculate government multiplier from city/state/nation settings
     */
    private double calculateGovernmentMultiplier(int chunkX, int chunkZ, String dimension) {
        // Get the tax base multiplier from the city this chunk belongs to
        return StateCraftIntegration.getGovernmentTaxMultiplier(server, chunkX, chunkZ, dimension);
    }

    /**
     * Recalculate all dirty chunks
     */
    private void recalculateDirtyChunks() {
        if (dirtyChunks.isEmpty()) return;

        int count = 0;
        for (String key : new ArrayList<>(dirtyChunks)) {
            String[] parts = key.split(":");
            if (parts.length >= 3) {
                int chunkX = Integer.parseInt(parts[0]);
                int chunkZ = Integer.parseInt(parts[1]);
                String dimension = parts[2];
                // For keys with colons in dimension name
                if (parts.length > 3) {
                    dimension = key.substring(key.indexOf(':', key.indexOf(':') + 1) + 1);
                }

                ChunkValuation valuation = calculateValuation(chunkX, chunkZ, dimension);
                valuationCache.put(key, valuation);
                dirtyChunks.remove(key);
                count++;
            }
        }

        if (count > 0) {
            StateCraftEconomy.LOGGER.debug("Recalculated {} chunk valuations", count);
        }
    }

    /**
     * Save cache to JSON file
     */
    public void saveCache() {
        if (savePath == null) return;

        try {
            JsonObject root = new JsonObject();
            JsonArray valuations = new JsonArray();

            for (ChunkValuation valuation : valuationCache.values()) {
                valuations.add(valuation.toJson());
            }

            root.add("valuations", valuations);
            root.addProperty("savedAt", System.currentTimeMillis());

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(savePath, gson.toJson(root));

            StateCraftEconomy.LOGGER.debug("Saved {} chunk valuations to cache", valuationCache.size());
        } catch (IOException e) {
            StateCraftEconomy.LOGGER.error("Failed to save valuation cache", e);
        }
    }

    /**
     * Load cache from JSON file
     */
    private void loadCache() {
        if (savePath == null || !Files.exists(savePath)) {
            StateCraftEconomy.LOGGER.info("No valuation cache found, starting fresh");
            cacheLoaded = true;
            return;
        }

        try {
            String json = Files.readString(savePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("valuations")) {
                JsonArray valuations = root.getAsJsonArray("valuations");
                valuationCache.clear();

                for (JsonElement elem : valuations) {
                    ChunkValuation valuation = ChunkValuation.fromJson(elem.getAsJsonObject());
                    valuationCache.put(valuation.getKey(), valuation);
                }
            }

            cacheLoaded = true;
            StateCraftEconomy.LOGGER.info("Loaded {} chunk valuations from cache", valuationCache.size());
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Failed to load valuation cache", e);
            cacheLoaded = true;
        }
    }

    /**
     * Clear all cached valuations (forces full recalculation)
     */
    public void clearCache() {
        valuationCache.clear();
        dirtyChunks.clear();
    }

    /**
     * Get the number of cached valuations
     */
    public int getCacheSize() {
        return valuationCache.size();
    }

    // Helper classes
    private static class BiomeResult {
        final double multiplier;
        final String biomeName;

        BiomeResult(double multiplier, String biomeName) {
            this.multiplier = multiplier;
            this.biomeName = biomeName;
        }
    }

    private static class DemandResult {
        final double multiplier;
        final int nearbyClaims;

        DemandResult(double multiplier, int nearbyClaims) {
            this.multiplier = multiplier;
            this.nearbyClaims = nearbyClaims;
        }
    }
}

