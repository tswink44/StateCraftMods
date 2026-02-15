package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of nation states
 */
public class SyncStatesPacket {
    private final List<StateInfo> states;
    private final boolean canCreateState; // Whether the player can create new states

    public SyncStatesPacket(List<StateInfo> states, boolean canCreateState) {
        this.states = states;
        this.canCreateState = canCreateState;
    }

    public SyncStatesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            states.add(new StateInfo(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean()
            ));
        }
        this.canCreateState = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(states.size());
        for (StateInfo state : states) {
            buf.writeUtf(state.name, 64);
            buf.writeUtf(state.governorName, 64);
            buf.writeVarInt(state.cityCount);
            buf.writeVarInt(state.chunkCount);
            buf.writeBoolean(state.isCitizen);
        }
        buf.writeBoolean(canCreateState);
    }

    public List<StateInfo> getStates() {
        return states;
    }

    public boolean canCreateState() {
        return canCreateState;
    }

    public static class StateInfo {
        public final String name;
        public final String governorName;
        public final int cityCount;
        public final int chunkCount;
        public final boolean isCitizen;

        public StateInfo(String name, String governorName, int cityCount, int chunkCount, boolean isCitizen) {
            this.name = name;
            this.governorName = governorName;
            this.cityCount = cityCount;
            this.chunkCount = chunkCount;
            this.isCitizen = isCitizen;
        }
    }
}

