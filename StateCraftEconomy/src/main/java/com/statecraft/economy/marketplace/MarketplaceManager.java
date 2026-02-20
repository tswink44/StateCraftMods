package com.statecraft.economy.marketplace;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.Transaction;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side singleton managing all marketplace listings.
 * Handles listing creation (with listing fee to city), purchasing (with sales tax
 * to seller's city/state/nation and import tariff to buyer's nation), cancellation,
 * expiration, and persistence.
 */
public class MarketplaceManager {

    private static final MarketplaceManager INSTANCE = new MarketplaceManager();
    public static MarketplaceManager getInstance() { return INSTANCE; }

    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();
    private boolean dirty = false;
    private long lastExpirationCheck = 0;
    private static final long EXPIRATION_CHECK_INTERVAL_TICKS = 1200; // every 60 seconds

    // ==================== Tax Info ====================

    /**
     * Holds the result of a sales tax calculation for a marketplace transaction.
     */
    public static class SalesTaxInfo {
        public double totalTax;
        public double cityShare;
        public double stateShare;
        public double nationShare;
        public UUID cityId;
        public UUID stateId;
        public UUID nationId;
        public String cityName = "";
        public String stateName = "";
        public String nationName = "";
        public double effectiveCityRate;
        public double effectiveStateRate;
        public double effectiveNationRate;
    }

    /**
     * Result of a purchase operation.
     */
    public record PurchaseResult(boolean success, String message, double totalCost,
                                  double itemCost, double salesTax, double importTariff) {
        public static PurchaseResult fail(String message) {
            return new PurchaseResult(false, message, 0, 0, 0, 0);
        }
    }

    // ==================== Listing CRUD ====================

    /**
     * Create a new listing. Charges listing fee to seller, deposits to city treasury.
     */
    public PurchaseResult createListing(ServerPlayer seller, ItemStack item, int quantity, double pricePerUnit) {
        if (!EconomyConfig.MARKETPLACE_ENABLED.get()) {
            return PurchaseResult.fail("Marketplace is disabled.");
        }

        int maxListings = EconomyConfig.MAX_LISTINGS_PER_PLAYER.get();
        if (maxListings > 0) {
            long count = listings.values().stream()
                .filter(l -> l.getSellerId().equals(seller.getUUID()) && l.isActive())
                .count();
            if (count >= maxListings) {
                return PurchaseResult.fail("You have reached the maximum of " + maxListings + " active listings.");
            }
        }

        double maxPrice = EconomyConfig.MAX_PRICE_PER_ITEM.get();
        if (maxPrice > 0 && pricePerUnit > maxPrice) {
            return PurchaseResult.fail("Price per unit cannot exceed $" + String.format("%,.2f", maxPrice));
        }

        if (quantity <= 0 || pricePerUnit <= 0) {
            return PurchaseResult.fail("Quantity and price must be positive.");
        }

        if (item.isEmpty()) {
            return PurchaseResult.fail("Cannot list an empty item.");
        }

        // Calculate listing fee
        double listingFeeRate = EconomyConfig.LISTING_FEE_PCT.get();
        double totalListedValue = pricePerUnit * quantity;
        double listingFee = totalListedValue * listingFeeRate;

        // Charge listing fee from seller's balance
        EconomyManager econ = EconomyManager.getInstance();
        if (listingFee > 0) {
            double sellerBalance = econ.getBalance(seller.getUUID());
            if (sellerBalance < listingFee) {
                return PurchaseResult.fail(String.format("Insufficient funds for listing fee ($%,.2f). Balance: $%,.2f",
                    listingFee, sellerBalance));
            }
            econ.withdraw(seller.getUUID(), listingFee, "Marketplace listing fee");
            econ.recordTransaction(seller.getUUID(), Transaction.Type.FEE, listingFee, null,
                "Marketplace listing fee for " + quantity + "x " + item.getHoverName().getString(),
                seller.getUUID(), seller.getName().getString());
        }

        // Deposit listing fee to seller's current city treasury
        UUID sellerCityId = StateCraftIntegration.getPlayerCityId(seller);
        if (listingFee > 0 && sellerCityId != null) {
            econ.getOrCreateCityTreasury(sellerCityId).add(listingFee);
            econ.recordTransaction(sellerCityId, Transaction.Type.TAX, listingFee, seller.getUUID(),
                "Marketplace listing fee from " + seller.getName().getString(),
                null, "System");
            econ.markDirty();
        }

        // Get seller's nation and city for tax jurisdiction
        UUID sellerNationId = StateCraftIntegration.getPlayerNationId(seller);

        // Remove items from seller's inventory
        int remaining = quantity;
        for (int i = 0; i < seller.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack invStack = seller.getInventory().getItem(i);
            if (!invStack.isEmpty() && ItemStack.isSameItemSameTags(invStack, item)) {
                int take = Math.min(remaining, invStack.getCount());
                invStack.shrink(take);
                remaining -= take;
            }
        }

        if (remaining > 0) {
            // Not enough items — refund listing fee and abort
            if (listingFee > 0) {
                econ.deposit(seller.getUUID(), listingFee, "Listing fee refund — insufficient items");
            }
            return PurchaseResult.fail("You don't have enough of that item. Need " + quantity + ", found " + (quantity - remaining) + ".");
        }

        // Create the listing
        UUID listingId = UUID.randomUUID();
        MarketListing listing = new MarketListing(listingId, seller.getUUID(), seller.getName().getString(),
            sellerNationId, sellerCityId, item, quantity, pricePerUnit);
        listings.put(listingId, listing);
        dirty = true;

        StateCraftEconomy.LOGGER.info("Player {} listed {}x {} at ${}/ea on marketplace (fee: ${})",
            seller.getName().getString(), quantity, item.getHoverName().getString(),
            String.format("%.2f", pricePerUnit), String.format("%.2f", listingFee));

        return new PurchaseResult(true,
            String.format("Listed %dx %s at $%,.2f/ea. Listing fee: $%,.2f",
                quantity, item.getHoverName().getString(), pricePerUnit, listingFee),
            listingFee, 0, 0, 0);
    }

