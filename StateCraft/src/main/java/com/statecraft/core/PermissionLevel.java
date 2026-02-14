package com.statecraft.core;

/**
 * Permission levels/roles in the hierarchy
 * Permissions are inherited from higher levels to lower levels
 */
public enum PermissionLevel {
    OWNER(4),       // Full control - nation owner
    ADMIN(3),       // Administrative access
    MEMBER(2),      // Regular member
    ALLY(1),        // Allied nations/players
    OUTSIDER(0);    // No affiliation

    private final int level;

    PermissionLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isAtLeast(PermissionLevel other) {
        return this.level >= other.level;
    }
}

