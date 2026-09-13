package dev.statecraft.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuRegistry {
    private static final Map<String, MenuPage> PAGES = new LinkedHashMap<>();

    private MenuRegistry() {}

    public static void register(MenuPage page) {
        if (PAGES.putIfAbsent(page.id(), page) != null) {
            throw new IllegalStateException("Duplicate menu page: " + page.id());
        }
    }

    public static Collection<MenuPage> pages() {
        return List.copyOf(PAGES.values());
    }

    public static MenuPage get(String id) {
        MenuPage page = PAGES.get(id);
        if (page == null) {
            throw new UserError("Unknown menu: " + id);
        }
        return page;
    }
}
