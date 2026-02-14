package com.statecraft.economy.gui;

import com.statecraft.economy.StateCraftEconomy;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all menu types
 */
public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, StateCraftEconomy.MOD_ID);

    public static final RegistryObject<MenuType<ATMMenu>> ATM = MENUS.register("atm",
        () -> IForgeMenuType.create((windowId, inv, data) -> {
            var pos = data.readBlockPos();
            var bankId = data.readUUID();
            return new ATMMenu(windowId, inv, pos, bankId);
        }));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}

