package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> Client packet to tell client to open a specific GUI screen
 */
public class OpenGuiPacket {

    public enum ScreenType {
        MAIN_MENU,
        NATION_INFO,
        STATE_INFO,
        CITY_INFO,
        CHUNK_INFO
    }

    private final ScreenType screenType;
    private final String nationName;
    private final String stateName;
    private final String cityName;
    private final int chunkX;
    private final int chunkZ;

    // Default constructor for main menu
    public OpenGuiPacket() {
        this(ScreenType.MAIN_MENU, "", "", "", 0, 0);
    }

    public OpenGuiPacket(ScreenType screenType, String nationName, String stateName, String cityName, int chunkX, int chunkZ) {
        this.screenType = screenType;
        this.nationName = nationName != null ? nationName : "";
        this.stateName = stateName != null ? stateName : "";
        this.cityName = cityName != null ? cityName : "";
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public OpenGuiPacket(FriendlyByteBuf buf) {
        this.screenType = buf.readEnum(ScreenType.class);
        this.nationName = buf.readUtf(64);
        this.stateName = buf.readUtf(64);
        this.cityName = buf.readUtf(64);
        this.chunkX = buf.readInt();
        this.chunkZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(screenType);
        buf.writeUtf(nationName, 64);
        buf.writeUtf(stateName, 64);
        buf.writeUtf(cityName, 64);
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
    }

    public ScreenType getScreenType() { return screenType; }
    public String getNationName() { return nationName; }
    public String getStateName() { return stateName; }
    public String getCityName() { return cityName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
}

