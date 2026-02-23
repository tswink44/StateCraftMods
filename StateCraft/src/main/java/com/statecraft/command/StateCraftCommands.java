package com.statecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Root command registration for StateCraft
 * All commands are under /statecraft (alias /sc)
 */
public class StateCraftCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main command: /statecraft
        var statecraftCommand = Commands.literal("statecraft")
            .then(NationCommand.register())
            .then(StateCommand.register())
            .then(CityCommand.register())
            .then(ChunkCommand.register())
            .then(InfoCommand.register())
            .then(GuiCommand.register())
            .then(BordersCommand.register())
            .then(AdminCommand.register())
            .then(MailCommand.register())
            .then(ElectionCommand.register())
            .then(MapCommand.register());

        dispatcher.register(statecraftCommand);

        // Alias: /sc
        dispatcher.register(Commands.literal("sc").redirect(dispatcher.getRoot().getChild("statecraft")));

        // Shortcut commands
        dispatcher.register(Commands.literal("nation").redirect(dispatcher.getRoot().getChild("statecraft").getChild("nation")));
        dispatcher.register(Commands.literal("city").redirect(dispatcher.getRoot().getChild("statecraft").getChild("city")));
        dispatcher.register(Commands.literal("chunk").redirect(dispatcher.getRoot().getChild("statecraft").getChild("chunk")));
        dispatcher.register(Commands.literal("mail").redirect(dispatcher.getRoot().getChild("statecraft").getChild("mail")));
        dispatcher.register(Commands.literal("election").redirect(dispatcher.getRoot().getChild("statecraft").getChild("election")));
    }
}

