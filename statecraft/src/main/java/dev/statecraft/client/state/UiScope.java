package dev.statecraft.client.state;

import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.UiText;
import java.util.Objects;
import java.util.UUID;

public record UiScope(UUID world, UUID player) {
    public UiScope {
        Objects.requireNonNull(world);
        Objects.requireNonNull(player);
    }

    public UiText referenceText(OperationRef operation) {
        if (!operation.present() || !world.equals(operation.world())) {
            throw new IllegalArgumentException("Operation does not belong to this world.");
        }
        return UiText.tr("gui.statecraft.operations.reference_details",
                "World: " + world + "\nPlayer: " + player + "\nOperation: " + operation.id(),
                world.toString(), player.toString(), operation.id().toString());
    }
}
