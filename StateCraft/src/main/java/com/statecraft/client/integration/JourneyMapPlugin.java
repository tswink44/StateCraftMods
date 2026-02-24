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

    // Store the API reference in case onApiReady is called before MinimapIntegrationLoader registers
    private static Object pendingApi = null;

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
     * Called by JourneyMapPluginImpl when JourneyMap initializes our plugin.
     * This may be called BEFORE MinimapIntegrationLoader has registered the integration,
     * so we store the API reference for later retrieval.
     */
    public static void onApiReady(Object api) {
        StateCraft.LOGGER.info("JourneyMap plugin initialized for StateCraft");
        pendingApi = api;

        // Try to connect immediately if the integration is already registered
        if (tryConnectApi(api)) {
            pendingApi = null;
        } else {
            StateCraft.LOGGER.info("JourneyMap API stored — will connect when integration is registered");
        }
    }

    /**
     * Returns the pending API if onApiReady was called before the integration was registered.
     * Called by MinimapIntegrationLoader after registering JourneyMapIntegration.
     */
    public static Object consumePendingApi() {
        Object api = pendingApi;
        pendingApi = null;
        return api;
    }

    private static boolean tryConnectApi(Object api) {
        for (MinimapIntegration integration : IntegrationRegistry.getMinimapIntegrations()) {
            if (integration instanceof JourneyMapIntegration jmIntegration) {
                jmIntegration.setClientApi(api);
                StateCraft.LOGGER.info("JourneyMap API connected to StateCraft integration");
                return true;
            }
        }
        return false;
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


