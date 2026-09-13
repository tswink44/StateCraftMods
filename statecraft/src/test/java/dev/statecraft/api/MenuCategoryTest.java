package dev.statecraft.api;

import dev.statecraft.domain.CoreMenus;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuCategoryTest {
    @Test
    void relatedGovernmentPagesShareOneSubmenu() {
        CoreMenus.register();
        Set<String> pages = MenuCategory.GOVERNMENTS.pages(MenuRegistry.pages()).stream()
                .map(MenuPage::id).collect(Collectors.toSet());
        assertEquals(Set.of("statecraft:nations", "statecraft:states", "statecraft:cities"), pages);
        assertTrue(MenuCategory.COMMUNICATIONS.pages(MenuRegistry.pages()).isEmpty());
        for (String hidden : List.of("members", "officers", "invitations", "mail", "official_mail")) {
            assertFalse(MenuRegistry.get("statecraft:" + hidden).listed());
        }
        assertEquals(MenuCategory.POLITICS, MenuCategory.of("statecraft:elections"));
        assertEquals(MenuCategory.TERRITORY, MenuCategory.of("economy:property"));
        assertEquals(MenuCategory.BUSINESS, MenuCategory.of("economy:stock"));
    }

    @Test
    void everyListedCoreSectionRemainsReachableExactlyOnce() {
        CoreMenus.register();
        List<MenuPage> pages = MenuRegistry.pages().stream().filter(page -> page.id().startsWith("statecraft:"))
                .filter(MenuPage::listed).toList();
        List<MenuPage> categorized = Arrays.stream(MenuCategory.values()).flatMap(category -> category.pages(pages).stream()).toList();
        assertEquals(pages.size(), categorized.size());
        assertEquals(Set.copyOf(pages), Set.copyOf(categorized));
        assertTrue(MenuCategory.OTHER.pages(pages).isEmpty());
        assertFalse(MenuRegistry.get("statecraft:detail").listed());
        assertFalse(categorized.contains(MenuRegistry.get("statecraft:detail")));
        assertEquals(MenuCategory.ECONOMY, MenuCategory.of("economy:loans"));
        assertEquals(MenuCategory.ECONOMY, MenuCategory.of("economy:deliveries"));
        assertEquals(MenuCategory.OTHER, MenuCategory.of("addon:section"));
    }
}
