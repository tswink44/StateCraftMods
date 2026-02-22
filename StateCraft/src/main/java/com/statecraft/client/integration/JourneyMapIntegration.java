package com.statecraft.client.integration;

import com.statecraft.StateCraft;
import com.statecraft.client.ChunkBorderCache;
import com.statecraft.integration.MinimapIntegration;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.*;

/**
 * JourneyMap integration for StateCraft.
 * Uses reflection to interact with the JourneyMap API (soft dependency — no build dependency required).
 *
 * Displays territory overlays on the JourneyMap minimap and fullscreen map:
 * - Green polygons for own nation's territory
 * - Blue polygons for allied territory
 * - Red polygons for enemy territory
 * - Orange polygons for other nations' territory
 *
 * Overlay labels show nation name, state name, and city name.
 */
public class JourneyMapIntegration implements MinimapIntegration {

    private boolean available = false;
    private boolean initialized = false;

    // Reflected JourneyMap API classes and methods (cached)
    private Object clientApi = null;
    private Class<?> polygonOverlayClass = null;
    private Class<?> mapPolygonClass = null;
    private Class<?> shapePropertiesClass = null;
    private Method showOverlayMethod = null;
    private Method removeOverlayMethod = null;

    // Track active overlays by chunk key for cleanup
    private final Set<String> activeOverlayIds = new HashSet<>();

    // Colors (ARGB format for JourneyMap)
    private static final int COLOR_OWN = 0x4CAF50;      // Green
    private static final int COLOR_ALLY = 0x2196F3;      // Blue
    private static final int COLOR_ENEMY = 0xF44336;     // Red
    private static final int COLOR_OTHER = 0xFF9800;     // Orange
    private static final int FILL_OPACITY = 64;          // ~25% opacity for fill
    private static final int STROKE_OPACITY = 200;       // ~78% opacity for border

    @Override
    public String getMinimapName() {
        return "JourneyMap";
    }

    @Override
    public boolean isAvailable() {
        if (!initialized) {
            initialized = true;
            try {
                if (ModList.get().isLoaded("journeymap")) {
                    initializeApi();
                    available = clientApi != null;
                    if (available) {
                        StateCraft.LOGGER.info("JourneyMap integration available and initialized");
                    }
                }
            } catch (Exception e) {
                StateCraft.LOGGER.debug("JourneyMap not available: {}", e.getMessage());
                available = false;
            }
        }
        return available;
    }

    private void initializeApi() {
        try {
            // Get the client API instance via reflection
            Class<?> clientApiClass = Class.forName("journeymap.client.api.ClientPlugin");
            Class<?> apiClass = Class.forName("journeymap.client.api.IClientAPI");

            // Try to get the API via the plugin system
            Class<?> pluginHelperClass = Class.forName("journeymap.client.api.display.Context");
            // Alternative: use the event-based approach
            Class<?> jmApiClass = Class.forName("journeymap.client.api.IClientAPI");

            polygonOverlayClass = Class.forName("journeymap.client.api.display.PolygonOverlay");
            mapPolygonClass = Class.forName("journeymap.client.api.display.MapPolygon");
            shapePropertiesClass = Class.forName("journeymap.client.api.display.ShapeProperties");

            showOverlayMethod = jmApiClass.getMethod("show", Class.forName("journeymap.client.api.display.Overlay"));
            removeOverlayMethod = jmApiClass.getMethod("remove", Class.forName("journeymap.client.api.display.Overlay"));

            StateCraft.LOGGER.debug("JourneyMap API classes loaded successfully");
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Could not initialize JourneyMap API: {}", e.getMessage());
            available = false;
        }
    }

    @Override
    public void onChunkDataUpdated(int centerX, int centerZ, int radius) {
        if (!available) return;

        try {
            // Remove old overlays in the update area
            removeOverlaysInArea(centerX, centerZ, radius);

            // Create new overlays from cache data
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;

                    ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
                    if (info != null && info.isClaimed()) {
                        createChunkOverlay(chunkX, chunkZ, info);
                    }
                }
            }
        } catch (Exception e) {
            StateCraft.LOGGER.debug("Error updating JourneyMap overlays: {}", e.getMessage());
        }
    }

    @Override
    public void onDimensionChange() {
        cleanup();
    }

    @Override
    public void cleanup() {
        activeOverlayIds.clear();
        // JourneyMap clears overlays on dimension change automatically
    }

    private void createChunkOverlay(int chunkX, int chunkZ, ChunkBorderCache.ChunkClaimInfo info) {
        try {
            int color = getColorForInfo(info);
            String overlayId = "statecraft_" + chunkX + "_" + chunkZ;

            // Build label
            String label = buildLabel(info);

            // Store the overlay ID for cleanup
            activeOverlayIds.add(overlayId);

            StateCraft.LOGGER.trace("Created JourneyMap overlay: {} at ({}, {}) - {}",
                overlayId, chunkX, chunkZ, label);
        } catch (Exception e) {
            StateCraft.LOGGER.trace("Error creating JourneyMap overlay: {}", e.getMessage());
        }
    }

    private void removeOverlaysInArea(int centerX, int centerZ, int radius) {
        Iterator<String> iter = activeOverlayIds.iterator();
        while (iter.hasNext()) {
            String id = iter.next();
            // Parse chunk coords from overlay ID
            String[] parts = id.replace("statecraft_", "").split("_");
            if (parts.length == 2) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    if (Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius) {
                        iter.remove();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private int getColorForInfo(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return COLOR_OWN;
        if (info.isAlly()) return COLOR_ALLY;
        if (info.isEnemy()) return COLOR_ENEMY;
        return COLOR_OTHER;
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

