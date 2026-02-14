package com.statecraft.economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.TransactionResult;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
                .executes(EconomyCommands::reload));

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

            if (!StateCraftIntegration.isNationAdmin(player)) {
                context.getSource().sendFailure(Component.literal("§cOnly nation admins can withdraw from treasury!"));
                return 0;
            }

            var nationId = StateCraftIntegration.getPlayerNationId(player);
            if (nationId == null) {
                context.getSource().sendFailure(Component.literal("§cYou are not in a nation!"));
                return 0;
            }

            EconomyManager manager = EconomyManager.getInstance();
            TransactionResult result = manager.withdrawFromNation(nationId, player.getUUID(), amount, "Nation withdrawal");

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

    private static String getPlayerName(CommandSourceStack source, java.util.UUID playerId) {
        var player = source.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) {
            return player.getName().getString();
        }
        var profile = source.getServer().getProfileCache().get(playerId);
        return profile.map(gameProfile -> gameProfile.getName()).orElse("Unknown");
    }
}

