package dev.statecraft.api.ui;

import java.util.Map;
import java.util.Objects;

public record UiAction(String page, String template, UiText label, Map<String, String> values,
                       boolean enabled, UiText disabledReason) {
    public UiAction {
        Objects.requireNonNull(label);
        Objects.requireNonNull(disabledReason);
        values = Map.copyOf(values);
        ActionSelection.form(page, template, values);
        if (label.fallback().isBlank() || label.fallback().length() > 128
                || disabledReason.fallback().length() > 512 || !enabled && disabledReason.fallback().isBlank()) {
            throw new IllegalArgumentException("Invalid contextual action.");
        }
    }

    public UiAction(String page, String template, UiText label, Map<String, String> values) {
        this(page, template, label, values, true, UiText.EMPTY);
    }

    public UiAction disabled(UiText reason) {
        return new UiAction(page, template, label, values, false, reason);
    }

    public ActionSelection selection() { return ActionSelection.form(page, template, values); }
}
