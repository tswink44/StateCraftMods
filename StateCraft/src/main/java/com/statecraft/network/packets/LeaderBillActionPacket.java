package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Nation leader signs or vetoes a bill
 */
public class LeaderBillActionPacket {

    public enum ActionType {
        SIGN,   // Enact the bill into law
        VETO    // Veto the bill (if not veto-proof)
    }

    private final String nationName;
    private final String billId;  // UUID as string
    private final ActionType actionType;

    public LeaderBillActionPacket(String nationName, String billId, ActionType actionType) {
        this.nationName = nationName;
        this.billId = billId;
        this.actionType = actionType;
    }

    public LeaderBillActionPacket(FriendlyByteBuf buf) {
        this.nationName = buf.readUtf(256);
        this.billId = buf.readUtf(64);
        this.actionType = buf.readEnum(ActionType.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nationName, 256);
        buf.writeUtf(billId, 64);
        buf.writeEnum(actionType);
    }

    public String getNationName() { return nationName; }
    public String getBillId() { return billId; }
    public ActionType getActionType() { return actionType; }
}

