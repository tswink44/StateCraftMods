package com.statecraft.company;

import com.statecraft.util.NBTUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a player-created company with shared ownership via shares.
 *
 * Companies have:
 * - A founder (creator, highest authority)
 * - Officers (can manage day-to-day operations)
 * - Shareholders (own shares)
 * - A headquarters city (determines jurisdiction)
 *
 * Economy-specific features (dividends, treasury, taxation) are handled
 * by StateCraftEconomy's CompanyEconomyManager.
 */
public class Company {

    public enum CompanyType {
        GENERAL,
        BANK
    }

    private final UUID id;
    private String name;
    private final UUID founderId;
    private CompanyType companyType = CompanyType.GENERAL;
    private final Set<UUID> officers = new HashSet<>();
    private final Map<UUID, Integer> shareholders = new HashMap<>();
    private int totalShares;
    private String description = "";

    // Headquarters — determines jurisdiction
    private UUID headquartersCityId;

    // Metadata
    private final long createdTime;

    public Company(UUID id, String name, UUID founderId, int totalShares) {
        this.id = id;
        this.name = name;
        this.founderId = founderId;
        this.totalShares = Math.max(1, totalShares);
        this.createdTime = System.currentTimeMillis();
        this.shareholders.put(founderId, this.totalShares);
    }

    private Company(UUID id, String name, UUID founderId, int totalShares, long createdTime) {
        this.id = id;
        this.name = name;
        this.founderId = founderId;
        this.totalShares = totalShares;
        this.createdTime = createdTime;
    }

    // ==================== Identity ====================

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getFounderId() { return founderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getCreatedTime() { return createdTime; }

    public CompanyType getCompanyType() { return companyType; }
    public void setCompanyType(CompanyType type) { this.companyType = type; }
    public boolean isBank() { return companyType == CompanyType.BANK; }

    public UUID getHeadquartersCityId() { return headquartersCityId; }
    public void setHeadquartersCityId(UUID cityId) { this.headquartersCityId = cityId; }

    // ==================== Roles & Permissions ====================

    public boolean isFounder(UUID playerId) { return founderId.equals(playerId); }

    public boolean isOfficer(UUID playerId) {
        return officers.contains(playerId) || isFounder(playerId);
    }

    public boolean isShareholder(UUID playerId) {
        return shareholders.containsKey(playerId) && shareholders.get(playerId) > 0;
    }

    public boolean canManage(UUID playerId) { return isOfficer(playerId); }

    public boolean addOfficer(UUID playerId) { return officers.add(playerId); }

    public boolean removeOfficer(UUID playerId) {
        if (isFounder(playerId)) return false;
        return officers.remove(playerId);
    }

    public Set<UUID> getOfficers() { return Collections.unmodifiableSet(officers); }

    // ==================== Share Management ====================

    public int getTotalShares() { return totalShares; }

    public int getShareCount(UUID playerId) {
        return shareholders.getOrDefault(playerId, 0);
    }

    public double getSharePercentage(UUID playerId) {
        if (totalShares <= 0) return 0;
        return (double) getShareCount(playerId) / totalShares;
    }

    public Map<UUID, Integer> getShareholders() {
        return Collections.unmodifiableMap(shareholders);
    }

    public int getIssuedShares() {
        return shareholders.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean transferShares(UUID from, UUID to, int count) {
        if (count <= 0) return false;
        int fromShares = getShareCount(from);
        if (fromShares < count) return false;

        int remaining = fromShares - count;
        if (remaining <= 0) {
            shareholders.remove(from);
        } else {
            shareholders.put(from, remaining);
        }
        shareholders.merge(to, count, Integer::sum);
        return true;
    }

    public void issueShares(UUID to, int count) {
        if (count <= 0) return;
        totalShares += count;
        shareholders.merge(to, count, Integer::sum);
    }

    /**
     * Buy back shares from a shareholder, reducing total shares.
     * Shares are removed from circulation entirely.
     * @param from The shareholder to buy shares from
     * @param count Number of shares to buy back
     * @return true if successful
     */
    public boolean buybackShares(UUID from, int count) {
        if (count <= 0) return false;
        int current = getShareCount(from);
        if (current < count) return false;

        int remaining = current - count;
        if (remaining <= 0) {
            shareholders.remove(from);
        } else {
            shareholders.put(from, remaining);
        }
        totalShares -= count;
        if (totalShares < 0) totalShares = 0;
        return true;
    }


    // ==================== NBT Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        NBTUtils.putSanitizedString(tag, "Name", name);
        tag.putUUID("FounderId", founderId);
        tag.putInt("TotalShares", totalShares);
        NBTUtils.putSanitizedString(tag, "Description", description);
        tag.putLong("CreatedTime", createdTime);
        tag.putString("CompanyType", companyType.name());

        if (headquartersCityId != null) {
            tag.putUUID("HeadquartersCityId", headquartersCityId);
        }


        ListTag officersList = new ListTag();
        for (UUID officer : officers) {
            CompoundTag officerTag = new CompoundTag();
            officerTag.putUUID("Id", officer);
            officersList.add(officerTag);
        }
        tag.put("Officers", officersList);

        ListTag shareholdersList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : shareholders.entrySet()) {
            CompoundTag shTag = new CompoundTag();
            shTag.putUUID("PlayerId", entry.getKey());
            shTag.putInt("Shares", entry.getValue());
            shareholdersList.add(shTag);
        }
        tag.put("Shareholders", shareholdersList);

        return tag;
    }

    public static Company load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        String name = tag.getString("Name");
        UUID founderId = tag.getUUID("FounderId");
        int totalShares = tag.getInt("TotalShares");
        long createdTime = tag.contains("CreatedTime") ? tag.getLong("CreatedTime") : System.currentTimeMillis();

        Company company = new Company(id, name, founderId, totalShares, createdTime);
        company.description = tag.getString("Description");

        if (tag.contains("CompanyType")) {
            try {
                company.companyType = CompanyType.valueOf(tag.getString("CompanyType"));
            } catch (IllegalArgumentException e) {
                company.companyType = CompanyType.GENERAL;
            }
        }

        if (tag.contains("HeadquartersCityId")) {
            company.headquartersCityId = tag.getUUID("HeadquartersCityId");
        }


        if (tag.contains("Officers")) {
            ListTag officersList = tag.getList("Officers", Tag.TAG_COMPOUND);
            for (int i = 0; i < officersList.size(); i++) {
                company.officers.add(officersList.getCompound(i).getUUID("Id"));
            }
        }

        if (tag.contains("Shareholders")) {
            ListTag shareholdersList = tag.getList("Shareholders", Tag.TAG_COMPOUND);
            for (int i = 0; i < shareholdersList.size(); i++) {
                CompoundTag shTag = shareholdersList.getCompound(i);
                company.shareholders.put(shTag.getUUID("PlayerId"), shTag.getInt("Shares"));
            }
        }

        return company;
    }
}

