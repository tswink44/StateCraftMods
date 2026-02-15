package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client requests to register as a candidate
 */
public class RegisterCandidatePacket {

    public RegisterCandidatePacket() {
    }

    public RegisterCandidatePacket(FriendlyByteBuf buf) {
        // No data needed - server gets player from context
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

