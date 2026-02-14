package com.statecraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.core.ChunkClaimManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * General info and help commands
 * /statecraft info
 * /statecraft help
 */
public class InfoCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("info")
            .executes(InfoCommand::showInfo)
            .then(Commands.literal("help")
                .executes(InfoCommand::showHelp));
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();

        context.getSource().sendSuccess(() -> Component.literal("§6=== StateCraft Info ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Total Nations: §f" + manager.getTotalNationCount()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Total Claimed Chunks: §f" + manager.getTotalClaimedChunks()), false);
        context.getSource().sendSuccess(() -> Component.literal("§7Use §e/sc info help §7for command list"), false);

        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§6=== StateCraft Commands ==="), false);
        context.getSource().sendSuccess(() -> Component.literal("§e/sc nation §7- Nation management"), false);
        context.getSource().sendSuccess(() -> Component.literal("  §7create, info, list, invite, kick, leave, disband"), false);
        context.getSource().sendSuccess(() -> Component.literal("§e/sc state §7- State management"), false);
        context.getSource().sendSuccess(() -> Component.literal("  §7create, info, list"), false);
        context.getSource().sendSuccess(() -> Component.literal("§e/sc city §7- City management"), false);
        context.getSource().sendSuccess(() -> Component.literal("  §7create, info, list, setpublic"), false);
        context.getSource().sendSuccess(() -> Component.literal("§e/sc chunk §7- Chunk claiming"), false);
        context.getSource().sendSuccess(() -> Component.literal("  §7claim, unclaim, info, transfer, map"), false);

        return 1;
    }
}


