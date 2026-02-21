package com.statecraft.economy.stockmarket;

import com.statecraft.company.Company;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all share listings on the stock market.
 * Handles listing, buying, cancelling, and persistence.
 *
 * Singleton — one instance per server.
 */
public class StockMarketManager {
    private static StockMarketManager instance;

    private final Map<UUID, ShareListing> listings = new ConcurrentHashMap<>();
    private boolean dirty = false;

    private StockMarketManager() {}

    public static StockMarketManager getInstance() {
        if (instance == null) {
            instance = new StockMarketManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Listing Management ====================

    /**
     * List shares for sale on the stock market.
     * The seller must own the shares (or be the company founder issuing new shares).
     *
     * @param sellerId The UUID of the seller
     * @param sellerName Display name of the seller
     * @param companyId The company whose shares are being sold
     * @param quantity Number of shares to list
     * @param pricePerShare Price per share
     * @return The created listing, or null on failure
     */
    @Nullable
    public ShareListing createListing(UUID sellerId, String sellerName,
                                       UUID companyId, int quantity, double pricePerShare) {
        if (quantity <= 0 || pricePerShare <= 0) return null;

        CompanyManager companyManager = CompanyManager.getInstance();
        Company company = companyManager.getCompany(companyId);
        if (company == null) return null;

        // Verify seller owns enough shares
        int owned = company.getShareCount(sellerId);
        if (owned < quantity) return null;

        // Check how many shares the seller already has listed for this company
        int alreadyListed = getActiveListingsForSeller(sellerId).stream()
            .filter(l -> l.getCompanyId().equals(companyId))
            .mapToInt(ShareListing::getQuantity)
            .sum();

        if (owned - alreadyListed < quantity) return null; // Can't list more than unlisted shares

        ShareListing listing = new ShareListing(
            UUID.randomUUID(), sellerId, sellerName,
            companyId, company.getName(),
            quantity, pricePerShare
        );

        listings.put(listing.getId(), listing);
        dirty = true;

        StateCraftEconomy.LOGGER.info("Share listing created: {} shares of '{}' by {} at ${}/share",
            quantity, company.getName(), sellerName, String.format("%.2f", pricePerShare));

        return listing;
    }

    /**
     * Purchase shares from a listing.
     *
     * @param listingId The listing to buy from
     * @param buyerId The buyer's UUID
     * @param buyerName The buyer's display name
     * @param quantity Number of shares to buy
     * @param server The server instance for notifications
     * @return Result message (success or error)
     */
    public String purchaseShares(UUID listingId, UUID buyerId, String buyerName,
                                  int quantity, MinecraftServer server) {
        ShareListing listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) {
            return "§cListing not found or no longer active.";
        }

        if (listing.getSellerId().equals(buyerId)) {
            return "§cYou cannot buy your own shares.";
        }

        if (quantity <= 0 || quantity > listing.getQuantity()) {
            return "§cInvalid quantity. Available: " + listing.getQuantity();
        }

        double totalCost = quantity * listing.getPricePerShare();

        EconomyManager ecoManager = EconomyManager.getInstance();
        CompanyManager companyManager = CompanyManager.getInstance();
        Company company = companyManager.getCompany(listing.getCompanyId());

        if (company == null) {
            listing.cancel();
            dirty = true;
            return "§cCompany no longer exists.";
        }

        // Check buyer has enough money
        double buyerBalance = ecoManager.getBalance(buyerId);
        if (buyerBalance < totalCost) {
            return "§cInsufficient funds. Need " + ecoManager.formatCurrency(totalCost) +
                   ", have " + ecoManager.formatCurrency(buyerBalance) + ".";
        }

        // Verify seller still owns the shares
        int sellerShares = company.getShareCount(listing.getSellerId());
        int sellerListedTotal = getActiveListingsForSeller(listing.getSellerId()).stream()
            .filter(l -> l.getCompanyId().equals(listing.getCompanyId()))
            .mapToInt(ShareListing::getQuantity)
            .sum();

        // The shares backing this listing must still be held by the seller
        if (sellerShares < quantity) {
            listing.cancel();
            dirty = true;
            return "§cSeller no longer holds enough shares.";
        }

        // Execute the transaction
        // 1. Withdraw money from buyer
        var withdrawResult = ecoManager.withdraw(buyerId, totalCost,
            "Purchased " + quantity + " shares of " + company.getName());
        if (!withdrawResult.isSuccess()) {
            return "§cPayment failed: " + withdrawResult.getMessage();
        }

        // 2. Deposit money to seller
        ecoManager.deposit(listing.getSellerId(), totalCost,
            "Sold " + quantity + " shares of " + company.getName());

        // 3. Transfer shares
        boolean transferred = company.transferShares(listing.getSellerId(), buyerId, quantity);
        if (!transferred) {
            // Rollback money
            ecoManager.deposit(buyerId, totalCost, "Refund: share transfer failed");
            ecoManager.withdraw(listing.getSellerId(), totalCost, "Refund: share transfer failed");
            return "§cShare transfer failed.";
        }

        // 4. Update listing
        listing.purchase(quantity);
        companyManager.markDirty();
        dirty = true;

        // Notify seller if online
        ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(listing.getSellerId());
        if (sellerPlayer != null) {
            sellerPlayer.sendSystemMessage(Component.literal(
                "§a[Stock Market] §f" + buyerName + " purchased " + quantity +
                " shares of " + company.getName() + " for " + ecoManager.formatCurrency(totalCost)
            ));
        }

        StateCraftEconomy.LOGGER.info("Share purchase: {} bought {} shares of '{}' from {} for {}",
            buyerName, quantity, company.getName(), listing.getSellerName(),
            ecoManager.formatCurrency(totalCost));

        return "§aPurchased " + quantity + " shares of " + company.getName() +
               " for " + ecoManager.formatCurrency(totalCost) + ".";
    }

    /**
     * Cancel a listing. Only the seller can cancel.
     */
    public String cancelListing(UUID listingId, UUID requesterId) {
        ShareListing listing = listings.get(listingId);
        if (listing == null) return "§cListing not found.";
        if (!listing.getSellerId().equals(requesterId)) return "§cYou can only cancel your own listings.";
        if (!listing.isActive()) return "§cListing is already inactive.";

        listing.cancel();
        dirty = true;

        StateCraftEconomy.LOGGER.info("Share listing cancelled: {} shares of '{}' by {}",
            listing.getQuantity(), listing.getCompanyName(), listing.getSellerName());

        return "§aListing cancelled.";
    }

    // ==================== Queries ====================

    /**
     * Get all active listings, optionally filtered by company.
     */
    public List<ShareListing> getActiveListings(@Nullable UUID companyFilter) {
        return listings.values().stream()
            .filter(ShareListing::isActive)
            .filter(l -> companyFilter == null || l.getCompanyId().equals(companyFilter))
            .sorted(Comparator.comparingLong(ShareListing::getListedTime).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get active listings for a specific seller.
     */
    public List<ShareListing> getActiveListingsForSeller(UUID sellerId) {
        return listings.values().stream()
            .filter(ShareListing::isActive)
            .filter(l -> l.getSellerId().equals(sellerId))
            .collect(Collectors.toList());
    }

    /**
     * Get all listings for a specific seller (including inactive).
     */
    public List<ShareListing> getAllListingsForSeller(UUID sellerId) {
        return listings.values().stream()
            .filter(l -> l.getSellerId().equals(sellerId))
            .sorted(Comparator.comparingLong(ShareListing::getListedTime).reversed())
            .collect(Collectors.toList());
    }

    @Nullable
    public ShareListing getListing(UUID listingId) {
        return listings.get(listingId);
    }

    /**
     * Clean up old sold/cancelled listings (older than 7 days).
     */
    public void cleanupOldListings() {
        long cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        int removed = 0;
        Iterator<Map.Entry<UUID, ShareListing>> it = listings.entrySet().iterator();
        while (it.hasNext()) {
            ShareListing listing = it.next().getValue();
            if (!listing.isActive() && listing.getListedTime() < cutoff) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            dirty = true;
            StateCraftEconomy.LOGGER.info("Cleaned up {} old stock market listings", removed);
        }
    }

    // ==================== Persistence ====================

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag listingsList = new ListTag();
        for (ShareListing listing : listings.values()) {
            listingsList.add(listing.save());
        }
        tag.put("Listings", listingsList);
        return tag;
    }

    public void load(CompoundTag tag) {
        listings.clear();
        if (tag.contains("Listings")) {
            ListTag listingsList = tag.getList("Listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < listingsList.size(); i++) {
                ShareListing listing = ShareListing.load(listingsList.getCompound(i));
                listings.put(listing.getId(), listing);
            }
        }
        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded {} stock market listings", listings.size());
    }
}

