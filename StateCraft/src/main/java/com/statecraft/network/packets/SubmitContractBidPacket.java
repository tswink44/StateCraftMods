package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Submit a bid on a contract
 */
public class SubmitContractBidPacket {

    private final String nationName;
    private final String contractId;
    private final double bidAmount;
    private final String proposal;
    private final int proposedDays;

    public SubmitContractBidPacket(String nationName, String contractId, double bidAmount,
                                    String proposal, int proposedDays) {
        this.nationName = nationName;
        this.contractId = contractId;
        this.bidAmount = bidAmount;
        this.proposal = proposal;
        this.proposedDays = proposedDays;
    }

    public SubmitContractBidPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.contractId = buf.readUtf(64);
        this.bidAmount = buf.readDouble();
        this.proposal = buf.readUtf(1024);
        this.proposedDays = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(contractId, 64);
        buf.writeDouble(bidAmount);
        buf.writeUtf(proposal, 1024);
        buf.writeVarInt(proposedDays);
    }

    // Getters
    public String getNationName() { return nationName; }
    public String getContractId() { return contractId; }
    public double getBidAmount() { return bidAmount; }
    public String getProposal() { return proposal; }
    public int getProposedDays() { return proposedDays; }
}

