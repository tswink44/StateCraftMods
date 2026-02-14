package com.statecraft.core;

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
    private final Set<UUID> members; // Basic nation members (not in a city yet)
    private final Map<UUID, State> states;
    private final Set<UUID> allies; // Allied nation IDs
    private final Set<UUID> enemies; // Enemy nation IDs

    // Nation settings
    private int maxStates;
    private int maxChunksPerCity;
    private String description;
    private String tag; // Short tag/prefix for chat
    private boolean open; // Can players join without invite?

    // Treasury (for future economy integration)
    private long balance;

    public Nation(UUID id, String name, UUID leaderId) {
        this.id = id;
        this.name = name;
        this.leaderId = leaderId;
        this.admins = new HashSet<>();
        this.members = new HashSet<>();
        this.states = new HashMap<>();
        this.allies = new HashSet<>();
        this.enemies = new HashSet<>();
        this.maxStates = 5; // Default max states
        this.maxChunksPerCity = 50; // Default max chunks per city
        this.description = "";
        this.tag = "";
        this.open = false;
        this.balance = 0;
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

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public void addMember(UUID playerId) {
        members.add(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
        admins.remove(playerId);
    }

    public int getMaxStates() {
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

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public void deposit(long amount) {
        this.balance += amount;
    }

    public boolean withdraw(long amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
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
        if (states.size() >= maxStates) {
            return null; // Max states reached
        }
        State state = new State(UUID.randomUUID(), stateName, this.id, governorId);
        states.put(state.getId(), state);
        return state;
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
        tag.putString("description", description);
        tag.putString("tag", this.tag);
        tag.putBoolean("open", open);
        tag.putLong("balance", balance);

        // Save admins
        ListTag adminsList = new ListTag();
        for (UUID admin : admins) {
            CompoundTag adminTag = new CompoundTag();
            adminTag.putUUID("id", admin);
            adminsList.add(adminTag);
        }
        tag.put("admins", adminsList);

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
        nation.description = tag.getString("description");
        nation.tag = tag.getString("tag");
        nation.open = tag.getBoolean("open");
        nation.balance = tag.getLong("balance");

        // Load admins
        ListTag adminsList = tag.getList("admins", Tag.TAG_COMPOUND);
        for (int i = 0; i < adminsList.size(); i++) {
            nation.admins.add(adminsList.getCompound(i).getUUID("id"));
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

