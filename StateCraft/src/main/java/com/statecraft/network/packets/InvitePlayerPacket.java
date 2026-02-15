package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to invite a player to a nation
 */
public class InvitePlayerPacket {

    public enum InviteType {
        NATION,
        STATE,
        CITY
    }

    private final InviteType type;
    private final String entityName; // Nation/State/City name
    private final String playerName; // Player to invite

    public InvitePlayerPacket(InviteType type, String entityName, String playerName) {
        this.type = type;
        this.entityName = entityName;
        this.playerName = playerName;
    }

    public InvitePlayerPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(InviteType.class);
        this.entityName = buf.readUtf(64);
        this.playerName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUtf(entityName, 64);
        buf.writeUtf(playerName, 64);
    }

    public InviteType getType() {
        return type;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getPlayerName() {
        return playerName;
    }

    // Helper factory methods
    public static InvitePlayerPacket inviteToNation(String nationName, String playerName) {
        return new InvitePlayerPacket(InviteType.NATION, nationName, playerName);
    }

    public static InvitePlayerPacket inviteToState(String stateName, String playerName) {
        return new InvitePlayerPacket(InviteType.STATE, stateName, playerName);
    }

    public static InvitePlayerPacket inviteToCity(String cityName, String playerName) {
        return new InvitePlayerPacket(InviteType.CITY, cityName, playerName);
    }
}

