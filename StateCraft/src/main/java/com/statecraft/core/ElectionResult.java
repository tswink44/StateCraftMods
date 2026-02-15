package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Stores the result of a completed election for history tracking
 */
public record ElectionResult(
    UUID winnerId,
    String winnerName,
    long timestamp,
    int voteCount,
    int totalVoters
) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("winnerId", winnerId);
        tag.putString("winnerName", winnerName);
        tag.putLong("timestamp", timestamp);
        tag.putInt("voteCount", voteCount);
        tag.putInt("totalVoters", totalVoters);
        return tag;
    }

    public static ElectionResult load(CompoundTag tag) {
        return new ElectionResult(
            tag.getUUID("winnerId"),
            tag.getString("winnerName"),
            tag.getLong("timestamp"),
            tag.getInt("voteCount"),
            tag.getInt("totalVoters")
        );
    }
}

