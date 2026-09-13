package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

public final class EconomyEngine implements EconomyAccess {
    final EconomyData data;
    final GovernanceAccess governance;
    final Clock clock;
    final Runnable dirty;
    final Ledger ledger;
    final Taxation taxes;
    final Commerce commerce;
    final PropertyMarket property;
    final Banking banking;
    final ToIntFunction<String> itemStackSize;
    EconomyConfig config;
    ItemValues values;
    private final ArrayDeque<Work> work = new ArrayDeque<>();
    private final Set<Work> scheduled = new HashSet<>();
    private long nextDiscovery;
    private final EconomyCommands commands;

    record Work(String kind, String id) {}

    public EconomyEngine(EconomyData data, GovernanceAccess governance, EconomyConfig config, ItemValues values,
                         Clock clock, Runnable dirty, ValuationEnvironment environment,
                         ToIntFunction<String> itemStackSize) {
        config.validate();
        this.data = Objects.requireNonNull(data);
        this.governance = Objects.requireNonNull(governance);
        this.config = config;
        this.values = Objects.requireNonNull(values);
        this.clock = clock;
        this.dirty = dirty;
        this.itemStackSize = itemStackSize;
        ledger = new Ledger(data, () -> this.config, clock, dirty, this::guardReserves);
        taxes = new Taxation(data, governance, ledger, () -> this.config, clock, dirty, this::spendable);
        property = new PropertyMarket(this, environment);
        commerce = new Commerce(this);
        banking = new Banking(this);
        commands = new EconomyCommands(this);
        EconomyIntegrity.validate(data);
        discover();
    }

    public EconomyData data() { return data; }
    public EconomyConfig config() { return config; }
    public ItemValues values() { return values; }
    public Commerce commerce() { return commerce; }
    public PropertyMarket property() { return property; }
    public Banking banking() { return banking; }
    public Taxation taxes() { return taxes; }
    public Ledger ledger() { return ledger; }

    public void reconfigure(EconomyConfig replacement, ItemValues values) {
        ledger.thread();
        replacement.validate();
        this.config = replacement;
        this.values = Objects.requireNonNull(values);
        Ledger.trim(data.transactions, replacement.historyLimit);
        Ledger.trim(data.taxHistory, replacement.taxHistoryLimit);
        dirty.run();
    }

    public void setCommandHooks(EconomyCommands.Hooks hooks) { commands.hooks(hooks); }

    public String execute(Actor actor, String line, InventoryPort inventory) {
        ledger.thread();
        ensurePlayer(actor);
        return commands.execute(actor, line, inventory);
    }

    public void ensurePlayer(Actor actor) {
        ledger.thread();
        if (!data.playerNames.containsKey(actor.id().toString())) {
            if (config.initialPlayerBalanceCents > 0) {
                ledger.prepare(List.of(), List.of(new Ledger.Adjustment(actor.account(),
                        config.initialPlayerBalanceCents, true, "system:initial", "Initial player balance")), false, Map.of()).commit();
            }
            data.playerNames.put(actor.id().toString(), actor.name());
            dirty.run();
        } else if (!actor.name().equals(data.playerNames.get(actor.id().toString()))) {
            data.playerNames.put(actor.id().toString(), actor.name());
            dirty.run();
        }
    }

