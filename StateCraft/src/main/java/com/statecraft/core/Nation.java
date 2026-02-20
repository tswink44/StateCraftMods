package com.statecraft.core;

import com.statecraft.config.StateCraftConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a nation - the top level of the hierarchy
 * Nations contain states and provide overall governance
 */
public class Nation {
    private final UUID id;
    private String name;
    private UUID leaderId; // Nation leader/ruler
    private final Set<UUID> admins; // Co-leaders with administrative powers
    private final Set<UUID> officers; // Legislature voting members appointed by leader
    private final Set<UUID> members; // Basic nation members (not in a city yet)
    private final Map<UUID, State> states;
    private final Set<UUID> allies; // Allied nation IDs
    private final Set<UUID> enemies; // Enemy nation IDs

    // Nation settings
    private int maxStates;
    private int maxChunksPerCity;
    private int defaultMaxCitiesPerState; // Default max cities for new states
    private String description;
    private String tag; // Short tag/prefix for chat
    private boolean open; // Can players join without invite?
    private boolean openBorders; // Can foreign players interact with blocks?
    private String flagUrl; // URL to flag image
    private double statePassThroughRate; // Rate states must give to nation (e.g., 0.20 = 20%)
    private double baseChunkValue; // Base valuation for chunks in the nation (default $100)
    private double chunkClaimFee; // Fee cities pay to nation when claiming chunks
    private double salesTaxRate; // Nation's sales tax rate (e.g., 0.20 = 20%) - set via legislature

    // Constitutional settings (can only be changed via constitutional amendment)
    private int leaderTermDays = 7;       // Default: 7 days term for leader
    private int electionDurationDays = 1; // Default: 1 day election duration
    private int maxOfficers = 3;          // Default: max 3 officers (can be 0-3)


    public Nation(UUID id, String name, UUID leaderId) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.admins = new HashSet<>();
        this.officers = new HashSet<>();
        this.members = new HashSet<>();
        this.states = new HashMap<>();
        this.allies = new HashSet<>();
        this.enemies = new HashSet<>();
        this.maxStates = 5; // Default max states
        this.maxChunksPerCity = 50; // Default max chunks per city
        this.defaultMaxCitiesPerState = 10; // Default max cities per state
        this.description = "";
        this.tag = "";
        this.open = false;
        this.openBorders = false; // Default: closed borders
        this.flagUrl = "";
        this.statePassThroughRate = 0.20; // Default 20%
        this.baseChunkValue = 100.0; // Default $100
        this.chunkClaimFee = 0.0; // Default: no fee
        this.salesTaxRate = 0.0; // Default: no nation sales tax (set via legislature)
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

    public UUID getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public Set<UUID> getAdmins() {
        return Collections.unmodifiableSet(admins);
    }

    public void addAdmin(UUID playerId) {
        admins.add(playerId);
    }

    public void removeAdmin(UUID playerId) {
        admins.remove(playerId);
    }

    public boolean isAdmin(UUID playerId) {
        return admins.contains(playerId) || playerId.equals(leaderId);
    }

    public Set<UUID> getOfficers() {
        return Collections.unmodifiableSet(officers);
    }

    public void addOfficer(UUID playerId) {
        officers.add(playerId);
    }

    public void removeOfficer(UUID playerId) {
        officers.remove(playerId);
    }

    public boolean isOfficer(UUID playerId) {
        return officers.contains(playerId);
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
        admins.remove(playerId);
        officers.remove(playerId);
    }

    public int getMaxStates() {
        // Use config value for max states - server config takes precedence
        if (StateCraftConfig.MAX_STATES_PER_NATION != null) {
            return StateCraftConfig.MAX_STATES_PER_NATION.get();
        }
        return maxStates;
    }

    public void setMaxStates(int maxStates) {
        this.maxStates = maxStates;
    }

    public int getMaxChunksPerCity() {
        return maxChunksPerCity;
    }

    public void setMaxChunksPerCity(int maxChunksPerCity) {
        this.maxChunksPerCity = maxChunksPerCity;
    }

    public int getDefaultMaxCitiesPerState() {
        return defaultMaxCitiesPerState;
    }

    public void setDefaultMaxCitiesPerState(int defaultMaxCitiesPerState) {
        this.defaultMaxCitiesPerState = Math.max(1, defaultMaxCitiesPerState);
    }

