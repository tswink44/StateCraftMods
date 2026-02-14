package com.statecraft.economy.block.entity;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for all mod block entities
 */
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StateCraftEconomy.MOD_ID);

    public static final RegistryObject<BlockEntityType<ATMBlockEntity>> ATM = BLOCK_ENTITIES.register("atm",
        () -> BlockEntityType.Builder.of(ATMBlockEntity::new, ModBlocks.ATM.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}

