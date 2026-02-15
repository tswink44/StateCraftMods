package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: List of all nations
 */
public class SyncAllNationsPacket {
    private final List<NationEntry> nations;

    public SyncAllNationsPacket(List<NationEntry> nations) {
        this.nations = nations;
    }

    public SyncAllNationsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.nations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            nations.add(new NationEntry(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(nations.size());
        for (NationEntry entry : nations) {
            buf.writeUtf(entry.name, 64);
            buf.writeUtf(entry.leaderName, 64);
            buf.writeVarInt(entry.memberCount);
            buf.writeVarInt(entry.stateCount);
            buf.writeBoolean(entry.isOpen);
            buf.writeBoolean(entry.isMember);
        }
    }

    public List<NationEntry> getNations() {
        return nations;
    }

    public static class NationEntry {
        public final String name;
        public final String leaderName;
        public final int memberCount;
        public final int stateCount;
        public final boolean isOpen;
        public final boolean isMember;

        public NationEntry(String name, String leaderName, int memberCount, int stateCount, boolean isOpen, boolean isMember) {
            this.name = name;
            this.leaderName = leaderName;
            this.memberCount = memberCount;
            this.stateCount = stateCount;
            this.isOpen = isOpen;
            this.isMember = isMember;
        }
    }
}

