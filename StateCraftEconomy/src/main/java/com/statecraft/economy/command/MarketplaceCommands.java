package com.statecraft.economy.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.economy.marketplace.MarketListing;
import com.statecraft.economy.marketplace.MarketplaceManager;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.MarketplaceActionPacket;
import com.statecraft.economy.network.packets.OpenMarketplaceScreenPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Commands for the marketplace system.
 * /eco market list — browse active listings
 * /eco market sell <price> [quantity] — list held item
 * /eco market cancel <id> — cancel own listing
 * /eco market search <query> — search listings
 * /eco market open — open the marketplace GUI
 */
public class MarketplaceCommands {

    public static LiteralArgumentBuilder<CommandSourceStack> buildMarketCommand() {
        return Commands.literal("market")
            .then(Commands.literal("list")
                .executes(MarketplaceCommands::listAll))
            .then(Commands.literal("search")
                .then(Commands.argument("query", StringArgumentType.greedyString())
                    .executes(MarketplaceCommands::searchListings)))
            .then(Commands.literal("sell")
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> sellItem(ctx, -1))
                    .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                        .executes(ctx -> sellItem(ctx, IntegerArgumentType.getInteger(ctx, "quantity"))))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("listingId", StringArgumentType.string())
                    .executes(MarketplaceCommands::cancelListing)))
            .then(Commands.literal("my")
                .executes(MarketplaceCommands::myListings))
            .then(Commands.literal("open")
                .executes(MarketplaceCommands::openGui));
    }

    private static int listAll(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            List<MarketListing> listings = MarketplaceManager.getInstance().getActiveListings();

            if (listings.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7No active marketplace listings."));
                return 1;
            }

            player.sendSystemMessage(Component.literal("§6§l=== Marketplace Listings ==="));
            int shown = 0;
            for (MarketListing listing : listings) {
                if (shown >= 20) {
                    player.sendSystemMessage(Component.literal("§7... and " + (listings.size() - 20) + " more. Use /eco market open for full view."));
                    break;
                }
                player.sendSystemMessage(Component.literal(String.format(
                    "§f%s §7x%d §a$%,.2f/ea §7by §f%s §8[%s]",
                    listing.getItemName(), listing.getQuantity(), listing.getPricePerUnit(),
                    listing.getSellerName(), listing.getId().toString().substring(0, 8))));
                shown++;
            }
            player.sendSystemMessage(Component.literal("§7Total: " + listings.size() + " listings"));
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int searchListings(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String query = StringArgumentType.getString(context, "query");
            List<MarketListing> results = MarketplaceManager.getInstance().searchListings(query);

            if (results.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7No listings found for: " + query));
                return 1;
            }

            player.sendSystemMessage(Component.literal("§6§l=== Search: " + query + " ==="));
            for (MarketListing listing : results) {
                player.sendSystemMessage(Component.literal(String.format(
                    "§f%s §7x%d §a$%,.2f/ea §7by §f%s §8[%s]",
                    listing.getItemName(), listing.getQuantity(), listing.getPricePerUnit(),
                    listing.getSellerName(), listing.getId().toString().substring(0, 8))));
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int sellItem(CommandContext<CommandSourceStack> context, int requestedQty) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double price = DoubleArgumentType.getDouble(context, "price");

            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.isEmpty()) {
                player.sendSystemMessage(Component.literal("§cYou must hold an item to sell it!"));
                return 0;
            }

            int qty = requestedQty > 0 ? Math.min(requestedQty, heldItem.getCount()) : heldItem.getCount();

            var result = MarketplaceManager.getInstance().createListing(player, heldItem, qty, price);
            String prefix = result.success() ? "§a" : "§c";
            player.sendSystemMessage(Component.literal(prefix + result.message()));
            return result.success() ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int cancelListing(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String idStr = StringArgumentType.getString(context, "listingId");
            UUID listingId;
            try {
                listingId = UUID.fromString(idStr);
            } catch (IllegalArgumentException e) {
                // Try partial match
                listingId = findListingByPartialId(idStr, player.getUUID());
                if (listingId == null) {
                    player.sendSystemMessage(Component.literal("§cInvalid listing ID: " + idStr));
                    return 0;
                }
            }

            var result = MarketplaceManager.getInstance().cancelListing(player, listingId);
            String prefix = result.success() ? "§a" : "§c";
            player.sendSystemMessage(Component.literal(prefix + result.message()));
            return result.success() ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int myListings(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            List<MarketListing> listings = MarketplaceManager.getInstance().getListingsBySeller(player.getUUID());

            if (listings.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7You have no active listings."));
                return 1;
            }

            player.sendSystemMessage(Component.literal("§6§l=== Your Listings ==="));
            for (MarketListing listing : listings) {
                player.sendSystemMessage(Component.literal(String.format(
                    "§f%s §7x%d §a$%,.2f/ea §8[%s]",
                    listing.getItemName(), listing.getQuantity(), listing.getPricePerUnit(),
                    listing.getId().toString().substring(0, 8))));
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int openGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            NetworkHandler.sendToPlayer(new OpenMarketplaceScreenPacket(), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Find a listing by partial UUID match (first 8 chars).
     */
    private static UUID findListingByPartialId(String partial, UUID sellerId) {
        String lower = partial.toLowerCase();
        for (MarketListing listing : MarketplaceManager.getInstance().getListingsBySeller(sellerId)) {
            if (listing.getId().toString().toLowerCase().startsWith(lower)) {
                return listing.getId();
            }
        }
        return null;
    }
}

