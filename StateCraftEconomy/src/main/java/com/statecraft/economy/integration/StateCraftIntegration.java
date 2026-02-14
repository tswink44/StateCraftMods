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
public class StateCraftIntegration {

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;

        try {
            // Verify StateCraft classes are available
            Class.forName("com.statecraft.core.ChunkClaimManager");
            Class.forName("com.statecraft.core.Nation");
            initialized = true;
            StateCraftEconomy.LOGGER.info("StateCraft integration initialized successfully");
        } catch (ClassNotFoundException e) {
            StateCraftEconomy.LOGGER.warn("StateCraft classes not found, integration disabled");
            initialized = false;
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
}
