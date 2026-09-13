package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import dev.statecraft.domain.GovernanceData.DiplomaticProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceReviewRegressionTest extends DomainFixture {
    @Test
    void reducedContractChunkLimitGrandfathersUnchangedAwardsForOfficialsAndOperators() {
        Tree tree = tree(alice, "Alpha");
        String first = claim(alice, tree, 0, 0);
        String second = claim(alice, tree, 1, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 2_000L);
        for (Actor reviewer : List.of(alice, operator)) {
            config.maxContractChunks = 2;
            configure();
            String contract = submittedContract(tree, first + "," + second);
            config.maxContractChunks = 1;
            configure();
            engine = new GovernanceEngine(data, config, economy, time::get);
            assertTrue(engine.validationIssues().isEmpty());
            run(reviewer, "contract complete " + contract);
            assertEquals("COMPLETED", data.contracts.get(contract).status);
            assertEquals(List.of(first, second), data.contracts.get(contract).chunks);
            assertEquals(0, data.contracts.get(contract).escrowCents);
            assertEquals(0, economy.balance("escrow:contract:" + contract));
            UserError limit = assertThrows(UserError.class, () -> run(alice,
                    "contract create " + tree.nation() + " Oversized Work " + first + "," + second));
            assertTrue(limit.getMessage().contains("number of contract chunks"));
        }
        assertEquals(2_000, economy.balance(bob.account()));
        assertEquals(0, economy.balance("nation:" + tree.nation()));
    }

    @Test
    void grandfatheredContractSettlementStillChecksTitlesReservationsAcceptedBidAndEscrow() {
        Tree tree = tree(alice, "Alpha");
        String first = claim(alice, tree, 0, 0);
        String second = claim(alice, tree, 1, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 1_000L);
        String contract = submittedContract(tree, first + "," + second);
        config.maxContractChunks = 1;
        configure();

        data.claims.get(first).ownerAccount = bob.account();
        assertThrows(UserError.class, () -> run(operator, "contract complete " + contract));
        data.claims.get(first).ownerAccount = "city:" + tree.city();
        economy.encumbered.add(second);
        assertThrows(UserError.class, () -> run(operator, "contract complete " + contract));
        economy.encumbered.remove(second);
        data.contracts.get(contract).payeeAccount = cara.account();
        assertThrows(UserError.class, () -> run(operator, "contract complete " + contract));
        data.contracts.get(contract).payeeAccount = bob.account();
        data.contracts.get(contract).bids.get(bob.id().toString()).cents = 1_001;
        assertThrows(UserError.class, () -> run(operator, "contract complete " + contract));
        data.contracts.get(contract).bids.get(bob.id().toString()).cents = 1_000;
        assertEquals("SUBMITTED", data.contracts.get(contract).status);
        assertEquals(1_000, data.contracts.get(contract).escrowCents);
        assertEquals(1_000, economy.balance("escrow:contract:" + contract));
        assertEquals(0, economy.balance(bob.account()));
        economy.balances.put("escrow:contract:" + contract, 0L);
        assertThrows(UserError.class, () -> run(operator, "contract complete " + contract));
        assertEquals("SUBMITTED", data.contracts.get(contract).status);
        assertEquals(1_000, data.contracts.get(contract).escrowCents);
        economy.balances.put("escrow:contract:" + contract, 1_000L);
        run(operator, "contract complete " + contract);
        assertEquals(1_000, economy.balance(bob.account()));
    }

    @Test
    void nationalOnlyLegislationPreservesAuthorizedLocalPoliciesAndIndependentTaxes() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(dave, "nation join " + tree.nation());
        run(alice, "nation officer " + tree.nation() + " Dave add");
        run(alice, "state leader " + tree.state() + " Bob");
        run(alice, "city leader " + tree.city() + " Cara");
        config.requireLegislationForPolicy = true;
        configure();

        assertThrows(UserError.class, () -> run(alice, "nation setting " + tree.nation() + " incomeTaxBps 500"));
        assertThrows(UserError.class, () -> run(bob, "nation setting " + tree.nation() + " incomeTaxBps 500"));
        assertThrows(UserError.class, () -> run(cara, "state setting " + tree.state() + " incomeTaxBps 100"));
        assertThrows(UserError.class, () -> run(dave, "city setting " + tree.city() + " incomeTaxBps 50"));
        assertThrows(UserError.class, () -> run(felix, "city setting " + tree.city() + " incomeTaxBps 50"));
        run(bob, "state setting " + tree.state() + " incomeTaxBps 100");
        run(cara, "city setting " + tree.city() + " incomeTaxBps 50");
        run(bob, "city setting " + tree.city() + " pvp true");
        run(alice, "nation setting " + tree.nation() + " open false");
        run(operator, "nation setting " + tree.nation() + " salesTaxBps 200");
        assertThrows(UserError.class, () -> run(bob,
                "bill propose " + tree.state() + " incomeTaxBps 100 LocalTax Text"));
        run(alice, "bill propose " + tree.nation() + " incomeTaxBps 500 NationalTax Text");
        String bill = latestBill();
        advance(config.debateDurationMillis);
        run(alice, "bill vote " + bill + " yes");
        run(dave, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        run(alice, "bill sign " + bill);
        assertEquals("500", government(tree.nation()).settings().get("incomeTaxBps"));
        assertEquals("100", government(tree.state()).settings().get("incomeTaxBps"));
        assertEquals("50", government(tree.city()).settings().get("incomeTaxBps"));
        run(bob, "state setting " + tree.state() + " incomeTaxBps inherit");
        run(cara, "city setting " + tree.city() + " incomeTaxBps inherit");
        assertEquals("0", government(tree.state()).settings().get("incomeTaxBps"));
        assertEquals("0", government(tree.city()).settings().get("incomeTaxBps"));
    }

    @Test
    void nationalLeaderCanRemoveAnOfficersLocalMembershipWithoutTouchingNationalOffice() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        assertTrue(engine.mayManageGovernment(alice.id(), tree.city()));
        run(alice, "city kick " + tree.city() + " Bob");
        assertNull(data.players.get(bob.id().toString()).cityId);
        assertEquals(tree.state(), data.players.get(bob.id().toString()).stateId);
        assertEquals(tree.nation(), data.players.get(bob.id().toString()).nationId);
        assertTrue(data.governments.get(tree.nation()).officers.contains(bob.id().toString()));
        run(bob, "city join " + tree.city());
        run(alice, "state kick " + tree.state() + " Bob");
        assertNull(data.players.get(bob.id().toString()).stateId);
        assertNull(data.players.get(bob.id().toString()).cityId);
        assertTrue(engine.mayManageGovernment(bob.id(), tree.nation()));
        assertTrue(engine.validationIssues().isEmpty());
    }

    @Test
    void subordinateAndPeerOfficialsCannotExpelSuperiorOfficialsAndLeadershipStaysProtected() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        join(dave, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(alice, "state leader " + tree.state() + " Dave");
        run(alice, "city leader " + tree.city() + " Cara");
        assertThrows(UserError.class, () -> run(cara, "city kick " + tree.city() + " Bob"));
        assertThrows(UserError.class, () -> run(dave, "state kick " + tree.state() + " Bob"));
        assertThrows(UserError.class, () -> run(dave, "city kick " + tree.city() + " Bob"));
        run(alice, "nation officer " + tree.nation() + " Dave add");
        assertThrows(UserError.class, () -> run(dave, "city kick " + tree.city() + " Bob"));
        assertThrows(UserError.class, () -> run(bob, "city kick " + tree.city() + " Alice"));
        run(alice, "city leader " + tree.city() + " Bob");
        UserError leadership = assertThrows(UserError.class, () -> run(alice, "city kick " + tree.city() + " Bob"));
        assertTrue(leadership.getMessage().contains("Transfer leadership"));
        assertThrows(UserError.class, () -> run(alice, "state kick " + tree.state() + " Bob"));
        assertEquals(bob.id().toString(), data.governments.get(tree.city()).leader);
        assertEquals(tree.city(), data.players.get(bob.id().toString()).cityId);
        assertTrue(data.governments.get(tree.nation()).officers.contains(bob.id().toString()));
    }

    @Test
    void ancestorOfficerCanRemoveASubordinateOfficersOrdinaryCityMembership() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "state officer " + tree.state() + " Bob add");
        run(alice, "nation officer " + tree.nation() + " Cara add");
        run(cara, "city kick " + tree.city() + " Bob");
        assertNull(data.players.get(bob.id().toString()).cityId);
        assertEquals(tree.state(), data.players.get(bob.id().toString()).stateId);
        assertTrue(data.governments.get(tree.state()).officers.contains(bob.id().toString()));
    }

    @Test
    void malformedLoadedTreatyTermsPauseWorkflowsWithoutDiscardingRecordsOrMovingAssets() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 1_000L);
        String treaty = awaitingTreaty(alpha, beta, key);
        Gson gson = new Gson();
        String saved = gson.toJson(data);
        Map<String, Long> balances = Map.copyOf(economy.balances);
        List<Consumer<DiplomaticProposal>> corruptions = List.of(
                p -> p.chunks.set(0, null),
                p -> p.chunks.get(0).fromNation = null,
                p -> p.chunks.get(0).fromCity = "",
                p -> p.chunks.get(0).fromCity = alpha.state(),
                p -> p.chunks.get(0).fromCity = UUID.randomUUID().toString(),
                p -> p.chunks.get(0).toNation = null,
                p -> p.chunks.get(0).toCity = beta.nation(),
                p -> p.chunks.get(0).toCity = alpha.city(),
                p -> p.chunks.get(0).toCity = UUID.randomUUID().toString(),
                p -> p.chunks.get(0).key = null,
                p -> p.chunks.get(0).key = "invalid",
                p -> p.chunks.get(0).key = "minecraft:overworld|00|0",
                p -> p.chunks.get(0).key = "minecraft:overworld|50|50",
                p -> p.chunks.add(p.chunks.get(0)));
        for (Consumer<DiplomaticProposal> corrupt : corruptions) {
            GovernanceData loaded = gson.fromJson(saved, GovernanceData.class);
            corrupt.accept(loaded.diplomacy.get(treaty));
            String malformed = gson.toJson(loaded);
            GovernanceData restoredData = gson.fromJson(malformed, GovernanceData.class);
            GovernanceEngine restored = new GovernanceEngine(restoredData, config, economy, time::get);
            assertTrue(restored.validationIssues().stream().anyMatch(issue -> issue.contains(treaty)
                    && issue.contains("operator repair")), restored.validationIssues().toString());
            assertDoesNotThrow(() -> restored.tick(time.get() + 6_000));
            assertEquals(malformed, gson.toJson(restoredData));
            assertEquals("AWAITING_RATIFICATION", restoredData.diplomacy.get(treaty).status);
            assertEquals(alpha.city(), restoredData.claims.get(key).cityId);
            assertEquals(balances, economy.balances);
            assertTrue(economy.batches.isEmpty());
        }
    }

    @Test
    void missingTermSourceDuringRatificationProducesRepairableReadyStateBeforeAnyPayment() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 1_000L);
        String treaty = awaitingTreaty(alpha, beta, key);
        advance(config.debateDurationMillis);
        run(alice, "diplomacy ratify " + treaty + " yes");
        run(dave, "diplomacy ratify " + treaty + " yes");
        DiplomaticProposal proposal = data.diplomacy.get(treaty);
        proposal.chunks.get(0).fromNation = null;
        assertDoesNotThrow(() -> advance(config.legislativeVotingMillis));
        assertEquals("READY", proposal.status);
        assertTrue(proposal.lastError.contains("fromNation"));
        assertTrue(proposal.lastError.contains("operator repair"));
        assertEquals(alpha.city(), data.claims.get(key).cityId);
        assertEquals(1_000, economy.balance("nation:" + alpha.nation()));
        assertEquals(0, economy.balance("nation:" + beta.nation()));
        assertEquals("WAR", engine.politics.relationStatus(alpha.nation(), beta.nation()));
        assertEquals(0, economy.attempts);
        UserError error = assertThrows(UserError.class, () -> run(operator, "diplomacy execute " + treaty));
        assertTrue(error.getMessage().contains("fromNation"));
        proposal.chunks.get(0).fromNation = alpha.nation();
        run(dave, "diplomacy execute " + treaty);
        assertEquals("ENACTED", proposal.status);
        assertEquals("", proposal.lastError);
        assertEquals(beta.city(), data.claims.get(key).cityId);
        assertEquals(1_000, economy.balance("nation:" + beta.nation()));
        assertEquals(1, economy.batches.size());
        assertThrows(UserError.class, () -> run(operator, "diplomacy execute " + treaty));
        assertEquals(1, economy.batches.size());
    }

    @Test
    void explicitSettlementHandlesMissingSignatoriesAndCollectionsWithoutUncheckedErrors() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        String treaty = awaitingTreaty(alpha, beta, key);
        advance(config.debateDurationMillis);
        run(alice, "diplomacy ratify " + treaty + " yes");
        run(dave, "diplomacy ratify " + treaty + " yes");
        advance(config.legislativeVotingMillis);
        DiplomaticProposal proposal = data.diplomacy.get(treaty);
        assertEquals("READY", proposal.status);
        int attempts = economy.attempts;
        proposal.fromNation = null;
        assertThrows(UserError.class, () -> run(operator, "diplomacy execute " + treaty));
        proposal.fromNation = alpha.nation();
        var ratified = proposal.ratified;
        proposal.ratified = null;
        assertThrows(UserError.class, () -> run(operator, "diplomacy execute " + treaty));
        proposal.ratified = ratified;
        var chunks = proposal.chunks;
        proposal.chunks = null;
        assertThrows(UserError.class, () -> run(operator, "diplomacy execute " + treaty));
        proposal.chunks = chunks;
        assertEquals(attempts, economy.attempts);
        assertEquals("READY", proposal.status);
        assertEquals(alpha.city(), data.claims.get(key).cityId);
        assertTrue(economy.batches.isEmpty());
    }

    @Test
    void completedTreatiesCanRetainHistoricalReferencesToDeletedCitiesAndClaims() {
        config.requirePeaceRatification = false;
        configure();
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        run(dave, "diplomacy accept " + treaty);
        run(alice, "city disband " + alpha.city());
        run(dave, "chunk unclaim " + key);
        run(dave, "city disband " + beta.city());
        Gson gson = new Gson();
        GovernanceData restoredData = gson.fromJson(gson.toJson(data), GovernanceData.class);
        GovernanceEngine restored = new GovernanceEngine(restoredData, config, EconomyAccess.UNAVAILABLE, time::get);
        assertTrue(restored.validationIssues().isEmpty(), restored.validationIssues().toString());
        assertEquals("ENACTED", restoredData.diplomacy.get(treaty).status);
        assertEquals(alpha.city(), restoredData.diplomacy.get(treaty).chunks.get(0).fromCity);
        assertEquals(beta.city(), restoredData.diplomacy.get(treaty).chunks.get(0).toCity);
        assertFalse(restoredData.governments.containsKey(alpha.city()));
        assertFalse(restoredData.governments.containsKey(beta.city()));
        assertFalse(restoredData.claims.containsKey(key));
    }

    private String submittedContract(Tree tree, String chunks) {
        run(alice, "contract create " + tree.nation() + " Road Work " + chunks);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 10 Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        run(bob, "contract submit " + contract + " Done");
        return contract;
    }

    private String awaitingTreaty(Tree alpha, Tree beta, String key) {
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 10 0 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        run(dave, "diplomacy accept " + treaty);
        return treaty;
    }
}
