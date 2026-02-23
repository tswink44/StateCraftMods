package com.statecraft.economy.block.entity;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
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
    private UUID cityId;

    public StockMarketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STOCK_MARKET.get(), pos, state);
    }

    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
        this.ownerName = player.getName().getString();

        // Resolve the city this block is placed in
        if (level != null && !level.isClientSide) {
            this.cityId = resolveCityId();
        }
        setChanged();
    }

    public UUID getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }

    /**
     * Resolve the city ID for this block's position via StateCraft integration.
     */
    private UUID resolveCityId() {
        if (level == null) return null;
        try {
            var managerClass = Class.forName("com.statecraft.core.ChunkClaimManager");
            var getInstance = managerClass.getMethod("getInstance");
            var manager = getInstance.invoke(null);

            ChunkPos chunkPos = new ChunkPos(worldPosition);
            var getChunk = managerClass.getMethod("getClaimedChunk",
                ChunkPos.class, level.dimension().getClass());
            Object chunk = getChunk.invoke(manager, chunkPos, level.dimension());
            if (chunk != null) {
                return (UUID) chunk.getClass().getMethod("getCityId").invoke(chunk);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.debug("Could not resolve city for stock market block: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if this block is placed in a claimed city chunk.
     */
    public boolean isInCity() {
        return cityId != null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUUID != null) {
            tag.putUUID("Owner", ownerUUID);
            NBTUtils.putSanitizedString(tag, "OwnerName", ownerName);
        }
        if (cityId != null) {
            tag.putUUID("CityId", cityId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Owner")) {
            ownerUUID = tag.getUUID("Owner");
            ownerName = tag.getString("OwnerName");
        }
        if (tag.contains("CityId")) {
            cityId = tag.getUUID("CityId");
        }
    }
}