    /**
     * Alias for setMaxChunksPerCity - used by legislature policy
     */
    public void setDefaultMaxChunksPerCity(int maxChunks) {
        setMaxChunksPerCity(maxChunks);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean hasOpenBorders() {
        return openBorders;
    }

    public void setOpenBorders(boolean openBorders) {
        this.openBorders = openBorders;
    }

    /**
     * Check if a player from another nation can interact with blocks in this nation.
     * Returns true if:
     * - Player is a member of this nation
     * - Open borders is enabled AND the player's nation is not at war with this nation
     * - Player is from an allied nation
     *
     * @param playerId The player to check
     * @param playerNation The nation the player belongs to (can be null)
     * @return true if the player can interact
     */
    public boolean canForeignerInteract(UUID playerId, Nation playerNation) {
        // Citizens can always interact
        if (isMember(playerId)) {
            return true;
        }

        // If player has no nation, check open borders only
        if (playerNation == null) {
            return openBorders;
        }

        // If at war with player's nation, they cannot interact regardless of open borders
        if (isEnemy(playerNation.getId())) {
            return false;
        }

        // Allies can always interact
        if (isAlly(playerNation.getId())) {
            return true;
        }

        // Otherwise, check open borders policy
        return openBorders;
    }

    public String getFlagUrl() {
        return flagUrl;
    }

    public void setFlagUrl(String flagUrl) {
        this.flagUrl = flagUrl != null ? flagUrl : "";
    }

    public double getStatePassThroughRate() {
        return statePassThroughRate;
    }

    public void setStatePassThroughRate(double statePassThroughRate) {
        // Clamp between 0 and 1 (0% to 100%)
        this.statePassThroughRate = Math.max(0, Math.min(1.0, statePassThroughRate));
    }

    public double getBaseChunkValue() {
        return baseChunkValue;
    }

    public void setBaseChunkValue(double baseChunkValue) {
        // Clamp to reasonable range ($1 to $100,000)
        this.baseChunkValue = Math.max(1, Math.min(100000, baseChunkValue));
    }

    public double getChunkClaimFee() {
        return chunkClaimFee;
    }

    public void setChunkClaimFee(double chunkClaimFee) {
        // Clamp to reasonable range ($0 to $1,000,000)
        this.chunkClaimFee = Math.max(0, Math.min(1000000, chunkClaimFee));
    }

    public double getSalesTaxRate() {
        return salesTaxRate;
    }

    public void setSalesTaxRate(double salesTaxRate) {
        // Clamp between 0 and 0.5 (0% to 50%)
        this.salesTaxRate = Math.max(0, Math.min(0.5, salesTaxRate));
    }

    // Constitutional settings getters and setters

    /**
     * Get the leader term duration in days
     * Default: 7 days
     */
    public int getLeaderTermDays() {
        return leaderTermDays;
    }

    /**
     * Set the leader term duration in days (1-365)
     * Can only be changed via constitutional amendment
     */
    public void setLeaderTermDays(int days) {
        this.leaderTermDays = Math.max(1, Math.min(365, days));
    }

    /**
     * Get the election duration in days
     * Default: 1 day
     */
    public int getElectionDurationDays() {
        return electionDurationDays;
    }

    /**
     * Set the election duration in days (1-30)
     * Can only be changed via constitutional amendment
     */
    public void setElectionDurationDays(int days) {
        this.electionDurationDays = Math.max(1, Math.min(30, days));
    }

    /**
     * Get the maximum number of officers allowed
     * Default: 3 (can be 0-3)
     */
    public int getMaxOfficers() {
        return maxOfficers;
    }

    /**
     * Set the maximum number of officers (0-3)
     * Can only be changed via constitutional amendment
     * Note: If current officers exceed new max, excess officers are NOT automatically removed
     */
    public void setMaxOfficers(int max) {
        this.maxOfficers = Math.max(0, Math.min(3, max));
    }

    /**
     * Check if another officer can be added
     */
    public boolean canAddOfficer() {
        return officers.size() < maxOfficers;
    }


    // Diplomacy
    public Set<UUID> getAllies() {
        return Collections.unmodifiableSet(allies);
    }

    public void addAlly(UUID nationId) {
        allies.add(nationId);
        enemies.remove(nationId);
    }

    public void removeAlly(UUID nationId) {
        allies.remove(nationId);
    }

    public boolean isAlly(UUID nationId) {
        return allies.contains(nationId);
    }

    public Set<UUID> getEnemies() {
        return Collections.unmodifiableSet(enemies);
    }

    public void addEnemy(UUID nationId) {
        enemies.add(nationId);
        allies.remove(nationId);
    }

    public void removeEnemy(UUID nationId) {
        enemies.remove(nationId);
    }

    public boolean isEnemy(UUID nationId) {
        return enemies.contains(nationId);
    }

    // State Management
    public State createState(String stateName, UUID governorId) {
        // Use the nation's maxStates which can be modified via legislation
        if (states.size() >= maxStates) {
            return null; // Max states reached
        }
        State state = new State(UUID.randomUUID(), stateName, this.id, governorId);
        // Apply nation's default limits to new state
        state.setMaxCities(defaultMaxCitiesPerState);
        states.put(state.getId(), state);
        return state;
    }

    /**
     * Check if nation can create another state
     */
    public boolean canCreateState() {
        return states.size() < maxStates;
    }

    /**
     * Get remaining state slots
     */
    public int getRemainingStateSlots() {
        return Math.max(0, maxStates - states.size());
    }

    public boolean removeState(UUID stateId) {
        return states.remove(stateId) != null;
    }

    public State getState(UUID stateId) {
        return states.get(stateId);
    }

    public State getStateByName(String name) {
        for (State state : states.values()) {
            if (state.getName().equalsIgnoreCase(name)) {
                return state;
            }
        }
        return null;
    }

    public Collection<State> getAllStates() {
        return Collections.unmodifiableCollection(states.values());
    }

    public int getStateCount() {
        return states.size();
    }

    public int getTotalCityCount() {
        return states.values().stream().mapToInt(State::getCityCount).sum();
    }

    public int getTotalChunkCount() {
        return states.values().stream().mapToInt(State::getTotalChunkCount).sum();
    }

    /**
     * Check if a player owns any property (chunks) in this nation
     */
    public boolean playerOwnsPropertyInNation(UUID playerId) {
        for (State state : states.values()) {
            if (state.playerOwnsChunkInState(playerId)) {
                return true;
            }
        }
        return false;
    }

    public Set<UUID> getAllMembers() {
        Set<UUID> allMembers = new HashSet<>();
        allMembers.add(leaderId);
        allMembers.addAll(admins);
        allMembers.addAll(members);
        for (State state : states.values()) {
            allMembers.addAll(state.getAllResidents());
        }
        return allMembers;
    }

    public boolean isMember(UUID playerId) {
        return getAllMembers().contains(playerId);
    }

    public PermissionLevel getPlayerRole(UUID playerId) {
        if (playerId.equals(leaderId)) {
            return PermissionLevel.OWNER;
        }
        if (admins.contains(playerId)) {
            return PermissionLevel.ADMIN;
        }
        // Check state/city membership
        for (State state : states.values()) {
            PermissionLevel stateRole = state.getPlayerRole(playerId);
            if (stateRole != PermissionLevel.OUTSIDER) {
                return stateRole;
            }
        }
        return PermissionLevel.OUTSIDER;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("leaderId", leaderId);
        tag.putInt("maxStates", maxStates);
        tag.putInt("maxChunksPerCity", maxChunksPerCity);
        tag.putInt("defaultMaxCitiesPerState", defaultMaxCitiesPerState);
        tag.putString("description", description);
        tag.putString("tag", this.tag);
        tag.putBoolean("open", open);
        tag.putBoolean("openBorders", openBorders);
        tag.putString("flagUrl", flagUrl);
        tag.putDouble("statePassThroughRate", statePassThroughRate);
        tag.putDouble("baseChunkValue", baseChunkValue);
        tag.putDouble("chunkClaimFee", chunkClaimFee);
        tag.putDouble("salesTaxRate", salesTaxRate);

        // Constitutional settings
        tag.putInt("leaderTermDays", leaderTermDays);
        tag.putInt("electionDurationDays", electionDurationDays);
        tag.putInt("maxOfficers", maxOfficers);

        // Save admins
        ListTag adminsList = new ListTag();
        for (UUID admin : admins) {
            CompoundTag adminTag = new CompoundTag();
            adminTag.putUUID("id", admin);
            adminsList.add(adminTag);
        }
        tag.put("admins", adminsList);

        // Save officers
        ListTag officersList = new ListTag();
        for (UUID officer : officers) {
            CompoundTag officerTag = new CompoundTag();
            officerTag.putUUID("id", officer);
            officersList.add(officerTag);
        }
        tag.put("officers", officersList);

        // Save members
        ListTag membersList = new ListTag();
        for (UUID member : members) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("id", member);
            membersList.add(memberTag);
        }
        tag.put("members", membersList);

        // Save allies
        ListTag alliesList = new ListTag();
        for (UUID ally : allies) {
            CompoundTag allyTag = new CompoundTag();
            allyTag.putUUID("id", ally);
            alliesList.add(allyTag);
        }
        tag.put("allies", alliesList);

        // Save enemies
        ListTag enemiesList = new ListTag();
        for (UUID enemy : enemies) {
            CompoundTag enemyTag = new CompoundTag();
            enemyTag.putUUID("id", enemy);
            enemiesList.add(enemyTag);
        }
        tag.put("enemies", enemiesList);

        // Save states
        ListTag statesList = new ListTag();
        for (State state : states.values()) {
            statesList.add(state.save());
        }
        tag.put("states", statesList);

        return tag;
    }

