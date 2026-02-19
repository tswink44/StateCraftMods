package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestChunkMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;
import java.util.function.Consumer;

/**
 * Chunk selection map for government contracts
 * Allows selecting multiple chunks for a contract's project area
 * Based on ChunkMapScreen but with multi-selection instead of claim actions
 */
public class ContractChunkSelectScreen extends StateCraftScreen {

    private static final int MAP_SIZE = 17; // 17x17 chunk grid
    private static final int MIN_CELL_SIZE = 12;
    private static final int MAX_CELL_SIZE = 20;
    private static final int TERRAIN_RESOLUTION = 8;

    private int cellSize = 14;

    // Chunk data from server
    private Map<Long, ChunkMapScreen.ChunkData> chunkMap = new HashMap<>();
    private boolean dataLoaded = false;

    // Terrain cache
    private Map<Long, int[]> terrainCache = new HashMap<>();
    private boolean terrainLoaded = false;
    private boolean terrainMode = true; // Default to terrain mode for better visibility

    // Player's current chunk position
    private int playerChunkX = 0;
    private int playerChunkZ = 0;

    // Selection
    private final Set<ChunkPos> selectedChunks;
    private final String dimension;

    // Callback when done selecting
    private final Consumer<Set<ChunkPos>> onConfirm;
    private final Runnable onCancel;

    // Drag selection
    private boolean isDragging = false;
    private int dragStartChunkX, dragStartChunkZ;
    private int dragEndChunkX, dragEndChunkZ;
    private boolean addingSelection = true; // true = add, false = remove

    // Double-click detection
    private long lastClickTime = 0;
    private int lastClickChunkX = Integer.MIN_VALUE;
    private int lastClickChunkZ = Integer.MIN_VALUE;
    private static final long DOUBLE_CLICK_TIME_MS = 400;

    // Buttons
    private Button toggleModeButton;
    private Button clearButton;
    private Button select3x3Button;
    private Button select5x5Button;

    // UI state
    private String playerNation = null;

    public ContractChunkSelectScreen(Set<ChunkPos> initialSelection, String dimension,
                                      Consumer<Set<ChunkPos>> onConfirm, Runnable onCancel) {
        super(Component.literal("Select Contract Chunks"));
        this.selectedChunks = new HashSet<>(initialSelection);
        this.dimension = dimension;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.guiWidth = 400;
        this.guiHeight = 400;
    }

