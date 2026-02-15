package com.statecraft.economy.integration;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
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
                        case "formatCurrency" -> { return economyIntegrationImpl.formatCurrency((Double) args[0]); }
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

        public String formatCurrency(double amount) {
            EconomyManager manager = EconomyManager.getInstance();
            if (manager != null) {
                return manager.formatCurrency(amount);
            }
            return String.format("$%.2f", amount);
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
     * Check if player is nation admin
     */
    public static boolean isNationAdmin(ServerPlayer player) {
        if (!initialized) return false;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getPlayerNation = managerClass.getMethod("getPlayerNation", UUID.class);
            var nation = getPlayerNation.invoke(manager, player.getUUID());

            if (nation != null) {
                var nationClass = Class.forName("com.statecraft.core.Nation");
                var isAdmin = nationClass.getMethod("isAdmin", UUID.class);
                return (Boolean) isAdmin.invoke(nation, player.getUUID());
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Error checking nation admin: {}", e.getMessage());
        }
        return false;
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
                var isAdmin = nationClass.getMethod("isAdmin", UUID.class);
                var getId = nationClass.getMethod("getId");
                var getName = nationClass.getMethod("getName");
                UUID nationId = (UUID) getId.invoke(nation);
                String nationName = (String) getName.invoke(nation);
                boolean isNationAdmin = (Boolean) isAdmin.invoke(nation, playerUUID);

                // Add nation account if player is admin
                if (isNationAdmin) {
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

                            // Add state account if player is governor or nation admin
                            if (isGovernor || isNationAdmin) {
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

                                        // Add city account if player is mayor, governor of parent state, or nation admin
                                        if (isMayor || isGovernorForCity || isNationAdmin) {
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
                // Only admins can withdraw
                if (!isNationAdmin(player)) {
                    NetworkHandler.sendToPlayer(new TransactionResultPacket(false,
                        "Only nation admins can withdraw from treasury", manager.getBalance(player.getUUID())), player);
                    return;
                }
                TransactionResult result = manager.withdrawFromNation(nationId, player.getUUID(), amount, "Nation treasury withdrawal");
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
                    var isAdmin = nationClass.getMethod("isAdmin", UUID.class);
                    return (Boolean) isAdmin.invoke(nation, playerId);
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

                                String dimStr = dimension.toString();

                                result.add(new com.statecraft.economy.core.TaxationManager.ChunkTaxInfo(
                                    x, z, dimStr,
                                    (net.minecraft.resources.ResourceKey<?>) dimension,
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
     * Get the nation's base chunk value for a chunk, or -1 if chunk is not in a nation
     * This allows nations to set their own base chunk value via legislation
     */
    public static double getNationBaseChunkValue(net.minecraft.server.MinecraftServer server,
                                                  int chunkX, int chunkZ, String dimension) {
        if (!initialized) return -1;

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

            if (dimKey == null) return -1;

            // Get the claimed chunk
            var getClaimedChunk = managerClass.getMethod("getClaimedChunk",
                net.minecraft.world.level.ChunkPos.class, net.minecraft.resources.ResourceKey.class);
            net.minecraft.world.level.ChunkPos pos = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
            Object chunk = getClaimedChunk.invoke(manager, pos, dimKey);

            if (chunk == null) return -1;

            // Get city ID from chunk
            var chunkClass = Class.forName("com.statecraft.core.ClaimedChunk");
            var getCityId = chunkClass.getMethod("getCityId");
            UUID cityId = (UUID) getCityId.invoke(chunk);

            if (cityId == null) return -1;

            // Get city
            var getCity = managerClass.getMethod("getCity", UUID.class);
            Object city = getCity.invoke(manager, cityId);

            if (city == null) return -1;

            // Get state ID from city
            var cityClass = Class.forName("com.statecraft.core.City");
            var getStateId = cityClass.getMethod("getStateId");
            UUID stateId = (UUID) getStateId.invoke(city);

            if (stateId == null) return -1;

            // Get state
            var getState = managerClass.getMethod("getState", UUID.class);
            Object state = getState.invoke(manager, stateId);

            if (state == null) return -1;

            // Get nation ID from state
            var stateClass = Class.forName("com.statecraft.core.State");
            var getNationId = stateClass.getMethod("getNationId");
            UUID nationId = (UUID) getNationId.invoke(state);

            if (nationId == null) return -1;

            // Get nation
            var getNation = managerClass.getMethod("getNation", UUID.class);
            Object nation = getNation.invoke(manager, nationId);

            if (nation == null) return -1;

            // Get base chunk value from nation
            var nationClass = Class.forName("com.statecraft.core.Nation");
            var getBaseChunkValue = nationClass.getMethod("getBaseChunkValue");
            return (Double) getBaseChunkValue.invoke(nation);

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
}

