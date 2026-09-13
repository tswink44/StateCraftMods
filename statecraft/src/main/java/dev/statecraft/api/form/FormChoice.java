package dev.statecraft.api.form;

import java.util.Objects;

public record FormChoice(String value, String label, String detail) {
    public FormChoice {
        Objects.requireNonNull(value);
        Objects.requireNonNull(label);
        Objects.requireNonNull(detail);
        if (value.isBlank() || value.length() > 256 || label.isBlank() || label.length() > 128 || detail.length() > 256) {
            throw new IllegalArgumentException("Invalid form choice.");
        }
    }

    public FormChoice(String value, String label) {
        this(value, label, "");
    }
}
