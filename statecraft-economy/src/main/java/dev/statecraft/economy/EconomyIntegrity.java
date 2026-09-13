package dev.statecraft.economy;

import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.Money;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Reject corrupt financial state rather than silently replacing it with empty accounts. */
final class EconomyIntegrity {
    private EconomyIntegrity() {}

    static void validate(EconomyData data) {
        if (data.schemaVersion != 1) fail("Unsupported economy section schema " + data.schemaVersion);
        for (Object field : List.of(data.accounts, data.playerNames, data.selectedAccounts, data.transactions,
                data.taxHistory, data.arrears, data.market, data.deliveries, data.hubQuotas, data.suggestions,
                data.properties, data.valuations, data.stocks, data.companyFinance, data.banks, data.bankAssociations,
                data.loans, data.bankHistory, data.notices)) Objects.requireNonNull(field, "Null economy collection");
        data.accounts.forEach((id, account) -> {
            Ledger.account(id);
            Money.nonNegative(account.balance);
            Money.nonNegative(account.dailyLimit);
            Money.nonNegative(account.spent);
        });
        data.playerNames.forEach((id, name) -> { uuid(id); Objects.requireNonNull(name); });
        data.selectedAccounts.forEach((id, account) -> { uuid(id); Ledger.account(account); });
        data.transactions.forEach(t -> {
            Ledger.account(t.from()); Ledger.account(t.to()); Money.nonNegative(t.cents());
            Objects.requireNonNull(t.id()); Objects.requireNonNull(t.reason());
        });
        data.taxHistory.forEach(t -> { Ledger.account(t.payer()); Money.nonNegative(t.cents()); });
        data.arrears.forEach((id, debt) -> {
            same(id, debt.id);
            Ledger.account(debt.payer); Ledger.account(debt.recipient);
            if (big(debt.cents).signum() == 0) fail("Zero-valued arrear " + id);
            Objects.requireNonNull(debt.kind); Objects.requireNonNull(debt.subject);
        });
        data.market.forEach((id, listing) -> {
            same(id, listing.id); uuid(listing.seller);
            same("player:" + listing.seller, listing.sellerAccount);
            lot(listing.item);
            if (listing.remaining < 1 || listing.remaining > listing.item.count()) fail("Invalid escrow quantity " + id);
            Money.positive(listing.unitPrice); Money.multiply(listing.unitPrice, listing.remaining);
            ChunkKey.parse(listing.sourceChunk); time(listing.createdAt, listing.expiresAt);
        });
        data.deliveries.forEach((id, deliveries) -> {
            uuid(id);
            for (EconomyData.Delivery delivery : deliveries) { lot(delivery.item); uuid(delivery.id); Objects.requireNonNull(delivery.reason); }
        });
        data.hubQuotas.forEach((id, quota) -> {
            uuid(id); Money.nonNegative(quota.gross);
            if (quota.items < 0) fail("Negative hub quantity");
        });
        data.suggestions.forEach(s -> { uuid(s.player()); Money.positive(s.proposedCents()); Objects.requireNonNull(s.item()); });
        data.properties.forEach((id, listing) -> {
            same(id, listing.chunk); ChunkKey.parse(id); Ledger.account(listing.ownerAccount);
            Money.positive(listing.price); time(listing.createdAt, listing.expiresAt);
        });
        data.valuations.forEach((id, value) -> {
            same(id, value.chunk); ChunkKey.parse(id); Money.nonNegative(value.value); Money.nonNegative(value.base);
            if (value.taxOwnerAccount != null) Ledger.account(value.taxOwnerAccount);
            if (value.nextTaxAt < 0 || value.nextRecalculationAt < 0) fail("Invalid property schedule");
        });
        data.stocks.forEach((id, listing) -> {
            same(id, listing.id); Ledger.account("company:" + listing.company); uuid(listing.seller);
            if (!id.startsWith("stock:") || listing.remaining < 1) fail("Invalid stock escrow " + id);
            Money.positive(listing.unitPrice); Money.multiply(listing.unitPrice, listing.remaining);
            ChunkKey.parse(listing.sourceChunk); time(listing.createdAt, listing.expiresAt);
        });
        data.companyFinance.forEach((id, company) -> {
            same(id, company.company); Ledger.account("company:" + id);
            if (company.nextFeeAt < 0) fail("Invalid company fee schedule");
        });
        data.banks.forEach((id, bank) -> {
            same(id, bank.id); same(id, bank.company); Ledger.account(bank.account());
            Objects.requireNonNull(bank.name); Objects.requireNonNull(bank.branding);
            Money.tax(0, bank.depositInterestBps); Money.tax(0, bank.loanInterestBps); Money.tax(0, bank.originationFeeBps);
            Money.nonNegative(bank.depositFeeCents); Money.nonNegative(bank.withdrawalFeeCents);
            period(bank.interestPeriodMillis);
            long total = 0;
            for (Map.Entry<String, EconomyData.Deposit> entry : bank.deposits.entrySet()) {
                uuid(entry.getKey());
                EconomyData.Deposit deposit = entry.getValue();
                Money.nonNegative(deposit.balance); Money.nonNegative(deposit.principal); big(deposit.pendingInterest);
                BigInteger remainder = big(deposit.interestRemainder);
                period(deposit.periodMillis); Money.tax(0, deposit.rateBps);
                if (deposit.principal > deposit.balance || deposit.lastAccruedAt < 0
                        || remainder.compareTo(BigInteger.valueOf(deposit.periodMillis).multiply(BigInteger.valueOf(10_000))) >= 0) {
                    fail("Invalid bank deposit " + id + " / " + entry.getKey());
                }
                if (bank.closed && (deposit.balance > 0 || big(deposit.pendingInterest).signum() > 0)) fail("Closed bank has liabilities");
                total = Money.add(total, deposit.balance);
            }
        });
        data.bankAssociations.forEach((player, bank) -> {
            uuid(player);
            if (!data.banks.containsKey(bank) || data.banks.get(bank).closed) fail("Invalid bank association");
        });
        data.loans.forEach((id, loan) -> {
            same(id, loan.id); uuid(id); uuid(loan.borrower);
            if (!Set.of("REQUESTED", "ACTIVE", "DEFAULTED", "REPAID", "CANCELLED", "EXPIRED").contains(loan.status)
                    || !data.banks.containsKey(loan.bank)) fail("Invalid loan status or bank");
            for (long amount : new long[]{loan.originalPrincipal, loan.principal, loan.interest,
                    loan.interestAccrued, loan.interestCap, loan.originationFee}) Money.nonNegative(amount);
            Money.positive(loan.originalPrincipal); Money.add(loan.originalPrincipal, loan.interestCap);
            period(loan.periodMillis); Money.tax(0, loan.rateBps); big(loan.interestRemainder);
            if (loan.principal > loan.originalPrincipal || loan.interest > loan.interestAccrued
                    || loan.interestAccrued > loan.interestCap || loan.originationFee >= loan.originalPrincipal
                    || loan.periods < 1 || loan.periods > 3650 || loan.defaultMissedPayments < 1
                    || loan.graceMillis < 0 || loan.lastAccruedAt < 0) fail("Invalid loan balances or terms " + id);
            if (loan.collateral != null) {
                ChunkKey.parse(loan.collateral);
                if (!loan.consentRepossession) fail("Collateral recorded without origination consent");
            }
            if (loan.status.equals("REPAID") && (loan.principal != 0 || loan.interest != 0)) fail("Repaid loan has outstanding debt");
            if (Set.of("ACTIVE", "DEFAULTED", "REQUESTED").contains(loan.status) && data.banks.get(loan.bank).closed) fail("Open loan at a closed bank");
            Objects.requireNonNull(loan.terms);
        });
    }

    private static void lot(ItemLot item) {
        Objects.requireNonNull(item);
        new ItemLot(item.item(), item.snbt(), item.count(), item.maxStackSize(), item.fullStackData());
        if (item.empty()) fail("Empty escrow item");
    }
    private static BigInteger big(String value) {
        if (value == null || !value.matches("[0-9]+")) throw new IllegalStateException("Invalid financial integer");
        return new BigInteger(value);
    }
    private static void uuid(String value) { if (!UUID.fromString(value).toString().equals(value)) fail("Invalid UUID"); }
    private static void same(String first, String second) { if (!Objects.equals(first, second)) fail("Mismatched persistent ID"); }
    private static void time(long created, long expires) { if (created < 0 || expires <= created) fail("Invalid financial expiration date"); }
    private static void period(long value) { if (value < 1000 || value > 315_576_000_000L) fail("Invalid persisted interest period"); }
    private static void fail(String message) { throw new IllegalStateException("Economy snapshot: " + message); }
}
