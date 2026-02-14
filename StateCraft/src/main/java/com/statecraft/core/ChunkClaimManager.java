package com.statecraft.core;

import com.statecraft.StateCraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry and manager for all chunk claims
 * Provides O(1) lookup for chunk ownership
 * Singleton pattern - one instance per server
 */
public class ChunkClaimManager {
    private static ChunkClaimManager instance;

    // Fast lookup: dimension -> chunkPos -> ClaimedChunk
    private final Map<ResourceKey<Level>, Map<Long, ClaimedChunk>> chunkIndex;

    // All nations indexed by ID
    private final Map<UUID, Nation> nations;

    // All cities indexed by ID (for quick city lookup)
    private final Map<UUID, City> cityIndex;

    // All states indexed by ID
    private final Map<UUID, State> stateIndex;

    // Player -> Nation mapping for quick player lookups
    private final Map<UUID, UUID> playerNationIndex;

    // Dirty flag for persistence
    private boolean dirty;

    private ChunkClaimManager() {
        this.chunkIndex = new ConcurrentHashMap<>();
        this.nations = new ConcurrentHashMap<>();
        this.cityIndex = new ConcurrentHashMap<>();
        this.stateIndex = new ConcurrentHashMap<>();
        this.playerNationIndex = new ConcurrentHashMap<>();
        this.dirty = false;
    }

