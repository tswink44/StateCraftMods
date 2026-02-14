package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet for chunk claim/unclaim actions
 */
public class ChunkActionPacket {

    public enum Action {
        CLAIM,
        UNCLAIM,
        TRANSFER,
        INFO
    }

    private final Action action;
    private final int chunkX;
    private final int chunkZ;
    private final String cityName; // For claim action

    public ChunkActionPacket(Action action, int chunkX, int chunkZ, String cityName) {
        this.action = action;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.cityName = cityName != null ? cityName : "";
    }

    public ChunkActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.cityName = buf.readUtf(24);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeUtf(cityName, 24);
    }

    public Action getAction() {
        return action;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public String getCityName() {
        return cityName;
    }
}

