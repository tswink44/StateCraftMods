package com.statecraft.economy.company;

import com.statecraft.economy.config.EconomyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bank-specific data attached to a Company with CompanyType.BANK.
 *
 * Banks hold depositor funds in exchange for paying interest, and can offer loans.
 * They are subject to a fractional reserve ratio — they must keep a percentage of
 * total deposits in their treasury at all times.
 *
 * Depositor balances are stored separately from the company treasury. When a player
 * deposits money into a bank, their personal account is debited and both the
 * depositor balance and the bank treasury are credited. The treasury represents
 * the bank's actual cash holdings, while depositor balances represent liabilities.
 */
public class BankCompany {

    /**
     * Strategy for handling loan defaults.
     */
    public enum DefaultStrategy {
        /** Force-withdraw the full remaining loan amount from borrower's personal account (can go negative). */
        SEIZE_BALANCE,
        /** Repossess the borrower's most valuable chunk to cover the loan via StateCraft integration. */
        REPOSSESS_PROPERTY
    }

    private final UUID companyId; // Links to the parent Company

    // Members — players with deposit accounts at this bank
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();

    // Depositor balances — how much each member has deposited (bank's liability)
    private final Map<UUID, Double> depositorBalances = new ConcurrentHashMap<>();

    // Interest rates
    private double depositInterestRate; // Rate paid TO depositors per interest period (e.g., 0.02 = 2%)
    private double loanInterestRate;    // Rate charged ON loans per accrual period (e.g., 0.05 = 5%)

    // Interest timing
    private long interestPeriodTicks;   // How often depositor interest is paid (game ticks)
    private long lastInterestTime;       // Last game tick when interest was paid

    // Loan accrual timing
    private long loanAccrualPeriodTicks; // How often loan interest accrues
    private long lastLoanAccrualTime;    // Last game tick when loan interest accrued

    // Reserve ratio — fraction of total deposits that must remain in treasury
    private double reserveRatio; // Default from config, overridable per-bank

    // Loan default strategy
    private DefaultStrategy defaultStrategy = DefaultStrategy.SEIZE_BALANCE;

    // Active loans
    private final Map<UUID, Loan> loans = new ConcurrentHashMap<>(); // loanId -> Loan

    public BankCompany(UUID companyId) {
        this.companyId = companyId;
        this.depositInterestRate = EconomyConfig.DEFAULT_DEPOSIT_INTEREST_RATE.get();
        this.loanInterestRate = EconomyConfig.DEFAULT_LOAN_INTEREST_RATE.get();
        this.interestPeriodTicks = EconomyConfig.BANK_INTEREST_PERIOD_TICKS.get();
        this.loanAccrualPeriodTicks = EconomyConfig.LOAN_INTEREST_ACCRUAL_PERIOD_TICKS.get();
        this.reserveRatio = EconomyConfig.DEFAULT_RESERVE_RATIO.get();
        this.lastInterestTime = 0;
        this.lastLoanAccrualTime = 0;
    }

    // ==================== Identity ====================

    public UUID getCompanyId() { return companyId; }

    // ==================== Membership ====================

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean openAccount(UUID playerId) {
        if (members.contains(playerId)) return false;
        members.add(playerId);
        depositorBalances.put(playerId, 0.0);
        return true;
    }

