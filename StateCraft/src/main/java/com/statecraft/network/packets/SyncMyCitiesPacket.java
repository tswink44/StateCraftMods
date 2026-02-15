package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: List of cities the player is a resident of
 */
public class SyncMyCitiesPacket {
    private final List<CityEntry> cities;

    public SyncMyCitiesPacket(List<CityEntry> cities) {
        this.cities = cities;
    }

    public SyncMyCitiesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.cities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cities.add(new CityEntry(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readVarInt()
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cities.size());
        for (CityEntry entry : cities) {
            buf.writeUtf(entry.cityName, 64);
            buf.writeUtf(entry.stateName, 64);
            buf.writeUtf(entry.mayorName, 64);
            buf.writeBoolean(entry.isPrimary);
            buf.writeVarInt(entry.ownedChunks);
        }
    }

    public List<CityEntry> getCities() {
        return cities;
    }

    public static class CityEntry {
        public final String cityName;
        public final String stateName;
        public final String mayorName;
        public final boolean isPrimary;
        public final int ownedChunks;

        public CityEntry(String cityName, String stateName, String mayorName, boolean isPrimary, int ownedChunks) {
            this.cityName = cityName;
            this.stateName = stateName;
            this.mayorName = mayorName;
            this.isPrimary = isPrimary;
            this.ownedChunks = ownedChunks;
        }
    }
}

