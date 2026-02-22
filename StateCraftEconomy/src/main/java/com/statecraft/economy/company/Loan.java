package com.statecraft.economy.company;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a loan issued by a bank company to a player.
 * Tracks principal, interest, repayments, and default status.
 */
public class Loan {

    public enum LoanStatus {
        ACTIVE,
        REPAID,
        DEFAULTED,
        SEIZED
    }

    private final UUID id;
    private final UUID bankCompanyId;
    private final UUID borrowerId;
    private final double principalAmount;
    private final double interestRate; // Per-period rate (matches bank's loan accrual period)
    private double totalOwed;
    private double amountRepaid;
    private final long issuedTime;
    private long dueTime; // 0 = no hard deadline, repay over time
    private int missedPayments; // Incremented each accrual period if no payment was made since last accrual
    private long lastPaymentTime;
    private long lastAccrualTime;
    private LoanStatus status;

    /**
     * Create a new loan.
     */
    public Loan(UUID id, UUID bankCompanyId, UUID borrowerId, double principalAmount, double interestRate) {
        this.id = id;
        this.bankCompanyId = bankCompanyId;
        this.borrowerId = borrowerId;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.totalOwed = principalAmount;
        this.amountRepaid = 0;
        this.issuedTime = System.currentTimeMillis();
        this.dueTime = 0;
        this.missedPayments = 0;
        this.lastPaymentTime = 0;
        this.lastAccrualTime = System.currentTimeMillis();
        this.status = LoanStatus.ACTIVE;
    }

    /**
     * Private constructor for loading from NBT.
     */
    private Loan(UUID id, UUID bankCompanyId, UUID borrowerId, double principalAmount,
                 double interestRate, double totalOwed, double amountRepaid,
                 long issuedTime, long dueTime, int missedPayments,
                 long lastPaymentTime, long lastAccrualTime, LoanStatus status) {
        this.id = id;
        this.bankCompanyId = bankCompanyId;
        this.borrowerId = borrowerId;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.totalOwed = totalOwed;
        this.amountRepaid = amountRepaid;
        this.issuedTime = issuedTime;
        this.dueTime = dueTime;
        this.missedPayments = missedPayments;
        this.lastPaymentTime = lastPaymentTime;
        this.lastAccrualTime = lastAccrualTime;
        this.status = status;
    }

    // ==================== Getters ====================

    public UUID getId() { return id; }
    public UUID getBankCompanyId() { return bankCompanyId; }
    public UUID getBorrowerId() { return borrowerId; }
    public double getPrincipalAmount() { return principalAmount; }
    public double getInterestRate() { return interestRate; }
    public double getTotalOwed() { return totalOwed; }
    public double getAmountRepaid() { return amountRepaid; }
    public long getIssuedTime() { return issuedTime; }
    public long getDueTime() { return dueTime; }
    public void setDueTime(long dueTime) { this.dueTime = dueTime; }
    public int getMissedPayments() { return missedPayments; }
    public long getLastPaymentTime() { return lastPaymentTime; }
    public long getLastAccrualTime() { return lastAccrualTime; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }

    // ==================== Computed ====================

    public double getRemainingBalance() {
        return Math.max(0, totalOwed - amountRepaid);
    }

    public boolean isFullyRepaid() {
        return getRemainingBalance() <= 0.01;
    }

    public boolean isOverdue() {
        return dueTime > 0 && System.currentTimeMillis() > dueTime && !isFullyRepaid();
    }

    public boolean isActive() {
        return status == LoanStatus.ACTIVE;
    }

    // ==================== Actions ====================

    /**
     * Make a payment towards this loan.
     * @param amount The payment amount
     * @return The actual amount applied (may be less than amount if loan is nearly paid off)
     */
    public double makePayment(double amount) {
        if (amount <= 0 || !isActive()) return 0;
        double remaining = getRemainingBalance();
        double applied = Math.min(amount, remaining);
        amountRepaid += applied;
        lastPaymentTime = System.currentTimeMillis();
        // Reset missed payments on any payment
        missedPayments = 0;

        if (isFullyRepaid()) {
            status = LoanStatus.REPAID;
        }

        return applied;
    }

    /**
     * Accrue interest on the remaining balance.
     * Called periodically by BankManager.
     * @return The interest amount added
     */
    public double accrueInterest() {
        if (!isActive() || interestRate <= 0) return 0;
        double remaining = getRemainingBalance();
        if (remaining <= 0.01) return 0;

        double interest = remaining * interestRate;
        totalOwed += interest;
        lastAccrualTime = System.currentTimeMillis();
        return interest;
    }

    /**
     * Record a missed payment (no repayment was made during this accrual period).
     */
    public void recordMissedPayment() {
        if (isActive()) {
            missedPayments++;
        }
    }

    // ==================== NBT Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("BankCompanyId", bankCompanyId);
        tag.putUUID("BorrowerId", borrowerId);
        tag.putDouble("PrincipalAmount", principalAmount);
        tag.putDouble("InterestRate", interestRate);
        tag.putDouble("TotalOwed", totalOwed);
        tag.putDouble("AmountRepaid", amountRepaid);
        tag.putLong("IssuedTime", issuedTime);
        tag.putLong("DueTime", dueTime);
        tag.putInt("MissedPayments", missedPayments);
        tag.putLong("LastPaymentTime", lastPaymentTime);
        tag.putLong("LastAccrualTime", lastAccrualTime);
        tag.putString("Status", status.name());
        return tag;
    }

    public static Loan load(CompoundTag tag) {
        return new Loan(
            tag.getUUID("Id"),
            tag.getUUID("BankCompanyId"),
            tag.getUUID("BorrowerId"),
            tag.getDouble("PrincipalAmount"),
            tag.getDouble("InterestRate"),
            tag.getDouble("TotalOwed"),
            tag.getDouble("AmountRepaid"),
            tag.getLong("IssuedTime"),
            tag.getLong("DueTime"),
            tag.getInt("MissedPayments"),
            tag.getLong("LastPaymentTime"),
            tag.getLong("LastAccrualTime"),
            LoanStatus.valueOf(tag.getString("Status"))
        );
    }
}

