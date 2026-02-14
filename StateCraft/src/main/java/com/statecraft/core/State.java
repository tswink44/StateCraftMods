package com.statecraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a state within a nation
 * States contain cities and provide regional governance
 */
public class State {
    private final UUID id;
    private String name;
    private UUID nationId;
    private UUID governorId; // Player who manages the state
    private final Map<UUID, City> cities;

    // State settings
    private int maxCities;
    private String description;

    public State(UUID id, String name, UUID nationId, UUID governorId) {
        this.id = id;
        this.name = name;
        this.nationId = nationId;
        this.governorId = governorId;
        this.cities = new HashMap<>();
        this.maxCities = 10; // Default max cities per state
        this.description = "";
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public int getTotalChunkCount() {
        return cities.values().stream().mapToInt(City::getChunkCount).sum();
    }

    public Set<UUID> getAllResidents() {
        Set<UUID> allResidents = new HashSet<>();
        allResidents.add(governorId);
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
        tag.putUUID("governorId", governorId);
        tag.putInt("maxCities", maxCities);
        tag.putString("description", description);

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
        UUID governorId = tag.getUUID("governorId");

        State state = new State(id, name, nationId, governorId);
        state.maxCities = tag.getInt("maxCities");
        state.description = tag.getString("description");

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

