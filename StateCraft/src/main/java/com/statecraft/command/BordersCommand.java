package com.statecraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.SetBorderModePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to control chunk border rendering modes
 * /statecraft borders [off|chunks|mychunks|myterritory|all]
 */
public class BordersCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("borders")
            .executes(BordersCommand::cycleBorders)
            .then(Commands.literal("off")
                .executes(ctx -> setMode(ctx, 0)))
            .then(Commands.literal("chunks")
                .executes(ctx -> setMode(ctx, 1)))
            .then(Commands.literal("allchunks")
                .executes(ctx -> setMode(ctx, 1))) // Alias
            .then(Commands.literal("mychunks")
                .executes(ctx -> setMode(ctx, 2)))
            .then(Commands.literal("myterritory")
                .executes(ctx -> setMode(ctx, 3)))
            .then(Commands.literal("mynation")
                .executes(ctx -> setMode(ctx, 3))) // Alias
            .then(Commands.literal("all")
                .executes(ctx -> setMode(ctx, 4)))
            .then(Commands.literal("territory")
                .executes(ctx -> setMode(ctx, 4))); // Alias
    }

    private static int cycleBorders(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            NetworkHandler.sendToPlayer(new SetBorderModePacket(-1), player); // -1 = cycle
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }

    private static int setMode(CommandContext<CommandSourceStack> context, int mode) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            NetworkHandler.sendToPlayer(new SetBorderModePacket(mode), player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("This command must be run by a player!"));
            return 0;
        }
    }
}