    public boolean closeAccount(UUID playerId) {
        if (!members.contains(playerId)) return false;
        // Cannot close if they have a positive balance — must withdraw first
        double balance = getDepositorBalance(playerId);
        if (balance > 0.01) return false;
        // Cannot close if they have active loans
        if (getActiveLoansForBorrower(playerId).stream().anyMatch(Loan::isActive)) return false;
        members.remove(playerId);
        depositorBalances.remove(playerId);
        return true;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public int getMemberCount() {
        return members.size();
    }

    // ==================== Depositor Balances ====================

    public double getDepositorBalance(UUID playerId) {
        return depositorBalances.getOrDefault(playerId, 0.0);
    }

    /**
     * Add to a depositor's balance. Does NOT modify the company treasury — caller must do that.
     * @return true if successful
     */
    public boolean addDepositorBalance(UUID playerId, double amount) {
        if (!members.contains(playerId) || amount <= 0) return false;
        depositorBalances.merge(playerId, amount, Double::sum);
        return true;
    }

    /**
     * Subtract from a depositor's balance. Does NOT modify the company treasury — caller must do that.
     * Enforces reserve ratio: withdrawal is rejected if it would cause the bank to breach reserve requirements.
     * @param treasuryBalance Current bank treasury balance (passed in to avoid circular dependency)
     * @return true if successful, false if insufficient depositor balance or would breach reserve
     */
    public boolean subtractDepositorBalance(UUID playerId, double amount, double treasuryBalance) {
        if (!members.contains(playerId) || amount <= 0) return false;
        double currentBalance = getDepositorBalance(playerId);
        if (currentBalance < amount) return false;

        // Check reserve ratio compliance after withdrawal
        double totalDepositsAfter = getTotalDeposits() - amount;
        double treasuryAfter = treasuryBalance - amount;
        if (totalDepositsAfter > 0 && treasuryAfter < totalDepositsAfter * reserveRatio) {
            return false; // Would breach reserve requirement
        }

        depositorBalances.put(playerId, currentBalance - amount);
        return true;
    }

    /**
     * Force-subtract from a depositor's balance (no reserve check — used for dissolution).
     */
    public void forceSubtractDepositorBalance(UUID playerId, double amount) {
        double current = getDepositorBalance(playerId);
        depositorBalances.put(playerId, Math.max(0, current - amount));
    }

    /**
     * Subtract from a depositor's balance without reserve checks.
     * Used for fee deductions where the money stays in the bank treasury (not withdrawn).
     * This reduces the bank's liability without reducing its cash, improving reserve compliance.
     */
    public void subtractDepositorBalanceRaw(UUID playerId, double amount) {
        double current = getDepositorBalance(playerId);
        depositorBalances.put(playerId, Math.max(0, current - amount));
    }

    /**
     * Get the total of all depositor balances (bank's total liability to depositors).
     */
    public double getTotalDeposits() {
        return depositorBalances.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public Map<UUID, Double> getAllDepositorBalances() {
        return Collections.unmodifiableMap(depositorBalances);
    }

    // ==================== Reserve Ratio ====================

    public double getReserveRatio() { return reserveRatio; }

    public void setReserveRatio(double ratio) {
        // Cannot go below the global minimum from config
        double globalMin = EconomyConfig.DEFAULT_RESERVE_RATIO.get();
        this.reserveRatio = Math.max(globalMin, Math.min(1.0, ratio));
    }

    /**
     * Set reserve ratio with a national policy override floor.
     * The ratio cannot go below the greater of (global config, national policy).
     */
    public void setReserveRatioWithPolicy(double ratio, double nationalMinimum) {
        double floor = Math.max(EconomyConfig.DEFAULT_RESERVE_RATIO.get(), nationalMinimum);
        this.reserveRatio = Math.max(floor, Math.min(1.0, ratio));
    }

    /**
     * How much of the treasury is available for lending (treasury - required reserves).
     * @param treasuryBalance Current bank treasury balance
     */
    public double getAvailableToLend(double treasuryBalance) {
        double requiredReserve = getTotalDeposits() * reserveRatio;
        return Math.max(0, treasuryBalance - requiredReserve);
    }

    /**
     * Check if the bank is meeting its reserve requirement.
     * @param treasuryBalance Current bank treasury balance
     */
    public boolean isReserveCompliant(double treasuryBalance) {
        double totalDeposits = getTotalDeposits();
        if (totalDeposits <= 0) return true;
        return treasuryBalance >= totalDeposits * reserveRatio;
    }

    /**
     * Get the reserve deficit (negative = compliant, positive = shortfall).
     * @param treasuryBalance Current bank treasury balance
     */
    public double getReserveDeficit(double treasuryBalance) {
        double required = getTotalDeposits() * reserveRatio;
        return required - treasuryBalance;
    }

    // ==================== Interest Configuration ====================

    public double getDepositInterestRate() { return depositInterestRate; }
    public void setDepositInterestRate(double rate) { this.depositInterestRate = Math.max(0, Math.min(1.0, rate)); }

    public double getLoanInterestRate() { return loanInterestRate; }
    public void setLoanInterestRate(double rate) { this.loanInterestRate = Math.max(0, Math.min(1.0, rate)); }

    public long getInterestPeriodTicks() { return interestPeriodTicks; }
    public void setInterestPeriodTicks(long ticks) { this.interestPeriodTicks = Math.max(1200, ticks); }

    public long getLastInterestTime() { return lastInterestTime; }
    public void setLastInterestTime(long time) { this.lastInterestTime = time; }

    public long getLoanAccrualPeriodTicks() { return loanAccrualPeriodTicks; }
    public void setLoanAccrualPeriodTicks(long ticks) { this.loanAccrualPeriodTicks = Math.max(1200, ticks); }

    public long getLastLoanAccrualTime() { return lastLoanAccrualTime; }
    public void setLastLoanAccrualTime(long time) { this.lastLoanAccrualTime = time; }

    // ==================== Default Strategy ====================

    public DefaultStrategy getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(DefaultStrategy strategy) { this.defaultStrategy = strategy; }

    // ==================== Loan Management ====================

    public void addLoan(Loan loan) {
        loans.put(loan.getId(), loan);
    }

    @Nullable
    public Loan getLoan(UUID loanId) {
        return loans.get(loanId);
    }

    public void removeLoan(UUID loanId) {
        loans.remove(loanId);
    }

    public Collection<Loan> getAllLoans() {
        return Collections.unmodifiableCollection(loans.values());
    }

    public List<Loan> getActiveLoans() {
        return loans.values().stream().filter(Loan::isActive).toList();
    }

    public List<Loan> getActiveLoansForBorrower(UUID borrowerId) {
        return loans.values().stream()
            .filter(l -> l.getBorrowerId().equals(borrowerId) && l.isActive())
            .toList();
    }

    public int getActiveLoanCount(UUID borrowerId) {
        return (int) loans.values().stream()
            .filter(l -> l.getBorrowerId().equals(borrowerId) && l.isActive())
            .count();
    }

    public double getTotalOutstandingLoans() {
        return loans.values().stream()
            .filter(Loan::isActive)
            .mapToDouble(Loan::getRemainingBalance)
            .sum();
    }

    // ==================== NBT Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CompanyId", companyId);
        tag.putDouble("DepositInterestRate", depositInterestRate);
        tag.putDouble("LoanInterestRate", loanInterestRate);
        tag.putLong("InterestPeriodTicks", interestPeriodTicks);
        tag.putLong("LastInterestTime", lastInterestTime);
        tag.putLong("LoanAccrualPeriodTicks", loanAccrualPeriodTicks);
        tag.putLong("LastLoanAccrualTime", lastLoanAccrualTime);
        tag.putDouble("ReserveRatio", reserveRatio);
        tag.putString("DefaultStrategy", defaultStrategy.name());

        // Members
        ListTag membersList = new ListTag();
        for (UUID member : members) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("Id", member);
            membersList.add(memberTag);
        }
        tag.put("Members", membersList);

        // Depositor balances
        ListTag depositsList = new ListTag();
        for (Map.Entry<UUID, Double> entry : depositorBalances.entrySet()) {
            CompoundTag depTag = new CompoundTag();
            depTag.putUUID("PlayerId", entry.getKey());
            depTag.putDouble("Balance", entry.getValue());
            depositsList.add(depTag);
        }
        tag.put("DepositorBalances", depositsList);

        // Loans
        ListTag loansList = new ListTag();
        for (Loan loan : loans.values()) {
            loansList.add(loan.save());
        }
        tag.put("Loans", loansList);

        return tag;
    }

