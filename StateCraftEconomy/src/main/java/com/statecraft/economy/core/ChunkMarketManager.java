package com.statecraft.economy.core;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages chunk buying and selling operations
 * Integrates with StateCraft for chunk ownership and Economy for transactions
 */
public class ChunkMarketManager {
    private static ChunkMarketManager instance;

    private ChunkMarketManager() {}

    public static ChunkMarketManager getInstance() {
        if (instance == null) {
            instance = new ChunkMarketManager();
        }
        return instance;
    }

    /**
     * Result of a market operation
     */
    public record MarketResult(boolean success, String message) {}

    /**
     * Check if a player can list a chunk for sale
     * @param player The player attempting to list
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return Result with success status and message
     */
    public MarketResult canListChunkForSale(ServerPlayer player, int chunkX, int chunkZ) {
        if (!StateCraftIntegration.isInitialized()) {
            return new MarketResult(false, "StateCraft integration not available");
        }

        try {
            // Get chunk info via reflection
            var chunkInfo = StateCraftIntegration.getChunkInfo(chunkX, chunkZ, player.level().dimension());
            if (chunkInfo == null) {
                return new MarketResult(false, "Chunk is not claimed - must be claimed by a city first");
            }

            UUID playerId = player.getUUID();

            // Check if chunk is already for sale
            if (chunkInfo.isForSale()) {
                return new MarketResult(false, "Chunk is already listed for sale");
            }

            // If privately owned, only the owner can sell
            if (chunkInfo.isPrivatelyOwned()) {
                if (!playerId.equals(chunkInfo.ownerId())) {
                    return new MarketResult(false, "Only the owner can sell this chunk");
                }
                return new MarketResult(true, "You can list your chunk for sale");
            }

            // Government-owned chunk - check if player is city/state/nation official
            if (!StateCraftIntegration.canManageChunk(player, chunkX, chunkZ)) {
                return new MarketResult(false, "You must be a city, state, or nation official to sell government land");
            }

            return new MarketResult(true, "You can list this government chunk for sale");

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error checking chunk sale permissions", e);
            return new MarketResult(false, "Error checking permissions");
        }
    }

