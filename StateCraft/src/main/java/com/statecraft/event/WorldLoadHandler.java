package com.statecraft.event;

import com.statecraft.StateCraft;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.InvitationManager;
import com.statecraft.data.NationSavedData;
import com.statecraft.mail.MailManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles world load/save events for data persistence
 */
public class WorldLoadHandler {

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // Only load data once from the overworld
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        StateCraft.LOGGER.info("Loading StateCraft nation data...");
        NationSavedData.get(level);

        // Initialize mail manager
        MailManager.getInstance().init(level.getServer());
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        // Only save from overworld
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        // Clean up expired invitations before saving
        InvitationManager.getInstance().cleanupExpired();

        // Save mail data
        MailManager.getInstance().save();

        if (ChunkClaimManager.getInstance().isDirty() || InvitationManager.getInstance().isDirty()) {
            NationSavedData data = NationSavedData.get(level);
            data.markForSave();
            StateCraft.LOGGER.debug("StateCraft data marked for save");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        StateCraft.LOGGER.info("Server stopping, resetting StateCraft managers...");

        // Save mail data before shutdown
        MailManager.getInstance().save();

        ChunkClaimManager.resetInstance();
        InvitationManager.resetInstance();
    }
}