    public static BankCompany load(CompoundTag tag) {
        UUID companyId = tag.getUUID("CompanyId");
        BankCompany bank = new BankCompany(companyId);

        bank.depositInterestRate = tag.getDouble("DepositInterestRate");
        bank.loanInterestRate = tag.getDouble("LoanInterestRate");
        if (tag.contains("InterestPeriodTicks")) {
            bank.interestPeriodTicks = tag.getLong("InterestPeriodTicks");
        }
        if (tag.contains("LastInterestTime")) {
            bank.lastInterestTime = tag.getLong("LastInterestTime");
        }
        if (tag.contains("LoanAccrualPeriodTicks")) {
            bank.loanAccrualPeriodTicks = tag.getLong("LoanAccrualPeriodTicks");
        }
        if (tag.contains("LastLoanAccrualTime")) {
            bank.lastLoanAccrualTime = tag.getLong("LastLoanAccrualTime");
        }
        if (tag.contains("ReserveRatio")) {
            bank.reserveRatio = tag.getDouble("ReserveRatio");
        }
        if (tag.contains("DefaultStrategy")) {
            try {
                bank.defaultStrategy = DefaultStrategy.valueOf(tag.getString("DefaultStrategy"));
            } catch (IllegalArgumentException e) {
                bank.defaultStrategy = DefaultStrategy.SEIZE_BALANCE;
            }
        }

        // Members
        if (tag.contains("Members")) {
            ListTag membersList = tag.getList("Members", Tag.TAG_COMPOUND);
            for (int i = 0; i < membersList.size(); i++) {
                bank.members.add(membersList.getCompound(i).getUUID("Id"));
            }
        }

        // Depositor balances
        if (tag.contains("DepositorBalances")) {
            ListTag depositsList = tag.getList("DepositorBalances", Tag.TAG_COMPOUND);
            for (int i = 0; i < depositsList.size(); i++) {
                CompoundTag depTag = depositsList.getCompound(i);
                bank.depositorBalances.put(depTag.getUUID("PlayerId"), depTag.getDouble("Balance"));
            }
        }

        // Loans
        if (tag.contains("Loans")) {
            ListTag loansList = tag.getList("Loans", Tag.TAG_COMPOUND);
            for (int i = 0; i < loansList.size(); i++) {
                Loan loan = Loan.load(loansList.getCompound(i));
                bank.loans.put(loan.getId(), loan);
            }
        }

        return bank;
    }
}

