package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client: Sends transaction history for a specific account.
 * Contains a list of transaction entries with all display-relevant fields.
 */
public class SyncAccountActivityPacket {

    private final String accountType;
    private final String accountName;
    private final String accountId;
    private final List<ActivityEntry> entries;

    public SyncAccountActivityPacket(String accountType, String accountName, String accountId, List<ActivityEntry> entries) {
        this.accountType = accountType;
        this.accountName = accountName;
        this.accountId = accountId;
        this.entries = entries;
    }

    public SyncAccountActivityPacket(FriendlyByteBuf buf) {
        this.accountType = buf.readUtf();
        this.accountName = buf.readUtf();
        this.accountId = buf.readUtf();
        int count = buf.readVarInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String type = buf.readUtf();
            double amount = buf.readDouble();
            String description = buf.readUtf();
            long timestamp = buf.readLong();
            String initiatorName = buf.readUtf();
            boolean incoming = buf.readBoolean();
            double runningBalance = buf.readDouble();
            entries.add(new ActivityEntry(type, amount, description, timestamp, initiatorName, incoming, runningBalance));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(accountType);
        buf.writeUtf(accountName);
        buf.writeUtf(accountId);
        buf.writeVarInt(entries.size());
        for (ActivityEntry entry : entries) {
            buf.writeUtf(entry.type());
            buf.writeDouble(entry.amount());
            buf.writeUtf(entry.description());
            buf.writeLong(entry.timestamp());
            buf.writeUtf(entry.initiatorName());
            buf.writeBoolean(entry.incoming());
            buf.writeDouble(entry.runningBalance());
        }
    }

    public String getAccountType() {
        return accountType;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<ActivityEntry> getEntries() {
        return entries;
    }

    /**
     * A single activity log entry for display on the client.
     * @param type Transaction type name (e.g., "DEPOSIT", "TRANSFER_OUT")
     * @param amount Transaction amount
     * @param description Human-readable description
     * @param timestamp Unix timestamp of the transaction
     * @param initiatorName Display name of who initiated (empty if unknown/self)
     * @param incoming Whether this is money coming in (true) or going out (false)
     * @param runningBalance The account balance after this transaction
     */
    public record ActivityEntry(String type, double amount, String description, long timestamp,
                                String initiatorName, boolean incoming, double runningBalance) {
    }
}

