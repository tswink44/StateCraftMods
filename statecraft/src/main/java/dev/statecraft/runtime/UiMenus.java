package dev.statecraft.runtime;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.ActionIntent;
import java.util.List;

public final class UiMenus {
    public static final String RESOLVE = "admin operation resolve <operation> <resolution> <reason>";
    public static final String RECOVER = "admin operation recover <player> <operation> <resolution> <reason>";

    private UiMenus() {}

    public static void register() {
        add("statecraft:operations", "Your operations");
        add("statecraft:admin_operations", "Operation administration");
    }

    private static void add(String page, String title) {
        if (MenuRegistry.pages().stream().noneMatch(existing -> existing.id().equals(page))) {
            MenuRegistry.register(new MenuPage(page, title, "info", List.of(
                    new MenuPage.Action("Dashboard", "gui statecraft:dashboard", ActionIntent.NAVIGATION),
                    new MenuPage.Action("Reconcile operation", RESOLVE, ActionIntent.MUTATION),
                    new MenuPage.Action("Recover unknown receipt", RECOVER, ActionIntent.MUTATION))));
        }
    }
}
