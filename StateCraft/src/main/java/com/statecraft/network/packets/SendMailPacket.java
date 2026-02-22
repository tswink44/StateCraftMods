package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to send mail to another player or broadcast to all nation members
 */
public class SendMailPacket {

    private final String recipientName;
    private final String subject;
    private final String body;
    private final double attachedCurrency; // 0 = no currency attachment
    private final boolean broadcast; // true = send to all nation members

    public SendMailPacket(String recipientName, String subject, String body) {
        this(recipientName, subject, body, 0, false);
    }

    public SendMailPacket(String recipientName, String subject, String body, double attachedCurrency) {
        this(recipientName, subject, body, attachedCurrency, false);
    }

    public SendMailPacket(String recipientName, String subject, String body, double attachedCurrency, boolean broadcast) {
        this.recipientName = recipientName;
        this.subject = subject;
        this.body = body;
        this.attachedCurrency = attachedCurrency;
        this.broadcast = broadcast;
    }

    public SendMailPacket(FriendlyByteBuf buf) {
        this.recipientName = buf.readUtf(32);
        this.subject = buf.readUtf(128);
        this.body = buf.readUtf(1024);
        this.attachedCurrency = buf.readDouble();
        this.broadcast = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(recipientName, 32);
        buf.writeUtf(subject, 128);
        buf.writeUtf(body, 1024);
        buf.writeDouble(attachedCurrency);
        buf.writeBoolean(broadcast);
    }

    public String getRecipientName() { return recipientName; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public double getAttachedCurrency() { return attachedCurrency; }
    public boolean isBroadcast() { return broadcast; }
}

