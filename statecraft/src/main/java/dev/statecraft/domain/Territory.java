package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ChunkKey;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.Money;
import dev.statecraft.domain.GovernanceData.Claim;
import dev.statecraft.domain.GovernanceData.ChunkTerm;
import dev.statecraft.domain.GovernanceData.DiplomaticProposal;
import dev.statecraft.domain.GovernanceData.Government;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.statecraft.domain.GovernanceEngine.check;

/** National sovereignty and optional allocations, separate from the property's financial title. */
final class Territory {
    static final int MAX_BATCH = 64;
    private final GovernanceEngine e;

    Territory(GovernanceEngine engine) {
        e = engine;
    }

    static void migrateLegacy(GovernanceData data) {
        for (Claim claim : data.claims.values()) {
            if (claim == null || claim.nationId != null || claim.stateId != null) continue;
            Government city = legacyCity(data, claim.cityId);
            if (city == null) continue;
            claim.stateId = city.parentId;
            claim.nationId = data.governments.get(city.parentId).parentId;
        }
        for (DiplomaticProposal proposal : data.diplomacy.values()) {
            if (proposal == null) continue;
            for (ChunkTerm term : proposal.chunks) {
                if (term == null) continue;
                Government from = legacyCity(data, term.fromCity);
                Government to = legacyCity(data, term.toCity);
                if (term.fromNation == null && term.fromState == null && from != null) {
                    term.fromState = from.parentId;
                    term.fromNation = data.governments.get(from.parentId).parentId;
                }
                if (term.toNation == null && term.toState == null && to != null) {
                    term.toState = to.parentId;
                    term.toNation = data.governments.get(to.parentId).parentId;
                }
            }
        }
    }

    private static Government legacyCity(GovernanceData data, String id) {
        Government city = indexed(data, id, Kind.CITY);
        Government state = city == null ? null : indexed(data, city.parentId, Kind.STATE);
        Government nation = state == null ? null : indexed(data, state.parentId, Kind.NATION);
        return nation != null && nation.parentId == null ? city : null;
    }

    private static Government indexed(GovernanceData data, String id, Kind kind) {
        Government government = data.governments.get(id);
        return GovernanceEngine.validUuid(id) && government != null
                && id.equals(government.id) && government.kind == kind ? government : null;
    }

    List<String> keys(Actor actor, String value) {
        check(value != null && !value.isBlank(), "Select here or a comma-separated list of chunk keys.");
        String[] parts = value.split(",", -1);
        check(parts.length <= MAX_BATCH, "A territory batch may contain at most " + MAX_BATCH + " chunks.");
        check(parts.length == 1 || java.util.Arrays.stream(parts).noneMatch("here"::equals),
                "Use here alone, or a comma-separated list of explicit chunk keys.");
        List<String> keys = new ArrayList<>();
        for (String part : parts) keys.add(e.chunkKey(actor, part));
        validateKeys(actor, keys);
        return List.copyOf(keys);
    }

    private void validateKeys(Actor actor, List<String> keys) {
        validateKeys(actor, keys, true);
    }

    private void validateKeys(Actor actor, List<String> keys, boolean currentDimension) {
        check(!keys.isEmpty() && keys.size() <= MAX_BATCH, "Select between 1 and " + MAX_BATCH + " chunks.");
        Set<String> unique = new HashSet<>();
        for (String key : keys) {
            check(key != null, "Specify a valid chunk key.");
            ChunkKey parsed = ChunkKey.parse(key);
            check(key.equals(parsed.toString()), "Chunk keys must use canonical dimension|x|z coordinates.");
            check(!currentDimension || actor.dimension().equals(parsed.dimension()), "All selected chunks must be in your current dimension.");
            check(unique.add(key), "A chunk cannot appear twice in a territory batch.");
        }
    }

    void nationalManager(Actor actor, Government nation) {
        check(e.validHierarchy(nation) && nation.kind == Kind.NATION, "Only nations can claim or manage national territory.");
        e.manage(actor, nation);
        check(!e.requiresRepair(), "Repair invalid governance data before ordinary territory changes; use explicit operator reassignment for orphan claims.");
    }

    List<Claim> claimPlan(Actor actor, Government nation, List<String> keys) {
        nationalManager(actor, nation);
        validateKeys(actor, keys);
        check((long) e.data.claims.size() + keys.size() <= e.config.maxTotalClaims, "World claim limit reached.");
        Set<String> existing = e.nationClaims(nation.id);
        check((long) existing.size() + keys.size() <= e.config.maxClaimsPerNation, "Nation claim limit reached.");
        for (String key : keys) {
            check(!e.data.claims.containsKey(key), "A selected chunk is already claimed.");
            e.assertClaimFree(key, null);
        }
        if (e.config.requireAdjacentClaims) validateAdditions(existing, keys);
        e.requireEconomy(Money.multiply(e.config.claimFee, keys.size()));
        List<Claim> result = new ArrayList<>();
        long at = e.now();
        for (String key : keys) {
            Claim claim = new Claim();
            claim.key = key;
            claim.nationId = nation.id;
            claim.ownerAccount = e.account(nation);
            claim.claimedAt = at;
            result.add(claim);
        }
        return List.copyOf(result);
    }

