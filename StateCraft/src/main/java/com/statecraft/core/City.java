package com.statecraft.core;

import com.statecraft.config.StateCraftConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Represents a city within a state
 * Cities contain claimed chunks and are the primary unit for land management
 */
public class City {
    private final UUID id;
    private String name;
    private UUID stateId;
    private UUID mayorId; // Player who manages the city
    private final Set<UUID> residents;
    private final Map<String, ClaimedChunk> chunks; // Key: "x,z,dimension"

    // City settings
    private boolean publicJoin; // Can players join without invite?
    private String description;
    private String flagUrl;
    private double taxMultiplier; // Multiplier for chunk valuation (government-controlled)
    private double taxRate; // Base tax rate as decimal (e.g., 0.05 = 5%)
    private double salesTaxRate; // Sales tax rate for trading hub sales (e.g., 0.05 = 5%)
    private double statePassThroughRate; // Percentage of tax revenue passed to state (e.g., 0.20 = 20%)
    private int maxChunks; // Maximum chunks this city can claim

    public City(UUID id, String name, UUID stateId, UUID mayorId) {
        this.id = id;
        this.name = name;
        this.stateId = stateId;
        this.mayorId = mayorId;
        this.residents = new HashSet<>();
        this.chunks = new HashMap<>();
        this.publicJoin = false;
        this.description = "";
        this.flagUrl = "";
        this.taxMultiplier = 1.0;
        this.taxRate = 0.05; // Default 5%
        this.salesTaxRate = 0.05; // Default 5% sales tax
        this.statePassThroughRate = 0.20; // Default 20% to state
        this.maxChunks = 50; // Default 50 chunks per city

        // Mayor is automatically a resident
        residents.add(mayorId);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getStateId() {
        return stateId;
    }

    public void setStateId(UUID stateId) {
        this.stateId = stateId;
    }

    public UUID getMayorId() {
        return mayorId;
    }

    public void setMayorId(UUID mayorId) {
        this.mayorId = mayorId;
        this.residents.add(mayorId);
    }

    public Set<UUID> getResidents() {
        return Collections.unmodifiableSet(residents);
    }

    public void addResident(UUID playerId) {
        residents.add(playerId);
    }

    public void removeResident(UUID playerId) {
        if (!playerId.equals(mayorId)) {
            residents.remove(playerId);
        }
    }

    public boolean isResident(UUID playerId) {
        return residents.contains(playerId);
    }

    public boolean isPublicJoin() {
        return publicJoin;
    }

    public void setPublicJoin(boolean publicJoin) {
        this.publicJoin = publicJoin;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFlagUrl() {
        return flagUrl;
    }

    public void setFlagUrl(String flagUrl) {
        this.flagUrl = flagUrl != null ? flagUrl : "";
    }

    public double getTaxMultiplier() {
        return taxMultiplier;
    }

    public void setTaxMultiplier(double taxMultiplier) {
        // Clamp between 0.5 and 2.0
        this.taxMultiplier = Math.max(0.5, Math.min(2.0, taxMultiplier));
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        // Clamp between 0 and 1 (0% to 100%)
        this.taxRate = Math.max(0, Math.min(1.0, taxRate));
    }

    public double getSalesTaxRate() {
        return salesTaxRate;
    }

    public void setSalesTaxRate(double salesTaxRate) {
        // Clamp between 0 and 0.5 (0% to 50% max sales tax)
        this.salesTaxRate = Math.max(0, Math.min(0.5, salesTaxRate));
    }

    public double getStatePassThroughRate() {
        return statePassThroughRate;
    }

    public void setStatePassThroughRate(double statePassThroughRate) {
        // Clamp between 0 and 1 (0% to 100%)
        this.statePassThroughRate = Math.max(0, Math.min(1.0, statePassThroughRate));
    }

    public int getMaxChunks() {
        // If maxChunks hasn't been explicitly set (still at default), use config value
        // This allows server admins to set global limits while still allowing per-city overrides
        if (maxChunks == 50 && StateCraftConfig.MAX_CHUNKS_PER_CITY != null) {
            return StateCraftConfig.MAX_CHUNKS_PER_CITY.get();
        }
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = Math.max(1, maxChunks); // At least 1 chunk
    }

    // Chunk Management
    private static String getChunkKey(ChunkPos pos, ResourceKey<Level> dimension) {
        return pos.x + "," + pos.z + "," + dimension.location().toString();
    }

    public ClaimedChunk claimChunk(ChunkPos pos, ResourceKey<Level> dimension) {
        String key = getChunkKey(pos, dimension);
        ClaimedChunk chunk = new ClaimedChunk(pos, dimension, this.id);
        chunks.put(key, chunk);
        return chunk;
    }

    public boolean unclaimChunk(ChunkPos pos, ResourceKey<Level> dimension) {
        String key = getChunkKey(pos, dimension);
        return chunks.remove(key) != null;
    }

    public ClaimedChunk getChunk(ChunkPos pos, ResourceKey<Level> dimension) {
        return chunks.get(getChunkKey(pos, dimension));
    }

    public Collection<ClaimedChunk> getAllChunks() {
        return Collections.unmodifiableCollection(chunks.values());
    }

    public Collection<ClaimedChunk> getChunks() {
        return getAllChunks();
    }

    public int getChunkCount() {
        return chunks.size();
    }

    /**
     * Check if player owns any chunk in this city
     */
    public boolean playerOwnsChunkInCity(UUID playerId) {
        for (ClaimedChunk chunk : chunks.values()) {
            if (playerId.equals(chunk.getPlayerOwner())) {
                return true;
            }
        }
        return false;
    }

    public PermissionLevel getPlayerRole(UUID playerId) {
        if (playerId.equals(mayorId)) {
            return PermissionLevel.ADMIN;
        } else if (residents.contains(playerId)) {
            return PermissionLevel.MEMBER;
        }
        return PermissionLevel.OUTSIDER;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("stateId", stateId);
        tag.putUUID("mayorId", mayorId);
        tag.putBoolean("publicJoin", publicJoin);
        tag.putString("description", description);
        tag.putString("flagUrl", flagUrl);
        tag.putDouble("taxMultiplier", taxMultiplier);
        tag.putDouble("taxRate", taxRate);
        tag.putDouble("salesTaxRate", salesTaxRate);
        tag.putDouble("statePassThroughRate", statePassThroughRate);
        tag.putInt("maxChunks", maxChunks);

        // Save residents
        ListTag residentsList = new ListTag();
        for (UUID resident : residents) {
            CompoundTag residentTag = new CompoundTag();
            residentTag.putUUID("id", resident);
            residentsList.add(residentTag);
        }
        tag.put("residents", residentsList);

        // Save chunks
        ListTag chunksList = new ListTag();
        for (ClaimedChunk chunk : chunks.values()) {
            chunksList.add(chunk.save());
        }
        tag.put("chunks", chunksList);

        return tag;
    }

    public static City load(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String name = tag.getString("name");
        UUID stateId = tag.getUUID("stateId");
        UUID mayorId = tag.getUUID("mayorId");

        City city = new City(id, name, stateId, mayorId);
        city.publicJoin = tag.getBoolean("publicJoin");
        city.description = tag.getString("description");
        city.flagUrl = tag.getString("flagUrl");
        city.taxMultiplier = tag.contains("taxMultiplier") ? tag.getDouble("taxMultiplier") : 1.0;
        city.taxRate = tag.contains("taxRate") ? tag.getDouble("taxRate") : 0.05;
        city.salesTaxRate = tag.contains("salesTaxRate") ? tag.getDouble("salesTaxRate") : 0.05;
        city.statePassThroughRate = tag.contains("statePassThroughRate") ? tag.getDouble("statePassThroughRate") : 0.20;
        city.maxChunks = tag.contains("maxChunks") ? tag.getInt("maxChunks") : 50;

        // Load residents
        ListTag residentsList = tag.getList("residents", Tag.TAG_COMPOUND);
        for (int i = 0; i < residentsList.size(); i++) {
            CompoundTag residentTag = residentsList.getCompound(i);
            city.residents.add(residentTag.getUUID("id"));
        }

        // Chunks are loaded separately by NationSavedData with dimension context

        return city;
    }

    public void loadChunk(ClaimedChunk chunk) {
        String key = getChunkKey(chunk.getChunkPos(), chunk.getDimension());
        chunks.put(key, chunk);
    }
}

