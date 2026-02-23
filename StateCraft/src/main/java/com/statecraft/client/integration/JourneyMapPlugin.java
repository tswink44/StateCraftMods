package com.statecraft.client.integration;

import com.statecraft.StateCraft;
import com.statecraft.integration.IntegrationRegistry;
import com.statecraft.integration.MinimapIntegration;

/**
 * JourneyMap plugin registration helper.
 *
 * JourneyMap uses annotation scanning to find plugins at runtime.
 * The actual plugin implementation is in a separate inner class to prevent
 * class loading issues when JourneyMap is not installed.
 *
 * This class provides a safe entry point that checks for JourneyMap before
 * attempting to register the plugin.
 */
public class JourneyMapPlugin {

    private static boolean registered = false;

    /**
     * Attempt to register with JourneyMap API.
     * Safe to call even if JourneyMap is not installed.
     */
    public static void tryRegister() {
        if (registered) return;
        registered = true;

        try {
            // Check if JourneyMap is loaded
            Class.forName("journeymap.client.api.IClientAPI");
            StateCraft.LOGGER.info("JourneyMap API classes found - plugin will be registered via annotation scanning");
        } catch (ClassNotFoundException e) {
            StateCraft.LOGGER.debug("JourneyMap not installed - skipping plugin registration");
        }
    }

    /**
     * Called by JourneyMapPluginImpl when JourneyMap initializes our plugin
     */
    public static void onApiReady(Object api) {
        StateCraft.LOGGER.info("JourneyMap plugin initialized for StateCraft");

        // Find and configure the JourneyMapIntegration instance
        for (MinimapIntegration integration : IntegrationRegistry.getMinimapIntegrations()) {
            if (integration instanceof JourneyMapIntegration jmIntegration) {
                jmIntegration.setClientApi(api);
                StateCraft.LOGGER.info("JourneyMap API connected to StateCraft integration");
                break;
            }
        }
    }

    /**
     * Called when JourneyMap mapping stops
     */
    public static void onMappingStopped() {
        for (MinimapIntegration integration : IntegrationRegistry.getMinimapIntegrations()) {
            if (integration instanceof JourneyMapIntegration jmIntegration) {
                jmIntegration.cleanup();
                break;
            }
        }
    }
}


