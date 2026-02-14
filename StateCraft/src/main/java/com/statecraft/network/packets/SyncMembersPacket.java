package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of nation members
 */
public class SyncMembersPacket {
    private final List<MemberInfo> members;

    public SyncMembersPacket(List<MemberInfo> members) {
        this.members = members;
    }

    public SyncMembersPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new MemberInfo(
                buf.readUtf(64),
                buf.readUtf(32),
                buf.readBoolean()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(members.size());
        for (MemberInfo member : members) {
            buf.writeUtf(member.name, 64);
            buf.writeUtf(member.role, 32);
            buf.writeBoolean(member.online);
        }
    }

    public List<MemberInfo> getMembers() {
        return members;
    }

    public static class MemberInfo {
        public final String name;
        public final String role;
        public final boolean online;

        public MemberInfo(String name, String role, boolean online) {
            this.name = name;
            this.role = role;
            this.online = online;
        }
    }
}

