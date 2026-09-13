package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GovernancePersistenceTest extends DomainFixture {
    @Test
    void gsonRoundTripPreservesMembershipTitlesMailBallotsAndShareReservations() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String key = claim(alice, tree, 0, 0);
        engine.transferProperty(key, bob.account());
        run(bob, "chunk permit " + key + " Alice BREAK,PLACE");
        engine.recordImprovement(key, 77);
        String company = company(alice, "Builders");
        engine.reserveShares(company, alice.id(), "persisted-stock", 6_000);
        engine.settleShares("persisted-stock", bob.id(), 1_000);
        run(alice, "mail send Bob Persisted \"Saved message.\"");
        run(alice, "election candidate " + tree.nation());
        run(operator, "admin bypass on");
        Gson gson = new Gson();
        GovernanceData restoredData = gson.fromJson(gson.toJson(data), GovernanceData.class);
        GovernanceEngine restored = new GovernanceEngine(restoredData, config, EconomyAccess.UNAVAILABLE, time::get);
        assertEquals(tree.nation(), restored.nationOf(bob.id()).orElseThrow());
        assertEquals(bob.account(), restored.claim(key).orElseThrow().ownerAccount());
        assertEquals(77, restored.claim(key).orElseThrow().improvements());
        assertTrue(restored.mayAct(alice, key, AccessAction.BREAK, null));
        assertEquals(9_000, restored.sharesOf(company, alice.id()));
        assertEquals(4_000, restored.availableShares(company, alice.id()));
        restored.settleShares("persisted-stock", cara.id(), 2_000);
        assertEquals(3_000, restoredData.shareReservations.get("persisted-stock").quantity);
        assertEquals(4_000, restored.availableShares(company, alice.id()));
        assertEquals(10_000, restored.company(company).orElseThrow().shares().values().stream().mapToLong(Long::longValue).sum());
        assertEquals("Saved message.", restoredData.players.get(bob.id().toString()).inbox.get(0).body);
        assertTrue(restoredData.elections.get(tree.nation()).candidates.contains(alice.id().toString()));
        assertFalse(restored.bypassEnabled(operator.id()));
        assertFalse(restored.mayAct(operator(operator, false), key, AccessAction.BREAK, null));
        assertThrows(UserError.class, () -> restored.execute(operator, "admin delete company " + company));
    }

    @Test
    void omittedJsonFieldsUsePojoDefaultsWithoutResettingExistingSections() {
        GovernanceData loaded = new Gson().fromJson("{\"schemaVersion\":1}", GovernanceData.class);
        GovernanceEngine restored = new GovernanceEngine(loaded, config, null, time::get);
        restored.execute(alice, "nation create FreshNation");
        assertNotNull(loaded.players);
        assertNotNull(loaded.governments);
        assertNotNull(loaded.claims);
        assertNotNull(loaded.history);
        assertNotNull(loaded.shareReservations);
        assertEquals(1, loaded.governments.size());
        assertTrue(restored.execute(operator, "admin audit").contains("No integrity issues"));
    }

    @Test
    void explicitNullStructureFailsConstructionWithoutDiscardingOtherData() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        var governments = data.governments;
        var claims = data.claims;
        data.players = null;
        UserError error = assertThrows(UserError.class,
                () -> new GovernanceEngine(data, config, economy, time::get));
        assertTrue(error.getMessage().contains("players"));
        assertNull(data.players);
        assertSame(governments, data.governments);
        assertSame(claims, data.claims);
        assertTrue(data.claims.containsKey(key));
        assertFalse(data.economySeen);
    }

    @Test
    void explicitNullShareLedgerIsNeverReplacedWithAnEmptyLedger() {
        String company = company(alice, "Builders");
        data.companies.get(company).shares = null;
        assertThrows(UserError.class, () -> new GovernanceEngine(data, config, null, time::get));
        assertNull(data.companies.get(company).shares);
        assertEquals(10_000, data.companies.get(company).totalShares);
    }

    @Test
    void orphanLoadsArePreservedAndPausedUntilExplicitRepairSucceeds() {
        Tree tree = tree(alice, "Alpha");
        String validKey = claim(alice, tree, 0, 0);
        GovernanceData.Claim orphan = new GovernanceData.Claim();
        orphan.key = "minecraft:overworld|50|50";
        orphan.cityId = java.util.UUID.randomUUID().toString();
        orphan.ownerAccount = "city:" + orphan.cityId;
        data.claims.put(orphan.key, orphan);
        long originalTick = data.lastTick;
        GovernanceEngine restored = new GovernanceEngine(data, config, null, time::get);
        assertSame(orphan, data.claims.get(orphan.key));
        assertFalse(restored.validationIssues().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> restored.validationIssues().clear());
        time.addAndGet(100_000);
        restored.tick(time.get());
        assertEquals(originalTick, data.lastTick);
        assertTrue(restored.execute(alice, "info").contains("REPAIR REQUIRED"));
        assertThrows(UserError.class, () -> restored.execute(alice, "nation rename " + tree.nation() + " Renamed"));
        assertFalse(restored.mayAct(alice, validKey, AccessAction.BREAK, null));
        restored.execute(operator, "admin repair apply");
        assertTrue(data.claims.containsKey(orphan.key));
        assertFalse(restored.validationIssues().isEmpty());
        restored.execute(operator, "admin reassign " + orphan.key + " " + tree.nation());
        assertEquals(tree.nation(), orphan.nationId);
        assertNull(orphan.stateId);
        assertNull(orphan.cityId);
        assertEquals("nation:" + tree.nation(), orphan.ownerAccount);
        assertTrue(data.claims.containsKey(validKey));
        assertTrue(restored.validationIssues().isEmpty());
        assertTrue(restored.mayAct(alice, validKey, AccessAction.BREAK, null));
        restored.tick(time.get());
        assertEquals(time.get(), data.lastTick);
        assertEquals(3, data.governments.size());
    }

    @Test
    void NormalExpiryAndReducedLimitsDoNotQuarantineOtherwiseConsistentLoads() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "nation invite " + tree.nation() + " Cara");
        engine.mail(alice.id(), "First", "Message one.");
        engine.mail(alice.id(), "Second", "Message two.");
        config.maxMembersPerNation = 1;
        config.maxMail = 1;
        time.addAndGet(3_001);
        GovernanceEngine restored = new GovernanceEngine(data, config, null, time::get);
        assertTrue(restored.validationIssues().isEmpty());
        assertEquals(2, data.players.get(alice.id().toString()).inbox.size());
        assertFalse(data.invitations.isEmpty());
        restored.tick(time.get());
        assertTrue(data.invitations.isEmpty());
        assertEquals(1, data.players.get(alice.id().toString()).inbox.size());
        assertEquals(2, restored.government(tree.nation()).orElseThrow().members().size());
    }

    @Test
    void unsupportedGovernanceSchemaFailsWithoutResettingIt() {
        data.schemaVersion = 99;
        assertThrows(UserError.class, () -> new GovernanceEngine(data, config, null, time::get));
        assertEquals(99, data.schemaVersion);
        assertEquals(6, data.players.size());
    }

    @Test
    void quarantinePreservesReadOnlyAndIdempotentReservationChecksForEconomyStartup() {
        String company = company(alice, "Builders");
        engine.reserveShares(company, alice.id(), "stock:existing", 100);
        GovernanceData.Claim orphan = new GovernanceData.Claim();
        orphan.key = "minecraft:overworld|50|50";
        orphan.cityId = java.util.UUID.randomUUID().toString();
        orphan.ownerAccount = "city:" + orphan.cityId;
        data.claims.put(orphan.key, orphan);
        GovernanceEngine restored = new GovernanceEngine(data, config, null, time::get);
        assertFalse(restored.validationIssues().isEmpty());
        assertEquals(9_900, restored.availableShares(company, alice.id()));
        assertDoesNotThrow(() -> restored.reserveShares(company, alice.id(), "stock:existing", 100));
        assertThrows(UserError.class, () -> restored.reserveShares(company, alice.id(), "stock:new", 100));
        assertThrows(UserError.class, () -> restored.settleShares("stock:existing", bob.id(), 1));
        assertThrows(UserError.class, () -> restored.transferShares(company, alice.id(), bob.id(), 1));
        assertEquals(10_000, restored.sharesOf(company, alice.id()));
        assertEquals(100, data.shareReservations.get("stock:existing").quantity);
    }

    @Test
    void auditAndRepairDoNotGuessMissingShareLedgersOrDeleteEscrow() {
        String company = company(alice, "Builders");
        data.companies.get(company).shares.clear();
        GovernanceData.Contract broken = new GovernanceData.Contract();
        broken.id = java.util.UUID.randomUUID().toString();
        broken.governmentId = java.util.UUID.randomUUID().toString();
        broken.status = "AWARDED";
        broken.escrowCents = 1_000;
        data.contracts.put(broken.id, broken);
        String audit = run(operator, "admin audit");
        assertTrue(audit.contains("Share ledger"));
        run(operator, "admin repair apply");
        assertTrue(data.companies.get(company).shares.isEmpty());
        assertEquals(1_000, data.contracts.get(broken.id).escrowCents);
        assertEquals("AWARDED", data.contracts.get(broken.id).status);
        assertThrows(UserError.class, () -> engine.reserveShares(company, alice.id(), "bad", 1));
    }

    @Test
    void invalidPoliciesAndBallotWeightsDoNotCrashScheduledProcessing() {
        Tree tree = tree(alice, "Alpha");
        data.governments.get(tree.nation()).settings.put(null, "false");
        data.governments.get(tree.nation()).settings.put("pvp", null);
        assertEquals("false", government(tree.city()).settings().get("pvp"));
        run(alice, "bill propose " + tree.nation() + " pvp true Combat Text");
        String bill = latestBill();
        data.bills.get(bill).policy = null;
        String company = company(alice, "Builders");
        run(alice, "company propose " + company + " roleplay - Decision Text");
        String proposal = latestCompanyProposal();
        data.companyProposals.get(proposal).electorate.put(bob.id().toString(), null);
        assertDoesNotThrow(() -> advance(1_000));
        assertEquals("FAILED", data.bills.get(bill).status);
        assertEquals("FAILED", data.companyProposals.get(proposal).status);
        assertTrue(run(operator, "admin audit").contains("Invalid policy"));
    }

    @Test
    void unknownWorkflowStatesRemainLockedAndAreNeverPrunedAsCompleted() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        GovernanceData.Contract unknown = new GovernanceData.Contract();
        unknown.id = java.util.UUID.randomUUID().toString();
        unknown.governmentId = tree.nation();
        unknown.status = "UNRECOGNIZED";
        unknown.chunks.add(key);
        data.contracts.put(unknown.id, unknown);
        advance(100_000);
        assertTrue(data.contracts.containsKey(unknown.id));
        assertFalse(engine.maySellProperty(alice.id(), key));
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + key));
        assertThrows(UserError.class, () -> run(operator, "admin delete nation " + tree.nation() + " cascade"));
        assertTrue(run(operator, "admin audit").contains("Invalid contract status"));
    }
}
