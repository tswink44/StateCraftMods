package com.statecraft.economy.valuation;

import com.google.gson.JsonObject;

/**
 * Cached valuation data for a single chunk.
 * Stores all multipliers and the final computed value.
 */
public class ChunkValuation {

    private final int chunkX;
    private final int chunkZ;
    private final String dimension;

    // Individual multipliers (for display/debugging)
    private double baseValue;
    private double locationMultiplier;
    private double biomeMultiplier;
    private String biomeName;
    private double demandMultiplier;
    private int nearbyClaims;
    private double governmentMultiplier;
    private double improvementValue;
    private int improvementScore;

    // Final computed value
    private double totalValue;

    // Cache metadata
    private long lastCalculated;
    private boolean dirty;

    public ChunkValuation(int chunkX, int chunkZ, String dimension) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
        this.baseValue = 100.0;
        this.locationMultiplier = 1.0;
        this.biomeMultiplier = 1.0;
        this.biomeName = "Unknown";
        this.demandMultiplier = 1.0;
        this.nearbyClaims = 0;
        this.governmentMultiplier = 1.0;
        this.improvementValue = 0.0;
        this.improvementScore = 0;
        this.totalValue = 100.0;
        this.lastCalculated = 0;
        this.dirty = true;
    }

    // Getters
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public String getDimension() { return dimension; }

    public double getBaseValue() { return baseValue; }
    public double getLocationMultiplier() { return locationMultiplier; }
    public double getBiomeMultiplier() { return biomeMultiplier; }
    public String getBiomeName() { return biomeName; }
    public double getDemandMultiplier() { return demandMultiplier; }
    public int getNearbyClaims() { return nearbyClaims; }
    public double getGovernmentMultiplier() { return governmentMultiplier; }
    public double getImprovementValue() { return improvementValue; }
    public int getImprovementScore() { return improvementScore; }
    public double getTotalValue() { return totalValue; }

    public long getLastCalculated() { return lastCalculated; }
    public boolean isDirty() { return dirty; }

    // Setters
    public void setBaseValue(double value) { this.baseValue = value; }
    public void setLocationMultiplier(double multiplier) { this.locationMultiplier = multiplier; }
    public void setBiomeMultiplier(double multiplier) { this.biomeMultiplier = multiplier; }
    public void setBiomeName(String name) { this.biomeName = name; }
    public void setDemandMultiplier(double multiplier) { this.demandMultiplier = multiplier; }
    public void setNearbyClaims(int claims) { this.nearbyClaims = claims; }
    public void setGovernmentMultiplier(double multiplier) { this.governmentMultiplier = multiplier; }
    public void setImprovementValue(double value) { this.improvementValue = value; }
    public void setImprovementScore(int score) { this.improvementScore = score; }

    public void setDirty(boolean dirty) { this.dirty = dirty; }

    /**
     * Recalculate the total value from components
     */
    public void recalculateTotal() {
        // Formula: (Base × Location × Biome × Demand × Government) + Improvements
        this.totalValue = (baseValue * locationMultiplier * biomeMultiplier * demandMultiplier * governmentMultiplier)
                          + improvementValue;
        this.lastCalculated = System.currentTimeMillis();
        this.dirty = false;
    }

    /**
     * Get distance from spawn in blocks
     */
    public double getDistanceFromSpawn() {
        return Math.sqrt(Math.pow(chunkX * 16.0, 2) + Math.pow(chunkZ * 16.0, 2));
    }

    /**
     * Get a breakdown string for display
     */
    public String getBreakdown() {
        return String.format(
            "Base: $%.2f × Loc: %.2f × Biome: %.2f × Demand: %.2f × Gov: %.2f + Improvements: $%.2f = $%.2f",
            baseValue, locationMultiplier, biomeMultiplier, demandMultiplier, governmentMultiplier,
            improvementValue, totalValue
        );
    }

    /**
     * Create a unique key for this chunk
     */
    public String getKey() {
        return makeKey(chunkX, chunkZ, dimension);
    }

    public static String makeKey(int chunkX, int chunkZ, String dimension) {
        return chunkX + ":" + chunkZ + ":" + dimension;
    }

    /**
     * Serialize to JSON for file storage
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("chunkX", chunkX);
        json.addProperty("chunkZ", chunkZ);
        json.addProperty("dimension", dimension);
        json.addProperty("baseValue", baseValue);
        json.addProperty("locationMultiplier", locationMultiplier);
        json.addProperty("biomeMultiplier", biomeMultiplier);
        json.addProperty("biomeName", biomeName);
        json.addProperty("demandMultiplier", demandMultiplier);
        json.addProperty("nearbyClaims", nearbyClaims);
        json.addProperty("governmentMultiplier", governmentMultiplier);
        json.addProperty("improvementValue", improvementValue);
        json.addProperty("improvementScore", improvementScore);
        json.addProperty("totalValue", totalValue);
        json.addProperty("lastCalculated", lastCalculated);
        return json;
    }

    /**
     * Deserialize from JSON
     */
    public static ChunkValuation fromJson(JsonObject json) {
        int chunkX = json.get("chunkX").getAsInt();
        int chunkZ = json.get("chunkZ").getAsInt();
        String dimension = json.get("dimension").getAsString();

        ChunkValuation valuation = new ChunkValuation(chunkX, chunkZ, dimension);
        valuation.baseValue = json.has("baseValue") ? json.get("baseValue").getAsDouble() : 100.0;
        valuation.locationMultiplier = json.has("locationMultiplier") ? json.get("locationMultiplier").getAsDouble() : 1.0;
        valuation.biomeMultiplier = json.has("biomeMultiplier") ? json.get("biomeMultiplier").getAsDouble() : 1.0;
        valuation.biomeName = json.has("biomeName") ? json.get("biomeName").getAsString() : "Unknown";
        valuation.demandMultiplier = json.has("demandMultiplier") ? json.get("demandMultiplier").getAsDouble() : 1.0;
        valuation.nearbyClaims = json.has("nearbyClaims") ? json.get("nearbyClaims").getAsInt() : 0;
        valuation.governmentMultiplier = json.has("governmentMultiplier") ? json.get("governmentMultiplier").getAsDouble() : 1.0;
        valuation.improvementValue = json.has("improvementValue") ? json.get("improvementValue").getAsDouble() : 0.0;
        valuation.improvementScore = json.has("improvementScore") ? json.get("improvementScore").getAsInt() : 0;
        valuation.totalValue = json.has("totalValue") ? json.get("totalValue").getAsDouble() : 100.0;
        valuation.lastCalculated = json.has("lastCalculated") ? json.get("lastCalculated").getAsLong() : 0;
        valuation.dirty = false;

        return valuation;
    }
}

