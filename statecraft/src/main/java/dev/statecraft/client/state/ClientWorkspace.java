package dev.statecraft.client.state;

import dev.statecraft.api.ui.ActionSelection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientWorkspace {
    public static final int MAX_DRAFTS = 24;
    private final UiScope scope;
    private final NavigationState navigation = new NavigationState();
    private final PendingOperations operations;
    private final Map<ActionSelection, FormDraft> drafts = new LinkedHashMap<>();

    public ClientWorkspace(UiScope scope) {
        this.scope = scope;
        operations = new PendingOperations(scope);
    }
    public UiScope scope() { return scope; }
    public NavigationState navigation() { return navigation; }
    public PendingOperations operations() { return operations; }

    public FormDraft draft(String page, String template, Map<String, String> seed) {
        ActionSelection key = ActionSelection.form(page, template, seed);
        var blocking = operations.blocking(key);
        if (blocking != null) {
            FormDraft captured = drafts.values().stream().filter(value -> value.operation().equals(blocking.operation()))
                    .findFirst().orElse(null);
            if (captured != null) return captured;
            seed = blocking.selection().values();
        }
        FormDraft draft = drafts.remove(key);
        if (draft == null) draft = new FormDraft(page, template, seed);
        if (blocking != null) draft.operation(blocking.operation());
        drafts.put(key, draft);
        while (drafts.size() > MAX_DRAFTS) {
            ActionSelection oldest = drafts.entrySet().stream()
                    .filter(entry -> !operations.blocked(entry.getValue().operation()))
                    .map(Map.Entry::getKey).filter(entry -> !entry.equals(key)).findFirst().orElse(null);
            if (oldest == null) break;
            drafts.remove(oldest);
        }
        return draft;
    }
}
