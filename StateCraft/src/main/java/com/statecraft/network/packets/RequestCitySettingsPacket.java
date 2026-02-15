package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request city settings for the settings screen
 */
public class RequestCitySettingsPacket {
    private final String nationName;
    private final String stateName;
    private final String cityName;

    public RequestCitySettingsPacket(String nationName, String stateName, String cityName) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
    }

    public RequestCitySettingsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(64);
        this.stateName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 64);
        buf.writeUtf(stateName, 64);
        buf.writeUtf(cityName, 64);
    }

    public String getNationName() { return nationName; }
    public String getStateName() { return stateName; }
    public String getCityName() { return cityName; }
}

