package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: Sync officer management data (citizens and current officers)
 */
public class SyncOfficerManagementDataPacket {

    public static final int MAX_OFFICERS = 3;

    private final String nationName;
    private final boolean isLeader;
    private final List<CitizenInfo> citizens;
    private final List<OfficerInfo> officers;

    public SyncOfficerManagementDataPacket(String nationName, boolean isLeader,
                                            List<CitizenInfo> citizens, List<OfficerInfo> officers) {
        this.nationName = nationName;
        this.isLeader = isLeader;
        this.citizens = citizens;
        this.officers = officers;
    }

    public SyncOfficerManagementDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.isLeader = buf.readBoolean();

        int citizenCount = buf.readVarInt();
        this.citizens = new ArrayList<>(citizenCount);
        for (int i = 0; i < citizenCount; i++) {
            citizens.add(CitizenInfo.decode(buf));
        }

        int officerCount = buf.readVarInt();
        this.officers = new ArrayList<>(officerCount);
        for (int i = 0; i < officerCount; i++) {
            officers.add(OfficerInfo.decode(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeBoolean(isLeader);

        buf.writeVarInt(citizens.size());
        for (CitizenInfo citizen : citizens) {
            citizen.encode(buf);
        }

        buf.writeVarInt(officers.size());
        for (OfficerInfo officer : officers) {
            officer.encode(buf);
        }
    }

    public String getNationName() { return nationName; }
    public boolean isLeader() { return isLeader; }
    public List<CitizenInfo> getCitizens() { return citizens; }
    public List<OfficerInfo> getOfficers() { return officers; }

    public boolean canAppointMore() {
        return officers.size() < MAX_OFFICERS;
    }

    /**
     * Info about a citizen who can potentially be appointed as officer
     */
    public static class CitizenInfo {
        private final UUID playerId;
        private final String playerName;
        private final boolean isGovernor; // Already a governor (can still be officer)
        private final boolean isOnline;

        public CitizenInfo(UUID playerId, String playerName, boolean isGovernor, boolean isOnline) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.isGovernor = isGovernor;
            this.isOnline = isOnline;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(playerName, 64);
            buf.writeBoolean(isGovernor);
            buf.writeBoolean(isOnline);
        }

        public static CitizenInfo decode(FriendlyByteBuf buf) {
            return new CitizenInfo(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readBoolean()
            );
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public boolean isGovernor() { return isGovernor; }
        public boolean isOnline() { return isOnline; }
    }

    /**
     * Info about a current officer
     */
    public static class OfficerInfo {
        private final UUID playerId;
        private final String playerName;
        private final boolean isOnline;

        public OfficerInfo(UUID playerId, String playerName, boolean isOnline) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.isOnline = isOnline;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(playerName, 64);
            buf.writeBoolean(isOnline);
        }

        public static OfficerInfo decode(FriendlyByteBuf buf) {
            return new OfficerInfo(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readBoolean()
            );
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public boolean isOnline() { return isOnline; }
    }
}

