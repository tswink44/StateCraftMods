package com.statecraft.economy.core;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Manages the taxation system for StateCraft
 *
 * Tax Flow:
 * 1. Chunk owners pay property tax to the city their chunk is in
 * 2. Cities pass through a percentage to their state
 * 3. States pass through a percentage to their nation
 *
 * Special Rules:
 * - Taxes can make accounts go negative
 * - 3 consecutive negative balance tax events = chunk repossession
 */
public class TaxationManager {
    private static TaxationManager instance;

    // Default tax rates (can be customized per entity)
    private static final double DEFAULT_CITY_TAX_RATE = 0.05;      // 5% of chunk value per tax period
    private static final double DEFAULT_STATE_PASSTHROUGH = 0.20;   // 20% of city revenue to state
    private static final double DEFAULT_NATION_PASSTHROUGH = 0.20;  // 20% of state revenue to nation

    // Tax period in game ticks (default: 1 real hour = 72000 ticks)
    private static final long DEFAULT_TAX_PERIOD_TICKS = 72000;

    // Chunk base value for taxation
    private static final double CHUNK_BASE_VALUE = 100.0;

    // Consecutive negative balance threshold for repossession
    private static final int REPOSSESSION_THRESHOLD = 3;

    // Track negative balance counts per player
    // Key: "playerUUID:chunkX:chunkZ:dimension" -> consecutive negative count
    private final Map<String, Integer> negativeBalanceCounts = new HashMap<>();

    // Last tax collection time
    private long lastTaxCollection = 0;
    private long taxPeriodTicks = DEFAULT_TAX_PERIOD_TICKS;

    // Tax collection enabled flag
    private boolean enabled = true;

    private TaxationManager() {}

    public static TaxationManager getInstance() {
        if (instance == null) {
            instance = new TaxationManager();
        }
        return instance;
    }

    /**
     * Called every server tick to check if taxes should be collected
     */
    public void tick(MinecraftServer server) {
        if (!enabled) return;

        long currentTime = server.overworld().getGameTime();

        if (currentTime - lastTaxCollection >= taxPeriodTicks) {
            collectAllTaxes(server);
            lastTaxCollection = currentTime;
        }
    }

