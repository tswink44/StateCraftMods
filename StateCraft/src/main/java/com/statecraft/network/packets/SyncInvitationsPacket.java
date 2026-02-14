package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet to sync pending invitations
 */
public class SyncInvitationsPacket {
    private final List<InviteInfo> invitations;

    public SyncInvitationsPacket(List<InviteInfo> invitations) {
        this.invitations = invitations;
    }

    public SyncInvitationsPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        this.invitations = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            invitations.add(new InviteInfo(
                buf.readUtf(36),
                buf.readUtf(10),
                buf.readUtf(24),
                buf.readUtf(16),
                buf.readLong()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(invitations.size());
        for (InviteInfo info : invitations) {
            buf.writeUtf(info.id, 36);
            buf.writeUtf(info.type, 10);
            buf.writeUtf(info.entityName, 24);
            buf.writeUtf(info.senderName, 16);
            buf.writeLong(info.remainingSeconds);
        }
    }

    public List<InviteInfo> getInvitations() { return invitations; }

    public static class InviteInfo {
        public final String id;
        public final String type;
        public final String entityName;
        public final String senderName;
        public final long remainingSeconds;

        public InviteInfo(String id, String type, String entityName, String senderName, long remainingSeconds) {
            this.id = id;
            this.type = type;
            this.entityName = entityName;
            this.senderName = senderName;
            this.remainingSeconds = remainingSeconds;
        }
    }
}

