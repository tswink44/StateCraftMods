package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to request available transfer recipients of a specific type
 */
public class RequestTransferRecipientsPacket {

    public enum RecipientType {
        PLAYER,
        NATION,
        STATE,
        CITY
    }

    private final RecipientType recipientType;

    public RequestTransferRecipientsPacket(RecipientType recipientType) {
        this.recipientType = recipientType;
    }

    public RequestTransferRecipientsPacket(FriendlyByteBuf buf) {
        this.recipientType = buf.readEnum(RecipientType.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(recipientType);
    }

    public RecipientType getRecipientType() {
        return recipientType;
    }
}

