package dev.statecraft.api.ui;

import java.util.List;
import java.util.Objects;

public record UiText(String key, String fallback, List<String> arguments) {
    public static final UiText EMPTY = literal("");

    public UiText {
        Objects.requireNonNull(key);
        Objects.requireNonNull(fallback);
        arguments = List.copyOf(arguments);
        if (!key.matches("[a-zA-Z0-9_.-]{0,192}") || fallback.length() > 30_000
                || arguments.size() > 16 || arguments.stream().anyMatch(value -> value.length() > 2048)
                || key.length() + fallback.length() + arguments.stream().mapToInt(String::length).sum() > 30_000) {
            throw new IllegalArgumentException("Invalid UI text.");
        }
    }

    public static UiText literal(String text) { return new UiText("", text, List.of()); }

    public static UiText tr(String key, String renderedFallback, String... arguments) {
        return new UiText(key, renderedFallback, List.of(arguments));
    }

    public int characters() {
        return key.length() + fallback.length() + arguments.stream().mapToInt(String::length).sum();
    }
}
