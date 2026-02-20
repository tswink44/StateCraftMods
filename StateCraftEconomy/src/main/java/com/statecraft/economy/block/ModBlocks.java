package com.statecraft.economy.block;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod blocks
 */
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, StateCraftEconomy.MOD_ID);

    // ATM Block
    public static final RegistryObject<Block> ATM = BLOCKS.register("atm",
        () -> new ATMBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(3.0F, 6.0F)
            .noOcclusion()));

    // Trading Hub Block
    public static final RegistryObject<Block> TRADING_HUB = BLOCKS.register("trading_hub",
        () -> new TradingHubBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(2.5F, 3.0F)
            .noOcclusion()));

    // Company Vault Block
    public static final RegistryObject<Block> COMPANY_VAULT = BLOCKS.register("company_vault",
        () -> new CompanyVaultBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .requiresCorrectToolForDrops()
            .strength(4.0F, 8.0F)));

    // Marketplace Block
    public static final RegistryObject<Block> MARKETPLACE = BLOCKS.register("marketplace",
        () -> new MarketplaceBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_CYAN)
            .strength(3.0F, 6.0F)
            .noOcclusion()));

    // Register block items
    public static final RegistryObject<Item> ATM_ITEM = ModItems.ITEMS.register("atm",
        () -> new BlockItem(ATM.get(), new Item.Properties()));

    public static final RegistryObject<Item> TRADING_HUB_ITEM = ModItems.ITEMS.register("trading_hub",
        () -> new BlockItem(TRADING_HUB.get(), new Item.Properties()));

    public static final RegistryObject<Item> COMPANY_VAULT_ITEM = ModItems.ITEMS.register("company_vault",
        () -> new BlockItem(COMPANY_VAULT.get(), new Item.Properties()));

    public static final RegistryObject<Item> MARKETPLACE_ITEM = ModItems.ITEMS.register("marketplace",
        () -> new BlockItem(MARKETPLACE.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}

