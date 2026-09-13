package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.EconomyAccess.Transfer;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Banking {
    private static final BigInteger TEN_THOUSAND = BigInteger.valueOf(10_000);
    private static final Set<String> OPEN_LOANS = Set.of("REQUESTED", "ACTIVE", "DEFAULTED");
    public record DepositSummary(long balance, long interestPrincipal, BigInteger unpaidInterest) {}
    public record LoanSummary(long principal, long interest, long total, long currentlyDue, String status,
                              int missedPayments, String terms, boolean autoPay, long nextDueAt) {}
    private record Interest(BigInteger earned, String remainder) {}
    private final EconomyEngine e;

    Banking(EconomyEngine engine) { e = engine; }

    public EconomyData.Bank bank(String id, boolean active) {
        if (id == null) throw new UserError("Choose a bank first with bank associate.");
        EconomyData.Bank bank = e.data.banks.get(id);
        if (bank == null) {
            List<EconomyData.Bank> matching = e.data.banks.values().stream().filter(b -> b.name.equalsIgnoreCase(id)).toList();
            if (matching.size() == 1) bank = matching.get(0);
        }
        if (bank == null || (active && bank.closed)) throw new UserError("Unknown or closed bank.");
        return bank;
    }

    void requireManager(Actor actor, EconomyData.Bank bank) {
        e.requireAccount(actor, "company:" + bank.company);
    }

    public String create(Actor actor, String companyId, String name, long capital,
                         int depositRate, int loanRate) {
        EconomyData.Bank bank = quoteCreate(actor, companyId, name, capital, depositRate, loanRate);
        e.ledger.prepare(creationTransfers(bank, capital)).commit();
        e.data.banks.put(bank.id, bank);
        event(bank.id, actor.id().toString(), "OPENED", capital, name);
        e.dirty.run();
        return bank.id;
    }

    EconomyData.Bank quoteCreate(Actor actor, String companyId, String name, long capital,
                                int depositRate, int loanRate) {
        e.ledger.thread();
        GovernanceAccess.CompanyView company = e.governance.company(companyId).orElseThrow(() -> new UserError("Unknown company."));
        e.requireAccount(actor, company.account());
        if (e.data.banks.containsKey(company.id())) throw new UserError("This company already has a bank identity, including a closed bank.");
        branding(name);
        if (e.data.banks.values().stream().anyMatch(b -> b.name.equalsIgnoreCase(name))) throw new UserError("That bank name is already in use.");
        checkRates(depositRate, loanRate, 0);
        Money.nonNegative(capital);
        if (capital < e.config.minimumBankReserveCents) throw new UserError("Seed capital must cover the configured minimum bank reserve.");
        EconomyData.Bank bank = new EconomyData.Bank();
        bank.id = company.id();
        bank.company = company.id();
        bank.name = name;
        bank.branding = name;
        bank.depositInterestBps = depositRate;
        bank.loanInterestBps = loanRate;
        bank.interestPeriodMillis = e.config.financialPeriodMillis;
        bank.openedAt = e.clock.millis();
        return bank;
    }

    List<Transfer> creationTransfers(EconomyData.Bank bank, long capital) {
        return List.of(new Transfer("company:" + bank.company, bank.account(), capital, "Bank seed capital"),
                new Transfer("company:" + bank.company, "system:fees", e.config.bankCreationFeeCents, "Bank creation fee"));
    }

    static void branding(String text) {
        if (text == null || text.isBlank() || text.length() > 64 || text.chars().anyMatch(Character::isISOControl)) {
            throw new UserError("Bank names and branding must contain 1-64 printable characters.");
        }
    }

    public void brand(Actor actor, String id, String name, String branding) {
        EconomyData.Bank bank = bank(id, true);
        requireManager(actor, bank);
        branding(name);
        branding(branding);
        if (e.data.banks.values().stream().anyMatch(b -> b != bank && b.name.equalsIgnoreCase(name))) {
            throw new UserError("That bank name is already in use.");
        }
        bank.name = name;
        bank.branding = branding;
        event(bank.id, actor.id().toString(), "BRANDING", 0, name + " / " + branding);
        e.dirty.run();
    }

    void checkRates(int deposit, int loan, int fee) {
        Money.tax(0, deposit);
        Money.tax(0, loan);
        Money.tax(0, fee);
        if (deposit > e.config.maximumDepositInterestBps || loan > e.config.maximumLoanInterestBps
                || fee > e.config.maximumOriginationFeeBps) throw new UserError("A bank rate exceeds the server's maximum.");
    }

    public void rates(Actor actor, String id, int deposit, int loan, int origination,
                      long depositFee, long withdrawalFee) {
        EconomyData.Bank bank = bank(id, true);
        requireManager(actor, bank);
        checkRates(deposit, loan, origination);
        Money.nonNegative(depositFee);
        Money.nonNegative(withdrawalFee);
        bank.depositInterestBps = deposit;
        bank.loanInterestBps = loan;
        bank.originationFeeBps = origination;
        bank.depositFeeCents = depositFee;
        bank.withdrawalFeeCents = withdrawalFee;
        event(bank.id, actor.id().toString(), "TERMS", 0, "Future deposits/loans: " + deposit + "/" + loan
                + " bps; origination=" + origination + " bps. Existing interest contracts are unchanged.");
        e.dirty.run();
    }

    public void close(Actor actor, String id) {
        e.ledger.thread();
        EconomyData.Bank bank = bank(id, true);
        requireManager(actor, bank);
        if (liabilities(bank).signum() != 0 || e.data.loans.values().stream().anyMatch(l -> l.bank.equals(bank.id) && OPEN_LOANS.contains(l.status))) {
            throw new UserError("A bank cannot close while it has deposits, unpaid depositor interest, loans, or loan applications.");
        }
        e.ledger.prepare(List.of(new Transfer(bank.account(), "company:" + bank.company, e.balance(bank.account()),
                "Bank closure capital return")), List.of(), true, Map.of(bank.account(), 0L)).commit();
        bank.closed = true;
        e.data.bankAssociations.values().removeIf(value -> value.equals(bank.id));
        event(bank.id, actor.id().toString(), "CLOSED", 0, bank.name);
        e.dirty.run();
    }

    public void associate(Actor actor, String id) {
        EconomyData.Bank bank = bank(id, true);
        e.data.bankAssociations.put(actor.id().toString(), bank.id);
        e.dirty.run();
    }

    public String associatedBank(Actor actor) {
        return bank(e.data.bankAssociations.get(actor.id().toString()), true).id;
    }

    public void capital(Actor actor, String id, boolean withdraw, long amount) {
        e.ledger.thread();
        EconomyData.Bank bank = bank(id, true);
        requireManager(actor, bank);
        Money.positive(amount);
        e.ledger.prepare(List.of(new Transfer(withdraw ? bank.account() : "company:" + bank.company,
                withdraw ? "company:" + bank.company : bank.account(), amount, "Bank capital " + (withdraw ? "withdrawal" : "contribution")))).commit();
        event(bank.id, actor.id().toString(), "CAPITAL", amount, withdraw ? "withdrawn" : "contributed");
    }

    public long deposit(Actor actor, String id, long amount) {
        e.ledger.thread();
        EconomyData.Bank bank = bank(id, true);
        Money.positive(amount);
        if (amount <= bank.depositFeeCents) throw new UserError("The deposit must exceed the bank's deposit fee.");
        String player = actor.id().toString();
        EconomyData.Deposit deposit = bank.deposits.get(player);
        if (deposit == null && bank.deposits.size() >= e.config.maximumBankCustomers) throw new UserError("This bank has reached its customer limit.");
        long now = e.clock.millis();
        if (deposit != null) accrueDeposit(deposit, now);
        DepositQuote quote = quoteDeposit(actor, id, amount);
        e.ledger.prepare(List.of(quote.transfer())).commit();
        if (deposit == null) {
            deposit = new EconomyData.Deposit();
            deposit.lastAccruedAt = now;
            deposit.periodMillis = bank.interestPeriodMillis;
            deposit.rateBps = bank.depositInterestBps;
            bank.deposits.put(player, deposit);
        } else if (deposit.principal == 0) {
            deposit.periodMillis = bank.interestPeriodMillis;
            deposit.rateBps = bank.depositInterestBps;
            deposit.lastAccruedAt = now;
        }
        deposit.balance = quote.newBalance();
        deposit.principal = quote.newPrincipal();
        e.data.bankAssociations.put(player, bank.id);
        e.schedule("deposit", bank.id + "|" + player);
        event(bank.id, player, "DEPOSIT", quote.credited(), "Fee " + Money.format(bank.depositFeeCents));
        e.dirty.run();
        return quote.credited();
    }

    record DepositQuote(EconomyData.Bank bank, long credited, long newBalance, long newPrincipal, Transfer transfer) {}

    DepositQuote quoteDeposit(Actor actor, String id, long amount) {
        EconomyData.Bank bank = bank(id, true);
        Money.positive(amount);
        if (amount <= bank.depositFeeCents) throw new UserError("The deposit must exceed the bank's deposit fee.");
        EconomyData.Deposit deposit = bank.deposits.get(actor.id().toString());
        if (deposit == null && bank.deposits.size() >= e.config.maximumBankCustomers) throw new UserError("This bank has reached its customer limit.");
        long net = amount - bank.depositFeeCents;
        Money.add(depositBalances(bank), net);
        return new DepositQuote(bank, net, Money.add(deposit == null ? 0 : deposit.balance, net),
                Money.add(deposit == null ? 0 : deposit.principal, net),
                new Transfer(actor.account(), bank.account(), amount, "Bank deposit at " + bank.id));
    }

    public void withdraw(Actor actor, String id, long amount) {
        e.ledger.thread();
        WalletFunding funding = walletFunding(actor, id, amount);
        e.ledger.prepare(List.of(funding.transfer()), List.of(), true, funding.reserveOverrides()).commitWith(funding::commit);
    }

    final class WalletFunding {
        private final EconomyData.Bank bank;
        private final EconomyData.Deposit deposit;
        private final String player;
        private final long amount;
        private final long debit;
        private final long newPrincipal;
        private final long floor;

        WalletFunding(EconomyData.Bank bank, EconomyData.Deposit deposit, String player, long amount, long debit, long now) {
            this.bank = bank;
            this.deposit = deposit;
            this.player = player;
            this.amount = amount;
            this.debit = debit;
            long earned = deposit.balance - deposit.principal;
            newPrincipal = deposit.principal - Math.max(0, debit - earned);
            floor = reserveFor(liabilities(bank, now).subtract(BigInteger.valueOf(debit)));
        }
        Transfer transfer() { return new Transfer(bank.account(), "player:" + player, amount, "Bank withdrawal at " + bank.id); }
        Map<String, Long> reserveOverrides() { return Map.of(bank.account(), floor); }
        long fee() { return debit - amount; }
        long debit() { return debit; }
        long balanceBefore() { return deposit.balance; }
        void commit() {
            deposit.principal = newPrincipal;
            deposit.balance -= debit;
            event(bank.id, player, "WITHDRAWAL", amount, "Fee " + Money.format(bank.withdrawalFeeCents));
        }
    }

    WalletFunding walletFunding(Actor actor, String id, long amount) {
        EconomyData.Bank bank = bank(id, true);
        Money.positive(amount);
        EconomyData.Deposit deposit = bank.deposits.get(actor.id().toString());
        if (deposit == null) throw new UserError("You have no deposit at this bank.");
        long now = e.clock.millis();
        accrueDeposit(deposit, now);
        fundInterest(bank, actor.id().toString(), deposit);
        return withdrawal(actor, bank, deposit, amount, now);
    }

    WalletFunding quoteWalletFunding(Actor actor, String id, long amount) {
        EconomyData.Bank bank = bank(id, true);
        long now = e.clock.millis();
        EconomyData.Deposit stored = bank.deposits.get(actor.id().toString());
        if (stored == null) throw new UserError("You have no deposit at this bank.");
        EconomyData.Deposit projected = new EconomyData.Deposit();
        projected.balance = stored.balance;
        projected.principal = stored.principal;
        projected.pendingInterest = new BigInteger(stored.pendingInterest).add(depositInterest(stored, now).earned()).toString();
        projected.balance = Money.add(projected.balance, fundableInterest(bank, projected));
        return withdrawal(actor, bank, projected, amount, now);
    }

    private WalletFunding withdrawal(Actor actor, EconomyData.Bank bank, EconomyData.Deposit deposit, long amount, long now) {
        Money.positive(amount);
        long debit = Money.add(amount, bank.withdrawalFeeCents);
        if (deposit.balance < debit) throw new UserError("Your bank deposit does not cover the withdrawal plus fee.");
        return new WalletFunding(bank, deposit, actor.id().toString(), amount, debit, now);
    }

    public DepositSummary balance(Actor actor, String bankId, String customer) {
        EconomyData.Bank bank = bank(bankId, false);
        String player = actor.id().toString();
        if (customer != null) {
            try {
                player = UUID.fromString(customer).toString();
                if (!player.equals(customer)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new UserError("Customer IDs must be full lowercase UUIDs.");
            }
        }
        if (!player.equals(actor.id().toString())) requireManager(actor, bank);
        EconomyData.Deposit deposit = bank.deposits.get(player);
        if (deposit == null) return new DepositSummary(0, 0, BigInteger.ZERO);
        return new DepositSummary(deposit.balance, deposit.principal, new BigInteger(deposit.pendingInterest));
    }

    public long depositBalances(EconomyData.Bank bank) {
        long total = 0;
        for (EconomyData.Deposit deposit : bank.deposits.values()) total = Money.add(total, deposit.balance);
        return total;
    }

    public BigInteger liabilities(EconomyData.Bank bank) {
        return liabilities(bank, e.clock.millis());
    }

    private BigInteger liabilities(EconomyData.Bank bank, long now) {
        BigInteger total = BigInteger.ZERO;
        for (EconomyData.Deposit deposit : bank.deposits.values()) {
            total = total.add(BigInteger.valueOf(deposit.balance)).add(new BigInteger(deposit.pendingInterest))
                    .add(depositInterest(deposit, now).earned());
        }
        return total;
    }

    private long reserveFor(BigInteger liabilities) {
        BigInteger reserve = liabilities.multiply(BigInteger.valueOf(e.config.minimumBankReserveBps))
                .add(TEN_THOUSAND.subtract(BigInteger.ONE)).divide(TEN_THOUSAND)
                .max(BigInteger.valueOf(e.config.minimumBankReserveCents));
        return reserve.min(BigInteger.valueOf(Money.MAX)).longValueExact();
    }

    public long requiredReserve(EconomyData.Bank bank) { return requiredReserve(bank, e.clock.millis()); }

    private long requiredReserve(EconomyData.Bank bank, long now) {
        return bank.closed ? 0 : reserveFor(liabilities(bank, now));
    }

    public long protectedBalance(EconomyData.Bank bank) {
        return protectedBalance(bank, e.clock.millis());
    }

    private long protectedBalance(EconomyData.Bank bank, long now) {
        if (bank.closed) return 0;
        BigInteger liabilities = liabilities(bank, now);
        return Math.max(reserveFor(liabilities), liabilities.min(BigInteger.valueOf(Money.MAX)).longValueExact());
    }

    void guardReserves(Map<String, Long> balances, Map<String, Long> overrides) {
        long now = e.clock.millis();
        for (Map.Entry<String, Long> account : balances.entrySet()) {
            if (!account.getKey().startsWith("bank:")) continue;
            EconomyData.Bank bank = e.data.banks.get(account.getKey().substring(5));
            if (bank == null || bank.closed) continue;
            long floor = overrides.containsKey(account.getKey()) ? overrides.get(account.getKey()) : protectedBalance(bank, now);
            Money.nonNegative(floor);
            if (account.getValue() < e.balance(account.getKey()) && account.getValue() < floor) {
                throw new UserError("Bank reserves or customer liabilities protect these funds. Use the banking deposit/loan commands.");
            }
        }
    }

    // Reserve/exposure validation and booked accrual must use the same saved-contract arithmetic.
    private static Interest interest(long principal, int rateBps, long periodMillis, long lastAccruedAt,
                                     String remainder, long now) {
        if (now <= lastAccruedAt) return new Interest(BigInteger.ZERO, remainder);
        BigInteger denominator = TEN_THOUSAND.multiply(BigInteger.valueOf(periodMillis));
        BigInteger duration = BigInteger.valueOf(now).subtract(BigInteger.valueOf(lastAccruedAt));
        BigInteger numerator = BigInteger.valueOf(principal).multiply(BigInteger.valueOf(rateBps))
                .multiply(duration).add(new BigInteger(remainder));
        BigInteger[] amount = numerator.divideAndRemainder(denominator);
        return new Interest(amount[0], amount[1].toString());
    }

    private static Interest depositInterest(EconomyData.Deposit deposit, long now) {
        return interest(deposit.principal, deposit.rateBps, deposit.periodMillis, deposit.lastAccruedAt,
                deposit.interestRemainder, now);
    }

    private void accrueDeposit(EconomyData.Deposit deposit, long now) {
        if (now <= deposit.lastAccruedAt) return;
        Interest interest = depositInterest(deposit, now);
        boolean changed = interest.earned().signum() != 0 || !deposit.interestRemainder.equals(interest.remainder());
        deposit.pendingInterest = new BigInteger(deposit.pendingInterest).add(interest.earned()).toString();
        deposit.interestRemainder = interest.remainder();
        deposit.lastAccruedAt = now;
        // An idle clock advance alone can wait for the runtime's periodic checkpoint.
        if (changed) e.dirty.run();
    }

    private long fundInterest(EconomyData.Bank bank, String player, EconomyData.Deposit deposit) {
        BigInteger pending = new BigInteger(deposit.pendingInterest);
        if (pending.signum() == 0) return 0;
        long funded = fundableInterest(bank, deposit);
        if (funded == 0) return 0;
        deposit.balance = Money.add(deposit.balance, funded);
        deposit.pendingInterest = pending.subtract(BigInteger.valueOf(funded)).toString();
        event(bank.id, player, "FUNDED_INTEREST", funded, "Allocated from bank equity; not newly minted currency.");
        e.dirty.run();
        return funded;
    }

    private long fundableInterest(EconomyData.Bank bank, EconomyData.Deposit deposit) {
        long balances = depositBalances(bank);
        long equity = Math.max(0, e.balance(bank.account()) - balances);
        long available = Math.max(0, equity - e.config.minimumBankReserveCents);
        available = Math.min(available, Money.MAX - balances);
        available = Math.min(available, Money.MAX - deposit.balance);
        return new BigInteger(deposit.pendingInterest).min(BigInteger.valueOf(available)).longValueExact();
    }

    boolean tickDeposit(String reference) {
        String[] parts = reference.split("\\|", 2);
        EconomyData.Bank bank = e.data.banks.get(parts[0]);
        if (bank == null || bank.closed) return false;
        EconomyData.Deposit deposit = bank.deposits.get(parts[1]);
        if (deposit == null) return false;
        accrueDeposit(deposit, e.clock.millis());
        fundInterest(bank, parts[1], deposit);
        return true;
    }

    public String requestLoan(Actor actor, String bankId, long principal, int periods, String collateral,
                              boolean consentBalance, boolean consentRepossession, boolean autoPay) {
        EconomyData.Loan loan = prepareLoan(actor, bankId, principal, periods, collateral, consentBalance, consentRepossession, autoPay, true);
        EconomyData.Bank bank = bank(loan.bank, true);
        e.data.loans.put(loan.id, loan);
        e.schedule("loan", loan.id);
        event(bank.id, loan.borrower, "LOAN_REQUEST", principal, loan.id + ": " + loan.terms);
        e.mail(loan.borrower, "Loan application recorded", loan.id + "\n" + loan.terms);
        e.governance.company(bank.company).ifPresent(company ->
                e.mail(company.owner().toString(), "Loan approval requested", loan.borrower + " requests " + Money.format(principal) + "; ID " + loan.id));
        e.dirty.run();
        return loan.id;
    }

    EconomyData.Loan quoteLoanRequest(Actor actor, String bankId, long principal, int periods, String collateral,
                                     boolean consentBalance, boolean consentRepossession, boolean autoPay) {
        return prepareLoan(actor, bankId, principal, periods, collateral, consentBalance, consentRepossession, autoPay, false);
    }

    private EconomyData.Loan prepareLoan(Actor actor, String bankId, long principal, int periods, String collateral,
                                        boolean consentBalance, boolean consentRepossession, boolean autoPay, boolean updateValuation) {
        e.ledger.thread();
        EconomyData.Bank bank = bank(bankId, true);
        Money.positive(principal);
        if (periods < 1 || periods > e.config.maximumLoanPeriods) throw new UserError("Invalid number of repayment periods.");
        if (!Objects.equals(e.data.bankAssociations.get(actor.id().toString()), bank.id)) {
            throw new UserError("Associate with this bank before applying for a loan.");
        }
        if (e.data.loans.values().stream().filter(l -> l.bank.equals(bank.id) && OPEN_LOANS.contains(l.status)).count()
                >= e.config.maximumBankLoans) throw new UserError("This bank's loan limit has been reached.");
        long interestCap = Money.tax(principal, e.config.loanLifetimeInterestCapBps);
        Money.add(principal, interestCap);
        long now = e.clock.millis();
        checkBorrowerExposure(actor.id().toString(), principal, null, now);
        if (collateral == null || collateral.equalsIgnoreCase("none")) {
            collateral = null;
            if (!e.config.allowUnsecuredLoans || principal > e.config.maximumUnsecuredLoanCents) {
                throw new UserError("An eligible, privately owned collateral claim is required for this loan.");
            }
            if (consentRepossession) throw new UserError("Repossession consent requires specifically identified collateral.");
        } else {
            collateral = ChunkKey.parse(collateral).toString();
            if (!consentRepossession) throw new UserError("A secured application requires explicit collateral-repossession consent.");
            if (!e.config.allowConsentedRepossession) throw new UserError("Secured repossession is disabled by the server.");
            checkCollateral(actor.id().toString(), collateral, principal, null, updateValuation);
        }
        EconomyData.Loan loan = new EconomyData.Loan();
        loan.id = EconomyEngine.id();
        loan.bank = bank.id;
        loan.borrower = actor.id().toString();
        loan.originalPrincipal = principal;
        loan.principal = principal;
        loan.interestCap = interestCap;
        loan.periods = periods;
        loan.periodMillis = bank.interestPeriodMillis;
        loan.rateBps = bank.loanInterestBps;
        loan.originationFee = Money.tax(principal, bank.originationFeeBps);
        if (loan.originationFee >= principal) throw new UserError("Origination fees must be less than the loan principal.");
        loan.requestedAt = now;
        loan.applicationExpiresAt = EconomyEngine.deadline(loan.requestedAt, e.config.loanApplicationLifetimeMillis);
        loan.graceMillis = e.config.loanGraceMillis;
        loan.defaultMissedPayments = e.config.defaultAfterMissedPayments;
        loan.collateral = collateral;
        loan.consentBalanceSeizure = consentBalance;
        loan.consentRepossession = consentRepossession;
        loan.autoPay = autoPay;
        loan.terms = "Principal " + Money.format(principal) + "; " + loan.rateBps + " bps simple interest per "
                + loan.periodMillis + "ms on outstanding principal; " + periods + " equal-principal periods; lifetime interest cap "
                + Money.format(interestCap) + "; origination fee " + Money.format(loan.originationFee)
                + "; balance seizure consent=" + consentBalance + "; specific collateral=" + collateral
                + "; repossession consent=" + consentRepossession + "; automatic payments=" + autoPay
                + "; default after " + loan.defaultMissedPayments + " missed periods plus " + loan.graceMillis + "ms grace.";
        return loan;
    }

    private void checkBorrowerExposure(String borrower, long principal, String ignoreLoan, long now) {
        BigInteger exposure = borrowerExposure(borrower, ignoreLoan, now).add(BigInteger.valueOf(principal));
        if (exposure.compareTo(BigInteger.valueOf(e.config.maximumBorrowerDebtCents)) > 0) {
            throw new UserError("The borrower debt limit would be exceeded.");
        }
    }

    long borrowingCapacity(Actor actor) {
        try {
            return BigInteger.valueOf(e.config.maximumBorrowerDebtCents).subtract(borrowerExposure(actor.id().toString(), null, e.clock.millis()))
                    .max(BigInteger.ZERO).longValueExact();
        } catch (UserError defaulted) { return 0; }
    }

    private BigInteger borrowerExposure(String borrower, String ignoreLoan, long now) {
        BigInteger exposure = BigInteger.ZERO;
        for (EconomyData.Loan loan : e.data.loans.values()) {
            if (!loan.borrower.equals(borrower) || loan.id.equals(ignoreLoan) || !OPEN_LOANS.contains(loan.status)) continue;
            if (loan.status.equals("DEFAULTED")) throw new UserError("Resolve existing defaults before taking another loan.");
            if (loan.status.equals("REQUESTED") && loan.applicationExpiresAt <= now) continue;
            exposure = exposure.add(BigInteger.valueOf(loan.principal)).add(BigInteger.valueOf(loan.interest))
                    .add(loanInterest(loan, now).earned());
        }
        return exposure;
    }

    private void checkCollateral(String borrower, String key, long principal, String ignoreLoan, boolean updateValuation) {
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElseThrow(() -> new UserError("The collateral is not a claimed chunk."));
        if (!claim.ownerAccount().equals("player:" + borrower) || !e.governance.maySellProperty(UUID.fromString(borrower), key)) {
            throw new UserError("Collateral must be a claim the borrower privately owns and may sell.");
        }
        if (e.data.properties.containsKey(key) || e.data.loans.values().stream().anyMatch(loan ->
                !loan.id.equals(ignoreLoan) && OPEN_LOANS.contains(loan.status) && !loan.collateralReleased && key.equals(loan.collateral))) {
            throw new UserError("This claim is already listed or pledged.");
        }
        long limit = Money.tax(updateValuation ? e.valueOf(key) : e.property.previewValue(key).value, e.config.maximumLoanToValueBps);
        if (principal > limit) throw new UserError("The loan exceeds the collateral's permitted loan-to-value amount: " + Money.format(limit));
    }

    public void approve(Actor actor, String id) {
        LoanFunding funding = prepareApproval(actor, id, true);
        EconomyData.Loan loan = funding.loan();
        EconomyData.Bank bank = bank(loan.bank, true);
        e.ledger.prepare(List.of(funding.transfer()), List.of(), true, funding.reserves()).commit();
        loan.status = "ACTIVE";
        loan.issuedAt = funding.time();
        loan.lastAccruedAt = funding.time();
        event(bank.id, loan.borrower, "LOAN_ISSUED", funding.transfer().cents(), loan.id + "; fee retained " + Money.format(loan.originationFee));
        e.mail(loan.borrower, "Loan approved", loan.id + "\n" + loan.terms + "\nNet disbursement: " + Money.format(funding.transfer().cents()));
        e.dirty.run();
    }

    record LoanFunding(EconomyData.Loan loan, Transfer transfer, Map<String, Long> reserves, long time) {}

    LoanFunding quoteApproval(Actor actor, String id) { return prepareApproval(actor, id, false); }

    private LoanFunding prepareApproval(Actor actor, String id, boolean updateValuation) {
        e.ledger.thread();
        EconomyData.Loan loan = loan(id);
        EconomyData.Bank bank = bank(loan.bank, true);
        requireManager(actor, bank);
        long now = e.clock.millis();
        if (!loan.status.equals("REQUESTED")) throw new UserError("This application is no longer pending.");
        if (loan.applicationExpiresAt <= now) throw new UserError("This loan application has expired.");
        checkBorrowerExposure(loan.borrower, loan.principal, loan.id, now);
        if (loan.collateral != null) checkCollateral(loan.borrower, loan.collateral, loan.principal, loan.id, updateValuation);
        else if (!e.config.allowUnsecuredLoans || loan.principal > e.config.maximumUnsecuredLoanCents) {
            throw new UserError("This unsecured loan is no longer eligible under server policy.");
        }
        long net = loan.originalPrincipal - loan.originationFee;
        EconomyEngine.deadline(now, Math.multiplyExact(loan.periodMillis, (long) loan.periods));
        return new LoanFunding(loan, new Transfer(bank.account(), "player:" + loan.borrower, net, "Loan disbursement " + loan.id),
                Map.of(bank.account(), requiredReserve(bank, now)), now);
    }

    public void reject(Actor actor, String id) {
        EconomyData.Loan loan = loan(id);
        if (!loan.borrower.equals(actor.id().toString())) requireManager(actor, bank(loan.bank, true));
        if (!loan.status.equals("REQUESTED")) throw new UserError("Only a pending application can be cancelled.");
        loan.status = "CANCELLED";
        loan.collateralReleased = true;
        event(loan.bank, loan.borrower, "LOAN_CANCELLED", 0, id);
        e.mail(loan.borrower, "Loan application cancelled", id + ": the collateral hold has been released.");
        e.dirty.run();
        trimClosedLoans();
    }

    public EconomyData.Loan loan(String id) {
        EconomyData.Loan loan = e.data.loans.get(id);
        if (loan == null) throw new UserError("Unknown loan.");
        return loan;
    }

    public void requireLoanViewer(Actor actor, EconomyData.Loan loan) {
        if (!loan.borrower.equals(actor.id().toString())) requireManager(actor, bank(loan.bank, false));
    }

    public LoanSummary loanSummary(Actor actor, String id) {
        EconomyData.Loan loan = loan(id);
        requireLoanViewer(actor, loan);
        if (loan.status.equals("ACTIVE") || loan.status.equals("DEFAULTED")) accrueLoan(loan);
        return summary(loan, loan.interest, e.clock.millis());
    }

    public LoanSummary previewLoan(Actor actor, String id) {
        e.ledger.thread();
        EconomyData.Loan loan = loan(id);
        requireLoanViewer(actor, loan);
        long now = e.clock.millis();
        return summary(loan, Money.add(loan.interest, loanInterest(loan, now).earned().longValueExact()), now);
    }

    private LoanSummary summary(EconomyData.Loan loan, long interest, long now) {
        boolean outstanding = Set.of("ACTIVE", "DEFAULTED").contains(loan.status);
        long next = outstanding ? EconomyEngine.deadline(loan.issuedAt,
                Math.multiplyExact(loan.periodMillis, Math.min(loan.periods, (long) paidPeriods(loan) + 1))) : 0;
        return new LoanSummary(loan.principal, interest, Money.add(loan.principal, interest), due(loan, interest, now),
                loan.status, loan.missedPayments, loan.terms, loan.autoPay, next);
    }

    public void autoPay(Actor actor, String id, boolean enabled) {
        EconomyData.Loan loan = loan(id);
        if (!actor.id().toString().equals(loan.borrower)) throw new UserError("Only the borrower may change automatic-payment consent.");
        if (!Set.of("ACTIVE", "REQUESTED", "DEFAULTED").contains(loan.status)) throw new UserError("This loan is closed.");
        loan.autoPay = enabled;
        event(loan.bank, loan.borrower, "AUTOPAY", 0, enabled ? "enabled" : "disabled");
        e.dirty.run();
    }

    private static Interest loanInterest(EconomyData.Loan loan, long now) {
        if (!(loan.status.equals("ACTIVE") || loan.status.equals("DEFAULTED")) || now <= loan.lastAccruedAt) {
            return new Interest(BigInteger.ZERO, loan.interestRemainder);
        }
        if (loan.interestCap == loan.interestAccrued) return new Interest(BigInteger.ZERO, "0");
        Interest interest = interest(loan.principal, loan.rateBps, loan.periodMillis, loan.lastAccruedAt,
                loan.interestRemainder, now);
        BigInteger remaining = BigInteger.valueOf(loan.interestCap - loan.interestAccrued);
        BigInteger earned = interest.earned().min(remaining);
        return new Interest(earned, earned.equals(remaining) ? "0" : interest.remainder());
    }

    private void accrueLoan(EconomyData.Loan loan) {
        long now = e.clock.millis();
        if (now <= loan.lastAccruedAt || !(loan.status.equals("ACTIVE") || loan.status.equals("DEFAULTED"))) return;
        Interest interest = loanInterest(loan, now);
        long earned = interest.earned().longValueExact();
        long balance = Money.add(loan.interest, earned);
        long accrued = Money.add(loan.interestAccrued, earned);
        boolean changed = earned != 0 || !loan.interestRemainder.equals(interest.remainder());
        loan.interest = balance;
        loan.interestAccrued = accrued;
        loan.interestRemainder = interest.remainder();
        loan.lastAccruedAt = now;
        if (changed) e.dirty.run();
    }

    private long debt(EconomyData.Loan loan) { return Money.add(loan.principal, loan.interest); }

    public void repay(Actor actor, String id, long amount) {
        e.ledger.thread();
        EconomyData.Loan loan = loan(id);
        if (!loan.borrower.equals(actor.id().toString()) && !actor.admin()) throw new UserError("Only the borrower may repay this loan from their wallet.");
        if (!loan.status.equals("ACTIVE") && !loan.status.equals("DEFAULTED")) throw new UserError("This loan is not outstanding.");
        accrueLoan(loan);
        Money.positive(amount);
        if (amount > debt(loan)) throw new UserError("The repayment exceeds the remaining debt.");
        repayment(loan, actor.account(), amount, false, "Loan repayment");
    }

    public void repayAll(Actor actor, String id) {
        e.ledger.thread();
        EconomyData.Loan loan = loan(id);
        if (!loan.borrower.equals(actor.id().toString()) && !actor.admin()) throw new UserError("Only the borrower may repay this loan from their wallet.");
        if (!loan.status.equals("ACTIVE") && !loan.status.equals("DEFAULTED")) throw new UserError("This loan is not outstanding.");
        accrueLoan(loan);
        repayment(loan, actor.account(), debt(loan), false, "Full loan repayment");
    }

    private void repayment(EconomyData.Loan loan, String account, long amount, boolean mandatory, String reason) {
        EconomyData.Bank bank = bank(loan.bank, true);
        e.ledger.prepare(List.of(new Transfer(account, bank.account(), amount, reason + " " + loan.id)),
                List.of(), !mandatory, Map.of()).commit();
        reduceDebt(loan, amount);
        event(bank.id, loan.borrower, "REPAYMENT", amount, reason + " " + loan.id);
        e.dirty.run();
    }

    private void reduceDebt(EconomyData.Loan loan, long amount) {
        long interestPayment = Math.min(amount, loan.interest);
        loan.interest -= interestPayment;
        loan.principal -= amount - interestPayment;
        if (loan.principal == 0 && loan.interest == 0) {
            loan.status = "REPAID";
            loan.collateralReleased = true;
            e.mail(loan.borrower, "Loan settled", loan.id + ": debt paid; any remaining collateral hold has been released.");
            trimClosedLoans();
        }
    }

    private int maturedPeriods(EconomyData.Loan loan) {
        return maturedPeriods(loan, e.clock.millis());
    }

    private int maturedPeriods(EconomyData.Loan loan, long now) {
        if (loan.status.equals("REQUESTED") || loan.issuedAt > now) return 0;
        return (int) Math.min(loan.periods, (now - loan.issuedAt) / loan.periodMillis);
    }

    private int paidPeriods(EconomyData.Loan loan) {
        return BigInteger.valueOf(loan.originalPrincipal - loan.principal).multiply(BigInteger.valueOf(loan.periods))
                .divide(BigInteger.valueOf(loan.originalPrincipal)).intValueExact();
    }

    private long due(EconomyData.Loan loan) {
        return due(loan, loan.interest, e.clock.millis());
    }

    private long due(EconomyData.Loan loan, long interest, long now) {
        if (!loan.status.equals("ACTIVE") && !loan.status.equals("DEFAULTED")) return 0;
        if (loan.status.equals("DEFAULTED")) return Money.add(loan.principal, interest);
        int periods = maturedPeriods(loan, now);
        if (periods == 0) return 0;
        long scheduled = BigInteger.valueOf(loan.originalPrincipal).multiply(BigInteger.valueOf(periods))
                .add(BigInteger.valueOf(loan.periods - 1L)).divide(BigInteger.valueOf(loan.periods)).longValueExact();
        long principalDue = Math.max(0, scheduled - (loan.originalPrincipal - loan.principal));
        return Money.add(principalDue, interest);
    }

    boolean tickLoan(String id) {
        EconomyData.Loan loan = e.data.loans.get(id);
        if (loan == null || !OPEN_LOANS.contains(loan.status)) return false;
        if (loan.status.equals("REQUESTED")) {
            if (loan.applicationExpiresAt <= e.clock.millis()) {
                loan.status = "EXPIRED";
                loan.collateralReleased = true;
                e.mail(loan.borrower, "Loan application expired", id + ": collateral released.");
                e.dirty.run();
                trimClosedLoans();
                return false;
            }
            return true;
        }
        accrueLoan(loan);
        long due = due(loan);
        int matured = maturedPeriods(loan);
        if (loan.autoPay && due > 0) {
            long payment = Math.min(due, e.spendable("player:" + loan.borrower));
            if (payment > 0) {
                try { repayment(loan, "player:" + loan.borrower, payment, false, "Consented scheduled loan payment"); }
                catch (UserError error) { e.notice("Automatic payment deferred for " + id + ": " + error.getMessage()); }
            }
        }
        if (loan.status.equals("REPAID")) return false;
        int missedPayments = Math.max(0, matured - paidPeriods(loan));
        if (loan.missedPayments != missedPayments) {
            loan.missedPayments = missedPayments;
            e.dirty.run();
        }
        if (loan.missedPayments > 0 && matured > loan.reportedDuePeriod) {
            loan.reportedDuePeriod = matured;
            e.mail(loan.borrower, "Loan installment overdue", id + ": " + Money.format(due(loan)) + " currently due.");
            event(loan.bank, loan.borrower, "MISSED_PAYMENT", due(loan), id + "; missed periods=" + loan.missedPayments);
            e.dirty.run();
        }
        long defaultPeriods = (long) paidPeriods(loan) + loan.defaultMissedPayments;
        BigInteger defaultAt = BigInteger.valueOf(loan.issuedAt).add(BigInteger.valueOf(defaultPeriods)
                .multiply(BigInteger.valueOf(loan.periodMillis))).add(BigInteger.valueOf(loan.graceMillis));
        if (!loan.status.equals("DEFAULTED") && loan.missedPayments > 0
                && BigInteger.valueOf(e.clock.millis()).compareTo(defaultAt) >= 0) {
            loan.status = "DEFAULTED";
            event(loan.bank, loan.borrower, "DEFAULTED", debt(loan), id);
            e.mail(loan.borrower, "Loan default", id + ": " + Money.format(debt(loan))
                    + " remains owed. Only recovery consent recorded at origination may be used.");
            EconomyData.Bank bank = bank(loan.bank, true);
            e.governance.company(bank.company).ifPresent(company ->
                    e.mail(company.owner().toString(), "Bank loan default", id + ": borrower " + loan.borrower));
            e.dirty.run();
        }
        if (loan.status.equals("DEFAULTED")) recover(loan);
        return OPEN_LOANS.contains(loan.status);
    }

    public void recover(Actor actor, String id) {
        EconomyData.Loan loan = loan(id);
        requireManager(actor, bank(loan.bank, true));
        if (!loan.status.equals("DEFAULTED")) throw new UserError("Recovery is only available after default.");
        accrueLoan(loan);
        recover(loan);
    }

    private void recover(EconomyData.Loan loan) {
        EconomyData.Bank bank = bank(loan.bank, true);
        if (loan.consentBalanceSeizure && e.config.allowConsentedBalanceSeizure) {
            long amount = Math.min(debt(loan), e.spendable("player:" + loan.borrower));
            amount = Math.min(amount, Money.MAX - e.balance(bank.account()));
            if (amount > 0) {
                repayment(loan, "player:" + loan.borrower, amount, true, "Origination-consented default balance recovery");
                e.mail(loan.borrower, "Loan balance recovery", Money.format(amount) + " applied to " + loan.id + " under your recorded consent.");
            }
        }
        if (loan.status.equals("REPAID") || loan.collateral == null || loan.collateralReleased
                || !loan.consentRepossession || !e.config.allowConsentedRepossession) return;
        GovernanceAccess.ClaimView claim = e.governance.claim(loan.collateral).orElse(null);
        if (claim == null || !claim.ownerAccount().equals("player:" + loan.borrower)) {
            e.notice("Collateral recovery blocked for " + loan.id + ": the specific pledged property is absent or changed owner.");
            return;
        }
        e.governance.company(bank.company).orElseThrow(() -> new UserError("The bank company no longer exists."));
        long value = e.valueOf(loan.collateral);
        if (value == 0) return;
        e.property.settleDueBeforeTransfer(loan.collateral);
        long debt = debt(loan);
        long credit = Math.min(value, debt);
        long surplus = Math.max(0, value - debt);
        Ledger.Plan payment = e.ledger.prepare(List.of(new Transfer(bank.account(), "player:" + loan.borrower, surplus,
                "Collateral equity surplus for " + loan.id)), List.of(), false,
                Map.of(bank.account(), requiredReserve(bank)));
        loan.collateralReleased = true;
        try {
            payment.commitWith(() -> e.governance.transferProperty(loan.collateral, "company:" + bank.company));
        } catch (RuntimeException | Error failure) {
            loan.collateralReleased = false;
            throw failure;
        }
        loan.repossessed = true;
        e.property.transferred(loan.collateral, "company:" + bank.company);
        reduceDebt(loan, credit);
        event(bank.id, loan.borrower, "COLLATERAL_RECOVERY", credit, loan.id + "; chunk=" + loan.collateral
                + "; valuation=" + Money.format(value) + "; equity paid=" + Money.format(surplus));
        e.mail(loan.borrower, "Consented collateral repossession", loan.collateral + " transferred to the bank company. Debt credited "
                + Money.format(credit) + "; surplus equity paid " + Money.format(surplus) + ". No other property may be seized.");
        e.dirty.run();
    }

    boolean encumbers(String chunk) {
        return e.data.loans.values().stream().anyMatch(loan -> !loan.collateralReleased
                && OPEN_LOANS.contains(loan.status) && chunk.equals(loan.collateral));
    }

    boolean accountInUse(String account) {
        for (EconomyData.Bank bank : e.data.banks.values()) {
            if (!bank.closed && (bank.account().equals(account) || ("company:" + bank.company).equals(account))) return true;
            if (account.startsWith("player:")) {
                EconomyData.Deposit deposit = bank.deposits.get(account.substring(7));
                if (deposit != null && (deposit.balance > 0 || new BigInteger(deposit.pendingInterest).signum() > 0)) return true;
            }
        }
        return e.data.loans.values().stream().anyMatch(loan -> OPEN_LOANS.contains(loan.status)
                && (("player:" + loan.borrower).equals(account) || ("bank:" + loan.bank).equals(account)));
    }

    public List<EconomyData.Loan> loans(Actor actor, String bankId) {
        if (bankId != null) {
            EconomyData.Bank bank = bank(bankId, false);
            requireManager(actor, bank);
            return e.data.loans.values().stream().filter(loan -> loan.bank.equals(bank.id)).toList();
        }
        return e.data.loans.values().stream().filter(loan -> loan.borrower.equals(actor.id().toString())).toList();
    }

    private void event(String bank, String player, String kind, long amount, String detail) {
        e.data.bankHistory.add(new EconomyData.BankEvent(e.clock.millis(), bank, player, kind, amount, detail));
        Ledger.trim(e.data.bankHistory, e.config.historyLimit);
        e.dirty.run();
    }

    private void trimClosedLoans() {
        long closed = e.data.loans.values().stream().filter(loan -> !OPEN_LOANS.contains(loan.status)).count();
        var iterator = e.data.loans.entrySet().iterator();
        while (closed > e.config.historyLimit && iterator.hasNext()) {
            if (!OPEN_LOANS.contains(iterator.next().getValue().status)) {
                iterator.remove();
                closed--;
            }
        }
    }
}
