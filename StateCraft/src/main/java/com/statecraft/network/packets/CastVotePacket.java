package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Client sends vote to server
 */
public class CastVotePacket {

    private final UUID candidateId;

    public CastVotePacket(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public CastVotePacket(FriendlyByteBuf buf) {
        this.candidateId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(candidateId);
    }

    public UUID getCandidateId() {
        return candidateId;
    }
}

