package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet containing chunk valuation breakdown data
 */
public class SyncChunkValuationPacket {

    private final int chunkX;
    private final int chunkZ;
    private final String dimension;

    // Valuation components
    private final double baseValue;
    private final double locationMultiplier;
    private final double distanceFromSpawn;
    private final double biomeMultiplier;
    private final String biomeName;
    private final double demandMultiplier;
    private final int nearbyClaims;
    private final double governmentMultiplier;
    private final double improvementValue;
    private final int improvementScore;
    private final double totalValue;

    public SyncChunkValuationPacket(int chunkX, int chunkZ, String dimension,
                                    double baseValue, double locationMultiplier, double distanceFromSpawn,
                                    double biomeMultiplier, String biomeName,
                                    double demandMultiplier, int nearbyClaims,
                                    double governmentMultiplier,
                                    double improvementValue, int improvementScore,
                                    double totalValue) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.dimension = dimension;
        this.baseValue = baseValue;
        this.locationMultiplier = locationMultiplier;
        this.distanceFromSpawn = distanceFromSpawn;
        this.biomeMultiplier = biomeMultiplier;
        this.biomeName = biomeName;
        this.demandMultiplier = demandMultiplier;
        this.nearbyClaims = nearbyClaims;
        this.governmentMultiplier = governmentMultiplier;
        this.improvementValue = improvementValue;
        this.improvementScore = improvementScore;
        this.totalValue = totalValue;
    }

    public SyncChunkValuationPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.dimension = buf.readUtf(128);
        this.baseValue = buf.readDouble();
        this.locationMultiplier = buf.readDouble();
        this.distanceFromSpawn = buf.readDouble();
        this.biomeMultiplier = buf.readDouble();
        this.biomeName = buf.readUtf(64);
        this.demandMultiplier = buf.readDouble();
        this.nearbyClaims = buf.readInt();
        this.governmentMultiplier = buf.readDouble();
        this.improvementValue = buf.readDouble();
        this.improvementScore = buf.readInt();
        this.totalValue = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeUtf(dimension, 128);
        buf.writeDouble(baseValue);
        buf.writeDouble(locationMultiplier);
        buf.writeDouble(distanceFromSpawn);
        buf.writeDouble(biomeMultiplier);
        buf.writeUtf(biomeName, 64);
        buf.writeDouble(demandMultiplier);
        buf.writeInt(nearbyClaims);
        buf.writeDouble(governmentMultiplier);
        buf.writeDouble(improvementValue);
        buf.writeInt(improvementScore);
        buf.writeDouble(totalValue);
    }

    // Getters
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public String getDimension() { return dimension; }
    public double getBaseValue() { return baseValue; }
    public double getLocationMultiplier() { return locationMultiplier; }
    public double getDistanceFromSpawn() { return distanceFromSpawn; }
    public double getBiomeMultiplier() { return biomeMultiplier; }
    public String getBiomeName() { return biomeName; }
    public double getDemandMultiplier() { return demandMultiplier; }
    public int getNearbyClaims() { return nearbyClaims; }
    public double getGovernmentMultiplier() { return governmentMultiplier; }
    public double getImprovementValue() { return improvementValue; }
    public int getImprovementScore() { return improvementScore; }
    public double getTotalValue() { return totalValue; }
}

