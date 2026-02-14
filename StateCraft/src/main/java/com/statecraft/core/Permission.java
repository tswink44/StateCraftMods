package com.statecraft.core;

/**
 * Permission types that can be granted in claimed chunks
 */
public enum Permission {
    BUILD,      // Place blocks
    BREAK,      // Break blocks
    INTERACT,   // Use doors, buttons, levers, etc.
    CONTAINER,  // Access chests, furnaces, etc.
    ATTACK,     // Attack entities
    MANAGE      // Manage chunk settings and permissions
}

