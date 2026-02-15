package com.statecraft.event;

import com.statecraft.core.ElectionManager;
import com.statecraft.data.NationSavedData;
import com.statecraft.legislature.LegislatureManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles server tick events for election and legislature timing
 */
@Mod.EventBusSubscriber(modid = "statecraft")
public class ElectionTickHandler {

    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 20 * 60; // Check every minute (20 ticks * 60 seconds)

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        // Run election tick check
        ElectionManager electionManager = ElectionManager.getInstance();
        electionManager.tick(event.getServer());

        // Run legislature tick check (handles bill transitions)
        LegislatureManager legislatureManager = LegislatureManager.getInstance();
        legislatureManager.setServer(event.getServer()); // Ensure server reference is set
        legislatureManager.tick(event.getServer());

        // Save if any manager is dirty
        boolean needsSave = electionManager.isDirty() || legislatureManager.isDirty();
        if (needsSave) {
            ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
            if (overworld != null) {
                NationSavedData.get(overworld).setDirty();
            }
        }
    }
}

