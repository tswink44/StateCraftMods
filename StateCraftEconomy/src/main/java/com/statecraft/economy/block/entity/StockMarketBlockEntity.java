package com.statecraft.economy.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Block entity for the Stock Market block.
 * Stores owner UUID. All listing data is managed globally by StockMarketManager.
 */
public class StockMarketBlockEntity extends BlockEntity {

    private UUID ownerUUID;
    private String ownerName = "";

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STOCK_MARKET.get(), pos, state);
    }

    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
        this.ownerName = player.getName().getString();
        setChanged();
    }

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
            tag.putString("OwnerName", ownerName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Owner")) {
            ownerUUID = tag.getUUID("Owner");
            ownerName = tag.getString("OwnerName");
        }
    }
}

