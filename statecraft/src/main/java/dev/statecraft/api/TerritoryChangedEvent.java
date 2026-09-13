package dev.statecraft.api;

import net.minecraftforge.eventbus.api.Event;

public final class TerritoryChangedEvent extends Event {
    private final TerritorySnapshot snapshot;

    public TerritoryChangedEvent(TerritorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public TerritorySnapshot snapshot() {
        return snapshot;
    }
}
