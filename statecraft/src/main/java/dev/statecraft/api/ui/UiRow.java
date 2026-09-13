package dev.statecraft.api.ui;

import java.util.Objects;

public record UiRow(UiText title, UiText detail, EntityRef entity) {
    public UiRow {
        Objects.requireNonNull(title);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(entity);
        if (title.fallback().length() > 256 || detail.fallback().length() > 2048) {
            throw new IllegalArgumentException("Invalid UI row.");
        }
    }

    public UiRow(String title, String detail, EntityRef entity) {
        this(UiText.literal(title), UiText.literal(detail), entity);
    }
}
