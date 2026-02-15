package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to join a state or city as a citizen
 */
public class JoinCitizenshipPacket {

    public enum JoinType {
        NATION,
        STATE,
        CITY
    }

    private final JoinType type;
    private final String nationName;
    private final String stateName;
    private final String cityName; // Only used for CITY type

    public JoinCitizenshipPacket(JoinType type, String nationName, String stateName, String cityName) {
        this.type = type;
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName != null ? cityName : "";
    }

    public JoinCitizenshipPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(JoinType.class);
        this.nationName = buf.readUtf(64);
        this.stateName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUtf(nationName, 64);
        buf.writeUtf(stateName, 64);
        buf.writeUtf(cityName, 64);
    }

    public JoinType getType() {
        return type;
    }

    public String getNationName() {
        return nationName;
    }

    public String getStateName() {
        return stateName;
    }

    public String getCityName() {
        return cityName;
    }

    // Helper factory methods
    public static JoinCitizenshipPacket joinNation(String nationName) {
        return new JoinCitizenshipPacket(JoinType.NATION, nationName, "", "");
    }

    public static JoinCitizenshipPacket joinState(String nationName, String stateName) {
        return new JoinCitizenshipPacket(JoinType.STATE, nationName, stateName, "");
    }

    public static JoinCitizenshipPacket joinCity(String nationName, String stateName, String cityName) {
        return new JoinCitizenshipPacket(JoinType.CITY, nationName, stateName, cityName);
    }
}

