package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.*;

/**
 * Server -> Client: Syncs emergency power state for the executive actions screen.
 * Sends each power's status: active/cooldown/available, remaining time, description.
 */
public class SyncEmergencyPowerDataPacket {

    private final String nationName;
    private final boolean isLeader;
    private final List<PowerEntry> powers;
    private final String resultMessage; // Non-empty if this is a response to an invoke/revoke

    public SyncEmergencyPowerDataPacket(String nationName, boolean isLeader, List<PowerEntry> powers, String resultMessage) {
        this.nationName = nationName;
        this.isLeader = isLeader;
        this.powers = powers;
        this.resultMessage = resultMessage != null ? resultMessage : "";
    }

    public SyncEmergencyPowerDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
        this.isLeader = buf.readBoolean();
        int count = buf.readVarInt();
        this.powers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            powers.add(new PowerEntry(
                buf.readUtf(),   // powerName (enum name)
                buf.readUtf(),   // displayName
                buf.readUtf(),   // description
                buf.readInt(),   // durationHours
                buf.readInt(),   // cooldownDays
                buf.readEnum(PowerStatus.class), // status
                buf.readLong(),  // remainingMs (active time left or cooldown time left)
                buf.readBoolean() // requiresTarget
            ));
        }
        this.resultMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
        buf.writeBoolean(isLeader);
        buf.writeVarInt(powers.size());
        for (PowerEntry entry : powers) {
            buf.writeUtf(entry.powerName);
            buf.writeUtf(entry.displayName);
            buf.writeUtf(entry.description);
            buf.writeInt(entry.durationHours);
            buf.writeInt(entry.cooldownDays);
            buf.writeEnum(entry.status);
            buf.writeLong(entry.remainingMs);
            buf.writeBoolean(entry.requiresTarget);
        }
        buf.writeUtf(resultMessage);
    }

    public String getNationName() { return nationName; }
    public boolean isLeader() { return isLeader; }
    public List<PowerEntry> getPowers() { return powers; }
    public String getResultMessage() { return resultMessage; }

    public enum PowerStatus {
        AVAILABLE,  // Can be invoked
        ACTIVE,     // Currently active (for duration-based powers)
        COOLDOWN,   // On cooldown
        INSTANT_COOLDOWN // Instant power that's on cooldown (EMERGENCY_TAX, DIPLOMATIC_CRISIS)
    }

    public static class PowerEntry {
        public final String powerName;
        public final String displayName;
        public final String description;
        public final int durationHours;
        public final int cooldownDays;
        public final PowerStatus status;
        public final long remainingMs;
        public final boolean requiresTarget;

        public PowerEntry(String powerName, String displayName, String description,
                         int durationHours, int cooldownDays,
                         PowerStatus status, long remainingMs, boolean requiresTarget) {
            this.powerName = powerName;
            this.displayName = displayName;
            this.description = description;
            this.durationHours = durationHours;
            this.cooldownDays = cooldownDays;
            this.status = status;
            this.remainingMs = remainingMs;
            this.requiresTarget = requiresTarget;
        }
    }
}

