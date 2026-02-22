package com.statecraft.economy.client;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.client.screen.ATMScreen;
import com.statecraft.economy.client.screen.CompanyVaultScreen;
import com.statecraft.economy.client.screen.TradingHubScreen;
import com.statecraft.economy.gui.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side initialization for screens and rendering
 */
@Mod.EventBusSubscriber(modid = StateCraftEconomy.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register screens
            MenuScreens.register(ModMenuTypes.ATM.get(), ATMScreen::new);
            MenuScreens.register(ModMenuTypes.TRADING_HUB.get(), TradingHubScreen::new);
            MenuScreens.register(ModMenuTypes.COMPANY_VAULT.get(), CompanyVaultScreen::new);

            StateCraftEconomy.LOGGER.info("Client setup complete - screens registered");
        });
    }
}

