package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Legislature member votes on a bill
 */
public class VoteBillPacket {

    public enum VoteType {
        YES, NO, ABSTAIN
    }

    private final String nationName;
    private final String billId;  // UUID as string
    private final VoteType voteType;

    public VoteBillPacket(String nationName, String billId, VoteType voteType) {
        this.nationName = nationName;
        this.billId = billId;
        this.voteType = voteType;
    }

    public VoteBillPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.billId = buf.readUtf(64);
        this.voteType = buf.readEnum(VoteType.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(billId, 64);
        buf.writeEnum(voteType);
    }

    public String getNationName() { return nationName; }
    public String getBillId() { return billId; }
    public VoteType getVoteType() { return voteType; }
}

