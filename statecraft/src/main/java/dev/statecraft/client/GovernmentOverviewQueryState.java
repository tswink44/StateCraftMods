package dev.statecraft.client;

import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.ViewState;

final class GovernmentOverviewQueryState {
    enum Phase { LOADING, READY, FAILED, INTERRUPTED }

    private int request = -1;
    private UiText startingBanner;
    private UiText failure = UiText.EMPTY;
    private boolean finished;
    private boolean interrupted;

    void observe(ViewState state) {
        if (state.content() != ViewState.Content.QUERY) {
            request = -1;
            startingBanner = null;
            failure = UiText.EMPTY;
            finished = false;
            interrupted = false;
        } else if (state.pending() >= 0 && state.pending() != request) {
            request = state.pending();
            startingBanner = state.banner();
            failure = UiText.EMPTY;
            finished = false;
            interrupted = false;
        } else if (request >= 0 && state.pending() < 0 && !finished) {
            interrupted = state.stale();
            if (!state.stale() && state.banner() != startingBanner && !state.outcome().success()) failure = state.banner();
            finished = true;
        }
    }

    Phase phase(ViewState state) {
        if (state.pending() >= 0) return Phase.LOADING;
        if (finished ? interrupted : state.stale()) return Phase.INTERRUPTED;
        return failure.fallback().isEmpty() ? Phase.READY : Phase.FAILED;
    }

    String body(ViewState state) {
        return phase(state) == Phase.FAILED ? failure.fallback() : state.output();
    }

    UiText failure() { return failure; }

    UiText placeholder(ViewState state) {
        return switch (phase(state)) {
            case LOADING -> UiText.tr("gui.statecraft.government.query_waiting", "Waiting for the selected query's response…");
            case INTERRUPTED -> UiText.tr("gui.statecraft.government.query_interrupted", "The query was interrupted. Run it again, or return to the overview.");
            default -> UiText.tr("gui.statecraft.government.query_empty", "The query completed without additional text.");
        };
    }

    static void returnToOverview(ViewState state) {
        if (state.content() == ViewState.Content.QUERY) state.query(state.query());
    }
}
