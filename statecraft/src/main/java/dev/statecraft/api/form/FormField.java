package dev.statecraft.api.form;

import java.util.List;
import java.util.Objects;

public record FormField(String key, String label, Kind kind, String value, String selectedLabel,
                        String hint, List<String> dependencies, boolean allowCustom,
                        List<FormChoice> choices, int offset, boolean more) {
    public enum Kind { TEXT, MULTILINE, CHOICE }

    public FormField {
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(value);
        Objects.requireNonNull(selectedLabel);
        Objects.requireNonNull(hint);
        dependencies = List.copyOf(dependencies);
        choices = List.copyOf(choices);
        if (key.isBlank() || key.length() > 64 || label.length() > 128 || value.length() > 2048
                || selectedLabel.length() > 128 || hint.length() > 384
                || dependencies.size() > FormSchema.MAX_FIELDS || choices.size() > FormSchema.PAGE_SIZE
                || dependencies.stream().anyMatch(d -> d.length() > 64) || offset < 0) {
            throw new IllegalArgumentException("Invalid form field.");
        }
    }
}
