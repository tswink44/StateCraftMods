package com.statecraft.network.packets;

import com.statecraft.mail.Mail;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet to sync mail inbox data
 */
public class SyncMailDataPacket {

    private final List<MailInfo> messages;
    private final int unreadCount;
    private final int totalCount;

    public SyncMailDataPacket(List<MailInfo> messages, int unreadCount, int totalCount) {
        this.messages = messages;
        this.unreadCount = unreadCount;
        this.totalCount = totalCount;
    }

    public SyncMailDataPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            messages.add(new MailInfo(buf));
        }
        this.unreadCount = buf.readVarInt();
        this.totalCount = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(messages.size());
        for (MailInfo info : messages) {
            info.encode(buf);
        }
        buf.writeVarInt(unreadCount);
        buf.writeVarInt(totalCount);
    }

    public List<MailInfo> getMessages() { return messages; }
    public int getUnreadCount() { return unreadCount; }
    public int getTotalCount() { return totalCount; }

    /**
     * Mail info for network transfer
     */
    public static class MailInfo {
        public final String mailId;
        public final String subject;
        public final String body;
        public final String senderName;
        public final String timeAgo;
        public final Mail.MailType type;
        public final boolean read;

        public MailInfo(String mailId, String subject, String body, String senderName,
                       String timeAgo, Mail.MailType type, boolean read) {
            this.mailId = mailId;
            this.subject = subject;
            this.body = body;
            this.senderName = senderName;
            this.timeAgo = timeAgo;
            this.type = type;
            this.read = read;
        }

        public MailInfo(FriendlyByteBuf buf) {
            this.mailId = buf.readUtf(64);
            this.subject = buf.readUtf(128);
            this.body = buf.readUtf(1024);
            this.senderName = buf.readUtf(64);
            this.timeAgo = buf.readUtf(32);
            this.type = buf.readEnum(Mail.MailType.class);
            this.read = buf.readBoolean();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(mailId, 64);
            buf.writeUtf(subject, 128);
            buf.writeUtf(body, 1024);
            buf.writeUtf(senderName, 64);
            buf.writeUtf(timeAgo, 32);
            buf.writeEnum(type);
            buf.writeBoolean(read);
        }
    }
}

