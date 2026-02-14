package com.statecraft.economy.core;

/**
 * Result of a transaction operation
 */
public class TransactionResult {
    private final boolean success;
    private final String message;
    private final double newBalance;

    public TransactionResult(boolean success, String message, double newBalance) {
        this.success = success;
        this.message = message;
        this.newBalance = newBalance;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public double getNewBalance() {
        return newBalance;
    }
}

