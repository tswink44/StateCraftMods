package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess.Transfer;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

public final class Taxation {
    public record Charge(String government, String account, String kind, long cents) {}
    public record TradeTaxes(List<Charge> buyer, List<Charge> seller) {
        public long buyerTotal() { return total(buyer); }
        public long sellerTotal() { return total(seller); }
    }

    private final EconomyData data;
    private final GovernanceAccess governance;
    private final Ledger ledger;
    private final Supplier<EconomyConfig> config;
    private final Clock clock;
    private final Runnable dirty;
    private final ToLongFunction<String> spendable;
    private final Consumer<String> schedulePropertyArrear;

    public Taxation(EconomyData data, GovernanceAccess governance, Ledger ledger,
                    Supplier<EconomyConfig> config, Clock clock, Runnable dirty, ToLongFunction<String> spendable) {
        this(data, governance, ledger, config, clock, dirty, spendable, ignored -> {});
    }

    Taxation(EconomyData data, GovernanceAccess governance, Ledger ledger,
             Supplier<EconomyConfig> config, Clock clock, Runnable dirty, ToLongFunction<String> spendable,
             Consumer<String> schedulePropertyArrear) {
        this.data = data;
        this.governance = governance;
        this.ledger = ledger;
        this.config = config;
        this.clock = clock;
        this.dirty = dirty;
        this.spendable = spendable;
        this.schedulePropertyArrear = Objects.requireNonNull(schedulePropertyArrear);
    }

    public List<GovernanceAccess.GovernmentView> tiers(String chunk, String fallbackNation) {
        Map<String, GovernanceAccess.GovernmentView> result = new LinkedHashMap<>();
        governance.claim(chunk).ifPresent(claim -> {
            addGovernment(result, claim.nationId());
            addGovernment(result, claim.stateId());
            addGovernment(result, claim.cityId());
        });
        if (result.isEmpty()) addGovernment(result, fallbackNation);
        return List.copyOf(result.values());
    }

    private void addGovernment(Map<String, GovernanceAccess.GovernmentView> target, String id) {
        if (id != null) governance.government(id).ifPresent(g -> target.put(g.id(), g));
    }

    public static int rate(GovernanceAccess.GovernmentView government, String setting) {
        String raw = government.settings().get(setting);
        if (raw == null) return 0;
        try {
            int value = Integer.parseInt(raw);
            Money.tax(0, value);
            return value;
        } catch (IllegalArgumentException | UserError e) {
            throw new UserError("Government " + government.name() + " has an invalid " + setting + "; an official must correct it.");
        }
    }

    public List<Charge> quote(String chunk, String fallbackNation, long gross, String setting) {
        Money.nonNegative(gross);
        List<Charge> charges = new ArrayList<>();
        for (GovernanceAccess.GovernmentView government : tiers(chunk, fallbackNation)) {
            long amount = Money.tax(gross, rate(government, setting));
            if (amount != 0) charges.add(new Charge(government.id(), government.account(), setting, amount));
        }
        return List.copyOf(charges);
    }

    public TradeTaxes trade(Actor buyer, String sellerAccount, String sellerChunk, String sellerNation, long gross) {
        String buyerNation = governance.nationOf(buyer.id()).orElse(null);
        List<Charge> buyerTaxes = new ArrayList<>(quote(buyer.chunkKey(), buyerNation, gross, "salesTaxBps"));
        if (buyerNation != null && !Objects.equals(buyerNation, sellerNation)) {
            governance.government(buyerNation).ifPresent(government -> {
                long tariff = Money.tax(gross, rate(government, "tariffBps"));
                if (tariff > 0) buyerTaxes.add(new Charge(government.id(), government.account(), "tariffBps", tariff));
            });
        }
        List<Charge> sellerTaxes = sellerAccount.startsWith("player:")
                ? quote(sellerChunk, sellerNation, gross, "incomeTaxBps")
                : sellerAccount.startsWith("company:")
                    ? quote(sellerChunk, sellerNation, gross, "corporateTaxBps") : List.of();
        return new TradeTaxes(List.copyOf(buyerTaxes), sellerTaxes);
    }

    public List<Charge> hub(Actor seller, long gross) {
        String nation = governance.nationOf(seller.id()).orElse(null);
        List<Charge> result = new ArrayList<>(quote(seller.chunkKey(), nation, gross, "salesTaxBps"));
        result.addAll(quote(seller.chunkKey(), nation, gross, "incomeTaxBps"));
        return List.copyOf(result);
    }

    public static long total(List<Charge> charges) {
        long sum = 0;
        for (Charge charge : charges) sum = Money.add(sum, charge.cents());
        return sum;
    }

    public static List<Transfer> transfers(String source, List<Charge> charges, String reason) {
        return charges.stream().map(c -> new Transfer(source, c.account(), c.cents(), reason + ": " + c.kind())).toList();
    }

    public void record(String payer, List<Charge> charges, String subject) {
        ledger.thread();
        int previousSize = data.taxHistory.size();
        for (Charge charge : charges) {
            data.taxHistory.add(new EconomyData.TaxEntry(clock.millis(), payer, charge.government(),
                    charge.kind(), charge.cents(), subject, "PAID"));
        }
        Ledger.trim(data.taxHistory, config.get().taxHistoryLimit);
        if (!charges.isEmpty() || data.taxHistory.size() != previousSize) dirty.run();
    }

    public void assess(String payer, Charge charge, String subject) {
        assess(payer, charge.account(), charge.government(), charge.kind(), charge.cents(), subject);
    }

