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
        private final String description;
        private final String authorName;
        private final String status;
        private final int yesVotes;
        private final int noVotes;
        private final int abstainVotes;
        private final long timeRemaining; // ms remaining for debate/vote
        private final long enactedTime;
        private final boolean playerHasVoted;
        private final boolean needsLeaderAction;
        private final boolean isVetoProof;
        private final boolean isConstitutionalAmendment;
        private final java.util.Map<String, String> policyChanges;
        private final String fullText;

        public BillSummary(String billId, String billNumber, String title, String authorName,
                           String status, int yesVotes, int noVotes, long timeRemaining,
                           boolean playerHasVoted, boolean needsLeaderAction) {
            this(billId, billNumber, title, "", authorName, status, yesVotes, noVotes, 0,
                 timeRemaining, 0L, playerHasVoted, needsLeaderAction, false, false,
                 new java.util.HashMap<>(), "");
        }

        public BillSummary(String billId, String billNumber, String title, String description,
                           String authorName, String status, int yesVotes, int noVotes, int abstainVotes,
                           long timeRemaining, long enactedTime, boolean playerHasVoted, boolean needsLeaderAction,
                           boolean isVetoProof, boolean isConstitutionalAmendment,
                           java.util.Map<String, String> policyChanges, String fullText) {
            this.billId = billId;
            this.billNumber = billNumber;
            this.title = title;
            this.description = description != null ? description : "";
            this.authorName = authorName;
            this.status = status;
            this.yesVotes = yesVotes;
            this.noVotes = noVotes;
            this.abstainVotes = abstainVotes;
            this.timeRemaining = timeRemaining;
            this.enactedTime = enactedTime;
            this.playerHasVoted = playerHasVoted;
            this.needsLeaderAction = needsLeaderAction;
            this.isVetoProof = isVetoProof;
            this.isConstitutionalAmendment = isConstitutionalAmendment;
            this.policyChanges = policyChanges != null ? policyChanges : new java.util.HashMap<>();
            this.fullText = fullText != null ? fullText : "";
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(billId, 64);
            buf.writeUtf(billNumber, 32);
            buf.writeUtf(title, 128);
            buf.writeUtf(description, 512);
            buf.writeUtf(authorName, 64);
            buf.writeUtf(status, 32);
            buf.writeVarInt(yesVotes);
            buf.writeVarInt(noVotes);
            buf.writeVarInt(abstainVotes);
            buf.writeLong(timeRemaining);
            buf.writeLong(enactedTime);
            buf.writeBoolean(playerHasVoted);
            buf.writeBoolean(needsLeaderAction);
            buf.writeBoolean(isVetoProof);
            buf.writeBoolean(isConstitutionalAmendment);

            // Policy changes
            buf.writeVarInt(policyChanges.size());
            for (java.util.Map.Entry<String, String> entry : policyChanges.entrySet()) {
                buf.writeUtf(entry.getKey(), 64);
                buf.writeUtf(entry.getValue(), 256);
            }

            buf.writeUtf(fullText, 2048);
        }

        public static BillSummary decode(FriendlyByteBuf buf) {
            String billId = buf.readUtf(64);
            String billNumber = buf.readUtf(32);
            String title = buf.readUtf(128);
            String description = buf.readUtf(512);
            String authorName = buf.readUtf(64);
            String status = buf.readUtf(32);
            int yesVotes = buf.readVarInt();
            int noVotes = buf.readVarInt();
            int abstainVotes = buf.readVarInt();
            long timeRemaining = buf.readLong();
            long enactedTime = buf.readLong();
            boolean playerHasVoted = buf.readBoolean();
            boolean needsLeaderAction = buf.readBoolean();
            boolean isVetoProof = buf.readBoolean();
            boolean isConstitutionalAmendment = buf.readBoolean();

            int policyCount = buf.readVarInt();
            java.util.Map<String, String> policyChanges = new java.util.HashMap<>();
            for (int i = 0; i < policyCount; i++) {
                String key = buf.readUtf(64);
                String value = buf.readUtf(256);
                policyChanges.put(key, value);
            }

            String fullText = buf.readUtf(2048);

            return new BillSummary(
                billId, billNumber, title, description, authorName, status,
                yesVotes, noVotes, abstainVotes, timeRemaining, enactedTime,
                playerHasVoted, needsLeaderAction, isVetoProof, isConstitutionalAmendment,
                policyChanges, fullText
            );
        }

        // Getters
        public String getBillId() { return billId; }
        public String getBillNumber() { return billNumber; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getAuthorName() { return authorName; }
        public String getStatus() { return status; }
        public int getYesVotes() { return yesVotes; }
        public int getNoVotes() { return noVotes; }
        public int getAbstainVotes() { return abstainVotes; }
        public long getTimeRemaining() { return timeRemaining; }
        public long getEnactedTime() { return enactedTime; }
        public boolean hasPlayerVoted() { return playerHasVoted; }
        public boolean needsLeaderAction() { return needsLeaderAction; }
        public boolean isVetoProof() { return isVetoProof; }
        public boolean isConstitutionalAmendment() { return isConstitutionalAmendment; }
        public java.util.Map<String, String> getPolicyChanges() { return policyChanges; }
        public String getFullText() { return fullText; }
    }
}