    /**
     * Collect taxes from all chunk owners
     */
    public void collectAllTaxes(MinecraftServer server) {
        if (!StateCraftIntegration.isInitialized()) {
            StateCraftEconomy.LOGGER.debug("Skipping tax collection - StateCraft not initialized");
            return;
        }

        StateCraftEconomy.LOGGER.info("Beginning tax collection cycle...");

        TaxCollectionResult result = new TaxCollectionResult();

        // Track per-player tax totals for mail summaries
        Map<UUID, PlayerTaxSummary> playerTaxSummaries = new HashMap<>();

        try {
            // Get all claimed chunks via integration
            List<ChunkTaxInfo> taxableChunks = StateCraftIntegration.getAllTaxableChunks(server);

            // Group by city for efficient processing
            Map<UUID, List<ChunkTaxInfo>> chunksByCity = new HashMap<>();
            for (ChunkTaxInfo chunk : taxableChunks) {
                if (chunk.isPrivatelyOwned() && chunk.getOwnerId() != null) {
                    chunksByCity.computeIfAbsent(chunk.getCityId(), k -> new ArrayList<>()).add(chunk);
                }
            }

            // Track city info for mail
            Map<UUID, CityTaxSummary> cityTaxSummaries = new HashMap<>();

            // Process each city's chunks
            Map<UUID, Double> cityRevenues = new HashMap<>();

            for (Map.Entry<UUID, List<ChunkTaxInfo>> entry : chunksByCity.entrySet()) {
                UUID cityId = entry.getKey();
                List<ChunkTaxInfo> chunks = entry.getValue();

                double cityTaxRate = getTaxRateForCity(cityId);
                StateCraftEconomy.LOGGER.info("Processing city {} with tax rate: {} ({}%)",
                    cityId, cityTaxRate, cityTaxRate * 100);
                double cityRevenue = 0;
                int cityChunksProcessed = 0;

                for (ChunkTaxInfo chunk : chunks) {
                    double taxAmount = calculateChunkTax(chunk, cityTaxRate);
                    UUID ownerId = chunk.getOwnerId();

                    // Collect tax from owner
                    TaxResult taxResult = collectTaxFromPlayer(server, ownerId, taxAmount, chunk);

                    // Track player summary
                    playerTaxSummaries.computeIfAbsent(ownerId, k -> new PlayerTaxSummary())
                        .addTax(taxAmount);

                    if (taxResult.collected) {
                        cityRevenue += taxResult.amountCollected;
                        result.totalCollected += taxResult.amountCollected;
                        result.chunksProcessed++;
                        cityChunksProcessed++;

                        // Reset negative count on successful payment
                        resetNegativeCount(chunk);
                    } else if (taxResult.wentNegative) {
                        cityRevenue += taxResult.amountCollected; // Still collect even if negative
                        result.totalCollected += taxResult.amountCollected;
                        result.chunksProcessed++;
                        result.negativeBalances++;
                        cityChunksProcessed++;

                        // Track negative balance
                        int negCount = incrementNegativeCount(chunk);

                        if (negCount >= REPOSSESSION_THRESHOLD) {
                            // Repossess the chunk
                            repossessChunk(server, chunk, ownerId);
                            result.chunksRepossessed++;
                            resetNegativeCount(chunk);
                        } else {
                            // Warn the player via mail
                            warnPlayerAboutNegativeBalance(server, ownerId, chunk, negCount);
                        }
                    }
                }

                cityRevenues.put(cityId, cityRevenue);
                cityTaxSummaries.put(cityId, new CityTaxSummary(cityRevenue, cityChunksProcessed));
            }

            // Deposit city revenues and process pass-through
            Map<UUID, Double> stateRevenues = new HashMap<>();
            Map<UUID, StateTaxSummary> stateTaxSummaries = new HashMap<>();

            StateCraftEconomy.LOGGER.info("Processing {} cities for tax revenue mail", cityRevenues.size());

            for (Map.Entry<UUID, Double> entry : cityRevenues.entrySet()) {
                UUID cityId = entry.getKey();
                double revenue = entry.getValue();

                StateCraftEconomy.LOGGER.info("City {} has revenue: ${}", cityId, revenue);

                if (revenue <= 0) continue;

                // Get pass-through rate for this city's state
                UUID stateId = StateCraftIntegration.getStateIdForCity(cityId);
                double statePassthrough = getStatePassthroughRate(stateId);

                double toState = revenue * statePassthrough;
                double cityKeeps = revenue - toState;

                StateCraftEconomy.LOGGER.info("City keeps: ${}, passes to state: ${}", cityKeeps, toState);

                // Deposit to city treasury
                depositToCityTreasury(cityId, cityKeeps);

                // Send city tax revenue mail
                CityTaxSummary citySummary = cityTaxSummaries.get(cityId);
                String cityName = StateCraftIntegration.getCityName(cityId);
                if (cityName == null) cityName = "Unknown City";
                StateCraftEconomy.LOGGER.info("Sending tax revenue mail to city: {} (ID: {})", cityName, cityId);
                StateCraftIntegration.sendCityTaxRevenue(cityId, cityName, revenue, cityKeeps, toState,
                    citySummary != null ? citySummary.chunksProcessed : 0);

                // Accumulate state revenue
                if (stateId != null) {
                    stateRevenues.merge(stateId, toState, Double::sum);
                    stateTaxSummaries.computeIfAbsent(stateId, k -> new StateTaxSummary()).addRevenue(toState);
                }
            }

            // Process state revenues and nation pass-through
            Map<UUID, Double> nationRevenues = new HashMap<>();

            for (Map.Entry<UUID, Double> entry : stateRevenues.entrySet()) {
                UUID stateId = entry.getKey();
                double revenue = entry.getValue();

                if (revenue <= 0) continue;

                UUID nationId = StateCraftIntegration.getNationIdForState(stateId);
                double nationPassthrough = getNationPassthroughRate(nationId);

                double toNation = revenue * nationPassthrough;
                double stateKeeps = revenue - toNation;

                // Deposit to state treasury
                depositToStateTreasury(stateId, stateKeeps);

                // Send state tax revenue mail
                String stateName = StateCraftIntegration.getStateName(stateId);
                if (stateName == null) stateName = "Unknown State";
                StateCraftIntegration.sendStateTaxRevenue(stateId, stateName, revenue, stateKeeps, toNation);

                // Deposit to nation treasury
                if (nationId != null && toNation > 0) {
                    depositToNationTreasury(nationId, toNation);
                    nationRevenues.merge(nationId, toNation, Double::sum);
                }
            }

            // Send nation tax revenue mails
            for (Map.Entry<UUID, Double> entry : nationRevenues.entrySet()) {
                UUID nationId = entry.getKey();
                double totalReceived = entry.getValue();
                String nationName = StateCraftIntegration.getNationName(nationId);
                if (nationName == null) nationName = "Unknown Nation";
                StateCraftIntegration.sendNationTaxRevenue(nationId, nationName, totalReceived);
            }

            // Send player tax summary mails
            EconomyManager ecoManager = EconomyManager.getInstance();
            for (Map.Entry<UUID, PlayerTaxSummary> entry : playerTaxSummaries.entrySet()) {
                UUID playerId = entry.getKey();
                PlayerTaxSummary summary = entry.getValue();
                double newBalance = ecoManager.getBalance(playerId);
                StateCraftIntegration.sendPlayerTaxSummary(playerId, summary.chunksOwned, summary.totalTaxPaid, newBalance);
            }

            result.success = true;

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error during tax collection", e);
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        logTaxCollectionResult(result);
    }

    // Helper class to track per-player tax totals
    private static class PlayerTaxSummary {
        int chunksOwned = 0;
        double totalTaxPaid = 0;

        void addTax(double amount) {
            chunksOwned++;
            totalTaxPaid += amount;
        }
    }

    // Helper class to track per-city tax totals
    private static class CityTaxSummary {
        double totalCollected;
        int chunksProcessed;

        CityTaxSummary(double totalCollected, int chunksProcessed) {
            this.totalCollected = totalCollected;
            this.chunksProcessed = chunksProcessed;
        }
    }

    // Helper class to track per-state tax totals
    private static class StateTaxSummary {
        double totalReceived = 0;

        void addRevenue(double amount) {
            totalReceived += amount;
        }
    }

    /**
     * Calculate tax for a single chunk using the valuation system
     */
    private double calculateChunkTax(ChunkTaxInfo chunk, double taxRate) {
        // Use the valuation system to get the chunk's actual value
        com.statecraft.economy.valuation.ChunkValuationManager valuationManager =
            com.statecraft.economy.valuation.ChunkValuationManager.getInstance();

        com.statecraft.economy.valuation.ChunkValuation valuation =
            valuationManager.getValuation(chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension());

        double chunkValue = valuation.getTotalValue();
        double taxAmount = chunkValue * taxRate;

        StateCraftEconomy.LOGGER.info("Tax calculation for chunk ({}, {}) dim='{}': " +
            "base={}, loc={}, biome={}, demand={}, gov={}, imp={}x (score={}), total={}, rate={}, tax={}",
            chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension(),
            valuation.getBaseValue(), valuation.getLocationMultiplier(), valuation.getBiomeMultiplier(),
            valuation.getDemandMultiplier(), valuation.getGovernmentMultiplier(),
            valuation.getImprovementMultiplier(), valuation.getImprovementScore(),
            chunkValue, taxRate, taxAmount);

        return taxAmount;
    }

    /**
     * Collect tax from a player, allowing negative balance
     */
    private TaxResult collectTaxFromPlayer(MinecraftServer server, UUID playerId, double amount, ChunkTaxInfo chunk) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        double currentBalance = ecoManager.getBalance(playerId);

        TaxResult result = new TaxResult();
        result.amountCollected = amount;

        // Always withdraw, even if it goes negative
        TransactionResult txResult = ecoManager.withdraw(playerId, amount,
            "Property tax for chunk (" + chunk.getChunkX() + ", " + chunk.getChunkZ() + ")");

        if (txResult.isSuccess()) {
            result.collected = true;
            result.wentNegative = (currentBalance - amount) < 0;
        } else {
            // Force the withdrawal even with insufficient funds
            ecoManager.forceWithdraw(playerId, amount,
                "Property tax for chunk (" + chunk.getChunkX() + ", " + chunk.getChunkZ() + ")");
            result.collected = true;
            result.wentNegative = true;
        }

        return result;
    }

