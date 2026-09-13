package dev.statecraft;

import com.mojang.logging.LogUtils;
import dev.statecraft.config.ForgeConfigBinding;
import dev.statecraft.domain.CoreMenus;
import dev.statecraft.domain.GovernanceConfig;
import dev.statecraft.network.SuiteNetwork;
import dev.statecraft.runtime.CoreCommands;
import dev.statecraft.runtime.ProtectionEvents;
import dev.statecraft.runtime.ServerRuntime;
import dev.statecraft.runtime.UiMenus;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(StateCraft.MOD_ID)
public final class StateCraft {
    public static final String MOD_ID = "statecraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigBinding<GovernanceConfig> CONFIG = new ForgeConfigBinding<>(GovernanceConfig::new);
    private static volatile ServerRuntime active;

    public StateCraft() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CONFIG.spec());
        modBus.addListener(this::configLoading);
        modBus.addListener(this::configReloading);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CoreCommands());
        MinecraftForge.EVENT_BUS.register(new ProtectionEvents());
        CoreMenus.register();
        UiMenus.register();
        SuiteNetwork.register();
    }

    public static ServerRuntime runtime() {
        ServerRuntime runtime = active;
        if (runtime == null) {
            throw new IllegalStateException("StateCraft has no active server.");
        }
        return runtime;
    }

    public static ServerRuntime runtimeOrNull() {
        return active;
    }

    private void configLoading(ModConfigEvent.Loading event) {
        CONFIG.loaded(event.getConfig());
    }

    private void configReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != CONFIG.spec()) {
            return;
        }
        CONFIG.loaded(event.getConfig());
        ServerRuntime runtime = active;
        if (runtime != null) {
            runtime.server().execute(() -> runtime.reloadGovernance(false));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void starting(ServerStartingEvent event) {
        try {
            GovernanceConfig config = CONFIG.read();
            config.validate();
            active = new ServerRuntime(event.getServer(), config);
        } catch (IOException e) {
            throw new UncheckedIOException("StateCraft could not load world data. No data was reset.", e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void started(ServerStartedEvent event) {
        runtime().flush();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && active != null) {
            active.tick();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void worldSave(LevelEvent.Save event) {
        if (active != null && event.getLevel() == active.server().overworld()) {
            active.saveNow();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void stopping(ServerStoppingEvent event) {
        if (active != null) {
            active.saveNow();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void stopped(ServerStoppedEvent event) {
        active = null;
        SuiteNetwork.clear();
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player && active != null) {
            active.join(player);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (active != null) {
            active.leave(event.getEntity().getUUID());
        }
    }
}
