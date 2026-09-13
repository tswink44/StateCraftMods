package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiRow;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GovernancePresentationTest extends DomainFixture {
    private GovernancePresentation ui;

    @BeforeEach
    void presentation() { ui = new GovernancePresentation(engine); }

    @Test
    void governmentDirectoryIsSearchablePagedAndUsesStableTypedRecordIds() {
        for (int index = 0; index < 24; index++) {
            Actor citizen = actor(100 + index, "Citizen" + index, false);
            engine.login(citizen);
            run(operator, "nation create Realm" + String.format(java.util.Locale.ROOT, "%02d", index) + " " + citizen.name());
        }
        String before = new Gson().toJson(data);
        UiView first = page(alice, "nations", "", 0);
        UiView second = page(alice, "nations", "", 20);
        assertEquals(20, first.rows().size());
        assertTrue(first.more());
        assertEquals(4, second.rows().size());
        assertFalse(second.more());
        assertEquals(20, second.offset());
        Set<String> ids = new HashSet<>();
        for (UiRow row : java.util.stream.Stream.concat(first.rows().stream(), second.rows().stream()).toList()) {
            assertEquals(EntityRef.Kind.GOVERNMENT, row.entity().kind());
            assertEquals("statecraft", row.entity().namespace());
            assertTrue(data.governments.containsKey(row.entity().id()));
            assertTrue(ids.add(row.entity().id()));
        }
        UiView searched = page(alice, "nations", "Realm23", 0);
        assertEquals(1, searched.rows().size());
        assertEquals(government("Realm23").id(), searched.rows().get(0).entity().id());
        UiView missing = page(alice, "nations", "No matching organization", 0);
        assertTrue(missing.rows().isEmpty());
        assertFalse(missing.emptyHint().fallback().isBlank());
        assertEquals(before, new Gson().toJson(data));
    }

    @Test
    void dashboardIsBoundedAndDoesNotDownloadMailBodiesOrMarkMailRead() {
        for (int index = 0; index < 25; index++) engine.mail(alice.id(), "Notice" + index, "PRIVATE BODY " + index);
        AtomicInteger dirty = observe();
        UiView first = ui.dashboard(alice, "", 0);
        UiView last = ui.dashboard(alice, "", 20);
        assertEquals(20, first.rows().size());
        assertTrue(first.more());
        assertEquals(5, last.rows().size());
        assertFalse(last.more());
        assertEquals(1, ui.dashboard(alice, "Notice24", 0).rows().size());
        assertTrue(ui.dashboard(bob, "", 0).rows().isEmpty());
        for (UiRow row : first.rows()) {
            assertEquals(EntityRef.Kind.MAIL, row.entity().kind());
            assertFalse(row.detail().fallback().contains("PRIVATE BODY"));
        }
        assertEquals(0, dirty.get());
        assertTrue(data.players.get(alice.id().toString()).inbox.stream().noneMatch(m -> m.read));
    }

    @Test
    void forgedKindsAliasesForeignNamespacesAndPrivateMailRefsFailClosed() {
        Tree tree = tree(alice, "Alpha");
        String company = company(alice, "Builders");
        String key = claim(alice, tree, 0, 0);
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.GOVERNMENT, company));
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.GOVERNMENT, "AlphaNation"));
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.CLAIM, "minecraft:overworld|00|0"));
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.BANK, tree.nation()));
        assertThrows(UserError.class, () -> ui.view(new UiContext(alice, UiQuery.detail(
                new EntityRef("statecraft_economy", EntityRef.Kind.CLAIM, key)))));
        engine.mail(bob.id(), "Private", "Only Bob may read this.");
        String message = data.players.get(bob.id().toString()).inbox.get(0).id;
        AtomicInteger dirty = observe();
        for (Actor actor : List.of(alice, operator, cara)) {
            assertThrows(UserError.class, () -> detail(actor, EntityRef.Kind.MAIL, bob.account() + "|inbox|" + message));
        }
        assertFalse(data.players.get(bob.id().toString()).inbox.get(0).read);
        assertEquals(0, dirty.get());
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.MAIL, "bad-reference"));
    }

    @Test
    void openingMailMarksOnlyTheAuthorizedCopyAndOfficialRolesAreRechecked() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        run(alice, "mail send Alice Self Body");
        String personal = data.players.get(alice.id().toString()).inbox.get(0).id;
        engine.governmentMail(tree.nation(), "Official", "OFFICIAL PRIVATE BODY");
        String official = data.governments.get(tree.nation()).inbox.get(0).id;
        AtomicInteger dirty = observe();
        ui.preview(alice, ActionSelection.form("statecraft:mail", "mail read <message>", Map.of("message", personal)));
        assertEquals(0, dirty.get());
        detail(alice, EntityRef.Kind.MAIL, alice.account() + "|sent|" + personal);
        assertFalse(data.players.get(alice.id().toString()).inbox.get(0).read);
        assertEquals(0, dirty.get());
        UiView opened = detail(alice, EntityRef.Kind.MAIL, alice.account() + "|inbox|" + personal);
        assertTrue(opened.body().fallback().contains("Body"));
        assertTrue(data.players.get(alice.id().toString()).inbox.get(0).read);
        assertEquals(1, dirty.get());
        detail(alice, EntityRef.Kind.MAIL, alice.account() + "|inbox|" + personal);
        assertEquals(1, dirty.get());
        assertTrue(page(bob, "official_mail", "", 0).rows().isEmpty());
        assertThrows(UserError.class, () -> detail(bob, EntityRef.Kind.MAIL, "nation:" + tree.nation() + "|inbox|" + official));
        UiView governmentMail = detail(alice, EntityRef.Kind.MAIL, "nation:" + tree.nation() + "|inbox|" + official);
        assertTrue(governmentMail.body().fallback().contains("OFFICIAL PRIVATE BODY"));
        assertEquals(2, dirty.get());
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.MAIL, "city:" + tree.nation() + "|inbox|" + official));
    }

    @Test
    void mailDetailsPreserveTheFullConfiguredBodyInsteadOfTruncatingTranslationArguments() {
        config.maxMailBodyLength = 4_000;
        configure();
        String body = "x".repeat(3_996) + " END";
        engine.mail(alice.id(), "Long message", body);
        var message = data.players.get(alice.id().toString()).inbox.get(0);
        UiView view = detail(alice, EntityRef.Kind.MAIL, GovernancePresentation.mailId(alice.account(), false, message));
        assertTrue(view.body().fallback().endsWith(body));
        assertTrue(view.body().arguments().stream().allMatch(value -> value.length() <= 2048));
    }

    @Test
    void completedContractBidsRemainAuthorizedAfterItsCompanyPayeeDissolves() {
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String company = company(bob, "Builders");
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 0 \"PRIVATE BID TERMS\" company:" + company);
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        run(bob, "contract submit " + contract + " Done");
        run(alice, "contract complete " + contract);
        run(bob, "company disband " + company);
        UiView authorized = detail(alice, EntityRef.Kind.CONTRACT, contract);
        assertTrue(authorized.body().fallback().contains("Former company"));
        assertTrue(authorized.rows().stream().anyMatch(row -> row.detail().fallback().contains("PRIVATE BID TERMS")));
        UiAction bids = action(authorized, "contract bids <contract> <page>");
        assertTrue(bids.enabled());
        assertEquals(contract, bids.values().get("contract"));
        UiView publicView = detail(cara, EntityRef.Kind.CONTRACT, contract);
        assertFalse(publicView.rows().stream().anyMatch(row -> row.detail().fallback().contains("PRIVATE BID TERMS")));
        assertFalse(action(publicView, "contract bids <contract> <page>").enabled());
        assertFalse(action(publicView, "contract bids <contract> <page>").disabledReason().fallback().isBlank());
        UiView bid = detail(alice, EntityRef.Kind.CONTRACT, contract + "|" + bob.id());
        assertTrue(bid.body().fallback().contains("PRIVATE BID TERMS"));
        assertTrue(bid.body().fallback().contains("Former company"));
        assertThrows(UserError.class, () -> detail(cara, EntityRef.Kind.CONTRACT, contract + "|" + bob.id()));
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.CONTRACT, contract + "|" + cara.id()));
    }

    @Test
    void fullBidDetailsPreserveLongTextWhilePagedSummariesStayWithinTheAggregateBudget() {
        config.maxDescriptionLength = 2_000;
        configure();
        Tree tree = tree(alice, "Alpha");
        String key = claim(alice, tree, 0, 0);
        String description = "d".repeat(1_996) + " END";
        run(alice, "contract create " + tree.nation() + " LargeRoad " + q(description) + " " + key);
        String contract = latestContract();
        for (int index = 0; index < 20; index++) {
            Actor bidder = actor(100 + index, "Bidder" + index, false);
            engine.login(bidder);
            run(bidder, "contract bid " + contract + " 0 " + q(description));
        }
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bidder0");
        run(actor(100, "Bidder0", false), "contract submit " + contract + " " + q(description));
        UiView summary = assertDoesNotThrow(() -> detail(alice, EntityRef.Kind.CONTRACT, contract));
        assertEquals(20, summary.rows().size());
        assertTrue(summary.more());
        UiRow first = summary.rows().get(0);
        assertEquals(EntityRef.Kind.CONTRACT, first.entity().kind());
        UiView full = ui.view(new UiContext(alice, UiQuery.detail(first.entity())));
        assertTrue(full.body().fallback().endsWith(description));
        UiAction award = action(full, "contract award <contract> <bidder>");
        assertEquals(contract, award.values().get("contract"));
        assertEquals(actor(100, "Bidder0", false).id().toString(), award.values().get("bidder"));
    }

    @Test
    void unusuallyLongExistingClaimKeysHaveAnExplicitCommandOnlyHintInsteadOfBreakingTheDirectory() {
        Tree tree = tree(alice, "Alpha");
        claim(alice, tree, 0, 0);
        String key = claim(alice, tree, "custom:" + "d".repeat(300), 0, 0);
        UiView claims = assertDoesNotThrow(() -> page(alice, "claims", "", 0));
        assertEquals(2, claims.rows().size());
        assertTrue(claims.rows().stream().anyMatch(row -> !row.entity().present()
                && row.detail().fallback().contains("unavailable in this view")));
        assertTrue(run(alice, "chunk info " + key).contains(key));
    }

    @Test
    void mapCoordinatesOpenPureWildernessDetailsAndSeedTheExactUnclaimedChunk() {
        tree(alice, "Alpha");
        String key = "minecraft:overworld|12|34";
        AtomicInteger dirty = observe();
        UiView wilderness = detail(alice, EntityRef.Kind.CLAIM, key);
        assertTrue(wilderness.body().fallback().contains("No nation currently claims"));
        UiAction claim = action(wilderness, "chunk claim <nation> <chunks>");
        assertTrue(claim.enabled());
        assertEquals(Map.of("chunks", key), claim.values());
        assertDoesNotThrow(() -> claim.selection().registeredAction());
        assertTrue(data.claims.isEmpty());
        assertEquals(0, dirty.get());
        UiView unauthorized = detail(bob, EntityRef.Kind.CLAIM, key);
        assertFalse(action(unauthorized, "chunk claim <nation> <chunks>").enabled());
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.CLAIM, "minecraft:overworld|012|34"));
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.CLAIM, "minecraft:overworld|1875001|0"));
        data.claims.put(key, null);
        assertThrows(UserError.class, () -> detail(alice, EntityRef.Kind.CLAIM, key));
    }

    @Test
    void dashboardBillAndPaidContractWorkUsesActualRolesAndConflictRules() {
        Tree tree = tree(alice, "Alpha");
        join(bob, tree);
        join(cara, tree);
        run(alice, "nation officer " + tree.nation() + " Cara add");
        run(alice, "bill propose " + tree.nation() + " roleplay - Charter Text");
        String bill = latestBill();
        advance(config.debateDurationMillis);
        assertTrue(pending(alice, EntityRef.Kind.BILL, bill));
        assertTrue(pending(cara, EntityRef.Kind.BILL, bill));
        assertFalse(pending(bob, EntityRef.Kind.BILL, bill));
        run(alice, "bill vote " + bill + " yes");
        run(cara, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        assertTrue(pending(alice, EntityRef.Kind.BILL, bill));
        assertFalse(pending(cara, EntityRef.Kind.BILL, bill));
        run(alice, "bill sign " + bill);
        run(alice, "nation officer " + tree.nation() + " Bob add");
        String key = claim(alice, tree, 0, 0);
        useEconomy();
        economy.balances.put("nation:" + tree.nation(), 1_000L);
        run(alice, "contract create " + tree.nation() + " Road Work " + key);
        String contract = latestContract();
        run(bob, "contract bid " + contract + " 10 Work");
        assertTrue(pending(alice, EntityRef.Kind.CONTRACT, contract));
        run(alice, "contract review " + contract);
        run(alice, "contract award " + contract + " Bob");
        assertTrue(pending(bob, EntityRef.Kind.CONTRACT, contract));
        assertFalse(pending(alice, EntityRef.Kind.CONTRACT, contract));
        run(bob, "contract submit " + contract + " Done");
        assertTrue(pending(alice, EntityRef.Kind.CONTRACT, contract));
        assertTrue(pending(cara, EntityRef.Kind.CONTRACT, contract));
        assertFalse(pending(bob, EntityRef.Kind.CONTRACT, contract));
        assertFalse(pending(operator(bob, true), EntityRef.Kind.CONTRACT, contract));
    }

    @Test
    void dashboardInvitationsDiplomaticDecisionsAndShareholderVotesAreScoped() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        run(alice, "nation invite " + alpha.nation() + " Felix");
        String invitation = data.invitations.keySet().iterator().next();
        assertTrue(pending(felix, EntityRef.Kind.INVITATION, invitation));
        assertFalse(pending(bob, EntityRef.Kind.INVITATION, invitation));
        assertThrows(UserError.class, () -> detail(bob, EntityRef.Kind.INVITATION, invitation));
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        String proposal = latestDiplomacy();
        assertTrue(pending(dave, EntityRef.Kind.DIPLOMACY, proposal));
        assertFalse(pending(alice, EntityRef.Kind.DIPLOMACY, proposal));
        assertFalse(pending(bob, EntityRef.Kind.DIPLOMACY, proposal));
        String company = company(alice, "Builders");
        engine.transferShares(company, alice.id(), cara.id(), 100);
        run(alice, "company propose " + company + " roleplay - Decision Text");
        String shareholder = latestCompanyProposal();
        assertTrue(pending(cara, EntityRef.Kind.COMPANY_PROPOSAL, shareholder));
        assertFalse(pending(bob, EntityRef.Kind.COMPANY_PROPOSAL, shareholder));
        run(cara, "company vote " + shareholder + " yes");
        assertFalse(pending(cara, EntityRef.Kind.COMPANY_PROPOSAL, shareholder));
    }

    @Test
    void retainedLawsAndConcludedProposalsRemainReadableWithoutFormerNonessentialParents() {
        Tree tree = tree(alice, "Alpha");
        run(alice, "bill propose " + tree.nation() + " roleplay - Charter Text");
        String bill = latestBill();
        advance(config.debateDurationMillis);
        run(alice, "bill vote " + bill + " yes");
        advance(config.legislativeVotingMillis);
        run(alice, "bill sign " + bill);
        String company = company(alice, "Builders");
        run(alice, "company propose " + company + " roleplay - Decision Text");
        String proposal = latestCompanyProposal();
        run(alice, "company cancel " + proposal);
        run(alice, "company disband " + company);
        UiView historical = detail(bob, EntityRef.Kind.COMPANY_PROPOSAL, proposal);
        assertTrue(historical.body().fallback().contains("Decision: Roleplay only"));
        assertTrue(historical.actions().stream().noneMatch(UiAction::enabled));
        UiView law = detail(bob, EntityRef.Kind.LAW, tree.nation() + ":" + bill);
        assertTrue(law.body().fallback().contains("Charter") || law.title().fallback().equals("Charter"));
        assertThrows(UserError.class, () -> detail(bob, EntityRef.Kind.LAW, tree.state() + ":" + bill));
    }

    @Test
    void allOwnedSectionAndDetailActionsUseRegisteredTemplatesAndValidSeedKeys() {
        Tree tree = tree(alice, "Alpha");
        String claim = claim(alice, tree, 0, 0);
        String company = company(alice, "Builders");
        run(alice, "bill propose " + tree.nation() + " roleplay - Charter Text");
        run(alice, "company propose " + company + " roleplay - Decision Text");
        run(alice, "contract create " + tree.nation() + " Road Work " + claim);
        engine.mail(alice.id(), "Message", "Body");
        Set<EntityRef> details = new HashSet<>();
        for (String page : List.of("main", "dashboard", "detail", "nations", "states", "cities", "members", "officers",
                "invitations", "claims", "map", "elections", "legislature", "laws", "executive", "diplomacy",
                "companies", "shareholders", "contracts", "mail", "official_mail", "profile", "help")) {
            UiView view = page(alice, page, "", 0);
            assertTrue(view.rows().size() <= UiView.PAGE_SIZE);
            assertTrue(view.actions().size() <= UiView.MAX_ACTIONS);
            assertActions(view);
            view.rows().stream().map(UiRow::entity).filter(EntityRef::present).forEach(details::add);
        }
        for (EntityRef entity : details) assertActions(ui.view(new UiContext(alice, UiQuery.detail(entity))));
        assertActions(page(operator, "admin", "", 0));
        assertThrows(UserError.class, () -> page(alice, "admin", "", 0));
        assertFalse(MenuRegistry.get("statecraft:detail").listed());
        assertEquals("info", MenuRegistry.get("statecraft:detail").query());
        assertTrue(MenuRegistry.get("statecraft:detail").actions().stream().allMatch(a -> a.intent() == ActionIntent.NAVIGATION));
        assertEquals("info", MenuRegistry.get("statecraft:dashboard").query());
        assertTrue(page(alice, "help", "bill veto", 0).rows().stream()
                .anyMatch(row -> row.detail().fallback().contains("bill veto")));
        for (var page : MenuRegistry.pages()) {
            if (!page.id().startsWith("statecraft:")) continue;
            for (var action : page.actions()) {
                assertNotEquals(ActionIntent.RAW, action.intent());
                if (action.financial()) assertEquals(ActionIntent.MUTATION, action.intent());
            }
        }
        assertEquals(ActionIntent.QUERY, MenuRegistry.get("statecraft:mail").actions().stream()
                .filter(a -> a.command().equals("mail read <message>")).findFirst().orElseThrow().intent());
    }

    @Test
    void localizedMetadataHasEnglishTemplatesMatchingItsRenderedFallbacks() throws Exception {
        Path dictionary = Path.of("tools", "translations", "governance.json");
        if (!Files.exists(dictionary)) dictionary = Path.of("..", "tools", "translations", "governance.json");
        var translations = new Gson().fromJson(Files.readString(dictionary), com.google.gson.JsonObject.class);
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(dave, "Beta");
        String key = claim(alice, alpha, 0, 0);
        String company = company(alice, "Builders");
        run(alice, "bill propose " + alpha.nation() + " roleplay - Charter Text");
        run(alice, "company propose " + company + " roleplay - Decision Text");
        run(alice, "contract create " + alpha.nation() + " Road Work " + key);
        run(alice, "diplomacy alliance " + alpha.nation() + " " + beta.nation());
        engine.mail(alice.id(), "Message", "Body");
        List<UiText> texts = new ArrayList<>();
        Set<EntityRef> details = new HashSet<>();
        for (String name : List.of("main", "dashboard", "nations", "states", "cities", "claims", "companies",
                "legislature", "elections", "diplomacy", "shareholders", "contracts", "invitations", "profile", "mail", "help")) {
            UiView view = page(alice, name, "", 0);
            texts(view, texts);
            view.rows().stream().map(UiRow::entity).filter(EntityRef::present).forEach(details::add);
        }
        for (EntityRef entity : details) texts(ui.view(new UiContext(alice, UiQuery.detail(entity))), texts);
        var preview = ui.preview(felix, ActionSelection.raw("statecraft:main", "nation create NewNation"));
        texts.add(preview.title());
        texts.add(preview.warning());
        preview.lines().forEach(line -> { texts.add(line.label()); texts.add(line.value()); });
        for (UiText text : texts) {
            if (text.key().isEmpty()) continue;
            assertTrue(translations.has(text.key()), text.key());
            String template = translations.get(text.key()).getAsString();
            assertEquals(text.fallback(), String.format(Locale.ROOT, template, text.arguments().toArray()), text.key());
        }
    }

    private void texts(UiView view, List<UiText> output) {
        output.add(view.title());
        output.add(view.body());
        output.add(view.emptyHint());
        view.rows().forEach(row -> { output.add(row.title()); output.add(row.detail()); });
        view.actions().forEach(action -> { output.add(action.label()); output.add(action.disabledReason()); });
    }

    private AtomicInteger observe() {
        AtomicInteger dirty = new AtomicInteger();
        engine = new GovernanceEngine(data, config, EconomyAccess.UNAVAILABLE, time::get, dirty::incrementAndGet);
        ui = new GovernancePresentation(engine);
        return dirty;
    }

    private UiView page(Actor actor, String page, String search, int offset) {
        return ui.view(new UiContext(actor, new UiQuery("statecraft:" + page, EntityRef.NONE, search, offset)));
    }

    private UiView detail(Actor actor, EntityRef.Kind kind, String id) {
        return ui.view(new UiContext(actor, UiQuery.detail(new EntityRef("statecraft", kind, id))));
    }

    private boolean pending(Actor actor, EntityRef.Kind kind, String id) {
        return ui.dashboard(actor, id, 0).rows().stream().anyMatch(row -> row.entity().kind() == kind && row.entity().id().equals(id));
    }

    private UiAction action(UiView view, String template) {
        return view.actions().stream().filter(action -> action.template().equals(template)).findFirst().orElseThrow();
    }

    private void assertActions(UiView view) {
        for (UiAction action : view.actions()) {
            assertDoesNotThrow(() -> action.selection().registeredAction(), action.template());
            assertTrue(new CommandTemplate(action.template()).fields().containsAll(action.values().keySet()));
            if (!action.enabled()) assertFalse(action.disabledReason().fallback().isBlank());
            assertFalse(action.values().containsKey("yes_no_abstain"), "A contextual action must not preselect a vote.");
        }
    }
}
