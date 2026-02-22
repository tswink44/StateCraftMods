package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a state within a nation
 * States contain cities and provide regional governance
 * Players can be citizens of a state directly, or through city membership
 */
public class State {
    private final UUID id;
    private String name;
    private UUID nationId;
    private UUID governorId; // Player who manages the state
    private final Map<UUID, City> cities;
    private final Set<UUID> citizens; // Direct state citizens (not through cities)

    // State settings
    private int maxCities;
    private int maxChunks; // Maximum total chunks across all cities in this state
    private String description;
    private String flagUrl;
    private double cityPassThroughRate; // Rate cities must give to state (e.g., 0.20 = 20%)
    private double salesTaxRate; // State's sales tax rate (e.g., 0.10 = 10%)

    public State(UUID id, String name, UUID nationId, UUID governorId) {
        this.id = id;
        this.name = name;
        this.nationId = nationId;
        this.governorId = governorId;
        this.cities = new HashMap<>();
        this.citizens = new HashSet<>();
        this.maxCities = 10; // Default max cities per state
        this.maxChunks = 500; // Default max chunks per state
        this.description = "";
        this.flagUrl = "";
        this.cityPassThroughRate = 0.20; // Default 20% from cities
        this.salesTaxRate = 0.0; // Default 0% state sales tax

        // Governor is automatically a citizen (if not vacant)
        if (governorId != null) {
            citizens.add(governorId);
        }
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

    public UUID getNationId() {
        return nationId;
    }

    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    public UUID getGovernorId() {
        return governorId;
    }

    public void setGovernorId(UUID governorId) {
        this.governorId = governorId;
    }

    public int getMaxCities() {
        return maxCities;
    }

    public void setMaxCities(int maxCities) {
        this.maxCities = maxCities;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = Math.max(1, maxChunks);
    }

    /**
     * Get total chunk count across all cities in this state
     */
    public int getTotalChunkCount() {
        int total = 0;
        for (City city : cities.values()) {
            total += city.getChunkCount();
        }
        return total;
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

    public double getCityPassThroughRate() {
        return cityPassThroughRate;
    }

    public void setCityPassThroughRate(double cityPassThroughRate) {
        // Clamp between 0 and 1 (0% to 100%)
        this.cityPassThroughRate = Math.max(0, Math.min(1.0, cityPassThroughRate));
    }

    public double getSalesTaxRate() {
        return salesTaxRate;
    }

    public void setSalesTaxRate(double salesTaxRate) {
        // Clamp between 0 and 0.5 (0% to 50%)
        this.salesTaxRate = Math.max(0, Math.min(0.5, salesTaxRate));
    }

    // City Management
    public City createCity(String cityName, UUID mayorId) {
        if (cities.size() >= maxCities) {
            return null; // Max cities reached
        }
        City city = new City(UUID.randomUUID(), cityName, this.id, mayorId);
        cities.put(city.getId(), city);
        return city;
    }

    public boolean removeCity(UUID cityId) {
        return cities.remove(cityId) != null;
    }

    public City getCity(UUID cityId) {
        return cities.get(cityId);
    }

    public City getCityByName(String name) {
        for (City city : cities.values()) {
            if (city.getName().equalsIgnoreCase(name)) {
                return city;
            }
        }
        return null;
    }

    public Collection<City> getAllCities() {
        return Collections.unmodifiableCollection(cities.values());
    }

    public int getCityCount() {
        return cities.size();
    }


    // Citizen Management
    public Set<UUID> getCitizens() {
        return Collections.unmodifiableSet(citizens);
    }

    public void addCitizen(UUID playerId) {
        citizens.add(playerId);
    }

    public void removeCitizen(UUID playerId) {
        if (!playerId.equals(governorId)) {
            citizens.remove(playerId);
        }
    }

    public boolean isCitizen(UUID playerId) {
        // Direct citizen or citizen through a city
        if (citizens.contains(playerId)) return true;
        for (City city : cities.values()) {
            if (city.isResident(playerId)) return true;
        }
        return false;
    }

    public boolean isDirectCitizen(UUID playerId) {
        return citizens.contains(playerId);
    }

    /**
     * Check if player owns any chunk in this state
     */
    public boolean playerOwnsChunkInState(UUID playerId) {
        for (City city : cities.values()) {
            for (ClaimedChunk chunk : city.getChunks()) {
                if (playerId.equals(chunk.getPlayerOwner())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<UUID> getAllResidents() {
        Set<UUID> allResidents = new HashSet<>();
        allResidents.add(governorId);
        allResidents.addAll(citizens);
        for (City city : cities.values()) {
            allResidents.addAll(city.getResidents());
        }
        return allResidents;
    }

    public PermissionLevel getPlayerRole(UUID playerId) {
        if (playerId.equals(governorId)) {
            return PermissionLevel.ADMIN;
        }
        // Check if player is in any city
        for (City city : cities.values()) {
            PermissionLevel cityRole = city.getPlayerRole(playerId);
            if (cityRole != PermissionLevel.OUTSIDER) {
                return cityRole;
            }
        }
        return PermissionLevel.OUTSIDER;
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putUUID("nationId", nationId);
        if (governorId != null) {
            tag.putUUID("governorId", governorId);
        }
        tag.putInt("maxCities", maxCities);
        tag.putInt("maxChunks", maxChunks);
        tag.putString("description", description);
        tag.putString("flagUrl", flagUrl);
        tag.putDouble("cityPassThroughRate", cityPassThroughRate);
        tag.putDouble("salesTaxRate", salesTaxRate);

        // Save citizens
        ListTag citizensList = new ListTag();
        for (UUID citizen : citizens) {
            CompoundTag citizenTag = new CompoundTag();
            citizenTag.putUUID("id", citizen);
            citizensList.add(citizenTag);
        }
        tag.put("citizens", citizensList);

        // Save cities
        ListTag citiesList = new ListTag();
        for (City city : cities.values()) {
            citiesList.add(city.save());
        }
        tag.put("cities", citiesList);

        return tag;
    }

    public static State load(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String name = tag.getString("name");
        UUID nationId = tag.getUUID("nationId");
        UUID governorId = tag.contains("governorId") ? tag.getUUID("governorId") : null;

        State state = new State(id, name, nationId, governorId);
        state.maxCities = tag.getInt("maxCities");
        state.maxChunks = tag.contains("maxChunks") ? tag.getInt("maxChunks") : 500;
        state.description = tag.getString("description");
        state.flagUrl = tag.getString("flagUrl");
        // Load cityPassThroughRate (check old name for backward compatibility)
        if (tag.contains("cityPassThroughRate")) {
            state.cityPassThroughRate = tag.getDouble("cityPassThroughRate");
        } else if (tag.contains("nationPassThroughRate")) {
            state.cityPassThroughRate = tag.getDouble("nationPassThroughRate");
        } else {
            state.cityPassThroughRate = 0.20;
        }

        // Load salesTaxRate
        if (tag.contains("salesTaxRate")) {
            state.salesTaxRate = tag.getDouble("salesTaxRate");
        } else {
            state.salesTaxRate = 0.0;
        }

        // Load citizens
        if (tag.contains("citizens")) {
            ListTag citizensList = tag.getList("citizens", Tag.TAG_COMPOUND);
            for (int i = 0; i < citizensList.size(); i++) {
                CompoundTag citizenTag = citizensList.getCompound(i);
                state.citizens.add(citizenTag.getUUID("id"));
            }
        }

        // Load cities
        ListTag citiesList = tag.getList("cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < citiesList.size(); i++) {
            City city = City.load(citiesList.getCompound(i));
            state.cities.put(city.getId(), city);
        }

        return state;
    }

    public void addCity(City city) {
        cities.put(city.getId(), city);
    }
}

