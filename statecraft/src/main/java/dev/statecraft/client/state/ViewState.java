package dev.statecraft.client.state;

import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import java.util.UUID;

public final class ViewState {
    public enum Content { TYPED, QUERY, RAW }
    private final UUID id = UUID.randomUUID();
    private UiQuery query;
    private UiView view;
    private Content content = Content.TYPED;
    private ActionSelection explicit;
    private String output = "";
    private UiText banner = UiText.EMPTY;
    private ActionOutcome outcome = ActionOutcome.COMPLETED;
    private long revision;
    private boolean stale = true;
    private int pending = -1;
    private int scroll;
    private EntityRef selected = EntityRef.NONE;
    private String selectedText = "";
    private String advanced = "";
    private EditSelection searchSelection = EditSelection.end("");
    private EditSelection advancedSelection = EditSelection.end("");
    private SearchState actionSearch = new SearchState();

    public ViewState(UiQuery query) { this.query = query; }
    public UUID id() { return id; }
    public UiQuery query() { return query; }
    public UiView view() { return view; }
    public Content content() { return content; }
    public ActionSelection explicit() { return explicit; }
    public String output() { return output; }
    public UiText banner() { return banner; }
    public ActionOutcome outcome() { return outcome; }
    public long revision() { return revision; }
    public boolean stale() { return stale; }
    public UiText placeholder() {
        return pending < 0 && !banner.fallback().isEmpty() ? banner
                : UiText.tr("gui.statecraft.loading", "Loading from server...");
    }
    public int pending() { return pending; }
    public int scroll() { return scroll; }
    public EntityRef selected() { return selected; }
    public String selectedText() { return selectedText; }
    public String advanced() { return advanced; }
    public EditSelection searchSelection() { return searchSelection; }
    public EditSelection advancedSelection() { return advancedSelection; }
    public SearchState actionSearch() { return actionSearch; }
    public void scroll(int value) { scroll = Math.max(0, value); }
    public void select(EntityRef entity, String text) { selected = entity; selectedText = text; }
    public void advanced(String value) { advanced = value; }
    public void searchSelection(EditSelection value) { searchSelection = value; }
    public void advancedSelection(EditSelection value) { advancedSelection = value; }

    public void query(UiQuery replacement) {
        if (replacement.equals(query) && content == Content.TYPED) return;
        query = replacement;
        content = Content.TYPED;
        explicit = null;
        scroll = 0;
        selected = EntityRef.NONE;
        selectedText = "";
        view = null;
        revision++;
        stale = true;
    }

    public void request(int request) { pending = request; }
    public void cancel(int request) {
        if (pending == request) {
            pending = -1;
            stale = true;
        }
    }
    public boolean accept(int request, long requestedRevision) {
        if (pending != request || revision != requestedRevision) return false;
        pending = -1;
        return true;
    }
    public void view(UiView replacement) {
        view = replacement;
        content = Content.TYPED;
        stale = false;
        if (replacement.offset() != query.offset()) {
            query = new UiQuery(query.page(), query.entity(), query.search(), replacement.offset());
        }
    }
    public void explicit(ActionSelection selection) {
        if (!selection.equals(explicit) || content != Content.QUERY) {
            explicit = selection;
            content = Content.QUERY;
            scroll = 0;
            selected = EntityRef.NONE;
            selectedText = "";
            revision++;
        }
        stale = true;
    }
    public void queryResult(boolean success, String text) {
        output = text;
        stale = false;
    }
    public void invalidate() {
        pending = -1;
        revision++;
        stale = content != Content.RAW;
    }
    public void feedback(ActionOutcome status, UiText text) { outcome = status; banner = text; }
    public void failure(UiText text) {
        pending = -1;
        stale = false;
        feedback(ActionOutcome.REJECTED, text);
    }
    public void mutationResult(ActionIntent intent, ActionSelection selection, ActionOutcome status, String text) {
        feedback(status, UiText.literal(text));
        if (intent == ActionIntent.RAW) {
            content = Content.RAW;
            explicit = selection;
            output = text;
            revision++;
            stale = false;
        } else if (status == ActionOutcome.COMPLETED) {
            stale = content != Content.RAW;
        }
    }
    public boolean automaticRefresh() { return stale && pending < 0 && content != Content.RAW; }

    public ViewState copy() {
        ViewState copy = new ViewState(query);
        copy.view = view;
        copy.content = content;
        copy.explicit = explicit;
        copy.output = output;
        copy.banner = banner;
        copy.outcome = outcome;
        copy.scroll = scroll;
        copy.selected = selected;
        copy.selectedText = selectedText;
        copy.advanced = advanced;
        copy.searchSelection = searchSelection;
        copy.advancedSelection = advancedSelection;
        copy.actionSearch = actionSearch.copy();
        copy.stale = stale;
        return copy;
    }
}
