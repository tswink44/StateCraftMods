package dev.statecraft.domain;

import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceMutationTest extends DomainFixture {
    private final AtomicInteger changes = new AtomicInteger();

    @BeforeEach
    void observeMutations() {
        engine = new GovernanceEngine(data, config, EconomyAccess.UNAVAILABLE, time::get, changes::incrementAndGet);
    }

    @Test
    void constructorInitializesObserverBeforeEconomyChangesAndDoesNotResetMalformedData() {
        changedBy(() -> new GovernanceEngine(data, config, economy, time::get, changes::incrementAndGet));
        assertTrue(data.economySeen);
        quiet(() -> new GovernanceEngine(data, config, economy, time::get, changes::incrementAndGet));
        GovernanceData malformed = new GovernanceData();
        malformed.players = null;
        quiet(() -> assertThrows(UserError.class,
                () -> new GovernanceEngine(malformed, config, economy, time::get, changes::incrementAndGet)));
        assertNull(malformed.players);
        assertFalse(malformed.economySeen);
        assertDoesNotThrow(() -> new GovernanceEngine(data, config, economy, time::get));
    }

    @Test
    void playerCreationNamesAndFirstEconomyObservationNotifyButActivityTimestampsDoNot() {
        quiet(() -> engine.login(alice));
        time.incrementAndGet();
        quiet(() -> engine.login(alice));
        assertEquals(time.get(), data.players.get(alice.id().toString()).lastSeen);
        changedBy(() -> engine.login(actor(7, "NewCitizen", false)));
        changedBy(() -> engine.login(new Actor(bob.id(), "RenamedBob", false, bob.dimension(), 0, 0)));
        changedBy(() -> engine.login(actor(8, "RenamedBob", false)));
        assertEquals(bob.id().toString(), data.players.get(bob.id().toString()).name);
        quiet(() -> engine.configure(config));
        economy.available = false;
        quiet(() -> engine.setEconomy(economy));
        economy.available = true;
        changedBy(() -> engine.tick(time.incrementAndGet()));
        assertTrue(data.economySeen);
        quiet(() -> engine.setEconomy(economy));
        quiet(() -> engine.setEconomy(EconomyAccess.UNAVAILABLE));
        quiet(() -> engine.tick(time.incrementAndGet()));
        assertEquals(time.get(), data.lastTick);
    }

    @Test
    void establishedPlayerQueriesAndFormsDoNotDirtyAnUnchangedWorld() {
        Tree tree = tree(alice, "Alpha");
        Tree other = tree(dave, "Beta");
        join(bob, tree);
        String key = claim(alice, tree, 0, 0);
        String company = company(alice, "Builders");
        run(alice, "company propose " + company + " roleplay - Charter Text");
        String companyProposal = latestCompanyProposal();
        run(alice, "bill propose " + tree.nation() + " roleplay - Charter Text");
        String bill = latestBill();
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        run(alice, "diplomacy alliance " + tree.nation() + " " + other.nation());
        String diplomacy = latestDiplomacy();
        engine.mail(alice.id(), "Unread", "Reading the inbox list must not mark this read.");
        for (String command : List.of("", "help", "help governments", "info", "profile", "profile Bob",
                "nation list", "state list", "city list", "government list",
                "government info " + tree.nation(), "government members " + tree.nation(),
                "government roles " + tree.nation(), "government officers " + tree.nation(),
                "government settings " + tree.nation(), "government invitations " + tree.nation(),
                "chunk info " + key, "chunk list all", "chunk permits " + key, "chunk map",
                "chunk protection " + key, "election list", "election status " + tree.nation(),
                "election candidates " + tree.nation(), "election history " + tree.nation(),
                "bill list all", "bill info " + bill, "bill votes " + bill, "bill history " + bill,
                "law list all", "executive status " + tree.nation(),
                "diplomacy status all", "diplomacy proposals all", "diplomacy terms " + diplomacy,
                "company list", "company info " + company, "company members " + company,
                "company shareholders " + company, "company proposals all", "company proposal " + companyProposal,
                "contract list all", "contract my", "contract info " + contract, "contract bids " + contract,
                "mail inbox", "mail sent", "mail invitations", "mail official inbox " + tree.nation(),
                "mail official sent " + tree.nation())) {
            time.incrementAndGet();
            int before = changes.get();
            run(alice, command);
            assertEquals(before, changes.get(), command);
        }
        for (String command : List.of("admin audit", "admin diagnostics", "admin history", "admin repair preview"))
            quiet(() -> run(operator, command));
        quiet(() -> {
            engine.governments();
            engine.government(tree.city());
            engine.companies();
            engine.company(company);
            engine.claims();
            engine.claim(key);
            engine.nationOf(alice.id());
            engine.mayManageGovernment(alice.id(), tree.city());
            engine.mayManageCompany(alice.id(), company);
            engine.mayAccessAccount(alice.id(), "nation:" + tree.nation());
            engine.accountsFor(alice.id());
            engine.maySellProperty(alice.id(), key);
            engine.mayBuyProperty(alice.id(), key);
            engine.sharesOf(company, alice.id());
            engine.availableShares(company, alice.id());
            engine.mayAct(alice, key, AccessAction.BREAK, null);
            engine.allowsExplosion(key);
            engine.autoClaimEnabled(alice.id());
            engine.bypassEnabled(alice.id());
            engine.autoClaim(alice);
            engine.validationIssues();
            FormContext context = new FormContext(alice, "statecraft:forms",
                    "government setting <government> <key> <value>", Map.of(), FormQuery.INITIAL);
            new GovernanceForms(engine).describe(context, new FormBuilder(context));
        });
        quiet(() -> assertThrows(UserError.class, () -> run(alice, "unknown-command")));
    }

    @Test
    void everyPublicGovernanceAccessMutatorNotifiesAndIdempotentCallsStayQuiet() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String key = claim(alice, tree, 0, 0);
        String company = company(alice, "Builders");
        changedBy(() -> engine.transferProperty(key, bob.account()));
        quiet(() -> engine.transferProperty(key, bob.account()));
        changedBy(() -> engine.transferShares(company, alice.id(), bob.id(), 100));
        changedBy(() -> engine.reserveShares(company, alice.id(), "stock:one", 200));
        quiet(() -> engine.reserveShares(company, alice.id(), "stock:one", 200));
        changedBy(() -> engine.settleShares("stock:one", bob.id(), 50));
        assertEquals(150, data.shareReservations.get("stock:one").quantity);
        changedBy(() -> engine.settleShares("stock:one", cara.id(), 150));
        assertFalse(data.shareReservations.containsKey("stock:one"));
        changedBy(() -> engine.reserveShares(company, alice.id(), "stock:two", 100));
        changedBy(() -> engine.releaseShares("stock:two"));
        quiet(() -> engine.releaseShares("stock:two"));
        changedBy(() -> engine.mail(bob.id(), "System", "Body"));
        changedBy(() -> engine.governmentMail(tree.nation(), "Official", "Body"));
        data.shareReservations.put("stock:malformed", null);
        changedBy(() -> engine.releaseShares("stock:malformed"));
        assertFalse(data.shareReservations.containsKey("stock:malformed"));
    }

    @Test
    void improvementAutoclaimAndPermissionRevocationNotifyOnlyActualChanges() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        changedBy(() -> engine.recordImprovement(key, 1));
        quiet(() -> engine.recordImprovement(key, 0));
        changedBy(() -> engine.recordImprovement(key, Integer.MAX_VALUE));
        quiet(() -> engine.recordImprovement(key, 1));
        changedBy(() -> engine.recordImprovement(key, Integer.MIN_VALUE));
        quiet(() -> engine.recordImprovement(key, -1));
        quiet(() -> engine.recordImprovement("minecraft:overworld|50|50", 1));
        mutate(alice, "chunk autoclaim on");
        quiet(() -> run(alice, "chunk autoclaim on"));
        changedBy(() -> engine.autoClaim(at(alice, "minecraft:overworld", 1, 0)));
        quiet(() -> engine.autoClaim(at(alice, "minecraft:overworld", 1, 0)));
        mutate(alice, "chunk autoclaim off");
        mutate(operator, "admin bypass on");
        quiet(() -> run(operator, "admin bypass on"));
        changedBy(() -> engine.mayAct(operator(operator, false), key, AccessAction.BREAK, null));
        assertFalse(data.players.get(operator.id().toString()).bypass);
        quiet(() -> engine.mayAct(operator(operator, false), key, AccessAction.BREAK, null));
    }

    @Test
    void governmentMembershipRolesPoliciesAndDeletionNotify() {
        mutate(alice, "nation create NotifyNation");
        String nation = government("NotifyNation").id();
        mutate(alice, "state create " + nation + " NotifyState");
        String state = government("NotifyState").id();
        mutate(alice, "city create " + state + " NotifyCity");
        String city = government("NotifyCity").id();
        for (String id : List.of(nation, state, city)) mutate(alice, "government setting " + id + " open true");
        mutate(alice, "government rename " + nation + " RenamedNation");
        mutate(alice, "government description " + nation + " Description");
        mutate(alice, "government tag " + nation + " TAG");
        mutate(alice, "government flag " + nation + " Blue");
        mutate(alice, "nation invite " + nation + " Bob");
        mutate(bob, "nation decline " + nation);
        mutate(alice, "nation invite " + nation + " Bob");
        mutate(alice, "nation revoke " + nation + " Bob");
        mutate(alice, "nation invite " + nation + " Bob");
        mutate(bob, "nation accept " + nation);
        mutate(bob, "state join " + state);
        mutate(bob, "city join " + city);
        mutate(alice, "nation officer " + nation + " Bob add");
        mutate(alice, "nation officer " + nation + " Bob remove");
        mutate(alice, "nation leader " + nation + " Bob");
        mutate(bob, "nation leader " + nation + " Alice");
        mutate(alice, "city kick " + city + " Bob");
        mutate(bob, "city join " + city);
        mutate(bob, "state leave " + state);
        mutate(bob, "nation leave " + nation);
        mutate(alice, "government setting " + nation + " pvp true");
        mutate(alice, "government setting " + nation + " pvp inherit");
        mutate(alice, "government disband " + nation + " cascade");
        assertTrue(data.governments.isEmpty());
    }

    @Test
    void companyMembershipRolesEquityAndDeletionNotify() {
        mutate(alice, "company create Builders");
        String company = engine.company("Builders").orElseThrow().id();
        mutate(alice, "company rename " + company + " Contractors");
        mutate(alice, "company description " + company + " Description");
        mutate(alice, "company invite " + company + " Bob");
        mutate(bob, "company decline " + company);
        mutate(alice, "company invite " + company + " Bob");
        mutate(alice, "company revoke " + company + " Bob");
        mutate(alice, "company invite " + company + " Bob");
        mutate(bob, "company accept " + company);
        mutate(alice, "company officer " + company + " Bob add");
        mutate(alice, "company officer " + company + " Bob remove");
        mutate(alice, "company transfer " + company + " Bob 100");
        mutate(alice, "company owner " + company + " Bob");
        mutate(bob, "company owner " + company + " Alice");
        mutate(bob, "company leave " + company);
        mutate(alice, "company invite " + company + " Bob");
        mutate(bob, "company accept " + company);
        mutate(alice, "company kick " + company + " Bob");
        mutate(bob, "company transfer " + company + " Alice 100");
        mutate(alice, "company disband " + company);
        assertTrue(data.companies.isEmpty());
    }

    @Test
    void personalAndOfficialMailReadDeleteSendAndReplyNotify() {
        Tree tree = tree(alice, "Alpha");
        mutate(alice, "mail send Bob Subject Body");
        String personal = data.players.get(bob.id().toString()).inbox.get(0).id;
        mutate(bob, "mail read " + personal);
        quiet(() -> run(bob, "mail read " + personal));
        mutate(bob, "mail reply " + personal + " Reply");
        mutate(bob, "mail delete " + personal);
        quiet(() -> run(alice, "mail read " + personal));
        mutate(alice, "mail delete " + personal);
        mutate(bob, "mail send government:" + tree.nation() + " Petition Body");
        String official = data.governments.get(tree.nation()).inbox.get(0).id;
        mutate(alice, "mail official read " + tree.nation() + " " + official);
        quiet(() -> run(alice, "mail official read " + tree.nation() + " " + official));
        mutate(alice, "mail official reply " + tree.nation() + " " + official + " Reply");
        mutate(alice, "mail official delete " + tree.nation() + " " + official);
        mutate(alice, "mail official send " + tree.nation() + " Cara Subject Body");
    }

    @Test
    void readSideLegacyInitializationNotifiesExactlyWhenItCreatesPersistedState() {
        Tree tree = tree(alice, "Alpha");
        data.elections.remove(tree.nation());
        mutate(alice, "election status " + tree.nation());
        quiet(() -> run(alice, "election status " + tree.nation()));
        String company = company(alice, "Builders");
        run(alice, "company propose " + company + " roleplay - Charter Text");
        String proposal = latestCompanyProposal();
        data.companyProposals.get(proposal).executionEndsAt = 0;
        mutate(alice, "company proposal " + proposal);
        assertTrue(data.companyProposals.get(proposal).executionEndsAt > 0);
        quiet(() -> run(alice, "company proposal " + proposal));
    }

    @Test
    void scheduledAndReadMutationsAreNotLostWhenTheRequestedCommandThenFails() {
        Actor newcomer = actor(7, "NewCitizen", false);
        changedBy(() -> assertThrows(UserError.class, () -> run(newcomer, "unknown-command")));
        assertTrue(data.players.containsKey(newcomer.id().toString()));
        Tree tree = tree(alice, "Alpha");
        run(alice, "nation invite " + tree.nation() + " Bob");
        time.addAndGet(config.invitationDurationMillis);
        changedBy(() -> assertThrows(UserError.class, () -> run(bob,
                "nation rename " + tree.nation() + " Unauthorized")));
        assertTrue(data.invitations.isEmpty());
        quiet(() -> assertThrows(UserError.class, () -> run(bob,
                "nation rename " + tree.nation() + " Unauthorized")));
        data.elections.remove(tree.nation());
        changedBy(() -> assertThrows(UserError.class, () -> run(alice,
                "election status " + tree.nation() + " unexpected")));
        assertTrue(data.elections.containsKey(tree.nation()));
    }

    @Test
    void removalsOfMalformedNullRecordsNotifyEvenWhenValidationThenThrows() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        data.contracts.get(contract).bids.put(bob.id().toString(), null);
        changedBy(() -> assertThrows(UserError.class, () -> run(bob, "contract withdraw " + contract)));
        assertFalse(data.contracts.get(contract).bids.containsKey(bob.id().toString()));
        data.emergencies.put(tree.nation(), null);
        changedBy(() -> assertThrows(UserError.class, () -> run(alice, "executive rescind " + tree.nation())));
        assertFalse(data.emergencies.containsKey(tree.nation()));
    }

    @Test
    void electionsNotifyForCandidateChangesVotesAndScheduledTransitions() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        mutate(alice, "election candidate " + tree.nation());
        mutate(alice, "election withdraw " + tree.nation());
        mutate(bob, "election candidate " + tree.nation());
        changedBy(() -> advance(config.electionIntervalMillis));
        mutate(alice, "election vote " + tree.nation() + " Bob");
        changedBy(() -> advance(config.electionVotingMillis));
        assertEquals(bob.id(), government(tree.nation()).leader());
        mutate(operator, "election start " + tree.nation());
        mutate(operator, "election close " + tree.nation());
        mutate(operator, "election cancel " + tree.nation());
        quiet(() -> engine.tick(time.incrementAndGet()));
    }

    @Test
    void legislationVotesOverridesEnactmentAndEmergencyExpiryNotify() {
        Tree tree = tree(alice, "Alpha");
        mutate(alice, "bill propose " + tree.nation() + " pvp true Combat Text");
        String cancelled = latestBill();
        mutate(alice, "bill revise " + cancelled + " false Peace Text");
        mutate(alice, "bill cancel " + cancelled);
        mutate(alice, "bill propose " + tree.nation() + " pvp true Combat Text");
        String bill = latestBill();
        changedBy(() -> advance(config.debateDurationMillis));
        mutate(alice, "bill vote " + bill + " yes");
        changedBy(() -> advance(config.legislativeVotingMillis));
        mutate(alice, "bill veto " + bill + " Reason");
        mutate(alice, "bill override " + bill);
        mutate(alice, "bill vote " + bill + " yes");
        changedBy(() -> advance(config.legislativeVotingMillis));
        assertEquals("ENACTED", data.bills.get(bill).status);
        mutate(alice, "executive emergency " + tree.nation() + " pvp false Reason");
        changedBy(() -> advance(config.emergencyDurationMillis));
        assertTrue(data.emergencies.isEmpty());
        quiet(() -> engine.tick(time.incrementAndGet()));
        quiet(() -> advance(config.emergencyCooldownMillis));
        mutate(alice, "executive emergency " + tree.nation() + " pvp false Reason");
        mutate(alice, "executive rescind " + tree.nation());
    }

    @Test
    void shareholderVotesScheduledFailuresAndSuccessfulRetriesNotify() {
        String company = company(alice, "Builders");
        useEconomy();
        mutate(alice, "company propose " + company + " dividend 1 Dividend Text");
        String proposal = latestCompanyProposal();
        mutate(alice, "company vote " + proposal + " yes");
        changedBy(() -> advance(config.companyVotingMillis));
        assertEquals("READY", data.companyProposals.get(proposal).status);
        assertFalse(data.companyProposals.get(proposal).lastError.isEmpty());
        economy.balances.put("company:" + company, 100L);
        mutate(alice, "company execute " + proposal);
        assertEquals("ENACTED", data.companyProposals.get(proposal).status);
        mutate(alice, "company propose " + company + " roleplay - Cancelled Text");
        mutate(alice, "company cancel " + latestCompanyProposal());
        quiet(() -> engine.tick(time.incrementAndGet()));
    }

    @Test
    void contractCommandsAndScheduledExpiryNotifyIncludingBidWithdrawal() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        mutate(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        mutate(bob, "contract bid " + contract + " 0 Work");
        mutate(bob, "contract withdraw " + contract);
        mutate(bob, "contract bid " + contract + " 0 Work");
        mutate(alice, "contract review " + contract);
        mutate(alice, "contract award " + contract + " Bob");
        mutate(bob, "contract submit " + contract + " Done");
        mutate(alice, "contract return " + contract + " Corrections");
        mutate(bob, "contract submit " + contract + " Done");
        mutate(alice, "contract complete " + contract);
        mutate(alice, "contract create " + tree.nation() + " Cancelled Work " + key);
        mutate(alice, "contract cancel " + latestContract() + " Reason");
        mutate(alice, "contract create " + tree.nation() + " Expiring Work " + key);
        String expiring = latestContract();
        changedBy(() -> advance(config.contractBiddingMillis));
        assertEquals("EXPIRED", data.contracts.get(expiring).status);
        quiet(() -> engine.tick(time.incrementAndGet()));
    }

    @Test
    void diplomacyCreationCancellationAcceptanceRatificationAndSettlementNotify() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        mutate(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        mutate(dave, "diplomacy reject " + latestDiplomacy());
        mutate(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        mutate(alice, "diplomacy cancel " + latestDiplomacy());
        mutate(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        mutate(dave, "diplomacy accept " + latestDiplomacy());
        mutate(alice, "diplomacy break " + alpha.nation() + " " + beta.nation());
        mutate(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        mutate(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        mutate(dave, "diplomacy accept " + treaty);
        changedBy(() -> advance(config.debateDurationMillis));
        mutate(alice, "diplomacy ratify " + treaty + " yes");
        mutate(dave, "diplomacy ratify " + treaty + " yes");
        changedBy(() -> advance(config.legislativeVotingMillis));
        assertEquals("ENACTED", data.diplomacy.get(treaty).status);
        assertEquals(beta.city(), data.claims.get(key).cityId);
        changedBy(() -> advance(config.truceDurationMillis));
        assertTrue(data.relations.isEmpty());
        quiet(() -> engine.tick(time.incrementAndGet()));
    }

    @Test
    void administratorReassignmentTitleChangesAndRepairNotify() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        mutate(alice, "chunk permit " + key + " Bob BREAK");
        mutate(alice, "chunk permit " + key + " Bob none");
        mutate(operator, "admin reassign " + key + " " + beta.city());
        mutate(operator, "admin owner " + key + " " + dave.account());
        mutate(operator, "admin rename " + alpha.city() + " RenamedCity");
        mutate(operator, "admin leader " + alpha.city() + " Bob");
        assertEquals(alpha.city(), data.players.get(bob.id().toString()).cityId);
        mutate(operator, "admin unclaim " + key);
        data.players.get(cara.id().toString()).autoClaim = true;
        quiet(() -> run(operator, "admin repair preview"));
        mutate(operator, "admin repair apply");
        assertFalse(data.players.get(cara.id().toString()).autoClaim);
        mutate(operator, "admin delete nation " + alpha.nation() + " cascade");
    }

    @Test
    void historyCodexAndMailboxRetentionNotifyOnlyWhenTrimmingOccurs() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "bill propose " + tree.nation() + " roleplay - Charter Text");
        String bill = latestBill();
        run(alice, "bill cancel " + bill);
        data.history.clear();
        data.bills.get(bill).history.clear();
        data.governments.get(tree.nation()).inbox.clear();
        config.maxHistory = 1;
        config.maxMail = 1;
        config.maxLawsPerNation = 1;
        configure();
        trackRetention(data.history, new GovernanceData.History());
        trackRetention(data.elections.get(tree.nation()).history, new GovernanceData.History());
        trackRetention(data.bills.get(bill).history, new GovernanceData.History());
        data.laws.put(tree.nation(), new ArrayList<>());
        trackRetention(data.laws.get(tree.nation()), new GovernanceData.Law());
        trackRetention(data.players.get(alice.id().toString()).inbox, new GovernanceData.Mail());
        trackRetention(data.players.get(alice.id().toString()).sent, new GovernanceData.Mail());
        trackRetention(data.governments.get(tree.nation()).inbox, new GovernanceData.Mail());
        trackRetention(data.governments.get(tree.nation()).sent, new GovernanceData.Mail());
    }

    @Test
    void concludedWorkflowRetentionNotifiesForEachRecordFamilyWithoutOtherMutations() {
        Tree tree = tree(alice, "Alpha");
        String company = company(alice, "Builders");
        data.history.clear();
        config.maxHistory = 1;
        configure();
        for (int index = 0; index < 2; index++) {
            GovernanceData.Bill bill = new GovernanceData.Bill();
            bill.id = GovernanceEngine.newId();
            bill.nationId = tree.nation();
            bill.policy = "roleplay";
            bill.value = "-";
            bill.status = "CANCELLED";
            data.bills.put(bill.id, bill);
        }
        changedBy(() -> engine.tick(time.get()));
        assertEquals(1, data.bills.size());
        quiet(() -> engine.tick(time.get()));
        for (int index = 0; index < 2; index++) {
            GovernanceData.DiplomaticProposal proposal = new GovernanceData.DiplomaticProposal();
            proposal.id = GovernanceEngine.newId();
            proposal.type = "ALLIANCE";
            proposal.fromNation = tree.nation();
            proposal.toNation = GovernanceEngine.newId();
            proposal.status = "ENACTED";
            data.diplomacy.put(proposal.id, proposal);
        }
        changedBy(() -> engine.tick(time.get()));
        assertEquals(1, data.diplomacy.size());
        quiet(() -> engine.tick(time.get()));
        for (int index = 0; index < 2; index++) {
            GovernanceData.CompanyProposal proposal = new GovernanceData.CompanyProposal();
            proposal.id = GovernanceEngine.newId();
            proposal.companyId = company;
            proposal.type = "roleplay";
            proposal.value = "-";
            proposal.status = "CANCELLED";
            data.companyProposals.put(proposal.id, proposal);
        }
        changedBy(() -> engine.tick(time.get()));
        assertEquals(1, data.companyProposals.size());
        quiet(() -> engine.tick(time.get()));
        for (int index = 0; index < 2; index++) {
            GovernanceData.Contract contract = new GovernanceData.Contract();
            contract.id = GovernanceEngine.newId();
            contract.governmentId = tree.nation();
            contract.status = "COMPLETED";
            data.contracts.put(contract.id, contract);
        }
        changedBy(() -> engine.tick(time.get()));
        assertEquals(1, data.contracts.size());
        quiet(() -> engine.tick(time.get()));
    }

    private <T> void trackRetention(List<T> entries, T example) {
        entries.add(example);
        entries.add(example);
        changedBy(() -> engine.tick(time.get()));
        assertEquals(1, entries.size());
        quiet(() -> engine.tick(time.get()));
    }

    private void mutate(Actor actor, String command) {
        int before = changes.get();
        run(actor, command);
        assertTrue(changes.get() > before, command);
    }

    private void changedBy(Runnable operation) {
        int before = changes.get();
        operation.run();
        assertTrue(changes.get() > before, "Persisted mutation must notify its observer.");
    }

    private void quiet(Runnable operation) {
        int before = changes.get();
        operation.run();
        assertEquals(before, changes.get(), "Read-only/idempotent operation must not notify its observer.");
    }
}