    /**
     * Purchase items from a listing. Handles sales tax + import tariff.
     */
    public PurchaseResult purchaseListing(ServerPlayer buyer, UUID listingId, int quantity) {
        MarketListing listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) {
            return PurchaseResult.fail("Listing not found or no longer available.");
        }

        if (listing.getSellerId().equals(buyer.getUUID())) {
            return PurchaseResult.fail("You cannot buy your own listing.");
        }

        int buyQty = Math.min(quantity, listing.getQuantity());
        if (buyQty <= 0) {
            return PurchaseResult.fail("No items available.");
        }

        double itemCost = listing.getPricePerUnit() * buyQty;

        // Calculate sales tax based on seller's city/state/nation
        SalesTaxInfo taxInfo = calculateSalesTax(itemCost, listing.getSellerCityId());
        double salesTax = taxInfo.totalTax;

        // Calculate import tariff
        UUID buyerNationId = StateCraftIntegration.getPlayerNationId(buyer);
        double importTariff = 0;
        String buyerNationName = "";
        if (buyerNationId != null) {
            // Only charge tariff if buyer and seller are different nations
            boolean sameNation = buyerNationId.equals(listing.getSellerNationId());
            if (!sameNation) {
                double tariffRate = StateCraftIntegration.getImportTariffRate(buyerNationId);
                importTariff = itemCost * tariffRate;
                buyerNationName = StateCraftIntegration.getNationName(buyerNationId);
            }
        }

        double totalCost = itemCost + importTariff;

        // Validate buyer has enough funds
        EconomyManager econ = EconomyManager.getInstance();
        double buyerBalance = econ.getBalance(buyer.getUUID());
        if (buyerBalance < totalCost) {
            return PurchaseResult.fail(String.format("Insufficient funds. Need $%,.2f (item: $%,.2f + tariff: $%,.2f), have $%,.2f",
                totalCost, itemCost, importTariff, buyerBalance));
        }

        // ---- Execute the transaction ----

        // 1. Deduct total cost from buyer
        econ.withdraw(buyer.getUUID(), totalCost, "Marketplace purchase");

        // 2. Pay seller (item cost minus sales tax)
        double sellerProceeds = itemCost - salesTax;
        econ.deposit(listing.getSellerId(), sellerProceeds, "Marketplace sale");

        // 3. Distribute sales tax to seller's city/state/nation
        distributeSalesTax(taxInfo);

