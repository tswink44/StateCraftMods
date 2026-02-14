package com.statecraft.client;

import com.statecraft.StateCraft;
import com.statecraft.client.render.ChunkBorderRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles client-side tick events for cache updates and chunk entry notifications
 */
@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Update chunk border cache
        ChunkBorderCache.tick();

        // Check for chunk change to show entry message
        int currentChunkX = mc.player.chunkPosition().x;
        int currentChunkZ = mc.player.chunkPosition().z;

        if (currentChunkX != lastChunkX || currentChunkZ != lastChunkZ) {
            onChunkChange(currentChunkX, currentChunkZ);
            lastChunkX = currentChunkX;
            lastChunkZ = currentChunkZ;
        }
    }

    private static void onChunkChange(int chunkX, int chunkZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
        ChunkBorderCache.ChunkClaimInfo oldInfo = ChunkBorderCache.getChunkInfo(lastChunkX, lastChunkZ);

        // Determine if we're entering a new territory
        boolean wasInTerritory = oldInfo != null && oldInfo.isClaimed();
        boolean nowInTerritory = info != null && info.isClaimed();

        // Only show message if territory changed
        if (nowInTerritory) {
            String oldNation = wasInTerritory ? oldInfo.getNationName() : null;
            String newNation = info.getNationName();

            if (!newNation.equals(oldNation)) {
                // Entering a different territory
                String color = info.isOwn() ? "§a" : (info.isAlly() ? "§9" : (info.isEnemy() ? "§c" : "§6"));

                // Build location string: Nation - State - City
                StringBuilder locationBuilder = new StringBuilder(newNation);
                if (info.getStateName() != null && !info.getStateName().isEmpty()) {
                    locationBuilder.append(" - ").append(info.getStateName());
                }
                if (info.getCityName() != null && !info.getCityName().isEmpty()) {
                    locationBuilder.append(" - ").append(info.getCityName());
                }

                mc.player.displayClientMessage(
                    Component.literal(color + "Entering: " + locationBuilder),
                    true // Action bar
                );
            }
        } else if (wasInTerritory && !nowInTerritory) {
            // Left territory, entering wilderness
            mc.player.displayClientMessage(
                Component.literal("§7Entering: Wilderness"),
                true
            );
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Clear cache when changing dimensions
        ChunkBorderCache.clear();
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }
}

