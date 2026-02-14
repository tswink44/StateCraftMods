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
        TAX
    }

    private final Type type;
    private final double amount;
    private final UUID otherId; // Other player/entity involved (nullable)
    private final String description;
    private final long timestamp;

    public Transaction(Type type, double amount, UUID otherId, String description, long timestamp) {
        this.type = type;
        this.amount = amount;
        this.otherId = otherId;
        this.description = description;
        this.timestamp = timestamp;
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

    public boolean isIncoming() {
        return type == Type.DEPOSIT || type == Type.TRANSFER_IN ||
               type == Type.NATION_WITHDRAWAL || type == Type.SALE;
    }

    public boolean isOutgoing() {
        return type == Type.WITHDRAWAL || type == Type.TRANSFER_OUT ||
               type == Type.NATION_DEPOSIT || type == Type.FEE ||
               type == Type.PURCHASE || type == Type.TAX;
    }
}