    /**
     * List a chunk for sale
     */
    public MarketResult listChunkForSale(ServerPlayer player, int chunkX, int chunkZ, double price) {
        if (price <= 0) {
            return new MarketResult(false, "Price must be greater than 0");
        }

        MarketResult canSell = canListChunkForSale(player, chunkX, chunkZ);
        if (!canSell.success()) {
            return canSell;
        }

        try {
            boolean success = StateCraftIntegration.setChunkForSale(chunkX, chunkZ, player.level().dimension(),
                price, player.getUUID());

            if (success) {
                String priceStr = EconomyManager.getInstance().formatCurrency(price);
                return new MarketResult(true, "Chunk listed for sale at " + priceStr);
            } else {
                return new MarketResult(false, "Failed to list chunk for sale");
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error listing chunk for sale", e);
            return new MarketResult(false, "Error listing chunk: " + e.getMessage());
        }
    }

    /**
     * Remove a chunk from sale
     */
    public MarketResult removeChunkFromSale(ServerPlayer player, int chunkX, int chunkZ) {
        MarketResult canSell = canListChunkForSale(player, chunkX, chunkZ);
        // Note: canSell may fail because it's already for sale, which is fine for removal

        try {
            var chunkInfo = StateCraftIntegration.getChunkInfo(chunkX, chunkZ, player.level().dimension());
            if (chunkInfo == null) {
                return new MarketResult(false, "Chunk is not claimed");
            }

            if (!chunkInfo.isForSale()) {
                return new MarketResult(false, "Chunk is not listed for sale");
            }

            // Check if player is the seller or has management rights
            UUID playerId = player.getUUID();
            if (!playerId.equals(chunkInfo.sellerId()) && !StateCraftIntegration.canManageChunk(player, chunkX, chunkZ)) {
                return new MarketResult(false, "You don't have permission to remove this listing");
            }

            boolean success = StateCraftIntegration.removeChunkFromSale(chunkX, chunkZ, player.level().dimension());

            if (success) {
                return new MarketResult(true, "Chunk removed from sale");
            } else {
                return new MarketResult(false, "Failed to remove chunk from sale");
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error removing chunk from sale", e);
            return new MarketResult(false, "Error removing listing: " + e.getMessage());
        }
    }

    /**
     * Purchase a chunk
     * Checks bank account first, then inventory for currency
     */
    public MarketResult purchaseChunk(ServerPlayer buyer, int chunkX, int chunkZ) {
        if (!StateCraftIntegration.isInitialized()) {
            return new MarketResult(false, "StateCraft integration not available");
        }

        try {
            var chunkInfo = StateCraftIntegration.getChunkInfo(chunkX, chunkZ, buyer.level().dimension());
            if (chunkInfo == null) {
                return new MarketResult(false, "Chunk is not claimed");
            }

            if (!chunkInfo.isForSale()) {
                return new MarketResult(false, "Chunk is not for sale");
            }

            double price = chunkInfo.salePrice();
            UUID buyerId = buyer.getUUID();
            UUID sellerId = chunkInfo.sellerId();

            // Can't buy your own privately-owned chunk
            // But government officials CAN buy government chunks they listed for sale
            // (they listed it as an official, but want to buy it personally)
            if (buyerId.equals(sellerId) && chunkInfo.isPrivatelyOwned()) {
                return new MarketResult(false, "You cannot buy your own chunk");
            }

            // Check if buyer has reached their personal chunk limit
            if (!StateCraftIntegration.canPlayerOwnMoreChunks(buyerId)) {
                return new MarketResult(false, "You have reached your personal chunk ownership limit");
            }

            // Check if buyer can afford it
            EconomyManager ecoManager = EconomyManager.getInstance();
            double bankBalance = ecoManager.getBalance(buyerId);
            double inventoryCurrency = calculateInventoryCurrency(buyer);
            double totalFunds = bankBalance + inventoryCurrency;

            if (totalFunds < price) {
                String needed = ecoManager.formatCurrency(price);
                String have = ecoManager.formatCurrency(totalFunds);
                return new MarketResult(false, "Insufficient funds. Need " + needed + ", have " + have);
            }

            // Process payment - use bank first, then inventory
            double remaining = price;

            // Deduct from bank account first
            if (bankBalance > 0) {
                double fromBank = Math.min(bankBalance, remaining);
                TransactionResult bankResult = ecoManager.withdraw(buyerId, fromBank, "Chunk purchase at (" + chunkX + ", " + chunkZ + ")");
                if (bankResult.isSuccess()) {
                    remaining -= fromBank;
                }
            }

            // If still need more, take from inventory
            if (remaining > 0) {
                boolean inventorySuccess = removeCurrencyFromInventory(buyer, remaining);
                if (!inventorySuccess) {
                    // Refund what was taken from bank
                    double refund = price - remaining;
                    if (refund > 0) {
                        ecoManager.deposit(buyerId, refund, "Refund - chunk purchase failed");
                    }
                    return new MarketResult(false, "Failed to collect payment from inventory");
                }
            }

            // Pay the seller
            if (sellerId != null) {
                // Determine where payment goes
                if (chunkInfo.isPrivatelyOwned()) {
                    // Pay the player owner
                    ecoManager.deposit(sellerId, price, "Chunk sale at (" + chunkX + ", " + chunkZ + ")");
                } else {
                    // Pay the city treasury
                    UUID cityId = chunkInfo.cityId();
                    if (cityId != null) {
                        var cityTreasury = ecoManager.getOrCreateCityTreasury(cityId);
                        cityTreasury.add(price);
                        ecoManager.markDirty();
                    }
                }
            }

            // Transfer ownership
            StateCraftEconomy.LOGGER.info("Attempting ownership transfer for chunk ({}, {}) to player {}",
                chunkX, chunkZ, buyerId);
            boolean transferSuccess = StateCraftIntegration.transferChunkOwnership(
                chunkX, chunkZ, buyer.level().dimension(), buyerId);
            StateCraftEconomy.LOGGER.info("Transfer result: {}", transferSuccess);

            if (!transferSuccess) {
                // Refund the buyer - this shouldn't happen but handle it
                StateCraftEconomy.LOGGER.error("Chunk ownership transfer failed! Refunding buyer.");
                ecoManager.deposit(buyerId, price, "Refund - ownership transfer failed");
                return new MarketResult(false, "Failed to transfer ownership");
            }

            String priceStr = ecoManager.formatCurrency(price);
            return new MarketResult(true, "Successfully purchased chunk for " + priceStr);

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error purchasing chunk", e);
            return new MarketResult(false, "Error during purchase: " + e.getMessage());
        }
    }

    /**
     * Calculate total currency value in player's inventory
     */
    private double calculateInventoryCurrency(ServerPlayer player) {
        EconomyManager manager = EconomyManager.getInstance();
        double total = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                total += manager.getCurrencyValue(stack);
            }
        }

        return total;
    }

    /**
     * Remove currency items from inventory to cover an amount
     * @return true if successful
     */
    private boolean removeCurrencyFromInventory(ServerPlayer player, double amount) {
        EconomyManager manager = EconomyManager.getInstance();
        double remaining = amount;

        // Collect items to remove (we'll remove them after calculating)
        List<int[]> toRemove = new ArrayList<>(); // [slot, count]

        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && manager.isCurrency(stack)) {
                double valuePerItem = manager.getCurrencyValue(stack.getItem());
                if (valuePerItem > 0) {
                    int needed = (int) Math.ceil(remaining / valuePerItem);
                    int toTake = Math.min(needed, stack.getCount());

                    toRemove.add(new int[]{i, toTake});
                    remaining -= toTake * valuePerItem;
                }
            }
        }

        if (remaining > 0.001) { // Small epsilon for floating point
            return false; // Not enough currency
        }

        // Actually remove the items
        for (int[] removal : toRemove) {
            ItemStack stack = player.getInventory().getItem(removal[0]);
            stack.shrink(removal[1]);
        }

        return true;
    }
}

