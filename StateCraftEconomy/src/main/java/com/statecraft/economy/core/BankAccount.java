package com.statecraft.economy.core;

import java.util.UUID;

/**
 * Represents a bank account (player, nation, state, city, or company)
 */
public class BankAccount {

    public enum AccountType {
        PLAYER,
        NATION,
        STATE,
        CITY,
        COMPANY
    }

    private final UUID ownerId;
    private final AccountType type;
    private UUID bankId; // Which bank this account belongs to
    private double balance;
    private long lastInterestTime;

    public BankAccount(UUID ownerId, AccountType type, double initialBalance) {
        this.ownerId = ownerId;
        this.type = type;
        this.bankId = null; // Will be set to default bank by EconomyManager
        this.balance = initialBalance;
        this.lastInterestTime = System.currentTimeMillis();
    }

    public BankAccount(UUID ownerId, AccountType type, UUID bankId, double initialBalance) {
        this.ownerId = ownerId;
        this.type = type;
        this.bankId = bankId;
        this.balance = initialBalance;
        this.lastInterestTime = System.currentTimeMillis();
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public AccountType getType() {
        return type;
    }

    public UUID getBankId() {
        return bankId;
    }

    public void setBankId(UUID bankId) {
        this.bankId = bankId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void add(double amount) {
        if (amount > 0) {
            this.balance += amount;
        }
    }

    public boolean subtract(double amount) {
        if (amount > 0 && this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    /**
     * Force subtract an amount, allowing the balance to go negative.
     * Used for mandatory payments like taxes where non-payment has consequences.
     */
    public void forceSubtract(double amount) {
        if (amount > 0) {
            this.balance -= amount;
        }
    }

    public long getLastInterestTime() {
        return lastInterestTime;
    }

    public void setLastInterestTime(long time) {
        this.lastInterestTime = time;
    }
}

