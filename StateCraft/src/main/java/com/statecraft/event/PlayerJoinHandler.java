package com.statecraft.event;

import com.statecraft.StateCraft;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.ElectionManager;
import com.statecraft.core.Nation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles player join/leave events
 */
public class PlayerJoinHandler {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if player is in a nation and send welcome message
        Nation nation = ChunkClaimManager.getInstance().getPlayerNation(player.getUUID());
        if (nation != null) {
            player.displayClientMessage(
                Component.literal("§6Welcome back to §e" + nation.getName() + "§6!"),
                false
            );

            // Notify of active elections
            ElectionManager.getInstance().onPlayerLogin(player, player.server);
        }

        StateCraft.LOGGER.debug("Player {} logged in", player.getName().getString());
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        StateCraft.LOGGER.debug("Player {} logged out", player.getName().getString());
    }
}

