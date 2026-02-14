package com.statecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.core.*;
import com.statecraft.data.NationSavedData;
import com.statecraft.network.ServerPacketHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Commands for chunk claiming and management
 * /statecraft chunk claim <city>
 * /statecraft chunk unclaim
 * /statecraft chunk info
 * /statecraft chunk transfer <player>
 * /statecraft chunk map
 */
public class ChunkCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("chunk")
            .then(Commands.literal("claim")
                .then(Commands.argument("city", StringArgumentType.greedyString())
                    .executes(ChunkCommand::claimChunk)))
            .then(Commands.literal("unclaim")
                .executes(ChunkCommand::unclaimChunk))
            .then(Commands.literal("info")
                .executes(ChunkCommand::chunkInfo))
            .then(Commands.literal("transfer")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ChunkCommand::transferChunk)))
            .then(Commands.literal("map")
                .executes(ChunkCommand::showMap));
    }

    private static int claimChunk(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            String cityName = StringArgumentType.getString(context, "city");

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null) {
                context.getSource().sendFailure(Component.literal("You are not in a nation!"));
                return 0;
            }

            // Find the city
            City targetCity = null;
            for (State state : nation.getAllStates()) {
                City city = state.getCityByName(cityName);
                if (city != null) {
                    targetCity = city;
                    break;
                }
            }

            if (targetCity == null) {
                context.getSource().sendFailure(Component.literal("City '" + cityName + "' not found!"));
                return 0;
            }

            // Check permission - must be city mayor or nation admin
            if (!targetCity.getMayorId().equals(player.getUUID()) && !nation.isAdmin(player.getUUID())) {
                context.getSource().sendFailure(Component.literal("You don't have permission to claim chunks for this city!"));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            // Check if already claimed
            if (ChunkClaimManager.getInstance().isClaimed(chunkPos, player.level().dimension())) {
                context.getSource().sendFailure(Component.literal("This chunk is already claimed!"));
                return 0;
            }

            ClaimedChunk chunk = ChunkClaimManager.getInstance().claimChunk(targetCity, chunkPos, player.level().dimension());
            if (chunk == null) {
                context.getSource().sendFailure(Component.literal("Could not claim chunk. City may have reached its chunk limit."));
                return 0;
            }

            markDataDirty(context);
            final City city = targetCity;
            context.getSource().sendSuccess(() -> Component.literal(
                "§aChunk (" + chunkPos.x + ", " + chunkPos.z + ") claimed for city §e" + city.getName() + "§a!"
            ), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int unclaimChunk(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimedChunk chunk = ChunkClaimManager.getInstance().getClaimedChunk(chunkPos, player.level().dimension());
            if (chunk == null) {
                context.getSource().sendFailure(Component.literal("This chunk is not claimed!"));
                return 0;
            }

            // Check permission
            City city = ChunkClaimManager.getInstance().getCity(chunk.getCityId());
            if (city == null) {
                context.getSource().sendFailure(Component.literal("Error: City not found!"));
                return 0;
            }

            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            if (nation == null || (!city.getMayorId().equals(player.getUUID()) && !nation.isAdmin(player.getUUID()))) {
                context.getSource().sendFailure(Component.literal("You don't have permission to unclaim this chunk!"));
                return 0;
            }

            ChunkClaimManager.getInstance().unclaimChunk(chunkPos, player.level().dimension());
            markDataDirty(context);

            // Broadcast chunk update to nearby players so borders are updated
            if (player.level() instanceof ServerLevel serverLevel) {
                ServerPacketHandler.broadcastChunkUpdateToNearby(serverLevel, chunkPos.x, chunkPos.z);
            }

            context.getSource().sendSuccess(() -> Component.literal(
                "§aChunk (" + chunkPos.x + ", " + chunkPos.z + ") has been unclaimed!"
            ), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int chunkInfo(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimedChunk chunk = ChunkClaimManager.getInstance().getClaimedChunk(chunkPos, player.level().dimension());
            if (chunk == null) {
                context.getSource().sendSuccess(() -> Component.literal("§7This chunk is not claimed (Wilderness)"), false);
                return 1;
            }

            City city = ChunkClaimManager.getInstance().getCity(chunk.getCityId());
            State state = city != null ? ChunkClaimManager.getInstance().getState(city.getStateId()) : null;
            Nation nation = state != null ? ChunkClaimManager.getInstance().getNation(state.getNationId()) : null;

            context.getSource().sendSuccess(() -> Component.literal("§6=== Chunk Info (" + chunkPos.x + ", " + chunkPos.z + ") ==="), false);

            if (nation != null) {
                context.getSource().sendSuccess(() -> Component.literal("§7Nation: §e" + nation.getName()), false);
            }
            if (state != null) {
                context.getSource().sendSuccess(() -> Component.literal("§7State: §e" + state.getName()), false);
            }
            if (city != null) {
                context.getSource().sendSuccess(() -> Component.literal("§7City: §e" + city.getName()), false);
            }

            context.getSource().sendSuccess(() -> Component.literal("§7Ownership: §f" + chunk.getOwnershipType().name()), false);

            if (chunk.getOwnershipType() == OwnershipType.PLAYER && chunk.getPlayerOwner() != null) {
                // Try to get player name
                context.getSource().sendSuccess(() -> Component.literal("§7Owner: §f" + chunk.getPlayerOwner().toString()), false);
            }

            // Show player's permission level
            PermissionLevel role = ChunkClaimManager.getInstance().getPlayerRoleInChunk(player.getUUID(), chunk);
            context.getSource().sendSuccess(() -> Component.literal("§7Your Role: §f" + role.name()), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int transferChunk(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ClaimedChunk chunk = ChunkClaimManager.getInstance().getClaimedChunk(chunkPos, player.level().dimension());
            if (chunk == null) {
                context.getSource().sendFailure(Component.literal("This chunk is not claimed!"));
                return 0;
            }

            // Check permission - must be chunk owner or city mayor
            boolean canTransfer = false;
            if (chunk.getOwnershipType() == OwnershipType.PLAYER && player.getUUID().equals(chunk.getPlayerOwner())) {
                canTransfer = true;
            } else {
                City city = ChunkClaimManager.getInstance().getCity(chunk.getCityId());
                if (city != null && city.getMayorId().equals(player.getUUID())) {
                    canTransfer = true;
                }
                Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
                if (nation != null && nation.isAdmin(player.getUUID())) {
                    canTransfer = true;
                }
            }

            if (!canTransfer) {
                context.getSource().sendFailure(Component.literal("You don't have permission to transfer this chunk!"));
                return 0;
            }

            // Check if target is in the same nation
            Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
            Nation targetNation = ChunkClaimManager.getInstance().getPlayerNation(target.getUUID());
            if (nation == null || targetNation == null || !nation.getId().equals(targetNation.getId())) {
                context.getSource().sendFailure(Component.literal("You can only transfer chunks to players in your nation!"));
                return 0;
            }

            chunk.setPlayerOwner(target.getUUID());
            ChunkClaimManager.getInstance().markDirty();
            markDataDirty(context);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aChunk transferred to §e" + target.getName().getString() + "§a!"
            ), true);

            target.displayClientMessage(Component.literal(
                "§aYou now own the chunk at (" + chunkPos.x + ", " + chunkPos.z + ")!"
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showMap(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos centerChunk = new ChunkPos(player.blockPosition());

            context.getSource().sendSuccess(() -> Component.literal("§6=== Chunk Map ==="), false);
            context.getSource().sendSuccess(() -> Component.literal("§7(You are at " + centerChunk.x + ", " + centerChunk.z + ")"), false);

            // Show a 7x7 grid of chunks
            StringBuilder map = new StringBuilder();
            for (int z = -3; z <= 3; z++) {
                StringBuilder row = new StringBuilder();
                for (int x = -3; x <= 3; x++) {
                    ChunkPos pos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);
                    ClaimedChunk chunk = ChunkClaimManager.getInstance().getClaimedChunk(pos, player.level().dimension());

                    if (x == 0 && z == 0) {
                        row.append("§b@"); // Player position
                    } else if (chunk == null) {
                        row.append("§8-"); // Wilderness
                    } else {
                        // Check if it's the player's nation
                        City city = ChunkClaimManager.getInstance().getCity(chunk.getCityId());
                        if (city != null) {
                            State state = ChunkClaimManager.getInstance().getState(city.getStateId());
                            if (state != null) {
                                Nation nation = ChunkClaimManager.getInstance().getNation(state.getNationId());
                                Nation playerNation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
                                if (nation != null && playerNation != null) {
                                    if (nation.getId().equals(playerNation.getId())) {
                                        row.append("§a#"); // Your nation
                                    } else if (nation.isAlly(playerNation.getId())) {
                                        row.append("§e#"); // Ally
                                    } else if (nation.isEnemy(playerNation.getId())) {
                                        row.append("§c#"); // Enemy
                                    } else {
                                        row.append("§f#"); // Neutral claimed
                                    }
                                } else {
                                    row.append("§f#");
                                }
                            } else {
                                row.append("§f#");
                            }
                        } else {
                            row.append("§f#");
                        }
                    }
                }
                final String rowStr = row.toString();
                context.getSource().sendSuccess(() -> Component.literal(rowStr), false);
            }

            context.getSource().sendSuccess(() -> Component.literal("§7Legend: §b@ §7You §8- §7Wild §a# §7Your Nation §e# §7Ally §c# §7Enemy"), false);

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

