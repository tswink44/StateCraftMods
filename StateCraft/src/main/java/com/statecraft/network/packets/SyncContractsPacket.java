package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> Client: Sync contract data for GUI display
 */
public class SyncContractsPacket {

    private final String nationName;
    private final boolean isLegislatureMember;
    private final boolean isNationLeader;
    private final List<ContractSummary> openBidding;
    private final List<ContractSummary> pendingApproval;
    private final List<ContractSummary> activeContracts;
    private final List<ContractSummary> history;
    private final List<ContractSummary> myContracts; // Player's own contracts as contractor
    private final double nationTreasuryBalance;

    public SyncContractsPacket(String nationName, boolean isLegislatureMember, boolean isNationLeader,
                               List<ContractSummary> openBidding, List<ContractSummary> pendingApproval,
                               List<ContractSummary> activeContracts, List<ContractSummary> history,
                               List<ContractSummary> myContracts, double nationTreasuryBalance) {
        this.nationName = nationName;
        this.isLegislatureMember = isLegislatureMember;
        this.isNationLeader = isNationLeader;
        this.openBidding = openBidding;
        this.pendingApproval = pendingApproval;
        this.activeContracts = activeContracts;
        this.history = history;
        this.myContracts = myContracts;
        this.nationTreasuryBalance = nationTreasuryBalance;
    }

    public SyncContractsPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.isLegislatureMember = buf.readBoolean();
        this.isNationLeader = buf.readBoolean();
        this.nationTreasuryBalance = buf.readDouble();

        this.openBidding = readContractList(buf);
        this.pendingApproval = readContractList(buf);
        this.activeContracts = readContractList(buf);
        this.history = readContractList(buf);
        this.myContracts = readContractList(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeBoolean(isLegislatureMember);
        buf.writeBoolean(isNationLeader);
        buf.writeDouble(nationTreasuryBalance);

        writeContractList(buf, openBidding);
        writeContractList(buf, pendingApproval);
        writeContractList(buf, activeContracts);
        writeContractList(buf, history);
        writeContractList(buf, myContracts);
    }

