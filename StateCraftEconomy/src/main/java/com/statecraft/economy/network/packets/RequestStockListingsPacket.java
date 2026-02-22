package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet to request stock market listings.
 * Can optionally filter by company name or request only the player's own listings.
 */
public class RequestStockListingsPacket {

    private final boolean myListingsOnly;
    private final String companyFilter; // empty = no filter

    public RequestStockListingsPacket(boolean myListingsOnly, String companyFilter) {
        this.myListingsOnly = myListingsOnly;
        this.companyFilter = companyFilter != null ? companyFilter : "";
    }

    public RequestStockListingsPacket(FriendlyByteBuf buf) {
        this.myListingsOnly = buf.readBoolean();
        this.companyFilter = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(myListingsOnly);
        buf.writeUtf(companyFilter, 256);
    }

    public boolean isMyListingsOnly() { return myListingsOnly; }
    public String getCompanyFilter() { return companyFilter; }
}

