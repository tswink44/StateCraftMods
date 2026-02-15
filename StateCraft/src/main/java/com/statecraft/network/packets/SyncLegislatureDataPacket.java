package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client: Sync legislature data for display
 */
public class SyncLegislatureDataPacket {

    private final String nationName;
    private final boolean isLegislatureMember;
    private final boolean isNationLeader;
    private final List<BillSummary> activeBills;
    private final List<BillSummary> recentHistory;
    private final List<String> votingMemberNames;
    private final int totalMembers;

    public SyncLegislatureDataPacket(String nationName, boolean isLegislatureMember, boolean isNationLeader,
                                      List<BillSummary> activeBills, List<BillSummary> recentHistory,
                                      List<String> votingMemberNames, int totalMembers) {
        this.nationName = nationName;
        this.isLegislatureMember = isLegislatureMember;
        this.isNationLeader = isNationLeader;
        this.activeBills = activeBills;
        this.recentHistory = recentHistory;
        this.votingMemberNames = votingMemberNames;
        this.totalMembers = totalMembers;
    }

    public SyncLegislatureDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.isLegislatureMember = buf.readBoolean();
        this.isNationLeader = buf.readBoolean();
        this.totalMembers = buf.readVarInt();

        // Read active bills
        int activeBillCount = buf.readVarInt();
        this.activeBills = new ArrayList<>(activeBillCount);
        for (int i = 0; i < activeBillCount; i++) {
            activeBills.add(BillSummary.decode(buf));
        }

        // Read history
        int historyCount = buf.readVarInt();
        this.recentHistory = new ArrayList<>(historyCount);
        for (int i = 0; i < historyCount; i++) {
            recentHistory.add(BillSummary.decode(buf));
        }

        // Read voting member names
        int memberCount = buf.readVarInt();
        this.votingMemberNames = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            votingMemberNames.add(buf.readUtf(64));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeBoolean(isLegislatureMember);
        buf.writeBoolean(isNationLeader);
        buf.writeVarInt(totalMembers);

        // Write active bills
        buf.writeVarInt(activeBills.size());
        for (BillSummary bill : activeBills) {
            bill.encode(buf);
        }

        // Write history
        buf.writeVarInt(recentHistory.size());
        for (BillSummary bill : recentHistory) {
            bill.encode(buf);
        }

        // Write voting member names
        buf.writeVarInt(votingMemberNames.size());
        for (String name : votingMemberNames) {
            buf.writeUtf(name, 64);
        }
    }

    // Getters
    public String getNationName() { return nationName; }
    public boolean isLegislatureMember() { return isLegislatureMember; }
    public boolean isNationLeader() { return isNationLeader; }
    public List<BillSummary> getActiveBills() { return activeBills; }
    public List<BillSummary> getRecentHistory() { return recentHistory; }
    public List<String> getVotingMemberNames() { return votingMemberNames; }
    public int getTotalMembers() { return totalMembers; }

    /**
     * Summary of a bill for display in the GUI
     */
    public static class BillSummary {
        private final String billId;
        private final String billNumber;
        private final String title;
        private final String authorName;
        private final String status;
        private final int yesVotes;
        private final int noVotes;
        private final long timeRemaining; // ms remaining for debate/vote
        private final boolean playerHasVoted;
        private final boolean needsLeaderAction;

        public BillSummary(String billId, String billNumber, String title, String authorName,
                           String status, int yesVotes, int noVotes, long timeRemaining,
                           boolean playerHasVoted, boolean needsLeaderAction) {
            this.billId = billId;
            this.billNumber = billNumber;
            this.title = title;
            this.authorName = authorName;
            this.status = status;
            this.yesVotes = yesVotes;
            this.noVotes = noVotes;
            this.timeRemaining = timeRemaining;
            this.playerHasVoted = playerHasVoted;
            this.needsLeaderAction = needsLeaderAction;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(billId, 64);
            buf.writeUtf(billNumber, 32);
            buf.writeUtf(title, 128);
            buf.writeUtf(authorName, 64);
            buf.writeUtf(status, 32);
            buf.writeVarInt(yesVotes);
            buf.writeVarInt(noVotes);
            buf.writeLong(timeRemaining);
            buf.writeBoolean(playerHasVoted);
            buf.writeBoolean(needsLeaderAction);
        }

        public static BillSummary decode(FriendlyByteBuf buf) {
            return new BillSummary(
                buf.readUtf(64),
                buf.readUtf(32),
                buf.readUtf(128),
                buf.readUtf(64),
                buf.readUtf(32),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readBoolean(),
                buf.readBoolean()
            );
        }

        // Getters
        public String getBillId() { return billId; }
        public String getBillNumber() { return billNumber; }
        public String getTitle() { return title; }
        public String getAuthorName() { return authorName; }
        public String getStatus() { return status; }
        public int getYesVotes() { return yesVotes; }
        public int getNoVotes() { return noVotes; }
        public long getTimeRemaining() { return timeRemaining; }
        public boolean hasPlayerVoted() { return playerHasVoted; }
        public boolean needsLeaderAction() { return needsLeaderAction; }
    }
}

