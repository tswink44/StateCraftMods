package dev.statecraft.domain;

import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GovernancePoliticsTest extends DomainFixture {
    @Test
    void scheduledElectionFreezesEligibilityAndTransfersLeadershipDeterministically() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "election candidate " + tree.nation());
        run(bob, "election candidate " + tree.nation());
        advance(10_000);
        assertTrue(data.elections.get(tree.nation()).voting);
        run(dave, "nation join " + tree.nation());
        assertThrows(UserError.class, () -> run(dave, "election vote " + tree.nation() + " Bob"));
        run(alice, "election vote " + tree.nation() + " Bob");
        run(bob, "election vote " + tree.nation() + " Bob");
        run(cara, "election vote " + tree.nation() + " Alice");
        assertThrows(UserError.class, () -> run(alice, "election vote " + tree.nation() + " Alice"));
        advance(2_000);
        assertEquals(bob.id(), government(tree.nation()).leader());
        assertFalse(data.elections.get(tree.nation()).voting);
        assertTrue(engine.mayManageGovernment(bob.id(), tree.nation()));
        assertFalse(engine.mayManageGovernment(alice.id(), tree.nation()));
        assertTrue(run(alice, "election history " + tree.nation()).contains("Winner"));
    }

    @Test
    void tiedElectionUsesUuidOrderAndExpulsionCannotEraseCastVotes() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        join(dave, tree);
        run(bob, "election candidate " + tree.nation());
        run(cara, "election candidate " + tree.nation());
        advance(10_000);
        run(alice, "election vote " + tree.nation() + " Cara");
        run(dave, "election vote " + tree.nation() + " Bob");
        run(alice, "nation kick " + tree.nation() + " Dave");
        advance(2_000);
        assertEquals(bob.id(), government(tree.nation()).leader());
        assertTrue(data.elections.get(tree.nation()).history.stream().anyMatch(h -> h.text.contains("Ties use")));
    }

    @Test
    void candidateFeesRequireEconomyAndARejectedPaymentDoesNotRegister() {
        Tree tree = tree(alice, "Alpha");
        config.electionCandidateFee = 250;
        configure();
        assertThrows(UserError.class, () -> run(alice, "election candidate " + tree.nation()));
        assertTrue(data.elections.get(tree.nation()).candidates.isEmpty());
        useEconomy();
        assertThrows(UserError.class, () -> run(alice, "election candidate " + tree.nation()));
        assertTrue(data.elections.get(tree.nation()).candidates.isEmpty());
        economy.balances.put(alice.account(), 500L);
        run(alice, "election candidate " + tree.nation());
        assertEquals(250, economy.balance(alice.account()));
        assertEquals(250, economy.balance("nation:" + tree.nation()));
        assertThrows(UserError.class, () -> run(alice, "election candidate " + tree.nation()));
        run(alice, "election withdraw " + tree.nation());
        assertEquals(250, economy.balance(alice.account()));
    }

    @Test
    void emptyElectionsRetainIncumbentAndCatchupCannotLoopForever() {
        Tree tree = tree(alice, "Alpha");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            time.set(Long.MAX_VALUE);
            engine.tick(Long.MAX_VALUE);
            engine.tick(Long.MAX_VALUE);
        });
        assertEquals(alice.id(), government(tree.nation()).leader());
        assertTrue(data.elections.get(tree.nation()).scheduleExhausted);
        assertTrue(data.elections.get(tree.nation()).history.size() <= config.maxHistory);
    }

    @Test
    void registeredCandidatesWithoutVotesDoNotAcquireLeadershipByDefault() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(bob, "election candidate " + tree.nation());
        advance(12_000);
        assertEquals(alice.id(), government(tree.nation()).leader());
        assertTrue(data.elections.get(tree.nation()).history.stream().anyMatch(h -> h.text.contains("No valid ballots")));
    }

    @Test
    void billLifecycleEnforcesDebateVoteSignatureAndTypedSettings() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(bob, "bill propose " + tree.nation() + " salesTaxBps 500 Revenue \"Fund public works.\"");
        String bill = latestBill();
        assertEquals("DEBATE", data.bills.get(bill).status);
        assertThrows(UserError.class, () -> run(alice, "bill vote " + bill + " yes"));
        assertThrows(UserError.class, () -> run(alice, "bill sign " + bill));
        advance(1_000);
        assertThrows(UserError.class, () -> run(bob, "bill vote " + bill + " yes"));
        run(alice, "bill vote " + bill + " yes");
        assertThrows(UserError.class, () -> run(alice, "bill vote " + bill + " no"));
        advance(1_000);
        assertEquals("PASSED", data.bills.get(bill).status);
        assertEquals("0", government(tree.nation()).settings().get("salesTaxBps"));
        assertThrows(UserError.class, () -> run(bob, "bill sign " + bill));
        run(alice, "bill sign " + bill);
        assertEquals("ENACTED", data.bills.get(bill).status);
        assertEquals("500", government(tree.nation()).settings().get("salesTaxBps"));
        assertEquals("0", government(tree.city()).settings().get("salesTaxBps"));
        assertEquals(bill, data.laws.get(tree.nation()).get(0).id);
    }

    @Test
    void vetoCanBeOverriddenOnlyByASeparateSupermajorityBallot() {
        Tree tree = tree(alice, "Alpha");
        legislators(tree);
        run(bob, "bill propose " + tree.nation() + " foreignAccess true Access \"Permit visitors.\"");
        String bill = latestBill();
        advance(1_000);
        run(alice, "bill vote " + bill + " yes");
        run(bob, "bill vote " + bill + " yes");
        advance(1_000);
        run(alice, "bill veto " + bill + " \"Reconsider security.\"");
        assertEquals("VETOED", data.bills.get(bill).status);
        assertEquals("false", government(tree.nation()).settings().get("foreignAccess"));
        run(bob, "bill override " + bill);
        assertEquals("OVERRIDE_VOTING", data.bills.get(bill).status);
        assertTrue(data.bills.get(bill).votes.isEmpty());
        run(alice, "bill vote " + bill + " no");
        run(bob, "bill vote " + bill + " yes");
        run(cara, "bill vote " + bill + " yes");
        advance(1_000);
        assertEquals("ENACTED", data.bills.get(bill).status);
        assertEquals("true", government(tree.nation()).settings().get("foreignAccess"));
    }

    @Test
    void quorumAndUnsignedExpiryAreEnforcedAndLargeTicksResolveBoundedly() {
        Tree tree = tree(alice, "Alpha");
        legislators(tree);
        run(alice, "bill propose " + tree.nation() + " pvp true Combat \"A policy.\"");
        String lowQuorum = latestBill();
        advance(1_000);
        run(alice, "bill vote " + lowQuorum + " yes");
        advance(1_000);
        assertEquals("FAILED", data.bills.get(lowQuorum).status);
        run(alice, "bill propose " + tree.nation() + " pvp true Combat \"A policy.\"");
        String unsigned = latestBill();
        advance(1_000);
        run(alice, "bill vote " + unsigned + " yes");
        run(bob, "bill vote " + unsigned + " yes");
        advance(1_000);
        assertEquals("PASSED", data.bills.get(unsigned).status);
        advance(1_000);
        assertEquals("EXPIRED", data.bills.get(unsigned).status);
        assertEquals("false", government(tree.nation()).settings().get("pvp"));
        run(alice, "bill propose " + tree.nation() + " explosions true Blasts \"A policy.\"");
        String skipped = latestBill();
        advance(100_000);
        assertEquals("FAILED", data.bills.get(skipped).status);
    }

    @Test
    void amendmentsUseWholeElectorateThresholdAndRoleplayHasNoMechanicalSideEffect() {
        config.amendmentThresholdBps = 7_500;
        configure();
        Tree tree = tree(alice, "Alpha");
        legislators(tree);
        run(alice, "bill amend " + tree.nation() + " pvp true Constitution \"A constitutional change.\"");
        String amendment = latestBill();
        advance(1_000);
        run(alice, "bill vote " + amendment + " yes");
        run(bob, "bill vote " + amendment + " yes");
        run(cara, "bill vote " + amendment + " no");
        advance(1_000);
        assertEquals("FAILED", data.bills.get(amendment).status);
        run(alice, "bill propose " + tree.nation() + " roleplay - Holiday \"Celebrate builders annually.\"");
        String roleplay = latestBill();
        advance(1_000);
        run(alice, "bill vote " + roleplay + " yes");
        run(bob, "bill vote " + roleplay + " yes");
        advance(1_000);
        run(alice, "bill sign " + roleplay);
        assertTrue(data.laws.get(tree.nation()).get(0).roleplayOnly);
        assertEquals("false", government(tree.nation()).settings().get("pvp"));
        assertThrows(UserError.class, () -> run(alice, "bill propose " + tree.nation() + " arbitrary true Test Text"));
    }

    @Test
    void citizenLegislatureHasFrozenRollAndOperatorCannotInventVotes() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "nation setting " + tree.nation() + " citizenLegislature true");
        run(bob, "bill propose " + tree.nation() + " open false Membership \"Close direct joining.\"");
        String bill = latestBill();
        advance(1_000);
        run(cara, "nation join " + tree.nation());
        assertThrows(UserError.class, () -> run(cara, "bill vote " + bill + " yes"));
        assertThrows(UserError.class, () -> run(operator, "bill vote " + bill + " yes"));
        run(bob, "bill vote " + bill + " yes");
        advance(1_000);
        assertEquals("PASSED", data.bills.get(bill).status);
    }

    @Test
    void emergencyOverlayExpiresWithoutOverwritingPermanentLawAndHasCooldown() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "executive emergency " + tree.nation() + " foreignAccess true \"Open relief corridors.\"");
        assertEquals("true", government(tree.city()).settings().get("foreignAccess"));
        run(alice, "nation setting " + tree.nation() + " foreignAccess false");
        assertEquals("true", government(tree.city()).settings().get("foreignAccess"));
        assertThrows(UserError.class, () -> run(alice, "executive emergency " + tree.nation() + " pvp true Again"));
        advance(1_000);
        assertEquals("false", government(tree.city()).settings().get("foreignAccess"));
        assertThrows(UserError.class, () -> run(alice, "executive emergency " + tree.nation() + " pvp true Again"));
        advance(2_000);
        run(alice, "executive emergency " + tree.nation() + " pvp true \"Emergency defense.\"");
        run(alice, "executive rescind " + tree.nation());
        assertEquals("false", government(tree.nation()).settings().get("pvp"));
        assertThrows(UserError.class, () -> run(alice, "executive emergency " + tree.nation() + " pvp true Again"));
    }

    @Test
    void alliancesAffectPublicAccessAndWarCannotTargetAllies() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        assertFalse(engine.mayAct(dave, key, AccessAction.BREAK, null));
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        String proposal = latestDiplomacy();
        assertThrows(UserError.class, () -> run(alice, "diplomacy accept " + proposal));
        run(dave, "diplomacy accept " + proposal);
        assertTrue(engine.mayAct(dave, key, AccessAction.BREAK, null));
        run(alice, "nation setting " + alpha.nation() + " pvp true");
        assertFalse(engine.mayAct(dave, key, AccessAction.PVP, alice.id()));
        assertThrows(UserError.class, () -> run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation()));
        run(alice, "diplomacy break " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        assertTrue(engine.mayAct(dave, key, AccessAction.PVP, alice.id()));
        assertFalse(engine.mayAct(dave, key, AccessAction.BREAK, null));
        assertFalse(data.governments.get(beta.nation()).inbox.isEmpty());
    }

    @Test
    void peaceRequiresBothLegislaturesAndExecutesMonetaryTermsOnlyOnce() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        engine.recordImprovement(key, 12);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 10_000L);
        economy.balances.put("nation:" + beta.nation(), 1_000L);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 40 10 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        assertThrows(UserError.class, () -> run(operator, "admin delete city " + beta.city()));
        run(dave, "diplomacy accept " + treaty);
        assertEquals("AWAITING_RATIFICATION", data.diplomacy.get(treaty).status);
        assertEquals(0, economy.batches.size());
        assertEquals(alpha.city(), engine.claim(key).orElseThrow().cityId());
        assertThrows(UserError.class, () -> run(operator, "admin unclaim " + key));
        advance(1_000);
        run(alice, "diplomacy ratify " + treaty + " yes");
        run(dave, "diplomacy ratify " + treaty + " yes");
        advance(1_000);
        assertEquals("ENACTED", data.diplomacy.get(treaty).status);
        assertEquals(1, economy.batches.size());
        assertEquals(2, economy.batches.get(0).size());
        assertEquals(7_000, economy.balance("nation:" + alpha.nation()));
        assertEquals(4_000, economy.balance("nation:" + beta.nation()));
        assertEquals(beta.city(), engine.claim(key).orElseThrow().cityId());
        assertEquals("city:" + beta.city(), engine.claim(key).orElseThrow().ownerAccount());
        assertEquals(12, engine.claim(key).orElseThrow().improvements());
        assertThrows(UserError.class, () -> run(alice, "diplomacy execute " + treaty));
        assertThrows(UserError.class, () -> run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation()));
        assertFalse(engine.mayAct(dave, key, AccessAction.PVP, alice.id()));
        advance(3_000);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
    }

    @Test
    void failedPeacePaymentLeavesClaimsAndWarUnchangedAndMayBeRetried() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 100 0 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        run(dave, "diplomacy accept " + treaty);
        advance(1_000);
        run(alice, "diplomacy ratify " + treaty + " yes");
        run(dave, "diplomacy ratify " + treaty + " yes");
        advance(1_000);
        assertEquals("READY", data.diplomacy.get(treaty).status);
        assertEquals(alpha.city(), engine.claim(key).orElseThrow().cityId());
        assertTrue(engine.mayAct(dave, key, AccessAction.PVP, alice.id()));
        assertTrue(economy.batches.isEmpty());
        economy.balances.put("nation:" + alpha.nation(), 10_000L);
        run(dave, "diplomacy execute " + treaty);
        assertEquals("ENACTED", data.diplomacy.get(treaty).status);
        assertEquals(beta.city(), engine.claim(key).orElseThrow().cityId());
        assertEquals(1, economy.batches.size());
    }

    @Test
    void failedOrExpiredRatificationCannotEndWarOrTransferMoney() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy truce " + alpha.nation() + " " + beta.nation());
        String failed = latestDiplomacy();
        run(dave, "diplomacy accept " + failed);
        advance(1_000);
        run(alice, "diplomacy ratify " + failed + " yes");
        run(dave, "diplomacy ratify " + failed + " no");
        advance(1_000);
        assertEquals("FAILED", data.diplomacy.get(failed).status);
        assertTrue(engine.mayAct(dave, key, AccessAction.PVP, alice.id()));
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + key + "=" + beta.city());
        String expired = latestDiplomacy();
        advance(12_000);
        assertEquals("EXPIRED", data.diplomacy.get(expired).status);
        assertEquals(alpha.city(), engine.claim(key).orElseThrow().cityId());
        run(alice, "chunk unclaim " + key);
    }

    @Test
    void directPeaceWithoutLegislaturesStillValidatesAndPaysBeforeMutation() {
        config.requirePeaceRatification = false;
        configure();
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 1_000L);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 10 0 " + key + "=" + beta.city());
        String proposal = latestDiplomacy();
        economy.failNext = true;
        assertThrows(UserError.class, () -> run(dave, "diplomacy accept " + proposal));
        assertEquals("PROPOSED", data.diplomacy.get(proposal).status);
        assertEquals(alpha.city(), engine.claim(key).orElseThrow().cityId());
        assertEquals(1_000, economy.balance("nation:" + alpha.nation()));
        run(dave, "diplomacy accept " + proposal);
        assertEquals("ENACTED", data.diplomacy.get(proposal).status);
        assertEquals(1_000, economy.balance("nation:" + beta.nation()));
        assertTrue(data.bills.isEmpty());
    }

    @Test
    void treatyCannotSeizePrivateLandSplitCitiesOrRequireAbsentEconomy() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        String first = claim(alice, alpha, 0, 0);
        String bridge = claim(alice, alpha, 1, 0);
        claim(alice, alpha, 2, 0);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        assertThrows(UserError.class, () -> run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 1 0 -"));
        assertThrows(UserError.class, () -> run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + bridge + "=" + beta.city()));
        engine.transferProperty(first, bob.account());
        assertThrows(UserError.class, () -> run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + first + "=" + beta.city()));
        assertTrue(data.diplomacy.isEmpty());
        assertEquals(3, engine.claims().size());
    }

    @Test
    void legislatureLimitsAndPolicyOnlyModeAreEnforced() {
        Tree tree = tree(alice, "Alpha");
        config.maxActiveBillsPerNation = 1;
        config.requireLegislationForPolicy = true;
        configure();
        assertThrows(UserError.class, () -> run(alice, "nation setting " + tree.nation() + " pvp true"));
        run(alice, "nation setting " + tree.nation() + " open false");
        run(alice, "bill propose " + tree.nation() + " pvp true Combat \"A policy.\"");
        String bill = latestBill();
        assertThrows(UserError.class, () -> run(alice, "bill propose " + tree.nation() + " pvp false Pacifism \"A policy.\""));
        run(alice, "bill cancel " + bill);
        run(alice, "bill propose " + tree.nation() + " pvp false Pacifism \"A policy.\"");
    }

    private void legislators(Tree tree) {
        join(bob, tree);
        join(cara, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(alice, "nation officer " + tree.nation() + " Cara add");
    }
}
