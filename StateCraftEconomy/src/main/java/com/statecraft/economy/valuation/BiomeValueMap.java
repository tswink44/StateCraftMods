package com.statecraft.economy.valuation;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps biomes to their value multipliers for chunk valuation.
 * Configurable via server config.
 */
public class BiomeValueMap {

    private static final Map<String, Double> BIOME_MULTIPLIERS = new HashMap<>();

    static {
        // Initialize default biome multipliers

        // High value biomes (1.2x - 1.5x)
        BIOME_MULTIPLIERS.put("minecraft:plains", 1.2);
        BIOME_MULTIPLIERS.put("minecraft:sunflower_plains", 1.25);
        BIOME_MULTIPLIERS.put("minecraft:meadow", 1.3);
        BIOME_MULTIPLIERS.put("minecraft:mushroom_fields", 1.5);
        BIOME_MULTIPLIERS.put("minecraft:cherry_grove", 1.35);

        // Standard biomes (1.0x)
        BIOME_MULTIPLIERS.put("minecraft:forest", 1.0);
        BIOME_MULTIPLIERS.put("minecraft:birch_forest", 1.0);
        BIOME_MULTIPLIERS.put("minecraft:old_growth_birch_forest", 1.05);
        BIOME_MULTIPLIERS.put("minecraft:dark_forest", 0.95);
        BIOME_MULTIPLIERS.put("minecraft:flower_forest", 1.1);
        BIOME_MULTIPLIERS.put("minecraft:taiga", 0.95);
        BIOME_MULTIPLIERS.put("minecraft:old_growth_pine_taiga", 1.0);
        BIOME_MULTIPLIERS.put("minecraft:old_growth_spruce_taiga", 1.0);
        BIOME_MULTIPLIERS.put("minecraft:savanna", 0.95);
        BIOME_MULTIPLIERS.put("minecraft:savanna_plateau", 1.0);
        BIOME_MULTIPLIERS.put("minecraft:windswept_savanna", 0.85);

        // Lower value biomes (0.7x - 0.9x)
        BIOME_MULTIPLIERS.put("minecraft:mountains", 0.9);
        BIOME_MULTIPLIERS.put("minecraft:windswept_hills", 0.85);
        BIOME_MULTIPLIERS.put("minecraft:windswept_gravelly_hills", 0.8);
        BIOME_MULTIPLIERS.put("minecraft:windswept_forest", 0.85);
        BIOME_MULTIPLIERS.put("minecraft:stony_peaks", 0.75);
        BIOME_MULTIPLIERS.put("minecraft:jagged_peaks", 0.7);
        BIOME_MULTIPLIERS.put("minecraft:frozen_peaks", 0.65);
        BIOME_MULTIPLIERS.put("minecraft:snowy_slopes", 0.7);
        BIOME_MULTIPLIERS.put("minecraft:grove", 0.8);

        // Desert/Badlands (0.7x - 0.8x)
        BIOME_MULTIPLIERS.put("minecraft:desert", 0.7);
        BIOME_MULTIPLIERS.put("minecraft:badlands", 0.75);
        BIOME_MULTIPLIERS.put("minecraft:wooded_badlands", 0.8);
        BIOME_MULTIPLIERS.put("minecraft:eroded_badlands", 0.7);

        // Jungle (0.8x - 0.9x)
        BIOME_MULTIPLIERS.put("minecraft:jungle", 0.85);
        BIOME_MULTIPLIERS.put("minecraft:sparse_jungle", 0.9);
        BIOME_MULTIPLIERS.put("minecraft:bamboo_jungle", 0.8);

        // Swamp (0.6x - 0.7x)
        BIOME_MULTIPLIERS.put("minecraft:swamp", 0.65);
        BIOME_MULTIPLIERS.put("minecraft:mangrove_swamp", 0.6);

        // Snowy/Ice (0.6x - 0.75x)
        BIOME_MULTIPLIERS.put("minecraft:snowy_plains", 0.7);
        BIOME_MULTIPLIERS.put("minecraft:ice_spikes", 0.6);
        BIOME_MULTIPLIERS.put("minecraft:snowy_taiga", 0.7);
        BIOME_MULTIPLIERS.put("minecraft:snowy_beach", 0.65);
        BIOME_MULTIPLIERS.put("minecraft:frozen_river", 0.5);
        BIOME_MULTIPLIERS.put("minecraft:frozen_ocean", 0.4);
        BIOME_MULTIPLIERS.put("minecraft:deep_frozen_ocean", 0.35);

        // Ocean (0.3x - 0.5x)
        BIOME_MULTIPLIERS.put("minecraft:ocean", 0.5);
        BIOME_MULTIPLIERS.put("minecraft:deep_ocean", 0.4);
        BIOME_MULTIPLIERS.put("minecraft:warm_ocean", 0.55);
        BIOME_MULTIPLIERS.put("minecraft:lukewarm_ocean", 0.5);
        BIOME_MULTIPLIERS.put("minecraft:deep_lukewarm_ocean", 0.45);
        BIOME_MULTIPLIERS.put("minecraft:cold_ocean", 0.45);
        BIOME_MULTIPLIERS.put("minecraft:deep_cold_ocean", 0.4);

        // Beach/River (0.6x - 0.8x)
        BIOME_MULTIPLIERS.put("minecraft:beach", 0.8);
        BIOME_MULTIPLIERS.put("minecraft:stony_shore", 0.6);
        BIOME_MULTIPLIERS.put("minecraft:river", 0.7);

        // Caves (0.3x - 0.5x) - rare to claim but possible
        BIOME_MULTIPLIERS.put("minecraft:dripstone_caves", 0.5);
        BIOME_MULTIPLIERS.put("minecraft:lush_caves", 0.6);
        BIOME_MULTIPLIERS.put("minecraft:deep_dark", 0.3);

        // Nether biomes (0.4x - 0.6x)
        BIOME_MULTIPLIERS.put("minecraft:nether_wastes", 0.5);
        BIOME_MULTIPLIERS.put("minecraft:crimson_forest", 0.55);
        BIOME_MULTIPLIERS.put("minecraft:warped_forest", 0.55);
        BIOME_MULTIPLIERS.put("minecraft:soul_sand_valley", 0.4);
        BIOME_MULTIPLIERS.put("minecraft:basalt_deltas", 0.35);

        // End biomes (0.5x - 0.7x)
        BIOME_MULTIPLIERS.put("minecraft:the_end", 0.5);
        BIOME_MULTIPLIERS.put("minecraft:end_highlands", 0.6);
        BIOME_MULTIPLIERS.put("minecraft:end_midlands", 0.55);
        BIOME_MULTIPLIERS.put("minecraft:small_end_islands", 0.4);
        BIOME_MULTIPLIERS.put("minecraft:end_barrens", 0.35);
    }

    /**
     * Get the multiplier for a biome by its registry name
     */
    public static double getMultiplier(String biomeName) {
        return BIOME_MULTIPLIERS.getOrDefault(biomeName, 1.0);
    }

    /**
     * Set a custom multiplier for a biome (for config loading)
     */
    public static void setMultiplier(String biomeName, double multiplier) {
        BIOME_MULTIPLIERS.put(biomeName, multiplier);
    }

    /**
     * Get all biome multipliers
     */
    public static Map<String, Double> getAllMultipliers() {
        return new HashMap<>(BIOME_MULTIPLIERS);
    }

    /**
     * Get a display name for the biome (removes minecraft: prefix and formats)
     */
    public static String getDisplayName(String biomeName) {
        if (biomeName == null) return "Unknown";
        String name = biomeName.replace("minecraft:", "");
        // Convert underscores to spaces and capitalize
        StringBuilder result = new StringBuilder();
        for (String word : name.split("_")) {
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return result.toString();
    }
}

