package com.statecraft.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.statecraft.client.integration.JourneyMapIntegration;
import com.statecraft.integration.IntegrationRegistry;
import com.statecraft.integration.MinimapIntegration;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Commands for controlling map integration settings.
 *
 * Usage:
 *   /sc map layers - Show current layer visibility status
 *   /sc map layers nation <on|off> - Toggle nation layer
 *   /sc map layers state <on|off> - Toggle state layer
 *   /sc map layers city <on|off> - Toggle city layer
 *   /sc map layers all <on|off> - Toggle all layers
 *   /sc map refresh - Force refresh map overlays
 */
public class MapCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("map")
            .then(Commands.literal("layers")
                .executes(MapCommand::showLayerStatus)
                .then(Commands.literal("nation")
                    .then(Commands.literal("on")
                        .executes(ctx -> setLayerVisible(ctx, "nation", true)))
                    .then(Commands.literal("off")
                        .executes(ctx -> setLayerVisible(ctx, "nation", false))))
                .then(Commands.literal("state")
                    .then(Commands.literal("on")
                        .executes(ctx -> setLayerVisible(ctx, "state", true)))
                    .then(Commands.literal("off")
                        .executes(ctx -> setLayerVisible(ctx, "state", false))))
                .then(Commands.literal("city")
                    .then(Commands.literal("on")
                        .executes(ctx -> setLayerVisible(ctx, "city", true)))
                    .then(Commands.literal("off")
                        .executes(ctx -> setLayerVisible(ctx, "city", false))))
                .then(Commands.literal("all")
                    .then(Commands.literal("on")
                        .executes(ctx -> setAllLayersVisible(ctx, true)))
                    .then(Commands.literal("off")
                        .executes(ctx -> setAllLayersVisible(ctx, false)))))
            .then(Commands.literal("refresh")
                .executes(MapCommand::refreshMap))
            .then(Commands.literal("status")
                .executes(MapCommand::showStatus));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("§6=== Map Integration Status ==="), false);

        if (!IntegrationRegistry.hasMinimapIntegration()) {
            context.getSource().sendSuccess(() -> Component.literal("§7No minimap mods detected."), false);
            context.getSource().sendSuccess(() -> Component.literal("§8Install JourneyMap or Xaero's Minimap for territory overlays."), false);
            return 1;
        }

        for (MinimapIntegration integration : IntegrationRegistry.getMinimapIntegrations()) {
            String name = integration.getMinimapName();
            boolean available = integration.isAvailable();
            String status = available ? "§aActive" : "§cInactive";
            context.getSource().sendSuccess(() -> Component.literal("§7" + name + ": " + status), false);
        }

        return 1;
    }

    private static int showLayerStatus(CommandContext<CommandSourceStack> context) {
        JourneyMapIntegration jm = getJourneyMapIntegration();

        if (jm == null) {
            context.getSource().sendFailure(Component.literal("§cJourneyMap integration not available."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§6=== Map Layer Visibility ==="), false);

        String nationStatus = jm.isNationLayerVisible() ? "§aON" : "§cOFF";
        String stateStatus = jm.isStateLayerVisible() ? "§aON" : "§cOFF";
        String cityStatus = jm.isCityLayerVisible() ? "§aON" : "§cOFF";

        context.getSource().sendSuccess(() -> Component.literal("§7Nation Layer: " + nationStatus), false);
        context.getSource().sendSuccess(() -> Component.literal("§7State Layer: " + stateStatus), false);
        context.getSource().sendSuccess(() -> Component.literal("§7City Layer: " + cityStatus), false);

        context.getSource().sendSuccess(() -> Component.literal("§8Use /sc map layers <nation|state|city> <on|off> to toggle"), false);

        return 1;
    }

    private static int setLayerVisible(CommandContext<CommandSourceStack> context, String layer, boolean visible) {
        JourneyMapIntegration jm = getJourneyMapIntegration();

        if (jm == null) {
            context.getSource().sendFailure(Component.literal("§cJourneyMap integration not available."));
            return 0;
        }

        switch (layer.toLowerCase()) {
            case "nation" -> jm.setNationLayerVisible(visible);
            case "state" -> jm.setStateLayerVisible(visible);
            case "city" -> jm.setCityLayerVisible(visible);
            default -> {
                context.getSource().sendFailure(Component.literal("§cUnknown layer: " + layer));
                return 0;
            }
        }

        String status = visible ? "§aenabled" : "§cdisabled";
        String layerName = layer.substring(0, 1).toUpperCase() + layer.substring(1);
        context.getSource().sendSuccess(() -> Component.literal("§7" + layerName + " layer " + status), false);

        // Trigger a refresh
        IntegrationRegistry.notifyMinimapDimensionChange();

        return 1;
    }

    private static int setAllLayersVisible(CommandContext<CommandSourceStack> context, boolean visible) {
        JourneyMapIntegration jm = getJourneyMapIntegration();

        if (jm == null) {
            context.getSource().sendFailure(Component.literal("§cJourneyMap integration not available."));
            return 0;
        }

        jm.setNationLayerVisible(visible);
        jm.setStateLayerVisible(visible);
        jm.setCityLayerVisible(visible);

        String status = visible ? "§aenabled" : "§cdisabled";
        context.getSource().sendSuccess(() -> Component.literal("§7All map layers " + status), false);

        // Trigger a refresh
        IntegrationRegistry.notifyMinimapDimensionChange();

        return 1;
    }

    private static int refreshMap(CommandContext<CommandSourceStack> context) {
        // Trigger a full refresh by simulating dimension change then re-requesting data
        IntegrationRegistry.notifyMinimapDimensionChange();

        context.getSource().sendSuccess(() -> Component.literal("§aMap overlays refreshed."), false);
        return 1;
    }

    private static JourneyMapIntegration getJourneyMapIntegration() {
        for (MinimapIntegration integration : IntegrationRegistry.getMinimapIntegrations()) {
            if (integration instanceof JourneyMapIntegration jm) {
                return jm;
            }
        }
        return null;
    }
}

