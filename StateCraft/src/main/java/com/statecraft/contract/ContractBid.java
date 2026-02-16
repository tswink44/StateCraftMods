package com.statecraft.contract;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a bid placed on a government contract
 */
public class ContractBid {

    private final UUID bidId;
    private final UUID contractId;
    private final UUID bidderId;
    private final String bidderName;
    private final long bidTime;

    private double bidAmount;           // Amount the bidder will do the work for
    private String proposal;            // Bidder's proposal/plan description
    private int proposedDurationDays;   // How many days the bidder estimates for completion
    private boolean bondPaid;           // Whether the bidder has paid the required bond

    public ContractBid(UUID contractId, UUID bidderId, String bidderName, double bidAmount) {
        this.bidId = UUID.randomUUID();
        this.contractId = contractId;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.bidTime = System.currentTimeMillis();
        this.bidAmount = bidAmount;
        this.proposal = "";
        this.proposedDurationDays = 7; // Default 7 days
        this.bondPaid = false;
    }

    // Private constructor for NBT loading
    private ContractBid(UUID bidId, UUID contractId, UUID bidderId, String bidderName, long bidTime) {
        this.bidId = bidId;
        this.contractId = contractId;
        this.bidderId = bidderId;
        this.bidderName = bidderName;
        this.bidTime = bidTime;
    }

    // Getters
    public UUID getBidId() { return bidId; }
    public UUID getContractId() { return contractId; }
    public UUID getBidderId() { return bidderId; }
    public String getBidderName() { return bidderName; }
    public long getBidTime() { return bidTime; }
    public double getBidAmount() { return bidAmount; }
    public String getProposal() { return proposal; }
    public int getProposedDurationDays() { return proposedDurationDays; }
    public boolean isBondPaid() { return bondPaid; }

    // Setters
    public void setBidAmount(double amount) {
        this.bidAmount = Math.max(0, amount);
    }

    public void setProposal(String proposal) {
        this.proposal = proposal != null ? proposal : "";
    }

    public void setProposedDurationDays(int days) {
        this.proposedDurationDays = Math.max(1, days);
    }

    public void setBondPaid(boolean paid) {
        this.bondPaid = paid;
    }

    // NBT Serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("bidId", bidId);
        tag.putUUID("contractId", contractId);
        tag.putUUID("bidderId", bidderId);
        tag.putString("bidderName", bidderName);
        tag.putLong("bidTime", bidTime);
        tag.putDouble("bidAmount", bidAmount);
        tag.putString("proposal", proposal);
        tag.putInt("proposedDurationDays", proposedDurationDays);
        tag.putBoolean("bondPaid", bondPaid);

        return tag;
    }

    public static ContractBid load(CompoundTag tag) {
        UUID bidId = tag.getUUID("bidId");
        UUID contractId = tag.getUUID("contractId");
        UUID bidderId = tag.getUUID("bidderId");
        String bidderName = tag.getString("bidderName");
        long bidTime = tag.getLong("bidTime");

        ContractBid bid = new ContractBid(bidId, contractId, bidderId, bidderName, bidTime);

        bid.bidAmount = tag.getDouble("bidAmount");
        bid.proposal = tag.getString("proposal");
        bid.proposedDurationDays = tag.getInt("proposedDurationDays");
        bid.bondPaid = tag.getBoolean("bondPaid");

        return bid;
    }
}

