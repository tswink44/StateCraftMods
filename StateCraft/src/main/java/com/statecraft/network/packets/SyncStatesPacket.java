package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of nation states
 */
public class SyncStatesPacket {
    private final List<StateInfo> states;

    public SyncStatesPacket(List<StateInfo> states) {
        this.states = states;
    }

    public SyncStatesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            states.add(new StateInfo(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(states.size());
        for (StateInfo state : states) {
            buf.writeUtf(state.name, 64);
            buf.writeUtf(state.governorName, 64);
            buf.writeVarInt(state.cityCount);
            buf.writeVarInt(state.chunkCount);
        }
    }

    public List<StateInfo> getStates() {
        return states;
    }

    public static class StateInfo {
        public final String name;
        public final String governorName;
        public final int cityCount;
        public final int chunkCount;

        public StateInfo(String name, String governorName, int cityCount, int chunkCount) {
            this.name = name;
            this.governorName = governorName;
            this.cityCount = cityCount;
            this.chunkCount = chunkCount;
        }
    }
}

