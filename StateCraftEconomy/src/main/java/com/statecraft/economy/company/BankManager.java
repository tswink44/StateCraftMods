package com.statecraft.economy.company;

import com.statecraft.company.Company;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.Bank;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.Transaction;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for all bank-type companies.
 *
 * Handles:
 * - Depositor interest distribution (pays depositors from bank treasury)
 * - Loan interest accrual (increases totalOwed on active loans)
 * - Loan default handling (seize balance or repossess property)
 * - Reserve compliance monitoring
 * - Bank lifecycle (create, dissolve with depositor insurance)
 */
public class BankManager {
    private static BankManager instance;

    // Bank data indexed by company ID
    private final Map<UUID, BankCompany> bankCompanies = new ConcurrentHashMap<>();

    private boolean dirty = false;

    private BankManager() {}

    public static BankManager getInstance() {
        if (instance == null) {
            instance = new BankManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Bank CRUD ====================

    /**
     * Initialize a new bank for an existing company.
     * Also registers the bank in the BankRegistry.
     */
    public BankCompany createBank(UUID companyId) {
        if (bankCompanies.containsKey(companyId)) return bankCompanies.get(companyId);

        BankCompany bank = new BankCompany(companyId);
        bankCompanies.put(companyId, bank);

        // Register in BankRegistry so players can associate accounts with this bank
        Company company = CompanyManager.getInstance().getCompany(companyId);
        if (company != null) {
            Bank registryBank = new Bank(companyId, company.getName().toLowerCase().replace(" ", "_"), company.getName());
            registryBank.setInterestRate(bank.getDepositInterestRate());
            registryBank.setAllowsLoans(true);
            EconomyManager.getInstance().getBankRegistry().registerBank(registryBank);
        }

        dirty = true;
        StateCraftEconomy.LOGGER.info("Bank created for company {}", companyId);
        return bank;
    }

    /**
     * Dissolve a bank. Pays depositors first (depositor insurance), then remaining goes to shareholders.
     * @param companyId The company ID of the bank
     * @param server The server (for notifications)
     * @return Map of depositor shortfalls (empty if everyone was paid in full)
     */
    public Map<UUID, Double> dissolveBank(UUID companyId, MinecraftServer server) {
        BankCompany bank = bankCompanies.get(companyId);
        if (bank == null) return Collections.emptyMap();

        EconomyManager ecoManager = EconomyManager.getInstance();
        double treasuryBalance = ecoManager.getCompanyBalance(companyId);
        Company company = CompanyManager.getInstance().getCompany(companyId);
        String bankName = company != null ? company.getName() : "Unknown Bank";
        Map<UUID, Double> shortfalls = new HashMap<>();

        // Step 1: Pay all depositors first (depositor insurance)
        double totalDeposits = bank.getTotalDeposits();
        if (totalDeposits > 0) {
            for (Map.Entry<UUID, Double> entry : bank.getAllDepositorBalances().entrySet()) {
                UUID depositorId = entry.getKey();
                double owed = entry.getValue();
                if (owed <= 0.01) continue;

                double canPay = Math.min(owed, treasuryBalance);
                if (canPay > 0.01) {
                    ecoManager.deposit(depositorId, canPay,
                        "Bank dissolution refund from " + bankName);
                    treasuryBalance -= canPay;

                    // Record transaction on the bank account
                    ecoManager.recordAccountTransaction(companyId, Transaction.Type.WITHDRAWAL,
                        canPay, depositorId, "Depositor refund (dissolution) to " + depositorId,
                        null, "Bank Dissolution");
                }

                double shortfall = owed - canPay;
                if (shortfall > 0.01) {
                    shortfalls.put(depositorId, shortfall);
                }

                // Notify depositor
                ServerPlayer depositorPlayer = server.getPlayerList().getPlayer(depositorId);
                if (depositorPlayer != null) {
                    if (shortfall > 0.01) {
                        depositorPlayer.sendSystemMessage(Component.literal(
                            "§c[" + bankName + "] §eDissolved. You were refunded §f" +
                            ecoManager.formatCurrency(canPay) + "§e but §c" +
                            ecoManager.formatCurrency(shortfall) + "§e was lost due to insufficient bank funds."));
                    } else {
                        depositorPlayer.sendSystemMessage(Component.literal(
                            "§a[" + bankName + "] §eDissolved. Your full deposit of §f" +
                            ecoManager.formatCurrency(owed) + "§e has been refunded."));
                    }
                }
            }
        }

        // Step 2: Mark all active loans as seized (bank no longer exists)
        for (Loan loan : bank.getAllLoans()) {
            if (loan.isActive()) {
                loan.setStatus(Loan.LoanStatus.SEIZED);
            }
        }

        // Step 3: Update treasury to reflect remaining balance (after paying depositors)
        ecoManager.getOrCreateCompanyTreasury(companyId).setBalance(treasuryBalance);

        // Step 4: Unregister from BankRegistry
        ecoManager.getBankRegistry().removeBank(companyId);

        // Step 5: Remove bank data
        bankCompanies.remove(companyId);
        dirty = true;

        if (!shortfalls.isEmpty()) {
            StateCraftEconomy.LOGGER.warn("Bank '{}' dissolved with {} depositors suffering losses",
                bankName, shortfalls.size());
        }

        return shortfalls;
    }

    // ==================== Lookups ====================

    @Nullable
    public BankCompany getBank(UUID companyId) {
        return bankCompanies.get(companyId);
    }

    public boolean isBank(UUID companyId) {
        return bankCompanies.containsKey(companyId);
    }

    public Collection<BankCompany> getAllBanks() {
        return Collections.unmodifiableCollection(bankCompanies.values());
    }

    /**
     * Get all banks where a player has an account.
     */
    public List<BankCompany> getPlayerBanks(UUID playerId) {
        return bankCompanies.values().stream()
            .filter(b -> b.isMember(playerId))
            .toList();
    }

    /**
     * Get a bank by its associated company name (case-insensitive).
     */
    @Nullable
    public BankCompany getBankByName(String name) {
        Company company = CompanyManager.getInstance().getCompanyByName(name);
        if (company == null) return null;
        return bankCompanies.get(company.getId());
    }

    // ==================== Tick ====================

    /**
     * Called every server tick. Processes depositor interest, loan accrual, and defaults.
     */
    public void tick(MinecraftServer server) {
        long currentTime = server.overworld().getGameTime();

        for (BankCompany bank : bankCompanies.values()) {
            // Depositor interest
            if (bank.getDepositInterestRate() > 0 &&
                currentTime - bank.getLastInterestTime() >= bank.getInterestPeriodTicks()) {
                payDepositorInterest(server, bank, currentTime);
            }

            // Loan interest accrual + missed payment tracking
            if (currentTime - bank.getLastLoanAccrualTime() >= bank.getLoanAccrualPeriodTicks()) {
                accrueAndCheckLoans(server, bank, currentTime);
            }
        }
    }

    // ==================== Depositor Interest ====================

    /**
     * Pay interest to all depositors of a bank from the bank's treasury.
     */
    private void payDepositorInterest(MinecraftServer server, BankCompany bank, long currentTime) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        UUID companyId = bank.getCompanyId();
        double treasuryBalance = ecoManager.getCompanyBalance(companyId);
        Company company = CompanyManager.getInstance().getCompany(companyId);
        String bankName = company != null ? company.getName() : "Bank";

        double totalInterest = 0;
        Map<UUID, Double> payouts = new HashMap<>();

        // Calculate interest for each depositor
        for (Map.Entry<UUID, Double> entry : bank.getAllDepositorBalances().entrySet()) {
            UUID depositorId = entry.getKey();
            double balance = entry.getValue();
            if (balance <= 0.01) continue;

            double interest = balance * bank.getDepositInterestRate();
            if (interest < 0.01) continue;

            payouts.put(depositorId, interest);
            totalInterest += interest;
        }

        if (totalInterest <= 0) {
            bank.setLastInterestTime(currentTime);
            return;
        }

        // Check if bank can afford to pay
        if (treasuryBalance < totalInterest) {
            // Insufficient funds — defer interest and warn officers
            bank.setLastInterestTime(currentTime);
            dirty = true;

            if (company != null) {
                ServerPlayer founder = server.getPlayerList().getPlayer(company.getFounderId());
                if (founder != null) {
                    founder.sendSystemMessage(Component.literal(
                        "§c[" + bankName + "] §eDepositor interest deferred — insufficient treasury funds. " +
                        "Need: " + ecoManager.formatCurrency(totalInterest) +
                        ", Have: " + ecoManager.formatCurrency(treasuryBalance)));
                }
            }
            return;
        }

        // Pay interest: add to each depositor's balance and debit the bank treasury
        for (Map.Entry<UUID, Double> entry : payouts.entrySet()) {
            UUID depositorId = entry.getKey();
            double interest = entry.getValue();

            // Add to depositor's bank balance
            bank.addDepositorBalance(depositorId, interest);

            // Record transaction for the depositor (on their personal account history)
            ecoManager.recordAccountTransaction(depositorId, Transaction.Type.INTEREST,
                interest, companyId, "Interest from " + bankName,
                null, "Bank Interest");

            // Notify online depositor
            ServerPlayer depositor = server.getPlayerList().getPlayer(depositorId);
            if (depositor != null) {
                depositor.sendSystemMessage(Component.literal(
                    "§a[" + bankName + "] §fInterest earned: " + ecoManager.formatCurrency(interest) +
                    " §7(New bank balance: " + ecoManager.formatCurrency(bank.getDepositorBalance(depositorId)) + ")"));
            }
        }

        // Debit the bank treasury
        ecoManager.getOrCreateCompanyTreasury(companyId).subtract(totalInterest);
        ecoManager.recordAccountTransaction(companyId, Transaction.Type.WITHDRAWAL,
            totalInterest, null, "Depositor interest payments (" + payouts.size() + " depositors)",
            null, "Bank Interest System");

        bank.setLastInterestTime(currentTime);
        dirty = true;
        ecoManager.markDirty();

        StateCraftEconomy.LOGGER.debug("Bank '{}' paid {} in interest to {} depositors",
            bankName, ecoManager.formatCurrency(totalInterest), payouts.size());
    }

    // ==================== Loan Interest & Default ====================

    /**
     * Accrue interest on all active loans and check for defaults.
     */
    private void accrueAndCheckLoans(MinecraftServer server, BankCompany bank, long currentTime) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        UUID companyId = bank.getCompanyId();
        Company company = CompanyManager.getInstance().getCompany(companyId);
        String bankName = company != null ? company.getName() : "Bank";
        int defaultThreshold = EconomyConfig.LOAN_DEFAULT_THRESHOLD.get();

        for (Loan loan : bank.getActiveLoans()) {
            // Accrue interest
            double interest = loan.accrueInterest();
            if (interest > 0.01) {
                ecoManager.recordAccountTransaction(loan.getBorrowerId(), Transaction.Type.INTEREST,
                    interest, companyId,
                    "Loan interest accrued from " + bankName + " (Loan " + loan.getId().toString().substring(0, 8) + ")",
                    null, "Loan System");
            }

            // Check if payment was made since last accrual
            if (loan.getLastPaymentTime() < loan.getLastAccrualTime() - bank.getLoanAccrualPeriodTicks()) {
                loan.recordMissedPayment();

                // Notify borrower
                ServerPlayer borrower = server.getPlayerList().getPlayer(loan.getBorrowerId());
                if (borrower != null) {
                    int remaining = defaultThreshold - loan.getMissedPayments();
                    borrower.sendSystemMessage(Component.literal(
                        "§c[" + bankName + "] §eMissed loan payment! Owed: " +
                        ecoManager.formatCurrency(loan.getRemainingBalance()) +
                        (remaining > 0 ? " §7(" + remaining + " more missed payments until default)" : " §c§lDEFAULT IMMINENT")));
                }
            }

            // Check for default
            if (loan.getMissedPayments() >= defaultThreshold) {
                handleLoanDefault(server, bank, loan, bankName);
            }
        }

        bank.setLastLoanAccrualTime(currentTime);
        dirty = true;
    }

