package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet to update Nation/State/City settings (name, flag URL, tax rates, etc.)
 */
public class UpdateEntitySettingsPacket {

    public enum EntityType {
        NATION,
        STATE,
        CITY
    }

    private final EntityType entityType;
    private final String entityName; // Current name to identify entity
    private final String newName;
    private final String flagUrl;
    private final boolean publicJoin; // Only used for CITY type
    private final double taxRate; // Base tax rate (for CITY) or pass-through rate (for STATE/NATION)
    private final double passThroughRate; // Pass-through rate to higher level

    public UpdateEntitySettingsPacket(EntityType entityType, String entityName, String newName, String flagUrl) {
        this(entityType, entityName, newName, flagUrl, false, -1, -1);
    }

    public UpdateEntitySettingsPacket(EntityType entityType, String entityName, String newName, String flagUrl, boolean publicJoin) {
        this(entityType, entityName, newName, flagUrl, publicJoin, -1, -1);
    }

    public UpdateEntitySettingsPacket(EntityType entityType, String entityName, String newName, String flagUrl,
                                       boolean publicJoin, double taxRate, double passThroughRate) {
        this.entityType = entityType;
        this.entityName = entityName;
        this.newName = newName;
        this.flagUrl = flagUrl;
        this.publicJoin = publicJoin;
        this.taxRate = taxRate;
        this.passThroughRate = passThroughRate;
    }

    public UpdateEntitySettingsPacket(FriendlyByteBuf buf) {
        this.entityType = buf.readEnum(EntityType.class);
        this.entityName = buf.readUtf(128);
        this.newName = buf.readUtf(128);
        this.flagUrl = buf.readUtf(512);
        this.publicJoin = buf.readBoolean();
        this.taxRate = buf.readDouble();
        this.passThroughRate = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(entityType);
        buf.writeUtf(entityName, 128);
        buf.writeUtf(newName, 128);
        buf.writeUtf(flagUrl, 512);
        buf.writeBoolean(publicJoin);
        buf.writeDouble(taxRate);
        buf.writeDouble(passThroughRate);
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getNewName() {
        return newName;
    }

    public String getFlagUrl() {
        return flagUrl;
    }

    public boolean isPublicJoin() {
        return publicJoin;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public double getPassThroughRate() {
        return passThroughRate;
    }

    public boolean hasTaxSettings() {
        return taxRate >= 0 || passThroughRate >= 0;
    }
}