    public static ChunkClaimManager getInstance() {
        if (instance == null) {
            instance = new ChunkClaimManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Chunk Operations ====================

    /**
     * Get the claimed chunk at a position, if any
     */
    @Nullable
    public ClaimedChunk getClaimedChunk(ChunkPos pos, ResourceKey<Level> dimension) {
        Map<Long, ClaimedChunk> dimensionChunks = chunkIndex.get(dimension);
        if (dimensionChunks == null) {
            return null;
        }
        return dimensionChunks.get(pos.toLong());
    }

    /**
     * Check if a chunk is claimed
     */
    public boolean isClaimed(ChunkPos pos, ResourceKey<Level> dimension) {
        return getClaimedChunk(pos, dimension) != null;
    }

    /**
     * Check if a player has a specific permission in a chunk
     */
    public boolean hasPermission(UUID playerId, ChunkPos pos, ResourceKey<Level> dimension, Permission permission) {
        ClaimedChunk chunk = getClaimedChunk(pos, dimension);
        if (chunk == null) {
            // WILDERNESS PROTECTION: Unclaimed chunks are protected
            // Only nation members can interact in wilderness (to encourage claiming)
            Nation playerNation = getPlayerNation(playerId);
            return playerNation != null;
        }

        // Get the player's role in this chunk's hierarchy
        PermissionLevel role = getPlayerRoleInChunk(playerId, chunk);
        return chunk.hasPermission(playerId, permission, role);
    }

    /**
     * Get a player's role in a specific chunk
     */
    public PermissionLevel getPlayerRoleInChunk(UUID playerId, ClaimedChunk chunk) {
        City city = cityIndex.get(chunk.getCityId());
        if (city == null) {
            return PermissionLevel.OUTSIDER;
        }

        State state = stateIndex.get(city.getStateId());
        if (state == null) {
            return city.getPlayerRole(playerId);
        }

        Nation nation = nations.get(state.getNationId());
        if (nation == null) {
            return state.getPlayerRole(playerId);
        }

        // Return the highest role the player has
        PermissionLevel nationRole = nation.getPlayerRole(playerId);
        PermissionLevel stateRole = state.getPlayerRole(playerId);
        PermissionLevel cityRole = city.getPlayerRole(playerId);

        // Return the highest permission level
        if (nationRole.getLevel() >= stateRole.getLevel() && nationRole.getLevel() >= cityRole.getLevel()) {
            return nationRole;
        } else if (stateRole.getLevel() >= cityRole.getLevel()) {
            return stateRole;
        }
        return cityRole;
    }

    // ==================== Nation Operations ====================

    public Nation createNation(String name, UUID leaderId) {
        // Check if player already has a nation
        if (playerNationIndex.containsKey(leaderId)) {
            return null;
        }

        // Check if nation name is taken
        for (Nation nation : nations.values()) {
            if (nation.getName().equalsIgnoreCase(name)) {
                return null;
            }
        }

        Nation nation = new Nation(UUID.randomUUID(), name, leaderId);
        nations.put(nation.getId(), nation);
        playerNationIndex.put(leaderId, nation.getId());
        markDirty();

        StateCraft.LOGGER.info("Nation '{}' created by player {}", name, leaderId);
        return nation;
    }

    public boolean disbandNation(UUID nationId) {
        Nation nation = nations.remove(nationId);
        if (nation == null) {
            return false;
        }

        // Remove all state/city/chunk references
        for (State state : nation.getAllStates()) {
            for (City city : state.getAllCities()) {
                for (ClaimedChunk chunk : city.getAllChunks()) {
                    removeChunkFromIndex(chunk);
                }
                cityIndex.remove(city.getId());
            }
            stateIndex.remove(state.getId());
        }

        // Remove player references
        for (UUID member : nation.getAllMembers()) {
            playerNationIndex.remove(member);
        }

        markDirty();
        StateCraft.LOGGER.info("Nation '{}' disbanded", nation.getName());
        return true;
    }

    @Nullable
    public Nation getNation(UUID nationId) {
        return nations.get(nationId);
    }

    @Nullable
    public Nation getNationByName(String name) {
        for (Nation nation : nations.values()) {
            if (nation.getName().equalsIgnoreCase(name)) {
                return nation;
            }
        }
        return null;
    }

    @Nullable
    public Nation getPlayerNation(UUID playerId) {
        UUID nationId = playerNationIndex.get(playerId);
        return nationId != null ? nations.get(nationId) : null;
    }

    public Collection<Nation> getAllNations() {
        return Collections.unmodifiableCollection(nations.values());
    }

    /**
     * Add a player to a nation (as a basic member, no city assignment yet)
     */
    public boolean addPlayerToNation(UUID playerId, UUID nationId) {
        // Check if player is already in a nation
        if (playerNationIndex.containsKey(playerId)) {
            return false;
        }

        Nation nation = nations.get(nationId);
        if (nation == null) {
            return false;
        }

        // Add to nation's member tracking
        nation.addMember(playerId);
        playerNationIndex.put(playerId, nationId);
        markDirty();

        StateCraft.LOGGER.info("Player {} joined nation '{}'", playerId, nation.getName());
        return true;
    }

    /**
     * Remove a player from their nation
     */
    public boolean removePlayerFromNation(UUID playerId) {
        UUID nationId = playerNationIndex.get(playerId);
        if (nationId == null) {
            return false;
        }

        Nation nation = nations.get(nationId);
        if (nation == null) {
            playerNationIndex.remove(playerId);
            return true;
        }

        // Can't remove the leader
        if (playerId.equals(nation.getLeaderId())) {
            return false;
        }

        // Remove from nation and any cities
        nation.removeMember(playerId);
        for (State state : nation.getAllStates()) {
            for (City city : state.getAllCities()) {
                city.removeResident(playerId);
            }
        }

        playerNationIndex.remove(playerId);
        markDirty();

        StateCraft.LOGGER.info("Player {} left nation '{}'", playerId, nation.getName());
        return true;
    }

    // ==================== State Operations ====================

    public State createState(Nation nation, String name, UUID governorId) {
        State state = nation.createState(name, governorId);
        if (state != null) {
            stateIndex.put(state.getId(), state);
            markDirty();
            StateCraft.LOGGER.info("State '{}' created in nation '{}'", name, nation.getName());
        }
        return state;
    }

    @Nullable
    public State getState(UUID stateId) {
        return stateIndex.get(stateId);
    }

    // ==================== City Operations ====================

    public City createCity(State state, String name, UUID mayorId) {
        City city = state.createCity(name, mayorId);
        if (city != null) {
            cityIndex.put(city.getId(), city);
            markDirty();
            StateCraft.LOGGER.info("City '{}' created in state '{}'", name, state.getName());
        }
        return city;
    }

    @Nullable
    public City getCity(UUID cityId) {
        return cityIndex.get(cityId);
    }

    // ==================== Chunk Claim Operations ====================

    public ClaimedChunk claimChunk(City city, ChunkPos pos, ResourceKey<Level> dimension) {
        // Check if already claimed
        if (isClaimed(pos, dimension)) {
            return null;
        }

        // Check chunk limit
        State state = stateIndex.get(city.getStateId());
        if (state != null) {
            Nation nation = nations.get(state.getNationId());
            if (nation != null && city.getChunkCount() >= nation.getMaxChunksPerCity()) {
                return null;
            }
        }

        ClaimedChunk chunk = city.claimChunk(pos, dimension);
        addChunkToIndex(chunk);
        markDirty();

        StateCraft.LOGGER.debug("Chunk ({}, {}) claimed by city '{}'", pos.x, pos.z, city.getName());
        return chunk;
    }

    public boolean unclaimChunk(ChunkPos pos, ResourceKey<Level> dimension) {
        ClaimedChunk chunk = getClaimedChunk(pos, dimension);
        if (chunk == null) {
            return false;
        }

        City city = cityIndex.get(chunk.getCityId());
        if (city != null) {
            city.unclaimChunk(pos, dimension);
        }

        removeChunkFromIndex(chunk);
        markDirty();

        StateCraft.LOGGER.debug("Chunk ({}, {}) unclaimed", pos.x, pos.z);
        return true;
    }

    private void addChunkToIndex(ClaimedChunk chunk) {
        chunkIndex.computeIfAbsent(chunk.getDimension(), k -> new ConcurrentHashMap<>())
                  .put(chunk.getChunkPos().toLong(), chunk);
    }

    private void removeChunkFromIndex(ClaimedChunk chunk) {
        Map<Long, ClaimedChunk> dimensionChunks = chunkIndex.get(chunk.getDimension());
        if (dimensionChunks != null) {
            dimensionChunks.remove(chunk.getChunkPos().toLong());
        }
    }

    // ==================== Data Management ====================

    public void clear() {
        chunkIndex.clear();
        nations.clear();
        cityIndex.clear();
        stateIndex.clear();
        playerNationIndex.clear();
        dirty = false;
    }

    public void loadNation(Nation nation) {
        nations.put(nation.getId(), nation);

        // Index all members
        for (UUID member : nation.getAllMembers()) {
            playerNationIndex.put(member, nation.getId());
        }

        // Index states and cities
        for (State state : nation.getAllStates()) {
            stateIndex.put(state.getId(), state);
            for (City city : state.getAllCities()) {
                cityIndex.put(city.getId(), city);
                // Index chunks
                for (ClaimedChunk chunk : city.getAllChunks()) {
                    addChunkToIndex(chunk);
                }
            }
        }
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    // ==================== Statistics ====================

    public int getTotalNationCount() {
        return nations.size();
    }

    public int getTotalClaimedChunks() {
        return chunkIndex.values().stream().mapToInt(Map::size).sum();
    }
}

