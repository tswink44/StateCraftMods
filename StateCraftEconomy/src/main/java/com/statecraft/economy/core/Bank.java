package com.statecraft.economy.core;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a bank institution
 * Different banks can have different interest rates, fees, and services
 */
public class Bank {

    private final UUID id;
    private String name;
    private String displayName;
    private double interestRate; // Annual interest rate (e.g., 0.05 for 5%)
    private double withdrawalFee; // Fee for withdrawals (0.0 = no fee)
    private double transferFee; // Fee for transfers (0.0 = no fee)
    private boolean allowsLoans; // Future feature
    private int color; // Color for UI (RGB)

    public Bank(UUID id, String name, String displayName) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.interestRate = 0.0; // No interest by default; player-made banks set their own rate via BankCompany
        this.withdrawalFee = 0.0;
        this.transferFee = 0.0;
        this.allowsLoans = false;
        this.color = 0x4CAF50; // Default green color
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(double interestRate) {
        this.interestRate = Math.max(0, interestRate);
    }

    public double getWithdrawalFee() {
        return withdrawalFee;
    }

    public void setWithdrawalFee(double withdrawalFee) {
        this.withdrawalFee = Math.max(0, withdrawalFee);
    }

    public double getTransferFee() {
        return transferFee;
    }

    public void setTransferFee(double transferFee) {
        this.transferFee = Math.max(0, transferFee);
    }

    public boolean allowsLoans() {
        return allowsLoans;
    }

    public void setAllowsLoans(boolean allowsLoans) {
        this.allowsLoans = allowsLoans;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("displayName", displayName);
        tag.putDouble("interestRate", interestRate);
        tag.putDouble("withdrawalFee", withdrawalFee);
        tag.putDouble("transferFee", transferFee);
        tag.putBoolean("allowsLoans", allowsLoans);
        tag.putInt("color", color);
        return tag;
    }

    public static Bank load(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String name = tag.getString("name");
        String displayName = tag.getString("displayName");

        Bank bank = new Bank(id, name, displayName);
        bank.setInterestRate(tag.getDouble("interestRate"));
        bank.setWithdrawalFee(tag.getDouble("withdrawalFee"));
        bank.setTransferFee(tag.getDouble("transferFee"));
        bank.setAllowsLoans(tag.getBoolean("allowsLoans"));
        bank.setColor(tag.getInt("color"));

        return bank;
    }
}

