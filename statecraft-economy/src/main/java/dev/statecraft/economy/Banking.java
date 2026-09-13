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
                              int missedPayments, String terms) {}
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

    private void requireManager(Actor actor, EconomyData.Bank bank) {
        e.requireAccount(actor, "company:" + bank.company);
    }

    public String create(Actor actor, String companyId, String name, long capital,
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
        e.ledger.prepare(List.of(new Transfer(company.account(), bank.account(), capital, "Bank seed capital"),
                new Transfer(company.account(), "system:fees", e.config.bankCreationFeeCents, "Bank creation fee"))).commit();
        e.data.banks.put(bank.id, bank);
        event(bank.id, actor.id().toString(), "OPENED", capital, name);
        e.dirty.run();
        return bank.id;
    }

    private static void branding(String text) {
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

    private void checkRates(int deposit, int loan, int fee) {
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
        if (deposit != null) accrueDeposit(bank, player, deposit);
        long net = amount - bank.depositFeeCents;
        long newBalance = Money.add(deposit == null ? 0 : deposit.balance, net);
        Money.add(depositBalances(bank), net);
        long newPrincipal = Money.add(deposit == null ? 0 : deposit.principal, net);
        e.ledger.prepare(List.of(new Transfer(actor.account(), bank.account(), amount, "Bank deposit at " + bank.id))).commit();
        if (deposit == null) {
            deposit = new EconomyData.Deposit();
            deposit.lastAccruedAt = e.clock.millis();
            deposit.periodMillis = bank.interestPeriodMillis;
            deposit.rateBps = bank.depositInterestBps;
            bank.deposits.put(player, deposit);
        } else if (deposit.principal == 0) {
            deposit.periodMillis = bank.interestPeriodMillis;
            deposit.rateBps = bank.depositInterestBps;
            deposit.lastAccruedAt = e.clock.millis();
        }
        deposit.balance = newBalance;
        deposit.principal = newPrincipal;
        e.data.bankAssociations.put(player, bank.id);
        e.schedule("deposit", bank.id + "|" + player);
        event(bank.id, player, "DEPOSIT", net, "Fee " + Money.format(bank.depositFeeCents));
        e.dirty.run();
        return net;
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

        WalletFunding(EconomyData.Bank bank, EconomyData.Deposit deposit, String player, long amount, long debit) {
            this.bank = bank;
            this.deposit = deposit;
            this.player = player;
            this.amount = amount;
            this.debit = debit;
            long earned = deposit.balance - deposit.principal;
            newPrincipal = deposit.principal - Math.max(0, debit - earned);
            floor = reserveFor(liabilities(bank).subtract(BigInteger.valueOf(debit)));
        }
        Transfer transfer() { return new Transfer(bank.account(), "player:" + player, amount, "Bank withdrawal at " + bank.id); }
        Map<String, Long> reserveOverrides() { return Map.of(bank.account(), floor); }
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
        accrueDeposit(bank, actor.id().toString(), deposit);
        fundInterest(bank, actor.id().toString(), deposit);
        long debit = Money.add(amount, bank.withdrawalFeeCents);
        if (deposit.balance < debit) throw new UserError("Your bank deposit does not cover the withdrawal plus fee.");
        return new WalletFunding(bank, deposit, actor.id().toString(), amount, debit);
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
        BigInteger total = BigInteger.ZERO;
        for (EconomyData.Deposit deposit : bank.deposits.values()) {
            total = total.add(BigInteger.valueOf(deposit.balance)).add(new BigInteger(deposit.pendingInterest));
        }
        return total;
    }

    private long reserveFor(BigInteger liabilities) {
        BigInteger reserve = liabilities.multiply(BigInteger.valueOf(e.config.minimumBankReserveBps))
                .add(TEN_THOUSAND.subtract(BigInteger.ONE)).divide(TEN_THOUSAND)
                .max(BigInteger.valueOf(e.config.minimumBankReserveCents));
        return reserve.min(BigInteger.valueOf(Money.MAX)).longValueExact();
    }

    public long requiredReserve(EconomyData.Bank bank) { return bank.closed ? 0 : reserveFor(liabilities(bank)); }

    public long protectedBalance(EconomyData.Bank bank) {
        if (bank.closed) return 0;
        return Math.max(requiredReserve(bank), liabilities(bank).min(BigInteger.valueOf(Money.MAX)).longValueExact());
    }

    void guardReserves(Map<String, Long> balances, Map<String, Long> overrides) {
        for (Map.Entry<String, Long> account : balances.entrySet()) {
            if (!account.getKey().startsWith("bank:")) continue;
            EconomyData.Bank bank = e.data.banks.get(account.getKey().substring(5));
            if (bank == null || bank.closed) continue;
            long floor = overrides.getOrDefault(account.getKey(), protectedBalance(bank));
            Money.nonNegative(floor);
            if (account.getValue() < e.balance(account.getKey()) && account.getValue() < floor) {
                throw new UserError("Bank reserves or customer liabilities protect these funds. Use the banking deposit/loan commands.");
            }
        }
    }

    private void accrueDeposit(EconomyData.Bank bank, String player, EconomyData.Deposit deposit) {
        long now = e.clock.millis();
        if (now <= deposit.lastAccruedAt) return;
        BigInteger denominator = TEN_THOUSAND.multiply(BigInteger.valueOf(deposit.periodMillis));
        BigInteger duration = BigInteger.valueOf(now).subtract(BigInteger.valueOf(deposit.lastAccruedAt));
        BigInteger numerator = BigInteger.valueOf(deposit.principal).multiply(BigInteger.valueOf(deposit.rateBps))
                .multiply(duration).add(new BigInteger(deposit.interestRemainder));
        BigInteger[] amount = numerator.divideAndRemainder(denominator);
        deposit.pendingInterest = new BigInteger(deposit.pendingInterest).add(amount[0]).toString();
        deposit.interestRemainder = amount[1].toString();
        deposit.lastAccruedAt = now;
        e.dirty.run();
    }

    private long fundInterest(EconomyData.Bank bank, String player, EconomyData.Deposit deposit) {
        BigInteger pending = new BigInteger(deposit.pendingInterest);
        if (pending.signum() == 0) return 0;
        long balances = depositBalances(bank);
        long equity = Math.max(0, e.balance(bank.account()) - balances);
        long available = Math.max(0, equity - e.config.minimumBankReserveCents);
        available = Math.min(available, Money.MAX - balances);
        available = Math.min(available, Money.MAX - deposit.balance);
        long funded = pending.min(BigInteger.valueOf(available)).longValueExact();
        if (funded == 0) return 0;
        deposit.balance = Money.add(deposit.balance, funded);
        deposit.pendingInterest = pending.subtract(BigInteger.valueOf(funded)).toString();
        event(bank.id, player, "FUNDED_INTEREST", funded, "Allocated from bank equity; not newly minted currency.");
        e.dirty.run();
        return funded;
    }

    boolean tickDeposit(String reference) {
        String[] parts = reference.split("\\|", 2);
        EconomyData.Bank bank = e.data.banks.get(parts[0]);
        if (bank == null || bank.closed) return false;
        EconomyData.Deposit deposit = bank.deposits.get(parts[1]);
        if (deposit == null) return false;
        accrueDeposit(bank, parts[1], deposit);
        fundInterest(bank, parts[1], deposit);
        return true;
    }

    public String requestLoan(Actor actor, String bankId, long principal, int periods, String collateral,
                              boolean consentBalance, boolean consentRepossession, boolean autoPay) {
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
        long exposure = 0;
        for (EconomyData.Loan loan : e.data.loans.values()) {
            if (!loan.borrower.equals(actor.id().toString()) || !OPEN_LOANS.contains(loan.status)) continue;
            if (loan.status.equals("DEFAULTED")) throw new UserError("Resolve existing defaults before taking another loan.");
            exposure = Money.add(exposure, Money.add(loan.principal, loan.interest));
        }
        if (Money.add(exposure, principal) > e.config.maximumBorrowerDebtCents) throw new UserError("The borrower debt limit would be exceeded.");
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
            checkCollateral(actor.id().toString(), collateral, principal, null);
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
        loan.requestedAt = e.clock.millis();
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
        e.data.loans.put(loan.id, loan);
        e.schedule("loan", loan.id);
        event(bank.id, loan.borrower, "LOAN_REQUEST", principal, loan.id + ": " + loan.terms);
        e.mail(loan.borrower, "Loan application recorded", loan.id + "\n" + loan.terms);
        e.governance.company(bank.company).ifPresent(company ->
                e.mail(company.owner().toString(), "Loan approval requested", loan.borrower + " requests " + Money.format(principal) + "; ID " + loan.id));
        e.dirty.run();
        return loan.id;
    }

    private void checkCollateral(String borrower, String key, long principal, String ignoreLoan) {
        GovernanceAccess.ClaimView claim = e.governance.claim(key).orElseThrow(() -> new UserError("The collateral is not a claimed chunk."));
        if (!claim.ownerAccount().equals("player:" + borrower) || !e.governance.maySellProperty(UUID.fromString(borrower), key)) {
            throw new UserError("Collateral must be a claim the borrower privately owns and may sell.");
        }
        if (e.data.properties.containsKey(key) || e.data.loans.values().stream().anyMatch(loan ->
                !loan.id.equals(ignoreLoan) && OPEN_LOANS.contains(loan.status) && !loan.collateralReleased && key.equals(loan.collateral))) {
            throw new UserError("This claim is already listed or pledged.");
        }
        long limit = Money.tax(e.valueOf(key), e.config.maximumLoanToValueBps);
        if (principal > limit) throw new UserError("The loan exceeds the collateral's permitted loan-to-value amount: " + Money.format(limit));
    }

    public void approve(Actor actor, String id) {
        e.ledger.thread();
        EconomyData.Loan loan = loan(id);
        EconomyData.Bank bank = bank(loan.bank, true);
        requireManager(actor, bank);
        if (!loan.status.equals("REQUESTED")) throw new UserError("This application is no longer pending.");
        if (loan.applicationExpiresAt <= e.clock.millis()) throw new UserError("This loan application has expired.");
        if (loan.collateral != null) checkCollateral(loan.borrower, loan.collateral, loan.principal, loan.id);
        else if (!e.config.allowUnsecuredLoans || loan.principal > e.config.maximumUnsecuredLoanCents) {
            throw new UserError("This unsecured loan is no longer eligible under server policy.");
        }
        if (e.data.loans.values().stream().anyMatch(other -> other.borrower.equals(loan.borrower) && other.status.equals("DEFAULTED"))) {
            throw new UserError("The borrower has defaulted on another loan.");
        }
        long net = loan.originalPrincipal - loan.originationFee;
        long now = e.clock.millis();
        EconomyEngine.deadline(now, Math.multiplyExact(loan.periodMillis, (long) loan.periods));
        e.ledger.prepare(List.of(new Transfer(bank.account(), "player:" + loan.borrower, net, "Loan disbursement " + loan.id)),
                List.of(), true, Map.of(bank.account(), requiredReserve(bank))).commit();
        loan.status = "ACTIVE";
        loan.issuedAt = now;
        loan.lastAccruedAt = now;
        event(bank.id, loan.borrower, "LOAN_ISSUED", net, loan.id + "; fee retained " + Money.format(loan.originationFee));
        e.mail(loan.borrower, "Loan approved", loan.id + "\n" + loan.terms + "\nNet disbursement: " + Money.format(net));
        e.dirty.run();
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
        return new LoanSummary(loan.principal, loan.interest, debt(loan), due(loan), loan.status, loan.missedPayments, loan.terms);
    }

    public void autoPay(Actor actor, String id, boolean enabled) {
        EconomyData.Loan loan = loan(id);
        if (!actor.id().toString().equals(loan.borrower)) throw new UserError("Only the borrower may change automatic-payment consent.");
        if (!Set.of("ACTIVE", "REQUESTED", "DEFAULTED").contains(loan.status)) throw new UserError("This loan is closed.");
        loan.autoPay = enabled;
        event(loan.bank, loan.borrower, "AUTOPAY", 0, enabled ? "enabled" : "disabled");
        e.dirty.run();
    }

    private void accrueLoan(EconomyData.Loan loan) {
        long now = e.clock.millis();
        if (now <= loan.lastAccruedAt || !(loan.status.equals("ACTIVE") || loan.status.equals("DEFAULTED"))) return;
        BigInteger denominator = TEN_THOUSAND.multiply(BigInteger.valueOf(loan.periodMillis));
        BigInteger duration = BigInteger.valueOf(now).subtract(BigInteger.valueOf(loan.lastAccruedAt));
        BigInteger numerator = BigInteger.valueOf(loan.principal).multiply(BigInteger.valueOf(loan.rateBps))
                .multiply(duration).add(new BigInteger(loan.interestRemainder));
        BigInteger[] amount = numerator.divideAndRemainder(denominator);
        long earned = amount[0].min(BigInteger.valueOf(loan.interestCap - loan.interestAccrued)).longValueExact();
        loan.interest = Money.add(loan.interest, earned);
        loan.interestAccrued = Money.add(loan.interestAccrued, earned);
        loan.interestRemainder = loan.interestAccrued == loan.interestCap ? "0" : amount[1].toString();
        loan.lastAccruedAt = now;
        e.dirty.run();
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
        if (loan.status.equals("REQUESTED") || loan.issuedAt > e.clock.millis()) return 0;
        return (int) Math.min(loan.periods, (e.clock.millis() - loan.issuedAt) / loan.periodMillis);
    }

    private int paidPeriods(EconomyData.Loan loan) {
        return BigInteger.valueOf(loan.originalPrincipal - loan.principal).multiply(BigInteger.valueOf(loan.periods))
                .divide(BigInteger.valueOf(loan.originalPrincipal)).intValueExact();
    }

    private long due(EconomyData.Loan loan) {
        if (!loan.status.equals("ACTIVE") && !loan.status.equals("DEFAULTED")) return 0;
        if (loan.status.equals("DEFAULTED")) return debt(loan);
        int periods = maturedPeriods(loan);
        if (periods == 0) return 0;
        long scheduled = BigInteger.valueOf(loan.originalPrincipal).multiply(BigInteger.valueOf(periods))
                .add(BigInteger.valueOf(loan.periods - 1L)).divide(BigInteger.valueOf(loan.periods)).longValueExact();
        long principalDue = Math.max(0, scheduled - (loan.originalPrincipal - loan.principal));
        return Money.add(principalDue, loan.interest);
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
        loan.missedPayments = Math.max(0, matured - paidPeriods(loan));
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
