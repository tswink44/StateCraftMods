package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet to sync nation data
 * Used for both basic summary and detailed info
 */
public class SyncNationDataPacket {
    // Basic data
    private final boolean inNation;
    private final String nationName;
    private final String stateName;
    private final String cityName;
    private final int chunks;
    private final int members;
    private final boolean isLeader;
    private final boolean isOfficer; // Was "isAdmin" — now represents officer status
    private final boolean isMember; // Is the player a member of THIS nation

    // Detailed data
    private final boolean detailedData;
    private final int states;
    private final int maxStates;
    private final int cities;
    private final double balance;
    private final boolean open;
    private final String description;
    private final String leaderName;
    private final List<String> stateNames;
    private final List<String> allyNames;
    private final List<String> enemyNames;

    // No nation constructor
    public SyncNationDataPacket() {
        this.inNation = false;
        this.nationName = "";
        this.stateName = "";
        this.cityName = "";
        this.chunks = 0;
        this.members = 0;
        this.isLeader = false;
        this.isOfficer = false;
        this.isMember = false;
        this.detailedData = false;
        this.states = 0;
        this.maxStates = 0;
        this.cities = 0;
        this.balance = 0;
        this.open = false;
        this.description = "";
        this.leaderName = "";
        this.stateNames = new ArrayList<>();
        this.allyNames = new ArrayList<>();
        this.enemyNames = new ArrayList<>();
    }

    // Basic data constructor
    public SyncNationDataPacket(String nationName, String stateName, String cityName,
                                 int chunks, int members, boolean isLeader, boolean isOfficer) {
        this.inNation = true;
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.chunks = chunks;
        this.members = members;
        this.isLeader = isLeader;
        this.isOfficer = isOfficer;
        this.isMember = true; // Basic constructor implies membership
        this.detailedData = false;
        this.states = 0;
        this.maxStates = 0;
        this.cities = 0;
        this.balance = 0;
        this.open = false;
        this.description = "";
        this.leaderName = "";
        this.stateNames = new ArrayList<>();
        this.allyNames = new ArrayList<>();
        this.enemyNames = new ArrayList<>();
    }

    // Detailed data constructor
    public SyncNationDataPacket(String nationName, int states, int maxStates, int cities, int chunks,
                                 int members, double balance, boolean open, String description,
                                 String leaderName, boolean isLeader, boolean isOfficer, boolean isMember,
                                 List<String> stateNames, List<String> allyNames, List<String> enemyNames) {
        this.inNation = true;
        this.nationName = nationName;
        this.stateName = "";
        this.cityName = "";
        this.chunks = chunks;
        this.members = members;
        this.isLeader = isLeader;
        this.isOfficer = isOfficer;
        this.isMember = isMember;
        this.detailedData = true;
        this.states = states;
        this.maxStates = maxStates;
        this.cities = cities;
        this.balance = balance;
        this.open = open;
        this.description = description;
        this.leaderName = leaderName;
        this.stateNames = stateNames;
        this.allyNames = allyNames;
        this.enemyNames = enemyNames;
    }

    public SyncNationDataPacket(FriendlyByteBuf buf) {
        this.inNation = buf.readBoolean();
        this.nationName = buf.readUtf(24);
        this.stateName = buf.readUtf(24);
        this.cityName = buf.readUtf(24);
        this.chunks = buf.readInt();
        this.members = buf.readInt();
        this.isLeader = buf.readBoolean();
        this.isOfficer = buf.readBoolean();
        this.isMember = buf.readBoolean();
        this.detailedData = buf.readBoolean();
        this.states = buf.readInt();
        this.maxStates = buf.readInt();
        this.cities = buf.readInt();
        this.balance = buf.readDouble();
        this.open = buf.readBoolean();
        this.description = buf.readUtf(100);
        this.leaderName = buf.readUtf(16);

        int stateCount = buf.readInt();
        this.stateNames = new ArrayList<>();
        for (int i = 0; i < stateCount; i++) {
            stateNames.add(buf.readUtf(24));
        }

        int allyCount = buf.readInt();
        this.allyNames = new ArrayList<>();
        for (int i = 0; i < allyCount; i++) {
            allyNames.add(buf.readUtf(24));
        }

        int enemyCount = buf.readInt();
        this.enemyNames = new ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            enemyNames.add(buf.readUtf(24));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(inNation);
        buf.writeUtf(nationName, 24);
        buf.writeUtf(stateName, 24);
        buf.writeUtf(cityName, 24);
        buf.writeInt(chunks);
        buf.writeInt(members);
        buf.writeBoolean(isLeader);
        buf.writeBoolean(isOfficer);
        buf.writeBoolean(isMember);
        buf.writeBoolean(detailedData);
        buf.writeInt(states);
        buf.writeInt(maxStates);
        buf.writeInt(cities);
        buf.writeDouble(balance);
        buf.writeBoolean(open);
        buf.writeUtf(description, 100);
        buf.writeUtf(leaderName, 16);

        buf.writeInt(stateNames.size());
        for (String name : stateNames) {
            buf.writeUtf(name, 24);
        }

        buf.writeInt(allyNames.size());
        for (String name : allyNames) {
            buf.writeUtf(name, 24);
        }

        buf.writeInt(enemyNames.size());
        for (String name : enemyNames) {
            buf.writeUtf(name, 24);
        }
    }

    // Getters
    public boolean isInNation() { return inNation; }
    public String getNationName() { return nationName; }
    public String getStateName() { return stateName; }
    public String getCityName() { return cityName; }
    public int getChunks() { return chunks; }
    public int getMembers() { return members; }
    public boolean isLeader() { return isLeader; }
    public boolean isOfficer() { return isOfficer; }
    public boolean isMember() { return isMember; }
    public boolean isDetailedData() { return detailedData; }
    public int getStates() { return states; }
    public int getMaxStates() { return maxStates; }
    public int getCities() { return cities; }
    public double getBalance() { return balance; }
    public boolean isOpen() { return open; }
    public String getDescription() { return description; }
    public String getLeaderName() { return leaderName; }
    public List<String> getStateNames() { return stateNames; }
    public List<String> getAllyNames() { return allyNames; }
    public List<String> getEnemyNames() { return enemyNames; }
}

