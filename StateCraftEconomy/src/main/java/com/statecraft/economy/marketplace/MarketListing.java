package com.statecraft.economy.marketplace;

import com.statecraft.economy.util.NBTUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Represents a single item listing on the global marketplace.
 * A seller lists a quantity of an item at a fixed price-per-unit.
 * Buyers can purchase any quantity up to the remaining stock.
 */
public class MarketListing {

    public enum ListingStatus {
        ACTIVE,
        SOLD,
        CANCELLED,
        EXPIRED
    }

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final UUID sellerNationId;   // null if seller is nationless
    private final UUID sellerCityId;     // city where the listing was created (for sales tax jurisdiction)
    private final ItemStack item;        // Template item (count = 1)
    private int quantity;                // Remaining quantity for sale
    private final int originalQuantity;
    private final double pricePerUnit;
    private final long listedTime;       // System.currentTimeMillis()
    private ListingStatus status;

    public MarketListing(UUID id, UUID sellerId, String sellerName,
                         UUID sellerNationId, UUID sellerCityId,
                         ItemStack item, int quantity, double pricePerUnit) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sellerNationId = sellerNationId;
        this.sellerCityId = sellerCityId;
        this.item = item.copy();
        this.item.setCount(1); // template is always count=1
        this.quantity = quantity;
        this.originalQuantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.listedTime = System.currentTimeMillis();
        this.status = ListingStatus.ACTIVE;
    }

    /** Load constructor */
    private MarketListing(UUID id, UUID sellerId, String sellerName,
                          UUID sellerNationId, UUID sellerCityId,
                          ItemStack item, int quantity, int originalQuantity,
                          double pricePerUnit, long listedTime, ListingStatus status) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sellerNationId = sellerNationId;
        this.sellerCityId = sellerCityId;
        this.item = item;
        this.quantity = quantity;
        this.originalQuantity = originalQuantity;
        this.pricePerUnit = pricePerUnit;
        this.listedTime = listedTime;
        this.status = status;
    }

    // ==================== Getters ====================

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public UUID getSellerNationId() { return sellerNationId; }
    public UUID getSellerCityId() { return sellerCityId; }
    public ItemStack getItem() { return item.copy(); }
    public String getItemName() { return item.getHoverName().getString(); }
    public String getItemId() {
        var rl = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        return rl != null ? rl.toString() : "unknown";
    }
    public int getQuantity() { return quantity; }
    public int getOriginalQuantity() { return originalQuantity; }
    public double getPricePerUnit() { return pricePerUnit; }
    public long getListedTime() { return listedTime; }
    public ListingStatus getStatus() { return status; }
    public boolean isActive() { return status == ListingStatus.ACTIVE && quantity > 0; }
    public double getTotalPrice() { return pricePerUnit * quantity; }
    public double getTotalPriceForQuantity(int qty) { return pricePerUnit * qty; }

    // ==================== Actions ====================

    public int reduceQuantity(int qty) {
        int actual = Math.min(qty, quantity);
        quantity -= actual;
        if (quantity <= 0) {
            status = ListingStatus.SOLD;
        }
        return actual;
    }

    public void cancel() { status = ListingStatus.CANCELLED; }
    public void expire() { status = ListingStatus.EXPIRED; }

    // ==================== NBT ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("SellerId", sellerId);
        NBTUtils.putSanitizedString(tag, "SellerName", sellerName);
        if (sellerNationId != null) tag.putUUID("SellerNationId", sellerNationId);
        if (sellerCityId != null) tag.putUUID("SellerCityId", sellerCityId);
        tag.put("Item", item.save(new CompoundTag()));
        tag.putInt("Quantity", quantity);
        tag.putInt("OriginalQuantity", originalQuantity);
        tag.putDouble("PricePerUnit", pricePerUnit);
        tag.putLong("ListedTime", listedTime);
        tag.putString("Status", status.name());
        return tag;
    }

    public static MarketListing load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        UUID sellerId = tag.getUUID("SellerId");
        String sellerName = tag.getString("SellerName");
        UUID sellerNationId = tag.contains("SellerNationId") ? tag.getUUID("SellerNationId") : null;
        UUID sellerCityId = tag.contains("SellerCityId") ? tag.getUUID("SellerCityId") : null;
        ItemStack item = ItemStack.of(tag.getCompound("Item"));
        int quantity = tag.getInt("Quantity");
        int originalQuantity = tag.contains("OriginalQuantity") ? tag.getInt("OriginalQuantity") : quantity;
        double pricePerUnit = tag.getDouble("PricePerUnit");
        long listedTime = tag.getLong("ListedTime");
        ListingStatus status;
        try {
            status = ListingStatus.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException e) {
            status = ListingStatus.ACTIVE;
        }
        return new MarketListing(id, sellerId, sellerName, sellerNationId, sellerCityId,
            item, quantity, originalQuantity, pricePerUnit, listedTime, status);
    }
}

