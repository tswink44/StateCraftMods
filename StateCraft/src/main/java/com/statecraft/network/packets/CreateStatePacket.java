package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Create a new state
 */
public class CreateStatePacket {
    private final String stateName;

    public CreateStatePacket(String stateName) {
        this.stateName = stateName;
    }

    public CreateStatePacket(FriendlyByteBuf buf) {
        this.stateName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stateName, 64);
    }

    public String getStateName() {
        return stateName;
    }
}

