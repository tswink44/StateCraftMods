package com.statecraft.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.SpendingLimitManager;
import com.statecraft.economy.core.TaxationManager;
import com.statecraft.economy.core.Transaction;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.integration.StateCraftIntegration;
import com.statecraft.economy.valuation.ChunkValuationManager;
import com.statecraft.economy.valuation.ChunkValuation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Commands for the economy system
 */
public class EconomyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main command: /economy or /eco
        var economyCommand = Commands.literal("economy")
            .then(Commands.literal("balance")
                .executes(EconomyCommands::showBalance)
                .then(Commands.argument("player", EntityArgument.player())
                    .requires(src -> src.hasPermission(2))
                    .executes(EconomyCommands::showOtherBalance)))
            .then(Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::pay))))
            .then(Commands.literal("give")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::give))))
            .then(Commands.literal("take")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(EconomyCommands::take))))
            .then(Commands.literal("set")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(EconomyCommands::setBalance))))
            .then(Commands.literal("top")
                .executes(EconomyCommands::showTop))
            .then(Commands.literal("reload")
                .requires(src -> src.hasPermission(2))
                .executes(EconomyCommands::reload))
            .then(Commands.literal("reloaditems")
                .requires(src -> src.hasPermission(2))
                .executes(EconomyCommands::reloadItemValues))
            .then(Commands.literal("setprice")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("item", StringArgumentType.string())
                    .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                        .executes(EconomyCommands::setItemPrice))))
            .then(Commands.literal("itemprice")
                .then(Commands.argument("item", StringArgumentType.string())
                    .executes(EconomyCommands::showItemPrice)))
            .then(Commands.literal("tax")
                .then(Commands.literal("history")
                    .executes(EconomyCommands::showTaxHistory))
                .then(Commands.literal("report")
                    .executes(EconomyCommands::showTaxReport))
                .then(Commands.literal("collect")
                    .requires(src -> src.hasPermission(2))
                    .executes(EconomyCommands::forceCollectTax))
                .then(Commands.literal("status")
                    .requires(src -> src.hasPermission(2))
                    .executes(EconomyCommands::showTaxStatus))
                .then(Commands.literal("enable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> setTaxEnabled(ctx, true)))
                .then(Commands.literal("disable")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> setTaxEnabled(ctx, false)))
                .then(Commands.literal("period")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("ticks", LongArgumentType.longArg(1200))
                        .executes(EconomyCommands::setTaxPeriod))))
            .then(CompanyCommands.buildCompanyCommand())
            .then(BankCommands.buildBankCommand())
            .then(MarketplaceCommands.buildMarketCommand())
            .then(Commands.literal("recipes")
                .executes(EconomyCommands::giveRecipeGuide));

        // Nation treasury commands (only if StateCraft is loaded)
        if (StateCraftEconomy.isStateCraftLoaded()) {
            economyCommand = economyCommand
                .then(Commands.literal("nation")
                    .then(Commands.literal("balance")
                        .executes(EconomyCommands::showNationBalance))
                    .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(EconomyCommands::depositToNation)))
                    .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                            .executes(EconomyCommands::withdrawFromNation))));
        }

        dispatcher.register(economyCommand);
        dispatcher.register(Commands.literal("eco").redirect(dispatcher.getRoot().getChild("economy")));
        dispatcher.register(Commands.literal("bal").executes(EconomyCommands::showBalance));
        dispatcher.register(Commands.literal("balance").executes(EconomyCommands::showBalance));
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(EconomyCommands::pay))));
    }

    private static int showBalance(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            EconomyManager manager = EconomyManager.getInstance();
            double balance = manager.getBalance(player.getUUID());

            context.getSource().sendSuccess(() ->
                Component.literal("§6Your Balance: §f" + manager.formatCurrency(balance)), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int giveRecipeGuide(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            net.minecraft.world.item.ItemStack guideBook = new net.minecraft.world.item.ItemStack(
                com.statecraft.economy.item.ModItems.RECIPE_GUIDE.get());
            if (!player.getInventory().add(guideBook)) {
                // Drop at player's feet if inventory is full
                player.drop(guideBook, false);
            }
            context.getSource().sendSuccess(() ->
                Component.literal("§aYou received a StateCraft Economy Recipe Guide!"), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int showOtherBalance(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            EconomyManager manager = EconomyManager.getInstance();
            double balance = manager.getBalance(target.getUUID());

            context.getSource().sendSuccess(() ->
                Component.literal("§6" + target.getName().getString() + "'s Balance: §f" + manager.formatCurrency(balance)), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found!"));
            return 0;
        }
    }

    private static int pay(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer sender = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            double amount = DoubleArgumentType.getDouble(context, "amount");

            EconomyManager manager = EconomyManager.getInstance();
            TransactionResult result = manager.transfer(sender.getUUID(), target.getUUID(), amount,
                "Payment to " + target.getName().getString());

            if (result.isSuccess()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.getMessage()), false);
                target.sendSystemMessage(Component.literal("§a" + sender.getName().getString() +
                    " paid you " + manager.formatCurrency(amount)));
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.getMessage()));
            }

            return result.isSuccess() ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int give(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            double amount = DoubleArgumentType.getDouble(context, "amount");

            EconomyManager manager = EconomyManager.getInstance();
            TransactionResult result = manager.deposit(target.getUUID(), amount, "Admin give");

            context.getSource().sendSuccess(() ->
                Component.literal("§aGave " + manager.formatCurrency(amount) + " to " + target.getName().getString()), false);
            target.sendSystemMessage(Component.literal("§aYou received " + manager.formatCurrency(amount)));

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int take(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            double amount = DoubleArgumentType.getDouble(context, "amount");

            EconomyManager manager = EconomyManager.getInstance();
            TransactionResult result = manager.withdraw(target.getUUID(), amount, "Admin take");

            if (result.isSuccess()) {
                context.getSource().sendSuccess(() ->
                    Component.literal("§aTook " + manager.formatCurrency(amount) + " from " + target.getName().getString()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.getMessage()));
            }

            return result.isSuccess() ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int setBalance(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            double amount = DoubleArgumentType.getDouble(context, "amount");

            EconomyManager manager = EconomyManager.getInstance();
            manager.getOrCreateAccount(target.getUUID()).setBalance(amount);
            manager.markDirty();

            context.getSource().sendSuccess(() ->
                Component.literal("§aSet " + target.getName().getString() + "'s balance to " + manager.formatCurrency(amount)), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showTop(CommandContext<CommandSourceStack> context) {
        EconomyManager manager = EconomyManager.getInstance();

        context.getSource().sendSuccess(() -> Component.literal("§6=== Richest Players ==="), false);

        var accounts = manager.getPlayerAccounts().entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().getBalance(), a.getValue().getBalance()))
            .limit(10)
            .toList();

        int rank = 1;
        for (var entry : accounts) {
            String name = getPlayerName(context.getSource(), entry.getKey());
            double balance = entry.getValue().getBalance();
            final int finalRank = rank;
            context.getSource().sendSuccess(() ->
                Component.literal("§e" + finalRank + ". §f" + name + " §7- §6" + manager.formatCurrency(balance)), false);
            rank++;
        }

        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        EconomyManager.getInstance().reloadCurrencyConfig();
        context.getSource().sendSuccess(() -> Component.literal("§aEconomy config reloaded!"), true);
        return 1;
    }

    private static int reloadItemValues(CommandContext<CommandSourceStack> context) {
        com.statecraft.economy.config.ItemValueRegistry.getInstance().reload();
        var values = com.statecraft.economy.config.ItemValueRegistry.getInstance().getAllValues();
        int count = values.size();

        // Sync updated values to all connected players
        com.statecraft.economy.network.NetworkHandler.sendToAll(
            new com.statecraft.economy.network.packets.SyncItemValuesPacket(values));

        context.getSource().sendSuccess(() ->
            Component.literal("§aItem values reloaded and synced to all players! §7(" + count + " items registered)"), true);
        return 1;
    }

    private static int setItemPrice(CommandContext<CommandSourceStack> context) {
        String itemId = StringArgumentType.getString(context, "item");
        double price = DoubleArgumentType.getDouble(context, "price");

        // Normalize item ID
        if (!itemId.contains(":")) {
            itemId = "minecraft:" + itemId;
        }

        var registry = com.statecraft.economy.config.ItemValueRegistry.getInstance();
        registry.setItemValue(itemId, price);
        registry.save();

        // Sync updated values to all connected players
        com.statecraft.economy.network.NetworkHandler.sendToAll(
            new com.statecraft.economy.network.packets.SyncItemValuesPacket(registry.getAllValues()));

        String finalItemId = itemId;
        if (price <= 0) {
            context.getSource().sendSuccess(() ->
                Component.literal("§eRemoved sell value for §f" + finalItemId), true);
        } else {
            context.getSource().sendSuccess(() ->
                Component.literal("§aSet §f" + finalItemId + " §asell value to §6$" + String.format("%.2f", price)), true);
        }
        return 1;
    }

    private static int showItemPrice(CommandContext<CommandSourceStack> context) {
        String itemId = StringArgumentType.getString(context, "item");
        if (!itemId.contains(":")) {
            itemId = "minecraft:" + itemId;
        }

        var registry = com.statecraft.economy.config.ItemValueRegistry.getInstance();
        double value = registry.getAllValues().getOrDefault(itemId, 0.0);

        String finalItemId = itemId;
        if (value > 0) {
            context.getSource().sendSuccess(() ->
                Component.literal("§7" + finalItemId + " §f= §6$" + String.format("%.2f", value) + " §7per unit"), false);
        } else {
            context.getSource().sendSuccess(() ->
                Component.literal("§7" + finalItemId + " §chas no sell value"), false);
        }
        return 1;
    }

    private static int showNationBalance(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double balance = StateCraftIntegration.getNationBalance(player);

            if (balance < 0) {
                context.getSource().sendFailure(Component.literal("§cYou are not in a nation!"));
                return 0;
            }

            context.getSource().sendSuccess(() ->
                Component.literal("§6Nation Treasury: §f" + EconomyManager.getInstance().formatCurrency(balance)), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int depositToNation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(context, "amount");

            var nationId = StateCraftIntegration.getPlayerNationId(player);
            if (nationId == null) {
                context.getSource().sendFailure(Component.literal("§cYou are not in a nation!"));
                return 0;
            }

            EconomyManager manager = EconomyManager.getInstance();
            TransactionResult result = manager.depositToNation(nationId, player.getUUID(), amount, "Nation deposit");

            if (result.isSuccess()) {
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.getMessage()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.getMessage()));
            }

            return result.isSuccess() ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int withdrawFromNation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            double amount = DoubleArgumentType.getDouble(context, "amount");

            if (!StateCraftIntegration.isNationLeaderOrOfficer(player)) {
                context.getSource().sendFailure(Component.literal("§cOnly the nation leader or officers can withdraw from treasury!"));
                return 0;
            }

            var nationId = StateCraftIntegration.getPlayerNationId(player);
            if (nationId == null) {
                context.getSource().sendFailure(Component.literal("§cYou are not in a nation!"));
                return 0;
            }

            // Check daily spending limit
            SpendingLimitManager spendingMgr = SpendingLimitManager.getInstance();
            SpendingLimitManager.GovernmentRole role =
                StateCraftIntegration.getPlayerGovernmentRole(player, "NATION", nationId);
            String limitError = spendingMgr.checkSpendingLimit(
                player.getUUID(), "NATION", nationId, amount, role, nationId, player);
            if (limitError != null) {
                context.getSource().sendFailure(Component.literal("§c" + limitError));
                return 0;
            }

            EconomyManager manager = EconomyManager.getInstance();
            TransactionResult result = manager.withdrawFromNation(nationId, player.getUUID(), amount, "Nation withdrawal");

            if (result.isSuccess()) {
                // Record spending against daily limit
                spendingMgr.recordSpending(player.getUUID(), "NATION", nationId, amount);
                context.getSource().sendSuccess(() -> Component.literal("§a" + result.getMessage()), false);
            } else {
                context.getSource().sendFailure(Component.literal("§c" + result.getMessage()));
            }

            return result.isSuccess() ? 1 : 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Tax Commands ====================

    /**
     * Show a player their property tax payment history and current chunk ownership summary.
     * Available to all players (no permission required).
     */
    private static int showTaxHistory(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            UUID playerId = player.getUUID();
            EconomyManager ecoManager = EconomyManager.getInstance();
            TaxationManager taxManager = TaxationManager.getInstance();

            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l=== Property Tax History ==="), false);

            // 1. Get player's current chunk ownership via StateCraft integration
            int chunksOwned = 0;
            double totalEstimatedNextTax = 0;
            Map<String, List<ChunkTaxEntry>> chunksByCity = new LinkedHashMap<>();

            if (StateCraftEconomy.isStateCraftLoaded() && StateCraftIntegration.isInitialized()) {
                List<TaxationManager.ChunkTaxInfo> allTaxable =
                    StateCraftIntegration.getAllTaxableChunks(context.getSource().getServer());

                ChunkValuationManager valuationManager = ChunkValuationManager.getInstance();

                for (TaxationManager.ChunkTaxInfo chunk : allTaxable) {
                    if (playerId.equals(chunk.getOwnerId())) {
                        chunksOwned++;

                        // Get chunk valuation and tax rate
                        ChunkValuation valuation = valuationManager.getValuation(
                            chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension());
                        double chunkValue = valuation.getTotalValue();
                        double taxRate = taxManager.getTaxRateForCity(chunk.getCityId());
                        double estimatedTax = chunkValue * taxRate;
                        totalEstimatedNextTax += estimatedTax;

                        // Group by city
                        String cityName = StateCraftIntegration.getCityName(chunk.getCityId());
                        if (cityName == null || cityName.isEmpty()) cityName = "Unknown City";

                        chunksByCity.computeIfAbsent(cityName, k -> new ArrayList<>())
                            .add(new ChunkTaxEntry(chunk.getChunkX(), chunk.getChunkZ(),
                                chunkValue, taxRate, estimatedTax));
                    }
                }
            }

            // 2. Scan transaction history for property tax payments
            List<Transaction> history = ecoManager.getTransactionHistory(playerId);
            double totalTaxPaid = 0;
            int taxPaymentCount = 0;
            long oldestTaxTimestamp = Long.MAX_VALUE;
            long newestTaxTimestamp = 0;

            for (Transaction tx : history) {
                String desc = tx.getDescription();
                if (desc != null && desc.contains("Property tax for chunk")) {
                    double amt = tx.getAmount();
                    totalTaxPaid += amt;
                    taxPaymentCount++;
                    if (tx.getTimestamp() < oldestTaxTimestamp) oldestTaxTimestamp = tx.getTimestamp();
                    if (tx.getTimestamp() > newestTaxTimestamp) newestTaxTimestamp = tx.getTimestamp();
                }
            }

            // 3. Display summary
            final int finalChunksOwned = chunksOwned;
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Chunks Owned: §f" + finalChunksOwned), false);

            double balance = ecoManager.getBalance(playerId);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Current Balance: §f" + ecoManager.formatCurrency(balance)), false);

            // Tax payment summary
            if (taxPaymentCount > 0) {
                final double finalTotalTaxPaid = totalTaxPaid;
                final int finalTaxPaymentCount = taxPaymentCount;

                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Total Tax Paid: §c" + ecoManager.formatCurrency(finalTotalTaxPaid) +
                    " §8(" + finalTaxPaymentCount + " payments)"), false);

                // Date range
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
                final String oldestStr = sdf.format(new Date(oldestTaxTimestamp));
                final String newestStr = sdf.format(new Date(newestTaxTimestamp));
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Period: §8" + oldestStr + " — " + newestStr), false);

                // Average per payment
                final double avgPerPayment = totalTaxPaid / taxPaymentCount;
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Avg per Collection: §c" + ecoManager.formatCurrency(avgPerPayment)), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Total Tax Paid: §a$0.00 §8(no tax payments on record)"), false);
            }

            // 4. Estimated next tax period
            if (chunksOwned > 0) {
                final double finalEstimate = totalEstimatedNextTax;
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Est. Next Period: §e" + ecoManager.formatCurrency(finalEstimate)), false);

                // Tax affordability check
                int periodsAffordable = totalEstimatedNextTax > 0
                    ? (int) Math.floor(balance / totalEstimatedNextTax)
                    : Integer.MAX_VALUE;

                if (periodsAffordable <= 0) {
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§c⚠ WARNING: Insufficient funds for next tax period!"), false);
                } else if (periodsAffordable <= 3) {
                    final int finalPeriods = periodsAffordable;
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§e⚠ Funds cover ~" + finalPeriods + " tax period(s)"), false);
                } else {
                    final int finalPeriods = periodsAffordable;
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§7Funds cover: §a~" + finalPeriods + " tax periods"), false);
                }
            }

            // 5. Per-city chunk breakdown (max 5 cities, 3 chunks each)
            if (!chunksByCity.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§6§l--- Per-City Breakdown ---"), false);

                int cityCount = 0;
                for (Map.Entry<String, List<ChunkTaxEntry>> entry : chunksByCity.entrySet()) {
                    if (cityCount >= 5) {
                        final int remaining = chunksByCity.size() - 5;
                        context.getSource().sendSuccess(() -> Component.literal(
                            "§8  +" + remaining + " more cities..."), false);
                        break;
                    }

                    String cityName = entry.getKey();
                    List<ChunkTaxEntry> chunks = entry.getValue();
                    double cityTotalTax = chunks.stream().mapToDouble(c -> c.estimatedTax).sum();

                    context.getSource().sendSuccess(() -> Component.literal(
                        "§e" + cityName + " §7(" + chunks.size() + " chunks, " +
                        "tax rate: §f" + String.format("%.1f%%", chunks.get(0).taxRate * 100) +
                        "§7, est: §c" + ecoManager.formatCurrency(cityTotalTax) + "§7)"), false);

                    // Show up to 3 individual chunks
                    int chunkCount = 0;
                    for (ChunkTaxEntry chunk : chunks) {
                        if (chunkCount >= 3) {
                            final int moreChunks = chunks.size() - 3;
                            context.getSource().sendSuccess(() -> Component.literal(
                                "§8    +" + moreChunks + " more chunks..."), false);
                            break;
                        }
                        context.getSource().sendSuccess(() -> Component.literal(
                            "§8    (" + chunk.chunkX + ", " + chunk.chunkZ + ") " +
                            "§7val: §f" + ecoManager.formatCurrency(chunk.value) +
                            " §7tax: §c" + ecoManager.formatCurrency(chunk.estimatedTax)), false);
                        chunkCount++;
                    }
                    cityCount++;
                }
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    /** Helper record for per-chunk tax breakdown display */
    private record ChunkTaxEntry(int chunkX, int chunkZ, double value, double taxRate, double estimatedTax) {}

    /**
     * Generate a comprehensive tax report summarizing all property taxes for a player.
     * Shows detailed breakdown by city, state, nation with valuation factors.
     */
    private static int showTaxReport(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            UUID playerId = player.getUUID();
            EconomyManager ecoManager = EconomyManager.getInstance();
            TaxationManager taxManager = TaxationManager.getInstance();

            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l╔════════════════════════════════════╗"), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l║      PROPERTY TAX REPORT           ║"), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l╚════════════════════════════════════╝"), false);

            if (!StateCraftEconomy.isStateCraftLoaded() || !StateCraftIntegration.isInitialized()) {
                context.getSource().sendFailure(Component.literal("§cStateCraft integration not available."));
                return 0;
            }

            // Collect all chunk data for this player
            List<TaxationManager.ChunkTaxInfo> allTaxable =
                StateCraftIntegration.getAllTaxableChunks(context.getSource().getServer());
            ChunkValuationManager valuationManager = ChunkValuationManager.getInstance();

            // Group chunks by nation -> state -> city
            Map<String, NationTaxData> nationData = new LinkedHashMap<>();
            int totalChunks = 0;
            double grandTotalValue = 0;
            double grandTotalTax = 0;

            for (TaxationManager.ChunkTaxInfo chunk : allTaxable) {
                if (!playerId.equals(chunk.getOwnerId())) continue;

                totalChunks++;

                // Get valuation details
                ChunkValuation valuation = valuationManager.getValuation(
                    chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension());
                double chunkValue = valuation.getTotalValue();
                double taxRate = taxManager.getTaxRateForCity(chunk.getCityId());
                double estimatedTax = chunkValue * taxRate;

                grandTotalValue += chunkValue;
                grandTotalTax += estimatedTax;

                // Get hierarchy names
                String cityName = StateCraftIntegration.getCityName(chunk.getCityId());
                String stateName = StateCraftIntegration.getStateName(StateCraftIntegration.getStateIdForCity(chunk.getCityId()));
                String nationName = StateCraftIntegration.getNationName(StateCraftIntegration.getNationIdForCity(chunk.getCityId()));

                if (cityName == null || cityName.isEmpty()) cityName = "Unknown City";
                if (stateName == null || stateName.isEmpty()) stateName = "Unknown State";
                if (nationName == null || nationName.isEmpty()) nationName = "Unknown Nation";

                // Build hierarchy
                NationTaxData nation = nationData.computeIfAbsent(nationName, k -> new NationTaxData());
                StateTaxData state = nation.states.computeIfAbsent(stateName, k -> new StateTaxData());
                CityTaxData city = state.cities.computeIfAbsent(cityName, k -> new CityTaxData(taxRate));

                city.chunks.add(new DetailedChunkEntry(
                    chunk.getChunkX(), chunk.getChunkZ(), chunk.getDimension(),
                    valuation.getBaseValue(),
                    valuation.getLocationMultiplier(),
                    valuation.getBiomeMultiplier(),
                    valuation.getDemandMultiplier(),
                    valuation.getGovernmentMultiplier(),
                    valuation.getImprovementMultiplier(),
                    valuation.getImprovementScore(),
                    chunkValue, estimatedTax
                ));
                city.totalValue += chunkValue;
                city.totalTax += estimatedTax;
                state.totalValue += chunkValue;
                state.totalTax += estimatedTax;
                nation.totalValue += chunkValue;
                nation.totalTax += estimatedTax;
            }

            if (totalChunks == 0) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7You don't own any taxable property."), false);
                return 1;
            }

            // Display summary header
            final int finalTotalChunks = totalChunks;
            final double finalGrandTotalValue = grandTotalValue;
            final double finalGrandTotalTax = grandTotalTax;
            double balance = ecoManager.getBalance(playerId);

            context.getSource().sendSuccess(() -> Component.literal(
                "§7Owner: §f" + player.getName().getString()), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Total Properties: §f" + finalTotalChunks + " chunks"), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Total Assessed Value: §e" + ecoManager.formatCurrency(finalGrandTotalValue)), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Est. Tax per Period: §c" + ecoManager.formatCurrency(finalGrandTotalTax)), false);

            // Tax period info
            long periodTicks = taxManager.getTaxPeriodTicks();
            long periodMinutes = (periodTicks / 20) / 60;
            long periodHours = periodMinutes / 60;
            String periodStr = periodHours > 0
                ? periodHours + "h " + (periodMinutes % 60) + "m"
                : periodMinutes + " minutes";
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Tax Period: §f" + periodStr), false);

            long ticksUntilNext = taxManager.getTicksUntilNextCollection(context.getSource().getServer());
            long nextMinutes = (ticksUntilNext / 20) / 60;
            long nextSeconds = (ticksUntilNext / 20) % 60;
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Next Collection: §f" + nextMinutes + "m " + nextSeconds + "s"), false);

            // Affordability analysis
            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l--- Financial Analysis ---"), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Current Balance: §f" + ecoManager.formatCurrency(balance)), false);

            int periodsAffordable = grandTotalTax > 0 ? (int) Math.floor(balance / grandTotalTax) : Integer.MAX_VALUE;
            if (periodsAffordable <= 0) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§c§l⚠ CRITICAL: Cannot afford next tax payment!"), false);
                double deficit = grandTotalTax - balance;
                context.getSource().sendSuccess(() -> Component.literal(
                    "§c  Deficit: " + ecoManager.formatCurrency(deficit)), false);
            } else if (periodsAffordable <= 3) {
                final int fp = periodsAffordable;
                context.getSource().sendSuccess(() -> Component.literal(
                    "§e⚠ Warning: Funds cover only ~" + fp + " tax period(s)"), false);
            } else {
                final int fp = periodsAffordable;
                context.getSource().sendSuccess(() -> Component.literal(
                    "§a✓ Funds cover ~" + fp + " tax periods"), false);
            }

            // Detailed breakdown by nation/state/city
            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l--- Breakdown by Jurisdiction ---"), false);

            for (Map.Entry<String, NationTaxData> nationEntry : nationData.entrySet()) {
                String nationName = nationEntry.getKey();
                NationTaxData nation = nationEntry.getValue();

                context.getSource().sendSuccess(() -> Component.literal(
                    "§d§l" + nationName + " §7(" +
                    ecoManager.formatCurrency(nation.totalValue) + " value, " +
                    ecoManager.formatCurrency(nation.totalTax) + " tax)"), false);

                for (Map.Entry<String, StateTaxData> stateEntry : nation.states.entrySet()) {
                    String stateName = stateEntry.getKey();
                    StateTaxData state = stateEntry.getValue();

                    context.getSource().sendSuccess(() -> Component.literal(
                        "  §b" + stateName + " §7(" +
                        ecoManager.formatCurrency(state.totalValue) + ", " +
                        ecoManager.formatCurrency(state.totalTax) + " tax)"), false);

                    for (Map.Entry<String, CityTaxData> cityEntry : state.cities.entrySet()) {
                        String cityName = cityEntry.getKey();
                        CityTaxData city = cityEntry.getValue();

                        context.getSource().sendSuccess(() -> Component.literal(
                            "    §a" + cityName + " §7[" + city.chunks.size() + " chunks, " +
                            String.format("%.1f%%", city.taxRate * 100) + " rate]"), false);
                        context.getSource().sendSuccess(() -> Component.literal(
                            "    §7  Value: §f" + ecoManager.formatCurrency(city.totalValue) +
                            " §7| Tax: §c" + ecoManager.formatCurrency(city.totalTax)), false);

                        // Show individual chunks with valuation breakdown (up to 5)
                        int shown = 0;
                        for (DetailedChunkEntry chunk : city.chunks) {
                            if (shown >= 5) {
                                final int remaining = city.chunks.size() - 5;
                                context.getSource().sendSuccess(() -> Component.literal(
                                    "    §8    +" + remaining + " more chunks..."), false);
                                break;
                            }
                            context.getSource().sendSuccess(() -> Component.literal(
                                "    §8    (" + chunk.chunkX + ", " + chunk.chunkZ + ") §7" +
                                abbreviateDimension(chunk.dimension)), false);
                            context.getSource().sendSuccess(() -> Component.literal(
                                "    §8      Base: §f" + ecoManager.formatCurrency(chunk.baseValue) +
                                " §8× Loc:" + String.format("%.2f", chunk.locationMult) +
                                " × Bio:" + String.format("%.2f", chunk.biomeMult) +
                                " × Dem:" + String.format("%.2f", chunk.demandMult) +
                                " × Gov:" + String.format("%.2f", chunk.govMult) +
                                " × Imp:" + String.format("%.2f", chunk.improvementMult)), false);
                            context.getSource().sendSuccess(() -> Component.literal(
                                "    §8      = §e" + ecoManager.formatCurrency(chunk.totalValue) +
                                " §8→ Tax: §c" + ecoManager.formatCurrency(chunk.estimatedTax)), false);
                            shown++;
                        }
                    }
                }
            }

            // Footer with tip
            context.getSource().sendSuccess(() -> Component.literal(
                "§6§l═══════════════════════════════════"), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Tip: Improve chunks to increase value. Keep funds available!"), false);

            return 1;
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error generating tax report", e);
            context.getSource().sendFailure(Component.literal("§cError generating tax report: " + e.getMessage()));
            return 0;
        }
    }

    /** Helper to abbreviate dimension names for display */
    private static String abbreviateDimension(String dimension) {
        if (dimension.contains("overworld")) return "OW";
        if (dimension.contains("the_nether")) return "Nether";
        if (dimension.contains("the_end")) return "End";
        return dimension.substring(dimension.lastIndexOf(':') + 1);
    }

    /** Helper classes for tax report data aggregation */
    private static class NationTaxData {
        Map<String, StateTaxData> states = new LinkedHashMap<>();
        double totalValue = 0;
        double totalTax = 0;
    }

    private static class StateTaxData {
        Map<String, CityTaxData> cities = new LinkedHashMap<>();
        double totalValue = 0;
        double totalTax = 0;
    }

    private static class CityTaxData {
        double taxRate;
        List<DetailedChunkEntry> chunks = new ArrayList<>();
        double totalValue = 0;
        double totalTax = 0;

        CityTaxData(double taxRate) {
            this.taxRate = taxRate;
        }
    }

    private record DetailedChunkEntry(
        int chunkX, int chunkZ, String dimension,
        double baseValue, double locationMult, double biomeMult,
        double demandMult, double govMult, double improvementMult,
        int improvementScore, double totalValue, double estimatedTax
    ) {}

    private static int forceCollectTax(CommandContext<CommandSourceStack> context) {
        TaxationManager taxManager = TaxationManager.getInstance();
        context.getSource().sendSuccess(() -> Component.literal("§eForcing tax collection..."), true);
        taxManager.forceCollectNow(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("§aTax collection complete!"), true);
        return 1;
    }

    private static int showTaxStatus(CommandContext<CommandSourceStack> context) {
        TaxationManager taxManager = TaxationManager.getInstance();

        boolean enabled = taxManager.isEnabled();
        long periodTicks = taxManager.getTaxPeriodTicks();
        long ticksUntilNext = taxManager.getTicksUntilNextCollection(context.getSource().getServer());

        // Convert ticks to readable time
        long periodSeconds = periodTicks / 20;
        long periodMinutes = periodSeconds / 60;
        long periodHours = periodMinutes / 60;

        long nextSeconds = ticksUntilNext / 20;
        long nextMinutes = nextSeconds / 60;

        context.getSource().sendSuccess(() -> Component.literal("§6=== Tax System Status ==="), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "§7Status: " + (enabled ? "§aEnabled" : "§cDisabled")), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "§7Tax Period: §f" + periodHours + "h " + (periodMinutes % 60) + "m (" + periodTicks + " ticks)"), false);
        context.getSource().sendSuccess(() -> Component.literal(
            "§7Next Collection: §f" + nextMinutes + "m " + (nextSeconds % 60) + "s"), false);

        return 1;
    }

    private static int setTaxEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        TaxationManager.getInstance().setEnabled(enabled);
        String status = enabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendSuccess(() -> Component.literal("§7Tax collection " + status), true);
        return 1;
    }

    private static int setTaxPeriod(CommandContext<CommandSourceStack> context) {
        long ticks = LongArgumentType.getLong(context, "ticks");
        TaxationManager.getInstance().setTaxPeriodTicks(ticks);

        long seconds = ticks / 20;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        context.getSource().sendSuccess(() -> Component.literal(
            "§aTax period set to " + hours + "h " + (minutes % 60) + "m (" + ticks + " ticks)"), true);
        return 1;
    }

    private static String getPlayerName(CommandSourceStack source, java.util.UUID playerId) {
        var player = source.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        var profile = source.getServer().getProfileCache().get(playerId);
        return profile.map(gameProfile -> gameProfile.getName()).orElse("Unknown");
    }
}

