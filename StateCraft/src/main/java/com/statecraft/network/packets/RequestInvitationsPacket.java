package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Request list of pending invitations for the player
 */
public class RequestInvitationsPacket {

    public RequestInvitationsPacket() {
    }

    public RequestInvitationsPacket(FriendlyByteBuf buf) {
        // No data needed
    }

    public void encode(FriendlyByteBuf buf) {
        // No data needed
    }
}

