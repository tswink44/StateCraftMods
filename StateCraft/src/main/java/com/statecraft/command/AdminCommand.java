package com.statecraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.Nation;
import com.statecraft.event.ProtectionHandler;
import com.statecraft.network.ServerPacketHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Admin commands for server operators
 * /statecraft admin bypass - Toggle protection bypass
 * /statecraft admin unclaim - Force unclaim a chunk
 * /statecraft admin delete <nation> - Force delete a nation
 * /statecraft admin reload - Reload configuration
 */
public class AdminCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("admin")
            .requires(source -> source.hasPermission(2)) // Require op level 2+
            .then(Commands.literal("bypass")
                .executes(AdminCommand::toggleBypass))
            .then(Commands.literal("unclaim")
                .executes(AdminCommand::forceUnclaimHere)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(AdminCommand::forceUnclaimAt)))
            .then(Commands.literal("delete")
                .then(Commands.argument("nation", com.mojang.brigadier.arguments.StringArgumentType.string())
                    .executes(AdminCommand::forceDeleteNation)))
            .then(Commands.literal("info")
                .executes(AdminCommand::adminInfo))
            .then(Commands.literal("setowner")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(AdminCommand::setChunkOwner)))
            .then(Commands.literal("reload")
                .executes(AdminCommand::reloadConfig))
            .then(Commands.literal("valuation")
                .then(Commands.literal("recalculate")
                    .executes(AdminCommand::recalculateAllValuations)
                    .then(Commands.argument("nation", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(AdminCommand::recalculateNationValuations)))
                .then(Commands.literal("clear")
                    .executes(AdminCommand::clearValuationCache))
                .then(Commands.literal("scanimprovements")
                    .executes(AdminCommand::scanChunkImprovements))
                .then(Commands.literal("status")
                    .executes(AdminCommand::valuationStatus)))
            .then(Commands.literal("tax")
                .then(Commands.literal("collect")
                    .executes(AdminCommand::forceCollectTax))
                .then(Commands.literal("status")
                    .executes(AdminCommand::showTaxStatus))
                .then(Commands.literal("enable")
                    .executes(ctx -> setTaxEnabled(ctx, true)))
                .then(Commands.literal("disable")
                    .executes(ctx -> setTaxEnabled(ctx, false)))
                .then(Commands.literal("period")
                    .then(Commands.argument("ticks", com.mojang.brigadier.arguments.LongArgumentType.longArg(1200))
                        .executes(AdminCommand::setTaxPeriod))))
            .then(ElectionCommand.registerAdmin())
            .then(LegislatureCommand.registerAdmin())
            .then(ContractCommand.registerAdmin());
    }

    /**
     * Toggle protection bypass for the executing player
     */
    private static int toggleBypass(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            boolean enabled = ProtectionHandler.toggleBypass(player.getUUID());

            if (enabled) {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§a§lBypass ENABLED §7- You can now interact in all claimed chunks."
                ), false);
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Use §e/sc admin bypass§7 again to disable."
                ), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal(
                    "§c§lBypass DISABLED §7- Normal protection rules apply."
                ), false);
            }

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    /**
     * Force unclaim the chunk the player is standing in
     */
    private static int forceUnclaimHere(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            BlockPos pos = player.blockPosition();
            return forceUnclaim(context, pos);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    /**
     * Force unclaim a chunk at a specific position
     */
    private static int forceUnclaimAt(CommandContext<CommandSourceStack> context) {
        try {
            BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
            return forceUnclaim(context, pos);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Invalid position!"));
            return 0;
        }
    }

    private static int forceUnclaim(CommandContext<CommandSourceStack> context, BlockPos pos) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        ChunkPos chunkPos = new ChunkPos(pos);

        var dimension = context.getSource().getLevel().dimension();
        var chunk = manager.getClaimedChunk(chunkPos, dimension);

        if (chunk == null) {
            context.getSource().sendFailure(Component.literal("This chunk is not claimed!"));
            return 0;
        }

        boolean success = manager.unclaimChunk(chunkPos, dimension);

        if (success) {
            // Broadcast chunk update to nearby players so borders are updated
            ServerLevel serverLevel = context.getSource().getLevel();
            ServerPacketHandler.broadcastChunkUpdateToNearby(serverLevel, chunkPos.x, chunkPos.z);

            context.getSource().sendSuccess(() -> Component.literal(
                "§c§lForce unclaimed§7 chunk at " + chunkPos.x + ", " + chunkPos.z
            ), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to unclaim chunk!"));
            return 0;
        }
    }

    /**
     * Force delete a nation
     */
    private static int forceDeleteNation(CommandContext<CommandSourceStack> context) {
        String nationName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "nation");

        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        Nation nation = manager.getNationByName(nationName);

        if (nation == null) {
            context.getSource().sendFailure(Component.literal("Nation '" + nationName + "' not found!"));
            return 0;
        }

        String name = nation.getName();
        int chunks = nation.getTotalChunkCount();
        int members = nation.getAllMembers().size();

        boolean success = manager.disbandNation(nation.getId());

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal(
                "§c§lForce deleted nation '§e" + name + "§c' §7(" + chunks + " chunks, " + members + " members)"
            ), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to delete nation!"));
            return 0;
        }
    }

    /**
     * Show admin info about the current chunk
     */
    private static int adminInfo(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            var chunk = manager.getClaimedChunk(chunkPos, player.level().dimension());

            context.getSource().sendSuccess(() -> Component.literal("§6=== Admin Chunk Info ==="), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Chunk: §f" + chunkPos.x + ", " + chunkPos.z
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Dimension: §f" + player.level().dimension().location()
            ), false);

            if (chunk == null) {
                context.getSource().sendSuccess(() -> Component.literal("§7Status: §aWilderness (unclaimed)"), false);
            } else {
                var city = manager.getCity(chunk.getCityId());
                if (city != null) {
                    var state = manager.getState(city.getStateId());
                    if (state != null) {
                        var nation = manager.getNation(state.getNationId());
                        if (nation != null) {
                            context.getSource().sendSuccess(() -> Component.literal(
                                "§7Nation: §e" + nation.getName() + " §8(ID: " + nation.getId() + ")"
                            ), false);
                        }
                        context.getSource().sendSuccess(() -> Component.literal(
                            "§7State: §f" + state.getName()
                        ), false);
                    }
                    context.getSource().sendSuccess(() -> Component.literal(
                        "§7City: §f" + city.getName()
                    ), false);
                }
                context.getSource().sendSuccess(() -> Component.literal(
                    "§7Chunk ID: §8" + chunk.getId()
                ), false);
            }

            // Bypass status
            boolean hasBypass = ProtectionHandler.hasBypass(player.getUUID());
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Your Bypass: " + (hasBypass ? "§aEnabled" : "§cDisabled")
            ), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    /**
     * Set the owner of the current chunk to a player
     */
    private static int setChunkOwner(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            ServerPlayer executor = context.getSource().getPlayerOrException();

            ChunkClaimManager manager = ChunkClaimManager.getInstance();
            ChunkPos chunkPos = new ChunkPos(executor.blockPosition());

            var chunk = manager.getClaimedChunk(chunkPos, executor.level().dimension());

            if (chunk == null) {
                context.getSource().sendFailure(Component.literal("This chunk is not claimed!"));
                return 0;
            }

            chunk.setOwnerId(target.getUUID());
            manager.markDirty();

            context.getSource().sendSuccess(() -> Component.literal(
                "§aSet chunk owner to §e" + target.getName().getString()
            ), true);

            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Reload configuration (placeholder for future config system)
     */
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        // TODO: Implement config reload when config system is added
        context.getSource().sendSuccess(() -> Component.literal(
            "§aConfiguration reloaded! §7(No config file yet - coming soon)"
        ), true);
        return 1;
    }

    // ==================== Valuation Admin Commands ====================

    /**
     * Recalculate all chunk valuations
     */
    private static int recalculateAllValuations(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> valuationManagerClass = Class.forName("com.statecraft.economy.valuation.ChunkValuationManager");
            Object manager = valuationManagerClass.getMethod("getInstance").invoke(null);

            // Mark all dirty and force recalculate
            valuationManagerClass.getMethod("markAllDirty").invoke(manager);
            valuationManagerClass.getMethod("forceRecalculateAllDirty").invoke(manager);

            // Get cache size
            int cacheSize = (Integer) valuationManagerClass.getMethod("getCacheSize").invoke(manager);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aRecalculated all " + cacheSize + " cached chunk valuations."
            ), true);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError recalculating valuations: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Recalculate chunk valuations for a specific nation
     */
    private static int recalculateNationValuations(CommandContext<CommandSourceStack> context) {
        String nationName = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "nation");

        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        Nation nation = manager.getNationByName(nationName);

        if (nation == null) {
            context.getSource().sendFailure(Component.literal("§cNation '" + nationName + "' not found!"));
            return 0;
        }

        try {
            Class<?> valuationManagerClass = Class.forName("com.statecraft.economy.valuation.ChunkValuationManager");
            Object valuationManager = valuationManagerClass.getMethod("getInstance").invoke(null);

            valuationManagerClass.getMethod("recalculateNationChunks", java.util.UUID.class)
                .invoke(valuationManager, nation.getId());

            context.getSource().sendSuccess(() -> Component.literal(
                "§aRecalculated chunk valuations for nation '§e" + nationName + "§a'."
            ), true);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError recalculating valuations: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Clear the entire valuation cache
     */
    private static int clearValuationCache(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> valuationManagerClass = Class.forName("com.statecraft.economy.valuation.ChunkValuationManager");
            Object manager = valuationManagerClass.getMethod("getInstance").invoke(null);

            int sizeBefore = (Integer) valuationManagerClass.getMethod("getCacheSize").invoke(manager);
            valuationManagerClass.getMethod("clearCache").invoke(manager);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aCleared valuation cache. §7(" + sizeBefore + " entries removed)"
            ), true);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Valuations will be recalculated on demand."
            ), false);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError clearing cache: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Show valuation cache status
     */
    private static int valuationStatus(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> valuationManagerClass = Class.forName("com.statecraft.economy.valuation.ChunkValuationManager");
            Object manager = valuationManagerClass.getMethod("getInstance").invoke(null);

            int cacheSize = (Integer) valuationManagerClass.getMethod("getCacheSize").invoke(manager);

            context.getSource().sendSuccess(() -> Component.literal("§6=== Valuation Cache Status ==="), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Cached Chunks: §f" + cacheSize
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Status: §aOperational"
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Commands:"
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§8  /sc admin valuation recalculate §7- Recalculate all"
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§8  /sc admin valuation recalculate <nation> §7- Recalculate nation"
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§8  /sc admin valuation scanimprovements §7- Rescan current chunk"
            ), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§8  /sc admin valuation clear §7- Clear cache"
            ), false);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendSuccess(() -> Component.literal("§6=== Valuation Cache Status ==="), false);
            context.getSource().sendSuccess(() -> Component.literal(
                "§7Status: §cStateCraft Economy mod not loaded"
            ), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError getting status: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Scan the current chunk for improvements (recalculate improvement score)
     */
    private static int scanChunkImprovements(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            String dimension = player.level().dimension().location().toString();

            Class<?> trackerClass = Class.forName("com.statecraft.economy.valuation.ImprovementTracker");
            Object tracker = trackerClass.getMethod("getInstance").invoke(null);

            // Call scanChunk method
            int score = (Integer) trackerClass.getMethod("scanChunk",
                    net.minecraft.server.MinecraftServer.class, int.class, int.class, String.class)
                .invoke(tracker, player.getServer(), chunkPos.x, chunkPos.z, dimension);

            context.getSource().sendSuccess(() -> Component.literal(
                "§aScanned chunk (§e" + chunkPos.x + ", " + chunkPos.z + "§a): Improvement score = §f" + score
            ), true);

            // Also recalculate the valuation
            Class<?> valuationManagerClass = Class.forName("com.statecraft.economy.valuation.ChunkValuationManager");
            Object valuationManager = valuationManagerClass.getMethod("getInstance").invoke(null);
            valuationManagerClass.getMethod("markDirty", int.class, int.class, String.class)
                .invoke(valuationManager, chunkPos.x, chunkPos.z, dimension);

            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError scanning chunk: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    // ==================== Tax Admin Commands ====================

    private static int forceCollectTax(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> taxManagerClass = Class.forName("com.statecraft.economy.core.TaxationManager");
            Object taxManager = taxManagerClass.getMethod("getInstance").invoke(null);

            context.getSource().sendSuccess(() -> Component.literal("§eForcing tax collection..."), true);
            taxManagerClass.getMethod("forceCollectNow", net.minecraft.server.MinecraftServer.class)
                .invoke(taxManager, context.getSource().getServer());
            context.getSource().sendSuccess(() -> Component.literal("§aTax collection complete!"), true);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError collecting taxes: " + e.getMessage()));
            return 0;
        }
    }

    private static int showTaxStatus(CommandContext<CommandSourceStack> context) {
        try {
            Class<?> taxManagerClass = Class.forName("com.statecraft.economy.core.TaxationManager");
            Object taxManager = taxManagerClass.getMethod("getInstance").invoke(null);

            boolean enabled = (Boolean) taxManagerClass.getMethod("isEnabled").invoke(taxManager);
            long periodTicks = (Long) taxManagerClass.getMethod("getTaxPeriodTicks").invoke(taxManager);
            long ticksUntilNext = (Long) taxManagerClass.getMethod("getTicksUntilNextCollection",
                net.minecraft.server.MinecraftServer.class).invoke(taxManager, context.getSource().getServer());

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
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError getting tax status: " + e.getMessage()));
            return 0;
        }
    }

    private static int setTaxEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        try {
            Class<?> taxManagerClass = Class.forName("com.statecraft.economy.core.TaxationManager");
            Object taxManager = taxManagerClass.getMethod("getInstance").invoke(null);
            taxManagerClass.getMethod("setEnabled", boolean.class).invoke(taxManager, enabled);

            String status = enabled ? "§aenabled" : "§cdisabled";
            context.getSource().sendSuccess(() -> Component.literal("§7Tax collection " + status), true);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError setting tax status: " + e.getMessage()));
            return 0;
        }
    }

    private static int setTaxPeriod(CommandContext<CommandSourceStack> context) {
        try {
            long ticks = com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "ticks");

            Class<?> taxManagerClass = Class.forName("com.statecraft.economy.core.TaxationManager");
            Object taxManager = taxManagerClass.getMethod("getInstance").invoke(null);
            taxManagerClass.getMethod("setTaxPeriodTicks", long.class).invoke(taxManager, ticks);

            long seconds = ticks / 20;
            long minutes = seconds / 60;
            long hours = minutes / 60;

            context.getSource().sendSuccess(() -> Component.literal(
                "§aTax period set to " + hours + "h " + (minutes % 60) + "m (" + ticks + " ticks)"), true);
            return 1;
        } catch (ClassNotFoundException e) {
            context.getSource().sendFailure(Component.literal("§cStateCraft Economy mod is not loaded!"));
            return 0;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cError setting tax period: " + e.getMessage()));
            return 0;
        }
    }
}

