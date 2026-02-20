package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Syncs diplomacy data (nation relationships, pending proposals) for the Diplomacy GUI.
 */
public class SyncDiplomacyDataPacket {

    private final String nationName;
    private final boolean isLeader;
    private final List<NationRelation> relations;
    private final List<ProposalEntry> inboundProposals;
    private final List<ProposalEntry> outboundProposals;
    private final String resultMessage;

    public SyncDiplomacyDataPacket(String nationName, boolean isLeader,
                                    List<NationRelation> relations,
                                    List<ProposalEntry> inboundProposals,
                                    List<ProposalEntry> outboundProposals,
                                    String resultMessage) {
        this.nationName = nationName;
        this.isLeader = isLeader;
        this.relations = relations;
        this.inboundProposals = inboundProposals;
        this.outboundProposals = outboundProposals;
        this.resultMessage = resultMessage != null ? resultMessage : "";
    }

    public SyncDiplomacyDataPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
        this.isLeader = buf.readBoolean();

        int relCount = buf.readVarInt();
        this.relations = new ArrayList<>(relCount);
        for (int i = 0; i < relCount; i++) {
            relations.add(new NationRelation(
                buf.readUtf(),  // nationName
                buf.readUtf()   // status: NEUTRAL, ALLIED, AT_WAR, TRUCE
            ));
        }

        int inCount = buf.readVarInt();
        this.inboundProposals = new ArrayList<>(inCount);
        for (int i = 0; i < inCount; i++) {
            inboundProposals.add(new ProposalEntry(
                buf.readUtf(),  // proposalId (UUID as string)
                buf.readUtf(),  // type: PEACE, ALLIANCE
                buf.readUtf(),  // otherNationName
                buf.readLong()  // expiresAt
            ));
        }

        int outCount = buf.readVarInt();
        this.outboundProposals = new ArrayList<>(outCount);
        for (int i = 0; i < outCount; i++) {
            outboundProposals.add(new ProposalEntry(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readLong()
            ));
        }

        this.resultMessage = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
        buf.writeBoolean(isLeader);

        buf.writeVarInt(relations.size());
        for (NationRelation rel : relations) {
            buf.writeUtf(rel.nationName);
            buf.writeUtf(rel.status);
        }

        buf.writeVarInt(inboundProposals.size());
        for (ProposalEntry p : inboundProposals) {
            buf.writeUtf(p.proposalId);
            buf.writeUtf(p.type);
            buf.writeUtf(p.otherNationName);
            buf.writeLong(p.expiresAt);
        }

        buf.writeVarInt(outboundProposals.size());
        for (ProposalEntry p : outboundProposals) {
            buf.writeUtf(p.proposalId);
            buf.writeUtf(p.type);
            buf.writeUtf(p.otherNationName);
            buf.writeLong(p.expiresAt);
        }

        buf.writeUtf(resultMessage);
    }

    public String getNationName() { return nationName; }
    public boolean isLeader() { return isLeader; }
    public List<NationRelation> getRelations() { return relations; }
    public List<ProposalEntry> getInboundProposals() { return inboundProposals; }
    public List<ProposalEntry> getOutboundProposals() { return outboundProposals; }
    public String getResultMessage() { return resultMessage; }

    public static class NationRelation {
        public final String nationName;
        public final String status; // NEUTRAL, ALLIED, AT_WAR, TRUCE

        public NationRelation(String nationName, String status) {
            this.nationName = nationName;
            this.status = status;
        }
    }

    public static class ProposalEntry {
        public final String proposalId; // UUID string
        public final String type;       // PEACE, ALLIANCE
        public final String otherNationName;
        public final long expiresAt;

        public ProposalEntry(String proposalId, String type, String otherNationName, long expiresAt) {
            this.proposalId = proposalId;
            this.type = type;
            this.otherNationName = otherNationName;
            this.expiresAt = expiresAt;
        }
    }
}

