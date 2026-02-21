package com.statecraft.economy.stockmarket;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Represents a listing of company shares on the stock market.
 * Sellers can be individuals (selling their own shares) or
 * company founders (selling newly issued or company-held shares).
 */
public class ShareListing {

    public enum ListingStatus {
        ACTIVE,
        SOLD,
        CANCELLED
    }

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final UUID companyId;
    private final String companyName;
    private int quantity;            // Remaining shares for sale
    private final int originalQuantity;
    private final double pricePerShare;
    private final long listedTime;   // System.currentTimeMillis()
    private ListingStatus status;

    public ShareListing(UUID id, UUID sellerId, String sellerName,
                        UUID companyId, String companyName,
                        int quantity, double pricePerShare) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.companyId = companyId;
        this.companyName = companyName;
        this.quantity = quantity;
        this.originalQuantity = quantity;
        this.pricePerShare = pricePerShare;
        this.listedTime = System.currentTimeMillis();
        this.status = ListingStatus.ACTIVE;
    }

    /** Load constructor */
    private ShareListing(UUID id, UUID sellerId, String sellerName,
                         UUID companyId, String companyName,
                         int quantity, int originalQuantity,
                         double pricePerShare, long listedTime, ListingStatus status) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.companyId = companyId;
        this.companyName = companyName;
        this.quantity = quantity;
        this.originalQuantity = originalQuantity;
        this.pricePerShare = pricePerShare;
        this.listedTime = listedTime;
        this.status = status;
    }

    // ==================== Getters ====================

    public UUID getId() { return id; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public UUID getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public int getQuantity() { return quantity; }
    public int getOriginalQuantity() { return originalQuantity; }
    public double getPricePerShare() { return pricePerShare; }
    public double getTotalPrice() { return quantity * pricePerShare; }
    public long getListedTime() { return listedTime; }
    public ListingStatus getStatus() { return status; }

    // ==================== Mutations ====================

    /**
     * Purchase shares from this listing.
     * @return true if enough shares remain
     */
    public boolean purchase(int count) {
        if (count <= 0 || count > quantity) return false;
        quantity -= count;
        if (quantity == 0) {
            status = ListingStatus.SOLD;
        }
        return true;
    }

    public void cancel() {
        status = ListingStatus.CANCELLED;
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE && quantity > 0;
    }

    // ==================== NBT Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("SellerId", sellerId);
        tag.putString("SellerName", sellerName);
        tag.putUUID("CompanyId", companyId);
        tag.putString("CompanyName", companyName);
        tag.putInt("Quantity", quantity);
        tag.putInt("OriginalQuantity", originalQuantity);
        tag.putDouble("PricePerShare", pricePerShare);
        tag.putLong("ListedTime", listedTime);
        tag.putString("Status", status.name());
        return tag;
    }

    public static ShareListing load(CompoundTag tag) {
        ListingStatus status;
        try {
            status = ListingStatus.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException e) {
            status = ListingStatus.CANCELLED;
        }

        return new ShareListing(
            tag.getUUID("Id"),
            tag.getUUID("SellerId"),
            tag.getString("SellerName"),
            tag.getUUID("CompanyId"),
            tag.getString("CompanyName"),
            tag.getInt("Quantity"),
            tag.getInt("OriginalQuantity"),
            tag.getDouble("PricePerShare"),
            tag.getLong("ListedTime"),
            status
        );
    }
}

