package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client: Sync auto-claim state
 */
public class SyncAutoClaimPacket {
    private final boolean enabled;
    private final boolean canUse; // Does the player have permission to use auto-claim?
    private final String cityName;

    public SyncAutoClaimPacket(boolean enabled, boolean canUse, String cityName) {
        this.enabled = enabled;
        this.canUse = canUse;
        this.cityName = cityName != null ? cityName : "";
    }

    public SyncAutoClaimPacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
        this.canUse = buf.readBoolean();
        this.cityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeBoolean(canUse);
        buf.writeUtf(cityName, 64);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canUse() {
        return canUse;
    }

    public String getCityName() {
        return cityName;
    }
}

