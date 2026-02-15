package com.statecraft.economy.valuation;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for chunk valuation system.
 * All values are server-side configurable.
 */
public class ValuationConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Base values
    public static final ForgeConfigSpec.DoubleValue BASE_CHUNK_VALUE;

    // Location multiplier settings
    public static final ForgeConfigSpec.DoubleValue MAX_DISTANCE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue MIN_LOCATION_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAX_LOCATION_MULTIPLIER;

    // Demand multiplier settings
    public static final ForgeConfigSpec.BooleanValue DEMAND_ENABLED;
    public static final ForgeConfigSpec.IntValue DEMAND_RADIUS;
    public static final ForgeConfigSpec.IntValue DEMAND_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue DEMAND_MAX_BONUS;

    // Improvement settings
    public static final ForgeConfigSpec.BooleanValue IMPROVEMENTS_ENABLED;
    public static final ForgeConfigSpec.DoubleValue IMPROVEMENT_VALUE_PER_SCORE;

    // Cache settings
    public static final ForgeConfigSpec.IntValue CACHE_RECALCULATION_INTERVAL;
    public static final ForgeConfigSpec.IntValue CACHE_SAVE_INTERVAL;

    static {
        BUILDER.push("valuation");

        BUILDER.comment("Base chunk value before multipliers");
        BASE_CHUNK_VALUE = BUILDER.defineInRange("baseChunkValue", 100.0, 1.0, 100000.0);

        BUILDER.push("location");
        BUILDER.comment("Maximum distance (in blocks) for location multiplier calculation");
        MAX_DISTANCE_THRESHOLD = BUILDER.defineInRange("maxDistanceThreshold", 10000.0, 1000.0, 100000.0);

        BUILDER.comment("Minimum location multiplier (for chunks far from spawn)");
        MIN_LOCATION_MULTIPLIER = BUILDER.defineInRange("minLocationMultiplier", 0.5, 0.1, 1.0);

        BUILDER.comment("Maximum location multiplier (for chunks near spawn)");
        MAX_LOCATION_MULTIPLIER = BUILDER.defineInRange("maxLocationMultiplier", 1.5, 1.0, 5.0);
        BUILDER.pop();

        BUILDER.push("demand");
        BUILDER.comment("Enable demand-based multiplier");
        DEMAND_ENABLED = BUILDER.define("enabled", true);

        BUILDER.comment("Radius (in chunks) to check for nearby claims");
        DEMAND_RADIUS = BUILDER.defineInRange("radius", 5, 1, 20);

        BUILDER.comment("Number of nearby claims needed for maximum demand bonus");
        DEMAND_THRESHOLD = BUILDER.defineInRange("threshold", 20, 1, 100);

        BUILDER.comment("Maximum bonus from demand (0.5 = 50% increase)");
        DEMAND_MAX_BONUS = BUILDER.defineInRange("maxBonus", 0.5, 0.0, 2.0);
        BUILDER.pop();

        BUILDER.push("improvements");
        BUILDER.comment("Enable improvement-based value additions");
        IMPROVEMENTS_ENABLED = BUILDER.define("enabled", true);

        BUILDER.comment("Value added per improvement score point");
        IMPROVEMENT_VALUE_PER_SCORE = BUILDER.defineInRange("valuePerScore", 0.10, 0.01, 10.0);
        BUILDER.pop();

        BUILDER.push("cache");
        BUILDER.comment("How often to recalculate valuations (in ticks, 20 ticks = 1 second)");
        CACHE_RECALCULATION_INTERVAL = BUILDER.defineInRange("recalculationInterval", 12000, 200, 72000);

        BUILDER.comment("How often to save valuations to disk (in ticks)");
        CACHE_SAVE_INTERVAL = BUILDER.defineInRange("saveInterval", 6000, 200, 72000);
        BUILDER.pop();

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // Convenience getters with defaults
    public static double getBaseChunkValue() {
        return BASE_CHUNK_VALUE.get();
    }

    public static double getMaxDistanceThreshold() {
        return MAX_DISTANCE_THRESHOLD.get();
    }

    public static double getMinLocationMultiplier() {
        return MIN_LOCATION_MULTIPLIER.get();
    }

    public static double getMaxLocationMultiplier() {
        return MAX_LOCATION_MULTIPLIER.get();
    }

    public static boolean isDemandEnabled() {
        return DEMAND_ENABLED.get();
    }

    public static int getDemandRadius() {
        return DEMAND_RADIUS.get();
    }

    public static int getDemandThreshold() {
        return DEMAND_THRESHOLD.get();
    }

    public static double getDemandMaxBonus() {
        return DEMAND_MAX_BONUS.get();
    }

    public static boolean isImprovementsEnabled() {
        return IMPROVEMENTS_ENABLED.get();
    }

    public static double getImprovementValuePerScore() {
        return IMPROVEMENT_VALUE_PER_SCORE.get();
    }

    public static int getCacheRecalculationInterval() {
        return CACHE_RECALCULATION_INTERVAL.get();
    }

    public static int getCacheSaveInterval() {
        return CACHE_SAVE_INTERVAL.get();
    }
}

