package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client -> Server: Create a new government contract
 */
public class CreateContractPacket {

    private final String nationName;
    private final String title;
    private final String description;
    private final String requirements;
    private final double budget;
    private final double bondAmount;
    private final String compensationType;  // FIXED, MILESTONE, VALUATION_BASED
    private final double paymentPerImprovementPoint;  // For VALUATION_BASED type
    private final List<ChunkData> chunks;
    private final String dimension;
    private final Map<Integer, String> milestoneDescriptions;  // For MILESTONE type

    public CreateContractPacket(String nationName, String title, String description, String requirements,
                                 double budget, double bondAmount, String compensationType,
                                 double paymentPerImprovementPoint,
                                 List<ChunkData> chunks, String dimension,
                                 Map<Integer, String> milestoneDescriptions) {
        this.nationName = nationName;
        this.title = title;
        this.description = description;
        this.requirements = requirements;
        this.budget = budget;
        this.bondAmount = bondAmount;
        this.compensationType = compensationType;
        this.paymentPerImprovementPoint = paymentPerImprovementPoint;
        this.chunks = chunks;
        this.dimension = dimension;
        this.milestoneDescriptions = milestoneDescriptions != null ? milestoneDescriptions : new HashMap<>();
    }

    public CreateContractPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.title = buf.readUtf(128);
        this.description = buf.readUtf(1024);
        this.requirements = buf.readUtf(2048);
        this.budget = buf.readDouble();
        this.bondAmount = buf.readDouble();
        this.compensationType = buf.readUtf(32);
        this.paymentPerImprovementPoint = buf.readDouble();
        this.dimension = buf.readUtf(128);

        int chunkCount = buf.readVarInt();
        this.chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(new ChunkData(buf.readInt(), buf.readInt()));
        }

        // Read milestone descriptions
        int milestoneCount = buf.readVarInt();
        this.milestoneDescriptions = new HashMap<>();
        for (int i = 0; i < milestoneCount; i++) {
            int percent = buf.readVarInt();
            String desc = buf.readUtf(256);
            milestoneDescriptions.put(percent, desc);
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(title, 128);
        buf.writeUtf(description, 1024);
        buf.writeUtf(requirements, 2048);
        buf.writeDouble(budget);
        buf.writeDouble(bondAmount);
        buf.writeUtf(compensationType, 32);
        buf.writeDouble(paymentPerImprovementPoint);
        buf.writeUtf(dimension, 128);

        buf.writeVarInt(chunks.size());
        for (ChunkData chunk : chunks) {
            buf.writeInt(chunk.x);
            buf.writeInt(chunk.z);
        }

        // Write milestone descriptions
        buf.writeVarInt(milestoneDescriptions.size());
        for (Map.Entry<Integer, String> entry : milestoneDescriptions.entrySet()) {
            buf.writeVarInt(entry.getKey());
            buf.writeUtf(entry.getValue(), 256);
        }
    }

    // Getters
    public String getNationName() { return nationName; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getRequirements() { return requirements; }
    public double getBudget() { return budget; }
    public double getBondAmount() { return bondAmount; }
    public String getCompensationType() { return compensationType; }
    public double getPaymentPerImprovementPoint() { return paymentPerImprovementPoint; }
    public List<ChunkData> getChunks() { return chunks; }
    public String getDimension() { return dimension; }
    public Map<Integer, String> getMilestoneDescriptions() { return milestoneDescriptions; }

    public static class ChunkData {
        public final int x;
        public final int z;

        public ChunkData(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
}

