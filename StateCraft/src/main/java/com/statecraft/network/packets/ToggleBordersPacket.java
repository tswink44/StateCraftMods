package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet to toggle border rendering
 */
public class ToggleBordersPacket {
    private final boolean toggle; // true = toggle, false = set to specific value
    private final boolean enabled; // only used if toggle is false

    public ToggleBordersPacket(boolean toggle, boolean enabled) {
        this.toggle = toggle;
        this.enabled = enabled;
    }

    public ToggleBordersPacket(FriendlyByteBuf buf) {
        this.toggle = buf.readBoolean();
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(toggle);
        buf.writeBoolean(enabled);
    }

    public boolean isToggle() {
        return toggle;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

