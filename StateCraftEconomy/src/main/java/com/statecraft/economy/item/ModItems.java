package com.statecraft.economy.item;

import com.statecraft.economy.StateCraftEconomy;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod items (currency bills)
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, StateCraftEconomy.MOD_ID);

    // Currency Bills - $1 to $1,000,000 in multiples of 10
    public static final RegistryObject<Item> BILL_1 = ITEMS.register("bill_1",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 1, "$1 Bill", 0x90EE90));

    public static final RegistryObject<Item> BILL_10 = ITEMS.register("bill_10",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 10, "$10 Bill", 0x98FB98));

    public static final RegistryObject<Item> BILL_100 = ITEMS.register("bill_100",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 100, "$100 Bill", 0x87CEEB));

    public static final RegistryObject<Item> BILL_1000 = ITEMS.register("bill_1000",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 1000, "$1,000 Bill", 0x4169E1));

    public static final RegistryObject<Item> BILL_10000 = ITEMS.register("bill_10000",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 10000, "$10,000 Bill", 0xFF69B4));

    public static final RegistryObject<Item> BILL_100000 = ITEMS.register("bill_100000",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 100000, "$100,000 Bill", 0xFF1493));

    public static final RegistryObject<Item> BILL_1000000 = ITEMS.register("bill_1000000",
        () -> new CoinItem(new Item.Properties().stacksTo(64), 1000000, "$1,000,000 Bill", 0xFFD700));

    // Special items
    public static final RegistryObject<Item> BANK_CARD = ITEMS.register("bank_card",
        () -> new BankCardItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RECIPE_GUIDE = ITEMS.register("recipe_guide",
        () -> new RecipeGuideItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

