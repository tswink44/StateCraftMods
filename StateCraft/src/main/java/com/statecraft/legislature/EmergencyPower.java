package com.statecraft.legislature;

/**
 * Defines emergency powers that the nation leader can invoke unilaterally
 * Each has automatic legislature review requirements
 */
public enum EmergencyPower {
    MARTIAL_LAW("Martial Law", 72, 30,
        "Suspends open borders and restricts building for non-citizens"),
    ECONOMIC_EMERGENCY("Economic Emergency", 48, 30,
        "Temporarily freezes all treasury withdrawals except by Leader"),
    DIPLOMATIC_CRISIS("Diplomatic Crisis", 0, 14,
        "Instantly declare enemy status (war) without legislature vote"),
    EMERGENCY_TAX("Emergency Tax", 0, 60,
        "One-time 10% levy on all citizen balances"),
    SUCCESSION_CRISIS("Succession Crisis", 168, 0,
        "Appoint temporary successor if Leader going offline");

    private final String displayName;
    private final int durationHours;  // 0 = permanent/instant
    private final int cooldownDays;   // Days before can use again
    private final String description;

    EmergencyPower(String displayName, int durationHours, int cooldownDays, String description) {
        this.displayName = displayName;
        this.durationHours = durationHours;
        this.cooldownDays = cooldownDays;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public int getDurationHours() { return durationHours; }
    public int getCooldownDays() { return cooldownDays; }
    public String getDescription() { return description; }

    public boolean isPermanent() { return durationHours == 0; }
    public boolean canBeOverridden() { return this != SUCCESSION_CRISIS; }
}

