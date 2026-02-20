package com.statecraft.economy.company;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents a player-created company with shared ownership via shares.
 *
 * Companies have:
 * - A founder (creator, highest authority)
 * - Officers (can access company vault, manage day-to-day operations)
 * - Shareholders (own shares, receive dividends)
 * - A treasury (BankAccount managed by EconomyManager)
 * - A headquarters city (determines tax jurisdiction)
 * - Configurable dividends paid to shareholders
 */
public class Company {

    /**
     * Type of company. Determines special behavior and restrictions.
     */
    public enum CompanyType {
        /** Standard company with shared ownership and dividends. */
        GENERAL,
        /** Bank — holds depositor funds, pays interest, offers loans. */
        BANK
    }

    private final UUID id;
    private String name;
    private final UUID founderId;
    private CompanyType companyType = CompanyType.GENERAL;
    private final Set<UUID> officers = new HashSet<>();
    private final Map<UUID, Integer> shareholders = new HashMap<>(); // playerId -> share count
    private int totalShares;
    private String description = "";

    // Headquarters — determines tax jurisdiction
    private UUID headquartersCityId; // nullable if not registered in a city

    // Dividend configuration
    private boolean dividendsEnabled = false;
    private double dividendRate = 0.0; // Percentage of balance paid out per cycle (0.0 - 1.0)
    private long dividendPeriodTicks = 72000; // Default: 1 real hour
    private long lastDividendTime = 0;

    // Metadata
    private final long createdTime;

    /**
     * Create a new company.
     * The founder receives all initial shares.
     */
    public Company(UUID id, String name, UUID founderId, int totalShares) {
        this.id = id;
        this.name = name;
        this.founderId = founderId;
        this.totalShares = Math.max(1, totalShares);
        this.createdTime = System.currentTimeMillis();

        // Founder gets all shares initially
        this.shareholders.put(founderId, this.totalShares);
    }

    /**
     * Private constructor for loading from NBT.
     */
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

    // ==================== Company Type ====================

    public CompanyType getCompanyType() { return companyType; }
    public void setCompanyType(CompanyType type) { this.companyType = type; }
    public boolean isBank() { return companyType == CompanyType.BANK; }

    // ==================== Headquarters ====================

    public UUID getHeadquartersCityId() { return headquartersCityId; }
    public void setHeadquartersCityId(UUID cityId) { this.headquartersCityId = cityId; }

    // ==================== Roles & Permissions ====================

    public boolean isFounder(UUID playerId) {
        return founderId.equals(playerId);
    }

    public boolean isOfficer(UUID playerId) {
        return officers.contains(playerId) || isFounder(playerId);
    }

    public boolean isShareholder(UUID playerId) {
        return shareholders.containsKey(playerId) && shareholders.get(playerId) > 0;
    }

    /**
     * Can the player manage the company (founder or officer)?
     */
    public boolean canManage(UUID playerId) {
        return isOfficer(playerId);
    }

    public boolean addOfficer(UUID playerId) {
        return officers.add(playerId);
    }

    public boolean removeOfficer(UUID playerId) {
        // Can't remove the founder from officers
        if (isFounder(playerId)) return false;
        return officers.remove(playerId);
    }

    public Set<UUID> getOfficers() {
        return Collections.unmodifiableSet(officers);
    }

    // ==================== Share Management ====================

    public int getTotalShares() { return totalShares; }

    public int getShareCount(UUID playerId) {
        return shareholders.getOrDefault(playerId, 0);
    }

    /**
     * Get the percentage of ownership (0.0 - 1.0).
     */
    public double getSharePercentage(UUID playerId) {
        if (totalShares <= 0) return 0;
        return (double) getShareCount(playerId) / totalShares;
    }

    /**
     * Get all shareholders and their share counts.
     */
    public Map<UUID, Integer> getShareholders() {
        return Collections.unmodifiableMap(shareholders);
    }

