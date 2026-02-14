package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet containing available transfer recipients of a specific type
 */
public class SyncTransferRecipientsPacket {

    private final String recipientType; // PLAYER, NATION, STATE, CITY
    private final List<RecipientInfo> recipients;

    public SyncTransferRecipientsPacket(String recipientType, List<RecipientInfo> recipients) {
        this.recipientType = recipientType;
        this.recipients = recipients;
    }

    public SyncTransferRecipientsPacket(FriendlyByteBuf buf) {
        this.recipientType = buf.readUtf();
        int count = buf.readVarInt();
        this.recipients = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf();
            String id = buf.readUtf();
            recipients.add(new RecipientInfo(name, id));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(recipientType);
        buf.writeVarInt(recipients.size());
        for (RecipientInfo recipient : recipients) {
            buf.writeUtf(recipient.name());
            buf.writeUtf(recipient.id());
        }
    }

    public String getRecipientType() {
        return recipientType;
    }

    public List<RecipientInfo> getRecipients() {
        return recipients;
    }

    /**
     * Information about a transfer recipient
     * @param name Display name (player name, nation name, etc.)
     * @param id Unique identifier (UUID string for players/entities)
     */
    public record RecipientInfo(String name, String id) {
    }
}

