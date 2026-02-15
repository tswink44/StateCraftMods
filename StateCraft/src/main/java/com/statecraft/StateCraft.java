package com.statecraft;

import com.statecraft.command.StateCraftCommands;
import com.statecraft.core.ChunkClaimManager;
import com.statecraft.event.PlayerJoinHandler;
import com.statecraft.event.WorldLoadHandler;
import com.statecraft.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * StateCraft - A nation and territory management mod
 *
 * Hierarchy: Nation -> State -> City -> Chunk
 * Chunks can be owned by the hierarchy or by individual players
 */
@Mod(StateCraft.MOD_ID)
public class StateCraft {
    public static final String MOD_ID = "statecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MinecraftServer server;

    public StateCraft() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register mod lifecycle events
        modEventBus.addListener(this::commonSetup);

        // Register game events
        // Note: ProtectionHandler is registered via @Mod.EventBusSubscriber annotation
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new PlayerJoinHandler());
        MinecraftForge.EVENT_BUS.register(new WorldLoadHandler());

        LOGGER.info("StateCraft initializing...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            LOGGER.info("StateCraft network handler registered");
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        StateCraftCommands.register(event.getDispatcher());
        LOGGER.info("StateCraft commands registered");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        server = null;
    }

    @Nullable
    public static MinecraftServer getServer() {
        return server;
    }
}

