package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to request mail data or perform mail actions
 */
public class RequestMailDataPacket {

    public enum Action {
        GET_INBOX,      // Request inbox contents
        MARK_READ,      // Mark a specific mail as read
        MARK_ALL_READ,  // Mark all mail as read
        DELETE,         // Delete a specific mail
        ARCHIVE,        // Archive a specific mail
        CLAIM_CURRENCY, // Claim currency attachment from a mail
        ACCEPT_INVITE,  // Accept an invite from actionable mail
        DENY_INVITE     // Deny an invite from actionable mail
    }

    private final Action action;
    private final String mailId;    // Used for single-mail actions
    private final String actionData; // Additional data for invite actions (e.g., nation ID)

    public RequestMailDataPacket() {
        this(Action.GET_INBOX, "", "");
    }

    public RequestMailDataPacket(Action action) {
        this(action, "", "");
    }

    public RequestMailDataPacket(Action action, String mailId) {
        this(action, mailId, "");
    }

    public RequestMailDataPacket(Action action, String mailId, String actionData) {
        this.action = action;
        this.mailId = mailId != null ? mailId : "";
        this.actionData = actionData != null ? actionData : "";
    }

    public RequestMailDataPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.mailId = buf.readUtf(64);
        this.actionData = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(mailId, 64);
        buf.writeUtf(actionData, 64);
    }

    public Action getAction() { return action; }
    public String getMailId() { return mailId; }
    public String getActionData() { return actionData; }
}