    public static Nation load(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String name = tag.getString("name");
        UUID leaderId = tag.getUUID("leaderId");

        Nation nation = new Nation(id, name, leaderId);
        nation.maxStates = tag.getInt("maxStates");
        nation.maxChunksPerCity = tag.getInt("maxChunksPerCity");
        nation.defaultMaxCitiesPerState = tag.contains("defaultMaxCitiesPerState") ? tag.getInt("defaultMaxCitiesPerState") : 10;
        nation.description = tag.getString("description");
        nation.tag = tag.getString("tag");
        nation.open = tag.getBoolean("open");
        nation.openBorders = tag.contains("openBorders") ? tag.getBoolean("openBorders") : false;
        nation.flagUrl = tag.getString("flagUrl");
        // Note: "balance" key in old saves is ignored — treasury is managed by EconomyManager
        nation.statePassThroughRate = tag.contains("statePassThroughRate") ? tag.getDouble("statePassThroughRate") : 0.20;
        nation.baseChunkValue = tag.contains("baseChunkValue") ? tag.getDouble("baseChunkValue") : 100.0;
        nation.chunkClaimFee = tag.contains("chunkClaimFee") ? tag.getDouble("chunkClaimFee") : 0.0;
        nation.salesTaxRate = tag.contains("salesTaxRate") ? tag.getDouble("salesTaxRate") : 0.0;

        // Constitutional settings (with defaults for backwards compatibility)
        nation.leaderTermDays = tag.contains("leaderTermDays") ? tag.getInt("leaderTermDays") : 7;
        nation.electionDurationDays = tag.contains("electionDurationDays") ? tag.getInt("electionDurationDays") : 1;
        nation.maxOfficers = tag.contains("maxOfficers") ? tag.getInt("maxOfficers") : 3;

        // Load admins
        ListTag adminsList = tag.getList("admins", Tag.TAG_COMPOUND);
        for (int i = 0; i < adminsList.size(); i++) {
            nation.admins.add(adminsList.getCompound(i).getUUID("id"));
        }

        // Load officers
        ListTag officersList = tag.getList("officers", Tag.TAG_COMPOUND);
        for (int i = 0; i < officersList.size(); i++) {
            nation.officers.add(officersList.getCompound(i).getUUID("id"));
        }

        // Load members
        ListTag membersList = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < membersList.size(); i++) {
            nation.members.add(membersList.getCompound(i).getUUID("id"));
        }

        // Load allies
        ListTag alliesList = tag.getList("allies", Tag.TAG_COMPOUND);
        for (int i = 0; i < alliesList.size(); i++) {
            nation.allies.add(alliesList.getCompound(i).getUUID("id"));
        }

        // Load enemies
        ListTag enemiesList = tag.getList("enemies", Tag.TAG_COMPOUND);
        for (int i = 0; i < enemiesList.size(); i++) {
            nation.enemies.add(enemiesList.getCompound(i).getUUID("id"));
        }

        // Load states
        ListTag statesList = tag.getList("states", Tag.TAG_COMPOUND);
        for (int i = 0; i < statesList.size(); i++) {
            State state = State.load(statesList.getCompound(i));
            nation.states.put(state.getId(), state);
        }

        return nation;
    }

    public void addState(State state) {
        states.put(state.getId(), state);
    }
}