        // 4. Deposit import tariff to buyer's nation treasury
        if (importTariff > 0 && buyerNationId != null) {
            econ.getOrCreateNationTreasury(buyerNationId).add(importTariff);
            econ.recordTransaction(buyerNationId, Transaction.Type.IMPORT_TARIFF, importTariff, buyer.getUUID(),
                "Import tariff on marketplace purchase by " + buyer.getName().getString(),
                null, "System");
            econ.markDirty();
        }

        // 5. Record transactions for buyer and seller
        String itemDesc = buyQty + "x " + listing.getItemName();
        econ.recordTransaction(buyer.getUUID(), Transaction.Type.MARKETPLACE_PURCHASE, itemCost, listing.getSellerId(),
            "Bought " + itemDesc + " from " + listing.getSellerName(),
            buyer.getUUID(), buyer.getName().getString());
        if (importTariff > 0) {
            econ.recordTransaction(buyer.getUUID(), Transaction.Type.IMPORT_TARIFF, importTariff, buyerNationId,
                "Import tariff to " + buyerNationName,
                null, "System");
        }

        econ.recordTransaction(listing.getSellerId(), Transaction.Type.MARKETPLACE_SALE, sellerProceeds, buyer.getUUID(),
            "Sold " + itemDesc + " to " + buyer.getName().getString() + " (tax: $" + String.format("%.2f", salesTax) + ")",
            buyer.getUUID(), buyer.getName().getString());

        // 6. Deliver items to buyer
        ItemStack deliveryItem = listing.getItem();
        int delivered = 0;
        while (delivered < buyQty) {
            int stackSize = Math.min(buyQty - delivered, deliveryItem.getMaxStackSize());
            ItemStack stack = deliveryItem.copy();
            stack.setCount(stackSize);
            if (!buyer.getInventory().add(stack)) {
                // Inventory full — drop remaining items
                buyer.drop(stack, false);
            }
            delivered += stackSize;
        }

        // 7. Reduce listing quantity
        listing.reduceQuantity(buyQty);
        dirty = true;

        // 8. Send mail to seller about the sale
        sendSaleMail(listing.getSellerId(), listing.getSellerName(), buyer.getName().getString(),
            itemDesc, sellerProceeds, salesTax, listing.getQuantity());

        StateCraftEconomy.LOGGER.info("Player {} bought {}x {} from {} for ${} (tax: ${}, tariff: ${})",
            buyer.getName().getString(), buyQty, listing.getItemName(), listing.getSellerName(),
            String.format("%.2f", itemCost), String.format("%.2f", salesTax), String.format("%.2f", importTariff));

