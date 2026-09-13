package dev.statecraft.api.ui;

import dev.statecraft.api.Actor;

public interface UiProvider {
    UiView view(UiContext context);
    UiView dashboard(Actor actor, String search, int offset);
}
