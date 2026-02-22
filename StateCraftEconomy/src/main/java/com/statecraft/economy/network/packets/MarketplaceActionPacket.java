package com.statecraft.economy.network.packets;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.integration.StateCraftIntegration;
import com.statecraft.economy.marketplace.MarketListing;
import com.statecraft.economy.marketplace.MarketplaceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client -> Server packet for marketplace actions: LIST, BUY, CANCEL.
 */
public class MarketplaceActionPacket {

    public enum Action {
        LIST,     // Create a new listing
        BUY,      // Purchase from a listing
        CANCEL    // Cancel own listing
    }

    private final Action action;

    // LIST fields
    private final ItemStack listItem;
    private final int quantity;
    private final double pricePerUnit;

    // BUY fields
    private final UUID listingId;
    private final int buyQuantity;

    /** LIST constructor */
    public MarketplaceActionPacket(ItemStack item, int quantity, double pricePerUnit) {
        this.action = Action.LIST;
        this.listItem = item;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.listingId = new UUID(0, 0);
        this.buyQuantity = 0;
    }

    /** BUY constructor */
    public MarketplaceActionPacket(UUID listingId, int buyQuantity) {
        this.action = Action.BUY;
        this.listItem = ItemStack.EMPTY;
        this.quantity = 0;
        this.pricePerUnit = 0;
        this.listingId = listingId;
        this.buyQuantity = buyQuantity;
    }

    /** CANCEL constructor */
    public MarketplaceActionPacket(UUID listingId) {
        this.action = Action.CANCEL;
        this.listItem = ItemStack.EMPTY;
        this.quantity = 0;
        this.pricePerUnit = 0;
        this.listingId = listingId;
        this.buyQuantity = 0;
    }

    /** Network decode */
    public MarketplaceActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.listItem = buf.readItem();
        this.quantity = buf.readVarInt();
        this.pricePerUnit = buf.readDouble();
        this.listingId = buf.readUUID();
        this.buyQuantity = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeItem(listItem);
        buf.writeVarInt(quantity);
        buf.writeDouble(pricePerUnit);
        buf.writeUUID(listingId);
        buf.writeVarInt(buyQuantity);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            MarketplaceManager mgr = MarketplaceManager.getInstance();

            switch (action) {
                case LIST -> {
                    var result = mgr.createListing(player, listItem, quantity, pricePerUnit);
                    String prefix = result.success() ? "§a" : "§c";
                    player.sendSystemMessage(Component.literal(prefix + result.message()));
                    if (result.success()) {
                        // Refresh the player's view
                        sendListingsToPlayer(player, "", false);
                    }
                }
                case BUY -> {
                    var result = mgr.purchaseListing(player, listingId, buyQuantity);
                    String prefix = result.success() ? "§a" : "§c";
                    player.sendSystemMessage(Component.literal(prefix + result.message()));
                    if (result.success()) {
                        sendListingsToPlayer(player, "", false);
                    }
                }
                case CANCEL -> {
                    var result = mgr.cancelListing(player, listingId);
                    String prefix = result.success() ? "§a" : "§c";
                    player.sendSystemMessage(Component.literal(prefix + result.message()));
                    if (result.success()) {
                        sendListingsToPlayer(player, "", true);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Utility: build and send SyncMarketListingsPacket to a player.
     * Also used by ServerPacketHandler for RequestMarketListingsPacket.
     */
    public static void sendListingsToPlayer(ServerPlayer player, String search, boolean myListingsOnly) {
        MarketplaceManager mgr = MarketplaceManager.getInstance();
        List<MarketListing> results;

        if (myListingsOnly) {
            results = mgr.getListingsBySeller(player.getUUID());
        } else if (!search.isEmpty()) {
            results = mgr.searchListings(search);
        } else {
            results = mgr.getActiveListings();
        }

        List<SyncMarketListingsPacket.ListingEntry> entries = new ArrayList<>();
        for (MarketListing listing : results) {
            String nationName = "";
            if (listing.getSellerNationId() != null) {
                nationName = StateCraftIntegration.getNationName(listing.getSellerNationId());
                if (nationName == null) nationName = "";
            }
            entries.add(new SyncMarketListingsPacket.ListingEntry(
                listing.getId(),
                listing.getItem(),
                listing.getItemName(),
                listing.getQuantity(),
                listing.getPricePerUnit(),
                listing.getSellerName(),
                listing.getSellerId(),
                nationName,
                listing.getListedTime()
            ));
        }

        com.statecraft.economy.network.NetworkHandler.sendToPlayer(
            new SyncMarketListingsPacket(entries, myListingsOnly), player);
    }
}

