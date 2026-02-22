package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Send all chunks belonging to a target nation.
 * Used by the PeaceTermsScreen to display selectable chunks for peace treaty demands.
 * Includes ALL chunks (not just privately owned ones).
 */
public class SyncTargetNationChunksPacket {

    private final String targetNationName;
    private final List<NationChunkEntry> chunks;
    private final double targetBalance;
    private final List<CityEntry> proposerCities; // Cities in the proposer's nation for receiving city dropdown

    public SyncTargetNationChunksPacket(String targetNationName, List<NationChunkEntry> chunks,
                                         double targetBalance, List<CityEntry> proposerCities) {
        this.targetNationName = targetNationName;
        this.chunks = chunks;
        this.targetBalance = targetBalance;
        this.proposerCities = proposerCities != null ? proposerCities : new ArrayList<>();
    }

    public SyncTargetNationChunksPacket(FriendlyByteBuf buf) {
        this.targetNationName = buf.readUtf();
        this.targetBalance = buf.readDouble();

        int count = buf.readVarInt();
        this.chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new NationChunkEntry(
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readInt(),
                buf.readDouble()
            ));
        }

        int cityCount = buf.readVarInt();
        this.proposerCities = new ArrayList<>(cityCount);
        for (int i = 0; i < cityCount; i++) {
            proposerCities.add(new CityEntry(buf.readUtf(64), buf.readUtf(64)));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetNationName);
        buf.writeDouble(targetBalance);

        buf.writeVarInt(chunks.size());
        for (NationChunkEntry entry : chunks) {
            buf.writeInt(entry.chunkX);
            buf.writeInt(entry.chunkZ);
            buf.writeUtf(entry.dimension, 128);
            buf.writeUtf(entry.cityName, 64);
            buf.writeInt(entry.improvementScore);
            buf.writeDouble(entry.totalValue);
        }

        buf.writeVarInt(proposerCities.size());
        for (CityEntry city : proposerCities) {
            buf.writeUtf(city.cityId, 64);
            buf.writeUtf(city.cityName, 64);
        }
    }

    public String getTargetNationName() { return targetNationName; }
    public List<NationChunkEntry> getChunks() { return chunks; }
    public double getTargetBalance() { return targetBalance; }
    public List<CityEntry> getProposerCities() { return proposerCities; }

    public static class CityEntry {
        public final String cityId;   // UUID as string
        public final String cityName;

        public CityEntry(String cityId, String cityName) {
            this.cityId = cityId;
            this.cityName = cityName;
        }
    }

    public static class NationChunkEntry {
        public final int chunkX;
        public final int chunkZ;
        public final String dimension;
        public final String cityName;
        public final int improvementScore;
        public final double totalValue;

        public NationChunkEntry(int chunkX, int chunkZ, String dimension, String cityName,
                                 int improvementScore, double totalValue) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.cityName = cityName;
            this.improvementScore = improvementScore;
            this.totalValue = totalValue;
        }
    }
}



