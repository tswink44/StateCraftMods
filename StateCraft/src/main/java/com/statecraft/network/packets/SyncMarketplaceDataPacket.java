package com.statecraft.network.packets;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Sync marketplace listings to client
 */
public class SyncMarketplaceDataPacket {
    private final List<ListingInfo> listings;

    public SyncMarketplaceDataPacket(List<ListingInfo> listings) {
        this.listings = listings;
    }

    public SyncMarketplaceDataPacket(FriendlyByteBuf buf) {
        int count = buf.readInt();
        listings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            listings.add(new ListingInfo(
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(64),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readUtf(64)
            ));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(listings.size());
        for (ListingInfo listing : listings) {
            buf.writeInt(listing.chunkX);
            buf.writeInt(listing.chunkZ);
            buf.writeUtf(listing.ownerName, 64);
            buf.writeBoolean(listing.isGovernment);
            buf.writeDouble(listing.price);
            buf.writeUtf(listing.cityName, 64);
        }
    }

    public List<ListingInfo> getListings() {
        return listings;
    }

    public static class ListingInfo {
        public final int chunkX;
        public final int chunkZ;
        public final String ownerName;
        public final boolean isGovernment;
        public final double price;
        public final String cityName;

        public ListingInfo(int chunkX, int chunkZ, String ownerName, boolean isGovernment, double price, String cityName) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerName = ownerName;
            this.isGovernment = isGovernment;
            this.price = price;
            this.cityName = cityName;
        }
    }
}

