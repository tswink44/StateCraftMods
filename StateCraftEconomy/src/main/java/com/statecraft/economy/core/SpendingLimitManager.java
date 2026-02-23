package com.statecraft.economy.core;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.config.EconomyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Manages per-role daily spending limits for government treasury withdrawals/transfers.
 *
 * Roles (from highest to lowest privilege):
 * - Nation Leader (OWNER) — limit configurable via legislature policy, default from config
 * - Nation Admin (ADMIN) — limit from config
 * - State Governor — limit from config
 * - City Mayor — limit from config
 *
 * Tracks cumulative daily spending per player per government account.
 * Resets at the start of each real-world day (midnight UTC).
 */
public class SpendingLimitManager {

    private static SpendingLimitManager instance;

    /**
     * Tracks daily spending: key = "playerUUID:accountType:accountUUID", value = amount spent today.
     * accountType is "NATION", "STATE", or "CITY".
     */
    private final Map<String, Double> dailySpending = new HashMap<>();

    /**
     * The day number (days since epoch) the current spending data applies to.
     * When the current day changes, all spending is reset.
     */
    private long currentDay;

    /**
     * Per-nation leader spending limits set via legislature.
     * Key = nationId, value = daily limit. Overrides the config default for nation leaders only.
     */
    private final Map<UUID, Double> nationLeaderLimitOverrides = new HashMap<>();

    private boolean dirty = false;

    private SpendingLimitManager() {
        this.currentDay = getCurrentDayNumber();
    }

    public static SpendingLimitManager getInstance() {
        if (instance == null) {
            instance = new SpendingLimitManager();
        }
        return instance;
    }

    // ==================== Day Tracking ====================

    private static long getCurrentDayNumber() {
        return System.currentTimeMillis() / (1000L * 60 * 60 * 24); // Days since epoch (UTC)
    }

    /**
     * Check if the day has rolled over and reset spending if so.
     */
    private void checkDayRollover() {
        long today = getCurrentDayNumber();
        if (today != currentDay) {
            dailySpending.clear();
            currentDay = today;
            dirty = true;
            StateCraftEconomy.LOGGER.info("Daily spending limits reset (new day)");
        }
    }

    // ==================== Spending Limit Queries ====================

    /**
     * The role a player has with respect to a specific government account.
     */
    public enum GovernmentRole {
        NATION_LEADER,
        NATION_OFFICER,
        STATE_GOVERNOR,
        CITY_MAYOR,
        COMPANY_OFFICER,
        UNKNOWN
    }

    /**
     * Get the daily spending limit for a given role and (optionally) a nation.
     * Returns 0 for unlimited.
     */
    public double getLimitForRole(GovernmentRole role, UUID nationId) {
        switch (role) {
            case NATION_LEADER -> {
                // Check for legislature override first
                if (nationId != null && nationLeaderLimitOverrides.containsKey(nationId)) {
                    return nationLeaderLimitOverrides.get(nationId);
                }
                return EconomyConfig.NATION_LEADER_DAILY_LIMIT.get();
            }
            case NATION_OFFICER -> {
                return EconomyConfig.NATION_OFFICER_DAILY_LIMIT.get();
            }
            case STATE_GOVERNOR -> {
                return EconomyConfig.STATE_GOVERNOR_DAILY_LIMIT.get();
            }
            case CITY_MAYOR -> {
                return EconomyConfig.CITY_MAYOR_DAILY_LIMIT.get();
            }
            case COMPANY_OFFICER -> {
                return EconomyConfig.COMPANY_OFFICER_DAILY_LIMIT.get();
            }
            default -> {
                return 0; // Unknown role — no limit (shouldn't happen; access check should block)
            }
        }
    }

    /**
     * Get how much a player has already spent today on a specific government account.
     */
    public double getSpentToday(UUID playerId, String accountType, UUID accountId) {
        checkDayRollover();
        String key = makeKey(playerId, accountType, accountId);
        return dailySpending.getOrDefault(key, 0.0);
    }

    /**
     * Get the remaining daily allowance for a player on a specific government account.
     * Returns Double.MAX_VALUE if unlimited (limit == 0).
     */
    public double getRemainingAllowance(UUID playerId, String accountType, UUID accountId,
                                         GovernmentRole role, UUID nationId) {
        double limit = getLimitForRole(role, nationId);
        if (limit <= 0) return Double.MAX_VALUE; // Unlimited

        double spent = getSpentToday(playerId, accountType, accountId);
        return Math.max(0, limit - spent);
    }

    /**
     * Check if a withdrawal/transfer of the given amount would exceed the player's daily limit.
     * Server operators (permission level 2+) bypass spending limits entirely.
     * @return null if allowed, or an error message string if denied
     */
    public String checkSpendingLimit(UUID playerId, String accountType, UUID accountId,
                                      double amount, GovernmentRole role, UUID nationId) {
        return checkSpendingLimit(playerId, accountType, accountId, amount, role, nationId, null);
    }

