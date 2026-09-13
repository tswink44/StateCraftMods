package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

abstract class DomainFixture {
    final Actor alice = actor(1, "Alice", false);
    final Actor bob = actor(2, "Bob", false);
    final Actor cara = actor(3, "Cara", false);
    final Actor dave = actor(4, "Dave", false);
    final Actor operator = actor(5, "Operator", true);
    final Actor felix = actor(6, "Felix", false);
    GovernanceData data;
    GovernanceConfig config;
    GovernanceEngine engine;
    FakeEconomy economy;
    AtomicLong time;

    @BeforeEach
    void prepare() {
        time = new AtomicLong(1_000_000);
        data = new GovernanceData();
        config = new GovernanceConfig();
        config.electionIntervalMillis = 10_000;
        config.electionVotingMillis = 2_000;
        config.debateDurationMillis = 1_000;
        config.legislativeVotingMillis = 1_000;
        config.signatureDurationMillis = 1_000;
        config.emergencyDurationMillis = 1_000;
        config.emergencyCooldownMillis = 3_000;
        config.invitationDurationMillis = 3_000;
        config.allianceProposalDurationMillis = 5_000;
        config.peaceProposalDurationMillis = 12_000;
        config.truceDurationMillis = 3_000;
        config.companyVotingMillis = 1_000;
        config.contractBiddingMillis = 5_000;
        config.contractReviewMillis = 5_000;
        engine = new GovernanceEngine(data, config, EconomyAccess.UNAVAILABLE, time::get);
        economy = new FakeEconomy();
        for (Actor actor : List.of(alice, bob, cara, dave, operator, felix)) engine.login(actor);
    }

    String run(Actor actor, String command) {
        return engine.execute(actor, command);
    }

    void useEconomy() {
        engine.setEconomy(economy);
    }

    void configure() {
        engine.configure(config);
    }

    void advance(long milliseconds) {
        time.addAndGet(milliseconds);
        engine.tick(time.get());
    }

    Tree tree(Actor leader, String prefix) {
        run(leader, "nation create " + q(prefix + "Nation"));
        String nation = government(prefix + "Nation").id();
        run(leader, "state create " + nation + " " + q(prefix + "State"));
        String state = government(prefix + "State").id();
        run(leader, "city create " + state + " " + q(prefix + "City"));
        String city = government(prefix + "City").id();
        for (String id : List.of(nation, state, city)) run(leader, "government setting " + id + " open true");
        return new Tree(nation, state, city);
    }

    void join(Actor actor, Tree tree) {
        run(actor, "nation join " + tree.nation);
        run(actor, "state join " + tree.state);
        run(actor, "city join " + tree.city);
    }

    String claim(Actor actor, Tree tree, int x, int z) {
        return claim(actor, tree, "minecraft:overworld", x, z);
    }

    String claim(Actor actor, Tree tree, String dimension, int x, int z) {
        String key = dimension + "|" + x + "|" + z;
        Actor local = at(actor, dimension, x, z);
        run(local, "chunk claim " + tree.nation + " " + key);
        run(local, "chunk assignstate " + tree.nation + " " + key + " " + tree.state);
        run(local, "chunk assigncity " + tree.state + " " + key + " " + tree.city);
        return key;
    }

    GovernanceAccess.GovernmentView government(String reference) {
        return engine.government(reference).orElseThrow();
    }

    String company(Actor actor, String name) {
        run(actor, "company create " + q(name));
        return engine.company(name).orElseThrow().id();
    }

    void employ(Actor manager, Actor player, String company) {
        run(manager, "company invite " + company + " " + player.name());
        run(player, "company accept " + company);
    }

    String latestBill() {
        return new ArrayList<>(data.bills.keySet()).get(data.bills.size() - 1);
    }

    String latestDiplomacy() {
        return new ArrayList<>(data.diplomacy.keySet()).get(data.diplomacy.size() - 1);
    }

    String latestCompanyProposal() {
        return new ArrayList<>(data.companyProposals.keySet()).get(data.companyProposals.size() - 1);
    }

    String latestContract() {
        return new ArrayList<>(data.contracts.keySet()).get(data.contracts.size() - 1);
    }

    static Actor actor(int value, String name, boolean admin) {
        return new Actor(new UUID(0, value), name, admin, "minecraft:overworld", 0, 0);
    }

    static Actor at(Actor actor, String dimension, int x, int z) {
        return new Actor(actor.id(), actor.name(), actor.admin(), dimension, x, z);
    }

    static Actor operator(Actor actor, boolean admin) {
        return new Actor(actor.id(), actor.name(), admin, actor.dimension(), actor.chunkX(), actor.chunkZ());
    }

    static String q(String value) {
        return CommandLine.quote(value);
    }

    record Tree(String nation, String state, String city) {}

    static final class FakeEconomy implements EconomyAccess {
        final Map<String, Long> balances = new LinkedHashMap<>();
        final Set<String> encumbered = new HashSet<>();
        final Set<String> used = new HashSet<>();
        final List<List<Transfer>> batches = new ArrayList<>();
        boolean available = true;
        boolean failNext;
        int attempts;

        @Override public boolean available() { return available; }

        @Override public long balance(String account) {
            if (!available) throw new UserError("Economy unavailable.");
            return balances.getOrDefault(account, 0L);
        }

        @Override public void transferBatch(List<Transfer> transfers) {
            attempts++;
            if (!available) throw new UserError("Economy unavailable.");
            if (failNext) {
                failNext = false;
                throw new UserError("Injected atomic transaction rejection.");
            }
            Map<String, Long> deltas = new HashMap<>();
            for (Transfer transfer : transfers) {
                Money.nonNegative(transfer.cents());
                deltas.merge(transfer.from(), -transfer.cents(), Math::addExact);
                deltas.merge(transfer.to(), transfer.cents(), Math::addExact);
            }
            Map<String, Long> next = new LinkedHashMap<>(balances);
            deltas.forEach((account, delta) -> {
                long value = Math.addExact(next.getOrDefault(account, 0L), delta);
                if (value < 0) throw new UserError("Insufficient funds: " + account);
                next.put(account, Money.nonNegative(value));
            });
            balances.clear();
            balances.putAll(next);
            batches.add(List.copyOf(transfers));
        }

        @Override public boolean isClaimEncumbered(String key) { return encumbered.contains(key); }
        @Override public boolean isAccountInUse(String account) { return used.contains(account); }
        @Override public long valueOf(String key) { return 10_000; }
    }
}
