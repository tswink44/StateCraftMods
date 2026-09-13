package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.ActionSelection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GovernancePreviewsTest extends DomainFixture {
    private GovernancePresentation ui;

    @BeforeEach
    void presentation() { ui = new GovernancePresentation(engine); }

    @Test
    void everyCreationClaimAndCandidateFeeMatchesTheRealCommandPayment() {
        config.nationCreationFee = 100;
        config.stateCreationFee = 200;
        config.cityCreationFee = 300;
        config.companyCreationFee = 400;
        config.claimFee = 500;
        config.electionCandidateFee = 600;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), 10_000L);
        fee(alice, "nation create FeeNation", 100, config.feeAccount);
        String nation = government("FeeNation").id();
        fee(alice, "state create " + nation + " FeeState", 200, "nation:" + nation);
        String state = government("FeeState").id();
        fee(alice, "city create " + state + " FeeCity", 300, "state:" + state);
        String city = government("FeeCity").id();
        fee(alice, "company create Builders", 400, config.feeAccount);
        fee(alice, "chunk claim " + nation + " here", 500, "nation:" + nation);
        fee(alice, "election candidate " + nation, 600, "nation:" + nation);
        assertEquals(7_900, economy.balance(alice.account()));
        assertEquals(6, economy.batches.size());
    }

    @Test
    void previewsArePureStableAcrossTimeAndBalanceChangesButBindMaterialFees() {
        config.nationCreationFee = 100;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), 1_000L);
        AtomicInteger dirty = observe();
        String saved = new Gson().toJson(data);
        ActionPreview first = preview(alice, "nation create QuotedNation");
        time.addAndGet(100);
        economy.balances.put(alice.account(), 2_000L);
        ActionPreview later = preview(alice, "nation create QuotedNation");
        assertEquals(first.fingerprint(), later.fingerprint());
        assertTrue(first.lines().stream().filter(line -> line.label().key().endsWith(".balances")).noneMatch(ActionPreview.Line::material));
        assertEquals(saved, new Gson().toJson(data));
        assertEquals(0, dirty.get());
        assertTrue(economy.batches.isEmpty());
        config.nationCreationFee = 200;
        configure();
        ActionPreview changed = preview(alice, "nation create QuotedNation");
        assertNotEquals(first.fingerprint(), changed.fingerprint());
        assertEquals(Money.format(200), value(changed, "pay_now"));
        assertEquals(0, economy.attempts);
        assertEquals(0, dirty.get());
    }

    @Test
    void hereClaimReviewsBindPositionAndDimensionEvenWhenPayerPayeeAndFeeMatch() {
        Tree tree = tree(alice, "Alpha");
        config.claimFee = 300;
        configure();
        useEconomy();
        economy.balances.put(alice.account(), 1_000L);
        Actor first = at(alice, "minecraft:overworld", 12, 34);
        Actor moved = at(alice, "minecraft:overworld", 13, 34);
        Actor changedDimension = at(alice, "minecraft:the_nether", 12, 34);
        String command = "chunk claim " + tree.nation() + " here";
        ActionPreview original = pure(first, command);
        ActionPreview neighboring = pure(moved, command);
        ActionPreview otherDimension = pure(changedDimension, command);
        assertFalse(has(original, "command"));
        assertFalse(has(neighboring, "command"));
        assertEquals(value(original, "pay_now"), value(neighboring, "pay_now"));
        assertEquals(value(original, "payment_parties"), value(neighboring, "payment_parties"));
        assertEquals(DisplayText.chunk(first.chunkKey()), value(original, "target_chunk"));
        assertEquals(DisplayText.chunk(moved.chunkKey()), value(neighboring, "target_chunk"));
        assertEquals(DisplayText.chunk(changedDimension.chunkKey()), value(otherDimension, "target_chunk"));
        assertNotEquals(original.fingerprint(), neighboring.fingerprint());
        assertNotEquals(original.fingerprint(), otherDimension.fingerprint());
        assertEquals(DisplayText.chunk(first.chunkKey()), value(pure(first, "claim " + tree.nation() + " here"), "target_chunk"));
        assertTrue(data.claims.isEmpty());
        run(moved, command);
        assertTrue(data.claims.containsKey(moved.chunkKey()));
        assertFalse(data.claims.containsKey(first.chunkKey()));
        assertEquals(700, economy.balance(alice.account()));
        assertEquals(300, economy.balance("nation:" + tree.nation()));
    }

    @Test
    void everyHereLandMutationAndDiagnosticRequotesBetweenOtherwiseIdenticalClaims() {
        Tree tree = tree(alice, "Alpha");
        Tree destination = tree(dave, "Beta");
        String first = claim(alice, tree, 0, 0);
        String second = claim(alice, tree, 1, 0);
        Actor here = at(alice, "minecraft:overworld", 0, 0);
        Actor moved = at(alice, "minecraft:overworld", 1, 0);
        assertEquals(data.claims.get(first).ownerAccount, data.claims.get(second).ownerAccount);
        for (String command : List.of("chunk unclaim here", "unclaim here", "chunk permit here Bob BREAK",
                "chunk info here", "chunk permits here", "chunk protection here Bob", "chunk map 2",
                "contract create " + tree.nation() + " Road Work here")) {
            ActionPreview before = pure(here, command);
            ActionPreview after = pure(moved, command);
            String field = command.startsWith("contract") ? "chunks" : "target_chunk";
            assertEquals(DisplayText.chunk(first), value(before, field), command);
            assertEquals(DisplayText.chunk(second), value(after, field), command);
            assertNotEquals(before.fingerprint(), after.fingerprint(), command);
        }
        Actor adminHere = at(operator, "minecraft:overworld", 0, 0);
        Actor adminMoved = at(operator, "minecraft:overworld", 1, 0);
        for (String command : List.of("admin unclaim here", "admin owner here " + alice.account(),
                "admin reassign here " + destination.city(), "admin diagnostics here")) {
            ActionPreview before = pure(adminHere, command);
            ActionPreview after = pure(adminMoved, command);
            assertEquals(DisplayText.chunk(first), value(before, "target_chunk"), command);
            assertEquals(DisplayText.chunk(second), value(after, "target_chunk"), command);
            assertNotEquals(before.fingerprint(), after.fingerprint(), command);
        }
        assertEquals(2, data.claims.size());
    }

    @Test
    void operatorReassignmentRefusesMismatchedRecordKeysWithoutChangingEitherClaim() {
        Tree tree = tree(alice, "Alpha");
        Tree destination = tree(dave, "Beta");
        String first = claim(alice, tree, 0, 0);
        String second = claim(alice, tree, 1, 0);
        data.claims.get(first).key = second;
        String command = "admin reassign here " + destination.city();
        assertThrows(UserError.class, () -> pure(at(operator, "minecraft:overworld", 0, 0), command));
        ActionPreview after = pure(at(operator, "minecraft:overworld", 1, 0), command);
        assertEquals(DisplayText.chunk(second), value(after, "target_chunk"));
        assertEquals(tree.nation(), data.claims.get(first).nationId);
        assertEquals(tree.nation(), data.claims.get(second).nationId);
    }

    @Test
    void zeroPriceGovernanceWorksWithoutEconomyAndPaidDependenciesAreExplicit() {
        ActionPreview free = preview(alice, "nation create FreeNation");
        assertEquals(Money.format(0), value(free, "pay_now"));
        assertTrue(free.lines().stream().anyMatch(line -> line.value().fallback().contains("Economy is unavailable")));
        assertTrue(data.governments.isEmpty());
        config.nationCreationFee = 100;
        configure();
        UserError missing = assertThrows(UserError.class, () -> preview(alice, "nation create PaidNation"));
        assertTrue(missing.getMessage().contains("Economy"));
        assertTrue(data.governments.isEmpty());
        assertFalse(data.economySeen);
    }

    @Test
    void ineligibleOrForgedTargetsNeverBecomeZeroCostReviews() {
        Tree tree = tree(alice, "Alpha");
        String before = new Gson().toJson(data);
        assertThrows(UserError.class, () -> preview(bob, "state create " + tree.nation() + " UnauthorizedState"));
        assertThrows(UserError.class, () -> preview(alice, "contract award " + java.util.UUID.randomUUID() + " Bob"));
        assertThrows(UserError.class, () -> ui.preview(alice, ActionSelection.form("statecraft:mail",
                "contract complete <contract>", Map.of("contract", java.util.UUID.randomUUID().toString()))));
        assertThrows(UserError.class, () -> preview(alice, "unknown command"));
        Actor renamed = new Actor(alice.id(), "ChangedName", false, alice.dimension(), alice.chunkX(), alice.chunkZ());
        assertThrows(UserError.class, () -> preview(renamed, "nation create AnotherNation"));
        assertEquals(before, new Gson().toJson(data));
    }

    @Test
    void contractBidsAreConditionalWhileAwardsAndCompletionUseTheirRealEscrowPlans() {
        Tree tree = tree(alice, "Alpha");
        String first = claim(alice, tree, 0, 0);
        String second = claim(alice, tree, 1, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 10_000L);
        run(alice, "contract create " + tree.nation() + " Road Work " + first + "," + second);
        String contract = latestContract();
        ActionPreview bid = pure(bob, "contract bid " + contract + " 10 Work");
        assertEquals(Money.format(1_000), value(bid, "conditional_total"));
        assertFalse(has(bid, "pay_now"));
        run(bob, "contract bid " + contract + " 10 Work");
        run(alice, "contract review " + contract);
        ActionPreview award = pure(alice, "contract award " + contract + " Bob");
        assertEquals(Money.format(1_000), value(award, "pay_now"));
        assertTrue(value(award, "payment_parties").contains("Road (contract escrow)"));
        data.contracts.get(contract).bids.get(bob.id().toString()).cents = 2_000;
        ActionPreview changed = pure(alice, "contract award " + contract + " Bob");
        assertNotEquals(award.fingerprint(), changed.fingerprint());
        assertEquals(Money.format(2_000), value(changed, "pay_now"));
        run(alice, "contract award " + contract + " Bob");
        assertEquals(2_000, economy.balance("escrow:contract:" + contract));
        run(bob, "contract submit " + contract + " Completed");
        config.maxContractChunks = 1;
        configure();
        ActionPreview completion = pure(alice, "contract complete " + contract);
        assertEquals(Money.format(2_000), value(completion, "pay_now"));
        assertTrue(value(completion, "payment_parties").contains("Bob (player)"));
        assertThrows(UserError.class, () -> preview(operator(bob, true), "contract complete " + contract));
        data.contracts.get(contract).payeeAccount = cara.account();
        assertThrows(UserError.class, () -> preview(alice, "contract complete " + contract));
        data.contracts.get(contract).payeeAccount = bob.account();
        economy.encumbered.add(first);
        assertThrows(UserError.class, () -> preview(alice, "contract complete " + contract));
        economy.encumbered.remove(first);
        run(alice, "contract complete " + contract);
        assertEquals(2_000, economy.balance(bob.account()));
        assertEquals(0, economy.balance("escrow:contract:" + contract));
    }

    @Test
    void cancellationQuotesRefundOnlyTheOriginalIssuingTreasury() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 1_000L);
        String contract = awarded(tree, key, "10");
        ActionPreview refund = pure(bob, "contract cancel " + contract + " Unable");
        assertEquals(Money.format(1_000), value(refund, "pay_now"));
        assertTrue(value(refund, "payment_parties").contains("Road (contract escrow)"));
        assertTrue(value(refund, "payment_parties").contains("AlphaNation (Nation)"));
        assertThrows(UserError.class, () -> preview(cara, "contract cancel " + contract + " Unwanted"));
        run(bob, "contract cancel " + contract + " Unable");
        assertEquals(1_000, economy.balance("nation:" + tree.nation()));
        assertEquals(0, economy.balance(bob.account()));
    }

    @Test
    void shareholderDividendReviewsUseSavedCentsAndExactCurrentAllocationsWithoutSettling() {
        String company = company(alice, "Builders");
        engine.transferShares(company, alice.id(), bob.id(), 3_000);
        engine.transferShares(company, alice.id(), cara.id(), 2_000);
        useEconomy();
        ActionPreview proposal = pure(alice, "company propose " + company + " dividend 0.10 Dividend Text");
        assertEquals(Money.format(10), value(proposal, "conditional_total"));
        run(alice, "company propose " + company + " dividend 0.10 Dividend Text");
        String id = latestCompanyProposal();
        run(alice, "company vote " + id + " yes");
        advance(config.companyVotingMillis);
        assertEquals("READY", data.companyProposals.get(id).status);
        economy.balances.put("company:" + company, 10L);
        ActionPreview payment = pure(alice, "company execute " + id);
        assertEquals(Money.format(10), value(payment, "pay_now"));
        assertTrue(value(payment, "payment_parties").contains("Alice (player): " + Money.format(5)));
        assertTrue(value(payment, "payment_parties").contains("Bob (player): " + Money.format(3)));
        assertTrue(value(payment, "payment_parties").contains("Cara (player): " + Money.format(2)));
        run(alice, "company execute " + id);
        assertEquals(5, economy.balance(alice.account()));
        assertEquals(3, economy.balance(bob.account()));
        assertEquals(2, economy.balance(cara.account()));
    }

    @Test
    void shareholderBallotReviewsBindTheirCompanyAndFrozenElectorate() {
        String first = company(alice, "First Company");
        String second = company(alice, "Second Company");
        run(alice, "company propose " + first + " roleplay - Decision Text");
        String proposal = latestCompanyProposal();
        ActionPreview original = pure(alice, "company vote " + proposal + " yes");
        data.companyProposals.get(proposal).companyId = second;
        ActionPreview moved = pure(alice, "company vote " + proposal + " yes");
        assertNotEquals(original.fingerprint(), moved.fingerprint());
        assertTrue(value(moved, "company").contains("Second Company"));
        data.companyProposals.get(proposal).electorate.put(bob.id().toString(), 100L);
        data.companyProposals.get(proposal).electorate.put(alice.id().toString(), 9_900L);
        ActionPreview reweighted = pure(alice, "company vote " + proposal + " yes");
        assertNotEquals(moved.fingerprint(), reweighted.fingerprint());
    }

    @Test
    void largeDividendReviewsGroupFundingButBindEveryUndisplayedPayout() {
        String company = company(alice, "Builders");
        for (int index = 0; index < 39; index++) {
            Actor holder = actor(100 + index, "Holder" + index, false);
            engine.login(holder);
            engine.transferShares(company, alice.id(), holder.id(), 100);
        }
        useEconomy();
        run(alice, "company propose " + company + " dividend 100 Dividend Text");
        String proposal = latestCompanyProposal();
        run(alice, "company vote " + proposal + " yes");
        advance(config.companyVotingMillis);
        assertEquals("READY", data.companyProposals.get(proposal).status);
        economy.balances.put("company:" + company, 10_000L);
        AtomicInteger dirty = observe();
        ActionPreview original = pure(alice, "company execute " + proposal);
        assertEquals("40", value(original, "transfer_count"));
        assertTrue(value(original, "distribution").contains("40 recipients"));
        assertTrue(value(original, "payment_parties").length() < 512);
        assertFalse(value(original, "payment_parties").contains("Holder38"));
        assertEquals(Money.format(10_000), value(original, "pay_now"));
        assertEquals(0, dirty.get());
        engine.transferShares(company, alice.id(), actor(100, "Holder0", false).id(), 1);
        int changed = dirty.get();
        ActionPreview redistributed = pure(alice, "company execute " + proposal);
        assertEquals(value(original, "payment_parties"), value(redistributed, "payment_parties"));
        assertEquals(value(original, "pay_now"), value(redistributed, "pay_now"));
        assertNotEquals(original.fingerprint(), redistributed.fingerprint());
        assertEquals(changed, dirty.get());
        run(alice, "company execute " + proposal);
        assertEquals(6_099, economy.balance(alice.account()));
        assertEquals(101, economy.balance(actor(100, "Holder0", false).account()));
    }

    @Test
    void bidsAndShareholderActionsDoNotDiscloseUnmanagedFundingBalances() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String company = company(alice, "Builders");
        engine.transferShares(company, alice.id(), bob.id(), 100);
        useEconomy();
        long privateBalance = 123_456_789L;
        economy.balances.put("nation:" + tree.nation(), privateBalance);
        economy.balances.put("company:" + company, privateBalance);
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        ActionPreview bid = pure(bob, "contract bid " + latestContract() + " 10 Work");
        assertTrue(value(bid, "balances").contains("not disclosed"));
        assertFalse(value(bid, "balances").contains(Money.format(privateBalance)));
        ActionPreview shareholder = pure(bob, "company propose " + company + " dividend 1 Dividend Text");
        assertTrue(value(shareholder, "balances").contains("not disclosed"));
        assertFalse(value(shareholder, "balances").contains(Money.format(privateBalance)));
        economy.balances.put("company:" + company, 0L);
        run(bob, "company propose " + company + " dividend 1 Dividend Text");
        String proposal = latestCompanyProposal();
        run(alice, "company vote " + proposal + " yes");
        advance(config.companyVotingMillis);
        economy.balances.put("company:" + company, privateBalance);
        ActionPreview execution = pure(bob, "company execute " + proposal);
        assertTrue(value(execution, "balances").contains("not disclosed"));
        assertFalse(value(execution, "balances").contains(Money.format(privateBalance)));
        assertEquals(Money.format(100), value(execution, "pay_now"));
    }

    @Test
    void treatyAcceptanceRequotesWhenRatificationChangesPaymentFromFutureToImmediate() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 1_000L);
        economy.balances.put("nation:" + beta.nation(), 500L);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        String command = "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 10 5 " + key + "=" + beta.city();
        ActionPreview first = pure(alice, command);
        time.addAndGet(100);
        assertEquals(first.fingerprint(), pure(alice, command).fingerprint());
        run(alice, command);
        String treaty = latestDiplomacy();
        ActionPreview pending = pure(dave, "diplomacy accept " + treaty);
        assertEquals("Yes", value(pending, "ratification"));
        assertEquals(Money.format(1_500), value(pending, "conditional_total"));
        assertTrue(economy.batches.isEmpty());
        config.requirePeaceRatification = false;
        configure();
        ActionPreview immediate = pure(dave, "diplomacy accept " + treaty);
        assertEquals("No", value(immediate, "ratification"));
        assertEquals(Money.format(1_500), value(immediate, "pay_now"));
        assertNotEquals(pending.fingerprint(), immediate.fingerprint());
        run(dave, "diplomacy accept " + treaty);
        assertEquals("ENACTED", data.diplomacy.get(treaty).status);
        assertEquals(500, economy.balance("nation:" + alpha.nation()));
        assertEquals(1_000, economy.balance("nation:" + beta.nation()));
        assertEquals(beta.city(), data.claims.get(key).cityId);
        assertEquals(2, economy.batches.get(0).size());
    }

    @Test
    void localPolicyAndSuperiorMembershipRulesRemainAuthoritativeInReviews() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(alice, "city leader " + tree.city() + " Cara");
        config.requireLegislationForPolicy = true;
        configure();
        assertThrows(UserError.class, () -> preview(alice, "nation setting " + tree.nation() + " incomeTaxBps 500"));
        assertThrows(UserError.class, () -> preview(bob, "city setting " + tree.city() + " incomeTaxBps 100"));
        ActionPreview local = pure(cara, "city setting " + tree.city() + " incomeTaxBps 100");
        assertEquals("1%", value(local, "replacement"));
        assertThrows(UserError.class, () -> preview(cara, "city kick " + tree.city() + " Bob"));
        assertDoesNotThrow(() -> pure(alice, "city kick " + tree.city() + " Bob"));
        assertThrows(UserError.class, () -> preview(alice, "city kick " + tree.city() + " Cara"));
        assertEquals(tree.city(), data.players.get(bob.id().toString()).cityId);
        assertEquals("0", government(tree.city()).settings().get("incomeTaxBps"));
    }

    @Test
    void inheritedPolicyReviewsBindTheirActualResultWithoutIntroducingTaxInheritance() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "nation setting " + tree.nation() + " pvp true");
        run(alice, "city setting " + tree.city() + " pvp false");
        ActionPreview original = pure(alice, "city setting " + tree.city() + " pvp inherit");
        assertEquals("On", value(original, "resulting_effective"));
        run(alice, "nation setting " + tree.nation() + " pvp false");
        ActionPreview changed = pure(alice, "city setting " + tree.city() + " pvp inherit");
        assertEquals("Off", value(changed, "resulting_effective"));
        assertNotEquals(original.fingerprint(), changed.fingerprint());
        run(alice, "city setting " + tree.city() + " incomeTaxBps 500");
        ActionPreview localTax = pure(alice, "city setting " + tree.city() + " incomeTaxBps inherit");
        assertEquals("0%", value(localTax, "resulting_effective"));
        run(alice, "nation setting " + tree.nation() + " incomeTaxBps 900");
        assertEquals(localTax.fingerprint(), pure(alice, "city setting " + tree.city() + " incomeTaxBps inherit").fingerprint());
        assertEquals("500", government(tree.city()).settings().get("incomeTaxBps"));
    }

    @Test
    void destructiveReviewsBindUnseenAffectedStateWithoutSerializingOrMutatingTheWorld() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        ActionPreview before = pure(alice, "nation disband " + tree.nation() + " cascade");
        data.claims.get(key).permits.put(bob.id().toString(), Set.of("BREAK"));
        ActionPreview changed = pure(alice, "nation disband " + tree.nation() + " cascade");
        assertNotEquals(before.fingerprint(), changed.fingerprint());
        assertEquals(value(before, "claims_removed"), value(changed, "claims_removed"));
        assertEquals("3", value(changed, "governments_removed"));
        assertTrue(changed.stateKey().length() <= 4096);
        assertEquals(3, data.governments.size());
        assertTrue(data.claims.containsKey(key));
    }

    @Test
    void mailQueriesAndLegacyReadInitializationRemainPureDuringPreview() {
        Tree tree = tree(alice, "Alpha");
        run(bob, "mail send Alice Subject Body");
        var message = data.players.get(alice.id().toString()).inbox.get(0);
        pure(alice, "mail read " + message.id);
        assertFalse(message.read);
        assertThrows(UserError.class, () -> preview(operator, "mail read " + message.id));
        engine.governmentMail(tree.nation(), "Official", "Private");
        String official = data.governments.get(tree.nation()).inbox.get(0).id;
        assertThrows(UserError.class, () -> preview(bob, "mail official read " + tree.nation() + " " + official));
        pure(alice, "mail official read " + tree.nation() + " " + official);
        assertFalse(data.governments.get(tree.nation()).inbox.get(0).read);
        String company = company(alice, "Builders");
        run(alice, "company propose " + company + " roleplay - Decision Text");
        String proposal = latestCompanyProposal();
        data.companyProposals.get(proposal).executionEndsAt = 0;
        pure(alice, "company proposal " + proposal);
        assertEquals(0, data.companyProposals.get(proposal).executionEndsAt);
    }

    @Test
    void operatorsCanReviewCancellingMalformedTerritorialTermsWithoutInventingASettlement() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        data.diplomacy.get(treaty).chunks.get(0).fromNation = null;
        ActionPreview cancel = pure(operator, "diplomacy cancel " + treaty);
        assertFalse(has(cancel, "pay_now"));
        assertTrue(value(cancel, "territory_terms").contains("Unavailable nation"));
        assertThrows(UserError.class, () -> preview(operator, "diplomacy accept " + treaty));
        run(operator, "diplomacy cancel " + treaty);
        assertEquals("CANCELLED", data.diplomacy.get(treaty).status);
        assertEquals(alpha.city(), data.claims.get(key).cityId);
    }

    private void fee(Actor actor, String command, long cents, String recipient) {
        ActionPreview quote = pure(actor, command);
        assertEquals(Money.format(cents), value(quote, "pay_now"));
        int before = economy.batches.size();
        run(actor, command);
        assertEquals(before + 1, economy.batches.size());
        EconomyAccess.Transfer actual = economy.batches.get(before).get(0);
        assertEquals(cents, actual.cents());
        assertEquals(actor.account(), actual.from());
        assertEquals(recipient, actual.to());
    }

    private ActionPreview preview(Actor actor, String command) {
        return ui.preview(actor, ActionSelection.raw("statecraft:main", command));
    }

    private ActionPreview pure(Actor actor, String command) {
        String before = new Gson().toJson(data);
        Map<String, Long> balances = Map.copyOf(economy.balances);
        int attempts = economy.attempts;
        ActionPreview result = preview(actor, command);
        assertEquals(before, new Gson().toJson(data), command);
        assertEquals(balances, economy.balances, command);
        assertEquals(attempts, economy.attempts, command);
        assertTrue(result.lines().size() <= ActionPreview.MAX_LINES);
        assertFalse(result.warning().fallback().isBlank());
        return result;
    }

    private AtomicInteger observe() {
        AtomicInteger dirty = new AtomicInteger();
        engine = new GovernanceEngine(data, config, economy, time::get, dirty::incrementAndGet);
        ui = new GovernancePresentation(engine);
        return dirty;
    }

    private String awarded(Tree tree, String key, String amount) {
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " " + amount + " Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        return contract;
    }

    private String value(ActionPreview preview, String key) {
        return preview.lines().stream().filter(line -> line.label().key().equals("ui.statecraft.gov.preview." + key))
                .findFirst().orElseThrow(() -> new AssertionError("Missing review line " + key)).value().fallback();
    }

    private boolean has(ActionPreview preview, String key) {
        return preview.lines().stream().anyMatch(line -> line.label().key().equals("ui.statecraft.gov.preview." + key));
    }
}