    /**
     * Get the number of issued shares (sum of all shareholder shares).
     * May differ from totalShares if shares are unissued.
     */
    public int getIssuedShares() {
        return shareholders.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Transfer shares from one player to another.
     * @return true if successful, false if insufficient shares
     */
    public boolean transferShares(UUID from, UUID to, int count) {
        if (count <= 0) return false;
        int fromShares = getShareCount(from);
        if (fromShares < count) return false;

        // Remove from sender
        int remaining = fromShares - count;
        if (remaining <= 0) {
            shareholders.remove(from);
        } else {
            shareholders.put(from, remaining);
        }

        // Add to receiver
        shareholders.merge(to, count, Integer::sum);
        return true;
    }

    /**
     * Issue new shares to a player (increases total share count).
     * Only the founder can do this.
     */
    public void issueShares(UUID to, int count) {
        if (count <= 0) return;
        totalShares += count;
        shareholders.merge(to, count, Integer::sum);
    }

    // ==================== Dividend Configuration ====================

    public boolean isDividendsEnabled() { return dividendsEnabled; }
    public void setDividendsEnabled(boolean enabled) { this.dividendsEnabled = enabled; }

    public double getDividendRate() { return dividendRate; }
    public void setDividendRate(double rate) { this.dividendRate = Math.max(0, Math.min(1.0, rate)); }

    public long getDividendPeriodTicks() { return dividendPeriodTicks; }
    public void setDividendPeriodTicks(long ticks) { this.dividendPeriodTicks = Math.max(1200, ticks); }

    public long getLastDividendTime() { return lastDividendTime; }
    public void setLastDividendTime(long time) { this.lastDividendTime = time; }

    /**
     * Calculate the dividend payout for a specific shareholder.
     * @param companyBalance The current company treasury balance
     * @param playerId The shareholder to calculate for
     * @return The payout amount
     */
    public double calculateDividend(double companyBalance, UUID playerId) {
        if (!dividendsEnabled || dividendRate <= 0 || companyBalance <= 0) return 0;
        double totalPayout = companyBalance * dividendRate;
        return totalPayout * getSharePercentage(playerId);
    }

    /**
     * Calculate the total dividend payout for all shareholders.
     * @param companyBalance The current company treasury balance
     * @return The total payout amount
     */
    public double calculateTotalDividend(double companyBalance) {
        if (!dividendsEnabled || dividendRate <= 0 || companyBalance <= 0) return 0;
        return companyBalance * dividendRate;
    }

    // ==================== NBT Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putUUID("FounderId", founderId);
        tag.putInt("TotalShares", totalShares);
        tag.putString("Description", description);
        tag.putLong("CreatedTime", createdTime);
        tag.putString("CompanyType", companyType.name());

        // Headquarters
        if (headquartersCityId != null) {
            tag.putUUID("HeadquartersCityId", headquartersCityId);
        }

        // Dividends
        tag.putBoolean("DividendsEnabled", dividendsEnabled);
        tag.putDouble("DividendRate", dividendRate);
        tag.putLong("DividendPeriodTicks", dividendPeriodTicks);
        tag.putLong("LastDividendTime", lastDividendTime);

        // Officers
        ListTag officersList = new ListTag();
        for (UUID officer : officers) {
            CompoundTag officerTag = new CompoundTag();
            officerTag.putUUID("Id", officer);
            officersList.add(officerTag);
        }
        tag.put("Officers", officersList);

        // Shareholders
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

        // Company type
        if (tag.contains("CompanyType")) {
            try {
                company.companyType = CompanyType.valueOf(tag.getString("CompanyType"));
            } catch (IllegalArgumentException e) {
                company.companyType = CompanyType.GENERAL;
            }
        }

        // Headquarters
        if (tag.contains("HeadquartersCityId")) {
            company.headquartersCityId = tag.getUUID("HeadquartersCityId");
        }

        // Dividends
        company.dividendsEnabled = tag.getBoolean("DividendsEnabled");
        company.dividendRate = tag.getDouble("DividendRate");
        if (tag.contains("DividendPeriodTicks")) {
            company.dividendPeriodTicks = tag.getLong("DividendPeriodTicks");
        }
        if (tag.contains("LastDividendTime")) {
            company.lastDividendTime = tag.getLong("LastDividendTime");
        }

        // Officers
        if (tag.contains("Officers")) {
            ListTag officersList = tag.getList("Officers", Tag.TAG_COMPOUND);
            for (int i = 0; i < officersList.size(); i++) {
                company.officers.add(officersList.getCompound(i).getUUID("Id"));
            }
        }

        // Shareholders
        if (tag.contains("Shareholders")) {
            ListTag shareholdersList = tag.getList("Shareholders", Tag.TAG_COMPOUND);
            for (int i = 0; i < shareholdersList.size(); i++) {
                CompoundTag shTag = shareholdersList.getCompound(i);
                UUID playerId = shTag.getUUID("PlayerId");
                int shares = shTag.getInt("Shares");
                company.shareholders.put(playerId, shares);
            }
        }

        return company;
    }
}





