package com.statecraft.integration;

import java.util.UUID;

/**
 * Interface for minimap mod integrations.
 * Implementations provide territory overlays, claim markers, and border
 * rendering for supported minimap mods (JourneyMap, Xaero's, etc.).
 *
 * Implementations are registered via {@link IntegrationRegistry#registerMinimapIntegration(MinimapIntegration)}
 * and called from the client-side chunk border cache update cycle.
 */
public interface MinimapIntegration {

    /**
     * @return Display name of the minimap mod this integration supports
     */
    String getMinimapName();

    /**
     * Check if the target minimap mod is loaded and available.
     */
    boolean isAvailable();

    /**
     * Called when chunk claim data is refreshed on the client.
     * The implementation should update any overlays/markers for the
     * specified area.
     *
     * @param centerX  center chunk X of the update area
     * @param centerZ  center chunk Z of the update area
     * @param radius   radius in chunks of the update area
     */
    void onChunkDataUpdated(int centerX, int centerZ, int radius);

    /**
     * Called when the player changes dimension. Implementations should
     * clear all overlays and re-request data for the new dimension.
     */
    void onDimensionChange();

    /**
     * Called when the mod is shutting down. Clean up all overlays.
     */
    void cleanup();
}

