package dev.statecraft.api.ui;

import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;

public final class ActionDisplay {
    private ActionDisplay() {}

    public static String title(ActionSelection selection) {
        if (selection == null) return "Previous action";
        if (selection.template().isEmpty()) return "Advanced command";
        try {
            return MenuRegistry.get(selection.page()).title() + " - " + selection.registeredAction().label();
        } catch (UserError unavailable) {
            return "Previous action";
        }
    }

    public static String feedback(ActionIntent intent, String title, ActionOutcome outcome, String result) {
        return outcome == ActionOutcome.COMPLETED && intent != ActionIntent.RAW ? title + " completed." : result;
    }

    public static String outcome(ActionOutcome outcome) {
        return switch (outcome) {
            case COMPLETED -> "Completed";
            case REJECTED -> "Not completed";
            case REVIEW_REQUIRED -> "Review again";
            case READY -> "Ready to retry";
            case UNKNOWN -> "Status unavailable - do not repeat";
            case UNCERTAIN -> "Needs attention - do not repeat";
        };
    }
}