    EconomyAccess.Transfer claimFee(Actor actor, Government nation, int count) {
        return new EconomyAccess.Transfer(actor.account(), e.account(nation),
                Money.multiply(e.config.claimFee, count), "Nation claim");
    }

    void claim(Actor actor, Government nation, List<String> keys) {
        List<Claim> claims = claimPlan(actor, nation, keys);
        e.pay(List.of(claimFee(actor, nation, claims.size())));
        for (Claim claim : claims) {
            e.data.claims.put(claim.key, claim);
            e.history("claim", claim.key, "Claimed by nation " + nation.id + " for " + actor.id());
        }
    }

    // Each new component must touch existing national land; the order of selection is immaterial.
    void validateAdditions(Set<String> existing, Collection<String> added) {
        Map<String, Set<ChunkKey>> additions = new LinkedHashMap<>();
        for (String key : added) {
            ChunkKey parsed = ChunkKey.parse(key);
            additions.computeIfAbsent(parsed.dimension(), ignored -> new HashSet<>()).add(parsed);
        }
        for (Map.Entry<String, Set<ChunkKey>> entry : additions.entrySet()) {
            Set<ChunkKey> unseen = new HashSet<>(entry.getValue());
            boolean hasLand = existing.stream().anyMatch(key -> key != null && key.startsWith(entry.getKey() + "|"));
            while (!unseen.isEmpty()) {
                ArrayDeque<ChunkKey> queue = new ArrayDeque<>();
                ChunkKey first = unseen.iterator().next();
                unseen.remove(first);
                queue.add(first);
                boolean anchored = false;
                while (!queue.isEmpty()) {
                    ChunkKey current = queue.removeFirst();
                    for (ChunkKey neighbor : neighbors(current)) {
                        if (existing.contains(neighbor.toString())) anchored = true;
                        if (unseen.remove(neighbor)) queue.addLast(neighbor);
                    }
                }
                check(hasLand ? anchored : unseen.isEmpty(),
                        "New claims must connect by an edge to this nation's land in the same dimension.");
            }
        }
    }

    private static List<ChunkKey> neighbors(ChunkKey key) {
        List<ChunkKey> result = new ArrayList<>(4);
        if (key.x() < ChunkKey.MAX_COORDINATE) result.add(new ChunkKey(key.dimension(), key.x() + 1, key.z()));
        if (key.x() > -ChunkKey.MAX_COORDINATE) result.add(new ChunkKey(key.dimension(), key.x() - 1, key.z()));
        if (key.z() < ChunkKey.MAX_COORDINATE) result.add(new ChunkKey(key.dimension(), key.x(), key.z() + 1));
        if (key.z() > -ChunkKey.MAX_COORDINATE) result.add(new ChunkKey(key.dimension(), key.x(), key.z() - 1));
        return result;
    }

    List<Claim> unclaimPlan(Actor actor, Government nation, List<String> keys, boolean force) {
        if (force) e.admin(actor);
        else nationalManager(actor, nation);
        validateKeys(actor, keys, !force);
        List<Claim> claims = new ArrayList<>();
        for (String key : keys) {
            Claim claim = e.requiredClaim(key);
            if (!force) {
                check(e.viewableClaim(claim) && nation.id.equals(claim.nationId), "All selected chunks must belong to this nation.");
                check(actor.admin() || e.mayAccessAccount(actor.id(), claim.ownerAccount),
                        "Private property cannot be unclaimed without its owner's authority.");
            }
            e.assertClaimFree(key, null);
            claims.add(claim);
        }
        if (!force && e.config.requireConnectedClaims) {
            Set<String> remaining = e.nationClaims(nation.id);
            remaining.removeAll(keys);
            Set<String> affected = keys.stream().map(ChunkKey::parse).map(ChunkKey::dimension).collect(Collectors.toSet());
            remaining.removeIf(key -> !affected.contains(ChunkKey.parse(key).dimension()));
            check(e.connected(remaining), "Unclaiming these chunks would split the nation's territory.");
        }
        return List.copyOf(claims);
    }

    void unclaim(Actor actor, Government nation, List<String> keys, boolean force) {
        List<Claim> claims = unclaimPlan(actor, nation, keys, force);
        for (Claim claim : claims) {
            e.data.claims.remove(claim.key);
            e.history("claim", claim.key, "Unclaimed by " + actor.id() + (force ? " (forced)" : ""));
        }
    }

