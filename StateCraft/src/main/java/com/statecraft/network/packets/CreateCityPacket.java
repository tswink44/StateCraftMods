package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Create a new city
 */
public class CreateCityPacket {
    private final String stateName;
    private final String cityName;

    public CreateCityPacket(String stateName, String cityName) {
        this.stateName = stateName;
        this.cityName = cityName;
    }

    public CreateCityPacket(FriendlyByteBuf buf) {
        this.stateName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stateName, 64);
        buf.writeUtf(cityName, 64);
    }

    public String getStateName() {
        return stateName;
    }

    public String getCityName() {
        return cityName;
    }
}

