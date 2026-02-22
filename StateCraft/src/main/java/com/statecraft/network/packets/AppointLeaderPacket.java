package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Appoint a new governor for a state or a new mayor for a city.
 *
 * Leaders can appoint governors.
 * Governors can appoint mayors.
 */
public class AppointLeaderPacket {

    public enum AppointmentType {
        GOVERNOR,
        MAYOR
    }

    private final String nationName;
    private final String stateName;
    private final String cityName;    // Only used for MAYOR appointments
    private final String targetPlayer; // Player name to appoint
    private final AppointmentType type;

    public AppointLeaderPacket(String nationName, String stateName, String cityName, String targetPlayer, AppointmentType type) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName != null ? cityName : "";
        this.targetPlayer = targetPlayer;
        this.type = type;
    }

    public AppointLeaderPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.stateName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
        this.targetPlayer = buf.readUtf(64);
        this.type = buf.readEnum(AppointmentType.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(stateName, 64);
        buf.writeUtf(cityName, 64);
        buf.writeUtf(targetPlayer, 64);
        buf.writeEnum(type);
    }

    public String getNationName() { return nationName; }
    public String getStateName() { return stateName; }
    public String getCityName() { return cityName; }
    public String getTargetPlayer() { return targetPlayer; }
    public AppointmentType getType() { return type; }

    /**
     * Create a packet to appoint a governor for a state
     */
    public static AppointLeaderPacket appointGovernor(String nationName, String stateName, String targetPlayer) {
        return new AppointLeaderPacket(nationName, stateName, "", targetPlayer, AppointmentType.GOVERNOR);
    }

    /**
     * Create a packet to appoint a mayor for a city
     */
    public static AppointLeaderPacket appointMayor(String nationName, String stateName, String cityName, String targetPlayer) {
        return new AppointLeaderPacket(nationName, stateName, cityName, targetPlayer, AppointmentType.MAYOR);
    }
}

