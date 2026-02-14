package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of cities in a state
 */
public class SyncCitiesPacket {
    private final List<CityInfo> cities;

    public SyncCitiesPacket(List<CityInfo> cities) {
        this.cities = cities;
    }

    public SyncCitiesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.cities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cities.add(new CityInfo(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cities.size());
        for (CityInfo city : cities) {
            buf.writeUtf(city.name, 64);
            buf.writeUtf(city.mayorName, 64);
            buf.writeVarInt(city.chunkCount);
            buf.writeVarInt(city.residentCount);
        }
    }

    public List<CityInfo> getCities() {
        return cities;
    }

    public static class CityInfo {
        public final String name;
        public final String mayorName;
        public final int chunkCount;
        public final int residentCount;

        public CityInfo(String name, String mayorName, int chunkCount, int residentCount) {
            this.name = name;
            this.mayorName = mayorName;
            this.chunkCount = chunkCount;
            this.residentCount = residentCount;
        }
    }
}

