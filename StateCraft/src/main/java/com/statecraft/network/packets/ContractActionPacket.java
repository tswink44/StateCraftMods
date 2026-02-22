package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Contract action (approve bid, complete milestone, etc.)
 */
public class ContractActionPacket {

    public enum ActionType {
        OPEN_BIDDING,       // Open contract for bidding
        CLOSE_BIDDING,      // Close bidding early
        APPROVE_BID,        // Legislature approves a specific bid
        UPDATE_PROGRESS,    // Update progress percentage (contractor)
        REQUEST_MILESTONE_APPROVAL, // Contractor requests milestone approval
        COMPLETE_MILESTONE, // Mark a milestone as complete (legislature approval)
        COMPLETE_CONTRACT,  // Mark contract as complete
        CANCEL_CONTRACT,    // Cancel the contract
        FAIL_CONTRACT       // Mark contract as failed
    }

    private final String nationName;
    private final String contractId;
    private final ActionType action;
    private final String targetId;      // Bid ID for APPROVE_BID
    private final int value;            // Progress percent or milestone percent
    private final long duration;        // Duration in ms (for bidding period or deadline)

    public ContractActionPacket(String nationName, String contractId, ActionType action) {
        this(nationName, contractId, action, "", 0, 0);
    }

    public ContractActionPacket(String nationName, String contractId, ActionType action,
                                 String targetId, int value, long duration) {
        this.nationName = nationName;
        this.contractId = contractId;
        this.action = action;
        this.targetId = targetId;
        this.value = value;
        this.duration = duration;
    }

    public ContractActionPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.contractId = buf.readUtf(64);
        this.action = ActionType.values()[buf.readVarInt()];
        this.targetId = buf.readUtf(64);
        this.value = buf.readVarInt();
        this.duration = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(contractId, 64);
        buf.writeVarInt(action.ordinal());
        buf.writeUtf(targetId, 64);
        buf.writeVarInt(value);
        buf.writeLong(duration);
    }

    // Getters
    public String getNationName() { return nationName; }
    public String getContractId() { return contractId; }
    public ActionType getAction() { return action; }
    public String getTargetId() { return targetId; }
    public int getValue() { return value; }
    public long getDuration() { return duration; }
}

