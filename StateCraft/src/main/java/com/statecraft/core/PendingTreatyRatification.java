package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks a pending peace treaty that requires ratification by both nations' legislatures.
 * Created when both leaders agree to peace terms.
 * Peace only takes effect when BOTH legislatures pass their ratification bills.
 */
public class PendingTreatyRatification {

    private final UUID ratificationId;
    private final UUID proposerNationId;
    private final UUID targetNationId;
    private final long createdTime;

    // The original proposal terms
    private final double currencyDemand;
    private final List<DiplomacyManager.ChunkDemand> chunkDemands;
    private final UUID receivingCityId;

    // Bill IDs for tracking (set when bills are created)
    private UUID proposerBillId;
    private UUID targetBillId;

    // Ratification status
    private boolean proposerRatified = false;
    private boolean targetRatified = false;
    private boolean proposerRejected = false;
    private boolean targetRejected = false;

    // Expiry (treaties must be ratified within 7 days)
    private final long expiresAt;
    private static final long RATIFICATION_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;

    public PendingTreatyRatification(UUID proposerNationId, UUID targetNationId,
                                      double currencyDemand, List<DiplomacyManager.ChunkDemand> chunkDemands,
                                      UUID receivingCityId) {
        this.ratificationId = UUID.randomUUID();
        this.proposerNationId = proposerNationId;
        this.targetNationId = targetNationId;
        this.createdTime = System.currentTimeMillis();
        this.expiresAt = createdTime + RATIFICATION_EXPIRY_MS;
        this.currencyDemand = currencyDemand;
        this.chunkDemands = chunkDemands != null ? new ArrayList<>(chunkDemands) : new ArrayList<>();
        this.receivingCityId = receivingCityId;
    }

    // For NBT loading
    private PendingTreatyRatification(UUID ratificationId, UUID proposerNationId, UUID targetNationId,
                                       long createdTime, long expiresAt, double currencyDemand,
                                       List<DiplomacyManager.ChunkDemand> chunkDemands, UUID receivingCityId) {
        this.ratificationId = ratificationId;
        this.proposerNationId = proposerNationId;
        this.targetNationId = targetNationId;
        this.createdTime = createdTime;
        this.expiresAt = expiresAt;
        this.currencyDemand = currencyDemand;
        this.chunkDemands = chunkDemands;
        this.receivingCityId = receivingCityId;
    }

    // Getters
    public UUID getRatificationId() { return ratificationId; }
    public UUID getProposerNationId() { return proposerNationId; }
    public UUID getTargetNationId() { return targetNationId; }
    public long getCreatedTime() { return createdTime; }
    public long getExpiresAt() { return expiresAt; }
    public double getCurrencyDemand() { return currencyDemand; }
    public List<DiplomacyManager.ChunkDemand> getChunkDemands() { return chunkDemands; }
    public UUID getReceivingCityId() { return receivingCityId; }
    public UUID getProposerBillId() { return proposerBillId; }
    public UUID getTargetBillId() { return targetBillId; }

    public void setProposerBillId(UUID billId) { this.proposerBillId = billId; }
    public void setTargetBillId(UUID billId) { this.targetBillId = billId; }

    public boolean isProposerRatified() { return proposerRatified; }
    public boolean isTargetRatified() { return targetRatified; }
    public boolean isProposerRejected() { return proposerRejected; }
    public boolean isTargetRejected() { return targetRejected; }

    public void setProposerRatified(boolean ratified) { this.proposerRatified = ratified; }
    public void setTargetRatified(boolean ratified) { this.targetRatified = ratified; }
    public void setProposerRejected(boolean rejected) { this.proposerRejected = rejected; }
    public void setTargetRejected(boolean rejected) { this.targetRejected = rejected; }

    /**
     * Check if both nations have ratified
     */
    public boolean isFullyRatified() {
        return proposerRatified && targetRatified;
    }

    /**
     * Check if either nation has rejected
     */
    public boolean isRejected() {
        return proposerRejected || targetRejected;
    }

    /**
     * Check if the ratification has expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Check if this ratification involves the given nation
     */
    public boolean involvesNation(UUID nationId) {
        return proposerNationId.equals(nationId) || targetNationId.equals(nationId);
    }

