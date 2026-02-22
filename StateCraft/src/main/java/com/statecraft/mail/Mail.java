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
    private double attachedCurrency;  // Currency attachment (0 = none)
    private boolean currencyClaimed;  // Whether the currency has been claimed by recipient

    // Action data for actionable mail (invites, etc.)
    private String actionData;        // JSON or simple string for action parameters (e.g., nation ID)
    private boolean actionTaken;      // Whether the action has been taken (accept/deny)

    /**
     * Types of mail recipients
     */
    public enum RecipientType {
        PLAYER,
        CITY,
        STATE,
        NATION,
        COMPANY
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

        // Nation invites
        NATION_INVITE("Nation Invite", "§d"),

        // System
        SYSTEM("System", "§7"),
        WELCOME("Welcome", "§a"),

        // Financial / Currency
        FINANCIAL("Financial", "§6"),
        CURRENCY_TRANSFER("Currency Transfer", "§a"),

        // Broadcast
        BROADCAST("Broadcast", "§d");

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
        this.attachedCurrency = 0;
        this.currencyClaimed = false;
        this.actionData = null;
        this.actionTaken = false;
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
        this.attachedCurrency = 0;
        this.currencyClaimed = false;
        this.actionData = null;
        this.actionTaken = false;
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

    // Currency attachment
    public double getAttachedCurrency() { return attachedCurrency; }
    public void setAttachedCurrency(double amount) { this.attachedCurrency = Math.max(0, amount); }
    public boolean isCurrencyClaimed() { return currencyClaimed; }
    public void setCurrencyClaimed(boolean claimed) { this.currencyClaimed = claimed; }
    public boolean hasUnclaimedCurrency() { return attachedCurrency > 0 && !currencyClaimed; }

    // Action data for invites and other actionable mail
    public String getActionData() { return actionData; }
    public void setActionData(String data) { this.actionData = data; }
    public boolean isActionTaken() { return actionTaken; }
    public void setActionTaken(boolean taken) { this.actionTaken = taken; }

    /**
     * Check if this mail has a pending action (invite that hasn't been accepted/denied)
     */
    public boolean hasActionPending() {
        return isActionableMail() && !actionTaken;
    }

    /**
     * Check if this mail type supports actions (accept/deny buttons)
     */
    public boolean isActionableMail() {
        return type == MailType.NATION_INVITE || type == MailType.CITIZENSHIP_INVITE;
    }

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

