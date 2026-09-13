package dev.statecraft.api;

import dev.statecraft.api.ui.UiContext;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.UiPresentation;
import dev.statecraft.domain.CoreMenus;
import dev.statecraft.domain.GovernanceConfig;
import dev.statecraft.domain.GovernanceData;
import dev.statecraft.domain.GovernanceEngine;
import dev.statecraft.domain.GovernancePresentation;
import dev.statecraft.runtime.UiMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelpNavigationTest {
    @Test
    void overviewContainsOnlyDashboardAndCombinedHelp() {
        CoreMenus.register();
        UiMenus.register();
        assertEquals(List.of("statecraft:dashboard", "statecraft:help"),
                MenuCategory.OVERVIEW.pages(MenuRegistry.pages()).stream().map(MenuPage::id).toList());
        assertEquals("Help", MenuRegistry.get("statecraft:help").title());
        for (String id : List.of("statecraft:main", "statecraft:profile", "statecraft:operations", HelpNavigation.COMMANDS)) {
            assertFalse(MenuRegistry.get(id).listed(), id);
        }
        assertTrue(MenuRegistry.get("statecraft:admin_operations").listed());
    }

    @Test
    void hiddenHelpDestinationsStayReachableWithoutRequiringEconomy() {
        CoreMenus.register();
        List<MenuPage> core = MenuRegistry.pages().stream().filter(page -> page.id().startsWith("statecraft:")).toList();
        assertTrue(HelpNavigation.available(core, HelpNavigation.COMMANDS));
        assertFalse(HelpNavigation.available(core, HelpNavigation.RECIPES));
        var both = new ArrayList<>(core);
        both.add(new MenuPage(HelpNavigation.RECIPES, "Recipes", "guide", List.of(), false));
        assertTrue(HelpNavigation.available(both, HelpNavigation.RECIPES));
        assertFalse(HelpNavigation.available(both, "statecraft:operations"));
    }

    @Test
    void recoveryIsNotAnAlwaysVisibleActivityEntry() {
        assertFalse(UiPresentation.needsAttention(0, false));
        assertTrue(UiPresentation.needsAttention(1, false));
        assertTrue(UiPresentation.needsAttention(0, true));
    }

    @Test
    void nationsKeepTheirRowsWithoutTheAuthorityBlurbAndCommandHelpStillWorks() {
        CoreMenus.register();
        Actor actor = new Actor(UUID.randomUUID(), "Reader", false, "minecraft:overworld", 0, 0);
        GovernanceEngine engine = new GovernanceEngine(new GovernanceData(), new GovernanceConfig(), EconomyAccess.UNAVAILABLE, () -> 1000);
        engine.execute(actor, "nation create Arcadia");
        GovernancePresentation presentation = new GovernancePresentation(engine);
        var nations = presentation.view(new UiContext(actor, UiQuery.page("statecraft:nations")));
        assertEquals(UiText.EMPTY, nations.body());
        assertTrue(nations.rows().stream().anyMatch(row -> row.title().fallback().equals("Arcadia")));
        var help = presentation.view(new UiContext(actor, UiQuery.page(HelpNavigation.COMMANDS)));
        assertFalse(help.rows().isEmpty());
        assertFalse(MenuRegistry.get(HelpNavigation.COMMANDS).listed());
    }
}
