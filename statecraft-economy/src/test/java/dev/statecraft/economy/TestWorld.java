package dev.statecraft.economy;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.UserError;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class TestWorld {
    static final Actor OWNER = actor(1, "Owner", false, 0);
    static final Actor BUYER = actor(2, "Buyer", false, 1);
    static final Actor FOREIGN = actor(3, "Foreign", false, 10);
    static final Actor ADMIN = actor(99, "Operator", true, 0);
    static final String ONE = "statecraft_economy:currency_1";
    static final String TEN = "statecraft_economy:currency_10";
    static final String HUNDRED = "statecraft_economy:currency_100";
    static final String CLAIM = OWNER.chunkKey();
    static final String BUYER_CLAIM = BUYER.chunkKey();
    final Time clock = new Time();
    final Government governance = new Government();
    final EconomyConfig config = new EconomyConfig();
    final EconomyData data = new EconomyData();
    final ItemValues values = new ItemValues(Map.of("minecraft:wheat", 25L, "minecraft:diamond", 500L),
            Map.of(ONE, 100L, TEN, 1000L, HUNDRED, 10_000L), id -> true);
    EconomyEngine engine;
    int dirty;

    TestWorld() { this(ignored -> {}, ValuationEnvironment.NEUTRAL); }
    TestWorld(Consumer<EconomyConfig> customize) { this(customize, ValuationEnvironment.NEUTRAL); }
    TestWorld(Consumer<EconomyConfig> customize, ValuationEnvironment environment) {
        config.marketCommissionBps = 0;
        config.bankCreationFeeCents = 0;
        config.minimumBankReserveCents = 0;
        config.financialPeriodMillis = 1000;
        config.marketLifetimeMillis = 1000;
        config.stockLifetimeMillis = 1000;
        config.propertyListingLifetimeMillis = 1000;
        config.loanApplicationLifetimeMillis = 1000;
        config.propertyTaxPeriodMillis = 1000;
        config.companyFeePeriodMillis = 1000;
        config.valuationIntervalMillis = 1000;
        config.discoveryIntervalMillis = 1000;
        config.defaultChunkValueCents = 10_000;
        config.locationBonusBps = 0;
        config.demandBonusPerChunkBps = 0;
        config.improvementBonusBps = 0;
        config.allowUnsecuredLoans = true;
        config.defaultAfterMissedPayments = 2;
        config.loanGraceMillis = 0;
        customize.accept(config);
        engine = new EconomyEngine(data, governance, config, values, clock, () -> dirty++, environment, id -> 64);
        governance.economy = engine;
        for (Actor actor : List.of(OWNER, BUYER, FOREIGN, ADMIN)) engine.ensurePlayer(actor);
    }

    static Actor actor(int id, String name, boolean admin, int x) {
        return new Actor(new UUID(0, id), name, admin, "minecraft:overworld", x, 0);
    }

    void set(String account, long cents) { engine.adminAdjust(ADMIN, account, "set", cents); }
    void set(Actor actor, long cents) { set(actor.account(), cents); }
    void advance(long millis) { clock.now = Math.addExact(clock.now, millis); }
    void tick() { engine.tick(); }
    void restart() {
        engine = new EconomyEngine(data, governance, config, values, clock, () -> dirty++, ValuationEnvironment.NEUTRAL, id -> 64);
        governance.economy = engine;
    }
    String bank(long capital, int depositRate, int loanRate) {
        set("company:co", capital);
        return engine.banking.create(OWNER, "co", "People's Bank", capital, depositRate, loanRate);
    }
    String loan(Actor borrower, String bank, long principal, String collateral, boolean balance, boolean repossession, boolean auto) {
        engine.banking.associate(borrower, bank);
        String id = engine.banking.requestLoan(borrower, bank, principal, 4, collateral, balance, repossession, auto);
        engine.banking.approve(OWNER, id);
        return id;
    }

    static final class Time extends Clock {
        long now = 1_000_000;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    static final class Inventory implements InventoryPort {
        final List<ItemLot> slots;
        final EnumSet<Utility> utilities = EnumSet.allOf(Utility.class);
        int selected;
        boolean rejectReplace;
        int commits;

        Inventory() { this(9); }
        Inventory(int size) {
            slots = new ArrayList<>();
            for (int i = 0; i < size; i++) slots.add(ItemLot.EMPTY);
        }
        Inventory item(int slot, String id, int count) { return item(slot, new ItemLot(id, "", count, 64)); }
        Inventory item(int slot, ItemLot lot) { slots.set(slot, lot); return this; }
        void fill() {
            for (int i = 0; i < slots.size(); i++) slots.set(i, new ItemLot("minecraft:stone", "", 64, 64));
        }
        long count(String id) { return slots.stream().filter(item -> item.item().equals(id)).mapToLong(ItemLot::count).sum(); }
        @Override public List<ItemLot> snapshot() { return List.copyOf(slots); }
        @Override public int selectedSlot() { return selected; }
        @Override public boolean near(Utility utility) { return utilities.contains(utility); }
        @Override public void replace(List<ItemLot> expected, List<ItemLot> replacement) {
            if (rejectReplace || !expected.equals(slots)) throw new UserError("Inventory changed.");
            slots.clear();
            slots.addAll(replacement);
            commits++;
        }
    }

    static final class Government implements GovernanceAccess {
        final Map<String, GovernmentView> governments = new LinkedHashMap<>();
        final Map<String, Company> companies = new LinkedHashMap<>();
        final Map<String, ClaimView> claims = new LinkedHashMap<>();
        final Map<UUID, String> nations = new LinkedHashMap<>();
        final Map<UUID, Set<String>> granted = new LinkedHashMap<>();
        final Map<String, Reservation> reservations = new LinkedHashMap<>();
        final List<String> messages = new ArrayList<>();
        final Set<UUID> ineligibleBuyers = new LinkedHashSet<>();
        EconomyEngine economy;
        boolean rejectProperty;
        boolean rejectReturningProperty;
        boolean rejectShares;
        int propertyTransfers;

        Government() {
            governments.put("n1", new GovernmentView("n1", Kind.NATION, "Nation One", null, "n1", OWNER.id(),
                    Set.of(OWNER.id(), BUYER.id()), Map.of()));
            governments.put("s1", new GovernmentView("s1", Kind.STATE, "State One", "n1", "n1", OWNER.id(),
                    Set.of(OWNER.id(), BUYER.id()), Map.of()));
            governments.put("c1", new GovernmentView("c1", Kind.CITY, "City One", "s1", "n1", OWNER.id(),
                    Set.of(OWNER.id(), BUYER.id()), Map.of()));
            governments.put("n2", new GovernmentView("n2", Kind.NATION, "Foreign Nation", null, "n2", FOREIGN.id(),
                    Set.of(FOREIGN.id()), Map.of()));
            nations.put(OWNER.id(), "n1");
            nations.put(BUYER.id(), "n1");
            nations.put(FOREIGN.id(), "n2");
            claims.put(CLAIM, new ClaimView(CLAIM, "minecraft:overworld", 0, 0, "c1", "s1", "n1", OWNER.account(), 0, 0));
            claims.put(BUYER_CLAIM, new ClaimView(BUYER_CLAIM, "minecraft:overworld", 1, 0, "c1", "s1", "n1", BUYER.account(), 0, 0));
            Company company = new Company();
            company.shares.put(OWNER.id(), 70L);
            company.shares.put(BUYER.id(), 30L);
            companies.put("co", company);
        }

        void setting(String id, String name, String value) {
            GovernmentView old = governments.get(id);
            Map<String, String> settings = new LinkedHashMap<>(old.settings());
            settings.put(name, value);
            governments.put(id, new GovernmentView(old.id(), old.kind(), old.name(), old.parentId(), old.nationId(),
                    old.leader(), old.members(), Map.copyOf(settings)));
        }
        void claimOwner(String key, String owner) {
            ClaimView old = claims.get(key);
            claims.put(key, new ClaimView(old.key(), old.dimension(), old.x(), old.z(), old.cityId(), old.stateId(),
                    old.nationId(), owner, old.improvements(), old.claimedAt()));
        }
        void allocation(String key, String state, String city) {
            ClaimView old = claims.get(key);
            if (city != null && state == null) throw new IllegalArgumentException("Cities require a state allocation.");
            String owner = old.ownerAccount();
            if (owner.startsWith("nation:") || owner.startsWith("state:") || owner.startsWith("city:")) {
                owner = city != null ? "city:" + city : state != null ? "state:" + state : "nation:" + old.nationId();
            }
            claims.put(key, new ClaimView(old.key(), old.dimension(), old.x(), old.z(), city, state,
                    old.nationId(), owner, old.improvements(), old.claimedAt()));
        }
        void assign(String key, String state, String city) {
            if (economy != null && economy.isClaimEncumbered(key)) throw new UserError("Encumbered.");
            allocation(key, state, city);
        }
        void improvements(String key, int count) {
            ClaimView old = claims.get(key);
            claims.put(key, new ClaimView(old.key(), old.dimension(), old.x(), old.z(), old.cityId(), old.stateId(),
                    old.nationId(), old.ownerAccount(), count, old.claimedAt()));
        }
        void grant(Actor actor, String account) { granted.computeIfAbsent(actor.id(), ignored -> new LinkedHashSet<>()).add(account); }

        @Override public Collection<GovernmentView> governments() { return List.copyOf(governments.values()); }
        @Override public Optional<GovernmentView> government(String id) {
            if (id == null) throw new AssertionError("Optional government IDs must not be looked up.");
            return governments.values().stream().filter(g -> g.id().equals(id) || g.name().equalsIgnoreCase(id)).findFirst();
        }
        @Override public Collection<CompanyView> companies() { return companies.entrySet().stream().map(this::view).toList(); }
        private CompanyView view(Map.Entry<String, Company> entry) {
            return new CompanyView(entry.getKey(), "Example Company", OWNER.id(), Set.of(OWNER.id(), BUYER.id()), Map.copyOf(entry.getValue().shares));
        }
        @Override public Optional<CompanyView> company(String id) { return companies().stream().filter(c -> c.id().equals(id) || c.name().equalsIgnoreCase(id)).findFirst(); }
        @Override public Collection<ClaimView> claims() { return List.copyOf(claims.values()); }
        @Override public Optional<ClaimView> claim(String key) { return Optional.ofNullable(claims.get(key)); }
        @Override public Optional<String> nationOf(UUID player) { return Optional.ofNullable(nations.get(player)); }
        @Override public boolean mayManageGovernment(UUID player, String id) {
            GovernmentView government = governments.get(id);
            return government != null && (government.leader().equals(player) || granted.getOrDefault(player, Set.of()).contains(government.account()));
        }
        @Override public boolean mayManageCompany(UUID player, String id) { return companies.containsKey(id) && (player.equals(OWNER.id()) || granted.getOrDefault(player, Set.of()).contains("company:" + id)); }
        @Override public boolean mayAccessAccount(UUID player, String account) {
            if (granted.getOrDefault(player, Set.of()).contains(account)) return true;
            if (account.equals("player:" + player)) return true;
            if (account.startsWith("company:")) return mayManageCompany(player, account.substring(8));
            return governments.values().stream().anyMatch(g -> g.account().equals(account) && g.leader().equals(player));
        }
        @Override public Set<String> accountsFor(UUID player) {
            Set<String> accounts = new LinkedHashSet<>();
            accounts.add("player:" + player);
            governments.values().stream().filter(g -> mayAccessAccount(player, g.account())).forEach(g -> accounts.add(g.account()));
            companies().stream().filter(c -> mayAccessAccount(player, c.account())).forEach(c -> accounts.add(c.account()));
            return Set.copyOf(accounts);
        }
        @Override public boolean maySellProperty(UUID player, String key) {
            ClaimView claim = claims.get(key);
            return claim != null && mayAccessAccount(player, claim.ownerAccount());
        }
        @Override public boolean mayBuyProperty(UUID player, String key) { return claims.containsKey(key) && !ineligibleBuyers.contains(player); }
        @Override public void transferProperty(String key, String owner) {
            if (rejectProperty) throw new UserError("Property transfer rejected by governance.");
            if (rejectReturningProperty && owner.equals(OWNER.account())) throw new UserError("The former owner no longer qualifies for a new acquisition.");
            if (economy != null && economy.isClaimEncumbered(key)) throw new UserError("Encumbered.");
            Ledger.account(owner);
            claimOwner(key, owner);
            propertyTransfers++;
        }
        @Override public long sharesOf(String company, UUID shareholder) { return companies.get(company).shares.getOrDefault(shareholder, 0L); }
        @Override public long availableShares(String company, UUID shareholder) {
            return sharesOf(company, shareholder) - reservations.values().stream()
                    .filter(r -> r.company.equals(company) && r.owner.equals(shareholder)).mapToLong(r -> r.remaining).sum();
        }
        @Override public void transferShares(String company, UUID from, UUID to, long quantity) {
            if (quantity < 1 || availableShares(company, from) < quantity) throw new UserError("Unavailable shares.");
            move(company, from, to, quantity);
        }
        @Override public void reserveShares(String company, UUID owner, String reference, long quantity) {
            if (quantity < 1 || reservations.containsKey(reference) || availableShares(company, owner) < quantity) throw new UserError("Invalid reservation.");
            Reservation reservation = new Reservation();
            reservation.company = company;
            reservation.owner = owner;
            reservation.remaining = quantity;
            reservations.put(reference, reservation);
        }
        @Override public void releaseShares(String reference) {
            if (reservations.remove(reference) == null) throw new UserError("Unknown share reservation.");
        }
        @Override public void settleShares(String reference, UUID buyer, long quantity) {
            Reservation reservation = reservations.get(reference);
            if (rejectShares || reservation == null || quantity < 1 || quantity > reservation.remaining || reservation.owner.equals(buyer)) {
                throw new UserError("Share settlement rejected.");
            }
            move(reservation.company, reservation.owner, buyer, quantity);
            reservation.remaining -= quantity;
            if (reservation.remaining == 0) reservations.remove(reference);
        }
        private void move(String company, UUID from, UUID to, long quantity) {
            Map<UUID, Long> shares = companies.get(company).shares;
            long received = Math.addExact(shares.getOrDefault(to, 0L), quantity);
            if (shares.getOrDefault(from, 0L) < quantity) throw new UserError("Not enough shares.");
            shares.put(from, shares.get(from) - quantity);
            shares.put(to, received);
        }
        @Override public void mail(UUID recipient, String subject, String body) { messages.add(recipient + " " + subject + " " + body); }
        @Override public void governmentMail(String id, String subject, String body) { messages.add(id + " " + subject + " " + body); }
        static final class Company { final Map<UUID, Long> shares = new LinkedHashMap<>(); }
        static final class Reservation { String company; UUID owner; long remaining; }
    }
}
