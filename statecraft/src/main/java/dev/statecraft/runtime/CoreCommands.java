package dev.statecraft.runtime;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.statecraft.StateCraft;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.network.SuiteNetwork;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CoreCommands {
    @SubscribeEvent
    public void register(RegisterCommandsEvent event) {
        for (String alias : new String[]{"statecraft", "sc"}) {
            event.getDispatcher().register(Commands.literal(alias)
                    .executes(context -> open(context.getSource(), ""))
                    .then(Commands.literal("gui")
                            .executes(context -> open(context.getSource(), ""))
                            .then(Commands.argument("page", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        MenuRegistry.pages().forEach(page -> builder.suggest(page.id()));
                                        return builder.buildFuture();
                                    })
                                    .executes(context -> open(context.getSource(), StringArgumentType.getString(context, "page")))))
                    .then(Commands.literal("borders").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        SuiteNetwork.cycleBorders(player);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.argument("action", StringArgumentType.greedyString())
                            .executes(context -> execute(context.getSource(), StringArgumentType.getString(context, "action")))));
        }
    }

    private int open(CommandSourceStack source, String page) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Use /sc admin <action> from the server console."));
            return 0;
        }
        try {
            String resolved = page.isBlank()
                    ? MenuRegistry.pages().stream().filter(p -> p.id().startsWith("statecraft:"))
                            .findFirst().orElseThrow().id()
                    : page.contains(":") ? page : "statecraft:" + page;
            StateCraft.runtime().open(player, resolved);
            return Command.SINGLE_SUCCESS;
        } catch (UserError e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private int execute(CommandSourceStack source, String line) {
        ServerRuntime runtime = StateCraft.runtime();
        if (source.getEntity() instanceof ServerPlayer player) {
            ServerRuntime.Reply reply = runtime.invoke(player, "statecraft", line);
            if (reply.success()) {
                source.sendSuccess(() -> Component.literal(reply.text()), false);
            } else {
                source.sendFailure(Component.literal(reply.text()));
            }
            return reply.success() ? Command.SINGLE_SUCCESS : 0;
        }
        var spawn = source.getLevel().getSharedSpawnPos();
        Actor console = new Actor(new UUID(0, 0), "Server", source.hasPermission(2),
                source.getLevel().dimension().location().toString(), spawn.getX() >> 4, spawn.getZ() >> 4);
        ServerRuntime.Reply reply = runtime.invoke(console, "statecraft", line, () -> {
            var args = CommandLine.split(line);
            if (!console.admin() || args.isEmpty() || !args.get(0).equalsIgnoreCase("admin")) {
                throw new UserError("This command requires a player; console supports /sc admin <action>.");
            }
            return runtime.executeCore(console, line);
        });
        if (reply.success()) {
            source.sendSuccess(() -> Component.literal(reply.text()).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal(reply.text()));
        }
        return reply.success() ? Command.SINGLE_SUCCESS : 0;
    }
}
