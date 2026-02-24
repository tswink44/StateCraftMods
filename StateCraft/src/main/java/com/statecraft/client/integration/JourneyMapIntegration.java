package com.statecraft.client.integration;

import com.statecraft.StateCraft;
import com.statecraft.client.ChunkBorderCache;
import com.statecraft.integration.MinimapIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * JourneyMap integration for StateCraft.
 * Uses reflection to interact with the JourneyMap API (soft dependency — no build dependency required).
 *
 * Displays territory overlays on the JourneyMap minimap and fullscreen map with separate layers:
 * - Nations Layer: Shows nation boundaries (colored by relationship)
 * - States Layer: Shows state boundaries within nations
 * - Cities Layer: Shows city boundaries within states
 *
 * Colors based on relationship:
 * - Green for own nation's territory
 * - Blue for allied territory
 * - Red for enemy territory
 * - Orange for other nations' territory
 */
public class JourneyMapIntegration implements MinimapIntegration {

    private boolean available = false;
    private boolean initialized = false;
    private boolean apiInitialized = false;

    // Reflected JourneyMap API - stored objects
    private Object clientApi = null;

    // Reflected classes
    private Class<?> iClientApiClass = null;
    private Class<?> polygonOverlayClass = null;
    private Class<?> mapPolygonClass = null;
    private Class<?> shapePropertiesClass = null;
    private Class<?> textPropertiesClass = null;
    private Class<?> overlayClass = null;
    private Class<?> displayTypeEnum = null;

    // Reflected methods - API operations
    private Method showMethod = null;
    private Method removeMethod = null;

    // Reflected methods - overlay properties (cached for JourneyMap toggle support)
    private Method setOverlayGroupNameMethod = null;
    private Method setTitleMethod = null;
    private Method setLabelMethod = null;
    private Method setMinZoomMethod = null;
    private Method setMaxZoomMethod = null;

    // Reflected methods - shape properties
    private Method setStrokeWidthMethod = null;
    private Method setStrokeColorMethod = null;
    private Method setStrokeOpacityMethod = null;
    private Method setFillColorMethod = null;
    private Method setFillOpacityMethod = null;

    // Cached constructors
    private Constructor<?> blockPosConstructor = null;
    private Constructor<?> mapPolygonConstructor = null;
    private Constructor<?> shapePropsConstructor = null;
    private Constructor<?> polygonOverlayConstructor = null;


    // Track active overlays by ID for cleanup
    private final Map<String, Object> activeNationOverlays = new HashMap<>();
    private final Map<String, Object> activeStateOverlays = new HashMap<>();
    private final Map<String, Object> activeCityOverlays = new HashMap<>();

    // Layer visibility (can be toggled by player)
    private boolean showNationLayer = true;
    private boolean showStateLayer = true;
    private boolean showCityLayer = true;

    // Colors (RGB format for JourneyMap - alpha is separate)
    private static final int COLOR_OWN = 0x4CAF50;       // Green
    private static final int COLOR_ALLY = 0x2196F3;      // Blue
    private static final int COLOR_ENEMY = 0xF44336;     // Red
    private static final int COLOR_OTHER = 0xFF9800;     // Orange

    // Layer-specific colors (variations)
    private static final int COLOR_STATE_OWN = 0x66BB6A;
    private static final int COLOR_STATE_OTHER = 0xFFB74D;
    private static final int COLOR_CITY_OWN = 0x81C784;
    private static final int COLOR_CITY_OTHER = 0xFFCC80;

    // Opacity levels
    private static final int FILL_OPACITY_NATION = 40;   // ~15% for nation background
    private static final int FILL_OPACITY_STATE = 30;    // ~12% for state overlay
    private static final int FILL_OPACITY_CITY = 50;     // ~20% for city overlay (most prominent)
    private static final int STROKE_OPACITY = 200;       // ~78% for all borders
    private static final int STROKE_WIDTH_NATION = 3;
    private static final int STROKE_WIDTH_STATE = 2;
    private static final int STROKE_WIDTH_CITY = 1;

