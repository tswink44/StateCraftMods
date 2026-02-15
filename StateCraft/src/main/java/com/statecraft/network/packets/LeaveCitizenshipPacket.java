package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Request to leave/abandon citizenship of a nation, state, or city
 */
public class LeaveCitizenshipPacket {

    public enum LeaveType {
        NATION,
        STATE,
        CITY
    }

    private final LeaveType type;
    private final String nationName;
    private final String stateName;  // Used for STATE and CITY types
    private final String cityName;   // Only used for CITY type

    public LeaveCitizenshipPacket(LeaveType type, String nationName, String stateName, String cityName) {
        this.type = type;
        this.nationName = nationName != null ? nationName : "";
        this.stateName = stateName != null ? stateName : "";
        this.cityName = cityName != null ? cityName : "";
    }

    public LeaveCitizenshipPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(LeaveType.class);
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

    public LeaveType getType() { return type; }
    public String getNationName() { return nationName; }
    public String getStateName() { return stateName; }
    public String getCityName() { return cityName; }

    // Helper factory methods
    public static LeaveCitizenshipPacket leaveNation(String nationName) {
        return new LeaveCitizenshipPacket(LeaveType.NATION, nationName, "", "");
    }

    public static LeaveCitizenshipPacket leaveState(String nationName, String stateName) {
        return new LeaveCitizenshipPacket(LeaveType.STATE, nationName, stateName, "");
    }

    public static LeaveCitizenshipPacket leaveCity(String nationName, String stateName, String cityName) {
        return new LeaveCitizenshipPacket(LeaveType.CITY, nationName, stateName, cityName);
    }
}

