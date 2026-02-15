package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client: Sync city settings for the settings screen
 */
public class SyncCitySettingsPacket {
    private final String cityName;
    private final String description;
    private final String flagUrl;
    private final boolean publicJoin;
    private final double taxRate;
    private final double salesTaxRate;
    private final double statePassThroughRate;

    public SyncCitySettingsPacket(String cityName, String description, String flagUrl,
                                   boolean publicJoin, double taxRate, double salesTaxRate,
                                   double statePassThroughRate) {
        this.cityName = cityName;
        this.description = description != null ? description : "";
        this.flagUrl = flagUrl != null ? flagUrl : "";
        this.publicJoin = publicJoin;
        this.taxRate = taxRate;
        this.salesTaxRate = salesTaxRate;
        this.statePassThroughRate = statePassThroughRate;
    }

    public SyncCitySettingsPacket(FriendlyByteBuf buf) {
        this.cityName = buf.readUtf(64);
        this.description = buf.readUtf(256);
        this.flagUrl = buf.readUtf(512);
        this.publicJoin = buf.readBoolean();
        this.taxRate = buf.readDouble();
        this.salesTaxRate = buf.readDouble();
        this.statePassThroughRate = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cityName, 64);
        buf.writeUtf(description, 256);
        buf.writeUtf(flagUrl, 512);
        buf.writeBoolean(publicJoin);
        buf.writeDouble(taxRate);
        buf.writeDouble(salesTaxRate);
        buf.writeDouble(statePassThroughRate);
    }

    public String getCityName() { return cityName; }
    public String getDescription() { return description; }
    public String getFlagUrl() { return flagUrl; }
    public boolean isPublicJoin() { return publicJoin; }
    public double getTaxRate() { return taxRate; }
    public double getSalesTaxRate() { return salesTaxRate; }
    public double getStatePassThroughRate() { return statePassThroughRate; }
}

