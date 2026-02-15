package com.statecraft.integration;

import com.statecraft.StateCraft;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Registry for economy integration
 * Economy mods can register their implementation to receive callbacks
 */
public class IntegrationRegistry {

    private static EconomyIntegration economyIntegration = null;

    /**
     * Register an economy integration handler
     */
    public static void registerEconomyIntegration(EconomyIntegration integration) {
        economyIntegration = integration;
        StateCraft.LOGGER.info("Economy integration registered: {}", integration.getClass().getName());
    }

    /**
     * Unregister the economy integration handler
     */
    public static void unregisterEconomyIntegration() {
        economyIntegration = null;
    }

    /**
     * Get the registered economy integration, if any
     */
    @Nullable
    public static EconomyIntegration getEconomyIntegration() {
        return economyIntegration;
    }

    /**
     * Check if economy integration is available
     */
    public static boolean hasEconomyIntegration() {
        return economyIntegration != null;
    }

    // ==================== Convenience Methods ====================

    public static void notifyNationCreated(UUID nationId, String nationName) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onNationCreated(nationId, nationName);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of nation creation: {}", e.getMessage());
            }
        }
    }

    public static void notifyStateCreated(UUID stateId, String stateName, UUID nationId) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onStateCreated(stateId, stateName, nationId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of state creation: {}", e.getMessage());
            }
        }
    }

    public static void notifyCityCreated(UUID cityId, String cityName, UUID stateId) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onCityCreated(cityId, cityName, stateId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of city creation: {}", e.getMessage());
            }
        }
    }

    public static void notifyNationDisbanded(UUID nationId) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onNationDisbanded(nationId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of nation disbandment: {}", e.getMessage());
            }
        }
    }

    public static void notifyStateDisbanded(UUID stateId) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onStateDisbanded(stateId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of state disbandment: {}", e.getMessage());
            }
        }
    }

    public static void notifyCityDisbanded(UUID cityId) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onCityDisbanded(cityId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of city disbandment: {}", e.getMessage());
            }
        }
    }
}

