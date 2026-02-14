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
                .executes(GuiCommand::openChunkGui));
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

            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            if (chunk == null) {
                context.getSource().sendFailure(Component.literal("§cThis chunk is wilderness - no state to view!"));
                return 0;
            }

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
            String nationName = nation != null ? nation.getName() : "";

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.STATE_INFO,
                nationName, state.getName(), city.getName(),
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

            ClaimedChunk chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            if (chunk == null) {
                context.getSource().sendFailure(Component.literal("§cThis chunk is wilderness - no city to view!"));
                return 0;
            }

            City city = manager.getCity(chunk.getCityId());
            if (city == null) {
                context.getSource().sendFailure(Component.literal("§cCould not find city for this chunk!"));
                return 0;
            }

            State state = manager.getState(city.getStateId());
            String stateName = state != null ? state.getName() : "";

            Nation nation = state != null ? manager.getNation(state.getNationId()) : null;
            String nationName = nation != null ? nation.getName() : "";

            NetworkHandler.sendToPlayer(new OpenGuiPacket(
                OpenGuiPacket.ScreenType.CITY_INFO,
                nationName, stateName, city.getName(),
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
}

