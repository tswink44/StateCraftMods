package com.statecraft.integration;

import java.util.UUID;

/**
 * Interface for economy integration
 * Allows economy mods to receive callbacks when nations/states/cities are created
 */
public interface EconomyIntegration {

    /**
     * Called when a nation is created
     * @param nationId The UUID of the new nation
     * @param nationName The name of the nation
     */
    void onNationCreated(UUID nationId, String nationName);

    /**
     * Called when a state is created
     * @param stateId The UUID of the new state
     * @param stateName The name of the state
     * @param nationId The UUID of the parent nation
     */
    void onStateCreated(UUID stateId, String stateName, UUID nationId);

    /**
     * Called when a city is created
     * @param cityId The UUID of the new city
     * @param cityName The name of the city
     * @param stateId The UUID of the parent state
     */
    void onCityCreated(UUID cityId, String cityName, UUID stateId);

    /**
     * Called when a nation is disbanded
     * @param nationId The UUID of the disbanded nation
     */
    void onNationDisbanded(UUID nationId);

    /**
     * Called when a state is disbanded
     * @param stateId The UUID of the disbanded state
     */
    void onStateDisbanded(UUID stateId);

    /**
     * Called when a city is disbanded
     * @param cityId The UUID of the disbanded city
     */
    void onCityDisbanded(UUID cityId);

    /**
     * Get a player's current balance
     * @param playerId The UUID of the player
     * @return The player's balance, or 0 if not available
     */
    default double getPlayerBalance(UUID playerId) {
        return 0;
    }

    /**
     * Withdraw funds from a player's account
     * @param playerId The UUID of the player
     * @param amount The amount to withdraw
     * @param description Description of the withdrawal
     * @return true if successful, false if insufficient funds or error
     */
    default boolean withdrawFromPlayer(UUID playerId, double amount, String description) {
        return false;
    }

    /**
     * Format a currency amount for display
     * @param amount The amount to format
     * @return Formatted string (e.g., "$1,000.00")
     */
    default String formatCurrency(double amount) {
        return String.format("$%.2f", amount);
    }
}

