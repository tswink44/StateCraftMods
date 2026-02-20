package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet requesting marketplace listings.
 * Includes optional search query and sort mode.
 */
public class RequestMarketListingsPacket {

    public enum SortMode {
        NEWEST, PRICE_LOW, PRICE_HIGH, NAME_AZ
    }

    private final String searchQuery;
    private final SortMode sortMode;
    private final boolean myListingsOnly;

    public RequestMarketListingsPacket(String searchQuery, SortMode sortMode, boolean myListingsOnly) {
        this.searchQuery = searchQuery != null ? searchQuery : "";
        this.sortMode = sortMode;
        this.myListingsOnly = myListingsOnly;
    }

    public RequestMarketListingsPacket(FriendlyByteBuf buf) {
        this.searchQuery = buf.readUtf(256);
        this.sortMode = buf.readEnum(SortMode.class);
        this.myListingsOnly = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(searchQuery, 256);
        buf.writeEnum(sortMode);
        buf.writeBoolean(myListingsOnly);
    }

    public String getSearchQuery() { return searchQuery; }
    public SortMode getSortMode() { return sortMode; }
    public boolean isMyListingsOnly() { return myListingsOnly; }
}

