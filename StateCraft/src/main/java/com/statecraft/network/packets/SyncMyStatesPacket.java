package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: List of states the player is a citizen of
 */
public class SyncMyStatesPacket {
    private final List<StateEntry> states;

    public SyncMyStatesPacket(List<StateEntry> states) {
        this.states = states;
    }

    public SyncMyStatesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            states.add(new StateEntry(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readVarInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(states.size());
        for (StateEntry entry : states) {
            buf.writeUtf(entry.stateName, 64);
            buf.writeUtf(entry.governorName, 64);
            buf.writeBoolean(entry.isPrimary);
            buf.writeVarInt(entry.ownedChunks);
        }
    }

    public List<StateEntry> getStates() {
        return states;
    }

    public static class StateEntry {
        public final String stateName;
        public final String governorName;
        public final boolean isPrimary;
        public final int ownedChunks;

        public StateEntry(String stateName, String governorName, boolean isPrimary, int ownedChunks) {
            this.stateName = stateName;
            this.governorName = governorName;
            this.isPrimary = isPrimary;
            this.ownedChunks = ownedChunks;
        }
    }
}

