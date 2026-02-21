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
     * Deposit funds to a player's account
     * @return true if successful
     */
    public static boolean depositToPlayer(UUID playerId, double amount, String description) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.depositToPlayer(playerId, amount, description);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error depositing to player: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Convenience overload for depositToPlayer without description
     */
    public static boolean depositToPlayer(UUID playerId, double amount) {
        return depositToPlayer(playerId, amount, "Deposit");
    }

    /**
     * Get a nation's treasury balance
     */
    public static double getNationBalance(String nationName) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.getNationBalance(nationName);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error getting nation balance: {}", e.getMessage());
            }
        }
        return 0;
    }

    /**
     * Withdraw funds from a nation's treasury
     * @return true if successful
     */
    public static boolean withdrawFromNation(String nationName, double amount, String description) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.withdrawFromNation(nationName, amount, description);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error withdrawing from nation: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Convenience overload for withdrawFromNation without description
     */
    public static boolean withdrawFromNation(String nationName, double amount) {
        return withdrawFromNation(nationName, amount, "Withdrawal");
    }

    /**
     * Deposit funds to a nation's treasury
     * @return true if successful
     */
    public static boolean depositToNation(String nationName, double amount, String description) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.depositToNation(nationName, amount, description);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error depositing to nation: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * Convenience overload for depositToNation without description
     */
    public static boolean depositToNation(String nationName, double amount) {
        return depositToNation(nationName, amount, "Deposit");
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

    /**
     * Get the improvement score for a chunk
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension (e.g., "minecraft:overworld")
     * @return The improvement score, or 0 if not available
     */
    public static int getChunkImprovementScore(int chunkX, int chunkZ, String dimension) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.getChunkImprovementScore(chunkX, chunkZ, dimension);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error getting chunk improvement score: {}", e.getMessage());
            }
        }
        return 0;
    }

    /**
     * Get the total valuation for a chunk (including all multipliers)
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension (e.g., "minecraft:overworld")
     * @return The total chunk value, or 0 if not available
     */
    public static double getChunkTotalValue(int chunkX, int chunkZ, String dimension) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.getChunkTotalValue(chunkX, chunkZ, dimension);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error getting chunk total value: {}", e.getMessage());
            }
        }
        return 0;
    }

    // ==================== Company Economy ====================

    public static void notifyCompanyCreated(UUID companyId) {
        if (economyIntegration != null) {
            try {
                economyIntegration.onCompanyCreated(companyId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of company creation: {}", e.getMessage());
            }
        }
    }

    public static boolean notifyCompanyDissolving(UUID companyId, UUID founderId) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.onCompanyDissolving(companyId, founderId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error notifying economy integration of company dissolution: {}", e.getMessage());
            }
        }
        return true;
    }

    public static double getCompanyBalance(UUID companyId) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.getCompanyBalance(companyId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error getting company balance: {}", e.getMessage());
            }
        }
        return 0;
    }

    public static boolean isDividendsEnabled(UUID companyId) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.isDividendsEnabled(companyId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error checking dividends enabled: {}", e.getMessage());
            }
        }
        return false;
    }

    public static double getDividendRate(UUID companyId) {
        if (economyIntegration != null) {
            try {
                return economyIntegration.getDividendRate(companyId);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error getting dividend rate: {}", e.getMessage());
            }
        }
        return 0;
    }

    public static void setDividendsEnabled(UUID companyId, boolean enabled) {
        if (economyIntegration != null) {
            try {
                economyIntegration.setDividendsEnabled(companyId, enabled);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error setting dividends enabled: {}", e.getMessage());
            }
        }
    }

    public static void setDividendRate(UUID companyId, double rate) {
        if (economyIntegration != null) {
            try {
                economyIntegration.setDividendRate(companyId, rate);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error setting dividend rate: {}", e.getMessage());
            }
        }
    }

    public static void setDividendPeriodTicks(UUID companyId, long ticks) {
        if (economyIntegration != null) {
            try {
                economyIntegration.setDividendPeriodTicks(companyId, ticks);
            } catch (Exception e) {
                StateCraft.LOGGER.warn("Error setting dividend period: {}", e.getMessage());
            }
        }
    }
}

