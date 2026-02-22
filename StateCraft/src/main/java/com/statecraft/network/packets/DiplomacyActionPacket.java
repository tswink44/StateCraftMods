package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Perform a diplomatic action.
 *
 * Actions: DECLARE_WAR, PROPOSE_PEACE, PROPOSE_ALLIANCE, BREAK_ALLIANCE,
 *          ACCEPT_PROPOSAL, REJECT_PROPOSAL
 */
public class DiplomacyActionPacket {

    public enum Action {
        DECLARE_WAR,
        PROPOSE_PEACE,
        PROPOSE_ALLIANCE,
        BREAK_ALLIANCE,
        ACCEPT_PROPOSAL,
        REJECT_PROPOSAL
    }

    private final String nationName;       // Player's nation
    private final Action action;
    private final String targetNationName; // Target nation (for war/peace/alliance/break)
    private final String proposalId;       // UUID string (for accept/reject)

    public DiplomacyActionPacket(String nationName, Action action, String targetNationName, String proposalId) {
        this.nationName = nationName;
        this.action = action;
        this.targetNationName = targetNationName != null ? targetNationName : "";
        this.proposalId = proposalId != null ? proposalId : "";
    }

    public DiplomacyActionPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf();
        this.action = buf.readEnum(Action.class);
        this.targetNationName = buf.readUtf();
        this.proposalId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName);
        buf.writeEnum(action);
        buf.writeUtf(targetNationName);
        buf.writeUtf(proposalId);
    }

    public String getNationName() { return nationName; }
    public Action getAction() { return action; }
    public String getTargetNationName() { return targetNationName; }
    public String getProposalId() { return proposalId; }
}

