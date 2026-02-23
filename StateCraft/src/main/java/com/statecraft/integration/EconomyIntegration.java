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
     * Deposit funds to a player's account
     * @param playerId The UUID of the player
     * @param amount The amount to deposit
     * @param description Description of the deposit
     * @return true if successful, false on error
     */
    default boolean depositToPlayer(UUID playerId, double amount, String description) {
        return false;
    }

    /**
     * Get a nation's treasury balance
     * @param nationName The name of the nation
     * @return The nation's balance, or 0 if not available
     */
    default double getNationBalance(String nationName) {
        return 0;
    }

    /**
     * Withdraw funds from a nation's treasury
     * @param nationName The name of the nation
     * @param amount The amount to withdraw
     * @param description Description of the withdrawal
     * @return true if successful, false if insufficient funds or error
     */
    default boolean withdrawFromNation(String nationName, double amount, String description) {
        return false;
    }

    /**
     * Force withdraw funds from a nation's treasury, allowing negative balance.
     * Used for mandatory government actions like eminent domain where payment is required.
     * @param nationName The name of the nation
     * @param amount The amount to withdraw
     * @param description Description of the withdrawal
     * @return true if successful, false on error
     */
    default boolean forceWithdrawFromNation(String nationName, double amount, String description) {
        return false;
    }

    /**
     * Deposit funds to a nation's treasury
     * @param nationName The name of the nation
     * @param amount The amount to deposit
     * @param description Description of the deposit
     * @return true if successful, false on error
     */
    default boolean depositToNation(String nationName, double amount, String description) {
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

    /**
     * Get the improvement score for a chunk
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension (e.g., "minecraft:overworld")
     * @return The improvement score, or 0 if not available
     */
    default int getChunkImprovementScore(int chunkX, int chunkZ, String dimension) {
        return 0;
    }

    /**
     * Get the total valuation for a chunk (including all multipliers)
     * @param chunkX The chunk X coordinate
     * @param chunkZ The chunk Z coordinate
     * @param dimension The dimension (e.g., "minecraft:overworld")
     * @return The total chunk value, or 0 if not available
     */
    default double getChunkTotalValue(int chunkX, int chunkZ, String dimension) {
        return 0;
    }

    // ==================== Company Economy Integration ====================

    /**
     * Called when a company is created — economy mod should create a treasury account
     * @param companyId The UUID of the new company
     */
    default void onCompanyCreated(UUID companyId) {}

    /**
     * Called when a company is set as a bank — economy mod should initialize bank data.
     * @param companyId The UUID of the company being made into a bank
     */
    default void onBankCreated(UUID companyId) {}

    /**
     * Called before a company is dissolved — economy mod should distribute balance
     * to shareholders and handle bank dissolution.
     * @param companyId The company being dissolved
     * @param founderId The founder requesting dissolution
     * @return true if economy-side cleanup succeeded (or no economy loaded)
     */
    default boolean onCompanyDissolving(UUID companyId, UUID founderId) {
        return true;
    }

    /**
     * Get a company's treasury balance
     * @param companyId The company UUID
     * @return The balance, or 0 if not available
     */
    default double getCompanyBalance(UUID companyId) {
        return 0;
    }

    /**
     * Check if dividends are enabled for a company
     */
    default boolean isDividendsEnabled(UUID companyId) {
        return false;
    }

    /**
     * Get the dividend rate for a company (0.0 - 1.0)
     */
    default double getDividendRate(UUID companyId) {
        return 0;
    }

    /**
     * Get the dividend period in ticks for a company
     */
    default long getDividendPeriodTicks(UUID companyId) {
        return 72000;
    }

    /**
     * Set dividend configuration for a company
     * @param companyId The company UUID
     * @param enabled Whether dividends are enabled
     * @param rate The dividend rate (0.0 - 1.0)
     * @param periodTicks The period between dividend payouts in game ticks
     */
    default void setDividendConfig(UUID companyId, boolean enabled, double rate, long periodTicks) {}

    /**
     * Enable or disable dividends for a company
     */
    default void setDividendsEnabled(UUID companyId, boolean enabled) {}

    /**
     * Set the dividend rate for a company
     */
    default void setDividendRate(UUID companyId, double rate) {}

    /**
     * Set the dividend period for a company
     */
    default void setDividendPeriodTicks(UUID companyId, long ticks) {}

    // ==================== Bank Company Integration ====================

    /** Get the bank's deposit interest rate */
    default double getBankDepositInterestRate(UUID companyId) { return 0; }

    /** Get the bank's loan interest rate */
    default double getBankLoanInterestRate(UUID companyId) { return 0; }

    /** Get the bank's withdrawal fee rate */
    default double getBankWithdrawalFee(UUID companyId) { return 0; }

    /** Get the bank's transfer fee rate */
    default double getBankTransferFee(UUID companyId) { return 0; }

    /** Get the bank's reserve ratio */
    default double getBankReserveRatio(UUID companyId) { return 0; }

    /** Get the number of active loans at this bank */
    default int getBankActiveLoanCount(UUID companyId) { return 0; }

    /** Get the total deposits at this bank */
    default double getBankTotalDeposits(UUID companyId) { return 0; }

    /** Set the bank's deposit interest rate */
    default void setBankDepositInterestRate(UUID companyId, double rate) {}

    /** Set the bank's loan interest rate */
    default void setBankLoanInterestRate(UUID companyId, double rate) {}

    /** Set the bank's withdrawal fee rate */
    default void setBankWithdrawalFee(UUID companyId, double fee) {}

    /** Set the bank's transfer fee rate */
    default void setBankTransferFee(UUID companyId, double fee) {}


    /**
     * Issue a loan from a bank company to a player.
     * @return A description of the result (success message or error)
     */
    default String issueBankLoan(UUID companyId, UUID borrowerId, double amount) {
        return "Economy module not loaded";
    }
}

