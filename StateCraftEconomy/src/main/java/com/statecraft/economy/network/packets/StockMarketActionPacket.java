package com.statecraft.economy.network.packets;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> Server packet for stock market actions: BUY, SELL (create listing), CANCEL.
 */
public class StockMarketActionPacket {

    public enum Action {
        BUY,      // Buy shares from a listing
        SELL,     // Create a new listing (sell shares)
        CANCEL    // Cancel own listing
    }

    private final Action action;
    private final String listingId;   // For BUY/CANCEL — the listing UUID string
    private final String companyId;   // For SELL — the company UUID string
    private final int quantity;       // For BUY/SELL — number of shares
    private final double price;       // For SELL — price per share

    /** BUY or CANCEL constructor */
    public StockMarketActionPacket(Action action, String listingId, int quantity) {
        this.action = action;
        this.listingId = listingId;
        this.companyId = "";
        this.quantity = quantity;
        this.price = 0;
    }

    /** SELL constructor */
    public StockMarketActionPacket(String companyId, int quantity, double price) {
        this.action = Action.SELL;
        this.listingId = "";
        this.companyId = companyId;
        this.quantity = quantity;
        this.price = price;
    }

    public StockMarketActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.listingId = buf.readUtf(64);
        this.companyId = buf.readUtf(64);
        this.quantity = buf.readVarInt();
        this.price = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(listingId, 64);
        buf.writeUtf(companyId, 64);
        buf.writeVarInt(quantity);
        buf.writeDouble(price);
    }

    public Action getAction() { return action; }
    public String getListingId() { return listingId; }
    public String getCompanyId() { return companyId; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
}

