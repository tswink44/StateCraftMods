package com.statecraft.company;

import com.statecraft.StateCraft;
import com.statecraft.config.StateCraftConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Central manager for all companies.
 * Handles company CRUD, lookups, and persistence.
 * Dividend distribution and taxation remain in StateCraftEconomy via integration.
 *
 * Singleton — one instance per server.
 */
public class CompanyManager {
    private static CompanyManager instance;

    private final Map<UUID, Company> companies = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
    private boolean dirty = false;

    private CompanyManager() {}

    public static CompanyManager getInstance() {
        if (instance == null) {
            instance = new CompanyManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    // ==================== Company CRUD ====================

    @Nullable
    public Company createCompany(String name, UUID founderId, int totalShares, @Nullable UUID headquartersCityId) {
        if (nameIndex.containsKey(name.toLowerCase())) {
            return null;
        }

        int maxPerPlayer = StateCraftConfig.MAX_COMPANIES_PER_PLAYER.get();
        if (maxPerPlayer > 0) {
            long foundedCount = companies.values().stream()
                .filter(c -> c.getFounderId().equals(founderId))
                .count();
            if (foundedCount >= maxPerPlayer) {
                return null;
            }
        }

        Company company = new Company(UUID.randomUUID(), name, founderId, totalShares);
        company.setHeadquartersCityId(headquartersCityId);

        companies.put(company.getId(), company);
        nameIndex.put(name.toLowerCase(), company.getId());

        dirty = true;
        StateCraft.LOGGER.info("Company '{}' created by {} with {} shares", name, founderId, totalShares);
        return company;
    }

    /**
     * Dissolve a company. Economy-side cleanup (balance distribution) is handled
     * by StateCraftEconomy via integration before calling this.
     */
    public boolean dissolveCompany(UUID companyId, UUID initiatorId) {
        Company company = companies.get(companyId);
        if (company == null) return false;
        if (!company.isFounder(initiatorId)) return false;

        nameIndex.remove(company.getName().toLowerCase());
        companies.remove(companyId);

        dirty = true;
        StateCraft.LOGGER.info("Company '{}' dissolved by {}", company.getName(), initiatorId);
        return true;
    }

    // ==================== Lookups ====================

    @Nullable
    public Company getCompany(UUID companyId) {
        return companies.get(companyId);
    }

    @Nullable
    public Company getCompanyByName(String name) {
        UUID id = nameIndex.get(name.toLowerCase());
        return id != null ? companies.get(id) : null;
    }

    public Collection<Company> getAllCompanies() {
        return Collections.unmodifiableCollection(companies.values());
    }

    public boolean renameCompany(UUID companyId, String newName) {
        Company company = companies.get(companyId);
        if (company == null) return false;
        if (nameIndex.containsKey(newName.toLowerCase())) return false;

        nameIndex.remove(company.getName().toLowerCase());
        company.setName(newName);
        nameIndex.put(newName.toLowerCase(), companyId);
        dirty = true;
        return true;
    }

    public List<Company> getPlayerCompanies(UUID playerId) {
        return companies.values().stream()
            .filter(c -> c.isShareholder(playerId) || c.isOfficer(playerId))
            .collect(Collectors.toList());
    }

    public List<Company> getPlayerManagedCompanies(UUID playerId) {
        return companies.values().stream()
            .filter(c -> c.canManage(playerId))
            .collect(Collectors.toList());
    }

    // ==================== Persistence ====================

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag companiesList = new ListTag();
        for (Company company : companies.values()) {
            companiesList.add(company.save());
        }
        tag.put("Companies", companiesList);
        return tag;
    }

    public void load(CompoundTag tag) {
        companies.clear();
        nameIndex.clear();

        if (tag.contains("Companies")) {
            ListTag companiesList = tag.getList("Companies", Tag.TAG_COMPOUND);
            for (int i = 0; i < companiesList.size(); i++) {
                Company company = Company.load(companiesList.getCompound(i));
                companies.put(company.getId(), company);
                nameIndex.put(company.getName().toLowerCase(), company.getId());
            }
        }

        dirty = false;
        StateCraft.LOGGER.info("Loaded {} companies", companies.size());
    }
}