    public void assess(String payer, String recipient, String government, String kind, long cents, String subject) {
        Money.nonNegative(cents);
        assessAmount(payer, recipient, government, kind, BigInteger.valueOf(cents), subject, subject);
    }

    public void assessPeriods(String payer, Charge charge, String subject, long periods) {
        if (periods < 1) throw new IllegalArgumentException("Assessment period count must be positive.");
        assessAmount(payer, charge.account(), charge.government(), charge.kind(),
                BigInteger.valueOf(charge.cents()).multiply(BigInteger.valueOf(periods)), subject,
                subject + " [" + periods + " overdue periods]");
    }

    private void assessAmount(String payer, String recipient, String government, String kind,
                              BigInteger cents, String subject, String reportSubject) {
        ledger.thread();
        if (cents.signum() == 0 || payer.equals(recipient)) return;
        String id = payer + "|" + recipient + "|" + kind + "|" + subject;
        EconomyData.Arrear debt = data.arrears.computeIfAbsent(id, ignored -> {
            EconomyData.Arrear value = new EconomyData.Arrear();
            value.id = id;
            value.payer = payer;
            value.recipient = recipient;
            value.government = government;
            value.kind = kind;
            value.subject = subject;
            value.firstDue = clock.millis();
            return value;
        });
        debt.cents = new BigInteger(debt.cents).add(cents).toString();
        long reportAmount = cents.min(BigInteger.valueOf(Money.MAX)).longValueExact();
        if (cents.compareTo(BigInteger.valueOf(Money.MAX)) > 0) reportSubject += " [full integer-cent assessment: " + cents + "]";
        data.taxHistory.add(new EconomyData.TaxEntry(clock.millis(), payer, government, kind, reportAmount, reportSubject, "ASSESSED"));
        Ledger.trim(data.taxHistory, config.get().taxHistoryLimit);
        dirty.run();
        if (isPropertyArrear(debt)) schedulePropertyArrear.accept(id);
    }

    public long pay(String payer, long maximum, boolean mandatory) {
        return pay(payer, maximum, mandatory, data.arrears.values());
    }

    static boolean isPropertyArrear(EconomyData.Arrear debt) {
        return "propertyTaxBps".equals(debt.kind);
    }

    boolean tickPropertyArrear(String id) {
        EconomyData.Arrear debt = data.arrears.get(id);
        if (debt == null || !isPropertyArrear(debt)) return false;
        if (config.get().mandatoryPropertyTaxes) pay(debt.payer, Money.MAX, true, List.of(debt));
        return data.arrears.containsKey(id);
    }

    private long pay(String payer, long maximum, boolean mandatory, Iterable<EconomyData.Arrear> debts) {
        PaymentQuote quote = quotePayment(payer, maximum, debts);
        if (quote.payments().isEmpty()) return 0;
        ledger.prepare(quote.transfers(), List.of(), !mandatory, Map.of()).commit();
        for (Map.Entry<EconomyData.Arrear, Long> payment : quote.payments().entrySet()) {
            EconomyData.Arrear debt = payment.getKey();
            debt.cents = new BigInteger(debt.cents).subtract(BigInteger.valueOf(payment.getValue())).toString();
            if (debt.cents.equals("0")) data.arrears.remove(debt.id);
            data.taxHistory.add(new EconomyData.TaxEntry(clock.millis(), payer, debt.government, debt.kind,
                    payment.getValue(), debt.subject, "ARREARS PAID"));
        }
        Ledger.trim(data.taxHistory, config.get().taxHistoryLimit);
        dirty.run();
        return quote.paid();
    }

    record PaymentQuote(List<Transfer> transfers, Map<EconomyData.Arrear, Long> payments, long paid) {}

    PaymentQuote quotePayment(String payer, long maximum) {
        return quotePayment(payer, maximum, data.arrears.values());
    }

    private PaymentQuote quotePayment(String payer, long maximum, Iterable<EconomyData.Arrear> debts) {
        ledger.thread();
        Money.nonNegative(maximum);
        long remaining = Math.min(maximum, spendable.applyAsLong(payer));
        List<Transfer> transfers = new ArrayList<>();
        Map<EconomyData.Arrear, Long> payments = new LinkedHashMap<>();
        Map<String, Long> destinationAmounts = new LinkedHashMap<>();
        for (EconomyData.Arrear debt : debts) {
            if (remaining == 0) break;
            if (!debt.payer.equals(payer)) continue;
            long amount = new BigInteger(debt.cents).min(BigInteger.valueOf(remaining)).longValueExact();
            long already = destinationAmounts.getOrDefault(debt.recipient, 0L);
            long capacity = Money.MAX - ledger.balance(debt.recipient) - already;
            amount = Math.min(amount, Math.max(0, capacity));
            if (amount == 0) continue;
            payments.put(debt, amount);
            transfers.add(new Transfer(payer, debt.recipient, amount, "Arrears: " + debt.kind));
            destinationAmounts.put(debt.recipient, Money.add(already, amount));
            remaining -= amount;
        }
        long paid = 0;
        for (long amount : payments.values()) paid = Money.add(paid, amount);
        return new PaymentQuote(List.copyOf(transfers), java.util.Collections.unmodifiableMap(payments), paid);
    }

    public BigInteger arrears(String payer) {
        return data.arrears.values().stream().filter(debt -> debt.payer.equals(payer))
                .map(debt -> new BigInteger(debt.cents)).reduce(BigInteger.ZERO, BigInteger::add);
    }
}
