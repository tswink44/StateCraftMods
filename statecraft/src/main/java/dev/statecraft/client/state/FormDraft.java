package dev.statecraft.client.state;

import dev.statecraft.api.form.FormState;
import dev.statecraft.api.form.FormValidation;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.UiText;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FormDraft {
    private final String page;
    private final String template;
    private final Map<String, String> seed;
    private final FormState state = new FormState();
    private final Map<String, EditSelection> selections = new LinkedHashMap<>();
    private final Map<String, SearchState> searches = new LinkedHashMap<>();
    private Map<String, UiText> serverErrors = Map.of();
    private OperationRef operation = OperationRef.NONE;
    private int firstField;
    private String focused = "";

    public FormDraft(String page, String template, Map<String, String> seed) {
        this.page = page;
        this.template = template;
        this.seed = Map.copyOf(seed);
        seed.forEach(state::change);
    }
    public String page() { return page; }
    public String template() { return template; }
    public Map<String, String> seed() { return seed; }
    public FormState state() { return state; }
    public OperationRef operation() { return operation; }
    public void operation(OperationRef value) { operation = value; }
    public int firstField() { return firstField; }
    public void firstField(int value) { firstField = Math.max(0, value); }
    public String focused() { return focused; }
    public void focused(String value) { focused = value; }
    public int layoutAnchor() {
        for (int i = 0; i < state.schema().fields().size(); i++) {
            if (state.schema().fields().get(i).key().equals(focused)) return i;
        }
        return firstField;
    }
    public void selection(String key, EditSelection value) { selections.put(key, value); }
    public EditSelection selection(String key) { return selections.getOrDefault(key, EditSelection.end(state.value(key))); }
    public SearchState search(String key) { return searches.computeIfAbsent(key, ignored -> new SearchState()); }
    public ActionSelection capture() { return ActionSelection.form(page, template, state.values()); }
    public Map<String, UiText> errors() {
        var errors = new LinkedHashMap<>(FormValidation.errors(state.schema(), state.values()));
        errors.putAll(serverErrors);
        return Map.copyOf(errors);
    }
    public void serverErrors(Map<String, UiText> errors) { serverErrors = Map.copyOf(errors); }
    public void edited(String key) {
        var errors = new LinkedHashMap<>(serverErrors);
        errors.remove(key);
        serverErrors = Map.copyOf(errors);
    }
}
