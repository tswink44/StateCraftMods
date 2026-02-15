package com.statecraft.mail;

import java.util.UUID;

/**
 * Represents a single mail message in the StateCraft mail system.
 * Mail can be sent between players, to/from government entities, or generated automatically.
 */
public class Mail {

    private final UUID id;
    private final UUID senderId;        // null for system mail
    private final String senderName;    // Display name of sender
    private final UUID recipientId;     // Player UUID or entity UUID
    private final RecipientType recipientType;
    private final MailType type;
    private final String subject;
    private final String body;
    private final long timestamp;
    private boolean read;
    private boolean archived;

    /**
     * Types of mail recipients
     */
    public enum RecipientType {
        PLAYER,
        CITY,
        STATE,
        NATION
    }

    /**
     * Categories of mail for filtering and display
     */
    public enum MailType {
        // Personal mail
        PERSONAL("Personal", "§f"),

        // Transaction notifications
        CHUNK_PURCHASE("Chunk Purchase", "§a"),
        CHUNK_SALE("Chunk Sale", "§e"),
        TRADING_HUB_SALE("Trading Hub Sale", "§e"),

        // Tax notifications
        TAX_SUMMARY("Tax Summary", "§6"),
        TAX_WARNING("Tax Warning", "§c"),
        TAX_REPOSSESSION("Repossession Notice", "§4"),

        // Government notifications
        GOV_TAX_REVENUE("Tax Revenue", "§2"),
        GOV_ANNOUNCEMENT("Announcement", "§b"),

        // Citizenship
        CITIZENSHIP_INVITE("Citizenship Invite", "§d"),
        CITIZENSHIP_APPROVED("Citizenship Approved", "§a"),
        CITIZENSHIP_REVOKED("Citizenship Revoked", "§c"),

        // System
        SYSTEM("System", "§7"),
        WELCOME("Welcome", "§a");

        private final String displayName;
        private final String colorCode;

        MailType(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() { return displayName; }
        public String getColorCode() { return colorCode; }
    }

    /**
     * Create a new mail message
     */
    public Mail(UUID senderId, String senderName, UUID recipientId, RecipientType recipientType,
                MailType type, String subject, String body) {
        this.id = UUID.randomUUID();
        this.senderId = senderId;
        this.senderName = senderName;
        this.recipientId = recipientId;
        this.recipientType = recipientType;
        this.type = type;
        this.subject = subject;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
        this.archived = false;
    }

    /**
     * Create mail from saved data
     */
    public Mail(UUID id, UUID senderId, String senderName, UUID recipientId, RecipientType recipientType,
                MailType type, String subject, String body, long timestamp, boolean read, boolean archived) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.recipientId = recipientId;
        this.recipientType = recipientType;
        this.type = type;
        this.subject = subject;
        this.body = body;
        this.timestamp = timestamp;
        this.read = read;
        this.archived = archived;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public UUID getRecipientId() { return recipientId; }
    public RecipientType getRecipientType() { return recipientType; }
    public MailType getType() { return type; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public long getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
    public boolean isArchived() { return archived; }

    public boolean isSystemMail() { return senderId == null; }

    // Setters
    public void setRead(boolean read) { this.read = read; }
    public void setArchived(boolean archived) { this.archived = archived; }

    /**
     * Get formatted timestamp for display
     */
    public String getFormattedTimestamp() {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " min" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "Just now";
        }
    }

    /**
     * Get a short preview of the body (first 50 chars)
     */
    public String getBodyPreview() {
        if (body == null || body.isEmpty()) return "";
        String clean = body.replaceAll("§.", ""); // Remove color codes
        if (clean.length() <= 50) return clean;
        return clean.substring(0, 47) + "...";
    }
}

