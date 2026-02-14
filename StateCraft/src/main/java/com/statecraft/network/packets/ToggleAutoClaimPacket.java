package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Toggle auto-claim mode
 */
public class ToggleAutoClaimPacket {
    private final boolean enable;
    private final String cityName; // Which city to auto-claim for

    public ToggleAutoClaimPacket(boolean enable, String cityName) {
        this.enable = enable;
        this.cityName = cityName != null ? cityName : "";
    }

    public ToggleAutoClaimPacket(FriendlyByteBuf buf) {
        this.enable = buf.readBoolean();
        this.cityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enable);
        buf.writeUtf(cityName, 64);
    }

    public boolean isEnable() {
        return enable;
    }

    public String getCityName() {
        return cityName;
    }
}

