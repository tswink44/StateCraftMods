package com.statecraft.economy;

import com.statecraft.economy.block.ModBlocks;
import com.statecraft.economy.block.entity.ModBlockEntities;
import com.statecraft.economy.command.EconomyCommands;
import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.integration.StateCraftIntegration;
import com.statecraft.economy.item.ModItems;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.gui.ModMenuTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

        // Register game events
        MinecraftForge.EVENT_BUS.register(this);

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
        // Add ATM block to Functional Blocks creative tab
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.ATM_ITEM);
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
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher());
        LOGGER.info("StateCraft Economy commands registered");
    }

    public static boolean isStateCraftLoaded() {
        return stateCraftLoaded;
    }
}

