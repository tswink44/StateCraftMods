package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.Nation;
import com.statecraft.core.State;
import com.statecraft.data.NationSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for state management
 * /statecraft state create <name>
 * /statecraft state info [name]
 * /statecraft state list
 */
public class StateCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("state")
            .then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(StateCommand::createState)))
            .then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(StateCommand::stateInfo)))
            .then(Commands.literal("list")
                .executes(StateCommand::listStates));
    }

    private static int createState(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            if (!nation.isAdmin(player.getUUID())) {
                context.getSource().sendFailure(Component.literal("Only nation admins can create states!"));
                return 0;
            }

            if (name.length() < 3 || name.length() > 24) {
                context.getSource().sendFailure(Component.literal("State name must be 3-24 characters!"));
                return 0;
            }

            // Check for duplicate name
            if (nation.getStateByName(name) != null) {
                context.getSource().sendFailure(Component.literal("A state with that name already exists!"));
                return 0;
            }

            State state = ChunkClaimManager.getInstance().createState(nation, name, player.getUUID());
            if (state == null) {
                context.getSource().sendFailure(Component.literal("Could not create state. Max states may be reached."));
                return 0;
            }

            markDataDirty(context);
            context.getSource().sendSuccess(() -> Component.literal("§aState §e" + name + "§a created successfully!"), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int stateInfo(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            State state = nation.getStateByName(name);
            if (state == null) {
                context.getSource().sendFailure(Component.literal("State '" + name + "' not found!"));
                return 0;
            }

            context.getSource().sendSuccess(() -> Component.literal("§6=== State: §e" + state.getName() + " §6==="), false);
            context.getSource().sendSuccess(() -> Component.literal("§7Cities: §f" + state.getCityCount() + "/" + state.getMaxCities()), false);
            context.getSource().sendSuccess(() -> Component.literal("§7Claimed Chunks: §f" + state.getTotalChunkCount()), false);
            context.getSource().sendSuccess(() -> Component.literal("§7Residents: §f" + state.getAllResidents().size()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int listStates(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            var states = nation.getAllStates();
            if (states.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("§7No states exist in your nation yet."), false);
                return 1;
            }

            context.getSource().sendSuccess(() -> Component.literal("§6=== States in " + nation.getName() + " (" + states.size() + ") ==="), false);
            for (State state : states) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§e" + state.getName() + " §7- " + state.getCityCount() + " cities, " +
                    state.getTotalChunkCount() + " chunks"
                ), false);
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static void markDataDirty(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        NationSavedData.get(level).markForSave();
    }
}

