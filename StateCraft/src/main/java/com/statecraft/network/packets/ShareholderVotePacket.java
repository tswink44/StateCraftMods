package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Shareholder vote actions (create proposal or cast vote).
 */
public class ShareholderVotePacket {

    public enum Action {
        CREATE_PROPOSAL,
        CAST_VOTE
    }

    private final String companyId;     // UUID as string
    private final Action action;
    private final String proposalId;    // UUID as string (for CAST_VOTE)
    private final String proposalType;  // ProposalType enum name (for CREATE_PROPOSAL)
    private final double doubleValue;   // Dividend rate, etc.
    private final int intValue;         // Share count, etc.
    private final long longValue;       // Dividend period ticks, etc.
    private final String stringValue;   // Target player name (for REMOVE_OFFICER)
    private final String voteChoice;    // YES, NO, ABSTAIN (for CAST_VOTE)

    public ShareholderVotePacket(String companyId, Action action, String proposalId,
                                  String proposalType, double doubleValue, int intValue,
                                  long longValue, String stringValue, String voteChoice) {
        this.companyId = companyId;
        this.action = action;
        this.proposalId = proposalId != null ? proposalId : "";
        this.proposalType = proposalType != null ? proposalType : "";
        this.doubleValue = doubleValue;
        this.intValue = intValue;
        this.longValue = longValue;
        this.stringValue = stringValue != null ? stringValue : "";
        this.voteChoice = voteChoice != null ? voteChoice : "";
    }

    public ShareholderVotePacket(FriendlyByteBuf buf) {
        this.companyId = buf.readUtf();
        this.action = buf.readEnum(Action.class);
        this.proposalId = buf.readUtf();
        this.proposalType = buf.readUtf();
        this.doubleValue = buf.readDouble();
        this.intValue = buf.readVarInt();
        this.longValue = buf.readLong();
        this.stringValue = buf.readUtf();
        this.voteChoice = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(companyId);
        buf.writeEnum(action);
        buf.writeUtf(proposalId);
        buf.writeUtf(proposalType);
        buf.writeDouble(doubleValue);
        buf.writeVarInt(intValue);
        buf.writeLong(longValue);
        buf.writeUtf(stringValue);
        buf.writeUtf(voteChoice);
    }

    public String getCompanyId() { return companyId; }
    public Action getAction() { return action; }
    public String getProposalId() { return proposalId; }
    public String getProposalType() { return proposalType; }
    public double getDoubleValue() { return doubleValue; }
    public int getIntValue() { return intValue; }
    public long getLongValue() { return longValue; }
    public String getStringValue() { return stringValue; }
    public String getVoteChoice() { return voteChoice; }

    // ==================== Factory Methods ====================

    /** Create a CREATE_PROPOSAL packet */
    public static ShareholderVotePacket createProposal(String companyId, String proposalType,
                                                        double doubleValue, int intValue,
                                                        long longValue, String targetPlayer) {
        return new ShareholderVotePacket(companyId, Action.CREATE_PROPOSAL, "",
            proposalType, doubleValue, intValue, longValue, targetPlayer, "");
    }

    /** Create a CAST_VOTE packet */
    public static ShareholderVotePacket castVote(String companyId, String proposalId, String voteChoice) {
        return new ShareholderVotePacket(companyId, Action.CAST_VOTE, proposalId,
            "", 0, 0, 0, "", voteChoice);
    }
}

