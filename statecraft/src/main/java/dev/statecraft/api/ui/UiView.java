package dev.statecraft.api.ui;

import java.util.List;
import java.util.Objects;

public record UiView(UiText title, UiText body, List<UiRow> rows, List<UiAction> actions,
                     int offset, boolean more, UiText emptyHint) {
    public static final int PAGE_SIZE = 20;
    public static final int MAX_ROWS = 60;
    public static final int MAX_ACTIONS = 32;

    public UiView {
        Objects.requireNonNull(title);
        Objects.requireNonNull(body);
        Objects.requireNonNull(emptyHint);
        rows = List.copyOf(rows);
        actions = List.copyOf(actions);
        if (title.fallback().length() > 128 || body.fallback().length() > 20_000
                || emptyHint.fallback().length() > 1024 || rows.size() > MAX_ROWS
                || actions.size() > MAX_ACTIONS || offset < 0 || offset > 100_000) {
            throw new IllegalArgumentException("Invalid UI view.");
        }
        long characters = title.characters() + body.characters() + emptyHint.characters();
        for (UiRow row : rows) {
            characters += row.title().characters() + row.detail().characters() + row.entity().id().length() + 64;
        }
        for (UiAction action : actions) {
            characters += action.label().characters() + action.disabledReason().characters()
                    + action.page().length() + action.template().length();
            for (var value : action.values().entrySet()) characters += value.getKey().length() + value.getValue().length();
        }
        if (characters > 80_000) throw new IllegalArgumentException("UI view exceeds the aggregate text budget.");
    }

    public static UiView text(String title, String body) {
        return new UiView(UiText.literal(title), UiText.literal(body), List.of(), List.of(), 0, false, UiText.EMPTY);
    }
}
