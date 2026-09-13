package dev.statecraft.economy.forge;

import dev.statecraft.api.HelpNavigation;
import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.domain.CoreMenus;
import dev.statecraft.runtime.UiMenus;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelpMenuRegistrationTest {
    @Test
    void bothModsUseOneHelpEntryAndKeepRecipesAsAnInternalDestination() {
        CoreMenus.register();
        UiMenus.register();
        if (MenuRegistry.pages().stream().noneMatch(page -> page.id().equals(HelpNavigation.RECIPES))) EconomyMenus.register();
        assertEquals(List.of("statecraft:dashboard", "statecraft:help"),
                MenuCategory.OVERVIEW.pages(MenuRegistry.pages()).stream().map(MenuPage::id).toList());
        assertFalse(MenuRegistry.get("economy:dashboard").listed());
        assertFalse(MenuRegistry.get(HelpNavigation.RECIPES).listed());
        assertTrue(HelpNavigation.available(MenuRegistry.pages(), HelpNavigation.RECIPES));
        assertFalse(MenuRegistry.get(HelpNavigation.RECIPES).actions().isEmpty());
    }
}
