package com.statecraft.economy.network.packets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> Client packet containing marketplace listing data.
 */
public class SyncMarketListingsPacket {

    /**
     * A single listing entry for display on the client.
     */
    public record ListingEntry(
        UUID listingId,
        ItemStack item,
        String itemName,
        int quantity,
        double pricePerUnit,
        String sellerName,
        UUID sellerId,
        String sellerNation,
        long listedTime
    ) {}

    private final List<ListingEntry> entries;
    private final boolean myListingsView; // true if showing player's own listings

    public SyncMarketListingsPacket(List<ListingEntry> entries, boolean myListingsView) {
        this.entries = entries;
        this.myListingsView = myListingsView;
    }

    public SyncMarketListingsPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID listingId = buf.readUUID();
            ItemStack item = buf.readItem();
            String itemName = buf.readUtf(256);
            int quantity = buf.readVarInt();
            double pricePerUnit = buf.readDouble();
            String sellerName = buf.readUtf(64);
            UUID sellerId = buf.readUUID();
            String sellerNation = buf.readUtf(128);
            long listedTime = buf.readLong();
            entries.add(new ListingEntry(listingId, item, itemName, quantity, pricePerUnit,
                sellerName, sellerId, sellerNation, listedTime));
        }
        this.myListingsView = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (ListingEntry e : entries) {
            buf.writeUUID(e.listingId());
            buf.writeItem(e.item());
            buf.writeUtf(e.itemName(), 256);
            buf.writeVarInt(e.quantity());
            buf.writeDouble(e.pricePerUnit());
            buf.writeUtf(e.sellerName(), 64);
            buf.writeUUID(e.sellerId());
            buf.writeUtf(e.sellerNation(), 128);
            buf.writeLong(e.listedTime());
        }
        buf.writeBoolean(myListingsView);
    }

    public List<ListingEntry> getEntries() { return entries; }
    public boolean isMyListingsView() { return myListingsView; }
}

