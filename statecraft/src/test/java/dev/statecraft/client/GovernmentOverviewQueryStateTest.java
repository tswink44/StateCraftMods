package dev.statecraft.client;

import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.ViewState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentOverviewQueryStateTest {
    private static final String GOVERNMENT = "00000000-0000-0000-0000-000000000001";
    private static final ActionSelection QUERY = ActionSelection.form("statecraft:nations", "nation settings <nation>",
            Map.of("nation", GOVERNMENT));

    @Test
    void liveQueryAndFullOutputStayInTheSameViewStateUntilExplicitBack() {
        ViewState state = state();
        UiQuery location = state.query();
        GovernmentOverview overview = state.governmentOverview();
        var result = new GovernmentOverviewQueryState();
        request(state, result, 41);
        assertEquals(41, state.pending());
        assertSame(QUERY, state.explicit());
        assertSame(overview, state.governmentOverview());
        assertEquals(GovernmentOverviewQueryState.Phase.LOADING, result.phase(state));
        String output = "Full query response\n" + "x".repeat(40_000);
        complete(state, result, 41, output);
        assertSame(output, state.output());
        assertEquals(output, result.body(state));
        assertEquals(ViewState.Content.QUERY, state.content());
        assertEquals(location, state.query());
        assertSame(overview, state.governmentOverview());
        assertFalse(state.automaticRefresh());
        GovernmentOverviewQueryState.returnToOverview(state);
        assertEquals(ViewState.Content.TYPED, state.content());
        assertNull(state.explicit());
        assertEquals(location, state.query());
        assertSame(overview, state.governmentOverview());
        assertSame(output, state.output());
        assertTrue(state.automaticRefresh());
    }

    @Test
    void rerunningTheSameQueryRetainsPreviousOutputWhileWaiting() {
        ViewState state = state();
        var result = new GovernmentOverviewQueryState();
        request(state, result, 10);
        complete(state, result, 10, "First response");
        long revision = state.revision();
        request(state, result, 11);
        assertEquals(revision, state.revision());
        assertEquals("First response", result.body(state));
        assertEquals(GovernmentOverviewQueryState.Phase.LOADING, result.phase(state));
        assertEquals(QUERY, state.explicit());
        complete(state, result, 11, "Second response");
        assertEquals("Second response", result.body(state));
        assertEquals(GovernmentOverviewQueryState.Phase.READY, result.phase(state));
    }

    @Test
    void failuresAreVisibleButDoNotEraseRetainedOutputOrPoisonASuccessfulRetry() {
        ViewState state = state();
        var result = new GovernmentOverviewQueryState();
        request(state, result, 20);
        complete(state, result, 20, "Retained result");
        request(state, result, 21);
        state.failure(UiText.tr("gui.statecraft.query.timeout", "The query timed out."));
        result.observe(state);
        assertEquals(GovernmentOverviewQueryState.Phase.FAILED, result.phase(state));
        assertEquals("The query timed out.", result.body(state));
        assertEquals("Retained result", state.output());
        request(state, result, 22);
        complete(state, result, 22, "Fresh result");
        assertEquals(GovernmentOverviewQueryState.Phase.READY, result.phase(state));
        assertEquals("Fresh result", result.body(state));
        state.feedback(ActionOutcome.REJECTED, UiText.literal("An unrelated operation failed."));
        state.invalidate();
        result.observe(state);
        assertEquals(GovernmentOverviewQueryState.Phase.READY, result.phase(state));
        assertEquals("Fresh result", result.body(state));
    }

    @Test
    void backInvalidatesLateQueryRepliesAndRestoresTheGovernmentRefreshRoute() {
        ViewState state = state();
        var result = new GovernmentOverviewQueryState();
        request(state, result, 30);
        long queryRevision = state.revision();
        GovernmentOverviewQueryState.returnToOverview(state);
        assertFalse(state.accept(30, queryRevision));
        state.cancel(30);
        result.observe(state);
        assertEquals(ViewState.Content.TYPED, state.content());
        assertEquals("statecraft:detail", state.query().page());
        assertEquals(GOVERNMENT, state.query().entity().id());
        assertNull(state.explicit());
        assertEquals(-1, state.pending());
        assertTrue(state.automaticRefresh());
    }

    private static ViewState state() {
        ViewState state = new ViewState(UiQuery.detail(new EntityRef("statecraft", EntityRef.Kind.GOVERNMENT, GOVERNMENT)));
        state.governmentOverview(new GovernmentOverview(GOVERNMENT, Kind.NATION, "Realm", "", "", null, null,
                UiText.literal("$100.00"), PersonalDashboard.Page.EMPTY, PersonalDashboard.Page.EMPTY, List.of(), null));
        return state;
    }

    private static void request(ViewState state, GovernmentOverviewQueryState result, int id) {
        state.explicit(QUERY);
        state.request(id);
        result.observe(state);
    }

    private static void complete(ViewState state, GovernmentOverviewQueryState result, int id, String output) {
        assertTrue(state.accept(id, state.revision()));
        state.queryResult(true, output);
        result.observe(state);
    }
}
