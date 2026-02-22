package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Syncs shareholder proposal/vote data for the Company GUI Votes tab.
 */
public class SyncShareholderVotesPacket {

    private final List<ProposalInfo> proposals;
    private final String resultMessage;

    public SyncShareholderVotesPacket(List<ProposalInfo> proposals, String resultMessage) {
        this.proposals = proposals;
        this.resultMessage = resultMessage != null ? resultMessage : "";
    }

    public SyncShareholderVotesPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.proposals = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            proposals.add(new ProposalInfo(
                buf.readUtf(),   // proposalId
                buf.readUtf(),   // typeName
                buf.readUtf(),   // typeDisplayName
                buf.readUtf(),   // summary
                buf.readUtf(),   // proposerName
                buf.readUtf(),   // status
                buf.readLong(),  // expiresAt
                buf.readVarInt(),// sharesYes
                buf.readVarInt(),// sharesNo
                buf.readVarInt(),// sharesAbstain
                buf.readVarInt(),// sharesVoted
                buf.readVarInt(),// totalShares
                buf.readUtf(),   // playerVote (empty if not voted)
                buf.readDouble(),// doubleValue
                buf.readVarInt(),// intValue
                buf.readLong()   // longValue
            ));
        }
        this.resultMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(proposals.size());
        for (ProposalInfo p : proposals) {
            buf.writeUtf(p.proposalId);
            buf.writeUtf(p.typeName);
            buf.writeUtf(p.typeDisplayName);
            buf.writeUtf(p.summary);
            buf.writeUtf(p.proposerName);
            buf.writeUtf(p.status);
            buf.writeLong(p.expiresAt);
            buf.writeVarInt(p.sharesYes);
            buf.writeVarInt(p.sharesNo);
            buf.writeVarInt(p.sharesAbstain);
            buf.writeVarInt(p.sharesVoted);
            buf.writeVarInt(p.totalShares);
            buf.writeUtf(p.playerVote);
            buf.writeDouble(p.doubleValue);
            buf.writeVarInt(p.intValue);
            buf.writeLong(p.longValue);
        }
        buf.writeUtf(resultMessage);
    }

    public List<ProposalInfo> getProposals() { return proposals; }
    public String getResultMessage() { return resultMessage; }

    public static class ProposalInfo {
        public final String proposalId;
        public final String typeName;
        public final String typeDisplayName;
        public final String summary;
        public final String proposerName;
        public final String status;
        public final long expiresAt;
        public final int sharesYes;
        public final int sharesNo;
        public final int sharesAbstain;
        public final int sharesVoted;
        public final int totalShares;
        public final String playerVote; // empty string if not voted
        public final double doubleValue;
        public final int intValue;
        public final long longValue;

        public ProposalInfo(String proposalId, String typeName, String typeDisplayName,
                            String summary, String proposerName, String status, long expiresAt,
                            int sharesYes, int sharesNo, int sharesAbstain,
                            int sharesVoted, int totalShares, String playerVote,
                            double doubleValue, int intValue, long longValue) {
            this.proposalId = proposalId;
            this.typeName = typeName;
            this.typeDisplayName = typeDisplayName;
            this.summary = summary;
            this.proposerName = proposerName;
            this.status = status;
            this.expiresAt = expiresAt;
            this.sharesYes = sharesYes;
            this.sharesNo = sharesNo;
            this.sharesAbstain = sharesAbstain;
            this.sharesVoted = sharesVoted;
            this.totalShares = totalShares;
            this.playerVote = playerVote;
            this.doubleValue = doubleValue;
            this.intValue = intValue;
            this.longValue = longValue;
        }

        public boolean isActive() { return "ACTIVE".equals(status); }
        public boolean hasVoted() { return !playerVote.isEmpty(); }

        public double yesPercentage() {
            int decisive = sharesYes + sharesNo;
            return decisive > 0 ? (double) sharesYes / decisive * 100 : 0;
        }

        public double quorumPercentage() {
            return totalShares > 0 ? (double) sharesVoted / totalShares * 100 : 0;
        }

        public String getTimeRemaining() {
            if (!isActive()) return status;
            long remaining = expiresAt - System.currentTimeMillis();
            if (remaining <= 0) return "Ending...";
            long hours = remaining / (1000 * 60 * 60);
            long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
            return hours + "h " + minutes + "m";
        }
    }
}

