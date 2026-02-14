package com.statecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.statecraft.StateCraft;
import com.statecraft.client.gui.MainMenuScreen;
import com.statecraft.client.render.ChunkBorderRenderer;
import com.statecraft.client.render.TerritoryBorderRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keybinding registration and input for opening the StateCraft GUI
 */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.categories.statecraft";
    public static final String KEY_OPEN_MENU = "key.statecraft.open_menu";
    public static final String KEY_TOGGLE_BORDERS = "key.statecraft.toggle_borders";

    public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
        KEY_OPEN_MENU,
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_N, // Default: N key
        KEY_CATEGORY
    );

    public static final KeyMapping TOGGLE_BORDERS_KEY = new KeyMapping(
        KEY_TOGGLE_BORDERS,
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B, // Default: B key
        KEY_CATEGORY
    );

    @Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU_KEY);
            event.register(TOGGLE_BORDERS_KEY);
            StateCraft.LOGGER.info("StateCraft keybindings registered");
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();

        if (OPEN_MENU_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MainMenuScreen());
            }
        }

        if (TOGGLE_BORDERS_KEY.consumeClick()) {
            // Cycle through border modes
            TerritoryBorderRenderer.cycleMode();
            TerritoryBorderRenderer.BorderMode mode = TerritoryBorderRenderer.getMode();

            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("§7Border Mode: " + mode.getDisplayText()),
                    true
                );
            }
        }
    }
}

