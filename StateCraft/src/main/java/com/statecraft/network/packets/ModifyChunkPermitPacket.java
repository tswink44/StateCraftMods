package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet to modify chunk permits (grant or revoke)
 */
public class ModifyChunkPermitPacket {

    public enum Action {
        GRANT,
        REVOKE
    }

    private final int chunkX;
    private final int chunkZ;
    private final String playerName; // Name of player to grant/revoke permit
    private final Action action;

    public ModifyChunkPermitPacket(int chunkX, int chunkZ, String playerName, Action action) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.playerName = playerName;
        this.action = action;
    }

    public ModifyChunkPermitPacket(FriendlyByteBuf buf) {
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
        this.playerName = buf.readUtf();
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
        buf.writeUtf(playerName);
        buf.writeEnum(action);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Action getAction() {
        return action;
    }
}

