package com.statecraft.core;

import com.statecraft.StateCraft;
import com.statecraft.integration.IntegrationRegistry;
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

    // Fast lookup: dimension location string -> chunkPos -> ClaimedChunk
    // Using String keys instead of ResourceKey to avoid reference equality issues
    private final Map<String, Map<Long, ClaimedChunk>> chunkIndex;

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
        Map<Long, ClaimedChunk> dimensionChunks = chunkIndex.get(dimension.location().toString());
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
            // WILDERNESS: Unclaimed chunks are not protected - anyone can interact
            return true;
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

    // ==================== Leadership Role Checks ====================

    /**
     * Check if a player is already the leader of any nation.
     * A player can only be the leader of one nation at a time.
     */
    public boolean isLeaderOfAnyNation(UUID playerId) {
        for (Nation nation : nations.values()) {
            if (playerId.equals(nation.getLeaderId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a player is already the governor of any state.
     * A player can only be the governor of one state at a time.
     */
    public boolean isGovernorOfAnyState(UUID playerId) {
        for (State state : stateIndex.values()) {
            if (playerId.equals(state.getGovernorId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a player is already the mayor of any city.
     * A player can only be the mayor of one city at a time.
     */
    public boolean isMayorOfAnyCity(UUID playerId) {
        for (City city : cityIndex.values()) {
            if (playerId.equals(city.getMayorId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the name of the nation where a player is leader, or null.
     */
    @Nullable
    public String getLeaderNationName(UUID playerId) {
        for (Nation nation : nations.values()) {
            if (playerId.equals(nation.getLeaderId())) {
                return nation.getName();
            }
        }
        return null;
    }

    /**
     * Get the name of the state where a player is governor, or null.
     */
    @Nullable
    public String getGovernorStateName(UUID playerId) {
        for (State state : stateIndex.values()) {
            if (playerId.equals(state.getGovernorId())) {
                return state.getName();
            }
        }
        return null;
    }

    /**
     * Get the name of the city where a player is mayor, or null.
     */
    @Nullable
    public String getMayorCityName(UUID playerId) {
        for (City city : cityIndex.values()) {
            if (playerId.equals(city.getMayorId())) {
                return city.getName();
            }
        }
        return null;
    }

    // ==================== Nation Operations ====================

    public Nation createNation(String name, UUID leaderId) {
        // Check if player already has a nation
        if (playerNationIndex.containsKey(leaderId)) {
            return null;
        }

        // Check if player is already the leader of another nation
        if (isLeaderOfAnyNation(leaderId)) {
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

        // Notify economy integration
        IntegrationRegistry.notifyNationCreated(nation.getId(), nation.getName());

        // Schedule first election
        ElectionManager.getInstance().onNationCreated(nation.getId());

        StateCraft.LOGGER.info("Nation '{}' created by player {}", name, leaderId);
        return nation;
    }

    public boolean disbandNation(UUID nationId) {
        Nation nation = nations.remove(nationId);
        if (nation == null) {
            return false;
        }

        // Remove all state/city/chunk references and notify economy
        for (State state : nation.getAllStates()) {
            for (City city : state.getAllCities()) {
                for (ClaimedChunk chunk : city.getAllChunks()) {
                    removeChunkFromIndex(chunk);
                }
                cityIndex.remove(city.getId());
                IntegrationRegistry.notifyCityDisbanded(city.getId());
            }
            stateIndex.remove(state.getId());
            IntegrationRegistry.notifyStateDisbanded(state.getId());
        }

        // Remove player references
        for (UUID member : nation.getAllMembers()) {
            playerNationIndex.remove(member);
        }

        // Notify economy integration
        IntegrationRegistry.notifyNationDisbanded(nationId);

        // Clean up election data
        ElectionManager.getInstance().onNationDisbanded(nationId);

        markDirty();
        StateCraft.LOGGER.info("Nation '{}' disbanded", nation.getName());
        return true;
    }

    /**
     * Disband a state - unclaim all chunks, remove all cities, clean up indexes
     * Does NOT remove players from the nation.
     */
    public boolean disbandState(UUID nationId, UUID stateId) {
        Nation nation = nations.get(nationId);
        if (nation == null) return false;

        State state = nation.getState(stateId);
        if (state == null) return false;

        // Remove all cities and their chunks
        for (City city : new ArrayList<>(state.getAllCities())) {
            for (ClaimedChunk chunk : city.getAllChunks()) {
                removeChunkFromIndex(chunk);
            }
            cityIndex.remove(city.getId());
            IntegrationRegistry.notifyCityDisbanded(city.getId());
        }

        // Remove state from nation and index
        nation.removeState(stateId);
        stateIndex.remove(stateId);
        IntegrationRegistry.notifyStateDisbanded(stateId);

        markDirty();
        StateCraft.LOGGER.info("State '{}' disbanded from nation '{}'", state.getName(), nation.getName());
        return true;
    }

    /**
     * Disband a city - unclaim all chunks, remove residents from city, clean up indexes
     * Does NOT remove players from the nation.
     */
    public boolean disbandCity(UUID stateId, UUID cityId) {
        State state = stateIndex.get(stateId);
        if (state == null) return false;

        City city = state.getCity(cityId);
        if (city == null) return false;

        // Remove all chunks from index
        for (ClaimedChunk chunk : city.getAllChunks()) {
            removeChunkFromIndex(chunk);
        }

        // Remove city from state and index
        state.removeCity(cityId);
        cityIndex.remove(cityId);
        IntegrationRegistry.notifyCityDisbanded(cityId);

        markDirty();
        StateCraft.LOGGER.info("City '{}' disbanded from state '{}'", city.getName(), state.getName());
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
        // If governorId is provided and already governor elsewhere, create with vacant governor
        UUID actualGovernorId = governorId;
        if (governorId != null && isGovernorOfAnyState(governorId)) {
            actualGovernorId = null; // Create with vacant governor position
        }

        State state = nation.createState(name, actualGovernorId);
        if (state != null) {
            stateIndex.put(state.getId(), state);
            markDirty();

            // Notify economy integration
            IntegrationRegistry.notifyStateCreated(state.getId(), state.getName(), nation.getId());

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
        // If mayorId is provided and already mayor elsewhere, create with vacant mayor
        UUID actualMayorId = mayorId;
        if (mayorId != null && isMayorOfAnyCity(mayorId)) {
            actualMayorId = null; // Create with vacant mayor position
        }

        City city = state.createCity(name, actualMayorId);
        if (city != null) {
            cityIndex.put(city.getId(), city);
            markDirty();

            // Notify economy integration
            IntegrationRegistry.notifyCityCreated(city.getId(), city.getName(), state.getId());

            StateCraft.LOGGER.info("City '{}' created in state '{}'", name, state.getName());
        }
        return city;
    }

    @Nullable
    public City getCity(UUID cityId) {
        return cityIndex.get(cityId);
    }

    // ==================== Player Chunk Ownership ====================

    /**
     * Count how many chunks a player personally owns across ALL nations.
     */
    public int getPlayerOwnedChunkCount(UUID playerId) {
        int count = 0;
        for (Nation nation : nations.values()) {
            for (State state : nation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    for (ClaimedChunk chunk : city.getAllChunks()) {
                        if (chunk.getOwnershipType() == OwnershipType.PLAYER &&
                            playerId.equals(chunk.getPlayerOwner())) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Get the effective max chunks per player for a nation.
     * Nation legislature setting takes priority over server config.
     * Returns 0 if unlimited.
     */
    public int getEffectiveMaxChunksPerPlayer(@Nullable Nation nation) {
        // Nation-level legislature policy takes priority
        if (nation != null && nation.getMaxChunksPerPlayer() > 0) {
            return nation.getMaxChunksPerPlayer();
        }
        // Fall back to server config
        return com.statecraft.config.StateCraftConfig.MAX_CHUNKS_PER_PLAYER.get();
    }

    /**
     * Check if a player can own another chunk (against the per-player limit).
     * @return true if the player can own another chunk, false if at limit.
     */
    public boolean canPlayerOwnMoreChunks(UUID playerId, @Nullable Nation nation) {
        int limit = getEffectiveMaxChunksPerPlayer(nation);
        if (limit <= 0) return true; // 0 = unlimited
        return getPlayerOwnedChunkCount(playerId) < limit;
    }

    // ==================== Company Chunk Ownership ====================

    /**
     * Count how many chunks a company owns across ALL nations.
     */
    public int getCompanyOwnedChunkCount(UUID companyId) {
        int count = 0;
        for (Nation nation : nations.values()) {
            for (State state : nation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    for (ClaimedChunk chunk : city.getAllChunks()) {
                        if (chunk.getOwnershipType() == OwnershipType.COMPANY &&
                            companyId.equals(chunk.getCompanyOwner())) {
                            count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Get all chunks owned by a specific company.
     */
    public List<ClaimedChunk> getCompanyOwnedChunks(UUID companyId) {
        List<ClaimedChunk> result = new ArrayList<>();
        for (Nation nation : nations.values()) {
            for (State state : nation.getAllStates()) {
                for (City city : state.getAllCities()) {
                    for (ClaimedChunk chunk : city.getAllChunks()) {
                        if (chunk.getOwnershipType() == OwnershipType.COMPANY &&
                            companyId.equals(chunk.getCompanyOwner())) {
                            result.add(chunk);
                        }
                    }
                }
            }
        }
        return result;
    }

    // ==================== Chunk Claim Operations ====================

    /**
     * Result of a claim attempt with reason for failure
     */
    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        CITY_CHUNK_LIMIT,
        STATE_CHUNK_LIMIT,
        NATION_CHUNK_LIMIT,  // Nation's maxChunksPerCity limit exceeded
        PLAYER_CHUNK_LIMIT,  // Player's max personal chunk limit exceeded
        NOT_CONTIGUOUS,
        INSUFFICIENT_FUNDS
    }

    /**
     * Check if a chunk position is adjacent (contiguous) to any existing chunk owned by the city
     */
    public boolean isContiguousToCity(City city, ChunkPos pos, ResourceKey<Level> dimension) {
        // If city has no chunks, any position is valid (first chunk)
        if (city.getChunkCount() == 0) {
            return true;
        }

        // Check all 4 adjacent positions
        ChunkPos[] adjacent = {
            new ChunkPos(pos.x + 1, pos.z),
            new ChunkPos(pos.x - 1, pos.z),
            new ChunkPos(pos.x, pos.z + 1),
            new ChunkPos(pos.x, pos.z - 1)
        };

        for (ChunkPos adjPos : adjacent) {
            ClaimedChunk adjChunk = getClaimedChunk(adjPos, dimension);
            if (adjChunk != null && adjChunk.getCityId().equals(city.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a chunk position is contiguous to any chunk in the same state
     */
    public boolean isContiguousToState(State state, ChunkPos pos, ResourceKey<Level> dimension) {
        // If state has no chunks, any position is valid
        int totalChunks = state.getTotalChunkCount();
        if (totalChunks == 0) {
            return true;
        }

        // Check all 4 adjacent positions
        ChunkPos[] adjacent = {
            new ChunkPos(pos.x + 1, pos.z),
            new ChunkPos(pos.x - 1, pos.z),
            new ChunkPos(pos.x, pos.z + 1),
            new ChunkPos(pos.x, pos.z - 1)
        };

        for (ChunkPos adjPos : adjacent) {
            ClaimedChunk adjChunk = getClaimedChunk(adjPos, dimension);
            if (adjChunk != null) {
                City adjCity = cityIndex.get(adjChunk.getCityId());
                if (adjCity != null && adjCity.getStateId().equals(state.getId())) {
                    return true;
                }
            }
        }

        return false;
    }

    public ClaimResult claimChunkWithResult(City city, ChunkPos pos, ResourceKey<Level> dimension) {
        // Check if already claimed
        if (isClaimed(pos, dimension)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        // Get state and nation
        State state = stateIndex.get(city.getStateId());
        if (state == null) {
            return ClaimResult.NOT_CONTIGUOUS; // No state means invalid
        }

        Nation nation = nations.get(state.getNationId());

        // Determine the effective max chunks for this city:
        // The nation's legislature-set limit takes priority over the city/config default.
        // Use the HIGHER of the nation limit and city limit so legislation can raise caps.
        int nationLimit = nation != null ? nation.getMaxChunksPerCity() : Integer.MAX_VALUE;
        int cityLimit = city.getMaxChunks();
        int effectiveLimit = Math.max(nationLimit, cityLimit);

        if (city.getChunkCount() >= effectiveLimit) {
            // Report as nation limit if nation limit is the binding constraint,
            // otherwise report as city limit
            if (nationLimit <= cityLimit) {
                return ClaimResult.NATION_CHUNK_LIMIT;
            }
            return ClaimResult.CITY_CHUNK_LIMIT;
        }

        // Check state chunk limit
        if (state.getTotalChunkCount() >= state.getMaxChunks()) {
            return ClaimResult.STATE_CHUNK_LIMIT;
        }

        // Check contiguity to city (chunks must be adjacent to existing city chunks)
        if (!isContiguousToCity(city, pos, dimension)) {
            // If not contiguous to city, check if contiguous to state
            // This allows expanding into new territory adjacent to state
            if (!isContiguousToState(state, pos, dimension)) {
                return ClaimResult.NOT_CONTIGUOUS;
            }
        }

        ClaimedChunk chunk = city.claimChunk(pos, dimension);
        addChunkToIndex(chunk);
        markDirty();

        StateCraft.LOGGER.debug("Chunk ({}, {}) claimed by city '{}'", pos.x, pos.z, city.getName());
        return ClaimResult.SUCCESS;
    }

    public ClaimedChunk claimChunk(City city, ChunkPos pos, ResourceKey<Level> dimension) {
        ClaimResult result = claimChunkWithResult(city, pos, dimension);
        if (result == ClaimResult.SUCCESS) {
            return getClaimedChunk(pos, dimension);
        }
        return null;
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
        chunkIndex.computeIfAbsent(chunk.getDimension().location().toString(), k -> new ConcurrentHashMap<>())
                  .put(chunk.getChunkPos().toLong(), chunk);
    }

    private void removeChunkFromIndex(ClaimedChunk chunk) {
        Map<Long, ClaimedChunk> dimensionChunks = chunkIndex.get(chunk.getDimension().location().toString());
        if (dimensionChunks != null) {
            dimensionChunks.remove(chunk.getChunkPos().toLong());
        }
    }

    /**
     * Transfer an existing claimed chunk to a new city (used by peace treaty chunk transfers).
     * The chunk is NOT removed from the chunk index — it stays in the same position.
     * Only the city association is changed.
     */
    public void transferChunkToCity(ClaimedChunk chunk, City targetCity, ChunkPos pos, ResourceKey<Level> dimension) {
        // The chunk is already in the index at the same position, just re-add it to be safe
        addChunkToIndex(chunk);
        // Add to target city's internal chunk map
        targetCity.claimChunkDirect(chunk, pos, dimension);
        markDirty();
        StateCraft.LOGGER.info("Chunk ({}, {}) transferred to city '{}'", pos.x, pos.z, targetCity.getName());
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

    // ==================== Orphan Cleanup ====================

    /**
     * Scan for and clean up orphaned data after world load.
     * Detects:
     * - States whose nationId references a non-existent nation
     * - Cities whose stateId references a non-existent state
     * - Chunks in the chunk index whose cityId references a non-existent city
     * - Player-nation index entries for players not in any nation's member list
     *
     * All orphans are logged and removed automatically.
     *
     * @return total number of orphaned entries removed
     */
    public int runOrphanCleanup() {
        int totalRemoved = 0;
        StateCraft.LOGGER.info("[OrphanCleanup] Starting data integrity scan...");

        // 1. States referencing non-existent nations
        int orphanedStates = cleanupOrphanedStates();
        totalRemoved += orphanedStates;

        // 2. Cities referencing non-existent states
        int orphanedCities = cleanupOrphanedCities();
        totalRemoved += orphanedCities;

        // 3. Chunks referencing non-existent cities
        int orphanedChunks = cleanupOrphanedChunks();
        totalRemoved += orphanedChunks;

        // 4. Player-nation index entries for players not in any nation's member list
        int orphanedPlayers = cleanupOrphanedPlayerEntries();
        totalRemoved += orphanedPlayers;

        if (totalRemoved > 0) {
            StateCraft.LOGGER.warn("[OrphanCleanup] Removed {} total orphaned entries " +
                    "(states={}, cities={}, chunks={}, player-index={}). Data will be saved on next save cycle.",
                totalRemoved, orphanedStates, orphanedCities, orphanedChunks, orphanedPlayers);
            markDirty();
        } else {
            StateCraft.LOGGER.info("[OrphanCleanup] Data integrity check passed — no orphans found.");
        }

        return totalRemoved;
    }

    /**
     * Remove states from the state index that reference a nation ID not in the nations map.
     * Also removes those states from within their parent nation if the nation exists but the
     * state's nationId is mismatched.
     */
    private int cleanupOrphanedStates() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, State> entry : stateIndex.entrySet()) {
            State state = entry.getValue();
            UUID nationId = state.getNationId();
            Nation nation = nations.get(nationId);

            if (nation == null) {
                StateCraft.LOGGER.warn("[OrphanCleanup] State '{}' (ID: {}) references non-existent nation {}",
                    state.getName(), state.getId(), nationId);
                toRemove.add(entry.getKey());
            } else {
                // Nation exists — verify the state is actually in the nation's states map
                if (nation.getState(state.getId()) == null) {
                    StateCraft.LOGGER.warn("[OrphanCleanup] State '{}' (ID: {}) is in stateIndex but not in nation '{}' states map",
                        state.getName(), state.getId(), nation.getName());
                    toRemove.add(entry.getKey());
                }
            }
        }

        for (UUID stateId : toRemove) {
            State state = stateIndex.remove(stateId);
            if (state != null) {
                // Also remove all cities belonging to this orphaned state from cityIndex
                for (City city : state.getAllCities()) {
                    cityIndex.remove(city.getId());
                    // Remove chunks belonging to orphaned cities
                    for (ClaimedChunk chunk : city.getAllChunks()) {
                        removeChunkFromIndex(chunk);
                    }
                }
                StateCraft.LOGGER.warn("[OrphanCleanup] Removed orphaned state '{}' and its {} cities",
                    state.getName(), state.getCityCount());
            }
        }

        return toRemove.size();
    }

    /**
     * Remove cities from the city index that reference a state ID not in the state index.
     */
    private int cleanupOrphanedCities() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, City> entry : cityIndex.entrySet()) {
            City city = entry.getValue();
            UUID stateId = city.getStateId();
            State state = stateIndex.get(stateId);

            if (state == null) {
                StateCraft.LOGGER.warn("[OrphanCleanup] City '{}' (ID: {}) references non-existent state {}",
                    city.getName(), city.getId(), stateId);
                toRemove.add(entry.getKey());
            } else {
                // State exists — verify the city is actually in the state's cities map
                if (state.getCity(city.getId()) == null) {
                    StateCraft.LOGGER.warn("[OrphanCleanup] City '{}' (ID: {}) is in cityIndex but not in state '{}' cities map",
                        city.getName(), city.getId(), state.getName());
                    toRemove.add(entry.getKey());
                }
            }
        }

        for (UUID cityId : toRemove) {
            City city = cityIndex.remove(cityId);
            if (city != null) {
                // Remove chunks belonging to orphaned city
                for (ClaimedChunk chunk : city.getAllChunks()) {
                    removeChunkFromIndex(chunk);
                }
                StateCraft.LOGGER.warn("[OrphanCleanup] Removed orphaned city '{}' and its {} chunks",
                    city.getName(), city.getChunkCount());
            }
        }

        return toRemove.size();
    }

    /**
     * Remove chunks from the chunk index that reference a city ID not in the city index.
     */
    private int cleanupOrphanedChunks() {
        int removed = 0;

        for (Map.Entry<String, Map<Long, ClaimedChunk>> dimEntry : chunkIndex.entrySet()) {
            List<Long> toRemove = new ArrayList<>();

            for (Map.Entry<Long, ClaimedChunk> chunkEntry : dimEntry.getValue().entrySet()) {
                ClaimedChunk chunk = chunkEntry.getValue();
                UUID cityId = chunk.getCityId();

                if (!cityIndex.containsKey(cityId)) {
                    StateCraft.LOGGER.warn("[OrphanCleanup] Chunk at ({}, {}) in dimension '{}' references non-existent city {}",
                        chunk.getChunkPos().x, chunk.getChunkPos().z, dimEntry.getKey(), cityId);
                    toRemove.add(chunkEntry.getKey());
                }
            }

            for (Long key : toRemove) {
                dimEntry.getValue().remove(key);
                removed++;
            }
        }

        if (removed > 0) {
            StateCraft.LOGGER.warn("[OrphanCleanup] Removed {} orphaned chunks from chunk index", removed);
        }

        return removed;
    }

    /**
     * Remove player-nation index entries where the player is not actually
     * in the referenced nation's member list.
     */
    private int cleanupOrphanedPlayerEntries() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : playerNationIndex.entrySet()) {
            UUID playerId = entry.getKey();
            UUID nationId = entry.getValue();
            Nation nation = nations.get(nationId);

            if (nation == null) {
                StateCraft.LOGGER.warn("[OrphanCleanup] Player {} in player-nation index references non-existent nation {}",
                    playerId, nationId);
                toRemove.add(playerId);
            } else if (!nation.isMember(playerId)) {
                StateCraft.LOGGER.warn("[OrphanCleanup] Player {} in player-nation index is not a member of nation '{}'",
                    playerId, nation.getName());
                toRemove.add(playerId);
            }
        }

        for (UUID playerId : toRemove) {
            playerNationIndex.remove(playerId);
        }

        if (!toRemove.isEmpty()) {
            StateCraft.LOGGER.warn("[OrphanCleanup] Removed {} orphaned player-nation index entries", toRemove.size());
        }

        return toRemove.size();
    }
}

