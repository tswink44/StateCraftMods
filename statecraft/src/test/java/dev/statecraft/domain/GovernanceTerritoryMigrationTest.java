package dev.statecraft.domain;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceTerritoryMigrationTest extends DomainFixture {
    private final Gson gson = new Gson();

    @Test
    void realCityOnlyJsonMigratesPrivateAndPublicTitlesPermitsImprovementsAndValuesLosslessly() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "city setting " + tree.city() + " baseChunkValue 123456");
        JsonObject root = gson.toJsonTree(data).getAsJsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("claims", JsonParser.parseString("""
                {
                  "minecraft:overworld|0|0": {
                    "key": "minecraft:overworld|0|0",
                    "cityId": "%s",
                    "ownerAccount": "city:%s",
                    "improvements": 72,
                    "claimedAt": 123456,
                    "permits": { "%s": ["BREAK"] }
                  },
                  "minecraft:overworld|1|0": {
                    "key": "minecraft:overworld|1|0",
                    "cityId": "%s",
                    "ownerAccount": "player:%s",
                    "improvements": 3456,
                    "claimedAt": 234567,
                    "permits": { "%s": ["BREAK", "PLACE"] }
                  }
                }
                """.formatted(tree.city(), tree.city(), bob.id(), tree.city(), bob.id(), alice.id())));
        GovernanceData loaded = gson.fromJson(root, GovernanceData.class);
        assertNull(loaded.claims.get("minecraft:overworld|0|0").nationId);
        AtomicInteger dirty = new AtomicInteger();
        GovernanceEngine restored = new GovernanceEngine(loaded, config, EconomyAccess.UNAVAILABLE, time::get, dirty::incrementAndGet);
        assertEquals(1, dirty.get());
        assertEquals(GovernanceData.CURRENT_SCHEMA, loaded.schemaVersion);
        for (GovernanceData.Claim claim : loaded.claims.values()) {
            assertEquals(tree.nation(), claim.nationId);
            assertEquals(tree.state(), claim.stateId);
            assertEquals(tree.city(), claim.cityId);
        }
        var publicClaim = loaded.claims.get("minecraft:overworld|0|0");
        assertEquals("city:" + tree.city(), publicClaim.ownerAccount);
        assertEquals(72, publicClaim.improvements);
        assertEquals(123456, publicClaim.claimedAt);
        assertEquals(Map.of(bob.id().toString(), Set.of("BREAK")), publicClaim.permits);
        var privateClaim = loaded.claims.get("minecraft:overworld|1|0");
        assertEquals(bob.account(), privateClaim.ownerAccount);
        assertEquals(3456, privateClaim.improvements);
        assertEquals(234567, privateClaim.claimedAt);
        assertEquals(Map.of(alice.id().toString(), Set.of("BREAK", "PLACE")), privateClaim.permits);
        assertTrue(restored.mayAct(alice, privateClaim.key, AccessAction.PLACE, null));
        assertEquals("123456", restored.government(tree.city()).orElseThrow().settings().get("baseChunkValue"));
        assertEquals(root.getAsJsonObject("governments"), gson.toJsonTree(loaded.governments));
        assertTrue(restored.validationIssues().isEmpty());
        String saved = gson.toJson(loaded);
        GovernanceData reloaded = gson.fromJson(saved, GovernanceData.class);
        AtomicInteger secondDirty = new AtomicInteger();
        GovernanceEngine again = new GovernanceEngine(reloaded, config, EconomyAccess.UNAVAILABLE, time::get, secondDirty::incrementAndGet);
        assertEquals(saved, gson.toJson(reloaded));
        assertEquals(0, secondDirty.get());
        assertEquals(restored.claims(), again.claims());
    }

    @Test
    void migrationPreservesAwardEscrowPrivateCollateralAndAllFinancialLocksWithoutPayments() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String publicKey = claim(alice, tree, 0, 0);
        String privateKey = claim(alice, tree, 1, 0);
        engine.transferProperty(privateKey, bob.account());
        run(bob, "chunk permit " + privateKey + " Alice BREAK");
        engine.recordImprovement(publicKey, 73);
        engine.recordImprovement(privateKey, 91);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 1_000L);
        run(alice, "contract create " + tree.nation() + " Bridge Work " + publicKey);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 5 Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        economy.encumbered.add(privateKey);
        String obligation = gson.toJson(data.contracts.get(contract));
        Map<String, Long> balances = Map.copyOf(economy.balances);
        int attempts = economy.attempts;
        GovernanceData loaded = gson.fromJson(legacyJson(), GovernanceData.class);
        GovernanceEngine restored = new GovernanceEngine(loaded, config, economy, time::get);
        assertEquals(obligation, gson.toJson(loaded.contracts.get(contract)));
        assertEquals("AWARDED", loaded.contracts.get(contract).status);
        assertEquals(500, loaded.contracts.get(contract).escrowCents);
        assertEquals(balances, economy.balances);
        assertEquals(attempts, economy.attempts);
        assertEquals(bob.account(), loaded.claims.get(privateKey).ownerAccount);
        assertEquals(Map.of(alice.id().toString(), Set.of("BREAK")), loaded.claims.get(privateKey).permits);
        assertEquals(91, loaded.claims.get(privateKey).improvements);
        assertEquals(73, loaded.claims.get(publicKey).improvements);
        assertTrue(restored.commerce.claimLocked(publicKey));
        assertTrue(restored.validationIssues().isEmpty(), restored.validationIssues().toString());
        String titles = gson.toJson(loaded.claims);
        assertThrows(UserError.class, () -> restored.execute(alice, "chunk assignstate " + tree.nation()
                + " " + publicKey + "," + privateKey + " none"));
        assertThrows(UserError.class, () -> restored.execute(operator, "admin unclaim " + privateKey));
        assertEquals(titles, gson.toJson(loaded.claims));
        assertEquals(balances, economy.balances);
        assertEquals(attempts, economy.attempts);
    }

    @Test
    void legacyCityTreatyTermsGainExplicitNationalSnapshotsAndStillSettleIdentically() {
        config.requirePeaceRatification = false;
        configure();
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        engine.recordImprovement(key, 49);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 1_000L);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 10 0 " + key + "=" + beta.city() + " Peace");
        String treaty = latestDiplomacy();
        long at = data.claims.get(key).claimedAt;
        GovernanceData loaded = gson.fromJson(legacyJson(), GovernanceData.class);
        assertNull(loaded.diplomacy.get(treaty).chunks.get(0).fromNation);
        GovernanceEngine restored = new GovernanceEngine(loaded, config, economy, time::get);
        var proposal = loaded.diplomacy.get(treaty);
        var term = proposal.chunks.get(0);
        assertEquals(alpha.nation(), term.fromNation);
        assertEquals(alpha.state(), term.fromState);
        assertEquals(alpha.city(), term.fromCity);
        assertEquals(beta.nation(), term.toNation);
        assertEquals(beta.state(), term.toState);
        assertEquals(beta.city(), term.toCity);
        assertEquals(1_000, proposal.offeredCents);
        assertEquals("Peace", proposal.message);
        assertEquals("PROPOSED", proposal.status);
        assertEquals(0, economy.attempts);
        assertTrue(restored.validationIssues().isEmpty());
        restored.execute(dave, "diplomacy accept " + treaty);
        var claim = restored.claim(key).orElseThrow();
        assertEquals(beta.nation(), claim.nationId());
        assertEquals(beta.state(), claim.stateId());
        assertEquals(beta.city(), claim.cityId());
        assertEquals("city:" + beta.city(), claim.ownerAccount());
        assertEquals(49, claim.improvements());
        assertEquals(at, claim.claimedAt());
        assertEquals(1_000, economy.balance("nation:" + beta.nation()));
        assertEquals(1, economy.batches.size());
    }

    @Test
    void invalidLegacyCityChainsNeverGuessNationalOwnershipOrDiscardTitles() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String key = claim(alice, tree, 0, 0);
        engine.transferProperty(key, bob.account());
        run(bob, "chunk permit " + key + " Alice BREAK");
        String legacy = legacyJson();
        List<Consumer<GovernanceData>> corruptions = List.of(
                loaded -> loaded.claims.get(key).cityId = UUID.randomUUID().toString(),
                loaded -> loaded.claims.get(key).cityId = tree.state(),
                loaded -> loaded.governments.get(tree.city()).id = UUID.randomUUID().toString(),
                loaded -> loaded.governments.get(tree.state()).parentId = tree.city(),
                loaded -> loaded.governments.get(tree.nation()).parentId = tree.city());
        for (Consumer<GovernanceData> corrupt : corruptions) {
            GovernanceData loaded = gson.fromJson(legacy, GovernanceData.class);
            corrupt.accept(loaded);
            String title = gson.toJson(loaded.claims.get(key));
            GovernanceEngine restored = new GovernanceEngine(loaded, config, EconomyAccess.UNAVAILABLE, time::get);
            assertEquals(title, gson.toJson(loaded.claims.get(key)));
            assertNull(loaded.claims.get(key).nationId);
            assertNull(loaded.claims.get(key).stateId);
            assertFalse(restored.validationIssues().isEmpty());
            assertTrue(restored.claim(key).isEmpty());
            assertFalse(restored.mayAct(bob, key, AccessAction.BREAK, null));
            assertTrue(loaded.claims.containsKey(key));
        }
    }

    @Test
    void schemaTwoDoesNotGuessMissingNationOrStateEvenIfTheCityStillExists() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        for (String field : List.of("nationId", "stateId")) {
            JsonObject root = gson.toJsonTree(data).getAsJsonObject();
            root.getAsJsonObject("claims").getAsJsonObject(key).remove(field);
            GovernanceData loaded = gson.fromJson(root, GovernanceData.class);
            String before = gson.toJson(loaded);
            GovernanceEngine restored = new GovernanceEngine(loaded, config, EconomyAccess.UNAVAILABLE, time::get);
            assertEquals(before, gson.toJson(loaded));
            assertFalse(restored.validationIssues().isEmpty());
            assertThrows(UserError.class, () -> restored.execute(operator, "chunk claim " + tree.nation() + " minecraft:overworld|1|0"));
            restored.execute(operator, "admin repair apply");
            assertTrue(loaded.claims.containsKey(key));
            assertTrue(restored.claim(key).isEmpty());
            restored.execute(operator, "admin reassign " + key + " " + tree.nation());
            assertTrue(restored.validationIssues().isEmpty());
            assertEquals(tree.nation(), loaded.claims.get(key).nationId);
            assertNull(loaded.claims.get(key).stateId);
            assertNull(loaded.claims.get(key).cityId);
        }
    }

    @Test
    void absentLegacyVersionMigratesButUnsupportedVersionsAndConflictingFieldsAreNeverReset() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        JsonObject root = JsonParser.parseString(legacyJson()).getAsJsonObject();
        root.remove("schemaVersion");
        GovernanceData loaded = gson.fromJson(root, GovernanceData.class);
        GovernanceEngine restored = new GovernanceEngine(loaded, config, EconomyAccess.UNAVAILABLE, time::get);
        assertEquals(tree.nation(), restored.claim(key).orElseThrow().nationId());
        for (int version : List.of(0, 3, 99)) {
            GovernanceData unsupported = gson.fromJson(legacyJson(), GovernanceData.class);
            unsupported.schemaVersion = version;
            String saved = gson.toJson(unsupported);
            assertThrows(UserError.class, () -> new GovernanceEngine(unsupported, config, economy, time::get));
            assertEquals(saved, gson.toJson(unsupported));
        }
        GovernanceData conflicting = gson.fromJson(legacyJson(), GovernanceData.class);
        conflicting.claims.get(key).stateId = tree.nation();
        String title = gson.toJson(conflicting.claims.get(key));
        GovernanceEngine invalid = new GovernanceEngine(conflicting, config, EconomyAccess.UNAVAILABLE, time::get);
        assertEquals(title, gson.toJson(conflicting.claims.get(key)));
        assertFalse(invalid.validationIssues().isEmpty());
    }

    @Test
    void malformedDescendantsBlockCascadeWithARepairErrorAndPreserveNationalLand() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        GovernanceData loaded = gson.fromJson(gson.toJson(data), GovernanceData.class);
        loaded.governments.get(tree.city()).kind = null;
        String titles = gson.toJson(loaded.claims);
        GovernanceEngine restored = new GovernanceEngine(loaded, config, EconomyAccess.UNAVAILABLE, time::get);
        assertFalse(restored.validationIssues().isEmpty());
        UserError error = assertThrows(UserError.class,
                () -> restored.execute(operator, "admin delete nation " + tree.nation() + " cascade"));
        assertTrue(error.getMessage().contains("Repair"));
        assertEquals(titles, gson.toJson(loaded.claims));
        assertEquals(tree.nation(), loaded.claims.get(key).nationId);
        assertEquals(3, loaded.governments.size());
    }

    @Test
    void aRetainedNullClaimEntryIsProtectedAndDiagnosedAsInvalidRatherThanWilderness() {
        Tree tree = tree(alice, "Alpha");
        claim(alice, tree, 0, 0);
        String broken = "minecraft:overworld|1|0";
        data.claims.put(broken, null);
        GovernanceEngine restored = new GovernanceEngine(data, config, EconomyAccess.UNAVAILABLE, time::get);
        assertFalse(restored.validationIssues().isEmpty());
        assertFalse(restored.mayAct(bob, broken, AccessAction.PLACE, null));
        assertFalse(restored.allowsExplosion(broken));
        assertTrue(restored.execute(operator, "chunk info " + broken).contains("invalid claim"));
        assertTrue(restored.execute(operator, "chunk map 1").contains("?"));
        assertTrue(data.claims.containsKey(broken));
        assertNull(data.claims.get(broken));
    }

    private String legacyJson() {
        JsonObject root = gson.toJsonTree(data).getAsJsonObject();
        root.addProperty("schemaVersion", 1);
        root.getAsJsonObject("claims").entrySet().forEach(entry -> {
            entry.getValue().getAsJsonObject().remove("nationId");
            entry.getValue().getAsJsonObject().remove("stateId");
        });
        root.getAsJsonObject("diplomacy").entrySet().forEach(entry ->
                entry.getValue().getAsJsonObject().getAsJsonArray("chunks").forEach(element -> {
                    JsonObject term = element.getAsJsonObject();
                    for (String field : List.of("fromNation", "fromState", "toNation", "toState")) term.remove(field);
                }));
        return gson.toJson(root);
    }
}
