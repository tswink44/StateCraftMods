package com.statecraft.economy.company;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Stores dividend configuration for a single company.
 * This data is owned by the Economy mod, not the base StateCraft mod,
 * because dividends involve money transfers.
 */
public class DividendConfig {

    private final UUID companyId;
    private boolean enabled = false;
    private double rate = 0.0;           // 0.0 - 1.0
    private long periodTicks = 72000;    // Default: 1 real hour
    private long lastDividendTime = 0;

    public DividendConfig(UUID companyId) {
        this.companyId = companyId;
    }

    // ==================== Getters / Setters ====================

    public UUID getCompanyId() { return companyId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = Math.max(0, Math.min(1.0, rate)); }

    public long getPeriodTicks() { return periodTicks; }
    public void setPeriodTicks(long ticks) { this.periodTicks = Math.max(1200, ticks); }

    public long getLastDividendTime() { return lastDividendTime; }
    public void setLastDividendTime(long time) { this.lastDividendTime = time; }

    // ==================== Calculations ====================

    /**
     * Calculate dividend payout for a specific shareholder.
     * @param companyBalance The current company treasury balance
     * @param sharePercentage The shareholder's ownership fraction (0.0 - 1.0)
     * @return The payout amount for this shareholder
     */
    public double calculateDividend(double companyBalance, double sharePercentage) {
        if (!enabled || rate <= 0 || companyBalance <= 0) return 0;
        return companyBalance * rate * sharePercentage;
    }

    /**
     * Calculate total dividend payout for all shareholders.
     * @param companyBalance The current company treasury balance
     * @return The total payout amount
     */
    public double calculateTotalDividend(double companyBalance) {
        if (!enabled || rate <= 0 || companyBalance <= 0) return 0;
        return companyBalance * rate;
    }

    // ==================== NBT Persistence ====================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("CompanyId", companyId);
        tag.putBoolean("Enabled", enabled);
        tag.putDouble("Rate", rate);
        tag.putLong("PeriodTicks", periodTicks);
        tag.putLong("LastDividendTime", lastDividendTime);
        return tag;
    }

    public static DividendConfig load(CompoundTag tag) {
        UUID id = tag.getUUID("CompanyId");
        DividendConfig config = new DividendConfig(id);
        config.enabled = tag.getBoolean("Enabled");
        config.rate = tag.getDouble("Rate");
        if (tag.contains("PeriodTicks")) {
            config.periodTicks = tag.getLong("PeriodTicks");
        }
        if (tag.contains("LastDividendTime")) {
            config.lastDividendTime = tag.getLong("LastDividendTime");
        }
        return config;
    }

    /**
     * Import dividend data from legacy Company NBT (migration from old format
     * where dividend config was stored inside Company data).
     */
    public static DividendConfig importFromLegacyCompanyTag(UUID companyId, CompoundTag companyTag) {
        DividendConfig config = new DividendConfig(companyId);
        if (companyTag.contains("DividendsEnabled")) {
            config.enabled = companyTag.getBoolean("DividendsEnabled");
        }
        if (companyTag.contains("DividendRate")) {
            config.rate = companyTag.getDouble("DividendRate");
        }
        if (companyTag.contains("DividendPeriodTicks")) {
            config.periodTicks = companyTag.getLong("DividendPeriodTicks");
        }
        if (companyTag.contains("LastDividendTime")) {
            config.lastDividendTime = companyTag.getLong("LastDividendTime");
        }
        return config;
    }
}