    /**
     * Get the unique key for tracking negative balances per chunk ownership
     */
    private String getChunkOwnerKey(ChunkTaxInfo chunk) {
        return chunk.getOwnerId() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ() + ":" + chunk.getDimension();
    }

    private int incrementNegativeCount(ChunkTaxInfo chunk) {
        String key = getChunkOwnerKey(chunk);
        int count = negativeBalanceCounts.getOrDefault(key, 0) + 1;
        negativeBalanceCounts.put(key, count);
        return count;
    }

    private void resetNegativeCount(ChunkTaxInfo chunk) {
        negativeBalanceCounts.remove(getChunkOwnerKey(chunk));
    }

    /**
     * Repossess a chunk from a player due to unpaid taxes
     */
    private void repossessChunk(MinecraftServer server, ChunkTaxInfo chunk, UUID formerOwnerId) {
        StateCraftEconomy.LOGGER.info("Repossessing chunk ({}, {}) from player {} due to unpaid taxes",
            chunk.getChunkX(), chunk.getChunkZ(), formerOwnerId);

        // Transfer ownership back to government (null owner = government owned)
        boolean success = StateCraftIntegration.transferChunkToGovernment(
            chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimensionKey());

        if (success) {
            // Get city name for the notification
            String cityName = StateCraftIntegration.getCityName(chunk.getCityId());
            if (cityName == null || cityName.isEmpty()) cityName = "Unknown City";

            // Send mail notification
            StateCraftIntegration.sendRepossessionNotice(formerOwnerId, chunk.getChunkX(), chunk.getChunkZ(), cityName);

            // Also notify the player directly if online
            ServerPlayer player = server.getPlayerList().getPlayer(formerOwnerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                    "§c§lTAX REPOSSESSION: §rYour chunk at (" + chunk.getChunkX() + ", " + chunk.getChunkZ() +
                    ") has been repossessed due to 3 consecutive tax periods with negative balance."
                ));
            }
        }
    }

    /**
     * Warn player about their negative balance status
     */
    private void warnPlayerAboutNegativeBalance(MinecraftServer server, UUID playerId, ChunkTaxInfo chunk, int negCount) {
        int remaining = REPOSSESSION_THRESHOLD - negCount;
        double balance = EconomyManager.getInstance().getBalance(playerId);

        // Send mail warning
        StateCraftIntegration.sendTaxWarning(playerId, chunk.getChunkX(), chunk.getChunkZ(), remaining, balance);

        // Also notify directly if online
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                "§e§lTAX WARNING: §rYou have a negative balance after paying taxes for chunk (" +
                chunk.getChunkX() + ", " + chunk.getChunkZ() + "). " +
                "§c" + remaining + "§r more negative tax period(s) and the chunk will be repossessed!"
            ));
        }
    }

    // Treasury deposit methods
    private void depositToCityTreasury(UUID cityId, double amount) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        var treasury = ecoManager.getOrCreateCityTreasury(cityId);
        treasury.add(amount);
        ecoManager.markDirty();
        StateCraftEconomy.LOGGER.debug("Deposited {} to city treasury {}", amount, cityId);
    }

    private void depositToStateTreasury(UUID stateId, double amount) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        var treasury = ecoManager.getOrCreateStateTreasury(stateId);
        treasury.add(amount);
        ecoManager.markDirty();
        StateCraftEconomy.LOGGER.debug("Deposited {} to state treasury {}", amount, stateId);
    }

    private void depositToNationTreasury(UUID nationId, double amount) {
        EconomyManager ecoManager = EconomyManager.getInstance();
        var treasury = ecoManager.getOrCreateNationTreasury(nationId);
        treasury.add(amount);
        ecoManager.markDirty();
        StateCraftEconomy.LOGGER.debug("Deposited {} to nation treasury {}", amount, nationId);
    }

    // Tax rate getters
    private double getTaxRateForCity(UUID cityId) {
        // Get the city's configured tax rate via StateCraft integration
        double rate = StateCraftIntegration.getCityTaxRate(cityId);
        if (rate < 0) {
            // Fallback to default if not configured
            return DEFAULT_CITY_TAX_RATE;
        }
        return rate;
    }

    private double getStatePassthroughRate(UUID stateId) {
        // Get the state's configured passthrough rate via StateCraft integration
        double rate = StateCraftIntegration.getStatePassthroughRate(stateId);
        if (rate < 0) {
            // Fallback to default if not configured
            return DEFAULT_STATE_PASSTHROUGH;
        }
        return rate;
    }

    private double getNationPassthroughRate(UUID nationId) {
        // Get the nation's configured passthrough rate via StateCraft integration
        double rate = StateCraftIntegration.getNationPassthroughRate(nationId);
        if (rate < 0) {
            // Fallback to default if not configured
            return DEFAULT_NATION_PASSTHROUGH;
        }
        return rate;
    }

    private void logTaxCollectionResult(TaxCollectionResult result) {
        if (result.success) {
            StateCraftEconomy.LOGGER.info("Tax collection complete: {} chunks processed, ${} collected, {} negative balances, {} repossessed",
                result.chunksProcessed, String.format("%.2f", result.totalCollected),
                result.negativeBalances, result.chunksRepossessed);
        } else {
            StateCraftEconomy.LOGGER.error("Tax collection failed: {}", result.errorMessage);
        }
    }

    // Configuration methods
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setTaxPeriodTicks(long ticks) {
        this.taxPeriodTicks = ticks;
    }

    public long getTaxPeriodTicks() {
        return taxPeriodTicks;
    }

    public void forceCollectNow(MinecraftServer server) {
        collectAllTaxes(server);
        lastTaxCollection = server.overworld().getGameTime();
    }

    /**
     * Get remaining ticks until next tax collection
     */
    public long getTicksUntilNextCollection(MinecraftServer server) {
        long currentTime = server.overworld().getGameTime();
        long elapsed = currentTime - lastTaxCollection;
        return Math.max(0, taxPeriodTicks - elapsed);
    }

    /**
     * Get the negative balance count for a specific chunk owner
     */
    public int getNegativeBalanceCount(UUID ownerId, int chunkX, int chunkZ, String dimension) {
        String key = ownerId + ":" + chunkX + ":" + chunkZ + ":" + dimension;
        return negativeBalanceCounts.getOrDefault(key, 0);
    }

    // Inner classes for results
    private static class TaxResult {
        boolean collected = false;
        boolean wentNegative = false;
        double amountCollected = 0;
    }

    private static class TaxCollectionResult {
        boolean success = false;
        int chunksProcessed = 0;
        double totalCollected = 0;
        int negativeBalances = 0;
        int chunksRepossessed = 0;
        String errorMessage = null;
    }

    /**
     * Data class for chunk tax information
     */
    public static class ChunkTaxInfo {
        private final int chunkX;
        private final int chunkZ;
        private final String dimension;
        private final UUID cityId;
        private final UUID ownerId;
        private final boolean privatelyOwned;
        private final double salePrice;
        private final net.minecraft.resources.ResourceKey<?> dimensionKey;

        public ChunkTaxInfo(int chunkX, int chunkZ, String dimension,
                          net.minecraft.resources.ResourceKey<?> dimensionKey,
                          UUID cityId, UUID ownerId, boolean privatelyOwned, double salePrice) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.dimensionKey = dimensionKey;
            this.cityId = cityId;
            this.ownerId = ownerId;
            this.privatelyOwned = privatelyOwned;
            this.salePrice = salePrice;
        }

        public int getChunkX() { return chunkX; }
        public int getChunkZ() { return chunkZ; }
        public String getDimension() { return dimension; }
        public net.minecraft.resources.ResourceKey<?> getDimensionKey() { return dimensionKey; }
        public UUID getCityId() { return cityId; }
        public UUID getOwnerId() { return ownerId; }
        public boolean isPrivatelyOwned() { return privatelyOwned; }
        public double getSalePrice() { return salePrice; }
    }
}

