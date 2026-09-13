package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.Money;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.DisplayText;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GovernanceFriendlyTextTest extends DomainFixture {
    private static final Pattern UUID_TEXT = Pattern.compile("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private GovernancePresentation ui;

    @BeforeEach
    void presentation() {
        time.set(Instant.parse("2026-09-13T13:30:00Z").toEpochMilli());
        ui = new GovernancePresentation(engine);
    }

    @Test
    void ordinaryPagesShowNamesPoliciesDatesAndAmountsEvenForOperators() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        String key = claim(alice, alpha, 12, 34);
        run(alice, "city setting " + alpha.city() + " incomeTaxBps 125");
        run(alice, "city setting " + alpha.city() + " foreignAccess true");
        String law = enactTaxLaw(alpha);
        String company = company(alice, "Builders");
        employ(alice, bob, company);
        engine.transferShares(company, alice.id(), bob.id(), 100);
        run(alice, "company propose " + company + " owner Bob Management Elect");
        run(alice, "nation invite " + alpha.nation() + " Felix");
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 10_000L);
        run(alice, "contract create " + alpha.nation() + " Road Work " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 12.34 Work");
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");

        for (Actor viewer : List.of(alice, operator)) {
            for (String page : List.of("main", "dashboard", "nations", "states", "cities", "members", "claims", "map",
                    "companies", "shareholders", "contracts", "legislature", "laws", "diplomacy", "elections",
                    "invitations", "profile", "mail", "official_mail")) {
                UiView directory = page(viewer, page);
                friendly(display(directory));
                for (var row : directory.rows()) {
                    if (row.entity().present()) friendly(display(ui.view(new UiContext(viewer, UiQuery.detail(row.entity())))));
                }
            }
        }
        String city = display(detail(alice, EntityRef.Kind.GOVERNMENT, alpha.city()));
        assertTrue(city.contains("Alice"));
        assertTrue(city.contains("AlphaState"));
        assertTrue(city.contains("Income tax: 1.25%"));
        assertTrue(city.contains("Foreign access: On"));
        assertFalse(city.contains("incomeTaxBps"));
        assertFalse(city.contains("{"));
        assertEquals("Chunk 12, 34 (Overworld)", detail(alice, EntityRef.Kind.CLAIM, key).title().fallback());
        assertTrue(display(detail(alice, EntityRef.Kind.CONTRACT, contract)).contains(Money.format(1_234)));
        assertTrue(display(detail(alice, EntityRef.Kind.LAW, alpha.nation() + ":" + law)).contains("Sep 13, 2026"));
        friendly(display(detail(alice, EntityRef.Kind.CONTRACT, contract + "|" + bob.id())));
        assertEquals(alpha.nation(), page(alice, "nations").rows().get(0).entity().id());
        assertEquals(key, detail(alice, EntityRef.Kind.CLAIM, key).actions().get(0).values().get("chunk"));
        assertEquals(company, detail(alice, EntityRef.Kind.COMPANY, company).actions().get(0).values().get("company"));
    }

    @Test
    void catalogsKeepExactChoiceValuesButShowHumanLabelsAndContext() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        String key = claim(alice, alpha, 0, 0);
        String company = company(alice, "Builders");
        employ(alice, bob, company);
        engine.transferShares(company, alice.id(), bob.id(), 100);
        run(bob, "mail send Alice Message Body");
        String mail = data.players.get(alice.id().toString()).inbox.stream()
                .filter(m -> "Message".equals(m.subject)).findFirst().orElseThrow().id;
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        String saved = new Gson().toJson(data);

        FormBuilder leaders = form(alice, "government leader <government> <player>", Map.of("government", alpha.nation()));
        assertEquals("Bob", choice(leaders, "player", bob.id().toString()).label());
        assertTrue(choice(leaders, "government", alpha.nation()).detail().contains("Nation"));
        FormBuilder rate = form(alice, "government setting <government> <key> <value>",
                Map.of("government", alpha.city(), "key", "incomeTaxBps", "value", "125"));
        assertEquals("125", rate.value("value"));
        assertEquals("1.25%", choice(rate, "value", "125").label());
        assertEquals("Income tax", choice(rate, "key", "incomeTaxBps").label());
        FormBuilder toggle = form(alice, "government setting <government> <key> <value>",
                Map.of("government", alpha.city(), "key", "foreignAccess"));
        assertEquals("On", choice(toggle, "value", "true").label());
        assertEquals("Off", choice(toggle, "value", "false").label());
        FormBuilder landValue = form(alice, "government setting <government> <key> <value>",
                Map.of("government", alpha.city(), "key", "baseChunkValue", "value", "12345"));
        assertEquals(Money.format(12_345), choice(landValue, "value", "12345").label());
        FormBuilder titles = form(alice, "chunk info <chunk>", Map.of("chunk", key));
        assertEquals(key, titles.value("chunk"));
        assertEquals(DisplayText.chunk(key), choice(titles, "chunk", key).label());
        String wilderness = "minecraft:the_nether|4|5";
        FormBuilder unclaimed = form(alice, "chunk info <chunk>", Map.of("chunk", wilderness));
        FormBuilder claiming = form(alice, "chunk claim <nation> <chunks>", Map.of("nation", alpha.nation(), "chunks", wilderness));
        assertEquals(DisplayText.chunk(wilderness), choice(unclaimed, "chunk", wilderness).label());
        assertEquals(wilderness, claiming.value("chunks"));
        assertEquals("AlphaNation", choice(claiming, "nation", alpha.nation()).label());
        FormBuilder permits = form(alice, "chunk permit <chunk> <player> <actions>",
                Map.of("chunk", key, "player", bob.id().toString(), "actions", "BLOCK_INTERACT,ATTACK"));
        assertTrue(choice(permits, "actions", "BLOCK_INTERACT,ATTACK").label().contains("Use blocks"));
        FormBuilder nominee = form(alice, "company propose <company> <type> <value> <title> <text>",
                Map.of("company", company, "type", "owner", "value", bob.id().toString()));
        assertEquals(bob.id().toString(), nominee.value("value"));
        assertEquals("Bob", choice(nominee, "value", bob.id().toString()).label());
        FormBuilder messages = form(alice, "mail read <message>", Map.of("message", mail));
        assertEquals(mail, messages.value("message"));
        assertTrue(choice(messages, "message", mail).detail().contains("Sep 13, 2026"));
        assertTrue(choice(messages, "message", mail).detail().contains("Bob"));
        String terms = key + "=" + beta.city();
        FormBuilder peace = form(alice, "diplomacy peace <from_nation> <to_nation> <offer_amount> <demand_amount> <chunk_terms_or_dash> <message>",
                Map.of("from_nation", alpha.nation(), "to_nation", beta.nation(), "chunk_terms_or_dash", terms));
        assertEquals(terms, peace.value("chunk_terms_or_dash"));
        assertTrue(choice(peace, "chunk_terms_or_dash", terms).label().contains("BetaCity"));
        FormBuilder accounts = form(alice, "economy:banking", "bank transfer <fromAccount> <toAccount>",
                Map.of("fromAccount", alice.account(), "toAccount", "company:" + company));
        assertEquals("company:" + company, accounts.value("toAccount"));
        assertEquals("Builders (company)", choice(accounts, "toAccount", "company:" + company).label());
        for (FormBuilder catalog : List.of(leaders, rate, toggle, landValue, titles, unclaimed, claiming, permits, nominee, messages, peace, accounts))
            friendly(display(catalog));
        assertEquals(saved, new Gson().toJson(data));
    }

    @Test
    void materialReviewsRetainExactMoneyPeopleTitlesTerritoryAndHiddenSelections() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        String key = claim(alice, alpha, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 10_000L);
        economy.balances.put("nation:" + beta.nation(), 10_000L);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        ActionSelection selection = ActionSelection.form("statecraft:diplomacy",
                "diplomacy peace <from_nation> <to_nation> <offer_amount> <demand_amount> <chunk_terms_or_dash> <message>",
                Map.of("from_nation", alpha.nation(), "to_nation", beta.nation(), "offer_amount", "12.34",
                        "demand_amount", "0.56", "chunk_terms_or_dash", key + "=" + beta.city(), "message", "Peace terms"));
        String saved = new Gson().toJson(data);
        ActionPreview peace = ui.preview(alice, selection);
        friendly(display(peace));
        assertEquals(alpha.nation(), selection.values().get("from_nation"));
        assertEquals(key + "=" + beta.city(), selection.values().get("chunk_terms_or_dash"));
        assertTrue(value(peace, "payment_parties").contains(Money.format(1_234)));
        assertTrue(value(peace, "payment_parties").contains(Money.format(56)));
        assertTrue(value(peace, "territory_terms").contains("AlphaCity"));
        assertTrue(value(peace, "territory_terms").contains("BetaCity"));
        assertTrue(value(peace, "territory_terms").contains("Chunk 0, 0 (Overworld)"));
        assertEquals("12 seconds", value(peace, "duration_proposal_duration"));
        assertEquals(saved, new Gson().toJson(data));
        assertTrue(economy.batches.isEmpty());

        ActionPreview tax = preview(alice, "city setting " + alpha.city() + " incomeTaxBps 125");
        friendly(display(tax));
        assertEquals("Income tax", value(tax, "policy"));
        assertEquals("1.25%", value(tax, "replacement"));
        Tree removed = tree(felix, "Removal");
        claim(felix, removed, 8, 8);
        ActionPreview removal = preview(felix, "nation disband " + removed.nation() + " cascade");
        friendly(display(removal));
        assertEquals("3", value(removal, "governments_removed"));
        assertEquals("1", value(removal, "claims_removed"));
        assertTrue(value(removal, "removal_scope").contains("RemovalState"));
        assertTrue(value(removal, "removed_territory").contains("RemovalCity"));
    }

    @Test
    void equalDisplayNamesCannotCollapseMaterialFingerprints() {
        String first = company(alice, "First Company");
        String second = company(alice, "Second Company");
        data.companies.get(second).name = data.companies.get(first).name;
        run(alice, "company propose " + first + " roleplay - Decision Text");
        String proposal = latestCompanyProposal();
        ActionSelection selection = ActionSelection.form("statecraft:shareholders", "company vote <proposal> <yes_no_abstain>",
                Map.of("proposal", proposal, "yes_no_abstain", "yes"));
        ActionPreview original = ui.preview(alice, selection);
        data.companyProposals.get(proposal).companyId = second;
        ActionPreview moved = ui.preview(alice, selection);
        assertEquals(display(original), display(moved));
        assertNotEquals(original.fingerprint(), moved.fingerprint());
        assertEquals(proposal, selection.values().get("proposal"));
        friendly(display(moved));

        data.players.get(bob.id().toString()).name = "Same player name";
        data.players.get(cara.id().toString()).name = "Same player name";
        ActionPreview bobShares = preview(alice, "company transfer " + first + " " + bob.id() + " 1");
        ActionPreview caraShares = preview(alice, "company transfer " + first + " " + cara.id() + " 1");
        assertEquals(display(bobShares), display(caraShares));
        assertNotEquals(bobShares.fingerprint(), caraShares.fingerprint());
    }

    @Test
    void retainedRecordsUseHonestFormerEntityLabelsWithoutRequiringTheirParents() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String law = enactTaxLaw(alpha);
        String company = company(alice, "Builders");
        employ(alice, bob, company);
        engine.transferShares(company, alice.id(), bob.id(), 100);
        run(alice, "company propose " + company + " owner Bob Management Text");
        String shareholder = latestCompanyProposal();
        run(alice, "company cancel " + shareholder);
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        String treaty = latestDiplomacy();
        run(dave, "diplomacy accept " + treaty);
        data.bills.get(law).author = bob.id().toString();
        data.companyProposals.get(shareholder).lastError = "Insufficient funds: company:" + company;
        data.companies.remove(company);
        data.players.remove(bob.id().toString());
        data.governments.clear();

        for (EntityRef ref : List.of(
                new EntityRef("statecraft", EntityRef.Kind.BILL, law),
                new EntityRef("statecraft", EntityRef.Kind.LAW, alpha.nation() + ":" + law),
                new EntityRef("statecraft", EntityRef.Kind.DIPLOMACY, treaty),
                new EntityRef("statecraft", EntityRef.Kind.COMPANY_PROPOSAL, shareholder))) {
            UiView historical = assertDoesNotThrow(() -> ui.view(new UiContext(operator, UiQuery.detail(ref))));
            friendly(display(historical));
        }
        assertTrue(display(detail(operator, EntityRef.Kind.BILL, law)).contains("Former nation"));
        assertTrue(display(detail(operator, EntityRef.Kind.BILL, law)).contains("Former player"));
        String decision = display(detail(operator, EntityRef.Kind.COMPANY_PROPOSAL, shareholder));
        assertTrue(decision.contains("Former company"));
        assertTrue(decision.contains("Former player"));
        assertTrue(decision.contains("not enough funds"));
        assertTrue(display(detail(operator, EntityRef.Kind.DIPLOMACY, treaty)).contains("Former nation"));
    }

    @Test
    void treatyGeneratedBillTitlesAndTextUsePartiesInsteadOfFullOrShortIds() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        run(alice, "diplomacy war " + alpha.nation() + " " + beta.nation());
        run(alice, "diplomacy peace " + alpha.nation() + " " + beta.nation() + " 0 0 " + key + "=" + beta.city());
        String treaty = latestDiplomacy();
        run(dave, "diplomacy accept " + treaty);
        String saved = new Gson().toJson(data);
        for (var bill : data.bills.values()) {
            String projected = display(detail(alice, EntityRef.Kind.BILL, bill.id));
            friendly(projected);
            assertFalse(projected.contains(treaty.substring(0, 8)));
            assertTrue(projected.contains("AlphaNation"));
            assertTrue(projected.contains("BetaNation"));
        }
        friendly(display(form(alice, "bill info <bill>", Map.of())));
        assertEquals(saved, new Gson().toJson(data));
    }

    @Test
    void authoredUuidNamesLawTextAndMailAreNotStrippedOrRewritten() {
        String reference = UUID.randomUUID().toString();
        String narrative = "Keep this reference " + reference + " and incomeTaxBps exactly as written.";
        String title = "Archive " + reference;
        config.maxNameLength = 64;
        configure();
        Tree alpha = tree(alice, "Alpha");
        String company = company(alice, title);
        run(alice, "nation description " + alpha.nation() + " " + q(narrative));
        run(alice, "mail send Bob " + q(title) + " " + q(narrative));
        var message = data.players.get(bob.id().toString()).inbox.stream().filter(m -> title.equals(m.subject)).findFirst().orElseThrow();
        UiView mail = detail(bob, EntityRef.Kind.MAIL, GovernancePresentation.mailId(bob.account(), false, message));
        assertEquals(title, mail.title().fallback());
        assertTrue(mail.body().fallback().endsWith(narrative));
        assertEquals(narrative, message.body);
        assertEquals(title, detail(alice, EntityRef.Kind.COMPANY, company).title().fallback());
        assertTrue(detail(alice, EntityRef.Kind.GOVERNMENT, alpha.nation()).body().fallback().contains(narrative));
        ActionPreview sending = ui.preview(alice, ActionSelection.form("statecraft:mail", "mail send <recipient> <subject> <body>",
                Map.of("recipient", bob.id().toString(), "subject", title, "body", narrative)));
        assertEquals(title, value(sending, "subject"));
        assertEquals(narrative, value(sending, "body"));
        run(alice, "bill propose " + alpha.nation() + " roleplay - " + q(title) + " " + q(narrative));
        String bill = latestBill();
        advance(config.debateDurationMillis);
        run(alice, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        run(alice, "bill sign " + bill);
        UiView law = detail(bob, EntityRef.Kind.LAW, alpha.nation() + ":" + bill);
        assertEquals(title, law.title().fallback());
        assertTrue(law.body().fallback().endsWith(narrative));
        assertEquals(title, choice(form(alice, "company info <company>", Map.of("company", company)), "company", company).label());
        engine.mail(bob.id(), "Custom notice", narrative);
        var custom = data.players.get(bob.id().toString()).inbox.stream().filter(m -> "Custom notice".equals(m.subject)).findFirst().orElseThrow();
        assertTrue(detail(bob, EntityRef.Kind.MAIL, GovernancePresentation.mailId(bob.account(), false, custom)).body().fallback().endsWith(narrative));
    }

    @Test
    void generatedNotificationsOnlyFormatTechnicalSlotsAndKeepQuotedPlayerContent() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String reference = UUID.randomUUID().toString();
        String message = "Narrative reference " + reference;
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation() + " " + q(message));
        String proposal = latestDiplomacy();
        var notice = data.governments.get(alpha.nation()).inbox.stream().filter(m -> "Alliance proposal".equals(m.subject)).findFirst().orElseThrow();
        UiView visible = detail(alice, EntityRef.Kind.MAIL,
                GovernancePresentation.mailId("nation:" + alpha.nation(), false, notice));
        assertTrue(visible.body().fallback().contains(message));
        assertFalse(visible.body().fallback().contains(proposal));
        assertTrue(notice.body.contains(proposal));
        run(alice, "executive emergency " + alpha.nation() + " incomeTaxBps 250 " + q(message));
        var emergency = data.governments.get(alpha.nation()).inbox.stream().filter(m -> "Emergency order".equals(m.subject)).findFirst().orElseThrow();
        String rendered = display(detail(alice, EntityRef.Kind.MAIL,
                GovernancePresentation.mailId("nation:" + alpha.nation(), false, emergency)));
        assertTrue(rendered.contains("Income tax: 2.5%"));
        assertTrue(rendered.contains("Sep 13, 2026"));
        assertTrue(rendered.contains(message));
        assertFalse(rendered.contains("incomeTaxBps"));
    }

    @Test
    void invitationAndElectionNotificationsExplainActionsWithoutCommandsOrTechnicalTieBreaks() {
        Tree alpha = tree(alice, "Alpha");
        join(bob, alpha);
        run(alice, "nation invite " + alpha.nation() + " Felix");
        String company = company(alice, "Builders");
        run(alice, "company invite " + company + " Felix");
        for (var message : data.players.get(felix.id().toString()).inbox) {
            String rendered = display(detail(felix, EntityRef.Kind.MAIL, GovernancePresentation.mailId(felix.account(), false, message)));
            friendly(rendered);
            assertTrue(rendered.contains("Open Invitations"));
            assertFalse(rendered.contains("nation accept"));
            assertFalse(rendered.contains("company accept"));
        }
        run(alice, "election candidate " + alpha.nation());
        run(bob, "election candidate " + alpha.nation());
        advance(config.electionIntervalMillis);
        run(alice, "election vote " + alpha.nation() + " Bob");
        run(bob, "election vote " + alpha.nation() + " Bob");
        advance(config.electionVotingMillis);
        var results = data.governments.get(alpha.nation()).inbox.stream()
                .filter(m -> "Election results".equals(m.subject)).findFirst().orElseThrow();
        String rendered = display(detail(bob, EntityRef.Kind.MAIL, GovernancePresentation.mailId("nation:" + alpha.nation(), false, results)));
        friendly(rendered);
        assertTrue(rendered.contains("Winner: Bob, votes: 2 of 2"));
        assertFalse(rendered.contains("UUID"));
    }

    @Test
    void registeredGuidedQueriesStayFriendlyAfterTheirOriginalAuthorizedExecution() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        String law = enactTaxLaw(alpha);
        String key = claim(alice, alpha, 0, 0);
        run(alice, "chunk permit " + key + " Bob BREAK,BLOCK_INTERACT");
        String company = company(alice, "Builders");
        employ(alice, bob, company);
        engine.transferShares(company, alice.id(), bob.id(), 100);
        run(alice, "company propose " + company + " owner Bob Management Decision");
        String shareholder = latestCompanyProposal();
        run(alice, "contract create " + alpha.nation() + " Road Work " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 0 Work");
        run(alice, "election candidate " + alpha.nation());
        run(bob, "election candidate " + alpha.nation());
        run(alice, "nation invite " + alpha.nation() + " Felix");
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        String proposal = latestDiplomacy();
        run(bob, "mail send Alice Personal Body");
        String personal = data.players.get(alice.id().toString()).inbox.get(0).id;
        String official = data.governments.get(alpha.nation()).inbox.get(0).id;
        Map<String, String> defaults = Map.ofEntries(Map.entry("nation", alpha.nation()), Map.entry("state", alpha.state()),
                Map.entry("city", alpha.city()), Map.entry("government", alpha.nation()), Map.entry("company", company),
                Map.entry("player", bob.id().toString()), Map.entry("page", "1"), Map.entry("radius", "2"),
                Map.entry("chunk", key), Map.entry("bill", law), Map.entry("law", law), Map.entry("contract", contract));
        int covered = 0;
        for (var page : MenuRegistry.pages()) {
            if (!page.id().startsWith("statecraft:") || page.id().equals("statecraft:admin") || page.id().equals("statecraft:help")) continue;
            for (var action : page.actions()) {
                if (action.intent() != ActionIntent.QUERY || action.command().startsWith("help ")) continue;
                Map<String, String> values = new HashMap<>();
                for (String field : new CommandTemplate(action.command()).fields()) {
                    String value = switch (field) {
                        case "proposal" -> action.command().startsWith("company ") ? shareholder : proposal;
                        case "message" -> action.command().startsWith("mail official ") ? official : personal;
                        default -> defaults.get(field);
                    };
                    assertNotNull(value, action.command() + " field " + field);
                    values.put(field, value);
                }
                String rendered = query(alice, page.id(), action.command(), values);
                friendly(rendered);
                covered++;
            }
        }
        assertTrue(covered >= 45, "Registered query coverage: " + covered);
        assertTrue(query(alice, "statecraft:nations", "nation settings <nation>", Map.of("nation", alpha.nation())).contains("Income tax: 1.25%"));
        String protection = query(alice, "statecraft:claims", "chunk protection <chunk> <player>",
                Map.of("chunk", key, "player", bob.id().toString()));
        assertTrue(protection.contains("Use blocks and containers:"));
        assertTrue(protection.contains("uses Alice as the opponent"));
    }

    @Test
    void guidedQueriesKeepRequestedPagesAndDoNotWidenGovernmentOrMailboxFilters() {
        config.pageSize = 2;
        configure();
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        join(bob, alpha);
        join(cara, alpha);
        join(felix, alpha);
        String members = query(alice, "statecraft:members", "government members <government> <page>",
                Map.of("government", alpha.nation(), "page", "2"));
        assertTrue(members.contains("(2/2, 4 total)"));
        assertTrue(members.contains("Cara"));
        assertTrue(members.contains("Felix"));
        assertFalse(members.contains("Alice"));
        assertFalse(members.contains("Bob"));
        run(alice, "election candidate " + alpha.nation());
        run(bob, "election candidate " + alpha.nation());
        run(cara, "election candidate " + alpha.nation());
        String candidates = query(alice, "statecraft:elections", "election candidates <nation> <page>",
                Map.of("nation", alpha.nation(), "page", "2"));
        assertTrue(candidates.contains("(2/2, 3 total)"));
        assertTrue(candidates.contains("Cara"));
        assertFalse(candidates.contains("Alice"));
        assertFalse(candidates.contains("Bob"));

        for (int index = 1; index <= 3; index++) {
            run(bob, "mail send Alice Memo" + index + " HiddenBody" + index);
            time.incrementAndGet();
            run(alice, "bill propose " + alpha.nation() + " roleplay - AlphaBill" + index + " Text");
        }
        run(dave, "bill propose " + beta.nation() + " roleplay - BetaBill Text");
        String inbox = query(alice, "statecraft:mail", "mail inbox <page>", Map.of("page", "2"));
        assertTrue(inbox.contains("Memo1"));
        assertFalse(inbox.contains("Memo2"));
        assertFalse(inbox.contains("Memo3"));
        assertFalse(inbox.contains("HiddenBody"));
        String bills = query(alice, "statecraft:legislature", "bill list <nation> <page>", Map.of("nation", alpha.nation(), "page", "2"));
        assertTrue(bills.contains("AlphaBill1"));
        assertFalse(bills.contains("AlphaBill2"));
        assertFalse(bills.contains("BetaBill"));

        String selected = "";
        for (int index = 1; index <= 4; index++) {
            String key = claim(alice, alpha, index - 1, 0);
            time.incrementAndGet();
            run(alice, "contract create " + (index == 4 ? alpha.state() : alpha.nation()) + " Road" + index + " Work " + key);
            if (index == 1) selected = latestContract();
        }
        String contracts = query(alice, "statecraft:contracts", "contract list <government> <page>",
                Map.of("government", alpha.nation(), "page", "2"));
        assertTrue(contracts.contains("(2/2, 3 total)"));
        assertTrue(contracts.contains("Road1"));
        assertFalse(contracts.contains("Road4"), "A nation filter must not include contracts issued by its state.");
        run(bob, "contract bid " + selected + " 0 Work");
        String mine = query(bob, "statecraft:contracts", "contract my <page>", Map.of("page", "1"));
        assertTrue(mine.contains("(1/1, 1 total)"));
        assertTrue(mine.contains("Road1"));
        assertFalse(mine.contains("Road2"));
        for (String result : List.of(members, candidates, inbox, bills, contracts, mine)) friendly(result);
    }

    @Test
    void guidedReadAndHistoryQueriesKeepWholeAuthoredBodiesAndReasons() {
        config.maxDescriptionLength = 2_000;
        config.maxMailBodyLength = 4_000;
        configure();
        Tree alpha = tree(alice, "Alpha");
        String reference = UUID.randomUUID().toString();
        String lawText = "l".repeat(1_900) + "\nKeep " + reference;
        String mailText = "m".repeat(3_900) + "\nKeep " + reference;
        run(bob, "mail send Alice Archive " + q(mailText));
        String message = data.players.get(alice.id().toString()).inbox.get(0).id;
        String mail = query(alice, "statecraft:mail", "mail read <message>", Map.of("message", message));
        assertTrue(mail.endsWith(mailText));
        assertTrue(data.players.get(alice.id().toString()).inbox.get(0).read);
        assertFalse(mail.contains(alice.id().toString()));
        assertFalse(mail.contains(bob.id().toString()));
        assertFalse(mail.contains(message));
        run(alice, "bill propose " + alpha.nation() + " roleplay - Archive " + q(lawText));
        String bill = latestBill();
        String read = query(alice, "statecraft:legislature", "bill info <bill>", Map.of("bill", bill));
        assertTrue(read.endsWith(lawText));
        assertFalse(read.contains(bill));
        assertFalse(read.contains(alpha.nation()));
        advance(config.debateDurationMillis);
        run(alice, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        String reason = "Retain the authored reference " + reference;
        run(alice, "bill veto " + bill + " " + q(reason));
        String history = query(alice, "statecraft:legislature", "bill history <bill> <page>", Map.of("bill", bill, "page", "1"));
        assertTrue(history.contains("Vetoed by Alice: " + reason));
        assertFalse(history.contains(alice.id().toString()));
        assertTrue(history.contains("Sep 13, 2026"));
        run(alice, "bill override " + bill);
        run(alice, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        String law = query(alice, "statecraft:laws", "law read <nation> <law>", Map.of("nation", alpha.nation(), "law", bill));
        assertTrue(law.endsWith(lawText));
        assertFalse(law.contains(bill));
        assertFalse(law.contains(alpha.nation()));
    }

    @Test
    void guidedContractDetailsRetainTheOriginalOutputBudgetPageWindow() {
        config.pageSize = 10;
        config.maxCommandOutput = 2_048;
        config.maxDescriptionLength = 2_000;
        configure();
        Tree alpha = tree(alice, "Alpha");
        String key = claim(alice, alpha, 0, 0);
        String description = "d".repeat(1_900) + " " + UUID.randomUUID();
        run(alice, "contract create " + alpha.nation() + " Road " + q(description) + " " + key);
        String contract = latestContract();
        String third = query(alice, "statecraft:contracts", "contract info <contract> <page>",
                Map.of("contract", contract, "page", "3"));
        assertTrue(third.startsWith("Contract details (3/6, 6 total)\n"));
        assertTrue(third.endsWith(description));
        assertFalse(third.contains("Bidding ends"));
        String second = query(alice, "statecraft:contracts", "contract info <contract> <page>",
                Map.of("contract", contract, "page", "2"));
        assertTrue(second.startsWith("Contract details (2/6, 6 total)\n"));
        assertTrue(second.contains("Government: AlphaNation; author: Alice"));
        assertFalse(second.contains(description));
        friendly(second);
    }

    @Test
    void advancedHelpAndUnrecognizedQueryRepliesRemainExactAndStalePlayerNamesDoNotExposeIds() {
        Tree alpha = tree(alice, "Alpha");
        join(bob, alpha);
        String company = company(alice, "Builders");
        employ(alice, bob, company);
        ActionSelection advanced = ActionSelection.raw("statecraft:nations", "nation info " + alpha.nation());
        String raw = run(alice, advanced.rendered());
        assertEquals(raw, ui.queryText(alice, advanced, raw));
        ActionSelection help = ActionSelection.form("statecraft:help", "help <section> <page>", Map.of("section", "governments", "page", "1"));
        raw = run(alice, help.rendered());
        assertEquals(raw, ui.queryText(alice, help, raw));
        ActionSelection audit = ActionSelection.form("statecraft:admin", "admin audit <page>", Map.of("page", "1"));
        raw = run(operator, audit.rendered());
        assertEquals(raw, ui.queryText(operator, audit, raw));
        String custom = "Unrecognized authored report " + UUID.randomUUID();
        assertEquals(custom, ui.queryText(alice, ActionSelection.form("statecraft:nations", "nation settings <nation>",
                Map.of("nation", alpha.nation())), custom));
        engine.login(actor(20, "Bob", false));
        assertEquals(bob.id().toString(), data.players.get(bob.id().toString()).name);
        String profile = query(alice, "statecraft:profile", "profile <player>", Map.of("player", bob.id().toString()));
        assertTrue(profile.startsWith("Former player"));
        friendly(profile);
        assertEquals("Former player", choice(form(alice, "profile <player>", Map.of()), "player", bob.id().toString()).label());
        friendly(display(detail(alice, EntityRef.Kind.COMPANY, company)));
    }

    private String enactTaxLaw(Tree tree) {
        run(alice, "bill propose " + tree.nation() + " incomeTaxBps 125 Revenue Roads");
        String bill = latestBill();
        advance(config.debateDurationMillis);
        run(alice, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        run(alice, "bill sign " + bill);
        return bill;
    }

    private UiView page(Actor actor, String page) {
        return ui.view(new UiContext(actor, new UiQuery("statecraft:" + page, EntityRef.NONE, "", 0)));
    }

    private UiView detail(Actor actor, EntityRef.Kind kind, String id) {
        return ui.view(new UiContext(actor, UiQuery.detail(new EntityRef("statecraft", kind, id))));
    }

    private ActionPreview preview(Actor actor, String command) {
        return ui.preview(actor, ActionSelection.raw("statecraft:main", command));
    }

    private String query(Actor actor, String page, String template, Map<String, String> values) {
        ActionSelection selection = ActionSelection.form(page, template, values);
        String raw = run(actor, selection.rendered());
        String saved = new Gson().toJson(data);
        String displayed = ui.queryText(actor, selection, raw);
        assertEquals(saved, new Gson().toJson(data), template + " projection must remain read-only");
        assertEquals(values, selection.values());
        return displayed;
    }

    private FormBuilder form(Actor actor, String command, Map<String, String> values) {
        return form(actor, "statecraft:forms", command, values);
    }

    private FormBuilder form(Actor actor, String page, String command, Map<String, String> values) {
        FormContext context = new FormContext(actor, page, command, values, FormQuery.INITIAL);
        FormBuilder form = new FormBuilder(context);
        new GovernanceForms(engine).describe(context, form);
        return form;
    }

    private FormChoice choice(FormBuilder form, String key, String value) {
        return form.choices(key).stream().filter(choice -> value.equals(choice.value())).findFirst().orElseThrow();
    }

    private String value(ActionPreview preview, String key) {
        return preview.lines().stream().filter(line -> line.label().key().equals("ui.statecraft.gov.preview." + key))
                .findFirst().orElseThrow().value().fallback();
    }

    private String display(UiView view) {
        List<String> text = new ArrayList<>(List.of(view.title().fallback(), view.body().fallback(), view.emptyHint().fallback()));
        view.rows().forEach(row -> { text.add(row.title().fallback()); text.add(row.detail().fallback()); });
        view.actions().forEach(action -> { text.add(action.label().fallback()); text.add(action.disabledReason().fallback()); });
        return String.join("\n", text);
    }

    private String display(FormBuilder form) {
        List<String> text = new ArrayList<>();
        for (FormField field : form.build().fields()) {
            text.add(field.label());
            text.add(field.hint());
            text.add(field.selectedLabel());
            form.choices(field.key()).forEach(choice -> { text.add(choice.label()); text.add(choice.detail()); });
        }
        return String.join("\n", text);
    }

    private String display(ActionPreview preview) {
        return preview.title().fallback() + "\n" + preview.warning().fallback() + "\n" + preview.lines().stream()
                .map(line -> line.label().fallback() + ": " + line.value().fallback()).collect(Collectors.joining("\n"));
    }

    private void friendly(String text) {
        assertFalse(UUID_TEXT.matcher(text).find(), text);
        assertFalse(Pattern.compile("\\b(?:player|company|nation|state|city|system):\\S").matcher(text).find(), text);
        for (String technical : List.of("escrow:contract:", "minecraft:", "incomeTaxBps", "BLOCK_INTERACT", "ENTITY_INTERACT",
                "OVERRIDE_VOTING", "AWAITING_RATIFICATION", "<government>", "<player>"))
            assertFalse(text.contains(technical), technical + " in " + text);
        assertFalse(text.contains(Long.toString(time.get())), text);
    }
}