    /**
     * Check if a withdrawal/transfer of the given amount would exceed the player's daily limit.
     * Server operators (permission level 2+) bypass spending limits entirely.
     * @param player Optional ServerPlayer - if provided and is op, limit is bypassed
     * @return null if allowed, or an error message string if denied
     */
    public String checkSpendingLimit(UUID playerId, String accountType, UUID accountId,
                                      double amount, GovernmentRole role, UUID nationId,
                                      net.minecraft.server.level.ServerPlayer player) {
        // Server ops bypass spending limits
        if (player != null && player.hasPermissions(2)) {
            return null;
        }

        double limit = getLimitForRole(role, nationId);
        if (limit <= 0) return null; // Unlimited

        checkDayRollover();
        double spent = getSpentToday(playerId, accountType, accountId);
        double remaining = limit - spent;

        if (amount > remaining) {
            String limitStr = EconomyManager.getInstance().formatCurrency(limit);
            String spentStr = EconomyManager.getInstance().formatCurrency(spent);
            String remainStr = EconomyManager.getInstance().formatCurrency(Math.max(0, remaining));
            return "Daily spending limit exceeded. Limit: " + limitStr +
                   ", Spent today: " + spentStr + ", Remaining: " + remainStr;
        }
        return null; // Allowed
    }

    /**
     * Record that a player has spent an amount from a government account.
     * Call this AFTER a successful withdrawal/transfer.
     */
    public void recordSpending(UUID playerId, String accountType, UUID accountId, double amount) {
        checkDayRollover();
        String key = makeKey(playerId, accountType, accountId);
        dailySpending.merge(key, amount, Double::sum);
        dirty = true;
    }

    // ==================== Nation Leader Limit Overrides ====================

    /**
     * Set a per-nation leader spending limit (from legislature policy).
     * @param limit the daily limit, or 0 for unlimited
     */
    public void setNationLeaderLimit(UUID nationId, double limit) {
        if (limit <= 0) {
            nationLeaderLimitOverrides.remove(nationId);
        } else {
            nationLeaderLimitOverrides.put(nationId, limit);
        }
        dirty = true;
    }

    /**
     * Get the per-nation leader spending limit override, or -1 if not set (uses config default).
     */
    public double getNationLeaderLimitOverride(UUID nationId) {
        return nationLeaderLimitOverrides.getOrDefault(nationId, -1.0);
    }

    // ==================== Persistence ====================

    private static String makeKey(UUID playerId, String accountType, UUID accountId) {
        return playerId.toString() + ":" + accountType + ":" + accountId.toString();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("CurrentDay", currentDay);

        // Save daily spending
        ListTag spendingList = new ListTag();
        for (Map.Entry<String, Double> entry : dailySpending.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Key", entry.getKey());
            entryTag.putDouble("Amount", entry.getValue());
            spendingList.add(entryTag);
        }
        tag.put("DailySpending", spendingList);

        // Save nation leader limit overrides
        ListTag overridesList = new ListTag();
        for (Map.Entry<UUID, Double> entry : nationLeaderLimitOverrides.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("NationId", entry.getKey());
            entryTag.putDouble("Limit", entry.getValue());
            overridesList.add(entryTag);
        }
        tag.put("NationLeaderLimitOverrides", overridesList);

        return tag;
    }

    public void load(CompoundTag tag) {
        dailySpending.clear();
        nationLeaderLimitOverrides.clear();

        if (tag.contains("CurrentDay")) {
            long savedDay = tag.getLong("CurrentDay");
            long today = getCurrentDayNumber();
            currentDay = today;

            // Only load spending data if it's from today
            if (savedDay == today && tag.contains("DailySpending")) {
                ListTag spendingList = tag.getList("DailySpending", Tag.TAG_COMPOUND);
                for (int i = 0; i < spendingList.size(); i++) {
                    CompoundTag entryTag = spendingList.getCompound(i);
                    dailySpending.put(entryTag.getString("Key"), entryTag.getDouble("Amount"));
                }
            }
        }

        // Load nation leader limit overrides (these persist across days)
        if (tag.contains("NationLeaderLimitOverrides")) {
            ListTag overridesList = tag.getList("NationLeaderLimitOverrides", Tag.TAG_COMPOUND);
            for (int i = 0; i < overridesList.size(); i++) {
                CompoundTag entryTag = overridesList.getCompound(i);
                UUID nationId = entryTag.getUUID("NationId");
                double limit = entryTag.getDouble("Limit");
                nationLeaderLimitOverrides.put(nationId, limit);
            }
        }

        dirty = false;
        StateCraftEconomy.LOGGER.info("Loaded spending limit data: {} spending entries, {} nation leader overrides",
            dailySpending.size(), nationLeaderLimitOverrides.size());
    }
}

