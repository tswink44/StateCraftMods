package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sync state details
 */
public class SyncStateDetailsPacket {
    private final String stateName;
    private final String governorName;
    private final int cityCount;
    private final int chunkCount;
    private final int memberCount;
    private final boolean isGovernor;
    private final boolean canManage;
    private final List<String> cityNames;
    private final boolean isNationLeader;
    private final String flagUrl;

    public SyncStateDetailsPacket(String stateName, String governorName, int cityCount, int chunkCount,
                                   int memberCount, boolean isGovernor, boolean canManage, List<String> cityNames) {
        this(stateName, governorName, cityCount, chunkCount, memberCount, isGovernor, canManage, cityNames, false, "");
    }

    public SyncStateDetailsPacket(String stateName, String governorName, int cityCount, int chunkCount,
                                   int memberCount, boolean isGovernor, boolean canManage, List<String> cityNames,
                                   boolean isNationLeader) {
        this(stateName, governorName, cityCount, chunkCount, memberCount, isGovernor, canManage, cityNames, isNationLeader, "");
    }

    public SyncStateDetailsPacket(String stateName, String governorName, int cityCount, int chunkCount,
                                   int memberCount, boolean isGovernor, boolean canManage, List<String> cityNames,
                                   boolean isNationLeader, String flagUrl) {
        this.stateName = stateName;
        this.governorName = governorName;
        this.cityCount = cityCount;
        this.chunkCount = chunkCount;
        this.memberCount = memberCount;
        this.isGovernor = isGovernor;
        this.canManage = canManage;
        this.cityNames = cityNames;
        this.isNationLeader = isNationLeader;
        this.flagUrl = flagUrl != null ? flagUrl : "";
    }

    public SyncStateDetailsPacket(FriendlyByteBuf buf) {
        this.stateName = buf.readUtf(64);
        this.governorName = buf.readUtf(64);
        this.cityCount = buf.readVarInt();
        this.chunkCount = buf.readVarInt();
        this.memberCount = buf.readVarInt();
        this.isGovernor = buf.readBoolean();
        this.canManage = buf.readBoolean();
        int count = buf.readVarInt();
        this.cityNames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cityNames.add(buf.readUtf(64));
        }
        this.isNationLeader = buf.readBoolean();
        this.flagUrl = buf.readUtf(512);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(stateName, 64);
        buf.writeUtf(governorName, 64);
        buf.writeVarInt(cityCount);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(memberCount);
        buf.writeBoolean(isGovernor);
        buf.writeBoolean(canManage);
        buf.writeVarInt(cityNames.size());
        for (String name : cityNames) {
            buf.writeUtf(name, 64);
        }
        buf.writeBoolean(isNationLeader);
        buf.writeUtf(flagUrl, 512);
    }

    public String getStateName() { return stateName; }
    public String getGovernorName() { return governorName; }
    public int getCityCount() { return cityCount; }
    public int getChunkCount() { return chunkCount; }
    public int getMemberCount() { return memberCount; }
    public boolean isGovernor() { return isGovernor; }
    public boolean canManage() { return canManage; }
    public List<String> getCityNames() { return cityNames; }
    public boolean isNationLeader() { return isNationLeader; }
    public String getFlagUrl() { return flagUrl; }
}

