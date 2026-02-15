package com.statecraft.mail;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a mailbox for a player or government entity.
 * Stores all incoming mail and provides filtering/sorting capabilities.
 */
public class Mailbox {

    private final UUID ownerId;
    private final Mail.RecipientType ownerType;
    private final List<Mail> messages;

    // Settings
    private int maxMessages = 100;
    private boolean notificationsEnabled = true;

    public Mailbox(UUID ownerId, Mail.RecipientType ownerType) {
        this.ownerId = ownerId;
        this.ownerType = ownerType;
        this.messages = new ArrayList<>();
    }

    // Getters
    public UUID getOwnerId() { return ownerId; }
    public Mail.RecipientType getOwnerType() { return ownerType; }
    public List<Mail> getAllMessages() { return new ArrayList<>(messages); }
    public int getMaxMessages() { return maxMessages; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    // Setters
    public void setMaxMessages(int max) { this.maxMessages = max; }
    public void setNotificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; }

    /**
     * Add a new message to the mailbox
     */
    public void addMessage(Mail mail) {
        messages.add(0, mail); // Add to front (newest first)

        // Trim old messages if over limit
        while (messages.size() > maxMessages) {
            // Remove oldest archived message first, then oldest read, then oldest unread
            Mail toRemove = messages.stream()
                .filter(Mail::isArchived)
                .reduce((first, second) -> second)
                .orElse(messages.stream()
                    .filter(Mail::isRead)
                    .reduce((first, second) -> second)
                    .orElse(messages.get(messages.size() - 1)));
            messages.remove(toRemove);
        }
    }

    /**
     * Get a message by ID
     */
    public Mail getMessage(UUID mailId) {
        return messages.stream()
            .filter(m -> m.getId().equals(mailId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Delete a message
     */
    public boolean deleteMessage(UUID mailId) {
        return messages.removeIf(m -> m.getId().equals(mailId));
    }

    /**
     * Get unread messages
     */
    public List<Mail> getUnreadMessages() {
        return messages.stream()
            .filter(m -> !m.isRead() && !m.isArchived())
            .collect(Collectors.toList());
    }

    /**
     * Get inbox (non-archived messages)
     */
    public List<Mail> getInbox() {
        return messages.stream()
            .filter(m -> !m.isArchived())
            .collect(Collectors.toList());
    }

    /**
     * Get archived messages
     */
    public List<Mail> getArchivedMessages() {
        return messages.stream()
            .filter(Mail::isArchived)
            .collect(Collectors.toList());
    }

    /**
     * Get messages by type
     */
    public List<Mail> getMessagesByType(Mail.MailType type) {
        return messages.stream()
            .filter(m -> m.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Get messages from a specific sender
     */
    public List<Mail> getMessagesFromSender(UUID senderId) {
        return messages.stream()
            .filter(m -> senderId.equals(m.getSenderId()))
            .collect(Collectors.toList());
    }

    /**
     * Get unread count
     */
    public int getUnreadCount() {
        return (int) messages.stream()
            .filter(m -> !m.isRead() && !m.isArchived())
            .count();
    }

    /**
     * Mark all messages as read
     */
    public void markAllAsRead() {
        messages.forEach(m -> m.setRead(true));
    }

    /**
     * Delete all archived messages
     */
    public void clearArchive() {
        messages.removeIf(Mail::isArchived);
    }

    /**
     * Delete all read messages
     */
    public void deleteReadMessages() {
        messages.removeIf(m -> m.isRead() && !m.isArchived());
    }

    /**
     * Delete all messages
     */
    public void clearAll() {
        messages.clear();
    }

    /**
     * Get total message count
     */
    public int getTotalCount() {
        return messages.size();
    }

    /**
     * Get messages (internal for saving)
     */
    List<Mail> getMessagesInternal() {
        return messages;
    }

    /**
     * Load messages (internal for loading)
     */
    void loadMessages(List<Mail> loadedMessages) {
        messages.clear();
        messages.addAll(loadedMessages);
    }
}