        return new PurchaseResult(true,
            String.format("Purchased %s for $%,.2f (tax: $%,.2f, tariff: $%,.2f)",
                itemDesc, totalCost, salesTax, importTariff),
            totalCost, itemCost, salesTax, importTariff);
    }

    /**
     * Cancel a listing and return items to the seller.
     */
    public PurchaseResult cancelListing(ServerPlayer seller, UUID listingId) {
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            return PurchaseResult.fail("Listing not found.");
        }
        if (!listing.getSellerId().equals(seller.getUUID()) && !seller.hasPermissions(2)) {
            return PurchaseResult.fail("You can only cancel your own listings.");
        }
        if (!listing.isActive()) {
            return PurchaseResult.fail("Listing is no longer active.");
        }

        listing.cancel();
        returnItemsToPlayer(seller, listing);
        dirty = true;

        return new PurchaseResult(true,
            String.format("Cancelled listing for %dx %s. Items returned.",
                listing.getQuantity(), listing.getItemName()),
            0, 0, 0, 0);
    }

    // ==================== Tax Calculation ====================

    /**
     * Calculate sales tax based on the city where the listing was created.
     * Uses independent city/state/nation rates with priority capping (Nation > State > City)
     * matching the TradingHub pattern.
     */
    public SalesTaxInfo calculateSalesTax(double grossValue, UUID cityId) {
        SalesTaxInfo info = new SalesTaxInfo();
        if (cityId == null || !StateCraftIntegration.isInitialized()) return info;

        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            var getCity = managerClass.getMethod("getCity", UUID.class);
            Object city = getCity.invoke(manager, cityId);
            if (city == null) return info;

            info.cityId = cityId;
            info.cityName = (String) city.getClass().getMethod("getName").invoke(city);
            double cityTaxRate = (double) city.getClass().getMethod("getSalesTaxRate").invoke(city);
            double stateTaxRate = 0;
            double nationTaxRate = 0;

            UUID stateId = (UUID) city.getClass().getMethod("getStateId").invoke(city);
            Object state = managerClass.getMethod("getState", UUID.class).invoke(manager, stateId);
            if (state != null) {
                info.stateId = stateId;
                info.stateName = (String) state.getClass().getMethod("getName").invoke(state);
                stateTaxRate = (double) state.getClass().getMethod("getSalesTaxRate").invoke(state);

                UUID nationId = (UUID) state.getClass().getMethod("getNationId").invoke(state);
                Object nation = managerClass.getMethod("getNation", UUID.class).invoke(manager, nationId);
                if (nation != null) {
                    info.nationId = nationId;
                    info.nationName = (String) nation.getClass().getMethod("getName").invoke(nation);
                    nationTaxRate = (double) nation.getClass().getMethod("getSalesTaxRate").invoke(nation);
                }
            }

            // Priority cap: Nation > State > City (same as TradingHub)
            double remaining = 1.0;
            info.effectiveNationRate = Math.min(nationTaxRate, remaining);
            remaining -= info.effectiveNationRate;
            info.effectiveStateRate = Math.min(stateTaxRate, remaining);
            remaining -= info.effectiveStateRate;
            info.effectiveCityRate = Math.min(cityTaxRate, remaining);

            info.nationShare = grossValue * info.effectiveNationRate;
            info.stateShare = grossValue * info.effectiveStateRate;
            info.cityShare = grossValue * info.effectiveCityRate;
            info.totalTax = info.nationShare + info.stateShare + info.cityShare;

        } catch (Exception e) {
            StateCraftEconomy.LOGGER.warn("Error calculating marketplace sales tax: {}", e.getMessage());
        }
        return info;
    }

    private void distributeSalesTax(SalesTaxInfo taxInfo) {
        if (taxInfo.totalTax <= 0) return;
        EconomyManager econ = EconomyManager.getInstance();

        if (taxInfo.cityId != null && taxInfo.cityShare > 0) {
            econ.getOrCreateCityTreasury(taxInfo.cityId).add(taxInfo.cityShare);
            econ.recordTransaction(taxInfo.cityId, Transaction.Type.TAX, taxInfo.cityShare, null,
                "Marketplace sales tax", null, "System");
        }
        if (taxInfo.stateId != null && taxInfo.stateShare > 0) {
            econ.getOrCreateStateTreasury(taxInfo.stateId).add(taxInfo.stateShare);
            econ.recordTransaction(taxInfo.stateId, Transaction.Type.TAX, taxInfo.stateShare, null,
                "Marketplace sales tax", null, "System");
        }
        if (taxInfo.nationId != null && taxInfo.nationShare > 0) {
            econ.getOrCreateNationTreasury(taxInfo.nationId).add(taxInfo.nationShare);
            econ.recordTransaction(taxInfo.nationId, Transaction.Type.TAX, taxInfo.nationShare, null,
                "Marketplace sales tax", null, "System");
        }
        econ.markDirty();
    }

    // ==================== Query ====================

    public List<MarketListing> getActiveListings() {
        return listings.values().stream()
            .filter(MarketListing::isActive)
            .collect(Collectors.toList());
    }

    public List<MarketListing> getListingsBySeller(UUID sellerId) {
        return listings.values().stream()
            .filter(l -> l.getSellerId().equals(sellerId) && l.isActive())
            .collect(Collectors.toList());
    }

    public List<MarketListing> searchListings(String query) {
        String lower = query.toLowerCase();
        return listings.values().stream()
            .filter(MarketListing::isActive)
            .filter(l -> l.getItemName().toLowerCase().contains(lower)
                      || l.getItemId().toLowerCase().contains(lower)
                      || l.getSellerName().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    public MarketListing getListing(UUID listingId) {
        return listings.get(listingId);
    }

    // ==================== Tick / Expiration ====================

    public void tick(MinecraftServer server) {
        lastExpirationCheck++;
        if (lastExpirationCheck < EXPIRATION_CHECK_INTERVAL_TICKS) return;
        lastExpirationCheck = 0;

        long expirationHours = EconomyConfig.LISTING_EXPIRATION_HOURS.get();
        if (expirationHours <= 0) return;

        long expirationMs = expirationHours * 3600 * 1000;
        long now = System.currentTimeMillis();
        int expired = 0;

        for (MarketListing listing : listings.values()) {
            if (listing.isActive() && (now - listing.getListedTime()) > expirationMs) {
                listing.expire();
                // Try to return items to seller
                ServerPlayer seller = server.getPlayerList().getPlayer(listing.getSellerId());
                if (seller != null) {
                    returnItemsToPlayer(seller, listing);
                }
                // Send expiration mail
                sendExpirationMail(listing);
                expired++;
                dirty = true;
            }
        }
        if (expired > 0) {
            StateCraftEconomy.LOGGER.info("Expired {} marketplace listings", expired);
        }
    }

    // ==================== Helpers ====================

    private void returnItemsToPlayer(ServerPlayer player, MarketListing listing) {
        int remaining = listing.getQuantity();
        ItemStack template = listing.getItem();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, template.getMaxStackSize());
            ItemStack stack = template.copy();
            stack.setCount(stackSize);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= stackSize;
        }
    }

    private void sendSaleMail(UUID sellerId, String sellerName, String buyerName,
                               String itemDesc, double proceeds, double tax, int remainingQty) {
        try {
            var mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            var mailManager = getInstance.invoke(null);
            var mailTypeClass = Class.forName("com.statecraft.mail.Mail$MailType");
            Object mailType = null;
            for (Object c : mailTypeClass.getEnumConstants()) {
                if ("FINANCIAL".equals(c.toString()) || "SYSTEM".equals(c.toString())) {
                    mailType = c;
                    break;
                }
            }
            if (mailType == null) mailType = mailTypeClass.getEnumConstants()[0];

            String subject = "Marketplace Sale — " + itemDesc;
            String body = String.format(
                "§aYour marketplace listing has sold!\n\n" +
                "§7Item: §f%s\n" +
                "§7Buyer: §f%s\n" +
                "§7Proceeds: §a$%,.2f\n" +
                "§7Sales Tax: §c$%,.2f\n" +
                "§7Remaining: §f%d\n\n" +
                "§7Funds have been deposited to your account.",
                itemDesc, buyerName, proceeds, tax, remainingQty);

            var sendMail = mailManagerClass.getMethod("sendSystemMail",
                UUID.class, mailTypeClass, String.class, String.class);
            sendMail.invoke(mailManager, sellerId, mailType, subject, body);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not send marketplace sale mail: {}", e.getMessage());
        }
    }

    private void sendExpirationMail(MarketListing listing) {
        try {
            var mailManagerClass = Class.forName("com.statecraft.mail.MailManager");
            var getInstance = mailManagerClass.getMethod("getInstance");
            var mailManager = getInstance.invoke(null);
            var mailTypeClass = Class.forName("com.statecraft.mail.Mail$MailType");
            Object mailType = null;
            for (Object c : mailTypeClass.getEnumConstants()) {
                if ("SYSTEM".equals(c.toString())) { mailType = c; break; }
            }
            if (mailType == null) mailType = mailTypeClass.getEnumConstants()[0];

            String subject = "Marketplace Listing Expired";
            String body = String.format(
                "§eYour marketplace listing has expired.\n\n" +
                "§7Item: §f%dx %s\n" +
                "§7Price: §f$%,.2f/ea\n\n" +
                "§7Items will be returned to your inventory next time you log in.",
                listing.getQuantity(), listing.getItemName(), listing.getPricePerUnit());

            var sendMail = mailManagerClass.getMethod("sendSystemMail",
                UUID.class, mailTypeClass, String.class, String.class);
            sendMail.invoke(mailManager, listing.getSellerId(), mailType, subject, body);
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not send marketplace expiration mail: {}", e.getMessage());
        }
    }

    // ==================== Persistence ====================

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag listingList = new ListTag();
        for (MarketListing listing : listings.values()) {
            // Only persist active listings (expired/sold/cancelled are transient)
            if (listing.isActive()) {
                listingList.add(listing.save());
            }
        }
        tag.put("Listings", listingList);
        return tag;
    }

    public void load(CompoundTag tag) {
        listings.clear();
        if (tag.contains("Listings")) {
            ListTag list = tag.getList("Listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                MarketListing listing = MarketListing.load(list.getCompound(i));
                listings.put(listing.getId(), listing);
            }
        }
        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded {} marketplace listings", listings.size());
    }
}






