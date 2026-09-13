package dev.statecraft.client.state;

import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiRow;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientNavigationTest {
    private static final EntityRef LISTING = new EntityRef("economy", EntityRef.Kind.MARKET_LISTING, "listing-7");
    private static final UiQuery FILTER = new UiQuery("economy:market", EntityRef.NONE, "stone", 20);
    private static UiView view() {
        return new UiView(UiText.literal("Market"), UiText.literal("Available listings"),
                List.of(new UiRow("Stone", "Two stacks", LISTING)), List.of(), 20, true, UiText.EMPTY);
    }

    @Test
    void backRestoresTypedEntitySearchPaginationScrollSelectionAndEditorStateWithoutScreenInstances() {
        NavigationState navigation = new NavigationState();
        navigation.sections("ECONOMY");
        navigation.current().offset(6);
        ViewState market = navigation.open(UiQuery.page("economy:market"));
        market.query(FILTER);
        market.view(view());
        market.scroll(3);
        market.select(LISTING, "Stone\nTwo stacks\nlisting-7");
        market.advanced("market show listing-7");
        market.advancedSelection(new EditSelection(6, 11, true));
        market.actionSearch().text("buy");
        market.actionSearch().selection(new EditSelection(1, 2, true));
        ViewState detail = navigation.open(UiQuery.detail(LISTING));
        assertEquals(LISTING, detail.query().entity());
        assertTrue(navigation.back());
        assertSame(market, navigation.current().view());
        assertEquals(FILTER, market.query());
        assertEquals(3, market.scroll());
        assertEquals(LISTING, market.selected());
        assertEquals("market show listing-7", market.advanced());
        assertEquals(new EditSelection(6, 11, true), market.advancedSelection());
        assertEquals("buy", market.actionSearch().text());
        assertTrue(navigation.back());
        assertEquals("ECONOMY", navigation.current().category());
        assertEquals(6, navigation.current().offset());
        assertEquals(FILTER, navigation.open(UiQuery.page("economy:market")).query());
    }

    @Test
    void mutationFeedbackDoesNotReplaceTypedSectionDataAndKeepsTheExactRefreshQuery() {
        ViewState state = new ViewState(FILTER);
        UiView original = view();
        state.view(original);
        state.select(LISTING, "Stone");
        state.scroll(7);
        state.mutationResult(ActionIntent.MUTATION, null, ActionOutcome.COMPLETED, "Purchased; delivery pending.");
        assertSame(original, state.view());
        assertEquals(FILTER, state.query());
        assertEquals(LISTING, state.selected());
        assertEquals(7, state.scroll());
        assertEquals("Purchased; delivery pending.", state.banner().fallback());
        assertTrue(state.automaticRefresh());
    }

    @Test
    void explicitQueryResultsSurviveNavigationAndMutationRefreshRepeatsThatQueryInsteadOfTheDefault() {
        ViewState state = new ViewState(FILTER);
        ActionSelection query = ActionSelection.form("economy:market", "market search <term>", Map.of("term", "diamonds"));
        state.explicit(query);
        state.queryResult(true, "Intentional diamond results");
        state.mutationResult(ActionIntent.MUTATION, null, ActionOutcome.COMPLETED, "Unrelated mutation complete.");
        assertEquals(ViewState.Content.QUERY, state.content());
        assertEquals(query, state.explicit());
        assertEquals("Intentional diamond results", state.output());
        assertTrue(state.automaticRefresh());
        var copy = state.copy();
        assertEquals(query, copy.explicit());
        assertEquals(state.output(), copy.output());
    }

    @Test
    void rawResultsRemainInspectableAndNeverBecomeAnAutomaticReplayTarget() {
        ViewState state = new ViewState(FILTER);
        ActionSelection raw = ActionSelection.raw("economy:market", "market collect");
        state.mutationResult(ActionIntent.RAW, raw, ActionOutcome.COMPLETED, "Collected items.\nReceipt #7");
        assertEquals(ViewState.Content.RAW, state.content());
        assertEquals(raw, state.explicit());
        assertFalse(state.automaticRefresh());
        state.mutationResult(ActionIntent.MUTATION, null, ActionOutcome.COMPLETED, "Another operation.");
        assertEquals("Collected items.\nReceipt #7", state.output());
        assertFalse(state.automaticRefresh());
        state.invalidate();
        assertEquals(ViewState.Content.RAW, state.content());
        assertFalse(state.automaticRefresh());
    }

    @Test
    void aLateViewResponseCannotOverwriteANewerSearchOrAQueryResult() {
        ViewState state = new ViewState(FILTER);
        long oldRevision = state.revision();
        state.request(1);
        state.query(new UiQuery("economy:market", EntityRef.NONE, "new search", 0));
        state.request(2);
        assertFalse(state.accept(1, oldRevision));
        assertEquals(2, state.pending());
        assertTrue(state.accept(2, state.revision()));
        state.view(UiView.text("New", "Correct result"));
        assertFalse(state.accept(1, oldRevision));
        assertEquals("Correct result", state.view().body().fallback());
    }

    @Test
    void querySuccessDoesNotRelabelAPreviousRejectedMutationAsCompleted() {
        ViewState state = new ViewState(FILTER);
        state.feedback(ActionOutcome.REJECTED, UiText.literal("Payment not executed."));
        state.explicit(ActionSelection.form("economy:market", "market list", Map.of()));
        state.queryResult(true, "Current listings");
        assertEquals(ActionOutcome.REJECTED, state.outcome());
        assertEquals("Payment not executed.", state.banner().fallback());
        assertEquals("Current listings", state.output());
    }

    @Test
    void initialViewFailureDisplaysItsErrorInsteadOfAnEndlessLoadingPlaceholder() {
        ViewState state = new ViewState(FILTER);
        state.request(3);
        assertEquals("gui.statecraft.loading", state.placeholder().key());
        UiText unavailable = UiText.literal("This operator view is not available to you.");
        state.failure(unavailable);
        assertEquals(unavailable, state.placeholder());
        assertFalse(state.automaticRefresh());
        state.request(4);
        assertEquals("gui.statecraft.loading", state.placeholder().key());
    }

    @Test
    void navigationIsBoundedAndDisconnectInvalidatesInvisibleViewWatchers() {
        NavigationState navigation = new NavigationState();
        ViewState first = navigation.open(FILTER);
        first.request(91);
        navigation.open(UiQuery.detail(LISTING)).request(92);
        navigation.invalidate();
        assertEquals(-1, first.pending());
        assertTrue(first.automaticRefresh());
        assertEquals(-1, navigation.current().view().pending());
        for (int i = 0; i < 90; i++) navigation.open(UiQuery.page("statecraft:section-" + i));
        assertEquals(NavigationState.MAX_DEPTH, navigation.depth());
        int count = 0;
        while (navigation.back()) count++;
        assertEquals(NavigationState.MAX_DEPTH, count);
    }
}
