package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client packet to sync stock market listing data.
 */
public class SyncStockListingsPacket {

    private final List<ListingEntry> entries;
    private final boolean myListingsView;
    private final List<CompanyShareInfo> playerShares; // Companies the player holds shares in

    public SyncStockListingsPacket(List<ListingEntry> entries, boolean myListingsView,
                                    List<CompanyShareInfo> playerShares) {
        this.entries = entries;
        this.myListingsView = myListingsView;
        this.playerShares = playerShares;
    }

    public SyncStockListingsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new ListingEntry(buf));
        }
        this.myListingsView = buf.readBoolean();

        int shareCount = buf.readVarInt();
        this.playerShares = new ArrayList<>(shareCount);
        for (int i = 0; i < shareCount; i++) {
            playerShares.add(new CompanyShareInfo(buf));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (ListingEntry entry : entries) {
            entry.encode(buf);
        }
        buf.writeBoolean(myListingsView);

        buf.writeVarInt(playerShares.size());
        for (CompanyShareInfo info : playerShares) {
            info.encode(buf);
        }
    }

    public List<ListingEntry> getEntries() { return entries; }
    public boolean isMyListingsView() { return myListingsView; }
    public List<CompanyShareInfo> getPlayerShares() { return playerShares; }

    /**
     * A single listing entry for display on the client.
     */
    public static class ListingEntry {
        private final String listingId;
        private final String sellerName;
        private final String companyName;
        private final String companyId;
        private final int quantity;
        private final double pricePerShare;
        private final long listedTime;
        private final String status;
        private final boolean isMine; // true if the current player is the seller

        public ListingEntry(String listingId, String sellerName, String companyName, String companyId,
                           int quantity, double pricePerShare, long listedTime,
                           String status, boolean isMine) {
            this.listingId = listingId;
            this.sellerName = sellerName;
            this.companyName = companyName;
            this.companyId = companyId;
            this.quantity = quantity;
            this.pricePerShare = pricePerShare;
            this.listedTime = listedTime;
            this.status = status;
            this.isMine = isMine;
        }

        public ListingEntry(FriendlyByteBuf buf) {
            this.listingId = buf.readUtf(64);
            this.sellerName = buf.readUtf(64);
            this.companyName = buf.readUtf(64);
            this.companyId = buf.readUtf(64);
            this.quantity = buf.readVarInt();
            this.pricePerShare = buf.readDouble();
            this.listedTime = buf.readLong();
            this.status = buf.readUtf(32);
            this.isMine = buf.readBoolean();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(listingId, 64);
            buf.writeUtf(sellerName, 64);
            buf.writeUtf(companyName, 64);
            buf.writeUtf(companyId, 64);
            buf.writeVarInt(quantity);
            buf.writeDouble(pricePerShare);
            buf.writeLong(listedTime);
            buf.writeUtf(status, 32);
            buf.writeBoolean(isMine);
        }

        public String getListingId() { return listingId; }
        public String getSellerName() { return sellerName; }
        public String getCompanyName() { return companyName; }
        public String getCompanyId() { return companyId; }
        public int getQuantity() { return quantity; }
        public double getPricePerShare() { return pricePerShare; }
        public double getTotalPrice() { return quantity * pricePerShare; }
        public long getListedTime() { return listedTime; }
        public String getStatus() { return status; }
        public boolean isMine() { return isMine; }
    }

    /**
     * Info about shares the player holds in a company — used to populate the Sell tab.
     */
    public static class CompanyShareInfo {
        private final String companyId;
        private final String companyName;
        private final int sharesOwned;
        private final int totalShares;
        private final int sharesListed; // Already listed for sale

        public CompanyShareInfo(String companyId, String companyName,
                               int sharesOwned, int totalShares, int sharesListed) {
            this.companyId = companyId;
            this.companyName = companyName;
            this.sharesOwned = sharesOwned;
            this.totalShares = totalShares;
            this.sharesListed = sharesListed;
        }

        public CompanyShareInfo(FriendlyByteBuf buf) {
            this.companyId = buf.readUtf(64);
            this.companyName = buf.readUtf(64);
            this.sharesOwned = buf.readVarInt();
            this.totalShares = buf.readVarInt();
            this.sharesListed = buf.readVarInt();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(companyId, 64);
            buf.writeUtf(companyName, 64);
            buf.writeVarInt(sharesOwned);
            buf.writeVarInt(totalShares);
            buf.writeVarInt(sharesListed);
        }

        public String getCompanyId() { return companyId; }
        public String getCompanyName() { return companyName; }
        public int getSharesOwned() { return sharesOwned; }
        public int getTotalShares() { return totalShares; }
        public int getSharesListed() { return sharesListed; }
        public int getAvailableToList() { return sharesOwned - sharesListed; }
    }
}

