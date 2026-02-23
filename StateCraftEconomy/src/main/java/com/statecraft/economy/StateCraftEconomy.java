package com.statecraft.economy;

import com.statecraft.economy.block.ModBlocks;
import com.statecraft.economy.block.entity.ModBlockEntities;
import com.statecraft.economy.command.EconomyCommands;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.config.ItemValueConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.TaxationManager;
import com.statecraft.economy.integration.StateCraftIntegration;
import com.statecraft.economy.item.ModItems;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.gui.ModMenuTypes;
import com.statecraft.economy.valuation.ChunkValuationManager;
import com.statecraft.economy.valuation.ImprovementTracker;
import com.statecraft.economy.valuation.ValuationConfig;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StateCraft Economy - An economy mod with ATM, currency, and StateCraft integration
 *
 * Features:
 * - Configurable currency items
 * - ATM block for deposits, withdrawals, transfers
 * - Player bank accounts
 * - Integration with StateCraft nation treasuries
 */
@Mod(StateCraftEconomy.MOD_ID)
public class StateCraftEconomy {
    public static final String MOD_ID = "statecraft_economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean stateCraftLoaded = false;

    public StateCraftEconomy() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register mod lifecycle events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        // Register items, blocks, etc.
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EconomyConfig.SPEC, "statecraft-economy.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ValuationConfig.SPEC, "statecraft-valuation.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ItemValueConfig.SPEC, "statecraft-item-values.toml");

        // Register game events
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ImprovementTracker.getInstance());

        LOGGER.info("StateCraft Economy initializing...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();

            // Check for StateCraft integration
            stateCraftLoaded = ModList.get().isLoaded("statecraft");
            if (stateCraftLoaded) {
                LOGGER.info("StateCraft detected! Enabling nation treasury integration.");
                StateCraftIntegration.init();
            } else {
                LOGGER.info("StateCraft not detected. Running in standalone mode.");
            }
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Add ATM block and Trading Hub to Functional Blocks creative tab
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.ATM_ITEM);
            event.accept(ModBlocks.TRADING_HUB_ITEM);
            event.accept(ModBlocks.COMPANY_VAULT_ITEM);
            event.accept(ModBlocks.MARKETPLACE_ITEM);
            event.accept(ModBlocks.STOCK_MARKET_ITEM);
        }

        // Add currency bills to Tools & Utilities creative tab
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.BILL_1);
            event.accept(ModItems.BILL_10);
            event.accept(ModItems.BILL_100);
            event.accept(ModItems.BILL_1000);
            event.accept(ModItems.BILL_10000);
            event.accept(ModItems.BILL_100000);
            event.accept(ModItems.BILL_1000000);
            event.accept(ModItems.BANK_CARD);
            event.accept(ModItems.RECIPE_GUIDE);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
        LOGGER.info("StateCraft Economy commands registered");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // Initialize item value registry (JSON-based)
        com.statecraft.economy.config.ItemValueRegistry.getInstance().init();
        LOGGER.info("Item value registry initialized");

        // Initialize valuation and improvement tracking
        ChunkValuationManager.getInstance().init(event.getServer());
        ImprovementTracker.getInstance().init(event.getServer());
        LOGGER.info("Chunk valuation system initialized");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Sync item value registry to the client so Trading Hub UI shows correct prices
        var registry = com.statecraft.economy.config.ItemValueRegistry.getInstance();
        var values = registry.getAllValues();
        if (!values.isEmpty()) {
            NetworkHandler.sendToPlayer(
                new com.statecraft.economy.network.packets.SyncItemValuesPacket(values), player);
            LOGGER.debug("Synced {} item values to player {}", values.size(), player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // Save valuation cache and improvement scores
        ChunkValuationManager.getInstance().saveCache();
        ImprovementTracker.getInstance().save();
        LOGGER.info("Chunk valuation data saved");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;

        // Run taxation system tick
        if (stateCraftLoaded) {
            TaxationManager.getInstance().tick(event.getServer());
        }

        // Run company dividend tick
        com.statecraft.economy.company.CompanyEconomyManager.getInstance().tick(event.getServer());

        // Run bank interest and loan tick
        com.statecraft.economy.company.BankManager.getInstance().tick(event.getServer());

        // Run marketplace listing expiration tick
        com.statecraft.economy.marketplace.MarketplaceManager.getInstance().tick(event.getServer());

        // Run valuation cache tick
        ChunkValuationManager.getInstance().tick(event.getServer());
    }

    public static boolean isStateCraftLoaded() {
        return stateCraftLoaded;
    }
}

