package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormConstraints;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceFormsTest extends DomainFixture {
    private static final String STATE = "state create <nation> <name> <governor>";
    private static final String CITY = "city create <state> <name> <mayor>";

    @Test
    void stateParentsRequireActualCreationAuthorityNotJustCitizenship() {
        String nation = nation(alice, "AlphaNation");
        run(bob, "nation join " + nation);
        assertTrue(catalog(form(felix, STATE), "nation").isEmpty());
        assertTrue(catalog(form(bob, STATE), "nation").isEmpty());
        assertEquals(Set.of(nation), catalog(form(alice, STATE), "nation"));
        run(alice, "nation officer " + nation + " Bob add");
        FormBuilder officer = form(bob, STATE);
        assertEquals(nation, officer.value("nation"));
        assertEquals(bob.id().toString(), officer.value("governor"));
        assertEquals(Set.of(alice.id().toString(), bob.id().toString()), catalog(officer, "governor"));
        assertEquals("", officer.value("name"));
        assertEquals(FormField.Kind.TEXT, field(officer, "name").kind());
        assertEquals(List.of("nation"), field(officer, "governor").dependencies());
    }

    @Test
    void stateGovernorIsLimitedToUnassignedCitizensOfTheResolvedNation() {
        String alpha = nation(alice, "AlphaNation");
        String beta = nation(dave, "BetaNation");
        run(bob, "nation join " + alpha);
        run(cara, "nation join " + alpha);
        run(alice, "state create " + alpha + " ExistingState Cara");
        FormBuilder result = form(alice, STATE, Map.of("nation", alpha));
        assertEquals(Set.of(alice.id().toString(), bob.id().toString()), catalog(result, "governor"));
        assertFalse(catalog(result, "governor").contains(cara.id().toString()));
        assertFalse(catalog(result, "nation").contains(beta));
        assertEquals(alice.id().toString(), result.value("governor"));
        assertEquals("Bob", result.choices("governor").stream()
                .filter(c -> c.value().equals(bob.id().toString())).findFirst().orElseThrow().label());
    }

    @Test
    void changingNationClearsTheOldGovernorAndInvalidParentDoesNotFallBack() {
        String alpha = nation(alice, "AlphaNation");
        String beta = nation(dave, "BetaNation");
        run(bob, "nation join " + alpha);
        FormBuilder first = form(operator, STATE, Map.of("nation", alpha, "governor", bob.id().toString()));
        assertEquals(bob.id().toString(), first.value("governor"));
        FormBuilder changed = form(operator, STATE, Map.of("nation", beta, "governor", bob.id().toString()));
        assertEquals(beta, changed.value("nation"));
        assertEquals("", changed.value("governor"));
        assertEquals(Set.of(dave.id().toString()), catalog(changed, "governor"));
        FormBuilder unauthorized = form(alice, STATE, Map.of("nation", beta, "governor", dave.id().toString()));
        assertEquals("", unauthorized.value("nation"));
        assertEquals("", unauthorized.value("governor"));
        assertTrue(catalog(unauthorized, "governor").isEmpty());
    }

    @Test
    void structuralCreationCapacityAppliesToOperatorsToo() {
        String nation = nation(alice, "AlphaNation");
        config.maxStatesPerNation = 1;
        configure();
        run(alice, "state create " + nation + " FirstState");
        assertTrue(catalog(form(alice, STATE), "nation").isEmpty());
        assertTrue(catalog(form(operator, STATE), "nation").isEmpty());
        assertFalse(field(form(operator, STATE), "nation").hint().isBlank());
    }

    @Test
    void cityParentsAndMayorsUseStateScopeAndExcludeExistingCityMembers() {
        String nation = nation(alice, "AlphaNation");
        run(bob, "nation join " + nation);
        run(cara, "nation join " + nation);
        run(alice, "state create " + nation + " AlphaState Bob");
        String state = government("AlphaState").id();
        run(bob, "state setting " + state + " open true");
        run(cara, "state join " + state);
        assertTrue(catalog(form(cara, CITY), "state").isEmpty());
        FormBuilder mayor = form(bob, CITY);
        assertEquals(state, mayor.value("state"));
        assertEquals(bob.id().toString(), mayor.value("mayor"));
        assertEquals(Set.of(bob.id().toString(), cara.id().toString()), catalog(mayor, "mayor"));
        assertFalse(catalog(mayor, "mayor").contains(alice.id().toString()));
        run(bob, "city create " + state + " FirstCity Bob");
        assertEquals(Set.of(cara.id().toString()), catalog(form(alice, CITY), "mayor"));
        assertEquals(cara.id().toString(), form(alice, CITY).value("mayor"));
        assertEquals(List.of("state"), field(form(alice, CITY), "mayor").dependencies());
    }

    @Test
    void changedStateClearsStaleMayorAndRespectsSiblingBoundaries() {
        String nation = nation(alice, "AlphaNation");
        run(bob, "nation join " + nation);
        run(cara, "nation join " + nation);
        run(alice, "state create " + nation + " FirstState Bob");
        run(alice, "state create " + nation + " SecondState Cara");
        String first = government("FirstState").id();
        String second = government("SecondState").id();
        assertEquals(Set.of(first), catalog(form(bob, CITY), "state"));
        FormBuilder changed = form(alice, CITY, Map.of("state", second, "mayor", bob.id().toString()));
        assertEquals("", changed.value("mayor"));
        assertEquals(Set.of(cara.id().toString()), catalog(changed, "mayor"));
    }

    @Test
    void revokedOfficerAndOperatorPermissionsClearPreviousSelectionsImmediately() {
        String nation = nation(alice, "AlphaNation");
        run(bob, "nation join " + nation);
        run(alice, "nation officer " + nation + " Bob add");
        assertFalse(catalog(form(bob, STATE), "nation").isEmpty());
        run(alice, "nation officer " + nation + " Bob remove");
        FormBuilder revoked = form(bob, STATE, Map.of("nation", nation, "governor", bob.id().toString()));
        assertEquals("", revoked.value("nation"));
        assertEquals("", revoked.value("governor"));
        run(operator, "admin bypass on");
        assertFalse(catalog(form(operator, STATE), "nation").isEmpty());
        Actor noLongerOperator = operator(operator, false);
        assertTrue(catalog(form(noLongerOperator, STATE), "nation").isEmpty());
    }

    @Test
    void selectedStateAndCityCreationValuesAreAcceptedByTheActualCommand() {
        String nation = nation(alice, "AlphaNation");
        FormBuilder state = form(alice, STATE, Map.of("name", "New State"));
        assertEquals(nation, state.value("nation"));
        run(alice, render(STATE, state));
        assertEquals("New State", government("New State").name());
        FormBuilder city = form(alice, CITY, Map.of("name", "New City"));
        run(alice, render(CITY, city));
        assertEquals(government("New State").id(), government("New City").parentId());
        assertEquals(alice.id(), government("New City").leader());
    }

    @Test
    void governmentMembershipAndLeadershipChoicesMatchActionScope() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        String appoint = "government leader <government> <player>";
        assertTrue(catalog(form(bob, appoint), "government").isEmpty());
        FormBuilder leaders = form(alice, appoint, Map.of("government", tree.nation()));
        assertEquals(Set.of(bob.id().toString(), cara.id().toString()), catalog(leaders, "player"));
        String remove = "government officer <government> <player> remove";
        assertEquals(Set.of(bob.id().toString()),
                catalog(form(alice, remove, Map.of("government", tree.nation())), "player"));
        assertTrue(catalog(form(alice, "nation leave <nation>"), "nation").isEmpty());
        assertTrue(catalog(form(felix, "nation join <nation>"), "nation").contains(tree.nation()));
    }

    @Test
    void invitationSelectorsOnlyOfferActualLiveInvitesOrEligibleRecipients() {
        String nation = nation(alice, "AlphaNation");
        run(alice, "nation setting " + nation + " open false");
        assertTrue(catalog(form(bob, "nation accept <nation>"), "nation").isEmpty());
        run(alice, "nation invite " + nation + " Bob");
        assertEquals(Set.of(nation), catalog(form(bob, "nation accept <nation>"), "nation"));
        assertTrue(catalog(form(cara, "nation accept <nation>"), "nation").isEmpty());
        assertEquals(Set.of(bob.id().toString()),
                catalog(form(alice, "government revoke <government> <player>", Map.of("government", nation)), "player"));
        time.addAndGet(config.invitationDurationMillis + 1);
        assertTrue(catalog(form(bob, "nation accept <nation>"), "nation").isEmpty());
        assertFalse(data.invitations.isEmpty(), "Describing expiry must not tick or delete invitations.");
    }

    @Test
    void formRequestsNeverTickChargeOrRefreshActorNames() {
        Tree tree = tree(alice, "Alpha");
        String json = new Gson().toJson(data);
        useEconomy();
        json = new Gson().toJson(data);
        int attempts = economy.attempts;
        time.addAndGet(100_000);
        Actor renamed = new Actor(alice.id(), "NotLoggedInName", alice.admin(), alice.dimension(), 0, 0);
        for (int i = 0; i < 3; i++) {
            form(renamed, STATE);
            form(renamed, "government setting <government> <key> <value>",
                    Map.of("government", tree.nation(), "key", "foreignAccess"));
            form(renamed, "election vote <nation> <candidate>");
        }
        assertEquals(json, new Gson().toJson(data));
        assertEquals(attempts, economy.attempts);
        assertEquals("Alice", data.players.get(alice.id().toString()).name);
    }

    @Test
    void mailboxIdsArePrivateAndOfficialChoicesRequireCurrentAuthority() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "mail send Bob Classified \"Secret body must never be in a selector.\"");
        var mail = data.players.get(bob.id().toString()).inbox.get(0);
        FormBuilder own = form(bob, "mail read <message>");
        assertEquals(FormField.Kind.CHOICE, field(own, "message").kind());
        assertEquals(Set.of(mail.id), catalog(own, "message"));
        assertEquals("Classified", own.choices("message").get(0).label());
        assertFalse(own.build().toString().contains("Secret body"));
        assertTrue(catalog(form(operator, "mail read <message>"), "message").isEmpty());
        assertEquals("", form(cara, "mail read <message>", Map.of("message", mail.id)).value("message"));
        engine.governmentMail(tree.nation(), "Official notice", "Official secret body.");
        String official = data.governments.get(tree.nation()).inbox.get(0).id;
        String read = "mail official read <government> <message>";
        assertTrue(catalog(form(bob, read, Map.of("government", tree.nation())), "message").isEmpty());
        run(alice, "nation officer " + tree.nation() + " Bob add");
        FormBuilder permitted = form(bob, read, Map.of("government", tree.nation()));
        assertEquals(Set.of(official), catalog(permitted, "message"));
        assertFalse(permitted.build().toString().contains("Official secret body"));
        assertFalse(data.governments.get(tree.nation()).inbox.get(0).read);
    }

    @Test
    void misplacedAndSystemMailAreNotExposedAsReplyTargets() {
        run(alice, "mail send Cara Other \"Other person's content.\"");
        var wrongEnvelope = data.players.get(cara.id().toString()).inbox.get(0);
        data.players.get(bob.id().toString()).inbox.add(wrongEnvelope);
        engine.mail(bob.id(), "System event", "A system notification.");
        assertFalse(catalog(form(bob, "mail read <message>"), "message").contains(wrongEnvelope.id));
        assertTrue(catalog(form(bob, "mail reply <message> <body>"), "message").isEmpty());
    }

    @Test
    void companySelectorsDistinguishMembersManagersOwnersAndShareholders() {
        String company = company(alice, "Builder Guild");
        employ(alice, bob, company);
        assertTrue(catalog(form(bob, "company invite <company> <player>"), "company").isEmpty());
        engine.transferShares(company, alice.id(), bob.id(), 100);
        assertEquals(Set.of(company), catalog(form(bob, "company transfer <company> <player> <shares>"), "company"));
        FormBuilder owner = form(alice, "company owner <company> <player>");
        assertEquals(Set.of(bob.id().toString()), catalog(owner, "player"));
        run(alice, "company officer " + company + " Bob add");
        assertEquals(Set.of(company), catalog(form(bob, "company invite <company> <player>"), "company"));
        assertTrue(catalog(form(bob, "company owner <company> <player>"), "company").isEmpty());
        FormBuilder proposal = form(alice, "company propose <company> <type> <value> <title> <text>",
                Map.of("company", company, "type", "owner"));
        assertTrue(catalog(proposal, "value").contains(bob.id().toString()));
        FormBuilder dividend = form(alice, "company propose <company> <type> <value> <title> <text>",
                Map.of("company", company, "type", "dividend"));
        assertEquals("", dividend.value("value"), "Never preselect a payment.");
    }

    @Test
    void economyCommonFieldsDoNotGrantTreasuryAccessToOrdinaryCompanyMembers() {
        String company = company(alice, "Builder Guild");
        employ(alice, bob, company);
        FormBuilder result = form(bob, "economy:banking",
                "bank transfer <fromAccount> <toAccount> <companyAccount> <company> <recipient>",
                Map.of(), FormQuery.INITIAL);
        assertEquals(Set.of(bob.account()), catalog(result, "fromAccount"));
        assertTrue(catalog(result, "companyAccount").isEmpty());
        assertTrue(catalog(result, "company").isEmpty());
        assertTrue(catalog(result, "toAccount").contains("company:" + company));
        assertTrue(catalog(result, "recipient").contains(alice.id().toString()));
        assertTrue(catalog(result, "toAccount").stream().noneMatch(a -> a.startsWith("escrow:") || a.startsWith("system:")));
    }

    @Test
    void electionAndBallotSelectorsUseEligibilityAndNeverPreselectVotes() {
        String nation = nation(alice, "AlphaNation");
        run(bob, "nation join " + nation);
        run(alice, "election candidate " + nation);
        run(bob, "election candidate " + nation);
        assertTrue(catalog(form(alice, "election vote <nation> <candidate>"), "nation").isEmpty());
        advance(config.electionIntervalMillis);
        FormBuilder election = form(alice, "election vote <nation> <candidate>");
        assertEquals(Set.of(alice.id().toString(), bob.id().toString()), catalog(election, "candidate"));
        assertTrue(catalog(form(felix, "election vote <nation> <candidate>"), "nation").isEmpty());
        run(alice, "election vote " + nation + " Bob");
        assertTrue(catalog(form(alice, "election vote <nation> <candidate>"), "nation").isEmpty());
        FormBuilder vote = form(alice, "bill vote <bill> <yes_no_abstain>");
        assertEquals("", vote.value("yes_no_abstain"));
        assertEquals(Set.of("yes", "no", "abstain"), catalog(vote, "yes_no_abstain"));
    }

    @Test
    void policiesAreTypedAndPrefilledOnlyWhenEditingWithoutReplacingUserText() {
        String nation = nation(alice, "AlphaNation");
        String command = "government setting <government> <key> <value>";
        FormBuilder editing = form(alice, command, Map.of("government", nation, "key", "foreignAccess"));
        assertEquals("false", editing.value("value"));
        assertEquals(Set.of("true", "false", "inherit"), catalog(editing, "value"));
        assertEquals(Set.of("government", "key"), Set.copyOf(field(editing, "value").dependencies()));
        FormBuilder typed = form(alice, command, Map.of("government", nation, "key", "incomeTaxBps", "value", "123"));
        assertEquals("123", typed.value("value"));
        assertEquals(FormField.Kind.TEXT, field(typed, "value").kind());
        FormBuilder proposing = form(alice, "bill propose <nation> <policy> <value> <title> <text>",
                Map.of("nation", nation, "policy", "foreignAccess", "title", "My title", "text", "My\ntext"));
        assertEquals("", proposing.value("value"));
        assertEquals("My\ntext", proposing.value("text"));
        config.requireLegislationForPolicy = true;
        configure();
        assertEquals(Set.of("open"), catalog(form(alice, command, Map.of("government", nation)), "key"));
    }

    @Test
    void legislationOnlyFormsRestrictNationalPoliciesButKeepAuthorizedLocalPolicies() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "state leader " + tree.state() + " Bob");
        run(alice, "city leader " + tree.city() + " Cara");
        config.requireLegislationForPolicy = true;
        configure();
        String command = "government setting <government> <key> <value>";
        FormBuilder national = form(alice, command, Map.of("government", tree.nation()));
        assertEquals(Set.of("open"), catalog(national, "key"));
        assertTrue(field(national, "key").hint().contains("National mechanical policies require bills"));
        FormBuilder state = form(bob, command, Map.of("government", tree.state(), "key", "incomeTaxBps"));
        assertTrue(catalog(state, "key").contains("incomeTaxBps"));
        assertEquals("0", state.value("value"));
        assertTrue(field(state, "key").hint().contains("only nationally"));
        FormBuilder city = form(cara, command, Map.of("government", tree.city(), "key", "propertyTaxBps"));
        assertTrue(catalog(city, "key").contains("propertyTaxBps"));
        assertEquals(Set.of(tree.city()), catalog(city, "government"));
        assertTrue(catalog(form(dave, command, Map.of("government", tree.city())), "government").isEmpty());
        assertTrue(catalog(form(cara, command, Map.of("government", tree.state())), "key").isEmpty());
        assertTrue(catalog(form(operator, command, Map.of("government", tree.nation())), "key").contains("incomeTaxBps"));
        String bills = "bill propose <nation> <policy> <value> <title> <text>";
        assertFalse(catalog(form(bob, bills), "nation").contains(tree.state()));
        assertFalse(catalog(form(cara, bills), "nation").contains(tree.city()));
    }

    @Test
    void kickFormsCompareCallerAuthorityAndStillExcludeAffectedLeaders() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        run(alice, "city leader " + tree.city() + " Cara");
        String command = "city kick <city> <player>";
        assertTrue(catalog(form(alice, command, Map.of("city", tree.city())), "player").contains(bob.id().toString()));
        assertFalse(catalog(form(cara, command, Map.of("city", tree.city())), "player").contains(bob.id().toString()));
        assertFalse(catalog(form(alice, command, Map.of("city", tree.city())), "player").contains(cara.id().toString()));
        run(alice, "city leader " + tree.city() + " Bob");
        assertFalse(catalog(form(alice, command, Map.of("city", tree.city())), "player").contains(bob.id().toString()));
    }

    @Test
    void historicalBidsStaySelectableAfterTheUnencumberedContractorCompanyDissolves() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String company = company(bob, "Builders");
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 0 Work company:" + company);
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        run(bob, "contract submit " + contract + " Done");
        run(alice, "contract complete " + contract);
        run(bob, "company disband " + company);
        String template = "contract bids <contract> <page>";
        FormBuilder retained = form(alice, template, Map.of("contract", contract));
        assertEquals(contract, retained.value("contract"));
        assertTrue(catalog(retained, "contract").contains(contract));
        assertTrue(run(alice, "contract bids " + contract).contains("company:" + company));
        assertTrue(catalog(form(cara, template), "contract").isEmpty());
        assertThrows(UserError.class, () -> run(cara, "contract bids " + contract));
        assertTrue(catalog(form(alice, "contract complete <contract>"), "contract").isEmpty());
    }

    @Test
    void constraintsMatchConfiguredTextLengthsMoneyAndDependentPolicyTypes() {
        Tree tree = tree(alice, "Alpha");
        String government = "government setting <government> <key> <value>";
        FormBuilder tax = form(alice, government, Map.of("government", tree.city(), "key", "incomeTaxBps"));
        FormConstraints rate = field(tax, "value").constraints();
        assertEquals(FormConstraints.Type.INTEGER, rate.type());
        assertEquals(0, rate.minimum());
        assertEquals(10_000, rate.maximum());
        assertEquals(List.of("inherit"), rate.alternatives());
        assertTrue(rate.error("10001", "Rate").isPresent());
        assertTrue(rate.error("inherit", "Rate").isEmpty());
        FormBuilder bill = form(alice, "bill propose <nation> <policy> <value> <title> <text>",
                Map.of("nation", tree.nation(), "policy", "incomeTaxBps"));
        assertTrue(field(bill, "value").constraints().alternatives().isEmpty());
        assertEquals(100, field(bill, "title").constraints().maxLength());
        assertEquals(config.maxDescriptionLength, field(bill, "text").constraints().maxLength());
        assertEquals(config.maxNameLength, field(form(alice, "company create <name>"), "name").constraints().maxLength());
        assertEquals(12, field(form(alice, "city tag <city> <tag>"), "tag").constraints().maxLength());
        assertEquals(128, field(form(alice, "city flag <city> <flag>"), "flag").constraints().maxLength());
        assertEquals(5, field(form(alice, "chunk map <radius>"), "radius").constraints().maximum());
        assertEquals(1_000_000, field(form(alice, "nation list <page>"), "page").constraints().maximum());
        assertEquals(config.maxMailBodyLength, field(form(alice, "mail send <recipient> <subject> <body>"), "body").constraints().maxLength());
        FormConstraints amount = field(form(bob, "contract bid <contract> <amount> <description>"), "amount").constraints();
        assertEquals(FormConstraints.Type.MONEY, amount.type());
        assertEquals(0, amount.minimum());
        assertTrue(amount.error("-1", "Bid").isPresent());
        assertTrue(amount.error("1.001", "Bid").isPresent());
        assertTrue(amount.error("0", "Bid").isEmpty());
    }

    @Test
    void constraintsFollowShareReservationsAndDividendsAndOfferDashOnlyWhereAccepted() {
        String company = company(alice, "Builders");
        engine.reserveShares(company, alice.id(), "held", 500);
        FormBuilder transfer = form(alice, "company transfer <company> <player> <shares>", Map.of("company", company));
        assertEquals(9_500, field(transfer, "shares").constraints().maximum());
        String template = "company propose <company> <type> <value> <title> <text>";
        FormConstraints dividend = field(form(alice, template, Map.of("company", company, "type", "dividend")), "value").constraints();
        assertEquals(FormConstraints.Type.MONEY, dividend.type());
        assertEquals(1, dividend.minimum());
        assertTrue(dividend.error("0", "Dividend").isPresent());
        assertTrue(dividend.alternatives().isEmpty());
        FormConstraints roleplay = field(form(alice, template, Map.of("company", company, "type", "roleplay")), "value").constraints();
        assertEquals(List.of("-"), roleplay.alternatives());
        FormConstraints terms = field(form(alice, "diplomacy peace <from_nation> <to_nation> <offer_amount> <demand_amount> <chunk_terms_or_dash> <message>"),
                "chunk_terms_or_dash").constraints();
        assertEquals(List.of("-"), terms.alternatives());
    }

    @Test
    void billsContractsAndPrivateClaimsOfferOnlyActionAppropriateTargets() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        String key = claim(alice, tree, 0, 0);
        run(alice, "bill propose " + tree.nation() + " roleplay - Charter Text");
        String bill = latestBill();
        assertTrue(catalog(form(alice, "bill sign <bill>"), "bill").isEmpty());
        advance(1_000);
        assertEquals(Set.of(bill), catalog(form(alice, "bill vote <bill> <yes_no_abstain>"), "bill"));
        assertTrue(catalog(form(bob, "bill vote <bill> <yes_no_abstain>"), "bill").isEmpty());
        run(alice, "bill vote " + bill + " yes");
        advance(1_000);
        assertEquals(Set.of(bill), catalog(form(alice, "bill sign <bill>"), "bill"));
        run(alice, "contract create " + tree.nation() + " Park \"Plant trees.\" " + key);
        String contract = latestContract();
        assertEquals(Set.of(contract), catalog(form(bob, "contract bid <contract> <amount> <description>"), "contract"));
        assertTrue(catalog(form(bob, "contract award <contract> <bidder>"), "contract").isEmpty());
        run(bob, "contract bid " + contract + " 0 Work");
        run(alice, "contract review " + contract);
        FormBuilder award = form(alice, "contract award <contract> <bidder>");
        assertEquals(Set.of(bob.id().toString()), catalog(award, "bidder"));
        run(alice, "contract cancel " + contract + " Cancelled");
        engine.transferProperty(key, bob.account());
        assertTrue(catalog(form(alice, "chunk permit <chunk> <player> <actions>"), "chunk").isEmpty());
        assertTrue(catalog(form(bob, "chunk permit <chunk> <player> <actions>"), "chunk").contains("here"));
    }

    @Test
    void parentSelectionOutsideDisplayedPageStillDrivesDependentChoices() {
        List<String> nations = new ArrayList<>();
        List<Actor> leaders = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            Actor leader = actor(100 + i, "Citizen" + i, false);
            engine.login(leader);
            leaders.add(leader);
            run(operator, "nation create Realm" + String.format(java.util.Locale.ROOT, "%02d", i) + " " + leader.name());
            nations.add(engine.nationOf(leader.id()).orElseThrow());
        }
        FormBuilder initial = form(operator, STATE, Map.of("nation", nations.get(26)));
        assertEquals(20, field(initial, "nation").choices().size());
        assertTrue(field(initial, "nation").more());
        assertEquals(nations.get(26), initial.value("nation"));
        assertEquals("Realm26", field(initial, "nation").selectedLabel());
        assertEquals(leaders.get(26).id().toString(), initial.value("governor"));
        FormBuilder next = form(operator, "statecraft:states", STATE, Map.of("nation", nations.get(0)),
                new FormQuery("nation", "", 20));
        assertEquals(20, field(next, "nation").offset());
        assertEquals(7, field(next, "nation").choices().size());
        assertFalse(field(next, "nation").more());
        assertEquals(nations.get(0), next.value("nation"));
        assertEquals(leaders.get(0).id().toString(), next.value("governor"));
        FormBuilder searched = form(operator, "statecraft:states", STATE, Map.of("nation", nations.get(0)),
                new FormQuery("nation", "Realm26", 0));
        assertEquals(1, field(searched, "nation").choices().size());
        assertEquals(nations.get(0), searched.value("nation"));
        assertEquals(leaders.get(0).id().toString(), searched.value("governor"));
    }

    @Test
    void everyRegisteredCoreActionHasAValidReadOnlyFormAndPaginationStaysNumeric() {
        Tree tree = tree(alice, "Alpha");
        claim(alice, tree, 0, 0);
        CoreMenus.register();
        String before = new Gson().toJson(data);
        for (var page : MenuRegistry.pages()) {
            if (!page.id().startsWith("statecraft:")) continue;
            for (var action : page.actions()) {
                FormBuilder builder = form(alice, page.id(), action.command(), Map.of(), FormQuery.INITIAL);
                assertEquals(new CommandTemplate(action.command()).fields().size(), builder.build().fields().size());
                if (builder.has("page")) {
                    assertEquals("1", builder.value("page"), action.command());
                    assertEquals(FormField.Kind.TEXT, field(builder, "page").kind(), action.command());
                }
            }
        }
        assertEquals(before, new Gson().toJson(data));
        assertEquals("3", form(alice, "chunk map <radius>").value("radius"));
        assertEquals(Set.of("nation", "state", "city"), catalog(form(operator, "admin delete <kind> <government>"), "kind"));
    }

    @Test
    void repairRequiredFormsRemainReadOnlyAndDoNotExposeOtherMail() {
        run(alice, "mail send Bob Private \"Must remain private.\"");
        GovernanceData.Claim orphan = new GovernanceData.Claim();
        orphan.key = "minecraft:overworld|90|90";
        orphan.cityId = UUID.randomUUID().toString();
        orphan.ownerAccount = "city:" + orphan.cityId;
        data.claims.put(orphan.key, orphan);
        engine = new GovernanceEngine(data, config, EconomyAccess.UNAVAILABLE, time::get);
        String before = new Gson().toJson(data);
        assertDoesNotThrow(() -> form(operator, "admin reassign <chunk> <city>"));
        assertTrue(catalog(form(cara, "mail read <message>"), "message").isEmpty());
        assertTrue(catalog(form(cara, STATE), "nation").isEmpty());
        assertEquals(before, new Gson().toJson(data));
    }

    private String nation(Actor actor, String name) {
        run(actor, "nation create " + name);
        String nation = government(name).id();
        run(actor, "nation setting " + nation + " open true");
        return nation;
    }

    private FormBuilder form(Actor actor, String command) {
        return form(actor, command, Map.of());
    }

    private FormBuilder form(Actor actor, String command, Map<String, String> values) {
        return form(actor, "statecraft:forms", command, values, FormQuery.INITIAL);
    }

    private FormBuilder form(Actor actor, String page, String command, Map<String, String> values, FormQuery query) {
        FormContext context = new FormContext(actor, page, command, values, query);
        FormBuilder builder = new FormBuilder(context);
        new GovernanceForms(engine).describe(context, builder);
        return builder;
    }

    private FormField field(FormBuilder builder, String key) {
        return builder.build().field(key).orElseThrow();
    }

    private Set<String> catalog(FormBuilder builder, String key) {
        return builder.choices(key).stream().map(FormChoice::value).collect(Collectors.toSet());
    }

    private String render(String command, FormBuilder builder) {
        Map<String, String> values = new LinkedHashMap<>();
        builder.keys().forEach(key -> values.put(key, builder.value(key)));
        return new CommandTemplate(command).render(values);
    }
}
