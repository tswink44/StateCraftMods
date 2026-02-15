package com.statecraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.core.*;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.OpenGuiPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Command to open the StateCraft GUI
 * /statecraft gui or /sc gui
 *
 * Subcommands:
 * /sc gui nation - Open nation info for current chunk's nation
 * /sc gui state - Open state info for current chunk's state
 * /sc gui city - Open city info for current chunk's city
 * /sc gui chunk - Open chunk info for current chunk
 */
public class GuiCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("gui")
            .executes(GuiCommand::openMainMenu)
            .then(Commands.literal("nation")
                .executes(GuiCommand::openNationGui))
            .then(Commands.literal("state")
                .executes(GuiCommand::openStateGui))
            .then(Commands.literal("city")
                .executes(GuiCommand::openCityGui))
            .then(Commands.literal("chunk")
                .executes(GuiCommand::openChunkGui))
            .then(Commands.literal("marketplace")
                .executes(GuiCommand::openMarketplaceGui));
    }

    private static int openMainMenu(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            NetworkHandler.sendToPlayer(new OpenGuiPacket(), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int openNationGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = player.chunkPosition();
            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            // Get chunk info
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            if (chunk == null) {
                // Check if player is in a nation
                Nation playerNation = manager.getPlayerNation(player.getUUID());
                if (playerNation != null) {
                    NetworkHandler.sendToPlayer(new OpenGuiPacket(
                        OpenGuiPacket.ScreenType.NATION_INFO,
                        playerNation.getName(), "", "",
                        chunkPos.x, chunkPos.z
                    ), player);
                    return 1;
                }
                context.getSource().sendFailure(Component.literal("§cThis chunk is wilderness and you're not in a nation!"));
                return 0;
            }

            // Get nation from chunk hierarchy
            City city = manager.getCity(chunk.getCityId());
            if (city == null) {
                context.getSource().sendFailure(Component.literal("§cCould not find city for this chunk!"));
                return 0;
            }

            State state = manager.getState(city.getStateId());
            if (state == null) {
                context.getSource().sendFailure(Component.literal("§cCould not find state for this chunk!"));
                return 0;
            }

            Nation nation = manager.getNation(state.getNationId());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("§cCould not find nation for this chunk!"));
                return 0;
            }

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.NATION_INFO,
                nation.getName(), state.getName(), city.getName(),
                chunkPos.x, chunkPos.z
            ), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int openStateGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = player.chunkPosition();
            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            // First check if player is in a nation
            Nation playerNation = manager.getPlayerNation(player.getUUID());
            if (playerNation == null) {
                context.getSource().sendFailure(Component.literal("§cYou are not part of a Nation."));
                return 0;
            }

            // Check if nation has any states
            if (playerNation.getAllStates().isEmpty()) {
                context.getSource().sendFailure(Component.literal("§cYour nation has no States. Create one to open this menu."));
                return 0;
            }

            // Try to find state from current chunk first
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());
            State state = null;
            City city = null;

            if (chunk != null) {
                city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    state = manager.getState(city.getStateId());
                }
            }

            // If not in a claimed chunk, use player's first state
            if (state == null) {
                state = playerNation.getAllStates().iterator().next();
            }

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.STATE_INFO,
                playerNation.getName(), state.getName(), city != null ? city.getName() : "",
                chunkPos.x, chunkPos.z
            ), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int openCityGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = player.chunkPosition();
            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            // First check if player is in a nation
            Nation playerNation = manager.getPlayerNation(player.getUUID());
            if (playerNation == null) {
                context.getSource().sendFailure(Component.literal("§cYou are not part of a Nation."));
                return 0;
            }

            // Check if nation has any cities
            boolean hasCities = false;
            City playerCity = null;
            State playerState = null;
            for (State state : playerNation.getAllStates()) {
                if (!state.getAllCities().isEmpty()) {
                    hasCities = true;
                    // Find player's city if they're a resident
                    for (City city : state.getAllCities()) {
                        if (city.isResident(player.getUUID())) {
                            playerCity = city;
                            playerState = state;
                            break;
                        }
                    }
                    if (playerCity != null) break;
                }
            }

            if (!hasCities) {
                context.getSource().sendFailure(Component.literal("§cYour nation has no Cities. Create one to open this menu."));
                return 0;
            }

            // Try to find city from current chunk first
            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());
            City city = null;
            State state = null;

            if (chunk != null) {
                city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    state = manager.getState(city.getStateId());
                }
            }

            // If not in a claimed chunk, use player's city or first available city
            if (city == null) {
                if (playerCity != null) {
                    city = playerCity;
                    state = playerState;
                } else {
                    // Use first city in nation
                    for (State s : playerNation.getAllStates()) {
                        if (!s.getAllCities().isEmpty()) {
                            city = s.getAllCities().iterator().next();
                            state = s;
                            break;
                        }
                    }
                }
            }

            if (city == null) {
                context.getSource().sendFailure(Component.literal("§cCould not find a city to display."));
                return 0;
            }

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.CITY_INFO,
                playerNation.getName(), state != null ? state.getName() : "", city.getName(),
                chunkPos.x, chunkPos.z
            ), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int openChunkGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = player.chunkPosition();
            ChunkClaimManager manager = ChunkClaimManager.getInstance();

            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            String nationName = "";
            String stateName = "";
            String cityName = "";

            if (chunk != null) {
                City city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    cityName = city.getName();
                    State state = manager.getState(city.getStateId());
                    if (state != null) {
                        stateName = state.getName();
                        Nation nation = manager.getNation(state.getNationId());
                        if (nation != null) {
                            nationName = nation.getName();
                        }
                    }
                }
            }

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.CHUNK_INFO,
                nationName, stateName, cityName,
                chunkPos.x, chunkPos.z
            ), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int openMarketplaceGui(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = player.chunkPosition();

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.MARKETPLACE,
                "", "", "",
                chunkPos.x, chunkPos.z
            ), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }
}

