package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.domain.GovernanceData.Claim;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceTerritoryTest extends DomainFixture {
    @Test
    void aNationNeedsNeitherAStateNorACityToClaimAndReloadItsLand() {
        run(alice, "nation create Frontier");
        String nation = government("Frontier").id();
        run(alice, "chunk claim Frontier here");
        var claim = engine.claim(alice.chunkKey()).orElseThrow();
        assertEquals(nation, claim.nationId());
        assertNull(claim.stateId());
        assertNull(claim.cityId());
        assertEquals("nation:" + nation, claim.ownerAccount());
        assertEquals(Kind.NATION, engine.claimGovernment(data.claims.get(claim.key())).kind);
        assertEquals(1, data.governments.size());
        assertTrue(engine.maySellProperty(alice.id(), claim.key()));
        assertTrue(run(alice, "chunk list Frontier").contains(claim.key()));

        Gson gson = new Gson();
        GovernanceData loaded = gson.fromJson(gson.toJson(data), GovernanceData.class);
        GovernanceEngine restored = new GovernanceEngine(loaded, config, EconomyAccess.UNAVAILABLE, time::get);
        assertEquals(GovernanceData.CURRENT_SCHEMA, loaded.schemaVersion);
        assertEquals(claim, restored.claim(claim.key()).orElseThrow());
        assertTrue(restored.validationIssues().isEmpty());
    }

    @Test
    void onlyCurrentNationalOfficialsClaimAndOnlyStateOrNationalOfficialsAllocateCities() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "state leader " + tree.state() + " Bob");
        run(alice, "city leader " + tree.city() + " Cara");
        for (Actor actor : List.of(alice, bob, cara, operator)) {
            for (String invalid : List.of(tree.state(), tree.city()))
                assertThrows(UserError.class, () -> run(actor, "chunk claim " + invalid + " here"));
        }
        assertThrows(UserError.class, () -> run(bob, "chunk claim " + tree.nation() + " here"));
        assertThrows(UserError.class, () -> run(cara, "claim"));
        run(alice, "chunk claim");
        assertThrows(UserError.class, () -> run(cara, "chunk unclaim " + tree.nation() + " here"));
        assertThrows(UserError.class, () -> run(bob, "chunk assignstate " + tree.nation() + " here " + tree.state()));
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(bob, "chunk assignstate " + tree.nation() + " here " + tree.state());
        assertThrows(UserError.class, () -> run(cara, "chunk assigncity " + tree.state() + " here " + tree.city()));
        run(bob, "chunk assigncity " + tree.state() + " here " + tree.city());
        run(alice, "nation officer " + tree.nation() + " Bob remove");
        run(bob, "chunk assigncity " + tree.state() + " here none");
        assertThrows(UserError.class, () -> run(bob, "chunk claim " + tree.nation() + " " + key(1)));
        run(operator, "chunk claim " + tree.nation() + " " + key(1));
        assertThrows(UserError.class, () -> run(operator(operator, false), "chunk claim " + tree.nation() + " " + key(2)));
        assertFalse(engine.mayManageGovernment(operator.id(), tree.nation()));
    }

    @Test
    void locationOnlyClaimAliasesResolveTheNationAndNeverInferItFromACityReference() {
        Tree tree = tree(alice, "Alpha");
        GovernancePresentation ui = new GovernancePresentation(engine);
        assertDoesNotThrow(() -> ui.preview(alice, ActionSelection.raw("statecraft:claims", "claim here")));
        run(alice, "chunk claim here");
        run(alice, "claim " + key(1) + "," + key(2));
        assertEquals(3, engine.nationClaims(tree.nation()).size());
        assertTrue(engine.cityClaims(tree.city()).isEmpty());
        assertThrows(UserError.class, () -> run(alice, "claim " + tree.city() + " " + key(3)));
        assertThrows(UserError.class, () -> run(alice, "claim " + tree.state() + " " + key(3)));
        assertFalse(data.claims.containsKey(key(3)));
    }

    @Test
    void claimFeesAreOneAtomicBatchAfterEveryOverlapDuplicateAndAdjacencyCheck() {
        Tree tree = tree(alice, "Alpha");
        config.claimFee = 125;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), 1_000L);
        run(alice, "chunk claim " + tree.nation() + " " + key(2) + "," + key(0) + "," + key(1));
        assertEquals(1, economy.attempts);
        assertEquals(1, economy.batches.size());
        assertEquals(375, economy.batches.get(0).get(0).cents());
        assertEquals(625, economy.balance(alice.account()));
        assertEquals(375, economy.balance("nation:" + tree.nation()));
        assertEquals(0, economy.balance("state:" + tree.state()));
        assertEquals(0, economy.balance("city:" + tree.city()));
        String before = claimsJson();
        for (String selection : List.of(key(3) + "," + key(0), key(3) + "," + key(3),
                key(3) + ",minecraft:overworld|03|0", key(5) + "," + key(6))) {
            assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + selection));
            assertEquals(before, claimsJson());
        }
        assertEquals(1, economy.attempts);
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + keys(3, 9)));
        assertEquals(before, claimsJson());
        assertEquals(625, economy.balance(alice.account()));
        economy.failNext = true;
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + keys(3, 5)));
        assertEquals(before, claimsJson());
        assertEquals(1, economy.batches.size());
        assertEquals(375, economy.balance("nation:" + tree.nation()));
    }

    @Test
    void everyMouseSelectionOrderOfAConnectedBatchPassesTheSamePurePreflight() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "chunk claim " + tree.nation() + " here");
        config.claimFee = 100;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), 1_000L);
        List<String> selected = List.of(key(1), key(2), "minecraft:overworld|2|1", "minecraft:overworld|3|1");
        List<List<String>> orders = permutations(selected);
        assertEquals(24, orders.size());
        String before = claimsJson();
        for (List<String> order : orders) {
            var plan = engine.territory.claimPlan(alice, engine.gov(tree.nation()), order);
            assertEquals(Set.copyOf(selected), plan.stream().map(c -> c.key).collect(Collectors.toSet()));
            assertEquals(before, claimsJson());
        }
        assertEquals(0, economy.attempts);
        run(alice, "chunk claim " + tree.nation() + " " + String.join(",", orders.get(orders.size() - 1)));
        assertEquals(5, engine.nationClaims(tree.nation()).size());
        assertEquals(400, economy.balance("nation:" + tree.nation()));
        assertEquals(600, economy.balance(alice.account()));
        assertEquals(1, economy.attempts);
        assertEquals(1, economy.batches.size());
    }

    @Test
    void foreignAndPrivateOverlapsNeverPartiallyClaimOrChargeAnOtherwiseFreeChunk() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        run(alice, "chunk claim " + alpha.nation() + " " + keys(0, 2));
        engine.transferProperty(key(1), bob.account());
        run(bob, "chunk permit " + key(1) + " Cara BREAK,PLACE");
        run(dave, "chunk claim " + beta.nation() + " " + keys(3, 5));
        engine.transferProperty(key(4), dave.account());
        useEconomy();
        config.claimFee = 100;
        configure();
        economy.balances.put(alice.account(), 1_000L);
        String before = claimsJson();
        Map<String, Long> balances = Map.copyOf(economy.balances);
        for (String blocked : List.of(key(1), key(3), key(4))) {
            UserError error = assertThrows(UserError.class,
                    () -> run(alice, "chunk claim " + alpha.nation() + " " + key(2) + "," + blocked));
            assertTrue(error.getMessage().contains("already claimed"));
            assertEquals(before, claimsJson());
            assertFalse(data.claims.containsKey(key(2)));
            assertEquals(balances, economy.balances);
            assertEquals(0, economy.attempts);
        }
        run(alice, "chunk claim " + alpha.nation() + " " + key(2));
        assertEquals("nation:" + alpha.nation(), data.claims.get(key(2)).ownerAccount);
        assertEquals(100, economy.balance("nation:" + alpha.nation()));
        assertEquals(1, economy.batches.size());
    }

    @Test
    void batchesAreBoundedCanonicalDimensionScopedAndRejectTotalFeeOverflow() {
        Tree tree = tree(alice, "Alpha");
        for (String selection : List.of(keys(0, 65), key(0) + ",", "here," + key(1),
                "minecraft:overworld|1875001|0", "minecraft:overworld|-1875001|0",
                "MINECRAFT:overworld|0|0", key(0) + ",minecraft:the_nether|1|0")) {
            assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + selection));
            assertTrue(data.claims.isEmpty());
        }
        config.claimFee = Money.MAX;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), Money.MAX);
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + keys(0, 2)));
        assertEquals(0, economy.attempts);
        config.claimFee = 0;
        configure();
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 64));
        assertEquals(64, engine.claims().size());
        assertEquals(0, engine.cityClaims(tree.city()).size());
        Actor nether = at(alice, "minecraft:the_nether", 0, 0);
        run(nether, "chunk claim " + tree.nation() + " here");
        assertEquals(65, engine.nationClaims(tree.nation()).size());
        assertThrows(UserError.class, () -> run(alice, "chunk assignstate " + tree.nation() + " " + nether.chunkKey() + " " + tree.state()));
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + tree.nation() + " " + nether.chunkKey()));
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " minecraft:the_nether|1|0"));
    }

    @Test
    void nationalUnclaimConnectivityIsAtomicAcrossAssignedAndUnassignedLand() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 3));
        run(alice, "chunk assignstate " + tree.nation() + " " + key(0) + " " + tree.state());
        run(alice, "chunk assigncity " + tree.state() + " " + key(0) + " " + tree.city());
        String before = claimsJson();
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + tree.nation() + " " + key(1)));
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + tree.nation() + " " + key(0) + "," + key(0)));
        assertEquals(before, claimsJson());
        run(alice, "chunk unclaim " + tree.nation() + " " + key(1) + "," + key(0));
        assertEquals(Set.of(key(2)), engine.nationClaims(tree.nation()));
        assertNull(data.claims.get(key(2)).stateId);
        run(alice, "unclaim " + key(2));
        assertTrue(data.claims.isEmpty());
    }

    @Test
    void explicitOperatorCleanupCanAddressRemoteDimensionsButCannotBypassCollateralLocks() {
        Tree tree = tree(alice, "Alpha");
        Actor nether = at(alice, "minecraft:the_nether", 0, 0);
        run(nether, "chunk claim " + tree.nation() + " here");
        useEconomy();
        economy.encumbered.add(nether.chunkKey());
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + nether.chunkKey()));
        economy.encumbered.clear();
        assertThrows(UserError.class, () -> run(operator(operator, false), "admin unclaim " + nether.chunkKey()));
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + tree.nation() + " " + nether.chunkKey()));
        run(operator, "admin unclaim " + nether.chunkKey());
        assertTrue(data.claims.isEmpty());
    }

    @Test
    void newBatchesCanAttachToAndBridgePreservedDisconnectedNationalTerritory() {
        Tree tree = tree(alice, "Alpha");
        config.requireAdjacentClaims = false;
        configure();
        run(alice, "chunk claim " + tree.nation() + " " + key(0) + "," + key(4));
        config.requireAdjacentClaims = true;
        configure();
        run(alice, "chunk claim " + tree.nation() + " " + key(1) + "," + key(3));
        assertFalse(engine.connected(engine.nationClaims(tree.nation())));
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + keys(8, 10)));
        run(alice, "chunk claim " + tree.nation() + " " + key(2));
        assertTrue(engine.connected(engine.nationClaims(tree.nation())));
    }

    @Test
    void publicTitleAndFutureSaleAuthorityFollowAllocationsWithoutMovingBalances() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "state leader " + tree.state() + " Bob");
        run(alice, "city leader " + tree.city() + " Cara");
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 101L);
        economy.balances.put("state:" + tree.state(), 202L);
        economy.balances.put("city:" + tree.city(), 303L);
        Map<String, Long> balances = Map.copyOf(economy.balances);
        run(alice, "chunk claim " + tree.nation() + " here");
        Claim claim = data.claims.get(alice.chunkKey());
        engine.recordImprovement(claim.key, 37);
        assertEquals("nation:" + tree.nation(), claim.ownerAccount);
        assertFalse(engine.maySellProperty(bob.id(), claim.key));
        run(alice, "chunk permit here Dave BREAK");
        run(alice, "chunk assignstate " + tree.nation() + " here " + tree.state());
        assertEquals("state:" + tree.state(), claim.ownerAccount);
        assertTrue(claim.permits.isEmpty());
        assertTrue(engine.maySellProperty(bob.id(), claim.key));
        assertFalse(engine.maySellProperty(cara.id(), claim.key));
        run(bob, "chunk permit here Dave PLACE");
        run(bob, "chunk assigncity " + tree.state() + " here " + tree.city());
        assertEquals("city:" + tree.city(), claim.ownerAccount);
        assertTrue(claim.permits.isEmpty());
        assertTrue(engine.maySellProperty(cara.id(), claim.key));
        run(bob, "chunk assigncity " + tree.state() + " here none");
        assertEquals("state:" + tree.state(), claim.ownerAccount);
        assertNull(claim.cityId);
        run(alice, "chunk assignstate " + tree.nation() + " here none");
        assertEquals("nation:" + tree.nation(), claim.ownerAccount);
        assertNull(claim.stateId);
        assertNull(claim.cityId);
        assertEquals(37, claim.improvements);
        assertEquals(balances, economy.balances);
        assertEquals(0, economy.attempts);
    }

    @Test
    void playerAndCompanyTitlesAndPermitsSurviveEveryAdministrativeAllocation() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String company = company(bob, "Builders");
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 2));
        engine.transferProperty(key(0), bob.account());
        engine.transferProperty(key(1), "company:" + company);
        for (int x = 0; x < 2; x++) {
            run(bob, "chunk permit " + key(x) + " Dave BREAK,PLACE");
            engine.recordImprovement(key(x), 27 + x);
        }
        String permits = new Gson().toJson(data.claims.values().stream().map(c -> c.permits).toList());
        long claimedAt = data.claims.get(key(0)).claimedAt;
        run(alice, "chunk assignstate " + tree.nation() + " " + keys(0, 2) + " " + tree.state());
        run(alice, "chunk assigncity " + tree.state() + " " + keys(0, 2) + " " + tree.city());
        run(alice, "chunk assigncity " + tree.state() + " " + keys(0, 2) + " none");
        run(alice, "chunk assignstate " + tree.nation() + " " + keys(0, 2) + " none");
        assertEquals(bob.account(), data.claims.get(key(0)).ownerAccount);
        assertEquals("company:" + company, data.claims.get(key(1)).ownerAccount);
        assertEquals(permits, new Gson().toJson(data.claims.values().stream().map(c -> c.permits).toList()));
        assertEquals(27, data.claims.get(key(0)).improvements);
        assertEquals(28, data.claims.get(key(1)).improvements);
        assertEquals(claimedAt, data.claims.get(key(0)).claimedAt);
        assertTrue(engine.mayAct(dave, key(0), AccessAction.BREAK, null));
        assertTrue(engine.mayAct(dave, key(1), AccessAction.PLACE, null));
        assertFalse(engine.mayAct(alice, key(0), AccessAction.BREAK, null));
        assertThrows(UserError.class, () -> run(alice, "chunk unclaim " + tree.nation() + " " + keys(0, 2)));
        assertThrows(UserError.class, () -> run(operator, "admin delete company " + company));
    }

    @Test
    void allocationsRejectOtherNationsAndStatesAndPreserveCompatibleCityChildren() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(felix, "Beta");
        run(dave, "nation join " + alpha.nation());
        run(alice, "state create " + alpha.nation() + " OtherState Dave");
        String otherState = government("OtherState").id();
        run(dave, "city create " + otherState + " OtherCity");
        String otherCity = government("OtherCity").id();
        String first = claim(alice, alpha, 0, 0);
        run(felix, "chunk claim " + beta.nation() + " " + key(5));
        run(alice, "chunk permit " + first + " Bob BREAK");
        run(alice, "chunk assignstate " + alpha.nation() + " here " + alpha.state());
        assertEquals(alpha.city(), data.claims.get(first).cityId);
        assertFalse(data.claims.get(first).permits.isEmpty());
        String before = claimsJson();
        for (String command : List.of(
                "chunk assignstate " + alpha.nation() + " here " + beta.state(),
                "chunk assignstate " + alpha.nation() + " " + first + "," + key(5) + " " + alpha.state(),
                "chunk assigncity " + alpha.state() + " here " + otherCity,
                "chunk assigncity " + otherState + " here " + otherCity)) {
            assertThrows(UserError.class, () -> run(alice, command));
            assertEquals(before, claimsJson());
        }
        run(alice, "chunk assignstate " + alpha.nation() + " here " + otherState);
        assertEquals(otherState, data.claims.get(first).stateId);
        assertNull(data.claims.get(first).cityId);
        assertEquals("state:" + otherState, data.claims.get(first).ownerAccount);
        run(dave, "chunk assigncity " + otherState + " here " + otherCity);
        assertEquals(otherCity, data.claims.get(first).cityId);
    }

    @Test
    void cityLimitsApplyOnlyToAtomicAllocationsAndReducedLimitsNeverEvictLand() {
        Tree tree = tree(alice, "Alpha");
        config.maxClaimsPerCity = 1;
        configure();
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 3));
        run(alice, "chunk assignstate " + tree.nation() + " " + keys(0, 3) + " " + tree.state());
        String before = claimsJson();
        assertThrows(UserError.class, () -> run(alice, "chunk assigncity " + tree.state() + " " + key(0) + "," + key(2) + " " + tree.city()));
        assertEquals(before, claimsJson());
        config.maxClaimsPerCity = 2;
        configure();
        run(alice, "chunk assigncity " + tree.state() + " " + key(0) + "," + key(2) + " " + tree.city());
        assertEquals(2, engine.cityClaims(tree.city()).size());
        config.maxClaimsPerCity = 1;
        config.maxClaimsPerNation = 1;
        configure();
        run(alice, "chunk assignstate " + tree.nation() + " " + key(0) + " " + tree.state());
        run(alice, "chunk assigncity " + tree.state() + " " + key(0) + " " + tree.city());
        assertTrue(engine.validationIssues().isEmpty());
        assertEquals(3, engine.nationClaims(tree.nation()).size());
        assertEquals(2, engine.cityClaims(tree.city()).size());
        assertThrows(UserError.class, () -> run(alice, "chunk claim " + tree.nation() + " " + key(3)));
        run(alice, "chunk assignstate " + tree.nation() + " " + keys(0, 3) + " none");
        assertEquals(3, engine.nationClaims(tree.nation()).size());
        assertTrue(engine.cityClaims(tree.city()).isEmpty());
    }

    @Test
    void encumbrancesAndCoreCommitmentsBlockWholeAllocationsWithoutPhantomFees() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        run(alice, "chunk claim " + alpha.nation() + " " + keys(0, 2));
        useEconomy();
        config.claimFee = 900;
        configure();
        economy.encumbered.add(key(1));
        String before = claimsJson();
        assertThrows(UserError.class, () -> run(alice, "chunk assignstate " + alpha.nation() + " " + keys(0, 2) + " " + alpha.state()));
        assertEquals(before, claimsJson());
        economy.encumbered.clear();
        run(alice, "contract create " + alpha.nation() + " Road Work " + key(1));
        String contract = latestContract();
        assertThrows(UserError.class, () -> run(alice, "chunk assignstate " + alpha.nation() + " " + keys(0, 2) + " " + alpha.state()));
        assertEquals(before, claimsJson());
        run(alice, "contract cancel " + contract + " Done");
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + key(1) + "=" + beta.nation());
        assertThrows(UserError.class, () -> run(alice, "chunk assignstate " + alpha.nation() + " " + keys(0, 2) + " " + alpha.state()));
        assertEquals(before, claimsJson());
        run(alice, "diplomacy cancel " + latestDiplomacy());
        run(alice, "chunk assignstate " + alpha.nation() + " " + keys(0, 2) + " " + alpha.state());
        run(alice, "chunk assigncity " + alpha.state() + " " + keys(0, 2) + " " + alpha.city());
        economy.encumbered.add(key(1));
        before = claimsJson();
        assertThrows(UserError.class, () -> run(alice, "chunk assigncity " + alpha.state() + " " + keys(0, 2) + " none"));
        assertEquals(before, claimsJson());
        assertEquals(0, economy.attempts);
        engine.setEconomy(EconomyAccess.UNAVAILABLE);
        assertThrows(UserError.class, () -> run(alice, "chunk assignstate " + alpha.nation() + " " + key(0) + " none"));
    }

    @Test
    void protectionPropertyEligibilityAndValuationPolicyUseTheMostSpecificAssignment() {
        Tree tree = tree(alice, "Alpha");
        run(bob, "nation join " + tree.nation());
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 3));
        run(alice, "nation setting " + tree.nation() + " foreignAccess true");
        run(alice, "nation setting " + tree.nation() + " foreignProperty true");
        run(alice, "nation setting " + tree.nation() + " explosions true");
        run(alice, "nation setting " + tree.nation() + " pvp true");
        run(alice, "nation setting " + tree.nation() + " baseChunkValue 12345");
        run(alice, "state setting " + tree.state() + " foreignAccess false");
        run(alice, "state setting " + tree.state() + " foreignProperty false");
        run(alice, "state setting " + tree.state() + " explosions false");
        run(alice, "state setting " + tree.state() + " pvp false");
        run(alice, "state setting " + tree.state() + " baseChunkValue 23456");
        run(alice, "chunk assignstate " + tree.nation() + " " + keys(1, 3) + " " + tree.state());
        run(alice, "chunk assigncity " + tree.state() + " " + key(2) + " " + tree.city());
        run(alice, "city setting " + tree.city() + " foreignAccess true");
        run(alice, "city setting " + tree.city() + " baseChunkValue 34567");
        assertTrue(engine.mayAct(dave, key(0), AccessAction.PLACE, null));
        assertFalse(engine.mayAct(dave, key(1), AccessAction.PLACE, null));
        assertTrue(engine.mayAct(dave, key(2), AccessAction.PLACE, null));
        assertTrue(engine.mayAct(bob, key(1), AccessAction.PLACE, null));
        assertTrue(engine.mayBuyProperty(dave.id(), key(0)));
        assertFalse(engine.mayBuyProperty(dave.id(), key(1)));
        assertTrue(engine.allowsExplosion(key(0)));
        assertFalse(engine.allowsExplosion(key(1)));
        assertFalse(engine.allowsExplosion(key(2)));
        assertTrue(engine.mayAct(alice, key(0), AccessAction.PVP, dave.id()));
        assertFalse(engine.mayAct(alice, key(1), AccessAction.PVP, dave.id()));
        for (int x = 0; x < 3; x++) {
            String expected = List.of("12345", "23456", "34567").get(x);
            assertEquals(expected, engine.settings(engine.claimGovernment(data.claims.get(key(x)))).get("baseChunkValue"));
        }
        engine.transferProperty(key(1), bob.account());
        assertFalse(engine.mayAct(alice, key(1), AccessAction.BREAK, null));
        assertEquals(tree.nation(), data.claims.get(key(1)).nationId);
        assertEquals(tree.state(), data.claims.get(key(1)).stateId);
        assertNull(data.claims.get(key(1)).cityId);
    }

    @Test
    void allianceEnemyAndWarPvpPoliciesWorkOnUnassignedNationalAndStateLand() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        run(alice, "chunk claim " + alpha.nation() + " " + keys(0, 2));
        run(alice, "chunk assignstate " + alpha.nation() + " " + key(1) + " " + alpha.state());
        run(alice, "state setting " + alpha.state() + " alliedAccess false");
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        run(dave, "diplomacy accept " + latestDiplomacy());
        assertTrue(engine.mayAct(dave, key(0), AccessAction.BREAK, null));
        assertFalse(engine.mayAct(dave, key(1), AccessAction.BREAK, null));
        assertFalse(engine.mayAct(alice, key(0), AccessAction.PVP, dave.id()));
        run(alice, "diplomacy break " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        assertFalse(engine.mayAct(dave, key(0), AccessAction.BREAK, null));
        assertTrue(engine.mayAct(alice, key(0), AccessAction.PVP, dave.id()));
        assertTrue(engine.mayAct(alice, key(1), AccessAction.PVP, dave.id()));
        run(alice, "nation setting " + alpha.nation() + " enemyAccess true");
        run(alice, "state setting " + alpha.state() + " enemyAccess false");
        assertTrue(engine.mayAct(dave, key(0), AccessAction.BREAK, null));
        assertFalse(engine.mayAct(dave, key(1), AccessAction.BREAK, null));
    }

    @Test
    void cityAndStateDeletionRequireUnassignmentAndNeverSilentlyRemoveNationalLand() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        assertThrows(UserError.class, () -> run(alice, "city disband " + tree.city() + " cascade"));
        assertThrows(UserError.class, () -> run(operator, "admin delete state " + tree.state() + " cascade"));
        assertEquals(3, data.governments.size());
        run(alice, "chunk assigncity " + tree.state() + " here none");
        run(alice, "city disband " + tree.city());
        assertTrue(data.claims.containsKey(key));
        assertEquals("state:" + tree.state(), data.claims.get(key).ownerAccount);
        assertThrows(UserError.class, () -> run(alice, "state disband " + tree.state() + " cascade"));
        run(alice, "chunk assignstate " + tree.nation() + " here none");
        run(alice, "state disband " + tree.state());
        assertTrue(data.claims.containsKey(key));
        assertEquals("nation:" + tree.nation(), data.claims.get(key).ownerAccount);
        assertThrows(UserError.class, () -> run(alice, "nation disband " + tree.nation()));
        run(alice, "nation disband " + tree.nation() + " cascade");
        assertTrue(data.claims.isEmpty());
        assertTrue(data.governments.isEmpty());
    }

    @Test
    void privateTitlesBlockNationalCascadeEvenForOperatorsAndPublicLocksStillApply() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 2));
        engine.transferProperty(key(1), bob.account());
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        assertEquals(2, engine.claims().size());
        engine.transferProperty(key(1), "nation:" + tree.nation());
        useEconomy();
        economy.encumbered.add(key(1));
        assertThrows(UserError.class, () -> run(alice, "nation disband " + tree.nation() + " cascade"));
        assertEquals(2, engine.claims().size());
        economy.encumbered.clear();
        run(alice, "nation disband " + tree.nation() + " cascade");
        assertTrue(data.claims.isEmpty());
    }

    @Test
    void nationalAndStateContractsCanUseTheirUnassignedPublicLand() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "chunk claim " + tree.nation() + " " + keys(0, 2));
        run(alice, "chunk assignstate " + tree.nation() + " " + key(1) + " " + tree.state());
        run(alice, "contract create " + tree.nation() + " NationalRoad Work " + keys(0, 2));
        assertEquals(List.of(key(0), key(1)), data.contracts.get(latestContract()).chunks);
        run(alice, "contract cancel " + latestContract() + " Done");
        assertThrows(UserError.class, () -> run(alice, "contract create " + tree.state() + " Bad Work " + key(0)));
        assertThrows(UserError.class, () -> run(alice, "contract create " + tree.city() + " Bad Work " + key(1)));
        run(alice, "contract create " + tree.state() + " StateRoad Work " + key(1));
        assertEquals(tree.state(), data.contracts.get(latestContract()).governmentId);
        assertTrue(engine.commerce.claimLocked(key(1)));
    }

    @Test
    void treatiesTransferNationalOwnershipWithOptionalAllocationsAndAtomicPayments() {
        config.requirePeaceRatification = false;
        configure();
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        run(alice, "chunk claim " + alpha.nation() + " " + keys(0, 3));
        run(alice, "chunk permit " + key(1) + " Bob BREAK");
        engine.recordImprovement(key(1), 99);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 1_000L);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        String terms = key(0) + "=" + beta.nation() + "," + key(1) + "=" + beta.state() + "," + key(2) + "=" + beta.city();
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 10 0 " + terms);
        String treaty = latestDiplomacy();
        String before = claimsJson();
        economy.failNext = true;
        assertThrows(UserError.class, () -> run(dave, "diplomacy accept " + treaty));
        assertEquals(before, claimsJson());
        assertEquals(1_000, economy.balance("nation:" + alpha.nation()));
        assertEquals("WAR", engine.politics.relationStatus(alpha.nation(), beta.nation()));
        run(dave, "diplomacy accept " + treaty);
        assertEquals(3, engine.nationClaims(beta.nation()).size());
        assertTrue(engine.nationClaims(alpha.nation()).isEmpty());
        assertNull(data.claims.get(key(0)).stateId);
        assertNull(data.claims.get(key(0)).cityId);
        assertEquals("nation:" + beta.nation(), data.claims.get(key(0)).ownerAccount);
        assertEquals(beta.state(), data.claims.get(key(1)).stateId);
        assertNull(data.claims.get(key(1)).cityId);
        assertEquals("state:" + beta.state(), data.claims.get(key(1)).ownerAccount);
        assertEquals(beta.city(), data.claims.get(key(2)).cityId);
        assertEquals("city:" + beta.city(), data.claims.get(key(2)).ownerAccount);
        assertEquals(99, data.claims.get(key(1)).improvements);
        assertTrue(data.claims.get(key(1)).permits.isEmpty());
        assertEquals(1_000, economy.balance("nation:" + beta.nation()));
        assertEquals(1, economy.batches.size());
        assertEquals(3, data.claims.size());
    }

    @Test
    void autoClaimRechecksNationalAuthorityEvenWhenMovementStaysInsideExistingLand() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(bob, "chunk autoclaim on");
        engine.autoClaim(bob);
        assertNull(data.claims.get(bob.chunkKey()).stateId);
        assertNull(data.claims.get(bob.chunkKey()).cityId);
        run(alice, "nation officer " + tree.nation() + " Bob remove");
        assertThrows(UserError.class, () -> engine.autoClaim(bob));
        assertThrows(UserError.class, () -> engine.autoClaim(at(bob, "minecraft:overworld", 1, 0)));
        assertEquals(1, data.claims.size());
    }

    @Test
    void formsReviewsAndReadableDetailsUseNationalScopeAndBindAllAllocations() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        run(alice, "chunk claim " + alpha.nation() + " here");
        String before = new Gson().toJson(data);
        FormBuilder claim = form(alice, "chunk claim <nation> <chunks>", Map.of());
        assertEquals(Set.of(alpha.nation()), choices(claim, "nation"));
        assertEquals("", claim.value("chunks"));
        FormBuilder states = form(alice, "chunk assignstate <nation> <chunks> <state_or_none>",
                Map.of("nation", alpha.nation(), "chunks", "here"));
        assertEquals(Set.of(alpha.state(), "none"), choices(states, "state_or_none"));
        FormBuilder cities = form(alice, "chunk assigncity <state> <chunks> <city_or_none>",
                Map.of("state", alpha.state(), "chunks", "here"));
        assertEquals(Set.of(alpha.city(), "none"), choices(cities, "city_or_none"));
        assertTrue(choices(form(bob, "chunk claim <nation> <chunks>", Map.of()), "nation").isEmpty());
        assertEquals(before, new Gson().toJson(data));
        GovernancePresentation ui = new GovernancePresentation(engine);
        ActionSelection selection = ActionSelection.form("statecraft:claims",
                "chunk assignstate <nation> <chunks> <state_or_none>",
                Map.of("nation", alpha.nation(), "chunks", "here", "state_or_none", alpha.state()));
        var first = ui.preview(alice, selection);
        assertEquals(before, new Gson().toJson(data));
        run(alice, selection.rendered());
        var second = ui.preview(alice, selection);
        assertNotEquals(first.fingerprint(), second.fingerprint());
        run(alice, "chunk assigncity " + alpha.state() + " here " + alpha.city());
        var third = ui.preview(alice, selection);
        assertNotEquals(second.fingerprint(), third.fingerprint());
        engine.transferProperty(alice.chunkKey(), alice.account());
        assertNotEquals(third.fingerprint(), ui.preview(alice, selection).fingerprint());
        var detail = ui.view(new UiContext(alice, UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.CLAIM, alice.chunkKey()))));
        assertTrue(detail.body().fallback().contains("AlphaNation"));
        assertFalse(detail.body().fallback().contains(alpha.nation()));
        assertFalse(detail.body().fallback().contains(beta.nation()));
        run(alice, "chunk assignstate " + alpha.nation() + " here none");
        detail = ui.view(new UiContext(alice, UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.CLAIM, alice.chunkKey()))));
        assertTrue(detail.body().fallback().contains("Unassigned"));
        assertTrue(detail.body().fallback().contains("Alice"));
    }

    @Test
    void allTerritoryFormsAcceptSixtyFourWorldEdgeKeysWithinTheFullCommandLimit() {
        Tree tree = tree(alice, "Alpha");
        config.claimFee = 25;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), 10_000L);
        List<String> keys = edgeKeys();
        String chunks = String.join(",", keys);
        assertEquals(64, keys.size());
        assertTrue(chunks.length() > 2048);
        GovernancePresentation ui = new GovernancePresentation(engine);
        List<ActionSelection> actions = List.of(
                ActionSelection.form("statecraft:claims", "chunk claim <nation> <chunks>",
                        Map.of("nation", tree.nation(), "chunks", chunks)),
                ActionSelection.form("statecraft:claims", "chunk assignstate <nation> <chunks> <state_or_none>",
                        Map.of("nation", tree.nation(), "chunks", chunks, "state_or_none", tree.state())),
                ActionSelection.form("statecraft:claims", "chunk assigncity <state> <chunks> <city_or_none>",
                        Map.of("state", tree.state(), "chunks", chunks, "city_or_none", tree.city())),
                ActionSelection.form("statecraft:claims", "chunk unclaim <nation> <chunks>",
                        Map.of("nation", tree.nation(), "chunks", chunks)));
        List<String> publicOwners = List.of("nation:" + tree.nation(), "state:" + tree.state(), "city:" + tree.city());
        for (int index = 0; index < actions.size(); index++) {
            ActionSelection action = actions.get(index);
            var field = form(alice, action.template(), action.values()).build().field("chunks").orElseThrow();
            assertEquals(FormSchema.MAX_VALUE_LENGTH, field.constraints().maxLength());
            assertEquals(chunks, field.value());
            assertTrue(field.constraints().error(chunks, field.label()).isEmpty());
            String command = action.rendered();
            assertTrue(command.length() <= CommandLine.MAX_LENGTH);
            String before = claimsJson();
            ui.preview(alice, action);
            assertEquals(before, claimsJson());
            run(alice, command);
            if (index < publicOwners.size()) {
                assertEquals(64, data.claims.size());
                for (Claim claim : data.claims.values()) assertEquals(publicOwners.get(index), claim.ownerAccount);
            }
        }
        assertTrue(data.claims.isEmpty());
        assertEquals(1, economy.attempts);
        assertEquals(1_600, economy.balance("nation:" + tree.nation()));
        assertEquals(8_400, economy.balance(alice.account()));
        assertEquals(0, economy.balance("state:" + tree.state()));
        assertEquals(0, economy.balance("city:" + tree.city()));
        var contractChunks = form(alice, "contract create <government> <title> <description> <chunks>",
                Map.of("government", tree.nation())).build().field("chunks").orElseThrow();
        assertEquals(2048, contractChunks.constraints().maxLength());
        String valid = actions.get(0).rendered();
        String oversized = valid + " ".repeat(CommandLine.MAX_LENGTH - valid.length() + 1);
        assertThrows(UserError.class, () -> run(alice, oversized));
        assertTrue(data.claims.isEmpty());
        assertEquals(1, economy.attempts);
    }

    @Test
    void abbreviatedAllocationPreviewsStillFingerprintTheLastOfSixtyFourClaims() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        List<String> keys = edgeKeys();
        String chunks = String.join(",", keys);
        run(alice, "chunk claim " + tree.nation() + " " + chunks);
        GovernancePresentation ui = new GovernancePresentation(engine);
        ActionSelection allocation = ActionSelection.form("statecraft:claims",
                "chunk assignstate <nation> <chunks> <state_or_none>",
                Map.of("nation", tree.nation(), "chunks", chunks, "state_or_none", tree.state()));
        ActionPreview initial = ui.preview(alice, allocation);
        String readable = initial.lines().stream().filter(line -> line.label().key().endsWith(".allocation_result"))
                .findFirst().orElseThrow().value().fallback();
        String last = keys.get(keys.size() - 1);
        assertTrue(readable.endsWith("..."));
        assertFalse(readable.contains(GovernancePresentation.chunkName(last)));
        engine.transferProperty(last, bob.account());
        ActionPreview privateOwner = ui.preview(alice, allocation);
        assertEquals(initial.lines(), privateOwner.lines());
        assertNotEquals(initial.fingerprint(), privateOwner.fingerprint());
        run(alice, "chunk assignstate " + tree.nation() + " " + last + " " + tree.state());
        ActionPreview assigned = ui.preview(alice, allocation);
        assertEquals(privateOwner.lines(), assigned.lines());
        assertNotEquals(privateOwner.fingerprint(), assigned.fingerprint());
        run(alice, allocation.rendered());
        assertEquals(64, engine.stateClaims(tree.state()).size());
        assertEquals(bob.account(), data.claims.get(last).ownerAccount);
    }

    @Test
    void territoryReviewTranslationMetadataMatchesEveryNewCommand() throws Exception {
        Tree tree = tree(alice, "Alpha");
        GovernancePresentation ui = new GovernancePresentation(engine);
        List<ActionPreview> previews = new ArrayList<>();
        String claim = "chunk claim " + tree.nation() + " here";
        String state = "chunk assignstate " + tree.nation() + " here " + tree.state();
        String city = "chunk assigncity " + tree.state() + " here " + tree.city();
        for (String command : List.of(claim, state, city)) {
            previews.add(ui.preview(alice, ActionSelection.raw("statecraft:claims", command)));
            run(alice, command);
        }
        previews.add(ui.preview(alice, ActionSelection.raw("statecraft:claims", "chunk unclaim " + tree.nation() + " here")));
        previews.add(ui.preview(alice, ActionSelection.raw("statecraft:claims", "chunk autoclaim on")));
        previews.add(ui.preview(operator, ActionSelection.raw("statecraft:admin", "admin reassign here " + tree.nation())));
        Path dictionary = Path.of("tools", "translations", "governance.json");
        if (!Files.exists(dictionary)) dictionary = Path.of("..", "tools", "translations", "governance.json");
        var translations = new Gson().fromJson(Files.readString(dictionary), com.google.gson.JsonObject.class);
        List<UiText> texts = new ArrayList<>();
        for (ActionPreview preview : previews) {
            texts.add(preview.title());
            texts.add(preview.warning());
            preview.lines().forEach(line -> { texts.add(line.label()); texts.add(line.value()); });
        }
        for (UiText text : texts) {
            if (text.key().isEmpty()) continue;
            assertTrue(translations.has(text.key()), text.key());
            assertEquals(text.fallback(), String.format(Locale.ROOT, translations.get(text.key()).getAsString(),
                    text.arguments().toArray()), text.key());
        }
    }

    private FormBuilder form(Actor actor, String command, Map<String, String> values) {
        FormContext context = new FormContext(actor, "statecraft:claims", command, values, FormQuery.INITIAL);
        FormBuilder builder = new FormBuilder(context);
        new GovernanceForms(engine).describe(context, builder);
        return builder;
    }

    private Set<String> choices(FormBuilder form, String field) {
        return form.choices(field).stream().map(FormChoice::value).collect(Collectors.toSet());
    }

    private String claimsJson() {
        return new Gson().toJson(data.claims);
    }

    private static List<List<String>> permutations(List<String> values) {
        if (values.isEmpty()) return List.of(List.<String>of());
        List<List<String>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            List<String> rest = new ArrayList<>(values);
            String first = rest.remove(index);
            for (List<String> tail : permutations(rest)) {
                List<String> order = new ArrayList<>(List.of(first));
                order.addAll(tail);
                result.add(List.copyOf(order));
            }
        }
        return result;
    }

    private static List<String> edgeKeys() {
        List<String> result = new ArrayList<>();
        for (int z = 1_874_993; z <= 1_875_000; z++)
            for (int x = 1_874_993; x <= 1_875_000; x++)
                result.add("minecraft:overworld|" + x + "|" + z);
        return List.copyOf(result);
    }

    private static String key(int x) {
        return "minecraft:overworld|" + x + "|0";
    }

    private static String keys(int from, int end) {
        return IntStream.range(from, end).mapToObj(GovernanceTerritoryTest::key).collect(Collectors.joining(","));
    }
}
