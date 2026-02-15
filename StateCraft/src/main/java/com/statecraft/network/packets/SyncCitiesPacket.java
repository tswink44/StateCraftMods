package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync list of cities in a state
 */
public class SyncCitiesPacket {
    private final List<CityInfo> cities;
    private final boolean canCreateCity; // Whether the player can create new cities

    public SyncCitiesPacket(List<CityInfo> cities, boolean canCreateCity) {
        this.cities = cities;
        this.canCreateCity = canCreateCity;
    }

    public SyncCitiesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.cities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cities.add(new CityInfo(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean()
            ));
        }
        this.canCreateCity = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cities.size());
        for (CityInfo city : cities) {
            buf.writeUtf(city.name, 64);
            buf.writeUtf(city.mayorName, 64);
            buf.writeVarInt(city.chunkCount);
            buf.writeVarInt(city.residentCount);
            buf.writeBoolean(city.isResident);
            buf.writeBoolean(city.isPublicJoin);
        }
        buf.writeBoolean(canCreateCity);
    }

    public List<CityInfo> getCities() {
        return cities;
    }

    public boolean canCreateCity() {
        return canCreateCity;
    }

    public static class CityInfo {
        public final String name;
        public final String mayorName;
        public final int chunkCount;
        public final int residentCount;
        public final boolean isResident;
        public final boolean isPublicJoin;

        public CityInfo(String name, String mayorName, int chunkCount, int residentCount, boolean isResident, boolean isPublicJoin) {
            this.name = name;
            this.mayorName = mayorName;
            this.chunkCount = chunkCount;
            this.residentCount = residentCount;
            this.isResident = isResident;
            this.isPublicJoin = isPublicJoin;
        }

        // Backward compatibility constructor
        public CityInfo(String name, String mayorName, int chunkCount, int residentCount) {
            this(name, mayorName, chunkCount, residentCount, false, false);
        }
    }
}

