package com.statecraft.client.integration;

import com.statecraft.StateCraft;
import com.statecraft.integration.IntegrationRegistry;

/**
 * Detects installed minimap mods and registers appropriate integrations.
 * Called from the client-side setup phase.
 *
 * Supported minimap mods:
 * - JourneyMap (soft dependency via reflection)
 * - Xaero's Minimap / World Map (soft dependency via reflection)
 *
 * All integrations use the {@link com.statecraft.integration.MinimapIntegration} interface
 * and register via {@link IntegrationRegistry#registerMinimapIntegration}.
 *
 * Third-party mods can also register their own implementations by calling
 * {@code IntegrationRegistry.registerMinimapIntegration(myIntegration)} during mod setup.
 */
public class MinimapIntegrationLoader {

    private static boolean loaded = false;

    /**
     * Detect and register all available minimap integrations.
     * Safe to call multiple times (only runs once).
     */
    public static void load() {
        if (loaded) return;
        loaded = true;

        StateCraft.LOGGER.info("Scanning for minimap mod integrations...");

        // Try JourneyMap
        try {
            JourneyMapIntegration journeyMap = new JourneyMapIntegration();
            if (journeyMap.isAvailable()) {
                IntegrationRegistry.registerMinimapIntegration(journeyMap);
            }
        } catch (Exception e) {
            StateCraft.LOGGER.debug("JourneyMap integration skipped: {}", e.getMessage());
        }

        // Try Xaero's Minimap
        try {
            XaerosMinimapIntegration xaeros = new XaerosMinimapIntegration();
            if (xaeros.isAvailable()) {
                IntegrationRegistry.registerMinimapIntegration(xaeros);
            }
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Xaero's Minimap integration skipped: {}", e.getMessage());
        }

        if (IntegrationRegistry.hasMinimapIntegration()) {
            StateCraft.LOGGER.info("Minimap integrations loaded: {}",
                IntegrationRegistry.getMinimapIntegrations().size());
        } else {
            StateCraft.LOGGER.info("No supported minimap mods detected. " +
                "Install JourneyMap or Xaero's Minimap for territory overlay support.");
        }
    }
}

