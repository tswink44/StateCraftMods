package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a pending invitation to join a nation, state, or city
 */
public class Invitation {

    public enum InvitationType {
        NATION,
        CITY
    }

    private final UUID id;
    private final UUID targetPlayerId;      // Player being invited
    private final UUID senderId;            // Player who sent the invite
    private final UUID entityId;            // Nation or City ID
    private final InvitationType type;
    private final long createdAt;           // Timestamp
    private final long expiresAt;           // Expiration timestamp

    private static final long INVITATION_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    public Invitation(UUID targetPlayerId, UUID senderId, UUID entityId, InvitationType type) {
        this.id = UUID.randomUUID();
        this.targetPlayerId = targetPlayerId;
        this.senderId = senderId;
        this.entityId = entityId;
        this.type = type;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + INVITATION_DURATION_MS;
    }

    private Invitation(UUID id, UUID targetPlayerId, UUID senderId, UUID entityId,
                       InvitationType type, long createdAt, long expiresAt) {
        this.id = id;
        this.targetPlayerId = targetPlayerId;
        this.senderId = senderId;
        this.entityId = entityId;
        this.type = type;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTargetPlayerId() {
        return targetPlayerId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public InvitationType getType() {
        return type;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public long getRemainingTimeSeconds() {
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("targetPlayerId", targetPlayerId);
        tag.putUUID("senderId", senderId);
        tag.putUUID("entityId", entityId);
        tag.putString("type", type.name());
        tag.putLong("createdAt", createdAt);
        tag.putLong("expiresAt", expiresAt);
        return tag;
    }

    public static Invitation load(CompoundTag tag) {
        return new Invitation(
            tag.getUUID("id"),
            tag.getUUID("targetPlayerId"),
            tag.getUUID("senderId"),
            tag.getUUID("entityId"),
            InvitationType.valueOf(tag.getString("type")),
            tag.getLong("createdAt"),
            tag.getLong("expiresAt")
        );
    }
}

