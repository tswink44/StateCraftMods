package dev.statecraft.api;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentOverviewTest {
    private static final String GOVERNMENT = "abcdefab-cdef-abcd-efab-cdefabcdefab";
    private static final String PLAYER = "00000000-0000-0000-0000-000000000001";

    @Test
    void requestRejectsAliasesNoncanonicalIdsAndInvalidOffsets() {
        for (String id : List.of("Nation name", "", "1-1-1-1-1", GOVERNMENT.toUpperCase(java.util.Locale.ROOT))) {
            assertThrows(UserError.class, () -> new GovernmentOverview.Request(id, 0, 0));
        }
        for (int offset : new int[]{-1, 1, 11, 13, 100_000, 100_008, Integer.MAX_VALUE}) {
            assertThrows(UserError.class, () -> new GovernmentOverview.Request(GOVERNMENT, offset, 0));
            assertThrows(UserError.class, () -> new GovernmentOverview.Request(GOVERNMENT, 0, offset));
        }
        assertDoesNotThrow(() -> new GovernmentOverview.Request(GOVERNMENT, 99_996, 99_996));
    }

    @Test
    void changingOneSectionPreservesTheOtherAndGovernmentIdentity() {
        var request = new GovernmentOverview.Request(GOVERNMENT, 12, 24);
        assertEquals(new GovernmentOverview.Request(GOVERNMENT, 36, 24), request.page(GovernmentOverview.Section.CHILDREN, 36));
        assertEquals(new GovernmentOverview.Request(GOVERNMENT, 12, 48), request.page(GovernmentOverview.Section.OFFICERS, 48));
    }

    @Test
    void recordsCopyCollectionsAndKeepReferencesSeparateFromDisplayFields() {
        var actions = new ArrayList<>(List.of(action("nation rename <nation> <name>", UiText.literal("Rename"))));
        var group = new GovernmentOverview.Group("settings", UiText.literal("Settings"), actions);
        var groups = new ArrayList<>(List.of(group));
        GovernmentOverview overview = overview("Realm", "", "", UiText.literal("$1,234.56"), leader("Alice"),
                PersonalDashboard.Page.EMPTY, groups);
        actions.clear();
        groups.clear();
        assertEquals(1, overview.groups().size());
        assertEquals(1, overview.groups().get(0).actions().size());
        assertThrows(UnsupportedOperationException.class, () -> overview.groups().clear());
        assertEquals(GOVERNMENT, overview.groups().get(0).actions().get(0).values().get("nation"));
        assertEquals(PLAYER, overview.leader().target().entity().id());
        assertEquals(PersonalDashboard.Page.EMPTY, overview.section(GovernmentOverview.Section.CHILDREN));
    }

    @Test
    void directAndNestedDisplayFieldsCannotSmuggleRawIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> overview(GOVERNMENT, "", "", UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, List.of()));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", GOVERNMENT, "", UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, List.of()));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "ID " + GOVERNMENT, UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, List.of()));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.literal(GOVERNMENT), null, PersonalDashboard.Page.EMPTY, List.of()));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, leader(PLAYER), PersonalDashboard.Page.EMPTY, List.of()));
        UiText translated = UiText.tr("ui.statecraft.overview.test", "Readable", PLAYER);
        assertThrows(IllegalArgumentException.class, () -> new GovernmentOverview.Group("settings", translated, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GovernmentOverview.Group("settings", UiText.literal("Settings"),
                List.of(action("nation rename <nation> <name>", translated))));
        UiAction disabled = action("nation rename <nation> <name>", UiText.literal("Rename")).disabled(translated);
        assertThrows(IllegalArgumentException.class, () -> new GovernmentOverview.Group("settings", UiText.literal("Settings"), List.of(disabled)));
        var child = new PersonalDashboard.Entry(UiText.literal("Realm"), UiText.literal(GOVERNMENT), governmentTarget());
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, null,
                new PersonalDashboard.Page(List.of(child), 0, 1), List.of()));
    }

    @Test
    void invalidLinkKindsAndNamespacesAreRejected() {
        var child = new PersonalDashboard.Entry(UiText.literal("Company"), UiText.EMPTY,
                UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.COMPANY, GOVERNMENT)));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, null,
                new PersonalDashboard.Page(List.of(child), 0, 1), List.of()));
        var foreign = new PersonalDashboard.Entry(UiText.literal("Alice"), UiText.EMPTY,
                UiQuery.detail(new EntityRef("economy", EntityRef.Kind.PLAYER, PLAYER)));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, foreign, PersonalDashboard.Page.EMPTY, List.of()));
        var alias = new PersonalDashboard.Entry(UiText.literal("Alice"), UiText.EMPTY,
                UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.PLAYER, "Alice")));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, alias, PersonalDashboard.Page.EMPTY, List.of()));
    }

    @Test
    void officialInboxIsNullableAndCanOnlyLinkToThisGovernmentsScopedMailbox() {
        UiQuery inbox = new UiQuery("statecraft:official_mail", new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, GOVERNMENT), "", 0);
        assertEquals(inbox, withInbox(inbox).officialInbox());
        assertNull(withInbox(null).officialInbox());
        for (UiQuery invalid : List.of(UiQuery.page("statecraft:official_mail"), governmentTarget(),
                new UiQuery("statecraft:official_mail", new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, PLAYER), "", 0),
                new UiQuery("statecraft:official_mail", new EntityRef("statecraft", EntityRef.Kind.PLAYER, GOVERNMENT), "", 0),
                new UiQuery("statecraft:official_mail", inbox.entity(), "filter", 0),
                new UiQuery("statecraft:official_mail", inbox.entity(), "", 20))) {
            assertThrows(IllegalArgumentException.class, () -> withInbox(invalid));
        }
    }

    @Test
    void groupAndFieldBoundsAndDuplicateActionsAreEnforced() {
        UiAction action = action("nation rename <nation> <name>", UiText.literal("Rename"));
        assertThrows(IllegalArgumentException.class, () -> new GovernmentOverview.Group("settings", UiText.literal("Settings"),
                java.util.Collections.nCopies(17, action)));
        assertThrows(IllegalArgumentException.class, () -> new GovernmentOverview.Group("settings", UiText.literal("Settings"), List.of(action, action)));
        var groups = new ArrayList<GovernmentOverview.Group>();
        for (int i = 0; i < 9; i++) groups.add(new GovernmentOverview.Group("group" + i, UiText.literal("Actions"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, groups));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "x".repeat(129), "", UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, List.of()));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "x".repeat(2001), UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, List.of()));
        var one = new GovernmentOverview.Group("settings", UiText.literal("Settings"), List.of(action));
        var two = new GovernmentOverview.Group("other", UiText.literal("Other"), List.of(action));
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, List.of(one, two)));
    }

    @Test
    void aggregateBudgetCountsHiddenTranslationArgumentsAndSeedValues() {
        List<GovernmentOverview.Group> groups = new ArrayList<>();
        UiText large = UiText.tr("ui.statecraft.overview.action.large", "Readable label",
                "x".repeat(2048), "x".repeat(2048), "x".repeat(2048), "x".repeat(2048));
        for (int i = 0; i < 8; i++) {
            List<UiAction> actions = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                actions.add(action("government rename <nation> <name" + i + j + ">", large));
            }
            groups.add(new GovernmentOverview.Group("group" + i, UiText.literal("Actions"), actions));
        }
        assertThrows(IllegalArgumentException.class, () -> overview("Realm", "", "", UiText.EMPTY, null, PersonalDashboard.Page.EMPTY, groups));
    }

    private static UiAction action(String template, UiText label) {
        return new UiAction("statecraft:nations", template, label, Map.of("nation", GOVERNMENT));
    }
    private static PersonalDashboard.Entry leader(String name) {
        return new PersonalDashboard.Entry(UiText.literal(name), UiText.literal("National leader"),
                UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.PLAYER, PLAYER)));
    }
    private static UiQuery governmentTarget() {
        return UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, GOVERNMENT));
    }
    private static GovernmentOverview overview(String name, String flag, String description, UiText treasury,
                                               PersonalDashboard.Entry leader, PersonalDashboard.Page children,
                                               List<GovernmentOverview.Group> groups) {
        return new GovernmentOverview(GOVERNMENT, GovernanceAccess.Kind.NATION, name, flag, description, leader, null,
                treasury, children, PersonalDashboard.Page.EMPTY, groups, null);
    }

    private static GovernmentOverview withInbox(UiQuery inbox) {
        return new GovernmentOverview(GOVERNMENT, GovernanceAccess.Kind.NATION, "Realm", "", "", leader("Alice"), null,
                UiText.EMPTY, PersonalDashboard.Page.EMPTY, PersonalDashboard.Page.EMPTY, List.of(), inbox);
    }
}
