package dev.statecraft.api;

import dev.statecraft.api.ui.ActionIntent;
import java.util.List;

public record MenuPage(String id, String title, String query, List<Action> actions, boolean listed) {
    public MenuPage(String id, String title, String query, List<Action> actions) {
        this(id, title, query, actions, true);
    }

    public MenuPage {
        if (!id.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_-]*") || title.isBlank() || title.length() > 128
                || query.isBlank() || query.length() > CommandLine.MAX_LENGTH || actions.size() > 128) {
            throw new IllegalArgumentException("Invalid menu page: " + id);
        }
        actions = List.copyOf(actions);
    }

    public record Action(String label, String command, ActionIntent intent, boolean financial) {
        public Action(String label, String command) { this(label, command, ActionIntent.MUTATION, false); }
        public Action(String label, String command, ActionIntent intent) { this(label, command, intent, false); }

        public Action {
            if (label == null || label.isBlank() || label.length() > 128 || command == null || command.isBlank()
                    || command.length() > CommandLine.MAX_LENGTH || intent == null || intent == ActionIntent.RAW
                    || financial && intent != ActionIntent.MUTATION) {
                throw new IllegalArgumentException("Invalid menu action.");
            }
            if (intent == ActionIntent.NAVIGATION) {
                List<String> words = CommandLine.split(command);
                if (words.size() != 2 || !words.get(0).equals("gui") || !new CommandTemplate(command).fields().isEmpty()) {
                    throw new IllegalArgumentException("Navigation actions must target a fixed registered page.");
                }
            }
        }
    }
}