    /**
     * Handle a loan that has defaulted.
     */
    private void handleLoanDefault(MinecraftServer server, BankCompany bank, Loan loan, String bankName) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        double remaining = loan.getRemainingBalance();

        switch (bank.getDefaultStrategy()) {
            case SEIZE_BALANCE -> {
                // Force-withdraw from borrower's personal account (can go negative)
                ecoManager.forceWithdraw(loan.getBorrowerId(), remaining,
                    "Loan default seizure by " + bankName);

                // Credit the bank treasury
                ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).add(remaining);
                ecoManager.recordAccountTransaction(bank.getCompanyId(), Transaction.Type.LOAN_REPAYMENT,
                    remaining, loan.getBorrowerId(),
                    "Loan default seizure (Loan " + loan.getId().toString().substring(0, 8) + ")",
                    null, "Loan Default System");

                loan.setStatus(Loan.LoanStatus.SEIZED);

                ServerPlayer borrower = server.getPlayerList().getPlayer(loan.getBorrowerId());
                if (borrower != null) {
                    borrower.sendSystemMessage(Component.literal(
                        "§c§l[" + bankName + "] LOAN DEFAULT: §r§c" +
                        ecoManager.formatCurrency(remaining) +
                        " has been seized from your account for defaulted loan."));
                }

                StateCraftEconomy.LOGGER.info("Loan {} defaulted — {} seized from player {}",
                    loan.getId(), ecoManager.formatCurrency(remaining), loan.getBorrowerId());
            }
            case REPOSSESS_PROPERTY -> {
                loan.setStatus(Loan.LoanStatus.DEFAULTED);

                // Attempt to repossess via StateCraft integration
                if (StateCraftEconomy.isStateCraftLoaded()) {
                    boolean repossessed = StateCraftIntegration.repossessPlayerChunkForLoan(
                        loan.getBorrowerId(), remaining, bankName);

                    if (repossessed) {
                        // Credit the loan value to the bank treasury
                        ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).add(remaining);
                        ecoManager.recordAccountTransaction(bank.getCompanyId(), Transaction.Type.LOAN_REPAYMENT,
                            remaining, loan.getBorrowerId(),
                            "Loan default — property repossessed (Loan " + loan.getId().toString().substring(0, 8) + ")",
                            null, "Loan Default System");
                        loan.setStatus(Loan.LoanStatus.SEIZED);
                    } else {
                        // Repossession failed — fall back to balance seizure
                        ecoManager.forceWithdraw(loan.getBorrowerId(), remaining,
                            "Loan default — repossession failed, balance seized by " + bankName);
                        ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).add(remaining);
                        loan.setStatus(Loan.LoanStatus.SEIZED);
                    }
                } else {
                    // No StateCraft — fall back to balance seizure
                    ecoManager.forceWithdraw(loan.getBorrowerId(), remaining,
                        "Loan default seizure by " + bankName);
                    ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).add(remaining);
                    loan.setStatus(Loan.LoanStatus.SEIZED);
                }

                ServerPlayer borrower = server.getPlayerList().getPlayer(loan.getBorrowerId());
                if (borrower != null) {
                    borrower.sendSystemMessage(Component.literal(
                        "§c§l[" + bankName + "] LOAN DEFAULT: §r§cYour property has been repossessed " +
                        "for defaulted loan of " + ecoManager.formatCurrency(remaining)));
                }
            }
        }

        dirty = true;
        ecoManager.markDirty();
    }

    // ==================== Loan Application ====================

    /**
     * Apply for a loan from a bank.
     * @return The created Loan, or null if application was rejected
     */
    @Nullable
    public Loan applyForLoan(UUID playerId, UUID bankCompanyId, double amount) {
        BankCompany bank = bankCompanies.get(bankCompanyId);
        if (bank == null) return null;
        if (!bank.isMember(playerId)) return null;

        // Check max active loans per player
        int maxLoans = EconomyConfig.MAX_ACTIVE_LOANS_PER_PLAYER.get();
        if (maxLoans > 0 && bank.getActiveLoanCount(playerId) >= maxLoans) return null;

        // Check max loan amount
        double maxAmount = EconomyConfig.MAX_LOAN_AMOUNT.get();
        if (maxAmount > 0 && amount > maxAmount) return null;

        // Check available lending capacity (reserve compliance)
        EconomyManager ecoManager = EconomyManager.getInstance();
        double treasuryBalance = ecoManager.getCompanyBalance(bankCompanyId);
        double available = bank.getAvailableToLend(treasuryBalance);
        if (amount > available) return null;

        // Check reserve compliance (don't lend if already non-compliant)
        if (!bank.isReserveCompliant(treasuryBalance)) return null;

        // Create the loan
        Loan loan = new Loan(UUID.randomUUID(), bankCompanyId, playerId, amount, bank.getLoanInterestRate());
        bank.addLoan(loan);

        // Transfer funds from bank treasury to borrower's personal account
        ecoManager.getOrCreateCompanyTreasury(bankCompanyId).subtract(amount);
        ecoManager.deposit(playerId, amount, "Loan from " + getCompanyName(bankCompanyId));

        // Record transactions
        ecoManager.recordAccountTransaction(bankCompanyId, Transaction.Type.WITHDRAWAL,
            amount, playerId, "Loan issued to " + playerId,
            null, "Loan System");
        ecoManager.recordAccountTransaction(playerId, Transaction.Type.DEPOSIT,
            amount, bankCompanyId, "Loan received from " + getCompanyName(bankCompanyId),
            null, "Loan System");

        dirty = true;
        ecoManager.markDirty();

        StateCraftEconomy.LOGGER.info("Loan {} issued: {} from bank {} to player {}",
            loan.getId(), ecoManager.formatCurrency(amount), getCompanyName(bankCompanyId), playerId);
        return loan;
    }

    /**
     * Repay a loan.
     * @return The amount actually applied to the loan
     */
    public double repayLoan(UUID playerId, UUID loanId, double amount) {
        // Find the loan
        Loan loan = null;
        BankCompany bank = null;
        for (BankCompany b : bankCompanies.values()) {
            Loan l = b.getLoan(loanId);
            if (l != null && l.getBorrowerId().equals(playerId)) {
                loan = l;
                bank = b;
                break;
            }
        }

        if (loan == null || !loan.isActive()) return 0;

        EconomyManager ecoManager = EconomyManager.getInstance();

        // Check player has enough
        double playerBalance = ecoManager.getBalance(playerId);
        double paymentAmount = Math.min(amount, Math.min(playerBalance, loan.getRemainingBalance()));
        if (paymentAmount <= 0.01) return 0;

        // Withdraw from player
        ecoManager.withdraw(playerId, paymentAmount,
            "Loan repayment to " + getCompanyName(bank.getCompanyId()));

        // Apply to loan
        double applied = loan.makePayment(paymentAmount);

        // Credit the bank treasury
        ecoManager.getOrCreateCompanyTreasury(bank.getCompanyId()).add(applied);

        // Record transactions
        ecoManager.recordAccountTransaction(bank.getCompanyId(), Transaction.Type.LOAN_REPAYMENT,
            applied, playerId, "Loan repayment from " + playerId +
            " (Loan " + loanId.toString().substring(0, 8) + ")",
            null, "Loan System");

        dirty = true;
        ecoManager.markDirty();

        if (loan.isFullyRepaid()) {
            StateCraftEconomy.LOGGER.info("Loan {} fully repaid by {}", loanId, playerId);
        }

        return applied;
    }

    // ==================== Helpers ====================

    private String getCompanyName(UUID companyId) {
        Company company = CompanyManager.getInstance().getCompany(companyId);
        return company != null ? company.getName() : "Unknown";
    }

    // ==================== Persistence ====================

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag banksList = new ListTag();
        for (BankCompany bank : bankCompanies.values()) {
            banksList.add(bank.save());
        }
        tag.put("Banks", banksList);
        return tag;
    }

    public void load(CompoundTag tag) {
        bankCompanies.clear();

        if (tag.contains("Banks")) {
            ListTag banksList = tag.getList("Banks", Tag.TAG_COMPOUND);
            for (int i = 0; i < banksList.size(); i++) {
                BankCompany bank = BankCompany.load(banksList.getCompound(i));
                bankCompanies.put(bank.getCompanyId(), bank);
            }
        }

        // Ensure all loaded bank companies are registered in the BankRegistry
        // This handles cases where the BankRegistry load didn't include them
        for (BankCompany bank : bankCompanies.values()) {
            if (!EconomyManager.getInstance().getBankRegistry().bankExists(bank.getCompanyId())) {
                Company company = CompanyManager.getInstance().getCompany(bank.getCompanyId());
                if (company != null) {
                    Bank registryBank = new Bank(bank.getCompanyId(),
                        company.getName().toLowerCase().replace(" ", "_"),
                        company.getName());
                    registryBank.setInterestRate(bank.getDepositInterestRate());
                    registryBank.setAllowsLoans(true);
                    EconomyManager.getInstance().getBankRegistry().registerBank(registryBank);
                    StateCraftEconomy.LOGGER.info("Re-registered bank company '{}' in BankRegistry", company.getName());
                }
            }
        }

        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded {} bank companies", bankCompanies.size());
    }
}

