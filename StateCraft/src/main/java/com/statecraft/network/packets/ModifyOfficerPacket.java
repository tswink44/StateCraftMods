package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Client -> Server: Appoint or remove an officer
 */
public class ModifyOfficerPacket {

    public enum Action {
        APPOINT,
        REMOVE
    }

    private final String nationName;
    private final UUID targetPlayerId;
    private final Action action;

    public ModifyOfficerPacket(String nationName, UUID targetPlayerId, Action action) {
        this.nationName = nationName;
        this.targetPlayerId = targetPlayerId;
        this.action = action;
    }

    public ModifyOfficerPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.targetPlayerId = buf.readUUID();
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUUID(targetPlayerId);
        buf.writeEnum(action);
    }

    public String getNationName() { return nationName; }
    public UUID getTargetPlayerId() { return targetPlayerId; }
    public Action getAction() { return action; }
}

