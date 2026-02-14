package com.statecraft.core;

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

    public City(UUID id, String name, UUID stateId, UUID mayorId) {
        this.id = id;
        this.name = name;
        this.stateId = stateId;
        this.mayorId = mayorId;
        this.residents = new HashSet<>();
        this.chunks = new HashMap<>();
        this.publicJoin = false;
        this.description = "";

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

    public int getChunkCount() {
        return chunks.size();
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

