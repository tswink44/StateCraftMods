package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to send mail to another player
 */
public class SendMailPacket {

    private final String recipientName;
    private final String subject;
    private final String body;

    public SendMailPacket(String recipientName, String subject, String body) {
        this.recipientName = recipientName;
        this.subject = subject;
        this.body = body;
    }

    public SendMailPacket(FriendlyByteBuf buf) {
        this.recipientName = buf.readUtf(32);
        this.subject = buf.readUtf(128);
        this.body = buf.readUtf(1024);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(recipientName, 32);
        buf.writeUtf(subject, 128);
        buf.writeUtf(body, 1024);
    }

    public String getRecipientName() { return recipientName; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
}

