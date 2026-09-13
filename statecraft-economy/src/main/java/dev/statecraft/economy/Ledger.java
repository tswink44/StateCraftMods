package dev.statecraft.economy;

import dev.statecraft.api.EconomyAccess.Transfer;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class Ledger {
    private static final Pattern ACCOUNT_ID = Pattern.compile(
            "(?:(?:player|nation|state|city|company|bank|escrow|system):[A-Za-z0-9_.-]{1,100}"
                    + "|escrow:contract:[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})");

    public record Adjustment(String account, long cents, boolean credit, String source, String reason) {}
    @FunctionalInterface public interface Guard {
        void check(Map<String, Long> balances, Map<String, Long> reserveOverrides);
    }

    private final EconomyData data;
    private final Supplier<EconomyConfig> config;
    private final Clock clock;
    private final Runnable dirty;
    private final Guard guard;
    private final Thread owner = Thread.currentThread();
    private long revision;
    private boolean committing;

    public Ledger(EconomyData data, Supplier<EconomyConfig> config, Clock clock, Runnable dirty, Guard guard) {
        this.data = data;
        this.config = config;
        this.clock = clock;
        this.dirty = dirty;
        this.guard = guard;
    }

    public static String account(String account) {
        if (account == null || !ACCOUNT_ID.matcher(account).matches()) {
            throw new UserError("Use a valid type:id account (player, nation, state, city, company, bank, escrow, system).");
        }
        if (account.startsWith("player:")) {
            try {
                String value = account.substring(7);
                if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                throw new UserError("Player accounts must use a full lowercase UUID.");
            }
        }
        return account;
    }

    public void thread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Economy mutations must run on the server thread.");
    }

    public long balance(String account) {
        thread();
        EconomyData.Account value = data.accounts.get(account(account));
        return value == null ? 0 : value.balance;
    }

    public void transferBatch(List<Transfer> transfers) {
        prepare(transfers).commit();
    }

    public Plan prepare(List<Transfer> transfers) {
        return prepare(transfers, List.of(), true, Map.of());
    }

    public Plan prepare(List<Transfer> transfers, List<Adjustment> adjustments, boolean spendingLimits,
                        Map<String, Long> reserveOverrides) {
        thread();
        if (committing) throw new UserError("A financial transaction is already committing; retry after it completes.");
        if (transfers == null || adjustments == null || transfers.size() + adjustments.size() > 100_000) {
            throw new UserError("Invalid or excessively large transaction batch.");
        }
        Map<String, BigInteger> deltas = new LinkedHashMap<>();
        Map<String, BigInteger> outgoing = new LinkedHashMap<>();
        List<EconomyData.Transaction> entries = new ArrayList<>();
        long now = clock.millis();
        String id = UUID.randomUUID().toString();
        for (Transfer transfer : transfers) {
            if (transfer == null) throw new UserError("Null transaction.");
            String from = account(transfer.from()), to = account(transfer.to());
            long cents = Money.nonNegative(transfer.cents());
            String reason = reason(transfer.reason());
            if (cents == 0 || from.equals(to)) continue;
            merge(deltas, from, -cents);
            merge(deltas, to, cents);
            merge(outgoing, from, cents);
            entries.add(new EconomyData.Transaction(id, now, from, to, cents, reason));
        }
        for (Adjustment adjustment : adjustments) {
            String account = account(adjustment.account());
            long cents = Money.nonNegative(adjustment.cents());
            String source = account(adjustment.source());
            if (cents == 0) continue;
            merge(deltas, account, adjustment.credit() ? cents : -cents);
            if (!adjustment.credit()) merge(outgoing, account, cents);
            entries.add(new EconomyData.Transaction(id, now, adjustment.credit() ? source : account,
                    adjustment.credit() ? account : source, cents, reason(adjustment.reason())));
        }
        Map<String, Long> balances = new LinkedHashMap<>();
        Map<String, Long> spent = new LinkedHashMap<>();
        Map<String, Long> spentDays = new LinkedHashMap<>();
        long day = Math.floorDiv(now, 86_400_000L);
        for (Map.Entry<String, BigInteger> delta : deltas.entrySet()) {
            BigInteger amount = BigInteger.valueOf(balance(delta.getKey())).add(delta.getValue());
            if (amount.signum() < 0) throw new UserError("Insufficient funds in " + delta.getKey() + ".");
            if (amount.compareTo(BigInteger.valueOf(Money.MAX)) > 0) {
                throw new UserError("The destination account would exceed the supported balance.");
            }
            balances.put(delta.getKey(), amount.longValueExact());
        }
        if (spendingLimits) {
            for (Map.Entry<String, BigInteger> entry : outgoing.entrySet()) {
                EconomyData.Account current = data.accounts.get(entry.getKey());
                long previous = current != null && current.spentDay >= day ? current.spent : 0;
                long limit = current == null ? Money.MAX : current.dailyLimit;
                BigInteger total = BigInteger.valueOf(previous).add(entry.getValue());
                if (total.compareTo(BigInteger.valueOf(limit)) > 0) {
                    throw new UserError("The daily spending limit for " + entry.getKey() + " would be exceeded.");
                }
                spent.put(entry.getKey(), total.longValueExact());
                spentDays.put(entry.getKey(), current == null ? day : Math.max(day, current.spentDay));
            }
        }
        guard.check(Map.copyOf(balances), reserveOverrides);
        return new Plan(revision, balances, spent, spentDays, entries);
    }

    private static void merge(Map<String, BigInteger> values, String key, long delta) {
        values.merge(key, BigInteger.valueOf(delta), BigInteger::add);
    }

    private static String reason(String reason) {
        if (reason == null || reason.length() > 256 || reason.chars().anyMatch(Character::isISOControl)) {
            throw new UserError("Transaction reasons must contain at most 256 printable characters.");
        }
        return reason;
    }

    public void spendingLimit(String account, long limit) {
        thread();
        if (committing) throw new UserError("Spending limits cannot change during a financial transaction.");
        account(account);
        Money.nonNegative(limit);
        EconomyData.Account existing = data.accounts.get(account);
        if (existing != null && existing.dailyLimit == limit) return;
        data.accounts.computeIfAbsent(account, ignored -> new EconomyData.Account()).dailyLimit = limit;
        revision++;
        dirty.run();
    }

    public final class Plan {
        private final long expectedRevision;
        private final Map<String, Long> balances;
        private final Map<String, Long> spent;
        private final Map<String, Long> spentDays;
        private final List<EconomyData.Transaction> entries;
        private boolean committed;

        private Plan(long revision, Map<String, Long> balances, Map<String, Long> spent, Map<String, Long> spentDays,
                     List<EconomyData.Transaction> entries) {
            expectedRevision = revision;
            this.balances = balances;
            this.spent = spent;
            this.spentDays = spentDays;
            this.entries = entries;
        }

        public long balanceAfter(String account) { return balances.getOrDefault(account, Ledger.this.balance(account)); }
        public void commit() { commitWith(() -> {}); }

        /** Ledger rollback also restores limits and history if an atomic external participant refuses. */
        public void commitWith(Runnable participant) {
            thread();
            if (committing || committed || expectedRevision != revision) throw new IllegalStateException("Stale or reentrant ledger transaction.");
            Map<String, EconomyData.Account> previous = new LinkedHashMap<>();
            List<EconomyData.Transaction> oldHistory = data.transactions;
            List<EconomyData.Transaction> newHistory = new ArrayList<>(oldHistory);
            newHistory.addAll(entries);
            trim(newHistory, config.get().historyLimit);
            for (String key : balances.keySet()) {
                EconomyData.Account old = data.accounts.get(key);
                if (old == null) previous.put(key, null);
                else {
                    EconomyData.Account copy = new EconomyData.Account();
                    copy.balance = old.balance;
                    copy.dailyLimit = old.dailyLimit;
                    copy.spentDay = old.spentDay;
                    copy.spent = old.spent;
                    previous.put(key, copy);
                }
            }
            committing = true;
            try {
                balances.forEach((key, value) -> data.accounts.computeIfAbsent(key, ignored -> new EconomyData.Account()).balance = value);
                spent.forEach((key, value) -> {
                    EconomyData.Account account = data.accounts.get(key);
                    account.spentDay = spentDays.get(key);
                    account.spent = value;
                });
                data.transactions = newHistory;
                participant.run();
                revision++;
                committed = true;
                if (!entries.isEmpty() || newHistory.size() != oldHistory.size()) dirty.run();
            } catch (RuntimeException | Error failure) {
                previous.forEach((key, value) -> {
                    if (value == null) data.accounts.remove(key);
                    else data.accounts.put(key, value);
                });
                data.transactions = oldHistory;
                throw failure;
            } finally {
                committing = false;
            }
        }
    }

    public static void trim(List<?> list, int maximum) {
        if (list.size() > maximum) list.subList(0, list.size() - maximum).clear();
    }
}
