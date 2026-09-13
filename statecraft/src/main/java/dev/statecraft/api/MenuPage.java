package dev.statecraft.api;

import java.util.List;

public record MenuPage(String id, String title, String query, List<Action> actions) {
    public MenuPage {
        if (!id.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_-]*") || title.isBlank() || title.length() > 128
                || query.isBlank() || query.length() > CommandLine.MAX_LENGTH || actions.size() > 128) {
            throw new IllegalArgumentException("Invalid menu page: " + id);
        }
        actions = List.copyOf(actions);
    }

    public record Action(String label, String command) {}
}
