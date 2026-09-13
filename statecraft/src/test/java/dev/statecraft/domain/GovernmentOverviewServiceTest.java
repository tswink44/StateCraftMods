package dev.statecraft.domain;

import com.google.gson.Gson;
import dev.statecraft.api.Actor;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentOverviewServiceTest extends DomainFixture {
    private static final Pattern UUID_TEXT = Pattern.compile("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    @Test
    void nationStateAndCityExposeOnlyTheirActualHierarchyAndExactLinks() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(bob, "Beta");
        String key = claim(alice, alpha, 2, -3);
        claim(bob, beta, 30, 0);
        run(alice, "city setting " + alpha.city() + " foreignProperty true");
        run(operator, "admin owner " + key + " " + felix.account());
        GovernmentOverview nation = describe(cara, alpha.nation());
        GovernmentOverview state = describe(cara, alpha.state());
        GovernmentOverview city = describe(cara, alpha.city());
        assertEquals(Kind.NATION, nation.kind());
        assertEquals("AlphaNation", nation.name());
        assertNull(nation.parent());
        assertEquals(List.of(alpha.state()), ids(nation.children()));
        assertEquals(List.of(alpha.city()), ids(state.children()));
        assertEquals("[AlphaCity] (AlphaState/AlphaNation)", state.children().entries().get(0).name().fallback());
        assertEquals(target(EntityRef.Kind.GOVERNMENT, alpha.nation()), state.parent().target());
        assertEquals(target(EntityRef.Kind.GOVERNMENT, alpha.state()), city.parent().target());
        assertEquals(target(EntityRef.Kind.PLAYER, alice.id().toString()), city.leader().target());
        assertEquals(List.of(key), ids(city.children()));
        assertEquals("Chunk 2, -3 (Overworld)", city.children().entries().get(0).name().fallback());
        assertEquals(target(EntityRef.Kind.CLAIM, key), city.children().entries().get(0).target());
        assertEquals("Assigned territory", city.children().entries().get(0).detail().fallback());
        assertEquals("Governor", state.leader().detail().fallback());
        assertEquals("Mayor", city.leader().detail().fallback());
        assertFalse(ids(nation.children()).contains(beta.state()));
    }

    @Test
    void treasuryIsReadOnlyForLeadersOfficersAncestorsAndOperatorsNotCitizensOrForeigners() {
        Tree alpha = tree(alice, "Alpha");
        tree(bob, "Beta");
        join(cara, alpha);
        join(dave, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        useEconomy();
        economy.balances.put("nation:" + alpha.nation(), 123_456L);
        economy.balances.put("city:" + alpha.city(), 4_321L);
        ProbeEconomy probe = new ProbeEconomy();
        var service = new GovernmentOverviewService(engine, probe);
        for (Actor visitor : List.of(bob, dave, actor(900, "NewVisitor", false))) {
            assertEquals("Officials only", service.describe(visitor, request(alpha.nation())).treasury().fallback());
        }
        assertTrue(probe.reads.isEmpty(), "Hidden balances must not even be fetched.");
        for (Actor authorized : List.of(alice, cara, operator)) {
            assertEquals(Money.format(123_456), service.describe(authorized, request(alpha.nation())).treasury().fallback());
        }
        assertEquals(Money.format(4_321), service.describe(cara, request(alpha.city())).treasury().fallback());
        assertEquals(List.of("nation:" + alpha.nation(), "nation:" + alpha.nation(), "nation:" + alpha.nation(),
                "city:" + alpha.city()), probe.reads);
        run(alice, "government officer " + alpha.nation() + " Cara remove");
        assertEquals("Officials only", service.describe(cara, request(alpha.nation())).treasury().fallback());
        assertEquals(4, probe.reads.size());
    }

    @Test
    void missingOrFailedEconomyNeverInventsAZeroBalance() {
        Tree alpha = tree(alice, "Alpha");
        var missing = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        for (Actor actor : List.of(alice, bob, operator)) {
            assertEquals("Economy not installed", missing.describe(actor, request(alpha.nation())).treasury().fallback());
        }
        ProbeEconomy probe = new ProbeEconomy();
        probe.fail = true;
        var service = new GovernmentOverviewService(engine, probe);
        assertEquals("Balance unavailable", service.describe(alice, request(alpha.nation())).treasury().fallback());
        assertEquals("Officials only", service.describe(bob, request(alpha.nation())).treasury().fallback());
        assertEquals(1, probe.reads.size());
        probe.available = false;
        assertEquals("Economy not installed", service.describe(alice, request(alpha.nation())).treasury().fallback());
        assertEquals(1, probe.reads.size());
    }

    @Test
    void rostersDistinguishAppointedOfficialsFromLeadersAndUseCurrentHumanNames() {
        Tree alpha = tree(alice, "Alpha");
        join(bob, alpha);
        join(cara, alpha);
        join(dave, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        run(alice, "government officer " + alpha.nation() + " Bob add");
        run(alice, "government officer " + alpha.city() + " Dave add");
        engine.login(new Actor(bob.id(), "Bobby", false, bob.dimension(), bob.chunkX(), bob.chunkZ()));
        GovernmentOverview nation = describe(alice, alpha.nation());
        assertEquals(List.of("Alice", "Bobby", "Cara"), nation.officers().entries().stream().map(entry -> entry.name().fallback()).toList());
        assertEquals(List.of("National leader", "Appointed officer", "Appointed officer"),
                nation.officers().entries().stream().map(entry -> entry.detail().fallback()).toList());
        assertEquals(List.of(alice.id().toString(), bob.id().toString(), cara.id().toString()), ids(nation.officers()));
        assertEquals(List.of("Alice", "Dave"), describe(alice, alpha.city()).officers().entries().stream()
                .map(entry -> entry.name().fallback()).toList());
        assertEquals(target(EntityRef.Kind.PLAYER, bob.id().toString()), nation.officers().entries().get(1).target());
    }

    @Test
    void childrenAndOfficersPageIndependentlyAtTwelveAndClampAfterTheLastPage() {
        Tree alpha = tree(alice, "Alpha");
        for (int i = 0; i < 12; i++) {
            Actor governor = actor(100 + i, "Governor" + String.format(java.util.Locale.ROOT, "%02d", i), false);
            engine.login(governor);
            run(governor, "nation join " + alpha.nation());
            run(alice, "state create " + alpha.nation() + " Province" + String.format(java.util.Locale.ROOT, "%02d", i) + " " + governor.name());
            run(alice, "government officer " + alpha.nation() + " " + governor.name() + " add");
        }
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        GovernmentOverview first = service.describe(bob, request(alpha.nation()));
        GovernmentOverview childrenNext = service.describe(bob, new GovernmentOverview.Request(alpha.nation(), 12, 0));
        GovernmentOverview officersNext = service.describe(bob, new GovernmentOverview.Request(alpha.nation(), 0, 12));
        GovernmentOverview last = service.describe(bob, new GovernmentOverview.Request(alpha.nation(), 99_996, 99_996));
        assertEquals(13, first.children().total());
        assertEquals(13, first.officers().total());
        assertEquals(12, first.children().entries().size());
        assertEquals(12, first.officers().entries().size());
        assertEquals(1, childrenNext.children().entries().size());
        assertEquals(12, childrenNext.children().offset());
        assertEquals(first.officers(), childrenNext.officers());
        assertEquals(first.children(), officersNext.children());
        assertEquals(1, officersNext.officers().entries().size());
        assertEquals(12, officersNext.officers().offset());
        assertEquals(childrenNext.children(), last.children());
        assertEquals(officersNext.officers(), last.officers());
        assertEquals("AlphaState", first.children().entries().get(0).name().fallback());
        assertFalse(last.children().more());
        assertFalse(last.officers().more());
    }

    @Test
    void cityClaimsAreNumericallySortedAndPagedIncludingForeignPrivateTitles() {
        Tree alpha = tree(alice, "Alpha");
        for (int x = 0; x < 14; x++) claim(alice, alpha, x, 0);
        run(alice, "city setting " + alpha.city() + " foreignProperty true");
        run(operator, "admin owner minecraft:overworld|12|0 " + felix.account());
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        GovernmentOverview first = service.describe(bob, request(alpha.city()));
        GovernmentOverview last = service.describe(bob, new GovernmentOverview.Request(alpha.city(), 12, 0));
        assertEquals(14, first.children().total());
        assertEquals("minecraft:overworld|2|0", first.children().entries().get(2).target().entity().id());
        assertEquals(List.of("minecraft:overworld|12|0", "minecraft:overworld|13|0"), ids(last.children()));
        assertEquals(0, last.officers().offset());
    }

    @Test
    void categoriesSeedOnlyThisGovernmentAndKeepCreationOnExistingFinancialForms() {
        Tree alpha = tree(alice, "Alpha");
        Tree beta = tree(bob, "Beta");
        GovernmentOverview nation = describe(alice, alpha.nation());
        assertEquals(List.of("diplomacy", "executive", "legislature", "elections", "settings", "contracts"),
                nation.groups().stream().map(GovernmentOverview.Group::id).toList());
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
            GovernmentOverview overview = describe(alice, id);
            var unique = new HashSet<String>();
            assertTrue(overview.groups().size() <= GovernmentOverview.MAX_GROUPS);
            for (GovernmentOverview.Group group : overview.groups()) {
                assertTrue(group.actions().size() <= GovernmentOverview.MAX_ACTIONS_PER_GROUP);
                for (UiAction action : group.actions()) {
                    assertTrue(action.values().containsValue(id), action.template());
                    assertFalse(action.values().containsValue(beta.nation()), action.template());
                    assertFalse(action.template().contains(" all "), action.template());
                    assertFalse(action.template().startsWith("nation create"), "Nation creation must not bypass its existing reviewed flow.");
                    assertTrue(unique.add(action.page() + "\n" + action.template()));
                    assertEquals(action.template(), action.selection().registeredAction().command());
                }
            }
        }
        UiAction state = action(nation, "state create <nation> <name> <governor>");
        UiAction city = action(describe(alice, alpha.state()), "city create <state> <name> <mayor>");
        assertEquals(Map.of("nation", alpha.nation()), state.values());
        assertEquals(Map.of("state", alpha.state()), city.values());
        assertTrue(state.selection().registeredAction().financial());
        assertTrue(city.selection().registeredAction().financial());
    }

    @Test
    void statesAndCitiesDoNotAcquireNationalPowersAndMembersAreNotOfficials() {
        Tree alpha = tree(alice, "Alpha");
        join(cara, alpha);
        join(dave, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        GovernmentOverview citizen = describe(dave, alpha.nation());
        assertTrue(citizen.groups().stream().noneMatch(group -> "settings".equals(group.id())));
        assertFalse(hasAction(citizen, "nation rename <nation> <name>"));
        assertNull(citizen.officialInbox());
        assertFalse(action(citizen, "diplomacy war <from_nation> <to_nation> <reason>").enabled());
        assertTrue(action(describe(cara, alpha.nation()), "nation rename <nation> <name>").enabled());
        assertFalse(action(describe(cara, alpha.nation()), "government officer <government> <player> add").enabled());
        assertTrue(action(describe(operator, alpha.nation()), "nation disband <nation>").enabled());
        for (String id : List.of(alpha.state(), alpha.city())) {
            GovernmentOverview subnational = describe(alice, id);
            assertTrue(subnational.groups().stream().noneMatch(group ->
                    Set.of("diplomacy", "executive", "legislature", "elections").contains(group.id())));
            assertTrue(subnational.groups().stream().flatMap(group -> group.actions().stream())
                    .noneMatch(action -> action.template().startsWith("diplomacy ") || action.template().startsWith("bill ")
                            || action.template().startsWith("election ")));
        }
        for (UiAction action : citizen.groups().stream().flatMap(group -> group.actions().stream()).toList()) {
            if (!action.enabled()) assertFalse(action.disabledReason().fallback().isBlank());
        }
    }

    @Test
    void settingsAndInvitationManagementAreOnlyVisibleToCurrentOfficialsAtEveryLevel() {
        Tree alpha = tree(alice, "Alpha");
        tree(bob, "Beta");
        join(cara, alpha);
        join(dave, alpha);
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
            run(alice, "government officer " + id + " Cara add");
            GovernmentOverview publicView = describe(bob, id);
            assertEquals(List.of("Alice", "Cara"), publicView.officers().entries().stream()
                    .map(entry -> entry.name().fallback()).toList());
            for (Actor visitor : List.of(dave, bob, operator(operator, false), actor(900, "Visitor", false))) {
                GovernmentOverview view = describe(visitor, id);
                assertTrue(view.groups().stream().noneMatch(group -> "settings".equals(group.id())));
                assertNull(view.officialInbox());
                for (String template : settingsManagementTemplates()) assertFalse(hasAction(view, template), template);
                assertEquals(publicView.officers(), view.officers());
            }
            for (Actor official : List.of(alice, cara, operator)) {
                GovernmentOverview view = describe(official, id);
                assertEquals(new UiQuery("statecraft:official_mail",
                        new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, id), "", 0), view.officialInbox());
                GovernmentOverview.Group settings = view.groups().stream().filter(group -> "settings".equals(group.id()))
                        .findFirst().orElseThrow();
                assertEquals(14, settings.actions().size());
                for (String template : settingsManagementTemplates()) {
                    assertTrue(settings.actions().stream().anyMatch(action -> template.equals(action.template())), template);
                    assertEquals(id, action(view, template).values().get("government"));
                }
                for (String template : List.of("government invite <government> <player>",
                        "government invitations <government> <page>", "government revoke <government> <player>")) {
                    assertTrue(action(view, template).enabled(), template);
                }
                assertEquals(publicView.officers(), view.officers());
            }
            assertFalse(action(describe(cara, id), "government officer <government> <player> add").enabled());
            assertFalse(action(describe(cara, id), "government officer <government> <player> remove").enabled());
            assertFalse(action(describe(cara, id), "government leader <government> <player>").enabled());
            assertTrue(action(describe(alice, id), "government officer <government> <player> add").enabled());
            assertTrue(action(describe(alice, id), "government officer <government> <player> remove").enabled());
            assertThrows(UserError.class, () -> run(cara, "government officer " + id + " Dave add"));
            run(alice, "government officer " + id + " Cara remove");
        }
    }

    @Test
    void ancestorAuthorityAndRevocationAreRecheckedWithoutHidingThePublicOfficerRoster() {
        Tree alpha = tree(alice, "Alpha");
        join(cara, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
            GovernmentOverview view = service.describe(cara, request(id));
            assertTrue(view.groups().stream().anyMatch(group -> "settings".equals(group.id())));
            assertTrue(action(view, "government invite <government> <player>").enabled());
        }
        run(alice, "government officer " + alpha.nation() + " Cara remove");
        for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
            GovernmentOverview view = service.describe(cara, request(id));
            assertTrue(view.groups().stream().noneMatch(group -> "settings".equals(group.id())));
            assertEquals(List.of("Alice"), view.officers().entries().stream().map(entry -> entry.name().fallback()).toList());
            assertEquals(describe(bob, id).officers(), view.officers());
        }
    }

    @Test
    void invitationRecipientsGetNoSettingsAccessAndThereAreNoMemberOrInvitationCategoryTabs() {
        Tree alpha = tree(alice, "Alpha");
        join(cara, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        run(alice, "government invite " + alpha.nation() + " Felix");
        assertNotNull(engine.invitation(alpha.nation(), null, felix.id().toString()));
        GovernmentOverview invited = describe(felix, alpha.nation());
        assertTrue(invited.groups().stream().noneMatch(group -> "settings".equals(group.id())));
        assertFalse(hasAction(invited, "government invitations <government> <page>"));
        for (Actor actor : List.of(alice, cara, bob, felix, operator)) {
            for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) {
                GovernmentOverview view = describe(actor, id);
                assertTrue(view.groups().stream().noneMatch(group ->
                        Set.of("members", "officers", "invitations", "communications", "mail", "official_mail").contains(group.id())));
                for (String template : List.of("government members <government> <page>", "government roles <government> <page>",
                        "government officers <government> <page>", "government accept <government>", "government decline <government>")) {
                    assertFalse(hasAction(view, template), template);
                }
                view.groups().stream().filter(group -> !"settings".equals(group.id())).forEach(group ->
                        assertTrue(group.actions().stream().noneMatch(action -> settingsManagementTemplates().contains(action.template()))));
                assertFalse(view.officers().entries().isEmpty());
            }
        }
    }

    @Test
    void describingDoesNotTickLoginMutateExpireMailOrChargeAnything() {
        Tree alpha = tree(alice, "Alpha");
        join(cara, alpha);
        run(alice, "government invite " + alpha.nation() + " Dave");
        run(alice, "executive emergency " + alpha.nation() + " pvp false Prepared");
        engine.governmentMail(alpha.nation(), "Official notice", "Private body never appears in the overview.");
        useEconomy();
        AtomicInteger dirty = new AtomicInteger();
        engine = new GovernanceEngine(data, config, economy, time::get, dirty::incrementAndGet);
        dirty.set(0);
        time.addAndGet(50_000);
        String before = new Gson().toJson(data);
        Map<String, Long> balances = Map.copyOf(economy.balances);
        int attempts = economy.attempts;
        for (Actor actor : List.of(alice, cara, bob, operator, actor(900, "Visitor", false))) {
            for (String id : List.of(alpha.nation(), alpha.state(), alpha.city())) describe(actor, id);
        }
        assertEquals(before, new Gson().toJson(data));
        assertEquals(0, dirty.get());
        assertEquals(attempts, economy.attempts);
        assertEquals(balances, economy.balances);
        assertFalse(data.players.containsKey(actor(900, "Visitor", false).id().toString()));
        assertTrue(data.governments.get(alpha.nation()).inbox.stream().noneMatch(mail -> mail.read));
    }

    @Test
    void missingGovernmentsOtherEntityIdsAndInvalidHierarchyFailWithoutLeakingIds() {
        Tree alpha = tree(alice, "Alpha");
        String company = company(alice, "Builders");
        var service = new GovernmentOverviewService(engine, EconomyAccess.UNAVAILABLE);
        for (String missing : List.of(company, java.util.UUID.randomUUID().toString())) {
            UserError error = assertThrows(UserError.class, () -> service.describe(alice, request(missing)));
            assertFalse(error.getMessage().contains(missing));
        }
        assertThrows(UserError.class, () -> service.describe(alice, request("AlphaNation")));
        data.governments.get(alpha.state()).parentId = alpha.city();
        assertThrows(UserError.class, () -> service.describe(operator, request(alpha.state())));
        assertThrows(UserError.class, () -> service.describe(operator, request(alpha.city())));
    }

    @Test
    void storedFlagTextDescriptionsAndNamesStayReadableWithoutUuidFallbacks() {
        Tree alpha = tree(alice, "Alpha");
        join(cara, alpha);
        run(alice, "government officer " + alpha.nation() + " Cara add");
        run(alice, "nation flag " + alpha.nation() + " " + q("Red field with three white stars"));
        run(alice, "nation description " + alpha.nation() + " " + q("A federation beside the river"));
        GovernmentOverview clean = describe(bob, alpha.nation());
        assertEquals("Red field with three white stars", clean.flag());
        assertEquals("A federation beside the river", clean.description());
        data.players.get(cara.id().toString()).name = cara.id().toString();
        data.governments.get(alpha.nation()).description = "Reference " + alpha.nation();
        data.governments.get(alpha.nation()).flag = "Archive " + alpha.state();
        GovernmentOverview redacted = describe(bob, alpha.nation());
        assertEquals("Former player", redacted.officers().entries().get(1).name().fallback());
        assertEquals(target(EntityRef.Kind.PLAYER, cara.id().toString()), redacted.officers().entries().get(1).target());
        assertNoIds(redacted);
        assertFalse(redacted.flag().contains(alpha.state().substring(alpha.state().length() - 8)));
    }

    @Test
    void emptyChildrenRemainGenuinelyEmptyAndNoSyntheticOfficersAppear() {
        run(alice, "nation create EmptyRealm");
        GovernmentOverview overview = describe(bob, government("EmptyRealm").id());
        assertEquals(PersonalDashboard.Page.EMPTY, overview.children());
        assertEquals(List.of("Alice"), overview.officers().entries().stream().map(entry -> entry.name().fallback()).toList());
        assertEquals("", overview.flag());
        assertEquals("", overview.description());
        assertNull(overview.parent());
    }

    @Test
    void nationAndStateOverviewsSupportTerritoryWithoutLowerLevelAssignments() {
        run(alice, "nation create Frontier");
        String nation = government("Frontier").id();
        String key = "minecraft:overworld|0|0";
        run(alice, "chunk claim " + nation + " " + key);
        assertEquals(nation, data.claims.get(key).nationId);
        assertNull(data.claims.get(key).stateId);
        assertNull(data.claims.get(key).cityId);
        GovernmentOverview nationView = describe(alice, nation);
        assertEquals(PersonalDashboard.Page.EMPTY, nationView.children());
        assertNotNull(nationView.officialInbox());
        run(alice, "state create " + nation + " Borderlands");
        String state = government("Borderlands").id();
        run(alice, "chunk assignstate " + nation + " " + key + " " + state);
        GovernmentOverview stateView = describe(alice, state);
        assertEquals(PersonalDashboard.Page.EMPTY, stateView.children());
        assertEquals(state, data.claims.get(key).stateId);
        assertNull(data.claims.get(key).cityId);
        assertNotNull(stateView.officialInbox());
        for (GovernmentOverview view : List.of(nationView, stateView)) {
            assertTrue(view.groups().stream().flatMap(group -> group.actions().stream())
                    .noneMatch(action -> action.template().startsWith("chunk claim ")));
        }
    }

    @Test
    void cityOverviewHasNoClaimActionsOrStandaloneMailCategory() {
        Tree alpha = tree(alice, "Alpha");
        GovernmentOverview city = describe(alice, alpha.city());
        assertNotNull(city.officialInbox());
        assertTrue(city.groups().stream().noneMatch(group -> "official_mail".equals(group.id()) || "mail".equals(group.id())));
        assertTrue(city.groups().stream().flatMap(group -> group.actions().stream())
                .noneMatch(action -> action.template().startsWith("chunk claim ") || action.template().startsWith("mail ")));
    }

    private GovernmentOverview describe(Actor actor, String id) {
        return new GovernmentOverviewService(engine, engine.economy).describe(actor, request(id));
    }

    private static GovernmentOverview.Request request(String id) { return new GovernmentOverview.Request(id, 0, 0); }
    private static UiQuery target(EntityRef.Kind kind, String id) { return UiQuery.detail(new EntityRef("statecraft", kind, id)); }
    private static List<String> ids(PersonalDashboard.Page page) {
        return page.entries().stream().map(entry -> entry.target().entity().id()).toList();
    }
    private static UiAction action(GovernmentOverview overview, String template) {
        return overview.groups().stream().flatMap(group -> group.actions().stream())
                .filter(action -> template.equals(action.template())).findFirst().orElseThrow();
    }
    private static boolean hasAction(GovernmentOverview overview, String template) {
        return overview.groups().stream().flatMap(group -> group.actions().stream())
                .anyMatch(action -> template.equals(action.template()));
    }
    private static List<String> settingsManagementTemplates() {
        return List.of("government leader <government> <player>", "government officer <government> <player> add",
                "government officer <government> <player> remove", "government invite <government> <player>",
                "government invitations <government> <page>", "government revoke <government> <player>");
    }

    private static void assertNoIds(GovernmentOverview overview) {
        List<String> display = new ArrayList<>(List.of(overview.name(), overview.flag(), overview.description()));
        add(display, overview.treasury());
        List<PersonalDashboard.Entry> entries = new ArrayList<>(overview.children().entries());
        entries.addAll(overview.officers().entries());
        if (overview.leader() != null) entries.add(overview.leader());
        if (overview.parent() != null) entries.add(overview.parent());
        entries.forEach(entry -> { add(display, entry.name()); add(display, entry.detail()); });
        overview.groups().forEach(group -> {
            add(display, group.title());
            group.actions().forEach(action -> { add(display, action.label()); add(display, action.disabledReason()); });
        });
        display.forEach(value -> assertFalse(UUID_TEXT.matcher(value).find(), value));
    }

    private static void add(List<String> result, UiText text) {
        result.add(text.fallback());
        result.addAll(text.arguments());
    }

    private final class ProbeEconomy implements EconomyAccess {
        final List<String> reads = new ArrayList<>();
        boolean available = true;
        boolean fail;

        @Override public boolean available() { return available; }
        @Override public long balance(String account) {
            assertTrue(Thread.holdsLock(engine), "Overview projections must hold the engine lock.");
            reads.add(account);
            if (fail) throw new UserError("This failure must not leak account " + account);
            return economy.balance(account);
        }
        @Override public void transferBatch(List<Transfer> transfers) { fail("The overview must never transfer money."); }
        @Override public boolean isClaimEncumbered(String key) { fail("Unexpected economy mutation planning."); return false; }
        @Override public boolean isAccountInUse(String account) { fail("Unexpected account inspection."); return false; }
        @Override public long valueOf(String key) { fail("Unexpected land valuation."); return 0; }
    }
}
