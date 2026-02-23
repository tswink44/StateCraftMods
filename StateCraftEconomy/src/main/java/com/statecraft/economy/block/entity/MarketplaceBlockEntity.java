package com.statecraft.economy.block.entity;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.integration.StateCraftIntegration;
import com.statecraft.economy.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Block entity for Marketplace block.
 * Stores owner UUID and the city ID where it was placed.
 * All listing data is managed globally by MarketplaceManager.
 */
public class MarketplaceBlockEntity extends BlockEntity {

    private UUID ownerUUID;
    private String ownerName = "";
    private UUID cityId;

    public MarketplaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MARKETPLACE.get(), pos, state);
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
    public UUID getCityId() { return cityId; }

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
            StateCraftEconomy.LOGGER.debug("Could not resolve city for marketplace block: {}", e.getMessage());
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
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        NBTUtils.putSanitizedString(tag, "OwnerName", ownerName);
        if (cityId != null) tag.putUUID("CityId", cityId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Owner")) ownerUUID = tag.getUUID("Owner");
        ownerName = tag.getString("OwnerName");
        if (tag.contains("CityId")) cityId = tag.getUUID("CityId");
    }
}

