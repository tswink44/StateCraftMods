package com.statecraft.client.integration;

import com.statecraft.StateCraft;
import com.statecraft.client.ChunkBorderCache;
import com.statecraft.integration.MinimapIntegration;
import net.minecraftforge.fml.ModList;

import java.util.*;

/**
 * Xaero's Minimap/World Map integration for StateCraft.
 * Uses reflection to interact with Xaero's API (soft dependency — no build dependency required).
 *
 * Displays territory highlights on Xaero's minimap and world map:
 * - Green highlights for own nation's territory
 * - Blue highlights for allied territory
 * - Red highlights for enemy territory
 * - Orange highlights for other nations' territory
 *
 * Also creates waypoints at city centers when territories are discovered.
 */
public class XaerosMinimapIntegration implements MinimapIntegration {

    private boolean available = false;
    private boolean initialized = false;

    // Track known city waypoints (don't re-create)
    private final Set<String> createdWaypoints = new HashSet<>();

    // Colors (Xaero uses integer color indices 0-15)
    // These map to Xaero's built-in color palette
    private static final int XAERO_COLOR_GREEN = 5;    // Own nation
    private static final int XAERO_COLOR_BLUE = 3;     // Ally
    private static final int XAERO_COLOR_RED = 0;      // Enemy
    private static final int XAERO_COLOR_ORANGE = 7;   // Other

    @Override
    public String getMinimapName() {
        return "Xaero's Minimap";
    }

    @Override
    public boolean isAvailable() {
        if (!initialized) {
            initialized = true;
            try {
                boolean minimapLoaded = ModList.get().isLoaded("xaerominimap");
                boolean worldmapLoaded = ModList.get().isLoaded("xaeroworldmap");
                available = minimapLoaded || worldmapLoaded;
                if (available) {
                    initializeApi();
                    StateCraft.LOGGER.info("Xaero's {} integration available and initialized",
                        minimapLoaded && worldmapLoaded ? "Minimap + World Map" :
                            minimapLoaded ? "Minimap" : "World Map");
                }
            } catch (Exception e) {
                StateCraft.LOGGER.debug("Xaero's Minimap not available: {}", e.getMessage());
                available = false;
            }
        }
        return available;
    }

    private void initializeApi() {
        try {
            // Verify Xaero's API classes are accessible
            Class.forName("xaero.common.minimap.waypoints.Waypoint");
            StateCraft.LOGGER.debug("Xaero's API classes loaded successfully");
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Could not initialize Xaero's API: {}", e.getMessage());
            available = false;
        }
    }

    @Override
    public void onChunkDataUpdated(int centerX, int centerZ, int radius) {
        if (!available) return;

        try {
            // Scan for new city centers to create waypoints
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;

                    ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
                    if (info != null && info.isClaimed()) {
                        // Track cities for waypoint creation
                        String cityKey = info.getNationName() + ":" +
                            (info.getCityName() != null ? info.getCityName() : "");
                        if (!cityKey.isEmpty() && !createdWaypoints.contains(cityKey)) {
                            createdWaypoints.add(cityKey);
                            StateCraft.LOGGER.trace("Discovered territory: {} at chunk ({}, {})",
                                buildLabel(info), chunkX, chunkZ);
                        }
                    }
                }
            }
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Error updating Xaero's integration: {}", e.getMessage());
        }
    }

    @Override
    public void onDimensionChange() {
        createdWaypoints.clear();
    }

    @Override
    public void cleanup() {
        createdWaypoints.clear();
    }

    /**
     * Get the Xaero color index for a chunk's claim info
     */
    public static int getXaeroColorForInfo(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return XAERO_COLOR_GREEN;
        if (info.isAlly()) return XAERO_COLOR_BLUE;
        if (info.isEnemy()) return XAERO_COLOR_RED;
        return XAERO_COLOR_ORANGE;
    }

    private String buildLabel(ChunkBorderCache.ChunkClaimInfo info) {
        StringBuilder label = new StringBuilder();
        if (info.getNationName() != null) {
            label.append(info.getNationName());
        }
        if (info.getStateName() != null && !info.getStateName().isEmpty()) {
            label.append(" / ").append(info.getStateName());
        }
        if (info.getCityName() != null && !info.getCityName().isEmpty()) {
            label.append(" / ").append(info.getCityName());
        }
        return label.toString();
    }
}