    public String resolveAccount(Actor actor, String text) {
        if (text == null || text.equalsIgnoreCase("me")) return actor.account();
        if (text.equalsIgnoreCase("selected")) return selectedAccount(actor);
        if (text.contains(":")) return Ledger.account(text);
        try { return Ledger.account("player:" + UUID.fromString(text)); }
        catch (IllegalArgumentException ignored) { }
        List<String> matching = data.playerNames.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(text))
                .map(Map.Entry::getKey).toList();
        if (matching.size() == 1) return "player:" + matching.get(0);
        throw new UserError("Unknown or ambiguous player. Use a full UUID or type:id account.");
    }

    public String requireAccount(Actor actor, String account) {
        ledger.thread();
        account = resolveAccount(actor, account);
        if (actor.admin() || actor.account().equals(account)) return account;
        String kind = account.substring(0, account.indexOf(':'));
        if (Set.of("nation", "state", "city", "company").contains(kind)
                && governance.mayAccessAccount(actor.id(), account)) {
            validateDestination(account);
            return account;
        }
        if (kind.equals("bank")) {
            EconomyData.Bank bank = banking.bank(account.substring(5), false);
            if (!bank.account().equals(account)) throw new UserError("Bank account labels must use their canonical ID.");
            if (governance.mayAccessAccount(actor.id(), "company:" + bank.company)) return account;
        }
        throw new UserError("You do not have treasury access to " + account + ".");
    }

    public String selectedAccount(Actor actor) {
        String selected = data.selectedAccounts.getOrDefault(actor.id().toString(), actor.account());
        return requireAccount(actor, selected);
    }

    public void selectAccount(Actor actor, String account) {
        account = requireAccount(actor, account);
        data.selectedAccounts.put(actor.id().toString(), account);
        dirty.run();
    }

    public List<String> accessibleAccounts(Actor actor) {
        Set<String> accounts = new java.util.TreeSet<>(governance.accountsFor(actor.id()));
        accounts.add(actor.account());
        for (EconomyData.Bank bank : data.banks.values()) {
            if (!bank.closed && (actor.admin() || governance.mayAccessAccount(actor.id(), "company:" + bank.company))) {
                accounts.add(bank.account());
            }
        }
        return accounts.stream().filter(account -> {
            try { requireAccount(actor, account); return true; } catch (UserError denied) { return false; }
        }).toList();
    }

    void validateDestination(String account) {
        Ledger.account(account);
        int separator = account.indexOf(':');
        String kind = account.substring(0, separator), id = account.substring(separator + 1);
        switch (kind) {
            case "player" -> { }
            case "company" -> {
                GovernanceAccess.CompanyView company = governance.company(id).orElseThrow(() -> new UserError("Unknown company account."));
                if (!company.account().equals(account)) throw new UserError("Company accounts must use the company's canonical ID, not its name.");
            }
            case "bank" -> {
                if (!banking.bank(id, true).account().equals(account)) throw new UserError("Bank accounts must use the bank's canonical ID, not its name.");
            }
            case "nation", "state", "city" -> {
                GovernanceAccess.GovernmentView government = governance.government(id)
                        .orElseThrow(() -> new UserError("Unknown government account."));
                if (!government.account().equals(account)) throw new UserError("Government account kind or ID is incorrect.");
            }
            case "escrow", "system" -> {
                if (!data.accounts.containsKey(account) && !account.equals("system:fees")) {
                    throw new UserError("Unknown internal account.");
                }
            }
            default -> throw new UserError("Unknown account kind.");
        }
    }

    void validatePublicDestination(Actor actor, String account) {
        if (!actor.admin() && (account.startsWith("escrow:") || account.startsWith("system:"))) {
            throw new UserError("Internal system/escrow accounts can only be funded through their authorized service.");
        }
        validateDestination(account);
    }

    public void pay(Actor actor, String from, String to, long cents, String reason) {
        from = requireAccount(actor, from);
        to = resolveAccount(actor, to);
        validatePublicDestination(actor, to);
        Money.positive(cents);
        if (from.equals(to)) throw new UserError("Choose a different destination account.");
        ledger.transferBatch(List.of(new Transfer(from, to, cents, reason)));
    }

    public long depositCash(Actor actor, String account, InventoryPort inventory) {
        inventory.require(InventoryPort.Utility.ATM, InventoryPort.Utility.VAULT);
        account = requireAccount(actor, account);
        InventoryPort.Plan items = inventory.plan();
        long cents = Money.positive(values.removeCash(items));
        ledger.prepare(List.of(), List.of(new Ledger.Adjustment(account, cents, true, "system:cash",
                "Physical currency deposited")), false, Map.of()).commitWith(items::commit);
        return cents;
    }

    public long withdrawCash(Actor actor, String account, long requested, InventoryPort inventory) {
        inventory.require(InventoryPort.Utility.ATM, InventoryPort.Utility.VAULT);
        account = requireAccount(actor, account);
        Money.positive(requested);
        // Do not let a nonrepresentable request disguise an otherwise unaffordable withdrawal.
        if (balance(account) < requested) throw new UserError("Insufficient funds.");
        InventoryPort.Plan items = inventory.plan();
        long issued = values.addCash(items, requested, itemStackSize);
        ledger.prepare(List.of(), List.of(new Ledger.Adjustment(account, issued, false, "system:cash",
                "Physical currency withdrawn")), true, Map.of()).commitWith(items::commit);
        return issued;
    }

    public void adminAdjust(Actor actor, String target, String operation, long amount) {
        requireAdmin(actor);
        target = resolveAccount(actor, target);
        if (!target.startsWith("system:") && !target.startsWith("escrow:")) validateDestination(target);
        Money.nonNegative(amount);
        long current = balance(target);
        long change;
        boolean credit;
        switch (operation.toLowerCase(Locale.ROOT)) {
            case "mint" -> { change = amount; credit = true; }
            case "take" -> { change = amount; credit = false; }
            case "set" -> { change = Math.abs(amount - current); credit = amount >= current; }
            default -> throw new UserError("Use mint, take, or set.");
        }
        ledger.prepare(List.of(), List.of(new Ledger.Adjustment(target, change, credit,
                "system:admin", "Operator " + actor.name() + ": " + operation)), false, Map.of()).commit();
    }

    public static void requireAdmin(Actor actor) {
        if (!actor.admin()) throw new UserError("This action requires operator permission.");
    }

    @Override public boolean available() { return true; }
    @Override public long balance(String account) { return ledger.balance(account); }
    @Override public void transferBatch(List<Transfer> transfers) { ledger.transferBatch(transfers); }
    @Override public long valueOf(String chunkKey) { ledger.thread(); return property.value(chunkKey).value; }
    @Override public boolean isClaimEncumbered(String chunkKey) {
        ledger.thread();
        return data.properties.containsKey(chunkKey) || banking.encumbers(chunkKey);
    }
    @Override public boolean isAccountInUse(String account) {
        ledger.thread();
        Ledger.account(account);
        if (balance(account) != 0) return true;
        if (account.startsWith("player:") && !data.deliveries.getOrDefault(account.substring(7), List.of()).isEmpty()) return true;
        if (data.arrears.values().stream().anyMatch(d -> d.payer.equals(account) || d.recipient.equals(account))) return true;
        if (data.market.values().stream().anyMatch(l -> l.sellerAccount.equals(account))) return true;
        if (data.properties.values().stream().anyMatch(l -> l.ownerAccount.equals(account))) return true;
        if (data.stocks.values().stream().anyMatch(l -> ("company:" + l.company).equals(account)
                || ("player:" + l.seller).equals(account))) return true;
        return banking.accountInUse(account);
    }

    private void guardReserves(Map<String, Long> balances, Map<String, Long> overrides) {
        if (banking != null) banking.guardReserves(balances, overrides);
    }

    public long spendable(String account) {
        long balance = balance(account);
        if (!account.startsWith("bank:") || banking == null) return balance;
        EconomyData.Bank bank = data.banks.get(account.substring(5));
        return bank == null ? balance : Math.max(0, balance - banking.protectedBalance(bank));
    }

    void schedule(String kind, String id) {
        Work entry = new Work(kind, id);
        if (scheduled.add(entry)) work.addLast(entry);
    }

    private void discover() {
        nextDiscovery = deadline(clock.millis(), config.discoveryIntervalMillis);
        governance.claims().forEach(c -> schedule("claim", c.key()));
        governance.companies().forEach(c -> schedule("company", c.id()));
        data.market.keySet().forEach(id -> schedule("market", id));
        data.properties.keySet().forEach(id -> schedule("property", id));
        data.stocks.keySet().forEach(id -> schedule("stock", id));
        data.loans.forEach((id, loan) -> {
            if (Set.of("REQUESTED", "ACTIVE", "DEFAULTED").contains(loan.status)) schedule("loan", id);
        });
        data.banks.forEach((id, bank) -> bank.deposits.keySet().forEach(player -> schedule("deposit", id + "|" + player)));
    }

    public void tick() {
        ledger.thread();
        long now = clock.millis();
        if (now >= nextDiscovery) discover();
        int budget = Math.min(config.workPerTick, work.size());
        for (int i = 0; i < budget; i++) {
            Work entry = work.removeFirst();
            boolean keep = false;
            try {
                keep = switch (entry.kind()) {
                    case "claim" -> property.tickClaim(entry.id());
                    case "company" -> commerce.tickCompany(entry.id());
                    case "market" -> commerce.tickMarket(entry.id());
                    case "property" -> property.tickListing(entry.id());
                    case "stock" -> commerce.tickStock(entry.id());
                    case "loan" -> banking.tickLoan(entry.id());
                    case "deposit" -> banking.tickDeposit(entry.id());
                    default -> false;
                };
            } catch (UserError failure) {
                keep = true;
                notice("Deferred " + entry.kind() + " " + entry.id() + ": " + failure.getMessage());
            }
            if (keep) work.addLast(entry);
            else scheduled.remove(entry);
        }
    }

    void notice(String text) {
        if (text.length() > 512) text = text.substring(0, 512);
        if (data.notices.isEmpty() || !data.notices.get(data.notices.size() - 1).equals(text)) {
            data.notices.add(text);
            Ledger.trim(data.notices, 200);
            dirty.run();
        }
    }

    void mail(String uuid, String subject, String body) {
        try { governance.mail(UUID.fromString(uuid), subject, body); }
        catch (RuntimeException failure) { notice("Mail delivery failed to " + uuid + ": " + subject + " — " + body); }
    }

    static long deadline(long time, long interval) {
        try { return Math.addExact(time, interval); }
        catch (ArithmeticException failure) { throw new UserError("The scheduled date is out of range."); }
    }

    static String id() { return UUID.randomUUID().toString(); }
    static int quantity(int quantity) {
        if (quantity < 1 || quantity > 1_000_000) throw new UserError("Item quantities must be between 1 and 1000000.");
        return quantity;
    }

    public List<EconomyData.Transaction> history(Actor actor, String account) {
        String selected = requireAccount(actor, account);
        return data.transactions.stream().filter(t -> t.from().equals(selected) || t.to().equals(selected)).toList();
    }

    public List<String> audit() {
        List<String> report = new ArrayList<>();
        try { EconomyIntegrity.validate(data); report.add("Economy snapshot structure and balances: OK"); }
        catch (RuntimeException problem) { report.add("INVALID: " + problem.getMessage()); }
        for (EconomyData.Bank bank : data.banks.values()) {
            if (!bank.closed && balance(bank.account()) < banking.requiredReserve(bank)) report.add("UNDER-RESERVED bank " + bank.id);
        }
        for (EconomyData.StockListing stock : data.stocks.values()) {
            if (governance.sharesOf(stock.company, UUID.fromString(stock.seller)) < stock.remaining) {
                report.add("UNBACKED stock listing " + stock.id);
            }
        }
        for (EconomyData.PropertyListing listing : data.properties.values()) {
            if (governance.claim(listing.chunk).map(c -> !c.ownerAccount().equals(listing.ownerAccount)).orElse(true)) {
                report.add("STALE property listing " + listing.chunk);
            }
        }
        report.add("Active listings: items=" + data.market.size() + ", property=" + data.properties.size()
                + ", shares=" + data.stocks.size() + "; arrears=" + data.arrears.size());
        return List.copyOf(report);
    }
}
