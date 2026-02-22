package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server: Perform a company management action.
 */
public class CompanyActionPacket {

    public enum Action {
        ADD_OFFICER,
        REMOVE_OFFICER,
        TRANSFER_SHARES,
        SET_DIVIDEND_RATE,
        TOGGLE_DIVIDENDS,
        RENAME,
        SET_DESCRIPTION,
        DISSOLVE
    }

    private final String companyId; // UUID as string
    private final Action action;
    private final String targetPlayer; // player name for officer/share actions
    private final int intValue;        // share count, etc.
    private final double doubleValue;  // dividend rate, etc.
    private final String stringValue;  // new name, description, etc.

    public CompanyActionPacket(String companyId, Action action, String targetPlayer,
                                int intValue, double doubleValue, String stringValue) {
        this.companyId = companyId;
        this.action = action;
        this.targetPlayer = targetPlayer;
        this.intValue = intValue;
        this.doubleValue = doubleValue;
        this.stringValue = stringValue;
    }

    public CompanyActionPacket(FriendlyByteBuf buf) {
        this.companyId = buf.readUtf();
        this.action = buf.readEnum(Action.class);
        this.targetPlayer = buf.readUtf();
        this.intValue = buf.readVarInt();
        this.doubleValue = buf.readDouble();
        this.stringValue = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(companyId);
        buf.writeEnum(action);
        buf.writeUtf(targetPlayer);
        buf.writeVarInt(intValue);
        buf.writeDouble(doubleValue);
        buf.writeUtf(stringValue);
    }

    public String getCompanyId() { return companyId; }
    public Action getAction() { return action; }
    public String getTargetPlayer() { return targetPlayer; }
    public int getIntValue() { return intValue; }
    public double getDoubleValue() { return doubleValue; }
    public String getStringValue() { return stringValue; }
}

