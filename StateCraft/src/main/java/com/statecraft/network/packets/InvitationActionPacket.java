package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Accept or deny an invitation
 */
public class InvitationActionPacket {
    private final String invitationId;
    private final boolean accept;

    public InvitationActionPacket(String invitationId, boolean accept) {
        this.invitationId = invitationId;
        this.accept = accept;
    }

    public InvitationActionPacket(FriendlyByteBuf buf) {
        this.invitationId = buf.readUtf(36); // UUID length
        this.accept = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(invitationId, 36);
        buf.writeBoolean(accept);
    }

    public String getInvitationId() {
        return invitationId;
    }

    public boolean isAccept() {
        return accept;
    }
}

