package dev.statecraft.api.ui;

import dev.statecraft.api.Actor;
import java.util.Objects;

public record UiContext(Actor actor, UiQuery query) {
    public UiContext {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(query);
    }
}
