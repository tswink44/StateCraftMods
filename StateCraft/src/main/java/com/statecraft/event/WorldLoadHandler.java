package com.statecraft.event;

import com.statecraft.StateCraft;
import com.statecraft.company.CompanyManager;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.core.InvitationManager;
import com.statecraft.data.BackupManager;
import com.statecraft.data.NationSavedData;
import com.statecraft.legislature.LegislatureManager;
import com.statecraft.mail.MailManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
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

        // Reset backup timer so first backup is one full interval after start
        BackupManager.getInstance().reset();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;
        BackupManager.getInstance().tick(event.getServer());
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

        if (ChunkClaimManager.getInstance().isDirty() ||
            InvitationManager.getInstance().isDirty() ||
            LegislatureManager.getInstance().isDirty() ||
            ContractManager.getInstance().isDirty() ||
            CompanyManager.getInstance().isDirty()) {
            NationSavedData data = NationSavedData.get(level);
            data.markForSave();
            StateCraft.LOGGER.debug("StateCraft data marked for save");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        StateCraft.LOGGER.info("Server stopping, saving and resetting StateCraft managers...");

        // Save mail data before shutdown
        MailManager.getInstance().save();

        // Run a final backup before shutdown
        BackupManager.getInstance().runBackup(event.getServer());

        // CRITICAL: Force-save nation data BEFORE resetting the managers.
        // If we reset first, SavedData.save() runs later with empty managers and wipes all data.
        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            // Clean up expired invitations before final save
            InvitationManager.getInstance().cleanupExpired();

            NationSavedData data = NationSavedData.get(overworld);
            data.markForSave();
            // Force the DataStorage to write immediately while managers still have data
            overworld.getDataStorage().save();
            StateCraft.LOGGER.info("StateCraft nation data saved before shutdown");
        }

        ChunkClaimManager.resetInstance();
        InvitationManager.resetInstance();
        LegislatureManager.resetInstance();
        ContractManager.resetInstance();
    }
}

