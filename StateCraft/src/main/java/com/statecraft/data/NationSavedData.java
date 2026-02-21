package com.statecraft.data;

import com.statecraft.StateCraft;
import com.statecraft.company.CompanyManager;
import com.statecraft.contract.ContractManager;
import com.statecraft.core.*;
import com.statecraft.legislature.LegislatureManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;

/**
 * Persists all nation data using Minecraft's SavedData system
 * Data is stored in the overworld's data folder
 */
public class NationSavedData extends SavedData {
    private static final String DATA_NAME = StateCraft.MOD_ID + "_nations";

    public NationSavedData() {
        super();
    }

    /**
     * Load data from NBT
     */
    public static NationSavedData load(CompoundTag tag) {
        NationSavedData data = new NationSavedData();

        ChunkClaimManager manager = ChunkClaimManager.getInstance();
        manager.clear();

        // Load all nations
        if (tag.contains("nations")) {
            ListTag nationsList = tag.getList("nations", Tag.TAG_COMPOUND);
            for (int i = 0; i < nationsList.size(); i++) {
                CompoundTag nationTag = nationsList.getCompound(i);
                Nation nation = Nation.load(nationTag);

                // Load chunk data with dimension context
                loadChunksForNation(nation, nationTag);

                manager.loadNation(nation);
            }
        }

        // Load invitations
        if (tag.contains("invitations")) {
            InvitationManager.getInstance().load(tag.getCompound("invitations"));
        }

        // Load election data
        if (tag.contains("elections")) {
            ElectionManager.getInstance().load(tag.getCompound("elections"));
        }

        // Load legislature data
        if (tag.contains("legislature")) {
            LegislatureManager.getInstance().load(tag.getCompound("legislature"));
            StateCraft.LOGGER.info("Loaded legislature data");
        }

        // Load contract data
        if (tag.contains("contracts")) {
            ContractManager.getInstance().load(tag.getCompound("contracts"));
            StateCraft.LOGGER.info("Loaded contract data");
        }

        // Load company data
        if (tag.contains("companies")) {
            CompanyManager.getInstance().load(tag.getCompound("companies"));
            StateCraft.LOGGER.info("Loaded company data");
        }

        StateCraft.LOGGER.info("Loaded {} nations with {} total claimed chunks",
            manager.getTotalNationCount(), manager.getTotalClaimedChunks());

        return data;
    }

    private static void loadChunksForNation(Nation nation, CompoundTag nationTag) {
        if (!nationTag.contains("states")) return;

        ListTag statesList = nationTag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < statesList.size(); i++) {
            CompoundTag stateTag = statesList.getCompound(i);
            State state = nation.getStateByName(stateTag.getString("name"));
            if (state == null) continue;

            if (!stateTag.contains("cities")) continue;

            ListTag citiesList = stateTag.getList("cities", Tag.TAG_COMPOUND);
            for (int j = 0; j < citiesList.size(); j++) {
                CompoundTag cityTag = citiesList.getCompound(j);
                City city = state.getCityByName(cityTag.getString("name"));
                if (city == null) continue;

                if (!cityTag.contains("chunks")) continue;

                ListTag chunksList = cityTag.getList("chunks", Tag.TAG_COMPOUND);
                for (int k = 0; k < chunksList.size(); k++) {
                    CompoundTag chunkTag = chunksList.getCompound(k);
                    String dimensionStr = chunkTag.getString("dimension");
                    ResourceKey<Level> dimension = ResourceKey.create(
                        Registries.DIMENSION,
                        new ResourceLocation(dimensionStr)
                    );
                    ClaimedChunk chunk = ClaimedChunk.load(chunkTag, dimension);
                    city.loadChunk(chunk);
                }
            }
        }
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) {
        ChunkClaimManager manager = ChunkClaimManager.getInstance();

        // Save all nations
        ListTag nationsList = new ListTag();
        for (Nation nation : manager.getAllNations()) {
            nationsList.add(nation.save());
        }
        tag.put("nations", nationsList);

        // Save invitations
        tag.put("invitations", InvitationManager.getInstance().save());

        // Save election data
        tag.put("elections", ElectionManager.getInstance().save());

        // Save legislature data
        tag.put("legislature", LegislatureManager.getInstance().save());

        // Save contract data
        tag.put("contracts", ContractManager.getInstance().save());

        // Save company data
        tag.put("companies", CompanyManager.getInstance().save());

        manager.clearDirty();
        InvitationManager.getInstance().clearDirty();
        ElectionManager.getInstance().clearDirty();
        LegislatureManager.getInstance().clearDirty();
        ContractManager.getInstance().clearDirty();
        CompanyManager.getInstance().clearDirty();

        StateCraft.LOGGER.debug("Saved {} nations", manager.getTotalNationCount());

        return tag;
    }

    /**
     * Get or create the saved data for the server
     */
    public static NationSavedData get(ServerLevel level) {
        // Always use the overworld for storage
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            overworld = level;
        }

        return overworld.getDataStorage().computeIfAbsent(
            NationSavedData::load,
            NationSavedData::new,
            DATA_NAME
        );
    }

    /**
     * Mark data as needing to be saved
     */
    public void markForSave() {
        setDirty();
    }
}

