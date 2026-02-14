package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet to set border rendering mode
 * mode: -1 = cycle, 0 = OFF, 1 = CHUNKS, 2 = TERRITORY, 3 = BOTH
 */
public class SetBorderModePacket {
    private final int mode;

    public SetBorderModePacket(int mode) {
        this.mode = mode;
    }

    public SetBorderModePacket(FriendlyByteBuf buf) {
        this.mode = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(mode);
    }

    public int getMode() {
        return mode;
    }
}

