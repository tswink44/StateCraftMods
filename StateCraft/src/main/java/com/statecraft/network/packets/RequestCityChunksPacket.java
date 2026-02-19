package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request list of all chunks in a city
 */
public class RequestCityChunksPacket {
    private final String nationName;
    private final String stateName;
    private final String cityName;

    public RequestCityChunksPacket(String nationName, String stateName, String cityName) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
    }

    public RequestCityChunksPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.stateName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(stateName, 64);
        buf.writeUtf(cityName, 64);
    }

    public String getNationName() { return nationName; }
    public String getStateName() { return stateName; }
    public String getCityName() { return cityName; }
}

