package com.statecraft.economy.event;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.core.SpendingLimitManager;
import com.statecraft.economy.core.TaxationManager;
import com.statecraft.company.CompanyManager;
import com.statecraft.economy.company.CompanyEconomyManager;
import com.statecraft.economy.company.BankManager;
import com.statecraft.economy.marketplace.MarketplaceManager;
import com.statecraft.economy.stockmarket.StockMarketManager;
import com.statecraft.economy.data.EconomySavedData;
import com.statecraft.economy.integration.StateCraftIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles world load/save events for economy data persistence
 */
@Mod.EventBusSubscriber(modid = StateCraftEconomy.MOD_ID)
public class WorldLoadHandler {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == ServerLevel.OVERWORLD) {
                EconomySavedData data = EconomySavedData.get(serverLevel);
                EconomyManager.getInstance().loadFromData(data);
                StateCraftEconomy.LOGGER.info("Economy data loaded");

                // Ensure all existing nations/states/cities have treasury accounts
                StateCraftIntegration.ensureAllTreasuryAccounts();
            }
        }
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (serverLevel.dimension() == ServerLevel.OVERWORLD) {
                EconomyManager manager = EconomyManager.getInstance();
                TaxationManager taxManager = TaxationManager.getInstance();
                SpendingLimitManager spendingMgr = SpendingLimitManager.getInstance();
                CompanyManager companyMgr = CompanyManager.getInstance();
                CompanyEconomyManager companyEcoMgr = CompanyEconomyManager.getInstance();
                BankManager bankMgr = BankManager.getInstance();
                MarketplaceManager marketMgr = MarketplaceManager.getInstance();
                StockMarketManager stockMgr = StockMarketManager.getInstance();
                if (manager.isDirty() || taxManager.isDirty() || spendingMgr.isDirty() || companyMgr.isDirty() || companyEcoMgr.isDirty() || bankMgr.isDirty() || marketMgr.isDirty() || stockMgr.isDirty()) {
                    EconomySavedData data = EconomySavedData.get(serverLevel);
                    data.markForSave();
                    StateCraftEconomy.LOGGER.debug("Economy data saved");
                }
            }
        }
    }
}

