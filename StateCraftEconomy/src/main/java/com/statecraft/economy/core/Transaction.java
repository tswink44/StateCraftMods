package com.statecraft.economy.core;

import java.util.UUID;

/**
 * Represents a single transaction in the economy
 */
public class Transaction {

    public enum Type {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT,
        FEE,
        NATION_DEPOSIT,
        NATION_WITHDRAWAL,
        CITY_DEPOSIT,
        CITY_WITHDRAWAL,
        PURCHASE,
        SALE,
        TAX,
        DIVIDEND,
        INTEREST,
        LOAN_REPAYMENT,
        MARKETPLACE_PURCHASE,
        MARKETPLACE_SALE,
        IMPORT_TARIFF
    }

    private final Type type;
    private final double amount;
    private final UUID otherId; // Other player/entity involved (nullable)
    private final String description;
    private final long timestamp;
    private final UUID initiatorId;   // Who initiated this transaction (nullable)
    private final String initiatorName; // Display name of initiator (nullable)

    /**
     * Legacy constructor (no initiator info)
     */
    public Transaction(Type type, double amount, UUID otherId, String description, long timestamp) {
        this(type, amount, otherId, description, timestamp, null, null);
    }

    /**
     * Full constructor with initiator tracking
     */
    public Transaction(Type type, double amount, UUID otherId, String description, long timestamp,
                       UUID initiatorId, String initiatorName) {
        this.type = type;
        this.amount = amount;
        this.otherId = otherId;
        this.description = description;
        this.timestamp = timestamp;
        this.initiatorId = initiatorId;
        this.initiatorName = initiatorName;
    }

    public Type getType() {
        return type;
    }

    public double getAmount() {
        return amount;
    }

    public UUID getOtherId() {
        return otherId;
    }

    public String getDescription() {
        return description;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public UUID getInitiatorId() {
        return initiatorId;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public boolean isIncoming() {
        return type == Type.DEPOSIT || type == Type.TRANSFER_IN ||
               type == Type.NATION_WITHDRAWAL || type == Type.SALE ||
               type == Type.INTEREST || type == Type.LOAN_REPAYMENT ||
               type == Type.MARKETPLACE_SALE;
    }

    public boolean isOutgoing() {
        return type == Type.WITHDRAWAL || type == Type.TRANSFER_OUT ||
               type == Type.NATION_DEPOSIT || type == Type.FEE ||
               type == Type.PURCHASE || type == Type.TAX ||
               type == Type.MARKETPLACE_PURCHASE || type == Type.IMPORT_TARIFF;
    }
}

