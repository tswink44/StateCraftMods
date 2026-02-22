package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to request government entity mail data
 */
public class RequestGovMailDataPacket {

    public enum EntityType {
        NATION,
        STATE,
        CITY,
        COMPANY
    }

    public enum Action {
        GET_INBOX,      // Request inbox contents
        MARK_ALL_READ   // Mark all mail as read
    }

    private final EntityType entityType;
    private final String entityName;
    private final Action action;

    public RequestGovMailDataPacket(EntityType entityType, String entityName) {
        this(entityType, entityName, Action.GET_INBOX);
    }

    public RequestGovMailDataPacket(EntityType entityType, String entityName, Action action) {
        this.entityType = entityType;
        this.entityName = entityName;
        this.action = action;
    }

    public RequestGovMailDataPacket(FriendlyByteBuf buf) {
        this.entityType = buf.readEnum(EntityType.class);
        this.entityName = buf.readUtf(64);
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(entityType);
        buf.writeUtf(entityName, 64);
        buf.writeEnum(action);
    }

    public EntityType getEntityType() { return entityType; }
    public String getEntityName() { return entityName; }
    public Action getAction() { return action; }
}

