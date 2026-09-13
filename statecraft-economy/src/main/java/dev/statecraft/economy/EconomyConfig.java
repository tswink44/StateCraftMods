package dev.statecraft.economy;

import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

/** Public fields are bound to Forge's server configuration by the shared runtime. */
public final class EconomyConfig {
    public long initialPlayerBalanceCents = 0;
    public int historyLimit = 1000;
    public int taxHistoryLimit = 1000;
    public int workPerTick = 16;
    public long discoveryIntervalMillis = 60_000;
    public int catchUpPeriodsPerTick = 4;
    public int utilityRadius = 8;
    public long marketListingFeeCents = 0;
    public int marketCommissionBps = 100;
    public long marketLifetimeMillis = 604_800_000;
    public int maximumListingsPerPlayer = 32;
    public int maximumActiveListings = 10_000;
    public int maximumDeliveryEntries = 256;
    public long hubDailyLimitCents = 1_000_000;
    public int hubDailyItemLimit = 4096;
    public int maximumSuggestions = 200;
    public long stockListingFeeCents = 0;
    public int stockCommissionBps = 0;
    public long stockLifetimeMillis = 604_800_000;
    public long propertyListingLifetimeMillis = 604_800_000;
    public long defaultChunkValueCents = 1_000_000;
    public int locationBonusBps = 5000;
    public int demandBonusPerChunkBps = 250;
    public int improvementBonusBps = 500;
    public long valuationIntervalMillis = 3_600_000;
    public long propertyTaxPeriodMillis = 86_400_000;
    public boolean mandatoryPropertyTaxes = false;
    public long companyTransferFeeCents = 0;
    public long companyPeriodicFeeCents = 0;
    public long companyFeePeriodMillis = 604_800_000;
    public long bankCreationFeeCents = 10_000;
    public long minimumBankReserveCents = 10_000;
    public int minimumBankReserveBps = 2000;
    public int maximumBankCustomers = 1000;
    public int maximumBankLoans = 1000;
    public int maximumDepositInterestBps = 200;
    public int maximumLoanInterestBps = 1000;
    public int maximumOriginationFeeBps = 1000;
    public long financialPeriodMillis = 86_400_000;
    public boolean allowUnsecuredLoans = false;
    public long maximumUnsecuredLoanCents = 100_000;
    public long maximumBorrowerDebtCents = 100_000_000;
    public int maximumLoanToValueBps = 7000;
    public int maximumLoanPeriods = 365;
    public int loanLifetimeInterestCapBps = 10_000;
    public int defaultAfterMissedPayments = 3;
    public long loanGraceMillis = 86_400_000;
    public long loanApplicationLifetimeMillis = 604_800_000;
    public boolean allowConsentedBalanceSeizure = true;
    public boolean allowConsentedRepossession = true;

    public void validate() {
        for (long value : new long[]{initialPlayerBalanceCents, marketListingFeeCents, hubDailyLimitCents,
                stockListingFeeCents, defaultChunkValueCents, companyTransferFeeCents, companyPeriodicFeeCents,
                bankCreationFeeCents, minimumBankReserveCents, maximumUnsecuredLoanCents,
                maximumBorrowerDebtCents}) {
            Money.nonNegative(value);
        }
        for (int value : new int[]{marketCommissionBps, stockCommissionBps, locationBonusBps,
                demandBonusPerChunkBps, improvementBonusBps, minimumBankReserveBps, maximumDepositInterestBps,
                maximumLoanInterestBps, maximumOriginationFeeBps, maximumLoanToValueBps,
                loanLifetimeInterestCapBps}) {
            Money.tax(0, value);
        }
        for (long value : new long[]{discoveryIntervalMillis, marketLifetimeMillis, stockLifetimeMillis,
                propertyListingLifetimeMillis, valuationIntervalMillis, propertyTaxPeriodMillis,
                companyFeePeriodMillis, financialPeriodMillis, loanApplicationLifetimeMillis}) {
            if (value < 1000 || value > 315_576_000_000L) {
                throw new UserError("Economy periods must be between one second and ten years.");
            }
        }
        if (loanGraceMillis < 0 || loanGraceMillis > 315_576_000_000L
                || historyLimit < 10 || historyLimit > 100_000 || taxHistoryLimit < 10
                || taxHistoryLimit > 100_000 || workPerTick < 1 || workPerTick > 256
                || catchUpPeriodsPerTick < 1 || catchUpPeriodsPerTick > 32
                || utilityRadius < 1 || utilityRadius > 16
                || maximumListingsPerPlayer < 1 || maximumListingsPerPlayer > 1000
                || maximumActiveListings < 1 || maximumActiveListings > 100_000
                || maximumDeliveryEntries < maximumListingsPerPlayer || maximumDeliveryEntries > 10_000
                || hubDailyItemLimit < 1 || maximumSuggestions < 1 || maximumSuggestions > 10_000
                || maximumBankCustomers < 1 || maximumBankCustomers > 10_000
                || maximumBankLoans < 1 || maximumBankLoans > 10_000
                || maximumLoanPeriods < 1 || maximumLoanPeriods > 3650
                || defaultAfterMissedPayments < 1 || defaultAfterMissedPayments > 3650) {
            throw new UserError("An economy limit is outside its supported range.");
        }
    }
}