    /**
     * Get the other nation in this treaty
     */
    public UUID getOtherNation(UUID nationId) {
        if (proposerNationId.equals(nationId)) return targetNationId;
        if (targetNationId.equals(nationId)) return proposerNationId;
        return null;
    }

    /**
     * Check if the given bill ID belongs to this ratification
     */
    public boolean hasBill(UUID billId) {
        return (proposerBillId != null && proposerBillId.equals(billId)) ||
               (targetBillId != null && targetBillId.equals(billId));
    }

    /**
     * Check if the given nation is the proposer
     */
    public boolean isProposer(UUID nationId) {
        return proposerNationId.equals(nationId);
    }

    /**
     * Build a summary of the treaty terms for display
     */
    public String getTermsSummary() {
        StringBuilder sb = new StringBuilder();
        if (currencyDemand > 0) {
            sb.append(String.format("$%.0f reparations", currencyDemand));
        }
        if (!chunkDemands.isEmpty()) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(chunkDemands.size()).append(" chunk").append(chunkDemands.size() > 1 ? "s" : "");
        }
        if (sb.length() == 0) {
            sb.append("No terms (unconditional peace)");
        }
        return sb.toString();
    }

    // ==================== NBT Serialization ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("ratificationId", ratificationId);
        tag.putUUID("proposerNationId", proposerNationId);
        tag.putUUID("targetNationId", targetNationId);
        tag.putLong("createdTime", createdTime);
        tag.putLong("expiresAt", expiresAt);
        tag.putDouble("currencyDemand", currencyDemand);
        if (receivingCityId != null) {
            tag.putUUID("receivingCityId", receivingCityId);
        }
        if (proposerBillId != null) {
            tag.putUUID("proposerBillId", proposerBillId);
        }
        if (targetBillId != null) {
            tag.putUUID("targetBillId", targetBillId);
        }
        tag.putBoolean("proposerRatified", proposerRatified);
        tag.putBoolean("targetRatified", targetRatified);
        tag.putBoolean("proposerRejected", proposerRejected);
        tag.putBoolean("targetRejected", targetRejected);

        // Save chunk demands
        ListTag chunkList = new ListTag();
        for (DiplomacyManager.ChunkDemand demand : chunkDemands) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putInt("chunkX", demand.chunkX);
            chunkTag.putInt("chunkZ", demand.chunkZ);
            chunkTag.putString("dimension", demand.dimension);
            chunkList.add(chunkTag);
        }
        tag.put("chunkDemands", chunkList);

        return tag;
    }

    public static PendingTreatyRatification load(CompoundTag tag) {
        UUID ratificationId = tag.getUUID("ratificationId");
        UUID proposerNationId = tag.getUUID("proposerNationId");
        UUID targetNationId = tag.getUUID("targetNationId");
        long createdTime = tag.getLong("createdTime");
        long expiresAt = tag.getLong("expiresAt");
        double currencyDemand = tag.getDouble("currencyDemand");
        UUID receivingCityId = tag.contains("receivingCityId") ? tag.getUUID("receivingCityId") : null;

        List<DiplomacyManager.ChunkDemand> chunkDemands = new ArrayList<>();
        ListTag chunkList = tag.getList("chunkDemands", Tag.TAG_COMPOUND);
        for (int i = 0; i < chunkList.size(); i++) {
            CompoundTag chunkTag = chunkList.getCompound(i);
            chunkDemands.add(new DiplomacyManager.ChunkDemand(
                chunkTag.getInt("chunkX"),
                chunkTag.getInt("chunkZ"),
                chunkTag.getString("dimension")
            ));
        }

        PendingTreatyRatification ratification = new PendingTreatyRatification(
            ratificationId, proposerNationId, targetNationId, createdTime, expiresAt,
            currencyDemand, chunkDemands, receivingCityId
        );

        if (tag.contains("proposerBillId")) {
            ratification.setProposerBillId(tag.getUUID("proposerBillId"));
        }
        if (tag.contains("targetBillId")) {
            ratification.setTargetBillId(tag.getUUID("targetBillId"));
        }
        ratification.setProposerRatified(tag.getBoolean("proposerRatified"));
        ratification.setTargetRatified(tag.getBoolean("targetRatified"));
        ratification.setProposerRejected(tag.getBoolean("proposerRejected"));
        ratification.setTargetRejected(tag.getBoolean("targetRejected"));

        return ratification;
    }
}