    @Override
    protected void init() {
        // Calculate cell size based on available screen space
        int availableWidth = this.width - 80 - 100;
        int availableHeight = this.height - 100;

        int maxCellsWidth = availableWidth / MAP_SIZE;
        int maxCellsHeight = availableHeight / MAP_SIZE;
        cellSize = Math.min(maxCellsWidth, maxCellsHeight);
        cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, cellSize));

        this.guiWidth = MAP_SIZE * cellSize + 100;
        this.guiHeight = MAP_SIZE * cellSize + 90;

        super.init();

        // Initialize player position
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.playerChunkX = mc.player.chunkPosition().x;
            this.playerChunkZ = mc.player.chunkPosition().z;
        }

        // Request chunk map data
        NetworkHandler.sendToServer(new RequestChunkMapPacket(MAP_SIZE));

        int buttonX = guiLeft + 15 + MAP_SIZE * cellSize + 10;
        int buttonY = guiTop + 35;
        int buttonWidth = 70;

        // Toggle mode button
        toggleModeButton = this.addRenderableWidget(createButton(
            buttonX, buttonY, buttonWidth, 18,
            Component.literal(terrainMode ? "Grid" : "Terrain"),
            btn -> toggleMapMode()
        ));
        buttonY += 24;

        // Selection helper buttons
        select3x3Button = this.addRenderableWidget(createButton(
            buttonX, buttonY, buttonWidth, 18,
            Component.literal("3x3 Area"),
            btn -> selectArea(3)
        ));
        buttonY += 22;

        select5x5Button = this.addRenderableWidget(createButton(
            buttonX, buttonY, buttonWidth, 18,
            Component.literal("5x5 Area"),
            btn -> selectArea(5)
        ));
        buttonY += 22;

        clearButton = this.addRenderableWidget(createButton(
            buttonX, buttonY, buttonWidth, 18,
            Component.literal("§cClear All"),
            btn -> clearSelection()
        ));

        // Bottom buttons
        int bottomY = guiTop + guiHeight - 28;

        this.addRenderableWidget(createButton(
            guiLeft + 15, bottomY, 70, 20,
            Component.literal("Cancel"),
            btn -> cancel()
        ));

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 85, bottomY, 70, 20,
            Component.literal("§aConfirm"),
            btn -> confirm()
        ));

        // Load terrain
        loadTerrainData();
    }

    private void toggleMapMode() {
        terrainMode = !terrainMode;
        toggleModeButton.setMessage(Component.literal(terrainMode ? "Grid" : "Terrain"));
        if (terrainMode && !terrainLoaded) {
            loadTerrainData();
        }
    }

    private void selectArea(int size) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                // Only add chunks that belong to the player's nation
                if (isChunkInPlayerNation(chunkX, chunkZ)) {
                    selectedChunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
    }

    /**
     * Check if a chunk belongs to the player's nation
     */
    private boolean isChunkInPlayerNation(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        ChunkMapScreen.ChunkData data = chunkMap.get(key);
        return data != null && data.isPlayerNation;
    }

    private void clearSelection() {
        selectedChunks.clear();
    }

    private void confirm() {
        if (onConfirm != null) {
            onConfirm.accept(new HashSet<>(selectedChunks));
        }
    }

    private void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void loadTerrainData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Level level = mc.level;
        int halfSize = MAP_SIZE / 2;

        terrainCache.clear();

        for (int dz = -halfSize; dz <= halfSize; dz++) {
            for (int dx = -halfSize; dx <= halfSize; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                long key = chunkKey(chunkX, chunkZ);

                int[] colors = new int[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION];
                int blockStep = 16 / TERRAIN_RESOLUTION;

                for (int bz = 0; bz < TERRAIN_RESOLUTION; bz++) {
                    for (int bx = 0; bx < TERRAIN_RESOLUTION; bx++) {
                        int worldX = chunkX * 16 + bx * blockStep + blockStep / 2;
                        int worldZ = chunkZ * 16 + bz * blockStep + blockStep / 2;

                        int color = getTerrainColor(level, worldX, worldZ);
                        colors[bz * TERRAIN_RESOLUTION + bx] = color;
                    }
                }

                terrainCache.put(key, colors);
            }
        }

        terrainLoaded = true;
    }

    private int getTerrainColor(Level level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        pos.setY(y);

        while (y > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            y--;
            pos.setY(y);
        }

        BlockState state = level.getBlockState(pos);
        return getBlockColor(state);
    }

    private int getBlockColor(BlockState state) {
        var block = state.getBlock();
        String blockId = state.getBlock().getDescriptionId();

        // Water
        if (block == Blocks.WATER) return 0xFF3B6FCF;
        // Lava
        if (block == Blocks.LAVA) return 0xFFFF6600;

        // Grass
        if (block == Blocks.GRASS_BLOCK || blockId.contains("grass")) return 0xFF4A7A35;
        // Leaves
        if (blockId.contains("leaves")) return 0xFF3A6025;
        // Wood
        if (blockId.contains("log") || blockId.contains("wood")) return 0xFF5B4028;

        // Sand
        if (block == Blocks.SAND || block == Blocks.SANDSTONE) return 0xFFD4C483;

        // Stone
        if (block == Blocks.STONE || block == Blocks.COBBLESTONE) return 0xFF707070;

        // Dirt
        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT) return 0xFF7A5B3B;

        // Snow
        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK) return 0xFFEEEEEE;

        // Water default
        if (blockId.contains("water")) return 0xFF3B6FCF;

        return 0xFF707070;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int mapX = guiLeft + 15;
        int mapY = guiTop + 35;

        // Map background
        graphics.fill(mapX - 1, mapY - 1,
            mapX + MAP_SIZE * cellSize + 1,
            mapY + MAP_SIZE * cellSize + 1,
            0xFF333333);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...",
                mapX + MAP_SIZE * cellSize / 2,
                mapY + MAP_SIZE * cellSize / 2 - 4,
                COLOR_TEXT);
        } else {
            // Render chunks
            int halfSize = MAP_SIZE / 2;
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                for (int dx = -halfSize; dx <= halfSize; dx++) {
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;

                    int cellX = mapX + (dx + halfSize) * cellSize;
                    int cellY = mapY + (dz + halfSize) * cellSize;

                    long key = chunkKey(chunkX, chunkZ);
                    ChunkMapScreen.ChunkData data = chunkMap.get(key);

                    if (terrainMode && terrainLoaded) {
                        renderTerrainChunk(graphics, cellX, cellY, key, data);
                    } else {
                        renderGridChunk(graphics, cellX, cellY, data);
                    }

                    // Selected chunk highlight
                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                    if (selectedChunks.contains(chunkPos)) {
                        drawSelectedBorder(graphics, cellX, cellY);
                    }

                    // Player position marker
                    if (dx == 0 && dz == 0) {
                        graphics.drawCenteredString(this.font, "§l@",
                            cellX + cellSize / 2, cellY + cellSize / 2 - 4, 0xFFFFFFFF);
                    }
                }
            }
        }

        // Info panel on right side
        renderInfoPanel(graphics);

        // Legend at bottom
        renderLegend(graphics);

        // Instructions
        int instructY = guiTop + guiHeight - 50;
        graphics.drawString(this.font, "§7Double-click: Select/Deselect chunk",
            guiLeft + 15, instructY, 0xFF888888);
        graphics.drawString(this.font, "§cOnly your nation's chunks can be selected",
            guiLeft + 15, instructY + 10, 0xFF888888);
    }

    private void renderGridChunk(GuiGraphics graphics, int cellX, int cellY, ChunkMapScreen.ChunkData data) {
        int color = getChunkColor(data);
        graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, color);
    }

    private void renderTerrainChunk(GuiGraphics graphics, int cellX, int cellY, long key, ChunkMapScreen.ChunkData data) {
        int[] colors = terrainCache.get(key);

        if (colors != null) {
            for (int tz = 0; tz < TERRAIN_RESOLUTION; tz++) {
                for (int tx = 0; tx < TERRAIN_RESOLUTION; tx++) {
                    int color = colors[tz * TERRAIN_RESOLUTION + tx];
                    int px1 = cellX + (tx * cellSize) / TERRAIN_RESOLUTION;
                    int py1 = cellY + (tz * cellSize) / TERRAIN_RESOLUTION;
                    int px2 = cellX + ((tx + 1) * cellSize) / TERRAIN_RESOLUTION;
                    int py2 = cellY + ((tz + 1) * cellSize) / TERRAIN_RESOLUTION;
                    graphics.fill(px1, py1, px2, py2, color);
                }
            }
        } else {
            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, 0xFF555555);
        }

        // Claim overlay
        if (data != null) {
            int overlayColor = getClaimOverlayColor(data);
            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, overlayColor);
        }
    }

    private int getClaimOverlayColor(ChunkMapScreen.ChunkData data) {
        if (data.isPlayerNation) {
            return 0x664CAF50; // Green
        } else if (data.isAlly) {
            return 0x662196F3; // Blue
        } else if (data.isEnemy) {
            return 0x66F44336; // Red
        } else {
            return 0x66FF9800; // Orange
        }
    }

    private void drawSelectedBorder(GuiGraphics graphics, int cellX, int cellY) {
        // Thicker green border for selected chunks
        int borderColor = 0xFF00FF00;
        int thickness = 2;

        // Top
        graphics.fill(cellX - thickness, cellY - thickness, cellX + cellSize + thickness, cellY, borderColor);
        // Bottom
        graphics.fill(cellX - thickness, cellY + cellSize, cellX + cellSize + thickness, cellY + cellSize + thickness, borderColor);
        // Left
        graphics.fill(cellX - thickness, cellY, cellX, cellY + cellSize, borderColor);
        // Right
        graphics.fill(cellX + cellSize, cellY, cellX + cellSize + thickness, cellY + cellSize, borderColor);

        // Semi-transparent fill
        graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, 0x4400FF00);
    }

    private int getChunkColor(ChunkMapScreen.ChunkData data) {
        if (data == null) {
            return 0xFF555555; // Wilderness
        }
        if (data.isPlayerNation) {
            return 0xFF4CAF50; // Green
        } else if (data.isAlly) {
            return 0xFF2196F3; // Blue
        } else if (data.isEnemy) {
            return 0xFFF44336; // Red
        }
        return 0xFFFF9800; // Orange
    }

    private void renderInfoPanel(GuiGraphics graphics) {
        int infoX = guiLeft + 15 + MAP_SIZE * cellSize + 10;
        int infoY = guiTop + 145;

        graphics.drawString(this.font, "§6Selection", infoX, infoY, COLOR_PRIMARY);
        infoY += 14;

        graphics.drawString(this.font, "§7Chunks: §f" + selectedChunks.size(), infoX, infoY, COLOR_TEXT);
        infoY += 12;

        // Estimate area
        int blocks = selectedChunks.size() * 16 * 16;
        graphics.drawString(this.font, "§7Area: §f" + blocks + " blocks", infoX, infoY, COLOR_TEXT);
        infoY += 16;

        // Position
        graphics.drawString(this.font, "§7Center: §f" + playerChunkX + ", " + playerChunkZ, infoX, infoY, COLOR_TEXT);
    }

    private void renderLegend(GuiGraphics graphics) {
        int legendX = guiLeft + 15;
        int legendY = guiTop + guiHeight - 80;

        graphics.drawString(this.font, "§7Legend:", legendX, legendY, COLOR_TEXT);
        legendY += 10;

        // Your nation
        graphics.fill(legendX, legendY + 2, legendX + 8, legendY + 10, 0xFF4CAF50);
        graphics.drawString(this.font, "§aYours", legendX + 12, legendY, COLOR_TEXT);

        // Selected
        graphics.fill(legendX + 55, legendY + 2, legendX + 63, legendY + 10, 0xFF00FF00);
        graphics.drawString(this.font, "§aSelected", legendX + 67, legendY, COLOR_TEXT);

        // Wilderness
        graphics.fill(legendX + 125, legendY + 2, legendX + 133, legendY + 10, 0xFF555555);
        graphics.drawString(this.font, "§8Wild", legendX + 137, legendY, COLOR_TEXT);
    }

    private long chunkKey(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    private boolean isInDragArea(int chunkX, int chunkZ) {
        int minX = Math.min(dragStartChunkX, dragEndChunkX);
        int maxX = Math.max(dragStartChunkX, dragEndChunkX);
        int minZ = Math.min(dragStartChunkZ, dragEndChunkZ);
        int maxZ = Math.max(dragStartChunkZ, dragEndChunkZ);

        return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int mapX = guiLeft + 15;
        int mapY = guiTop + 35;
        int mapWidth = MAP_SIZE * cellSize;
        int mapHeight = MAP_SIZE * cellSize;

        if (mouseX >= mapX && mouseX < mapX + mapWidth &&
            mouseY >= mapY && mouseY < mapY + mapHeight) {

            int halfSize = MAP_SIZE / 2;
            int clickedCellX = (int) ((mouseX - mapX) / cellSize) - halfSize;
            int clickedCellZ = (int) ((mouseY - mapY) / cellSize) - halfSize;

            int clickedChunkX = playerChunkX + clickedCellX;
            int clickedChunkZ = playerChunkZ + clickedCellZ;

            ChunkPos clickedPos = new ChunkPos(clickedChunkX, clickedChunkZ);
            long currentTime = System.currentTimeMillis();

            // Check if this is a double-click on the same chunk
            boolean isDoubleClick = (currentTime - lastClickTime < DOUBLE_CLICK_TIME_MS) &&
                                    (clickedChunkX == lastClickChunkX) &&
                                    (clickedChunkZ == lastClickChunkZ);

            // Update last click tracking
            lastClickTime = currentTime;
            lastClickChunkX = clickedChunkX;
            lastClickChunkZ = clickedChunkZ;

            // Only process on double-click
            if (isDoubleClick) {
                if (selectedChunks.contains(clickedPos)) {
                    // Double-click on selected chunk: deselect it
                    selectedChunks.remove(clickedPos);
                } else {
                    // Double-click on unselected chunk: select if in player's nation
                    if (isChunkInPlayerNation(clickedChunkX, clickedChunkZ)) {
                        selectedChunks.add(clickedPos);
                    }
                }
                // Reset double-click tracking to prevent triple-click issues
                lastClickTime = 0;
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Drag selection disabled - use double-click to select, single-click to deselect
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Drag selection disabled - use double-click to select, single-click to deselect
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        cancel();
    }

    // Called by network handler when map data is received
    public void updateMapData(int playerX, int playerZ, String playerNation, Map<Long, ChunkMapScreen.ChunkData> chunks) {
        this.playerChunkX = playerX;
        this.playerChunkZ = playerZ;
        this.playerNation = playerNation;
        this.chunkMap = chunks;
        this.dataLoaded = true;

        // Reload terrain for correct position
        this.terrainLoaded = false;
        loadTerrainData();
    }
}

