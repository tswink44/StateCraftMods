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
        ARCHIVE         // Archive a specific mail
    }

    private final Action action;
    private final String mailId; // Used for single-mail actions

    public RequestMailDataPacket() {
        this(Action.GET_INBOX, "");
    }

    public RequestMailDataPacket(Action action) {
        this(action, "");
    }

    public RequestMailDataPacket(Action action, String mailId) {
        this.action = action;
        this.mailId = mailId != null ? mailId : "";
    }

    public RequestMailDataPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.mailId = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(mailId, 64);
    }

    public Action getAction() { return action; }
    public String getMailId() { return mailId; }
}

