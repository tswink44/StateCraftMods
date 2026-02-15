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

    /**
     * Get a player's balance from the economy system
     */
    public static double getPlayerBalance(UUID playerId) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.getPlayerBalance(playerId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error getting player balance: {}", e.getMessage());
            }
        }
        return 0;
    }

    /**
     * Withdraw funds from a player's account
     * @return true if successful
     */
    public static boolean withdrawFromPlayer(UUID playerId, double amount, String description) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.withdrawFromPlayer(playerId, amount, description);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error withdrawing from player: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Format a currency amount for display
     */
    public static String formatCurrency(double amount) {
        if (economyIntegration != null) {
            try {
                String result = economyIntegration.formatCurrency(amount);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return String.format("$%.2f", amount);
    }
}

