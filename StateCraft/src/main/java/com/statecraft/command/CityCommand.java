package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.config.StateCraftConfig;
import com.statecraft.core.*;
import com.statecraft.data.NationSavedData;
import com.statecraft.integration.IntegrationRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands for city management
 * /statecraft city create <state> <name>
 * /statecraft city info [name]
 * /statecraft city list
 * /statecraft city setpublic <true|false>
 */
public class CityCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("city")
            .then(Commands.literal("create")
                .then(Commands.argument("state", StringArgumentType.string())
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(CityCommand::createCity))))
            .then(Commands.literal("info")
                .executes(CityCommand::infoSelf)
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(CityCommand::infoOther)))
            .then(Commands.literal("list")
                .executes(CityCommand::listCities))
            .then(Commands.literal("setpublic")
                .then(Commands.argument("public", StringArgumentType.word())
                    .executes(CityCommand::setPublic)));
    }

    private static int createCity(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String stateName = StringArgumentType.getString(context, "state");
            String cityName = StringArgumentType.getString(context, "name");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            State state = nation.getStateByName(stateName);
            if (state == null) {
                context.getSource().sendFailure(Component.literal("State '" + stateName + "' not found!"));
                return 0;
            }

            // Check permission - must be state governor or nation admin
            if (!player.getUUID().equals(state.getGovernorId()) && !nation.isAdmin(player.getUUID())) {
                context.getSource().sendFailure(Component.literal("You don't have permission to create cities in this state!"));
                return 0;
            }

            if (cityName.length() < 3 || cityName.length() > 24) {
                context.getSource().sendFailure(Component.literal("City name must be 3-24 characters!"));
                return 0;
            }

            // Check for duplicate name in state
            if (state.getCityByName(cityName) != null) {
                context.getSource().sendFailure(Component.literal("A city with that name already exists in this state!"));
                return 0;
            }

            // Check creation fee
            double creationFee = StateCraftConfig.CITY_CREATION_FEE.get();
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                double playerBalance = IntegrationRegistry.getPlayerBalance(player.getUUID());
                if (playerBalance < creationFee) {
                    context.getSource().sendFailure(Component.literal(
                        "§cInsufficient funds! City creation costs " + IntegrationRegistry.formatCurrency(creationFee) +
                        ". You have " + IntegrationRegistry.formatCurrency(playerBalance) + "."));
                    return 0;
                }
            }

            // Check if player is already mayor of another city
            boolean vacantMayor = ChunkClaimManager.getInstance().isMayorOfAnyCity(player.getUUID());

            City city = ChunkClaimManager.getInstance().createCity(state, cityName, player.getUUID());
            if (city == null) {
                context.getSource().sendFailure(Component.literal("Could not create city. Max cities may be reached."));
                return 0;
            }

            // Charge creation fee after successful creation
            if (creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()) {
                boolean charged = IntegrationRegistry.withdrawFromPlayer(player.getUUID(), creationFee, "City creation fee: " + cityName);
                if (!charged) {
                    context.getSource().sendSystemMessage(Component.literal("§eWarning: Could not charge creation fee."));
                }
            }

            markDataDirty(context);
            String feeMessage = creationFee > 0 && IntegrationRegistry.hasEconomyIntegration()
                ? " (Cost: " + IntegrationRegistry.formatCurrency(creationFee) + ")" : "";
            if (vacantMayor) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§aCity §e" + cityName + "§a created in state §e" + stateName +
                    "§a with §eVACANT§a mayor position! Use /sc appoint to assign a mayor." + feeMessage), true);
            } else {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§aCity §e" + cityName + "§a created in state §e" + stateName + "§a!" + feeMessage), true);
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int infoSelf(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            // Find the city the player is mayor of or resident in
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            City foundCity = null;
            for (State state : nation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    if (player.getUUID().equals(city.getMayorId()) || city.isResident(player.getUUID())) {
                        foundCity = city;
                        break;
                    }
                }
                if (foundCity != null) break;
            }

            if (foundCity == null) {
                context.getSource().sendFailure(Component.literal("You are not a resident of any city!"));
                return 0;
            }

            sendCityInfo(context.getSource(), foundCity);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int infoOther(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String name = StringArgumentType.getString(context, "name");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            City foundCity = null;
            for (State state : nation.getAllStates()) {
                City city = state.getCityByName(name);
                if (city != null) {
                    foundCity = city;
                    break;
                }
            }

            if (foundCity == null) {
                context.getSource().sendFailure(Component.literal("City '" + name + "' not found!"));
                return 0;
            }

            sendCityInfo(context.getSource(), foundCity);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static void sendCityInfo(CommandSourceStack source, City city) {
        source.sendSuccess(() -> Component.literal("§6=== City: §e" + city.getName() + " §6==="), false);
        source.sendSuccess(() -> Component.literal("§7Claimed Chunks: §f" + city.getChunkCount()), false);
        source.sendSuccess(() -> Component.literal("§7Residents: §f" + city.getResidents().size()), false);
        source.sendSuccess(() -> Component.literal("§7Public Join: §f" + (city.isPublicJoin() ? "Yes" : "No")), false);
        if (!city.getDescription().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Description: §f" + city.getDescription()), false);
        }
    }

    private static int listCities(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            context.getSource().sendSuccess(() -> Component.literal("§6=== Cities in " + nation.getName() + " ==="), false);

            for (State state : nation.getAllStates()) {
                var cities = state.getAllCities();
                if (!cities.isEmpty()) {
                    context.getSource().sendSuccess(() -> Component.literal("§e" + state.getName() + ":"), false);
                    for (City city : cities) {
                        context.getSource().sendSuccess(() -> Component.literal(
                            "  §7- §f" + city.getName() + " §7(" + city.getChunkCount() + " chunks, " +
                            city.getResidents().size() + " residents)"
                        ), false);
                    }
                }
            }
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int setPublic(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String publicStr = StringArgumentType.getString(context, "public");
            boolean isPublic = publicStr.equalsIgnoreCase("true") || publicStr.equalsIgnoreCase("yes");

            // Find city the player is mayor of
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            City mayorCity = null;
            for (State state : nation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    if (player.getUUID().equals(city.getMayorId())) {
                        mayorCity = city;
                        break;
                    }
                }
                if (mayorCity != null) break;
            }

            if (mayorCity == null) {
                context.getSource().sendFailure(Component.literal("You are not a mayor of any city!"));
                return 0;
            }

            mayorCity.setPublicJoin(isPublic);
            ChunkClaimManager.getInstance().markDirty();
            markDataDirty(context);

            final City city = mayorCity;
            context.getSource().sendSuccess(() -> Component.literal(
                "§aCity §e" + city.getName() + "§a is now " + (isPublic ? "§eopen§a to new residents!" : "§eclosed§a to new residents!")
            ), true);
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

