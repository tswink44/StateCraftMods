package com.statecraft.economy.integration;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.SpendingLimitManager;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.NationTreasuryPacket;
import com.statecraft.economy.network.packets.SyncAccountsPacket;
import com.statecraft.economy.network.packets.SyncBalancePacket;
import com.statecraft.economy.network.packets.TransactionResultPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Integration with StateCraft mod for nation treasury features
 * This class uses reflection/soft dependency to avoid hard compile-time dependency
 */
@SuppressWarnings("unchecked")
public class StateCraftIntegration {

    private static boolean initialized = false;
    private static StateCraftEconomyIntegration economyIntegrationImpl = null;

    public static void init() {
        if (initialized) return;

        try {
            // Verify StateCraft classes are available
            Class.forName("com.statecraft.core.ChunkClaimManager");
            Class.forName("com.statecraft.core.Nation");
            initialized = true;

            // Register our economy integration handler with StateCraft
            registerEconomyIntegration();

            StateCraftEconomy.LOGGER.info("StateCraft integration initialized successfully");
        } catch (ClassNotFoundException e) {
            StateCraftEconomy.LOGGER.warn("StateCraft classes not found, integration disabled");
            initialized = false;
        }
    }

    /**
     * Register economy integration handler with StateCraft
     */
    private static void registerEconomyIntegration() {
        try {
            Class<?> registryClass = Class.forName("com.statecraft.integration.IntegrationRegistry");
            Class<?> integrationInterface = Class.forName("com.statecraft.integration.EconomyIntegration");

            // Create a proxy that implements EconomyIntegration
            economyIntegrationImpl = new StateCraftEconomyIntegration();

            // Create dynamic proxy to implement the interface
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                StateCraftIntegration.class.getClassLoader(),
                new Class<?>[] { integrationInterface },
                (proxyObj, method, args) -> {
                    switch (method.getName()) {
                        case "onNationCreated" -> economyIntegrationImpl.onNationCreated((UUID) args[0], (String) args[1]);
                        case "onStateCreated" -> economyIntegrationImpl.onStateCreated((UUID) args[0], (String) args[1], (UUID) args[2]);
                        case "onCityCreated" -> economyIntegrationImpl.onCityCreated((UUID) args[0], (String) args[1], (UUID) args[2]);
                        case "onNationDisbanded" -> economyIntegrationImpl.onNationDisbanded((UUID) args[0]);
                        case "onStateDisbanded" -> economyIntegrationImpl.onStateDisbanded((UUID) args[0]);
                        case "onCityDisbanded" -> economyIntegrationImpl.onCityDisbanded((UUID) args[0]);
                        case "getPlayerBalance" -> { return economyIntegrationImpl.getPlayerBalance((UUID) args[0]); }
                        case "withdrawFromPlayer" -> { return economyIntegrationImpl.withdrawFromPlayer((UUID) args[0], (Double) args[1], (String) args[2]); }
                        case "depositToPlayer" -> { return economyIntegrationImpl.depositToPlayer((UUID) args[0], (Double) args[1], (String) args[2]); }
                        case "getNationBalance" -> { return economyIntegrationImpl.getNationBalance((String) args[0]); }
                        case "withdrawFromNation" -> { return economyIntegrationImpl.withdrawFromNation((String) args[0], (Double) args[1], (String) args[2]); }
                        case "depositToNation" -> { return economyIntegrationImpl.depositToNation((String) args[0], (Double) args[1], (String) args[2]); }
                        case "formatCurrency" -> { return economyIntegrationImpl.formatCurrency((Double) args[0]); }
                        case "getChunkImprovementScore" -> { return economyIntegrationImpl.getChunkImprovementScore((Integer) args[0], (Integer) args[1], (String) args[2]); }
                        case "getChunkTotalValue" -> { return economyIntegrationImpl.getChunkTotalValue((Integer) args[0], (Integer) args[1], (String) args[2]); }
                        case "onCompanyCreated" -> economyIntegrationImpl.onCompanyCreated((UUID) args[0]);
                        case "onCompanyDissolving" -> { return economyIntegrationImpl.onCompanyDissolving((UUID) args[0], (UUID) args[1]); }
                        case "getCompanyBalance" -> { return economyIntegrationImpl.getCompanyBalance((UUID) args[0]); }
                        case "isDividendsEnabled" -> { return economyIntegrationImpl.isDividendsEnabled((UUID) args[0]); }
                        case "getDividendRate" -> { return economyIntegrationImpl.getDividendRate((UUID) args[0]); }
                        case "getDividendPeriodTicks" -> { return economyIntegrationImpl.getDividendPeriodTicks((UUID) args[0]); }
                        case "setDividendConfig" -> economyIntegrationImpl.setDividendConfig((UUID) args[0], (Boolean) args[1], (Double) args[2], (Long) args[3]);
                        case "setDividendsEnabled" -> economyIntegrationImpl.setDividendsEnabled((UUID) args[0], (Boolean) args[1]);
                        case "setDividendRate" -> economyIntegrationImpl.setDividendRate((UUID) args[0], (Double) args[1]);
                        case "setDividendPeriodTicks" -> economyIntegrationImpl.setDividendPeriodTicks((UUID) args[0], (Long) args[1]);
                    }
                    return null;
                }
            );

            var registerMethod = registryClass.getMethod("registerEconomyIntegration", integrationInterface);
            registerMethod.invoke(null, proxy);

            StateCraftEconomy.LOGGER.info("Economy integration handler registered with StateCraft");
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.warn("Could not register economy integration with StateCraft: {}", e.getMessage());
        }
    }

    /**
     * Implementation of economy integration callbacks
     */
    private static class StateCraftEconomyIntegration {
        public void onNationCreated(UUID nationId, String nationName) {
            // Create treasury account for the nation
            EconomyManager.getInstance().getOrCreateNationTreasury(nationId);
            StateCraftEconomy.LOGGER.info("Created treasury account for nation '{}' ({})", nationName, nationId);
        }

        public void onStateCreated(UUID stateId, String stateName, UUID nationId) {
            // Create treasury account for the state
            EconomyManager.getInstance().getOrCreateStateTreasury(stateId);
            StateCraftEconomy.LOGGER.info("Created treasury account for state '{}' ({})", stateName, stateId);
        }

        public void onCityCreated(UUID cityId, String cityName, UUID stateId) {
            // Create treasury account for the city
            EconomyManager.getInstance().getOrCreateCityTreasury(cityId);
            StateCraftEconomy.LOGGER.info("Created treasury account for city '{}' ({})", cityName, cityId);
        }

        public void onNationDisbanded(UUID nationId) {
            // Optionally handle nation disbandment (e.g., redistribute funds)
            StateCraftEconomy.LOGGER.info("Nation disbanded: {}", nationId);
        }

        public void onStateDisbanded(UUID stateId) {
            StateCraftEconomy.LOGGER.info("State disbanded: {}", stateId);
        }

        public void onCityDisbanded(UUID cityId) {
            StateCraftEconomy.LOGGER.info("City disbanded: {}", cityId);
        }

        public double getPlayerBalance(UUID playerId) {
            EconomyManager manager = EconomyManager.getInstance();
            double bankBalance = manager.getBalance(playerId);

            // Try to get player from server to include inventory currency
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    double inventoryValue = manager.getInventoryCurrencyValue(player);
                    return bankBalance + inventoryValue;
                }
            }

            // Fallback to bank balance only if player not found (offline)
            return bankBalance;
        }

        public boolean withdrawFromPlayer(UUID playerId, double amount, String description) {
            EconomyManager manager = EconomyManager.getInstance();

            // Try to get player from server to use smart withdrawal
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    var result = manager.withdrawSmart(player, amount, description);
                    return result.isSuccess();
                }
            }

            // Fallback to bank-only withdrawal if player not found
            var result = manager.withdraw(playerId, amount, description);
            return result.isSuccess();
        }

        public boolean depositToPlayer(UUID playerId, double amount, String description) {
            EconomyManager manager = EconomyManager.getInstance();
            var result = manager.deposit(playerId, amount, description);
            return result.isSuccess();
        }

        public double getNationBalance(String nationName) {
            // Look up nation by name and get its treasury balance
            try {
                var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
                var getInstance = managerClass.getMethod("getInstance");
                var claimManager = getInstance.invoke(null);

                var getNationByName = managerClass.getMethod("getNationByName", String.class);
                var nation = getNationByName.invoke(claimManager, nationName);

                if (nation != null) {
                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var getId = nationClass.getMethod("getId");
                    UUID nationId = (UUID) getId.invoke(nation);

                    EconomyManager manager = EconomyManager.getInstance();
                    return manager.getNationTreasuryBalance(nationId);
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error getting nation balance for '{}': {}", nationName, e.getMessage());
            }
            return 0.0;
        }

        public boolean withdrawFromNation(String nationName, double amount, String description) {
            try {
                var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
                var getInstance = managerClass.getMethod("getInstance");
                var claimManager = getInstance.invoke(null);

                var getNationByName = managerClass.getMethod("getNationByName", String.class);
                var nation = getNationByName.invoke(claimManager, nationName);

                if (nation != null) {
                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var getId = nationClass.getMethod("getId");
                    UUID nationId = (UUID) getId.invoke(nation);

                    EconomyManager manager = EconomyManager.getInstance();
                    var result = manager.withdrawFromNationTreasury(nationId, amount, description);
                    return result.isSuccess();
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error withdrawing from nation '{}': {}", nationName, e.getMessage());
            }
            return false;
        }

        public boolean depositToNation(String nationName, double amount, String description) {
            try {
                var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
                var getInstance = managerClass.getMethod("getInstance");
                var claimManager = getInstance.invoke(null);

                var getNationByName = managerClass.getMethod("getNationByName", String.class);
                var nation = getNationByName.invoke(claimManager, nationName);

                if (nation != null) {
                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var getId = nationClass.getMethod("getId");
                    UUID nationId = (UUID) getId.invoke(nation);

                    EconomyManager manager = EconomyManager.getInstance();
                    var result = manager.depositToNationTreasury(nationId, amount, description);
                    return result.isSuccess();
                }
            } catch (Exception e) {
                StateCraftEconomy.LOGGER.warn("Error depositing to nation '{}': {}", nationName, e.getMessage());
            }
            return false;
        }

        public String formatCurrency(double amount) {
            EconomyManager manager = EconomyManager.getInstance();
            if (manager != null) {
                return manager.formatCurrency(amount);
            }
            return String.format("$%.2f", amount);
        }

        public int getChunkImprovementScore(int chunkX, int chunkZ, String dimension) {
            com.statecraft.economy.valuation.ImprovementTracker tracker =
                com.statecraft.economy.valuation.ImprovementTracker.getInstance();
            return tracker.getScoreNoScan(chunkX, chunkZ, dimension);
        }

        public double getChunkTotalValue(int chunkX, int chunkZ, String dimension) {
            com.statecraft.economy.valuation.ChunkValuationManager valuationManager =
                com.statecraft.economy.valuation.ChunkValuationManager.getInstance();
            com.statecraft.economy.valuation.ChunkValuation valuation =
                valuationManager.getValuation(chunkX, chunkZ, dimension);
            return valuation != null ? valuation.getTotalValue() : 0;
        }

        // ==================== Company Economy ====================

        public void onCompanyCreated(UUID companyId) {
            EconomyManager.getInstance().getOrCreateCompanyTreasury(companyId);
            StateCraftEconomy.LOGGER.info("Created treasury account for company {}", companyId);
        }

        public boolean onCompanyDissolving(UUID companyId, UUID founderId) {
            return com.statecraft.economy.company.CompanyEconomyManager.getInstance()
                .dissolveCompany(companyId, founderId);
        }

        public double getCompanyBalance(UUID companyId) {
            return EconomyManager.getInstance().getCompanyBalance(companyId);
        }

        public boolean isDividendsEnabled(UUID companyId) {
            var config = com.statecraft.economy.company.CompanyEconomyManager.getInstance()
                .getDividendConfig(companyId);
            return config != null && config.isEnabled();
        }

        public double getDividendRate(UUID companyId) {
            var config = com.statecraft.economy.company.CompanyEconomyManager.getInstance()
                .getDividendConfig(companyId);
            return config != null ? config.getRate() : 0;
        }

        public long getDividendPeriodTicks(UUID companyId) {
            var config = com.statecraft.economy.company.CompanyEconomyManager.getInstance()
                .getDividendConfig(companyId);
            return config != null ? config.getPeriodTicks() : 72000;
        }

        public void setDividendConfig(UUID companyId, boolean enabled, double rate, long periodTicks) {
            var mgr = com.statecraft.economy.company.CompanyEconomyManager.getInstance();
            var config = mgr.getOrCreateDividendConfig(companyId);
            config.setEnabled(enabled);
            config.setRate(rate);
            config.setPeriodTicks(periodTicks);
            mgr.markDirty();
        }

        public void setDividendsEnabled(UUID companyId, boolean enabled) {
            var mgr = com.statecraft.economy.company.CompanyEconomyManager.getInstance();
            mgr.getOrCreateDividendConfig(companyId).setEnabled(enabled);
            mgr.markDirty();
        }

        public void setDividendRate(UUID companyId, double rate) {
            var mgr = com.statecraft.economy.company.CompanyEconomyManager.getInstance();
            mgr.getOrCreateDividendConfig(companyId).setRate(rate);
            mgr.markDirty();
        }

        public void setDividendPeriodTicks(UUID companyId, long ticks) {
            var mgr = com.statecraft.economy.company.CompanyEconomyManager.getInstance();
            mgr.getOrCreateDividendConfig(companyId).setPeriodTicks(ticks);
            mgr.markDirty();
        }
    }

    /**
     * Ensure all existing nations, states, and cities have treasury accounts
     * Call this when the economy mod loads to handle existing worlds
     */
    public static void ensureAllTreasuryAccounts() {
        if (!initialized) return;

        EconomyManager manager = EconomyManager.getInstance();
        int nationCount = 0, stateCount = 0, cityCount = 0;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            var getAllNations = managerClass.getMethod("getAllNations");
            @SuppressWarnings("unchecked")
            var nations = (java.util.Collection<?>) getAllNations.invoke(claimManager);

            if (nations != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var nationGetId = nationClass.getMethod("getId");
                var nationGetName = nationClass.getMethod("getName");
                var getAllStates = nationClass.getMethod("getAllStates");

                var stateClass = Class.forName("com.statecraft.core.State");
                var stateGetId = stateClass.getMethod("getId");
                var stateGetName = stateClass.getMethod("getName");
                var stateGetAllCities = stateClass.getMethod("getAllCities");

                var cityClass = Class.forName("com.statecraft.core.City");
                var cityGetId = cityClass.getMethod("getId");
                var cityGetName = cityClass.getMethod("getName");

                for (Object nation : nations) {
                    UUID nationId = (UUID) nationGetId.invoke(nation);
                    String nationName = (String) nationGetName.invoke(nation);
                    manager.getOrCreateNationTreasury(nationId);
                    nationCount++;

                    @SuppressWarnings("unchecked")
                    var states = (java.util.Collection<?>) getAllStates.invoke(nation);
                    if (states != null) {
                        for (Object state : states) {
                            UUID stateId = (UUID) stateGetId.invoke(state);
                            manager.getOrCreateStateTreasury(stateId);
                            stateCount++;

                            @SuppressWarnings("unchecked")
                            var cities = (java.util.Collection<?>) stateGetAllCities.invoke(state);
                            if (cities != null) {
                                for (Object city : cities) {
                                    UUID cityId = (UUID) cityGetId.invoke(city);
                                    manager.getOrCreateCityTreasury(cityId);
                                    cityCount++;
                                }
                            }
                        }
                    }
                }
            }

            StateCraftEconomy.LOGGER.info("Ensured treasury accounts for {} nations, {} states, {} cities",
                nationCount, stateCount, cityCount);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.warn("Error ensuring treasury accounts: {}", e.getMessage());
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if an economic emergency is active for a nation.
     * During economic emergency, only the nation leader can withdraw from the treasury.
     */
    public static boolean isEconomicEmergencyActive(UUID nationId) {
        if (!initialized || nationId == null) return false;
        try {
            var emergencyManagerClass = Class.forName("com.statecraft.legislature.EmergencyPowerManager");
            var isActive = emergencyManagerClass.getMethod("isEconomicEmergencyActive", UUID.class);
            return (Boolean) isActive.invoke(null, nationId);
        } catch (Exception e) {
            // EmergencyPowerManager not available — no emergency
            return false;
        }
    }

    /**
     * Check if a player is the leader of a specific nation.
     */
    public static boolean isNationLeader(UUID playerId, UUID nationId) {
        if (!initialized || playerId == null || nationId == null) return false;
        try {
            var emergencyManagerClass = Class.forName("com.statecraft.legislature.EmergencyPowerManager");
            var isLeader = emergencyManagerClass.getMethod("isLeaderOfNation", UUID.class, UUID.class);
            return (Boolean) isLeader.invoke(null, playerId, nationId);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the nation ID for a player
     */
    public static UUID getPlayerNationId(ServerPlayer player) {
        if (!initialized) return null;

        try {
            // Use StateCraft API to get player's nation
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
            var nation = getPlayerNation.invoke(manager, player.getUUID());

            if (nation != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var getId = nationClass.getMethod("getId");
                return (UUID) getId.invoke(nation);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting player nation: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if player is nation leader or officer.
     */
    public static boolean isNationLeaderOrOfficer(ServerPlayer player) {
        if (!initialized) return false;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
            var nation = getPlayerNation.invoke(manager, player.getUUID());

            if (nation != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var isLeaderOrOfficer = nationClass.getMethod("isLeaderOrOfficer", UUID.class);
                return (Boolean) isLeaderOrOfficer.invoke(nation, player.getUUID());
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error checking nation leader/officer: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Determine the player's role relative to a specific government account.
     * Used for spending limit enforcement.
     *
     * @param player The player
     * @param accountType "NATION", "STATE", or "CITY"
     * @param accountId The UUID of the nation/state/city
     * @return The player's role, or UNKNOWN if they don't have access
     */
    public static SpendingLimitManager.GovernmentRole getPlayerGovernmentRole(
            ServerPlayer player, String accountType, UUID accountId) {
        if (!initialized) return SpendingLimitManager.GovernmentRole.UNKNOWN;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);
            UUID playerUUID = player.getUUID();

            switch (accountType) {
                case "NATION" -> {
                    var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
                    var nation = getPlayerNation.invoke(claimManager, playerUUID);
                    if (nation == null) return SpendingLimitManager.GovernmentRole.UNKNOWN;

                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var getId = nationClass.getMethod("getId");
                    UUID nationId = (UUID) getId.invoke(nation);
                    if (!nationId.equals(accountId)) return SpendingLimitManager.GovernmentRole.UNKNOWN;

                    var getLeaderId = nationClass.getMethod("getLeaderId");
                    UUID leaderId = (UUID) getLeaderId.invoke(nation);
                    if (playerUUID.equals(leaderId)) {
                        return SpendingLimitManager.GovernmentRole.NATION_LEADER;
                    }

                    var isLeaderOrOfficer = nationClass.getMethod("isLeaderOrOfficer", UUID.class);
                    if ((Boolean) isLeaderOrOfficer.invoke(nation, playerUUID)) {
                        return SpendingLimitManager.GovernmentRole.NATION_OFFICER;
                    }
                }
                case "STATE" -> {
                    var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
                    var nation = getPlayerNation.invoke(claimManager, playerUUID);
                    if (nation == null) return SpendingLimitManager.GovernmentRole.UNKNOWN;

                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var getAllStates = nationClass.getMethod("getAllStates");
                    @SuppressWarnings("unchecked")
                    var states = (java.util.Collection<?>) getAllStates.invoke(nation);

                    var stateClass = Class.forName("com.statecraft.core.State");
                    var stateGetId = stateClass.getMethod("getId");
                    var stateGetGovernorId = stateClass.getMethod("getGovernorId");

                    for (Object state : states) {
                        UUID stateId = (UUID) stateGetId.invoke(state);
                        if (stateId.equals(accountId)) {
                            UUID governorId = (UUID) stateGetGovernorId.invoke(state);
                            if (playerUUID.equals(governorId)) {
                                return SpendingLimitManager.GovernmentRole.STATE_GOVERNOR;
                            }
                            // Nation leader/officer accessing state account
                            var getLeaderId = nationClass.getMethod("getLeaderId");
                            UUID leaderId = (UUID) getLeaderId.invoke(nation);
                            if (playerUUID.equals(leaderId)) {
                                return SpendingLimitManager.GovernmentRole.NATION_LEADER;
                            }
                            var isLeaderOrOfficer = nationClass.getMethod("isLeaderOrOfficer", UUID.class);
                            if ((Boolean) isLeaderOrOfficer.invoke(nation, playerUUID)) {
                                return SpendingLimitManager.GovernmentRole.NATION_OFFICER;
                            }
                            break;
                        }
                    }
                }
                case "CITY" -> {
                    var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
                    var nation = getPlayerNation.invoke(claimManager, playerUUID);
                    if (nation == null) return SpendingLimitManager.GovernmentRole.UNKNOWN;

                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var getAllStates = nationClass.getMethod("getAllStates");
                    @SuppressWarnings("unchecked")
                    var states = (java.util.Collection<?>) getAllStates.invoke(nation);

                    var stateClass = Class.forName("com.statecraft.core.State");
                    var stateGetAllCities = stateClass.getMethod("getAllCities");
                    var stateGetGovernorId = stateClass.getMethod("getGovernorId");

                    var cityClass = Class.forName("com.statecraft.core.City");
                    var cityGetId = cityClass.getMethod("getId");
                    var cityGetMayorId = cityClass.getMethod("getMayorId");

                    for (Object state : states) {
                        @SuppressWarnings("unchecked")
                        var cities = (java.util.Collection<?>) stateGetAllCities.invoke(state);
                        for (Object city : cities) {
                            UUID cityId = (UUID) cityGetId.invoke(city);
                            if (cityId.equals(accountId)) {
                                UUID mayorId = (UUID) cityGetMayorId.invoke(city);
                                if (playerUUID.equals(mayorId)) {
                                    return SpendingLimitManager.GovernmentRole.CITY_MAYOR;
                                }
                                UUID governorId = (UUID) stateGetGovernorId.invoke(state);
                                if (playerUUID.equals(governorId)) {
                                    return SpendingLimitManager.GovernmentRole.STATE_GOVERNOR;
                                }
                                var getLeaderId = nationClass.getMethod("getLeaderId");
                                UUID leaderId = (UUID) getLeaderId.invoke(nation);
                                if (playerUUID.equals(leaderId)) {
                                    return SpendingLimitManager.GovernmentRole.NATION_LEADER;
                                }
                                var isLeaderOrOfficer = nationClass.getMethod("isLeaderOrOfficer", UUID.class);
                                if ((Boolean) isLeaderOrOfficer.invoke(nation, playerUUID)) {
                                    return SpendingLimitManager.GovernmentRole.NATION_OFFICER;
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error determining government role: {}", e.getMessage());
        }
        return SpendingLimitManager.GovernmentRole.UNKNOWN;
    }

    /**
     * Get the nation ID that a government account belongs to.
     * For NATION accounts, this is the account ID itself.
     * For STATE/CITY accounts, this finds the parent nation.
     */
    public static UUID getNationIdForAccount(String accountType, UUID accountId) {
        if (!initialized) return null;
        if ("NATION".equals(accountType)) return accountId;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            var getAllNations = managerClass.getMethod("getAllNations");
            @SuppressWarnings("unchecked")
            var nations = (java.util.Collection<?>) getAllNations.invoke(claimManager);

            var nationClass = Class.forName("com.statecraft.core.Nation");
            var nationGetId = nationClass.getMethod("getId");
            var getAllStates = nationClass.getMethod("getAllStates");

            var stateClass = Class.forName("com.statecraft.core.State");
            var stateGetId = stateClass.getMethod("getId");
            var stateGetAllCities = stateClass.getMethod("getAllCities");

            var cityClass = Class.forName("com.statecraft.core.City");
            var cityGetId = cityClass.getMethod("getId");

            for (Object nation : nations) {
                UUID nationId = (UUID) nationGetId.invoke(nation);

                @SuppressWarnings("unchecked")
                var states = (java.util.Collection<?>) getAllStates.invoke(nation);
                for (Object state : states) {
                    if ("STATE".equals(accountType)) {
                        UUID stateId = (UUID) stateGetId.invoke(state);
                        if (stateId.equals(accountId)) return nationId;
                    }
                    if ("CITY".equals(accountType)) {
                        @SuppressWarnings("unchecked")
                        var cities = (java.util.Collection<?>) stateGetAllCities.invoke(state);
                        for (Object city : cities) {
                            UUID cityId = (UUID) cityGetId.invoke(city);
                            if (cityId.equals(accountId)) return nationId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error finding nation for account: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get all government accounts the player has admin access to
     * Returns list of AccountInfo for nations, states, and cities where player is admin/governor/mayor
     */
    public static List<SyncAccountsPacket.AccountInfo> getPlayerAdminAccounts(ServerPlayer player) {
        List<SyncAccountsPacket.AccountInfo> accounts = new ArrayList<>();

        if (!initialized) return accounts;

        EconomyManager manager = EconomyManager.getInstance();
        UUID playerUUID = player.getUUID();

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            // Get player's nation
            var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
            var nation = getPlayerNation.invoke(claimManager, playerUUID);

            if (nation != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var isLeaderOrOfficer = nationClass.getMethod("isLeaderOrOfficer", UUID.class);
                var getId = nationClass.getMethod("getId");
                var getName = nationClass.getMethod("getName");
                UUID nationId = (UUID) getId.invoke(nation);
                String nationName = (String) getName.invoke(nation);
                boolean isNationLeaderOrOfficer = (Boolean) isLeaderOrOfficer.invoke(nation, playerUUID);

                // Add nation account if player is leader or officer
                if (isNationLeaderOrOfficer) {
                    accounts.add(new SyncAccountsPacket.AccountInfo(
                        "NATION",
                        nationName,
                        nationId.toString(),
                        manager.getNationBalance(nationId)
                    ));
                }

                // Get states where player is governor (check all states, not just if nation admin)
                try {
                    var getAllStates = nationClass.getMethod("getAllStates");
                    @SuppressWarnings("unchecked")
                    var states = (java.util.Collection<?>) getAllStates.invoke(nation);

                    if (states != null) {
                        var stateClass = Class.forName("com.statecraft.core.State");
                        var stateGetId = stateClass.getMethod("getId");
                        var stateGetName = stateClass.getMethod("getName");
                        var stateGetGovernorId = stateClass.getMethod("getGovernorId");
                        var stateGetAllCities = stateClass.getMethod("getAllCities");

                        for (Object state : states) {
                            UUID governorId = (UUID) stateGetGovernorId.invoke(state);
                            boolean isGovernor = playerUUID.equals(governorId);

                            // Add state account if player is governor or nation leader/officer
                            if (isGovernor || isNationLeaderOrOfficer) {
                                UUID stateId = (UUID) stateGetId.invoke(state);
                                String stateName = (String) stateGetName.invoke(state);

                                accounts.add(new SyncAccountsPacket.AccountInfo(
                                    "STATE",
                                    stateName,
                                    stateId.toString(),
                                    manager.getGovernmentBalance("state", stateId)
                                ));
                            }

                            // Get cities from this state where player is mayor
                            try {
                                @SuppressWarnings("unchecked")
                                var cities = (java.util.Collection<?>) stateGetAllCities.invoke(state);

                                if (cities != null) {
                                    var cityClass = Class.forName("com.statecraft.core.City");
                                    var cityGetId = cityClass.getMethod("getId");
                                    var cityGetName = cityClass.getMethod("getName");
                                    var cityGetMayorId = cityClass.getMethod("getMayorId");

                                    for (Object city : cities) {
                                        UUID mayorId = (UUID) cityGetMayorId.invoke(city);
                                        boolean isMayor = playerUUID.equals(mayorId);
                                        UUID governorIdForCity = (UUID) stateGetGovernorId.invoke(state);
                                        boolean isGovernorForCity = playerUUID.equals(governorIdForCity);

                                        // Add city account if player is mayor, governor of parent state, or nation leader/officer
                                        if (isMayor || isGovernorForCity || isNationLeaderOrOfficer) {
                                            UUID cityId = (UUID) cityGetId.invoke(city);
                                            String cityName = (String) cityGetName.invoke(city);

                                            accounts.add(new SyncAccountsPacket.AccountInfo(
                                                "CITY",
                                                cityName,
                                                cityId.toString(),
                                                manager.getGovernmentBalance("city", cityId)
                                            ));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                StateCraftEconomy.LOGGER.debug("Error getting cities from state: {}", e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    StateCraftEconomy.LOGGER.debug("Error getting states: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting player admin accounts: {}", e.getMessage());
        }

        return accounts;
    }

    /**
     * Check if a player is a member of a government entity (nation, state, or city)
     */
    public static boolean isPlayerMemberOfEntity(ServerPlayer player, String entityType, UUID entityId) {
        if (!initialized) return false;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
            var nation = getPlayerNation.invoke(claimManager, player.getUUID());

            if (nation == null) return false;

            var nationClass = Class.forName("com.statecraft.core.Nation");
            var getId = nationClass.getMethod("getId");
            UUID nationId = (UUID) getId.invoke(nation);

            switch (entityType) {
                case "NATION" -> {
                    return nationId.equals(entityId);
                }
                case "STATE" -> {
                    var getAllStates = nationClass.getMethod("getAllStates");
                    @SuppressWarnings("unchecked")
                    var states = (java.util.Collection<?>) getAllStates.invoke(nation);
                    if (states != null) {
                        var stateClass = Class.forName("com.statecraft.core.State");
                        var stateGetId = stateClass.getMethod("getId");
                        for (Object state : states) {
                            UUID stateId = (UUID) stateGetId.invoke(state);
                            if (stateId.equals(entityId)) return true;
                        }
                    }
                }
                case "CITY" -> {
                    var getAllStates = nationClass.getMethod("getAllStates");
                    @SuppressWarnings("unchecked")
                    var states = (java.util.Collection<?>) getAllStates.invoke(nation);
                    if (states != null) {
                        var stateClass = Class.forName("com.statecraft.core.State");
                        var stateGetAllCities = stateClass.getMethod("getAllCities");
                        var cityClass = Class.forName("com.statecraft.core.City");
                        var cityGetId = cityClass.getMethod("getId");
                        for (Object state : states) {
                            @SuppressWarnings("unchecked")
                            var cities = (java.util.Collection<?>) stateGetAllCities.invoke(state);
                            for (Object city : cities) {
                                UUID cityId = (UUID) cityGetId.invoke(city);
                                if (cityId.equals(entityId)) return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error checking membership: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get the display name for a government entity by type and UUID
     */
    public static String getEntityName(String entityType, UUID entityId) {
        if (!initialized) return "Unknown";

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            switch (entityType) {
                case "NATION" -> {
                    var getAllNations = managerClass.getMethod("getAllNations");
                    @SuppressWarnings("unchecked")
                    var nations = (java.util.Collection<?>) getAllNations.invoke(claimManager);
                    if (nations != null) {
                        var nationClass = Class.forName("com.statecraft.core.Nation");
                        var getId = nationClass.getMethod("getId");
                        var getName = nationClass.getMethod("getName");
                        for (Object nation : nations) {
                            if (entityId.equals(getId.invoke(nation))) {
                                return (String) getName.invoke(nation);
                            }
                        }
                    }
                }
                case "STATE" -> {
                    var getAllNations = managerClass.getMethod("getAllNations");
                    @SuppressWarnings("unchecked")
                    var nations = (java.util.Collection<?>) getAllNations.invoke(claimManager);
                    if (nations != null) {
                        var nationClass = Class.forName("com.statecraft.core.Nation");
                        var getAllStates = nationClass.getMethod("getAllStates");
                        var stateClass = Class.forName("com.statecraft.core.State");
                        var stateGetId = stateClass.getMethod("getId");
                        var stateGetName = stateClass.getMethod("getName");
                        for (Object nation : nations) {
                            @SuppressWarnings("unchecked")
                            var states = (java.util.Collection<?>) getAllStates.invoke(nation);
                            if (states != null) {
                                for (Object state : states) {
                                    if (entityId.equals(stateGetId.invoke(state))) {
                                        return (String) stateGetName.invoke(state);
                                    }
                                }
                            }
                        }
                    }
                }
                case "CITY" -> {
                    var getAllNations = managerClass.getMethod("getAllNations");
                    @SuppressWarnings("unchecked")
                    var nations = (java.util.Collection<?>) getAllNations.invoke(claimManager);
                    if (nations != null) {
                        var nationClass = Class.forName("com.statecraft.core.Nation");
                        var getAllStates = nationClass.getMethod("getAllStates");
                        var stateClass = Class.forName("com.statecraft.core.State");
                        var stateGetAllCities = stateClass.getMethod("getAllCities");
                        var cityClass = Class.forName("com.statecraft.core.City");
                        var cityGetId = cityClass.getMethod("getId");
                        var cityGetName = cityClass.getMethod("getName");
                        for (Object nation : nations) {
                            @SuppressWarnings("unchecked")
                            var states = (java.util.Collection<?>) getAllStates.invoke(nation);
                            if (states != null) {
                                for (Object state : states) {
                                    @SuppressWarnings("unchecked")
                                    var cities = (java.util.Collection<?>) stateGetAllCities.invoke(state);
                                    if (cities != null) {
                                        for (Object city : cities) {
                                            if (entityId.equals(cityGetId.invoke(city))) {
                                                return (String) cityGetName.invoke(city);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting entity name: {}", e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Get nation balance
     */
    public static double getNationBalance(ServerPlayer player) {
        UUID nationId = getPlayerNationId(player);
        if (nationId == null) return -1;

        return EconomyManager.getInstance().getNationBalance(nationId);
    }

    /**
     * Simple record for entity info (nation, state, city)
     */
    public record EntityInfo(String name, String id) {}

    /**
     * Get all nations for transfer recipient list
     */
    public static List<EntityInfo> getAllNations() {
        List<EntityInfo> nations = new ArrayList<>();
        if (!initialized) return nations;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            var getAllNations = managerClass.getMethod("getAllNations");
            @SuppressWarnings("unchecked")
            var nationCollection = (java.util.Collection<?>) getAllNations.invoke(claimManager);

            if (nationCollection != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var getId = nationClass.getMethod("getId");
                var getName = nationClass.getMethod("getName");

                for (Object nation : nationCollection) {
                    UUID nationId = (UUID) getId.invoke(nation);
                    String nationName = (String) getName.invoke(nation);
                    nations.add(new EntityInfo(nationName, nationId.toString()));
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting all nations: {}", e.getMessage());
        }

        return nations;
    }

    /**
     * Get all states for transfer recipient list
     */
    public static List<EntityInfo> getAllStates() {
        List<EntityInfo> states = new ArrayList<>();
        if (!initialized) return states;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            var getAllNations = managerClass.getMethod("getAllNations");
            @SuppressWarnings("unchecked")
            var nationCollection = (java.util.Collection<?>) getAllNations.invoke(claimManager);

            if (nationCollection != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var getAllStates = nationClass.getMethod("getAllStates");

                var stateClass = Class.forName("com.statecraft.core.State");
                var stateGetId = stateClass.getMethod("getId");
                var stateGetName = stateClass.getMethod("getName");

                for (Object nation : nationCollection) {
                    @SuppressWarnings("unchecked")
                    var stateCollection = (java.util.Collection<?>) getAllStates.invoke(nation);

                    if (stateCollection != null) {
                        for (Object state : stateCollection) {
                            UUID stateId = (UUID) stateGetId.invoke(state);
                            String stateName = (String) stateGetName.invoke(state);
                            states.add(new EntityInfo(stateName, stateId.toString()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting all states: {}", e.getMessage());
        }

        return states;
    }

    /**
     * Get all cities for transfer recipient list
     */
    public static List<EntityInfo> getAllCities() {
        List<EntityInfo> cities = new ArrayList<>();
        if (!initialized) return cities;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var claimManager = getInstance.invoke(null);

            var getAllNations = managerClass.getMethod("getAllNations");
            @SuppressWarnings("unchecked")
            var nationCollection = (java.util.Collection<?>) getAllNations.invoke(claimManager);

            if (nationCollection != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var getAllStates = nationClass.getMethod("getAllStates");

                var stateClass = Class.forName("com.statecraft.core.State");
                var stateGetAllCities = stateClass.getMethod("getAllCities");

                var cityClass = Class.forName("com.statecraft.core.City");
                var cityGetId = cityClass.getMethod("getId");
                var cityGetName = cityClass.getMethod("getName");

                for (Object nation : nationCollection) {
                    @SuppressWarnings("unchecked")
                    var stateCollection = (java.util.Collection<?>) getAllStates.invoke(nation);

                    if (stateCollection != null) {
                        for (Object state : stateCollection) {
                            @SuppressWarnings("unchecked")
                            var cityCollection = (java.util.Collection<?>) stateGetAllCities.invoke(state);

                            if (cityCollection != null) {
                                for (Object city : cityCollection) {
                                    UUID cityId = (UUID) cityGetId.invoke(city);
                                    String cityName = (String) cityGetName.invoke(city);
                                    cities.add(new EntityInfo(cityName, cityId.toString()));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting all cities: {}", e.getMessage());
        }

        return cities;
    }

    /**
     * Handle nation treasury operations
     */
    public static void handleNationTreasury(ServerPlayer player, NationTreasuryPacket.Action action, double amount) {
        UUID nationId = getPlayerNationId(player);

        if (nationId == null) {
            NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                "You are not in a nation", EconomyManager.getInstance().getBalance(player.getUUID())), player);
            return;
        }

        EconomyManager manager = EconomyManager.getInstance();

        switch (action) {
            case VIEW -> {
                double nationBalance = manager.getNationBalance(nationId);
                double playerBalance = manager.getBalance(player.getUUID());
                NetworkHandler.sendToPlayer(new SyncBalancePacket(playerBalance, nationBalance), player);
            }
            case DEPOSIT -> {
                TransactionResult result = manager.depositToNation(nationId, player.getUUID(), amount, "Nation treasury deposit");
                NetworkHandler.sendToPlayer(new TransactionResultPacket(result.isSuccess(),
                    result.getMessage(), result.getNewBalance()), player);
                NetworkHandler.sendToPlayer(new SyncBalancePacket(result.getNewBalance(), manager.getNationBalance(nationId)), player);
            }
            case WITHDRAW -> {
                // Only leader/officers can withdraw
                if (!isNationLeaderOrOfficer(player)) {
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                        "Only nation leader or officers can withdraw from treasury", manager.getBalance(player.getUUID())), player);
                    return;
                }
                // Check daily spending limit
                SpendingLimitManager spendingMgr = SpendingLimitManager.getInstance();
                SpendingLimitManager.GovernmentRole role =
                    getPlayerGovernmentRole(player, "NATION", nationId);
                String limitError = spendingMgr.checkSpendingLimit(
                    player.getUUID(), "NATION", nationId, amount, role, nationId);
                if (limitError != null) {
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                        limitError, manager.getBalance(player.getUUID())), player);
                    return;
                }
                TransactionResult result = manager.withdrawFromNation(nationId, player.getUUID(), amount, "Nation treasury withdrawal");
                if (result.isSuccess()) {
                    spendingMgr.recordSpending(player.getUUID(), "NATION", nationId, amount);
                }
                NetworkHandler.sendToPlayer(new TransactionResultPacket(result.isSuccess(),
                    result.getMessage(), manager.getBalance(player.getUUID())), player);
                NetworkHandler.sendToPlayer(new SyncBalancePacket(manager.getBalance(player.getUUID()), result.getNewBalance()), player);
            }
        }
    }

    // ==================== Chunk Market Methods ====================

    /**
     * Record for chunk information
     */
    public record ChunkInfo(
        UUID cityId,
        boolean isPrivatelyOwned,
        UUID ownerId,
        boolean isForSale,
        double salePrice,
        UUID sellerId
    ) {}

    /**
     * Get city name by ID
     */
    public static String getCityName(UUID cityId) {
        if (!initialized || cityId == null) return "";

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getCity = managerClass.getMethod("getCity", UUID.class);
            var city = getCity.invoke(manager, cityId);

            if (city != null) {
                var cityClass = Class.forName("com.statecraft.core.City");
                var getName = cityClass.getMethod("getName");
                return (String) getName.invoke(city);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting city name: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Get state name by ID
     */
    public static String getStateName(UUID stateId) {
        if (!initialized || stateId == null) return "";

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getState = managerClass.getMethod("getState", UUID.class);
            var state = getState.invoke(manager, stateId);

            if (state != null) {
                var stateClass = Class.forName("com.statecraft.core.State");
                var getName = stateClass.getMethod("getName");
                return (String) getName.invoke(state);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting state name: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Get nation name by ID
     */
    public static String getNationName(UUID nationId) {
        if (!initialized || nationId == null) return "";

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getNation = managerClass.getMethod("getNation", UUID.class);
            var nation = getNation.invoke(manager, nationId);

            if (nation != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var getName = nationClass.getMethod("getName");
                return (String) getName.invoke(nation);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting nation name: {}", e.getMessage());
        }
        return "";
    }

    /**
     * Get the IMPORT_TARIFF rate for a nation (set via legislature policy).
     * Returns 0.0 if no policy is set or nation not found.
     */
    public static double getImportTariffRate(UUID nationId) {
        if (!initialized || nationId == null) return 0.0;

        try {
            var legislatureClass = Class.forName("com.statecraft.legislature.LegislatureManager");
            var getInstance = legislatureClass.getMethod("getInstance");
            var legislatureManager = getInstance.invoke(null);

            var policyTypeClass = Class.forName("com.statecraft.legislature.PolicyType");
            Object importTariffPolicy = null;
            for (Object constant : policyTypeClass.getEnumConstants()) {
                if ("IMPORT_TARIFF".equals(constant.toString())) {
                    importTariffPolicy = constant;
                    break;
                }
            }
            if (importTariffPolicy == null) return 0.0;

            var getPassedPolicy = legislatureClass.getMethod("getPassedPolicyValue", UUID.class, policyTypeClass);
            Object result = getPassedPolicy.invoke(legislatureManager, nationId, importTariffPolicy);
            if (result instanceof Number num) {
                return num.doubleValue();
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting import tariff rate: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * Get information about a claimed chunk
     */
    @SuppressWarnings("unchecked")
    public static ChunkInfo getChunkInfo(int chunkX, int chunkZ, net.minecraft.resources.ResourceKey<?> dimension) {
        if (!initialized) return null;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            // Create ChunkPos
            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, dimension);

            if (chunk == null) {
                return null;
            }

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getCityId = chunkClass.getMethod("getCityId");
            var getOwnershipType = chunkClass.getMethod("getOwnershipType");
            var getPlayerOwner = chunkClass.getMethod("getPlayerOwner");
            var isForSale = chunkClass.getMethod("isForSale");
            var getSalePrice = chunkClass.getMethod("getSalePrice");
            var getSellerId = chunkClass.getMethod("getSellerId");

            UUID cityId = (UUID) getCityId.invoke(chunk);
            Object ownershipType = getOwnershipType.invoke(chunk);
            boolean isPrivate = ownershipType.toString().equals("PLAYER");
            UUID ownerId = (UUID) getPlayerOwner.invoke(chunk);
            boolean forSale = (Boolean) isForSale.invoke(chunk);
            double price = (Double) getSalePrice.invoke(chunk);
            UUID seller = (UUID) getSellerId.invoke(chunk);

            return new ChunkInfo(cityId, isPrivate, ownerId, forSale, price, seller);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting chunk info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a player can manage a chunk (city mayor, state governor, or nation admin)
     */
    public static boolean canManageChunk(ServerPlayer player, int chunkX, int chunkZ) {
        if (!initialized) return false;

        try {
            var chunkInfo = getChunkInfo(chunkX, chunkZ, player.level().dimension());
            if (chunkInfo == null) return false;

            UUID playerId = player.getUUID();
            UUID cityId = chunkInfo.cityId();

            // Check if player is the private owner
            if (chunkInfo.isPrivatelyOwned() && playerId.equals(chunkInfo.ownerId())) {
                return true;
            }

            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            // Get city
            var getCity = managerClass.getMethod("getCity", UUID.class);
            var city = getCity.invoke(manager, cityId);
            if (city == null) return false;

            var cityClass = Class.forName("com.statecraft.core.City");
            var getMayorId = cityClass.getMethod("getMayorId");
            var getStateId = cityClass.getMethod("getStateId");

            // Check if mayor
            UUID mayorId = (UUID) getMayorId.invoke(city);
            if (playerId.equals(mayorId)) return true;

            // Get state
            UUID stateId = (UUID) getStateId.invoke(city);
            var getState = managerClass.getMethod("getState", UUID.class);
            var state = getState.invoke(manager, stateId);
            if (state != null) {
                var stateClass = Class.forName("com.statecraft.core.State");
                var getGovernorId = stateClass.getMethod("getGovernorId");
                var getNationId = stateClass.getMethod("getNationId");

                // Check if governor
                UUID governorId = (UUID) getGovernorId.invoke(state);
                if (playerId.equals(governorId)) return true;

                // Get nation
                UUID nationId = (UUID) getNationId.invoke(state);
                var getNation = managerClass.getMethod("getNation", UUID.class);
                var nation = getNation.invoke(manager, nationId);
                if (nation != null) {
                    var nationClass = Class.forName("com.statecraft.core.Nation");
                    var isLeaderOrOfficer = nationClass.getMethod("isLeaderOrOfficer", UUID.class);
                    return (Boolean) isLeaderOrOfficer.invoke(nation, playerId);
                }
            }

            return false;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error checking chunk management: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Set a chunk for sale
     */
    public static boolean setChunkForSale(int chunkX, int chunkZ, net.minecraft.resources.ResourceKey<?> dimension,
                                          double price, UUID sellerId) {
        if (!initialized) return false;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, dimension);

            if (chunk == null) return false;

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var listForSale = chunkClass.getMethod("listForSale", double.class, UUID.class);
            listForSale.invoke(chunk, price, sellerId);

            // Mark data dirty
            var markDirty = managerClass.getMethod("markDirty");
            markDirty.invoke(manager);

            return true;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error setting chunk for sale: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Remove a chunk from sale
     */
    public static boolean removeChunkFromSale(int chunkX, int chunkZ, net.minecraft.resources.ResourceKey<?> dimension) {
        if (!initialized) return false;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, dimension);

            if (chunk == null) return false;

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var removeFromSale = chunkClass.getMethod("removeFromSale");
            removeFromSale.invoke(chunk);

            var markDirty = managerClass.getMethod("markDirty");
            markDirty.invoke(manager);

            return true;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error removing chunk from sale: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Transfer chunk ownership to a new player
     */
    public static boolean transferChunkOwnership(int chunkX, int chunkZ,
                                                  net.minecraft.resources.ResourceKey<?> dimension, UUID newOwnerId) {
        if (!initialized) {
            StateCraftEconomy.LOGGER.warn("Cannot transfer chunk ownership - StateCraft not initialized");
            return false;
        }

        try {
            StateCraftEconomy.LOGGER.info("Attempting to transfer chunk ({}, {}) to player {}", chunkX, chunkZ, newOwnerId);

            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, dimension);

            if (chunk == null) {
                StateCraftEconomy.LOGGER.warn("Chunk ({}, {}) not found for transfer", chunkX, chunkZ);
                return false;
            }

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");

            // Set new owner - this also sets ownershipType to PLAYER
            var setPlayerOwner = chunkClass.getMethod("setPlayerOwner", UUID.class);
            setPlayerOwner.invoke(chunk, newOwnerId);
            StateCraftEconomy.LOGGER.info("Set player owner to {}", newOwnerId);

            // Remove from sale
            var removeFromSale = chunkClass.getMethod("removeFromSale");
            removeFromSale.invoke(chunk);
            StateCraftEconomy.LOGGER.info("Removed chunk from sale");

            // Mark data dirty so it saves
            var markDirty = managerClass.getMethod("markDirty");
            markDirty.invoke(manager);
            StateCraftEconomy.LOGGER.info("Marked data dirty");

            // Verify the transfer worked
            var getOwnershipType = chunkClass.getMethod("getOwnershipType");
            var ownershipType = getOwnershipType.invoke(chunk);
            var getPlayerOwner = chunkClass.getMethod("getPlayerOwner");
            var playerOwner = getPlayerOwner.invoke(chunk);
            StateCraftEconomy.LOGGER.info("Verification - ownershipType: {}, playerOwner: {}", ownershipType, playerOwner);

            return true;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error transferring chunk ownership: {}", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Transfer chunk ownership back to government (for repossession)
     */
    public static boolean transferChunkToGovernment(int chunkX, int chunkZ,
                                                     net.minecraft.resources.ResourceKey<?> dimension) {
        if (!initialized) return false;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            var chunkPos = chunkPosClass.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ);

            var getClaimedChunk = managerClass.getMethod("getClaimedChunk", chunkPosClass,
                Class.forName("net.minecraft.resources.ResourceKey"));
            var chunk = getClaimedChunk.invoke(manager, chunkPos, dimension);

            if (chunk == null) return false;

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");

            // Set owner to null (government owned)
            var setPlayerOwner = chunkClass.getMethod("setPlayerOwner", UUID.class);
            setPlayerOwner.invoke(chunk, (UUID) null);

            // Remove from sale if listed
            var removeFromSale = chunkClass.getMethod("removeFromSale");
            removeFromSale.invoke(chunk);

            var markDirty = managerClass.getMethod("markDirty");
            markDirty.invoke(manager);

            StateCraftEconomy.LOGGER.info("Chunk ({}, {}) transferred to government", chunkX, chunkZ);
            return true;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error transferring chunk to government: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get all taxable chunks (privately owned chunks)
     */
    public static List<com.statecraft.economy.core.TaxationManager.ChunkTaxInfo> getAllTaxableChunks(
            net.minecraft.server.MinecraftServer server) {
        List<com.statecraft.economy.core.TaxationManager.ChunkTaxInfo> result = new ArrayList<>();

        if (!initialized) return result;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getAllNations = managerClass.getMethod("getAllNations");
            @SuppressWarnings("unchecked")
            var nations = (java.util.Collection<?>) getAllNations.invoke(manager);

            var nationClass = Class.forName("com.statecraft.core.Nation");
            var getAllStates = nationClass.getMethod("getAllStates");

            var stateClass = Class.forName("com.statecraft.core.State");
            var getAllCities = stateClass.getMethod("getAllCities");

            var cityClass = Class.forName("com.statecraft.core.City");
            var getAllChunks = cityClass.getMethod("getAllChunks");
            var getCityId = cityClass.getMethod("getId");

            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getOwnershipType = chunkClass.getMethod("getOwnershipType");
            var getPlayerOwner = chunkClass.getMethod("getPlayerOwner");
            var getChunkPos = chunkClass.getMethod("getChunkPos");
            var getDimension = chunkClass.getMethod("getDimension");
            var getSalePrice = chunkClass.getMethod("getSalePrice");

            var ownershipTypeClass = Class.forName("com.statecraft.core.OwnershipType");
            var playerType = Enum.valueOf((Class<Enum>) ownershipTypeClass, "PLAYER");

            for (Object nation : nations) {
                @SuppressWarnings("unchecked")
                var states = (java.util.Collection<?>) getAllStates.invoke(nation);

                for (Object state : states) {
                    @SuppressWarnings("unchecked")
                    var cities = (java.util.Collection<?>) getAllCities.invoke(state);

                    for (Object city : cities) {
                        UUID cityId = (UUID) getCityId.invoke(city);

                        @SuppressWarnings("unchecked")
                        var chunks = (java.util.Collection<?>) getAllChunks.invoke(city);

                        for (Object chunk : chunks) {
                            Object ownership = getOwnershipType.invoke(chunk);

                            if (ownership.equals(playerType)) {
                                UUID ownerId = (UUID) getPlayerOwner.invoke(chunk);
                                Object chunkPos = getChunkPos.invoke(chunk);
                                Object dimension = getDimension.invoke(chunk);
                                double salePrice = (Double) getSalePrice.invoke(chunk);

                                var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
                                int x = (int) chunkPosClass.getField("x").get(chunkPos);
                                int z = (int) chunkPosClass.getField("z").get(chunkPos);

                                // Get dimension string using location().toString() for consistency
                                // ResourceKey.toString() returns "ResourceKey[minecraft:dimension / minecraft:overworld]"
                                // but we need "minecraft:overworld" to match cache keys
                                net.minecraft.resources.ResourceKey<?> dimKey = (net.minecraft.resources.ResourceKey<?>) dimension;
                                String dimStr = dimKey.location().toString();

                                result.add(new com.statecraft.economy.core.TaxationManager.ChunkTaxInfo(
                                    x, z, dimStr,
                                    dimKey,
                                    cityId, ownerId, true, salePrice
                                ));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error getting taxable chunks: {}", e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Get the state ID for a city
     */
    public static UUID getStateIdForCity(UUID cityId) {
        if (!initialized || cityId == null) return null;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getCity = managerClass.getMethod("getCity", UUID.class);
            var city = getCity.invoke(manager, cityId);

            if (city != null) {
                var cityClass = Class.forName("com.statecraft.core.City");
                var getStateId = cityClass.getMethod("getStateId");
                return (UUID) getStateId.invoke(city);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting state ID for city: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the nation ID for a state
     */
    public static UUID getNationIdForState(UUID stateId) {
        if (!initialized || stateId == null) return null;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getState = managerClass.getMethod("getState", UUID.class);
            var state = getState.invoke(manager, stateId);

            if (state != null) {
                var stateClass = Class.forName("com.statecraft.core.State");
                var getNationId = stateClass.getMethod("getNationId");
                return (UUID) getNationId.invoke(state);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting nation ID for state: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Count nearby claimed chunks for demand calculation
     */
    public static int countNearbyClaimedChunks(net.minecraft.server.MinecraftServer server,
                                               int centerX, int centerZ, String dimension, int radius) {
        if (!initialized) return 0;

        int count = 0;
        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);
            var getClaimedChunk = managerClass.getMethod("getClaimedChunk",
                net.minecraft.world.level.ChunkPos.class, net.minecraft.resources.ResourceKey.class);

            // Get the dimension key
            net.minecraft.resources.ResourceKey<?> dimKey = null;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(dimension)) {
                    dimKey = level.dimension();
                    break;
                }
            }

            if (dimKey == null) return 0;

            // Count claimed chunks in radius
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip center chunk

                    net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(centerX + dx, centerZ + dz);
                    Object chunk = getClaimedChunk.invoke(manager, pos, dimKey);
                    if (chunk != null) {
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error counting nearby claims: {}", e.getMessage());
        }

        return count;
    }

    /**
     * Get the nation ID that owns a chunk, or null if not in a nation
     */
    public static java.util.UUID getChunkNationId(net.minecraft.server.MinecraftServer server,
                                                   int chunkX, int chunkZ, String dimension) {
        if (!initialized) return null;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            // Get the dimension key
            net.minecraft.resources.ResourceKey<?> dimKey = null;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(dimension)) {
                    dimKey = level.dimension();
                    break;
                }
            }

            if (dimKey == null) return null;

            // Get the claimed chunk
            var getClaimedChunk = managerClass.getMethod("getClaimedChunk",
                net.minecraft.world.level.ChunkPos.class, net.minecraft.resources.ResourceKey.class);
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
            Object chunk = getClaimedChunk.invoke(manager, pos, dimKey);

            if (chunk == null) return null;

            // Get city ID from chunk
            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getCityId = chunkClass.getMethod("getCityId");
            java.util.UUID cityId = (java.util.UUID) getCityId.invoke(chunk);

            if (cityId == null) return null;

            // Get city
            var getCity = managerClass.getMethod("getCity", java.util.UUID.class);
            Object city = getCity.invoke(manager, cityId);

            if (city == null) return null;

            // Get state ID from city
            var cityClass = Class.forName("com.statecraft.core.City");
            var getStateId = cityClass.getMethod("getStateId");
            java.util.UUID stateId = (java.util.UUID) getStateId.invoke(city);

            if (stateId == null) return null;

            // Get state
            var getState = managerClass.getMethod("getState", java.util.UUID.class);
            Object state = getState.invoke(manager, stateId);

            if (state == null) return null;

            // Get nation ID from state
            var stateClass = Class.forName("com.statecraft.core.State");
            var getNationId = stateClass.getMethod("getNationId");
            return (java.util.UUID) getNationId.invoke(state);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting chunk nation ID: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the nation's base chunk value for a chunk, or -1 if chunk is not in a nation
     * This allows nations to set their own base chunk value via legislation
     */
    public static double getNationBaseChunkValue(net.minecraft.server.MinecraftServer server,
                                                  int chunkX, int chunkZ, String dimension) {
        if (!initialized) {
            StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: not initialized");
            return -1;
        }

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            // Get the dimension key
            net.minecraft.resources.ResourceKey<?> dimKey = null;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(dimension)) {
                    dimKey = level.dimension();
                    break;
                }
            }

            if (dimKey == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: dimKey is null for {}", dimension);
                return -1;
            }

            // Get the claimed chunk
            var getClaimedChunk = managerClass.getMethod("getClaimedChunk",
                net.minecraft.world.level.ChunkPos.class, net.minecraft.resources.ResourceKey.class);
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
            Object chunk = getClaimedChunk.invoke(manager, pos, dimKey);

            if (chunk == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: chunk at ({}, {}) is not claimed", chunkX, chunkZ);
                return -1;
            }

            // Get city ID from chunk
            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getCityId = chunkClass.getMethod("getCityId");
            UUID cityId = (UUID) getCityId.invoke(chunk);

            if (cityId == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: chunk has no cityId");
                return -1;
            }

            // Get city
            var getCity = managerClass.getMethod("getCity", UUID.class);
            Object city = getCity.invoke(manager, cityId);

            if (city == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: city {} not found", cityId);
                return -1;
            }

            // Get state ID from city
            var cityClass = Class.forName("com.statecraft.core.City");
            var getStateId = cityClass.getMethod("getStateId");
            UUID stateId = (UUID) getStateId.invoke(city);

            if (stateId == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: city has no stateId");
                return -1;
            }

            // Get state
            var getState = managerClass.getMethod("getState", UUID.class);
            Object state = getState.invoke(manager, stateId);

            if (state == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: state {} not found", stateId);
                return -1;
            }

            // Get nation ID from state
            var stateClass = Class.forName("com.statecraft.core.State");
            var getNationId = stateClass.getMethod("getNationId");
            UUID nationId = (UUID) getNationId.invoke(state);

            if (nationId == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: state has no nationId");
                return -1;
            }

            // Get nation
            var getNation = managerClass.getMethod("getNation", UUID.class);
            Object nation = getNation.invoke(manager, nationId);

            if (nation == null) {
                StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: nation {} not found", nationId);
                return -1;
            }

            // Get base chunk value from nation
            var nationClass = Class.forName("com.statecraft.core.Nation");
            var getBaseChunkValue = nationClass.getMethod("getBaseChunkValue");
            double value = (Double) getBaseChunkValue.invoke(nation);
            StateCraftEconomy.LOGGER.debug("getNationBaseChunkValue: nation base chunk value = {}", value);
            return value;

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting nation base chunk value: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the government tax multiplier for a chunk (from city settings)
     */
    public static double getGovernmentTaxMultiplier(net.minecraft.server.MinecraftServer server,
                                                    int chunkX, int chunkZ, String dimension) {
        if (!initialized) return 1.0;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            // Get the dimension key
            net.minecraft.resources.ResourceKey<?> dimKey = null;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(dimension)) {
                    dimKey = level.dimension();
                    break;
                }
            }

            if (dimKey == null) return 1.0;

            // Get the claimed chunk
            var getClaimedChunk = managerClass.getMethod("getClaimedChunk",
                net.minecraft.world.level.ChunkPos.class, net.minecraft.resources.ResourceKey.class);
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
            Object chunk = getClaimedChunk.invoke(manager, pos, dimKey);

            if (chunk == null) return 1.0;

            // Get city ID
            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getCityId = chunkClass.getMethod("getCityId");
            UUID cityId = (UUID) getCityId.invoke(chunk);

            if (cityId == null) return 1.0;

            // Get city
            var getCity = managerClass.getMethod("getCity", UUID.class);
            Object city = getCity.invoke(manager, cityId);

            if (city == null) return 1.0;

            // Try to get tax multiplier from city (if method exists)
            var cityClass = Class.forName("com.statecraft.core.City");
            try {
                var getTaxMultiplier = cityClass.getMethod("getTaxMultiplier");
                return (Double) getTaxMultiplier.invoke(city);
            } catch (NoSuchMethodException e) {
                // Method doesn't exist yet, return default
                return 1.0;
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting government tax multiplier: {}", e.getMessage());
        }

        return 1.0;
    }

    // ==================== Mail Integration ====================

    /**
     * Send tax summary mail to a player
     */
    public static void sendPlayerTaxSummary(UUID playerId, int chunksOwned, double totalTaxPaid, double newBalance) {
        if (!initialized) return;

        try {
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            Object mailManager = getInstance.invoke(null);

            var sendMethod = mailManagerClass.getMethod("sendPlayerTaxSummary",
                UUID.class, int.class, double.class, double.class);
            sendMethod.invoke(mailManager, playerId, chunksOwned, totalTaxPaid, newBalance);

            StateCraftEconomy.LOGGER.debug("Sent tax summary mail to player {}", playerId);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not send tax summary mail: {}", e.getMessage());
        }
    }

    /**
     * Send tax warning mail to a player
     */
    public static void sendTaxWarning(UUID playerId, int chunkX, int chunkZ, int warningsRemaining, double balance) {
        if (!initialized) return;

        try {
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            Object mailManager = getInstance.invoke(null);

            var sendMethod = mailManagerClass.getMethod("sendTaxWarning",
                UUID.class, int.class, int.class, int.class, double.class);
            sendMethod.invoke(mailManager, playerId, chunkX, chunkZ, warningsRemaining, balance);

            StateCraftEconomy.LOGGER.debug("Sent tax warning mail to player {}", playerId);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not send tax warning mail: {}", e.getMessage());
        }
    }

    /**
     * Send repossession notice mail to a player
     */
    public static void sendRepossessionNotice(UUID playerId, int chunkX, int chunkZ, String cityName) {
        if (!initialized) return;

        try {
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            Object mailManager = getInstance.invoke(null);

            var sendMethod = mailManagerClass.getMethod("sendRepossessionNotice",
                UUID.class, int.class, int.class, String.class);
            sendMethod.invoke(mailManager, playerId, chunkX, chunkZ, cityName);

            StateCraftEconomy.LOGGER.debug("Sent repossession notice to player {}", playerId);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not send repossession notice: {}", e.getMessage());
        }
    }

    /**
     * Send tax revenue report to city
     */
    public static void sendCityTaxRevenue(UUID cityId, String cityName, double totalCollected,
                                          double keptAmount, double passedToState, int chunksProcessed) {
        if (!initialized) {
            StateCraftEconomy.LOGGER.warn("Cannot send city tax revenue - StateCraft not initialized");
            return;
        }

        try {
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            Object mailManager = getInstance.invoke(null);

            var sendMethod = mailManagerClass.getMethod("sendCityTaxRevenue",
                UUID.class, String.class, double.class, double.class, double.class, int.class);
            sendMethod.invoke(mailManager, cityId, cityName, totalCollected, keptAmount, passedToState, chunksProcessed);

            StateCraftEconomy.LOGGER.info("Sent tax revenue report to city {} - collected: ${}, kept: ${}", cityName, totalCollected, keptAmount);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Could not send city tax revenue mail: {}", e.getMessage(), e);
        }
    }

    /**
     * Send tax revenue report to state
     */
    public static void sendStateTaxRevenue(UUID stateId, String stateName, double totalReceived,
                                           double keptAmount, double passedToNation) {
        if (!initialized) {
            StateCraftEconomy.LOGGER.warn("Cannot send state tax revenue - StateCraft not initialized");
            return;
        }

        try {
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            Object mailManager = getInstance.invoke(null);

            var sendMethod = mailManagerClass.getMethod("sendStateTaxRevenue",
                UUID.class, String.class, double.class, double.class, double.class);
            sendMethod.invoke(mailManager, stateId, stateName, totalReceived, keptAmount, passedToNation);

            StateCraftEconomy.LOGGER.info("Sent tax revenue report to state {} - received: ${}, kept: ${}", stateName, totalReceived, keptAmount);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Could not send state tax revenue mail: {}", e.getMessage(), e);
        }
    }

    /**
     * Send tax revenue report to nation
     */
    public static void sendNationTaxRevenue(UUID nationId, String nationName, double totalReceived) {
        if (!initialized) {
            StateCraftEconomy.LOGGER.warn("Cannot send nation tax revenue - StateCraft not initialized");
            return;
        }

        try {
            Class<?> mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            Object mailManager = getInstance.invoke(null);

            var sendMethod = mailManagerClass.getMethod("sendNationTaxRevenue",
                UUID.class, String.class, double.class);
            sendMethod.invoke(mailManager, nationId, nationName, totalReceived);

            StateCraftEconomy.LOGGER.info("Sent tax revenue report to nation {} - received: ${}", nationName, totalReceived);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Could not send nation tax revenue mail: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the city's property tax rate for a chunk
     * @return Tax rate as a decimal (e.g., 0.05 for 5%), or 0 if not in a city
     */
    public static double getChunkCityTaxRate(net.minecraft.server.MinecraftServer server,
                                              int chunkX, int chunkZ, String dimension) {
        if (!initialized) return 0.0;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            // Get the dimension key
            net.minecraft.resources.ResourceKey<?> dimKey = null;
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (level.dimension().location().toString().equals(dimension)) {
                    dimKey = level.dimension();
                    break;
                }
            }

            if (dimKey == null) return 0.0;

            // Get the claimed chunk
            var getClaimedChunk = managerClass.getMethod("getClaimedChunk",
                net.minecraft.world.level.ChunkPos.class, net.minecraft.resources.ResourceKey.class);
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
            Object chunk = getClaimedChunk.invoke(manager, pos, dimKey);

            if (chunk == null) return 0.0;

            // Get city ID from chunk
            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getCityId = chunkClass.getMethod("getCityId");
            UUID cityId = (UUID) getCityId.invoke(chunk);

            if (cityId == null) return 0.0;

            // Get city
            var getCity = managerClass.getMethod("getCity", UUID.class);
            Object city = getCity.invoke(manager, cityId);

            if (city == null) return 0.0;

            // Get tax rate from city
            var cityClass = Class.forName("com.statecraft.core.City");
            var getTaxRate = cityClass.getMethod("getTaxRate");
            return (Double) getTaxRate.invoke(city);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting city tax rate: {}", e.getMessage());
        }

        return 0.0;
    }

    /**
     * Get a city's property tax rate by city ID
     * @return Tax rate as decimal (e.g., 0.05 for 5%), or -1 if not found
     */
    public static double getCityTaxRate(UUID cityId) {
        if (!initialized || cityId == null) return -1;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getCity = managerClass.getMethod("getCity", UUID.class);
            Object city = getCity.invoke(manager, cityId);

            if (city == null) return -1;

            var cityClass = Class.forName("com.statecraft.core.City");
            var getTaxRate = cityClass.getMethod("getTaxRate");
            return (Double) getTaxRate.invoke(city);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting city tax rate: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get a state's passthrough rate (portion of city revenue passed to state)
     * @return Passthrough rate as decimal (e.g., 0.20 for 20%), or -1 if not found
     */
    public static double getStatePassthroughRate(UUID stateId) {
        if (!initialized || stateId == null) return -1;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getState = managerClass.getMethod("getState", UUID.class);
            Object state = getState.invoke(manager, stateId);

            if (state == null) return -1;

            var stateClass = Class.forName("com.statecraft.core.State");
            var getPassthroughRate = stateClass.getMethod("getPropertyTaxPassthrough");
            return (Double) getPassthroughRate.invoke(state);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting state passthrough rate: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get a nation's passthrough rate (portion of state revenue passed to nation)
     * @return Passthrough rate as decimal (e.g., 0.20 for 20%), or -1 if not found
     */
    public static double getNationPassthroughRate(UUID nationId) {
        if (!initialized || nationId == null) return -1;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getNation = managerClass.getMethod("getNation", UUID.class);
            Object nation = getNation.invoke(manager, nationId);

            if (nation == null) return -1;

            var nationClass = Class.forName("com.statecraft.core.Nation");
            var getPassthroughRate = nationClass.getMethod("getPropertyTaxPassthrough");
            return (Double) getPassthroughRate.invoke(nation);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting nation passthrough rate: {}", e.getMessage());
        }

        return -1;
    }

    // ==================== Company Integration ====================

    /**
     * Get the city ID at the player's current chunk position.
     */
    public static UUID getPlayerCityId(net.minecraft.server.level.ServerPlayer player) {
        if (!initialized) return null;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            int chunkX = player.blockPosition().getX() >> 4;
            int chunkZ = player.blockPosition().getZ() >> 4;
            String dimension = player.level().dimension().location().toString();

            var getClaim = managerClass.getMethod("getClaimedChunk", String.class, int.class, int.class);
            Object chunk = getClaim.invoke(manager, dimension, chunkX, chunkZ);

            if (chunk != null) {
                var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
                var getCityId = chunkClass.getMethod("getCityId");
                return (UUID) getCityId.invoke(chunk);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting player city ID: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the nation ID for a city (via its state).
     */
    public static UUID getNationIdForCity(UUID cityId) {
        if (!initialized || cityId == null) return null;
        UUID stateId = getStateIdForCity(cityId);
        if (stateId == null) return null;
        return getNationIdForState(stateId);
    }

    /**
     * Send mail notification when a company dividend payout is deferred due to insufficient funds.
     */
    public static void sendCompanyDividendDeferredMail(UUID founderId, String companyName,
                                                        double balance, double requiredPayout) {
        if (!initialized) return;

        try {
            var mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            var mailManager = getInstance.invoke(null);

            var mailTypeClass = Class.forName("com.statecraft.mail.Mail$MailType");
            // Use SYSTEM or FINANCIAL type — try SYSTEM first as it's guaranteed to exist
            Object mailType = null;
            for (Object constant : mailTypeClass.getEnumConstants()) {
                if ("SYSTEM".equals(constant.toString()) || "FINANCIAL".equals(constant.toString())) {
                    mailType = constant;
                    break;
                }
            }
            if (mailType == null) {
                mailType = mailTypeClass.getEnumConstants()[0]; // Fallback to first enum
            }

            String subject = "Dividend Deferred — " + companyName;
            String body = String.format(
                "§eThe scheduled dividend payout for §f%s §ehas been deferred.\n\n" +
                "§7Company Balance: §f$%,.2f\n" +
                "§7Required Payout: §c$%,.2f\n\n" +
                "§7The company does not have sufficient funds to cover the full dividend.\n" +
                "§7Dividends will be attempted again next cycle.",
                companyName, balance, requiredPayout);

            var sendSystemMail = mailManagerClass.getMethod("sendSystemMail",
                UUID.class, mailTypeClass, String.class, String.class);
            sendSystemMail.invoke(mailManager, founderId, mailType, subject, body);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error sending company dividend deferred mail: {}", e.getMessage());
        }
    }

    /**
     * Attempt to repossess a player's most valuable chunk to cover a loan default.
     * Uses the same transferChunkToGovernment mechanism as tax repossession.
     *
     * @param playerId The player whose chunk to repossess
     * @param loanAmount The outstanding loan amount
     * @param bankName The name of the bank repossessing
     * @return true if a chunk was successfully repossessed
     */
    public static boolean repossessPlayerChunkForLoan(UUID playerId, double loanAmount, String bankName) {
        if (!initialized) return false;

        try {
            // Find the player's chunks and pick one to repossess
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getPlayerChunks = managerClass.getMethod("getPlayerOwnedChunks", UUID.class);
            @SuppressWarnings("unchecked")
            var chunks = (java.util.Collection<?>) getPlayerChunks.invoke(manager, playerId);

            if (chunks == null || chunks.isEmpty()) return false;

            // Pick the first chunk (simplification — future improvement: pick most valuable)
            var firstChunk = chunks.iterator().next();
            var chunkClass = firstChunk.getClass();
            var getChunkPos = chunkClass.getMethod("getChunkPos");
            var getDimension = chunkClass.getMethod("getDimension");

            Object chunkPos = getChunkPos.invoke(firstChunk);
            Object dimension = getDimension.invoke(firstChunk);

            var chunkPosClass = Class.forName("net.minecraft.world.level.ChunkPos");
            int chunkX = (int) chunkPosClass.getField("x").get(chunkPos);
            int chunkZ = (int) chunkPosClass.getField("z").get(chunkPos);

            net.minecraft.resources.ResourceKey<?> dimKey = (net.minecraft.resources.ResourceKey<?>) dimension;

            boolean success = transferChunkToGovernment(chunkX, chunkZ, dimKey);

            if (success) {
                // Send mail notification
                sendRepossessionNotice(playerId, chunkX, chunkZ, bankName + " (loan default)");
            }

            return success;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error repossessing chunk for loan default: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the nation's reserve ratio policy for a city. Returns the national legislature-mandated
     * minimum reserve ratio, or 0 if no policy exists.
     *
     * @param cityId The city ID (to look up the nation)
     * @return The national minimum reserve ratio (0.0 - 1.0), or 0 if no policy
     */
    public static double getNationReserveRatioPolicy(UUID cityId) {
        if (!initialized) return 0;

        try {
            UUID nationId = getNationIdForCity(cityId);
            if (nationId == null) return 0;

            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getNation = managerClass.getMethod("getNation", UUID.class);
            var nation = getNation.invoke(manager, nationId);
            if (nation == null) return 0;

            var nationClass = Class.forName("com.statecraft.core.Nation");
            var getLegislature = nationClass.getMethod("getLegislature");
            var legislature = getLegislature.invoke(nation);
            if (legislature == null) return 0;

            var legClass = legislature.getClass();
            var getPassedPolicies = legClass.getMethod("getPassedPolicies");
            @SuppressWarnings("unchecked")
            var policies = (java.util.Collection<?>) getPassedPolicies.invoke(legislature);
            if (policies == null) return 0;

            for (Object policy : policies) {
                var policyClass = policy.getClass();
                var getType = policyClass.getMethod("getType");
                var policyType = getType.invoke(policy);
                if (policyType.toString().equals("RESERVE_RATIO")) {
                    var getValue = policyClass.getMethod("getValue");
                    return ((Number) getValue.invoke(policy)).doubleValue();
                }
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error getting nation reserve ratio policy: {}", e.getMessage());
        }
        return 0;
    }
}