    List<Allocation> statePlan(Actor actor, Government nation, List<String> keys, Government target) {
        nationalManager(actor, nation);
        validateKeys(actor, keys);
        check(target == null || e.validHierarchy(target) && target.kind == Kind.STATE && nation.id.equals(target.parentId),
                "The target state must belong to the selected nation.");
        List<Allocation> result = new ArrayList<>();
        for (String key : keys) {
            Claim claim = e.requiredClaim(key);
            check(e.viewableClaim(claim) && nation.id.equals(claim.nationId), "All selected chunks must belong to this nation.");
            String stateId = target == null ? null : target.id;
            String cityId = stateId != null && stateId.equals(claim.stateId) ? claim.cityId : null;
            result.add(allocation(claim, nation.id, stateId, cityId));
        }
        return guarded(result);
    }

    List<Allocation> cityPlan(Actor actor, Government state, List<String> keys, Government target) {
        check(e.validHierarchy(state) && state.kind == Kind.STATE, "Choose the state whose territory is being allocated.");
        e.manage(actor, state);
        check(!e.requiresRepair(), "Repair invalid governance data before ordinary territory changes.");
        validateKeys(actor, keys);
        check(target == null || e.validHierarchy(target) && target.kind == Kind.CITY && state.id.equals(target.parentId),
                "The target city must belong to the selected state.");
        List<Allocation> result = new ArrayList<>();
        for (String key : keys) {
            Claim claim = e.requiredClaim(key);
            check(e.viewableClaim(claim) && state.parentId.equals(claim.nationId) && state.id.equals(claim.stateId),
                    "All selected chunks must already be assigned to exactly this state.");
            result.add(allocation(claim, claim.nationId, state.id, target == null ? null : target.id));
        }
        return guarded(result);
    }

    Allocation reassignPlan(Actor actor, String key, Government target) {
        e.admin(actor);
        Claim claim = e.requiredClaim(key);
        check(key.equals(claim.key), "Repair the claim's key before reassigning its hierarchy.");
        check(e.validHierarchy(target), "The destination government must have a valid hierarchy.");
        String stateId = target.kind == Kind.CITY ? target.parentId : target.kind == Kind.STATE ? target.id : null;
        Allocation plan = allocation(claim, e.nationId(target), stateId, target.kind == Kind.CITY ? target.id : null);
        check(plan.changes(), "The chunk already has this jurisdiction and public title.");
        guarded(List.of(plan));
        return plan;
    }

    private List<Allocation> guarded(List<Allocation> plans) {
        for (Allocation plan : plans) if (plan.changes()) e.assertClaimFree(plan.claim.key, null);
        validateCapacity(plans);
        return List.copyOf(plans);
    }

    void validateCapacity(List<Allocation> plans) {
        Map<String, Integer> nations = new LinkedHashMap<>();
        Map<String, Integer> cities = new LinkedHashMap<>();
        for (Allocation plan : plans) {
            if (!Objects.equals(plan.claim.nationId, plan.nationId)) {
                if (plan.claim.nationId != null) nations.merge(plan.claim.nationId, -1, Integer::sum);
                nations.merge(plan.nationId, 1, Integer::sum);
            }
            if (!Objects.equals(plan.claim.cityId, plan.cityId)) {
                if (plan.claim.cityId != null) cities.merge(plan.claim.cityId, -1, Integer::sum);
                if (plan.cityId != null) cities.merge(plan.cityId, 1, Integer::sum);
            }
        }
        nations.forEach((id, delta) -> check(delta <= 0 || (long) e.nationClaims(id).size() + delta <= e.config.maxClaimsPerNation,
                "Destination nation claim limit reached."));
        cities.forEach((id, delta) -> check(delta <= 0 || (long) e.cityClaims(id).size() + delta <= e.config.maxClaimsPerCity,
                "Destination city allocation limit reached."));
    }

    Allocation allocation(Claim claim, String nationId, String stateId, String cityId) {
        String owner = e.publicTitle(claim) ? publicAccount(nationId, stateId, cityId) : claim.ownerAccount;
        return new Allocation(claim, nationId, stateId, cityId, owner);
    }

    static String publicAccount(String nationId, String stateId, String cityId) {
        return cityId != null ? "city:" + cityId : stateId != null ? "state:" + stateId : "nation:" + nationId;
    }

    void apply(List<Allocation> plans, String category, Actor actor) {
        for (Allocation plan : plans) {
            if (!plan.changes()) continue;
            apply(plan);
            e.history(category, plan.claim.key, "Nation=" + plan.nationId + ", state=" + plan.stateId
                    + ", city=" + plan.cityId + "; allocated by " + actor.id());
        }
    }

    void apply(Allocation plan) {
        Claim claim = plan.claim;
        if (!Objects.equals(claim.ownerAccount, plan.ownerAccount)) claim.permits.clear();
        claim.nationId = plan.nationId;
        claim.stateId = plan.stateId;
        claim.cityId = plan.cityId;
        claim.ownerAccount = plan.ownerAccount;
        e.changed();
    }

    record Allocation(Claim claim, String nationId, String stateId, String cityId, String ownerAccount) {
        boolean changes() {
            return !Objects.equals(claim.nationId, nationId) || !Objects.equals(claim.stateId, stateId)
                    || !Objects.equals(claim.cityId, cityId) || !Objects.equals(claim.ownerAccount, ownerAccount);
        }
    }
}
