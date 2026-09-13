package dev.statecraft.api;

import java.util.Collection;
import java.util.List;

public final class HelpNavigation {
    public static final String COMMANDS = "statecraft:command_help";
    public static final String RECIPES = "economy:guide";

    private HelpNavigation() {}

    public static boolean available(Collection<MenuPage> pages, String target) {
        return List.of(COMMANDS, RECIPES).contains(target) && pages.stream().anyMatch(page -> page.id().equals(target));
    }

}
