package dev.statecraft.api.ui;

import dev.statecraft.api.Actor;

public interface UiProvider {
    UiView view(UiContext context);
    UiView dashboard(Actor actor, String search, int offset);

    default String queryText(Actor actor, ActionSelection selection, String commandResult) {
        return commandResult;
    }

    default PersonalDashboard.Page personalAccounts(Actor actor, int offset) {
        return PersonalDashboard.Page.EMPTY;
    }
}
