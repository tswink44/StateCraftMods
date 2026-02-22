package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync city details
 */
public class SyncCityDetailsPacket {
    private final String cityName;
    private final String mayorName;
    private final int chunkCount;
    private final int residentCount;
    private final boolean isMayor;
    private final boolean canManage;
    private final List<String> residentNames;
    private final boolean canAppoint; // true if player is governor or nation leader (can appoint mayor)

    public SyncCityDetailsPacket(String cityName, String mayorName, int chunkCount, int residentCount,
                                  boolean isMayor, boolean canManage, List<String> residentNames) {
        this(cityName, mayorName, chunkCount, residentCount, isMayor, canManage, residentNames, false);
    }

    public SyncCityDetailsPacket(String cityName, String mayorName, int chunkCount, int residentCount,
                                  boolean isMayor, boolean canManage, List<String> residentNames, boolean canAppoint) {
        this.cityName = cityName;
        this.mayorName = mayorName;
        this.chunkCount = chunkCount;
        this.residentCount = residentCount;
        this.isMayor = isMayor;
        this.canManage = canManage;
        this.residentNames = residentNames;
        this.canAppoint = canAppoint;
    }

    public SyncCityDetailsPacket(FriendlyByteBuf buf) {
        this.cityName = buf.readUtf(64);
        this.mayorName = buf.readUtf(64);
        this.chunkCount = buf.readVarInt();
        this.residentCount = buf.readVarInt();
        this.isMayor = buf.readBoolean();
        this.canManage = buf.readBoolean();
        int count = buf.readVarInt();
        this.residentNames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            residentNames.add(buf.readUtf(64));
        }
        this.canAppoint = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cityName, 64);
        buf.writeUtf(mayorName, 64);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(residentCount);
        buf.writeBoolean(isMayor);
        buf.writeBoolean(canManage);
        buf.writeVarInt(residentNames.size());
        for (String name : residentNames) {
            buf.writeUtf(name, 64);
        }
        buf.writeBoolean(canAppoint);
    }

    public String getCityName() { return cityName; }
    public String getMayorName() { return mayorName; }
    public int getChunkCount() { return chunkCount; }
    public int getResidentCount() { return residentCount; }
    public boolean isMayor() { return isMayor; }
    public boolean canManage() { return canManage; }
    public List<String> getResidentNames() { return residentNames; }
    public boolean canAppoint() { return canAppoint; }
}