    private List<ContractSummary> readContractList(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ContractSummary> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(ContractSummary.decode(buf));
        }
        return list;
    }

    private void writeContractList(FriendlyByteBuf buf, List<ContractSummary> list) {
        buf.writeVarInt(list.size());
        for (ContractSummary summary : list) {
            summary.encode(buf);
        }
    }

    // Getters
    public String getNationName() { return nationName; }
    public boolean isLegislatureMember() { return isLegislatureMember; }
    public boolean isNationLeader() { return isNationLeader; }
    public List<ContractSummary> getOpenBidding() { return openBidding; }
    public List<ContractSummary> getPendingApproval() { return pendingApproval; }
    public List<ContractSummary> getActiveContracts() { return activeContracts; }
    public List<ContractSummary> getHistory() { return history; }
    public List<ContractSummary> getMyContracts() { return myContracts; }
    public double getNationTreasuryBalance() { return nationTreasuryBalance; }

    /**
     * Summary of a contract for client display
     */
    public static class ContractSummary {
        private final String contractId;
        private final String contractNumber;
        private final String title;
        private final String description;
        private final String creatorName;
        private final String status;
        private final String compensationType;  // FIXED, MILESTONE, VALUATION_BASED
        private final double budget;
        private final double bondAmount;
        private final int chunkCount;
        private final int bidCount;
        private final long timeRemaining;  // Bidding time or deadline remaining
        private final String contractorName;
        private final int progressPercent;
        private final boolean playerHasBid;
        private final boolean isPlayerContractor;  // Is the current player the contractor?
        private final boolean isPlayerCreator;     // Is the current player the contract creator?
        private final List<BidSummary> bids;  // Only populated for pending approval
        private final java.util.Map<Integer, Boolean> milestonesCompleted;  // Milestone completion status
        private final java.util.Set<Integer> pendingMilestoneApprovals;  // Milestones waiting for approval
        private final java.util.List<int[]> chunkCoordinates;  // List of [x, z] chunk coordinates
        private final String dimension;  // e.g., "minecraft:overworld"
        private final boolean finalApprovalRequested;  // Contractor has submitted for early final approval

        public ContractSummary(String contractId, String contractNumber, String title, String description,
                               String creatorName, String status, String compensationType, double budget, double bondAmount,
                               int chunkCount, int bidCount, long timeRemaining, String contractorName,
                               int progressPercent, boolean playerHasBid, boolean isPlayerContractor, boolean isPlayerCreator,
                               List<BidSummary> bids, java.util.Map<Integer, Boolean> milestonesCompleted,
                               java.util.Set<Integer> pendingMilestoneApprovals,
                               java.util.List<int[]> chunkCoordinates, String dimension,
                               boolean finalApprovalRequested) {
            this.contractId = contractId;
            this.contractNumber = contractNumber;
            this.title = title;
            this.description = description;
            this.creatorName = creatorName;
            this.status = status;
            this.compensationType = compensationType;
            this.budget = budget;
            this.bondAmount = bondAmount;
            this.chunkCount = chunkCount;
            this.bidCount = bidCount;
            this.timeRemaining = timeRemaining;
            this.contractorName = contractorName;
            this.progressPercent = progressPercent;
            this.playerHasBid = playerHasBid;
            this.isPlayerContractor = isPlayerContractor;
            this.isPlayerCreator = isPlayerCreator;
            this.bids = bids;
            this.milestonesCompleted = milestonesCompleted;
            this.pendingMilestoneApprovals = pendingMilestoneApprovals;
            this.chunkCoordinates = chunkCoordinates;
            this.dimension = dimension;
            this.finalApprovalRequested = finalApprovalRequested;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(contractId, 64);
            buf.writeUtf(contractNumber, 32);
            buf.writeUtf(title, 128);
            buf.writeUtf(description, 512);
            buf.writeUtf(creatorName, 64);
            buf.writeUtf(status, 32);
            buf.writeUtf(compensationType != null ? compensationType : "MILESTONE", 32);
            buf.writeDouble(budget);
            buf.writeDouble(bondAmount);
            buf.writeVarInt(chunkCount);
            buf.writeVarInt(bidCount);
            buf.writeLong(timeRemaining);
            buf.writeUtf(contractorName != null ? contractorName : "", 64);
            buf.writeVarInt(progressPercent);
            buf.writeBoolean(playerHasBid);
            buf.writeBoolean(isPlayerContractor);
            buf.writeBoolean(isPlayerCreator);

            buf.writeVarInt(bids.size());
            for (BidSummary bid : bids) {
                bid.encode(buf);
            }

            // Write milestone completion status
            buf.writeVarInt(milestonesCompleted.size());
            for (java.util.Map.Entry<Integer, Boolean> entry : milestonesCompleted.entrySet()) {
                buf.writeVarInt(entry.getKey());
                buf.writeBoolean(entry.getValue());
            }

            // Write pending milestone approvals
            buf.writeVarInt(pendingMilestoneApprovals.size());
            for (Integer milestone : pendingMilestoneApprovals) {
                buf.writeVarInt(milestone);
            }

            // Write chunk coordinates
            buf.writeVarInt(chunkCoordinates.size());
            for (int[] chunk : chunkCoordinates) {
                buf.writeVarInt(chunk[0]);
                buf.writeVarInt(chunk[1]);
            }
            buf.writeUtf(dimension != null ? dimension : "minecraft:overworld", 128);
            buf.writeBoolean(finalApprovalRequested);
        }

        public static ContractSummary decode(FriendlyByteBuf buf) {
            String contractId = buf.readUtf(64);
            String contractNumber = buf.readUtf(32);
            String title = buf.readUtf(128);
            String description = buf.readUtf(512);
            String creatorName = buf.readUtf(64);
            String status = buf.readUtf(32);
            String compensationType = buf.readUtf(32);
            double budget = buf.readDouble();
            double bondAmount = buf.readDouble();
            int chunkCount = buf.readVarInt();
            int bidCount = buf.readVarInt();
            long timeRemaining = buf.readLong();
            String contractorName = buf.readUtf(64);
            int progressPercent = buf.readVarInt();
            boolean playerHasBid = buf.readBoolean();
            boolean isPlayerContractor = buf.readBoolean();
            boolean isPlayerCreator = buf.readBoolean();

            int bidListSize = buf.readVarInt();
            List<BidSummary> bids = new ArrayList<>(bidListSize);
            for (int i = 0; i < bidListSize; i++) {
                bids.add(BidSummary.decode(buf));
            }

            // Read milestone completion status
            int milestoneCount = buf.readVarInt();
            java.util.Map<Integer, Boolean> milestonesCompleted = new java.util.HashMap<>();
            for (int i = 0; i < milestoneCount; i++) {
                int milestone = buf.readVarInt();
                boolean completed = buf.readBoolean();
                milestonesCompleted.put(milestone, completed);
            }

            // Read pending milestone approvals
            int pendingCount = buf.readVarInt();
            java.util.Set<Integer> pendingMilestoneApprovals = new java.util.HashSet<>();
            for (int i = 0; i < pendingCount; i++) {
                pendingMilestoneApprovals.add(buf.readVarInt());
            }

            // Read chunk coordinates
            int chunkCoordCount = buf.readVarInt();
            java.util.List<int[]> chunkCoordinates = new java.util.ArrayList<>(chunkCoordCount);
            for (int i = 0; i < chunkCoordCount; i++) {
                chunkCoordinates.add(new int[]{buf.readVarInt(), buf.readVarInt()});
            }
            String dimension = buf.readUtf(128);
            boolean finalApprovalRequested = buf.readBoolean();

            return new ContractSummary(contractId, contractNumber, title, description, creatorName,
                status, compensationType, budget, bondAmount, chunkCount, bidCount, timeRemaining, contractorName,
                progressPercent, playerHasBid, isPlayerContractor, isPlayerCreator, bids, milestonesCompleted, pendingMilestoneApprovals,
                chunkCoordinates, dimension, finalApprovalRequested);
        }

        // Getters
        public String getContractId() { return contractId; }
        public String getContractNumber() { return contractNumber; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getCreatorName() { return creatorName; }
        public String getStatus() { return status; }
        public String getCompensationType() { return compensationType; }
        public double getBudget() { return budget; }
        public double getBondAmount() { return bondAmount; }
        public int getChunkCount() { return chunkCount; }
        public int getBidCount() { return bidCount; }
        public long getTimeRemaining() { return timeRemaining; }
        public String getContractorName() { return contractorName; }
        public int getProgressPercent() { return progressPercent; }
        public boolean hasPlayerBid() { return playerHasBid; }
        public boolean isPlayerContractor() { return isPlayerContractor; }
        public boolean isPlayerCreator() { return isPlayerCreator; }
        public List<BidSummary> getBids() { return bids; }
        public java.util.Map<Integer, Boolean> getMilestonesCompleted() { return milestonesCompleted; }
        public java.util.Set<Integer> getPendingMilestoneApprovals() { return pendingMilestoneApprovals; }
        public java.util.List<int[]> getChunkCoordinates() { return chunkCoordinates; }
        public String getDimension() { return dimension; }
        public boolean isFinalApprovalRequested() { return finalApprovalRequested; }
    }

    /**
     * Summary of a bid for display
     */
    public static class BidSummary {
        private final String bidId;
        private final String bidderName;
        private final double bidAmount;
        private final int proposedDays;
        private final String proposal;
        private final boolean bondPaid;

        public BidSummary(String bidId, String bidderName, double bidAmount, int proposedDays,
                          String proposal, boolean bondPaid) {
            this.bidId = bidId;
            this.bidderName = bidderName;
            this.bidAmount = bidAmount;
            this.proposedDays = proposedDays;
            this.proposal = proposal;
            this.bondPaid = bondPaid;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(bidId, 64);
            buf.writeUtf(bidderName, 64);
            buf.writeDouble(bidAmount);
            buf.writeVarInt(proposedDays);
            buf.writeUtf(proposal, 512);
            buf.writeBoolean(bondPaid);
        }

        public static BidSummary decode(FriendlyByteBuf buf) {
            return new BidSummary(
                buf.readUtf(64),
                buf.readUtf(64),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readUtf(512),
                buf.readBoolean()
            );
        }

        // Getters
        public String getBidId() { return bidId; }
        public String getBidderName() { return bidderName; }
        public double getBidAmount() { return bidAmount; }
        public int getProposedDays() { return proposedDays; }
        public String getProposal() { return proposal; }
        public boolean isBondPaid() { return bondPaid; }
    }
}