    // Mod ID for JourneyMap registration
    private static final String MOD_ID = StateCraft.MOD_ID;

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
                    available = true;
                    StateCraft.LOGGER.info("JourneyMap detected - integration will be available");
                }
            } catch (Exception e) {
                StateCraft.LOGGER.debug("JourneyMap not available: {}", e.getMessage());
                available = false;
            }
        }
        return available;
    }

    /**
     * Called when the JourneyMap API becomes available (from JourneyMapPlugin)
     */
    public void setClientApi(Object api) {
        this.clientApi = api;
        if (api != null) {
            initializeReflection();
        }
    }

    private void initializeReflection() {
        if (apiInitialized) return;

        try {
            iClientApiClass = Class.forName("journeymap.client.api.IClientAPI");
            polygonOverlayClass = Class.forName("journeymap.client.api.display.PolygonOverlay");
            mapPolygonClass = Class.forName("journeymap.client.api.model.MapPolygon");
            shapePropertiesClass = Class.forName("journeymap.client.api.model.ShapeProperties");
            textPropertiesClass = Class.forName("journeymap.client.api.model.TextProperties");
            overlayClass = Class.forName("journeymap.client.api.display.Displayable");
            displayTypeEnum = Class.forName("journeymap.client.api.display.DisplayType");

            showMethod = iClientApiClass.getMethod("show", overlayClass);
            removeMethod = iClientApiClass.getMethod("remove", overlayClass);

            // Cache overlay property methods (inherited from Displayable)
            setOverlayGroupNameMethod = polygonOverlayClass.getMethod("setOverlayGroupName", String.class);
            setTitleMethod = polygonOverlayClass.getMethod("setTitle", String.class);

            // Cache ShapeProperties methods
            setStrokeWidthMethod = shapePropertiesClass.getMethod("setStrokeWidth", float.class);
            setStrokeColorMethod = shapePropertiesClass.getMethod("setStrokeColor", int.class);
            setStrokeOpacityMethod = shapePropertiesClass.getMethod("setStrokeOpacity", float.class);
            setFillColorMethod = shapePropertiesClass.getMethod("setFillColor", int.class);
            setFillOpacityMethod = shapePropertiesClass.getMethod("setFillOpacity", float.class);

            // Cache constructors
            blockPosConstructor = Class.forName("net.minecraft.core.BlockPos")
                .getConstructor(int.class, int.class, int.class);
            mapPolygonConstructor = mapPolygonClass.getConstructor(List.class);
            shapePropsConstructor = shapePropertiesClass.getConstructor();
            polygonOverlayConstructor = polygonOverlayClass.getConstructor(
                String.class, String.class, ResourceKey.class, shapePropertiesClass, mapPolygonClass);

            apiInitialized = true;
            StateCraft.LOGGER.info("JourneyMap API reflection initialized successfully");

            // Optional methods - may not exist in all API versions
            try {
                setLabelMethod = polygonOverlayClass.getMethod("setLabel", String.class);
            } catch (NoSuchMethodException ignored) {}
            try {
                setMinZoomMethod = polygonOverlayClass.getMethod("setMinZoom", int.class);
                setMaxZoomMethod = polygonOverlayClass.getMethod("setMaxZoom", int.class);
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            StateCraft.LOGGER.error("Failed to initialize JourneyMap API reflection: {}", e.getMessage());
            available = false;
        }
    }

    @Override
    public void onChunkDataUpdated(int centerX, int centerZ, int radius) {
        if (!available || clientApi == null || !apiInitialized) return;

        try {
            // Collect chunk data grouped by nation/state/city
            Map<String, List<ChunkBorderCache.ChunkClaimInfo>> nationChunks = new LinkedHashMap<>();
            Map<String, List<ChunkBorderCache.ChunkClaimInfo>> stateChunks = new LinkedHashMap<>();
            Map<String, List<ChunkBorderCache.ChunkClaimInfo>> cityChunks = new LinkedHashMap<>();

            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;

                    ChunkBorderCache.ChunkClaimInfo info = ChunkBorderCache.getChunkInfo(chunkX, chunkZ);
                    if (info != null && info.isClaimed()) {
                        // Group by nation
                        String nationKey = info.getNationName() != null ? info.getNationName() : "Unknown";
                        nationChunks.computeIfAbsent(nationKey, k -> new ArrayList<>()).add(info);

                        // Group by state (nation:state)
                        String stateKey = nationKey + ":" + (info.getStateName() != null ? info.getStateName() : "Unknown");
                        stateChunks.computeIfAbsent(stateKey, k -> new ArrayList<>()).add(info);

                        // Group by city (nation:state:city)
                        String cityKey = stateKey + ":" + (info.getCityName() != null ? info.getCityName() : "Unknown");
                        cityChunks.computeIfAbsent(cityKey, k -> new ArrayList<>()).add(info);
                    }
                }
            }

            // Update each layer
            if (showCityLayer) {
                updateCityOverlays(cityChunks, centerX, centerZ, radius);
            }
            if (showStateLayer) {
                updateStateOverlays(stateChunks, centerX, centerZ, radius);
            }
            if (showNationLayer) {
                updateNationOverlays(nationChunks, centerX, centerZ, radius);
            }

        } catch (Exception e) {
            StateCraft.LOGGER.debug("Error updating JourneyMap overlays: {}", e.getMessage(), e);
        }
    }

    private void updateNationOverlays(Map<String, List<ChunkBorderCache.ChunkClaimInfo>> nationChunks,
                                       int centerX, int centerZ, int radius) {
        // Remove overlays in the update area
        removeOverlaysInArea(activeNationOverlays, "nation", centerX, centerZ, radius);

        // Create new overlays for each nation
        for (Map.Entry<String, List<ChunkBorderCache.ChunkClaimInfo>> entry : nationChunks.entrySet()) {
            String nationName = entry.getKey();
            List<ChunkBorderCache.ChunkClaimInfo> chunks = entry.getValue();

            if (chunks.isEmpty()) continue;

            // Use first chunk to determine relationship color
            ChunkBorderCache.ChunkClaimInfo sampleInfo = chunks.get(0);
            int color = getColorForInfo(sampleInfo);

            ChunkBorderCache.ChunkClaimInfo centroid = findCentroidChunk(chunks);

            // Create individual chunk overlays for nation layer
            for (ChunkBorderCache.ChunkClaimInfo info : chunks) {
                boolean showLabel = (info == centroid);
                createChunkOverlay(activeNationOverlays, "nation", info, color,
                    FILL_OPACITY_NATION, STROKE_WIDTH_NATION, nationName, showLabel);
            }
        }
    }

    private void updateStateOverlays(Map<String, List<ChunkBorderCache.ChunkClaimInfo>> stateChunks,
                                      int centerX, int centerZ, int radius) {
        removeOverlaysInArea(activeStateOverlays, "state", centerX, centerZ, radius);

        for (Map.Entry<String, List<ChunkBorderCache.ChunkClaimInfo>> entry : stateChunks.entrySet()) {
            List<ChunkBorderCache.ChunkClaimInfo> chunks = entry.getValue();
            if (chunks.isEmpty()) continue;

            ChunkBorderCache.ChunkClaimInfo sampleInfo = chunks.get(0);
            int color = sampleInfo.isOwn() ? COLOR_STATE_OWN : COLOR_STATE_OTHER;
            String label = sampleInfo.getStateName() != null ? sampleInfo.getStateName() : "Unknown State";

            ChunkBorderCache.ChunkClaimInfo centroid = findCentroidChunk(chunks);

            for (ChunkBorderCache.ChunkClaimInfo info : chunks) {
                boolean showLabel = (info == centroid);
                createChunkOverlay(activeStateOverlays, "state", info, color,
                    FILL_OPACITY_STATE, STROKE_WIDTH_STATE, label, showLabel);
            }
        }
    }

    private void updateCityOverlays(Map<String, List<ChunkBorderCache.ChunkClaimInfo>> cityChunks,
                                     int centerX, int centerZ, int radius) {
        removeOverlaysInArea(activeCityOverlays, "city", centerX, centerZ, radius);

        for (Map.Entry<String, List<ChunkBorderCache.ChunkClaimInfo>> entry : cityChunks.entrySet()) {
            List<ChunkBorderCache.ChunkClaimInfo> chunks = entry.getValue();
            if (chunks.isEmpty()) continue;

            ChunkBorderCache.ChunkClaimInfo sampleInfo = chunks.get(0);
            int color = sampleInfo.isOwn() ? COLOR_CITY_OWN : COLOR_CITY_OTHER;
            String label = sampleInfo.getCityName() != null ? sampleInfo.getCityName() : "Unknown City";

            ChunkBorderCache.ChunkClaimInfo centroid = findCentroidChunk(chunks);

            for (ChunkBorderCache.ChunkClaimInfo info : chunks) {
                boolean showLabel = (info == centroid);
                createChunkOverlay(activeCityOverlays, "city", info, color,
                    FILL_OPACITY_CITY, STROKE_WIDTH_CITY, label, showLabel);
            }
        }
    }

    private void createChunkOverlay(Map<String, Object> overlayMap, String layerType,
                                     ChunkBorderCache.ChunkClaimInfo info, int color,
                                     int fillOpacity, int strokeWidth, String label,
                                     boolean showLabel) {
        if (clientApi == null || !apiInitialized) return;

        try {
            int chunkX = info.getChunkX();
            int chunkZ = info.getChunkZ();
            String overlayId = MOD_ID + "_" + layerType + "_" + chunkX + "_" + chunkZ;

            // Calculate block coordinates for chunk corners
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            int maxX = minX + 16;
            int maxZ = minZ + 16;

            // Get current dimension
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            ResourceKey<Level> dimension = mc.level.dimension();

            // Create polygon points (chunk corners)
            Object point1 = blockPosConstructor.newInstance(minX, 64, minZ);
            Object point2 = blockPosConstructor.newInstance(maxX, 64, minZ);
            Object point3 = blockPosConstructor.newInstance(maxX, 64, maxZ);
            Object point4 = blockPosConstructor.newInstance(minX, 64, maxZ);

            // Create MapPolygon
            Object mapPolygon = mapPolygonConstructor.newInstance(
                Arrays.asList(point1, point2, point3, point4));

            // Create and configure ShapeProperties
            Object shapeProps = shapePropsConstructor.newInstance();
            setStrokeWidthMethod.invoke(shapeProps, (float) strokeWidth);
            setStrokeColorMethod.invoke(shapeProps, color);
            setStrokeOpacityMethod.invoke(shapeProps, STROKE_OPACITY / 255.0f);
            setFillColorMethod.invoke(shapeProps, color);
            setFillOpacityMethod.invoke(shapeProps, fillOpacity / 255.0f);

            // Create PolygonOverlay
            Object overlay = polygonOverlayConstructor.newInstance(
                MOD_ID, overlayId, dimension, shapeProps, mapPolygon
            );

            // Assign to a toggleable overlay group in JourneyMap's UI
            String groupName = getLayerGroupName(layerType);
            setOverlayGroupNameMethod.invoke(overlay, groupName);
            setTitleMethod.invoke(overlay, label);
            if (setLabelMethod != null && showLabel) {
                setLabelMethod.invoke(overlay, label);
            }

            // Set zoom level visibility so layers don't all overlap at every zoom
            if (setMinZoomMethod != null) {
                switch (layerType) {
                    case "state" -> setMinZoomMethod.invoke(overlay, 2);
                    case "city" -> setMinZoomMethod.invoke(overlay, 4);
                }
            }

            // Show the overlay
            showMethod.invoke(clientApi, overlay);

            // Store for later cleanup
            overlayMap.put(overlayId, overlay);

        } catch (Exception e) {
            StateCraft.LOGGER.trace("Error creating JourneyMap {} overlay: {}", layerType, e.getMessage());
        }
    }

    private String getLayerGroupName(String layerType) {
        return switch (layerType) {
            case "nation" -> "StateCraft Nations";
            case "state" -> "StateCraft States";
            case "city" -> "StateCraft Cities";
            default -> "StateCraft " + layerType;
        };
    }

    private void removeOverlaysInArea(Map<String, Object> overlayMap, String layerType,
                                       int centerX, int centerZ, int radius) {
        if (clientApi == null) return;

        Iterator<Map.Entry<String, Object>> iter = overlayMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Object> entry = iter.next();
            String id = entry.getKey();

            // Parse chunk coords from overlay ID: "modid_layertype_x_z"
            String[] parts = id.split("_");
            if (parts.length >= 4) {
                try {
                    int x = Integer.parseInt(parts[parts.length - 2]);
                    int z = Integer.parseInt(parts[parts.length - 1]);
                    if (Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius) {
                        try {
                            removeMethod.invoke(clientApi, entry.getValue());
                        } catch (Exception e) {
                            StateCraft.LOGGER.trace("Error removing overlay: {}", e.getMessage());
                        }
                        iter.remove();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @Override
    public void onDimensionChange() {
        cleanup();
    }

    @Override
    public void cleanup() {
        // Remove all overlays
        removeAllOverlays(activeNationOverlays);
        removeAllOverlays(activeStateOverlays);
        removeAllOverlays(activeCityOverlays);
    }

    private void removeAllOverlays(Map<String, Object> overlayMap) {
        if (clientApi == null) return;

        for (Object overlay : overlayMap.values()) {
            try {
                removeMethod.invoke(clientApi, overlay);
            } catch (Exception e) {
                StateCraft.LOGGER.trace("Error removing overlay: {}", e.getMessage());
            }
        }
        overlayMap.clear();
    }

    /**
     * Finds the chunk closest to the centroid of the group, used to place a single label.
     */
    private ChunkBorderCache.ChunkClaimInfo findCentroidChunk(List<ChunkBorderCache.ChunkClaimInfo> chunks) {
        if (chunks.size() == 1) return chunks.get(0);

        double avgX = 0, avgZ = 0;
        for (ChunkBorderCache.ChunkClaimInfo c : chunks) {
            avgX += c.getChunkX();
            avgZ += c.getChunkZ();
        }
        avgX /= chunks.size();
        avgZ /= chunks.size();

        ChunkBorderCache.ChunkClaimInfo closest = chunks.get(0);
        double minDist = Double.MAX_VALUE;
        for (ChunkBorderCache.ChunkClaimInfo c : chunks) {
            double dist = (c.getChunkX() - avgX) * (c.getChunkX() - avgX)
                        + (c.getChunkZ() - avgZ) * (c.getChunkZ() - avgZ);
            if (dist < minDist) {
                minDist = dist;
                closest = c;
            }
        }
        return closest;
    }

    private int getColorForInfo(ChunkBorderCache.ChunkClaimInfo info) {
        if (info.isOwn()) return COLOR_OWN;
        if (info.isAlly()) return COLOR_ALLY;
        if (info.isEnemy()) return COLOR_ENEMY;
        return COLOR_OTHER;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // Layer visibility controls
    public void setNationLayerVisible(boolean visible) {
        this.showNationLayer = visible;
        if (!visible) removeAllOverlays(activeNationOverlays);
    }

    public void setStateLayerVisible(boolean visible) {
        this.showStateLayer = visible;
        if (!visible) removeAllOverlays(activeStateOverlays);
    }

    public void setCityLayerVisible(boolean visible) {
        this.showCityLayer = visible;
        if (!visible) removeAllOverlays(activeCityOverlays);
    }

    public boolean isNationLayerVisible() { return showNationLayer; }
    public boolean isStateLayerVisible() { return showStateLayer; }
    public boolean isCityLayerVisible() { return showCityLayer; }
}

