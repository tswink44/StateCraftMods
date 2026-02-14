package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.ChunkActionPacket;
import com.statecraft.network.packets.RequestChunkMapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

/**
 * Visual chunk map showing claims in the surrounding area
 * Supports both grid mode (solid colors) and terrain mode (top-down terrain view)
 */
public class ChunkMapScreen extends StateCraftScreen {

    private static final int MAP_SIZE = 17; // 17x17 chunk grid (8 chunks in each direction)
    private static final int MIN_CELL_SIZE = 12; // Minimum cell size
    private static final int MAX_CELL_SIZE = 24; // Maximum cell size
    private static final int TERRAIN_RESOLUTION = 8; // Sample 8x8 points per chunk for better building clarity

    // Dynamically calculated cell size based on screen
    private int cellSize = 16;

    // Chunk ownership data from server
    private Map<Long, ChunkData> chunkMap = new HashMap<>();
    private boolean dataLoaded = false;

    // Terrain color cache: chunkKey -> array of colors (16x16 per chunk, but we sample every 4th block)
    private Map<Long, int[]> terrainCache = new HashMap<>();
    private boolean terrainLoaded = false;

    // Display mode
    private boolean terrainMode = false;

    // Player's current chunk position
    private int playerChunkX = 0;
    private int playerChunkZ = 0;

    // Selected chunk for actions
    private int selectedChunkX = 0;
    private int selectedChunkZ = 0;
    private boolean hasSelection = false;

    // Pending action tracking for visual feedback
    private long pendingActionChunkKey = -1;
    private long pendingActionTime = 0;

    // Current player's nation ID (for coloring)
    private String playerNation = null;

    // Auto-claim state
    private boolean autoClaimEnabled = false;
    private boolean canUseAutoClaim = false;
    private String autoClaimCityName = null;

    // Buttons
    private Button claimButton;
    private Button unclaimButton;
    private Button infoButton;
    private Button toggleModeButton;

    public ChunkMapScreen() {
        super(Component.literal("Chunk Map"));
        // Initial size - will be recalculated in init() based on screen size
        this.guiWidth = 400;
        this.guiHeight = 400;
    }

    @Override
    protected void init() {
        // Calculate cell size based on available screen space
        // Leave margins of 40 pixels on each side and 80 pixels for buttons on right
        int availableWidth = this.width - 80 - 80; // 80 for margins, 80 for buttons
        int availableHeight = this.height - 100; // 100 for top/bottom margins

        // Calculate the cell size that fits
        int maxCellsWidth = availableWidth / MAP_SIZE;
        int maxCellsHeight = availableHeight / MAP_SIZE;
        cellSize = Math.min(maxCellsWidth, maxCellsHeight);

        // Clamp to min/max
        cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, cellSize));

        // Now calculate GUI dimensions based on cell size
        this.guiWidth = MAP_SIZE * cellSize + 80; // 80 for buttons on right
        this.guiHeight = MAP_SIZE * cellSize + 80; // 80 for top/bottom content

        super.init();

        // Initialize player chunk position from current player location
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.playerChunkX = mc.player.chunkPosition().x;
            this.playerChunkZ = mc.player.chunkPosition().z;
        }

        // Request map data from server
        NetworkHandler.sendToServer(new RequestChunkMapPacket(MAP_SIZE));

        int mapRight = guiLeft + 15 + MAP_SIZE * cellSize + 10;
        int buttonY = guiTop + 35;

        // Toggle terrain/grid mode button
        toggleModeButton = this.addRenderableWidget(createButton(
            mapRight, buttonY, 50, 18,
            Component.literal("Terrain"),
            btn -> toggleMapMode()
        ));

        // Claim button
        claimButton = this.addRenderableWidget(createButton(
            mapRight, buttonY + 22, 50, 18,
            Component.literal("Claim"),
            btn -> claimSelectedChunk()
        ));
        claimButton.active = false;

        // Unclaim button
        unclaimButton = this.addRenderableWidget(createButton(
            mapRight, buttonY + 44, 50, 18,
            Component.literal("Unclaim"),
            btn -> unclaimSelectedChunk()
        ));
        unclaimButton.active = false;

        // Info button
        infoButton = this.addRenderableWidget(createButton(
            mapRight, buttonY + 66, 50, 18,
            Component.literal("Info"),
            btn -> showChunkInfo()
        ));
        infoButton.active = false;

        // Close button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Close"),
            btn -> goBack()
        ));

        // Load terrain data in background
        loadTerrainData();
    }

    private void toggleMapMode() {
        terrainMode = !terrainMode;
        if (terrainMode) {
            toggleModeButton.setMessage(Component.literal("Grid"));
            if (!terrainLoaded) {
                loadTerrainData();
            }
        } else {
            toggleModeButton.setMessage(Component.literal("Terrain"));
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

                // Sample terrain colors for this chunk
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
        // Get the top block at this position
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);

        // Try to get height from heightmap, fall back to searching
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        pos.setY(y);

        // Go down to find first non-air block
        while (y > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            y--;
            pos.setY(y);
        }

        BlockState state = level.getBlockState(pos);

        // Return color based on block type
        return getBlockColor(state);
    }

    private int getBlockColor(BlockState state) {
        // Map common blocks to colors - improved for building clarity
        var block = state.getBlock();
        String blockId = state.getBlock().getDescriptionId();

        // Water
        if (block == Blocks.WATER) return 0xFF3B6FCF;

        // Lava
        if (block == Blocks.LAVA) return 0xFFFF6600;

        // === BUILDING MATERIALS - Bright/distinct colors for clarity ===

        // Stone bricks and variants (common building material) - Light gray with blue tint
        if (blockId.contains("stone_brick") || blockId.contains("stonebrick")) {
            return 0xFFB0B8C0;
        }

        // Regular bricks - Distinct brick red
        if (block == Blocks.BRICKS || blockId.contains("brick")) {
            return 0xFFB54A32;
        }

        // Concrete and concrete powder - Bright colors by type
        if (blockId.contains("concrete")) {
            if (blockId.contains("white")) return 0xFFE8E8E8;
            if (blockId.contains("black")) return 0xFF252525;
            if (blockId.contains("gray") && !blockId.contains("light")) return 0xFF4B4B4B;
            if (blockId.contains("light_gray")) return 0xFF9B9B9B;
            if (blockId.contains("red")) return 0xFFA02722;
            if (blockId.contains("orange")) return 0xFFE06101;
            if (blockId.contains("yellow")) return 0xFFF9C628;
            if (blockId.contains("lime")) return 0xFF70B919;
            if (blockId.contains("green")) return 0xFF5D7C15;
            if (blockId.contains("cyan")) return 0xFF157788;
            if (blockId.contains("light_blue")) return 0xFF3AB3DA;
            if (blockId.contains("blue")) return 0xFF3C44AA;
            if (blockId.contains("purple")) return 0xFF8932B8;
            if (blockId.contains("magenta")) return 0xFFC74EBD;
            if (blockId.contains("pink")) return 0xFFF38BAA;
            if (blockId.contains("brown")) return 0xFF835432;
            return 0xFFC0C0C0; // Default concrete
        }

        // Wool and carpet - Building/interior materials
        if (blockId.contains("wool") || blockId.contains("carpet")) {
            if (blockId.contains("white")) return 0xFFE9ECEC;
            if (blockId.contains("black")) return 0xFF1D1D21;
            return 0xFFD0D0D0; // Default wool
        }

        // Glass - Cyan tint to show windows clearly
        if (blockId.contains("glass")) {
            return 0xFF88D4E5;
        }

        // Planks and wooden building materials
        if (blockId.contains("plank")) {
            if (blockId.contains("oak")) return 0xFFB8945F;
            if (blockId.contains("spruce")) return 0xFF73533B;
            if (blockId.contains("birch")) return 0xFFD5C98D;
            if (blockId.contains("jungle")) return 0xFFB58857;
            if (blockId.contains("acacia")) return 0xFFA85D3D;
            if (blockId.contains("dark_oak")) return 0xFF4F3218;
            if (blockId.contains("crimson")) return 0xFF7E3A56;
            if (blockId.contains("warped")) return 0xFF2B6963;
            return 0xFFAA8855; // Default planks
        }

        // Polished stone variants - Smooth building materials
        if (blockId.contains("polished")) {
            return 0xFFA0A0A0;
        }

        // Quartz - White building material
        if (blockId.contains("quartz")) {
            return 0xFFECE5DD;
        }

        // Prismarine - Aqua building material
        if (blockId.contains("prismarine")) {
            return 0xFF63A495;
        }

        // Purpur - End building material
        if (blockId.contains("purpur")) {
            return 0xFFA77BA7;
        }

        // Nether bricks
        if (blockId.contains("nether_brick")) {
            return 0xFF2C151A;
        }

        // Copper blocks
        if (blockId.contains("copper") && !blockId.contains("ore")) {
            if (blockId.contains("oxidized")) return 0xFF52A384;
            if (blockId.contains("weathered")) return 0xFF6C9C6E;
            if (blockId.contains("exposed")) return 0xFF9F7B65;
            return 0xFFBF6B46; // Regular copper
        }

        // Iron blocks
        if (block == Blocks.IRON_BLOCK) return 0xFFDBDBDB;

        // Gold blocks
        if (block == Blocks.GOLD_BLOCK) return 0xFFF9D627;

        // Diamond blocks
        if (block == Blocks.DIAMOND_BLOCK) return 0xFF6BE8E4;

        // Emerald blocks
        if (block == Blocks.EMERALD_BLOCK) return 0xFF17DD62;

        // === NATURAL BLOCKS ===

        // Grass/vegetation - Darker greens to contrast with buildings
        if (block == Blocks.GRASS_BLOCK || block == Blocks.TALL_GRASS ||
            block == Blocks.FERN || blockId.contains("grass") || blockId.contains("fern")) {
            return 0xFF4A7A35;
        }

        // Trees/leaves - Even darker green
        if (blockId.contains("leaves")) {
            return 0xFF3A6025;
        }
        if (blockId.contains("log") || blockId.contains("wood")) {
            return 0xFF5B4028;
        }

        // Sand - Keep distinct
        if (block == Blocks.SAND || block == Blocks.SANDSTONE) return 0xFFD4C483;
        if (block == Blocks.RED_SAND || block == Blocks.RED_SANDSTONE) return 0xFFB5633A;

        // Stone types - Natural stone is darker than building stone
        if (block == Blocks.STONE || block == Blocks.COBBLESTONE ||
            block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE) {
            return 0xFF707070;
        }
        if (block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE) return 0xFF454545;

        // Dirt
        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT ||
            block == Blocks.ROOTED_DIRT || block == Blocks.PODZOL) {
            return 0xFF7A5B3B;
        }

        // Snow
        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW) {
            return 0xFFEEEEEE;
        }

        // Ice
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            return 0xFFAADDFF;
        }

        // Terracotta/clay
        if (blockId.contains("terracotta")) {
            return 0xFF9B5B3B;
        }

        // Netherrack
        if (block == Blocks.NETHERRACK) return 0xFF6B3030;
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) return 0xFF5B4B3B;
        if (block == Blocks.CRIMSON_NYLIUM) return 0xFF8B2020;
        if (block == Blocks.WARPED_NYLIUM) return 0xFF207B7B;

        // End stone
        if (block == Blocks.END_STONE) return 0xFFDBD8A0;

        // Mycelium
        if (block == Blocks.MYCELIUM) return 0xFF8B7B8B;

        // Gravel
        if (block == Blocks.GRAVEL) return 0xFF9B9B9B;

        // Default - gray
        return 0xFF707070;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Divider
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int mapX = guiLeft + 15;
        int mapY = guiTop + 35;

        // Render map background
        graphics.fill(mapX - 1, mapY - 1,
                      mapX + MAP_SIZE * cellSize + 1,
                      mapY + MAP_SIZE * cellSize + 1,
                      0xFF333333);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...",
                                        mapX + MAP_SIZE * cellSize / 2,
                                        mapY + MAP_SIZE * cellSize / 2 - 4,
                                        COLOR_TEXT);
            return;
        }

        // Render chunks
        int halfSize = MAP_SIZE / 2;
        for (int dz = -halfSize; dz <= halfSize; dz++) {
            for (int dx = -halfSize; dx <= halfSize; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                int cellX = mapX + (dx + halfSize) * cellSize;
                int cellY = mapY + (dz + halfSize) * cellSize;

                long key = chunkKey(chunkX, chunkZ);
                ChunkData data = chunkMap.get(key);

                if (terrainMode && terrainLoaded) {
                    // Render terrain with claim overlay
                    renderTerrainChunk(graphics, cellX, cellY, key, data);
                } else {
                    // Render solid color grid
                    renderGridChunk(graphics, cellX, cellY, key, data);
                }

                // Draw selection highlight
                if (hasSelection && chunkX == selectedChunkX && chunkZ == selectedChunkZ) {
                    drawSelectionBorder(graphics, cellX, cellY);
                }

                // Draw player position marker
                if (dx == 0 && dz == 0) {
                    graphics.drawCenteredString(this.font, "§l@", cellX + cellSize / 2, cellY + cellSize / 2 - 4, 0xFFFFFFFF);
                }
            }
        }

        // Render legend
        renderLegend(graphics);

        // Selected chunk info on right side
        renderSelectedInfo(graphics);

        // Coordinates display
        graphics.drawString(this.font, "§7Pos: §f" + playerChunkX + ", " + playerChunkZ,
                           guiLeft + guiWidth - 80, guiTop + 24, COLOR_TEXT);

        // Mode indicator
        String modeText = terrainMode ? "§aTerrain" : "§7Grid";
        graphics.drawString(this.font, modeText, guiLeft + 15, guiTop + 24, COLOR_TEXT);
    }

    private void renderGridChunk(GuiGraphics graphics, int cellX, int cellY, long key, ChunkData data) {
        int color = getChunkColor(data);

        // Check if this is a pending action chunk (flash effect)
        if (key == pendingActionChunkKey) {
            long elapsed = System.currentTimeMillis() - pendingActionTime;
            if (elapsed < 2000) {
                boolean flash = (elapsed / 200) % 2 == 0;
                color = flash ? 0xFFFFFF00 : 0xFF888800;
            } else {
                pendingActionChunkKey = -1;
            }
        }

        graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, color);
    }

    private void renderTerrainChunk(GuiGraphics graphics, int cellX, int cellY, long key, ChunkData data) {
        int[] colors = terrainCache.get(key);

        if (colors != null) {
            // Render terrain pixels
            int pixelSize = Math.max(1, cellSize / TERRAIN_RESOLUTION);
            for (int tz = 0; tz < TERRAIN_RESOLUTION; tz++) {
                for (int tx = 0; tx < TERRAIN_RESOLUTION; tx++) {
                    int color = colors[tz * TERRAIN_RESOLUTION + tx];
                    int px = cellX + tx * pixelSize;
                    int py = cellY + tz * pixelSize;
                    graphics.fill(px, py, px + pixelSize, py + pixelSize, color);
                }
            }
        } else {
            // Fallback to gray if terrain not loaded
            graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, 0xFF555555);
        }

        // Draw semi-transparent claim overlay
        if (data != null) {
            int overlayColor = getClaimOverlayColor(data);
            graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, overlayColor);
        }

        // Pending action flash
        if (key == pendingActionChunkKey) {
            long elapsed = System.currentTimeMillis() - pendingActionTime;
            if (elapsed < 2000) {
                boolean flash = (elapsed / 200) % 2 == 0;
                int flashColor = flash ? 0x88FFFF00 : 0x44888800;
                graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, flashColor);
            } else {
                pendingActionChunkKey = -1;
            }
        }
    }

    private int getClaimOverlayColor(ChunkData data) {
        // Semi-transparent overlay colors (alpha = 0x66 for ~40% opacity)
        if (data.isPlayerNation) {
            return 0x664CAF50; // Green overlay
        } else if (data.isAlly) {
            return 0x662196F3; // Blue overlay
        } else if (data.isEnemy) {
            return 0x66F44336; // Red overlay
        } else {
            return 0x66FF9800; // Orange overlay for other nations
        }
    }

    private void drawSelectionBorder(GuiGraphics graphics, int cellX, int cellY) {
        int borderColor = 0xFFFFFFFF;
        graphics.fill(cellX - 1, cellY - 1, cellX + cellSize, cellY, borderColor);
        graphics.fill(cellX - 1, cellY + cellSize - 1, cellX + cellSize, cellY + cellSize, borderColor);
        graphics.fill(cellX - 1, cellY, cellX, cellY + cellSize - 1, borderColor);
        graphics.fill(cellX + cellSize - 1, cellY, cellX + cellSize, cellY + cellSize - 1, borderColor);
    }

    private void renderLegend(GuiGraphics graphics) {
        int legendX = guiLeft + 15;
        int legendY = guiTop + guiHeight - 50;

        graphics.drawString(this.font, "§7Legend:", legendX, legendY, COLOR_TEXT);
        legendY += 10;

        // Your nation
        graphics.fill(legendX, legendY + 2, legendX + 8, legendY + 10, 0xFF4CAF50);
        graphics.drawString(this.font, "§aYours", legendX + 12, legendY, COLOR_TEXT);

        // Ally
        graphics.fill(legendX + 50, legendY + 2, legendX + 58, legendY + 10, 0xFF2196F3);
        graphics.drawString(this.font, "§9Ally", legendX + 62, legendY, COLOR_TEXT);

        // Enemy
        graphics.fill(legendX + 95, legendY + 2, legendX + 103, legendY + 10, 0xFFF44336);
        graphics.drawString(this.font, "§cEnemy", legendX + 107, legendY, COLOR_TEXT);

        // Neutral/Other
        legendY += 10;
        graphics.fill(legendX, legendY + 2, legendX + 8, legendY + 10, 0xFFFF9800);
        graphics.drawString(this.font, "§6Other", legendX + 12, legendY, COLOR_TEXT);

        // Wilderness
        graphics.fill(legendX + 50, legendY + 2, legendX + 58, legendY + 10, 0xFF555555);
        graphics.drawString(this.font, "§8Wild", legendX + 62, legendY, COLOR_TEXT);
    }

    private void renderSelectedInfo(GuiGraphics graphics) {
        int infoX = guiLeft + 15 + MAP_SIZE * cellSize + 10;
        int infoY = guiTop + 130;

        if (hasSelection) {
            ChunkData selected = chunkMap.get(chunkKey(selectedChunkX, selectedChunkZ));

            graphics.drawString(this.font, "§6Selected:", infoX, infoY, COLOR_TEXT);
            graphics.drawString(this.font, "§7" + selectedChunkX + ", " + selectedChunkZ, infoX, infoY + 10, COLOR_TEXT);

            if (selected != null) {
                graphics.drawString(this.font, "§7" + selected.nationName, infoX, infoY + 22, COLOR_TEXT);
                if (selected.cityName != null) {
                    graphics.drawString(this.font, "§8" + selected.cityName, infoX, infoY + 32, 0xFFAAAAAA);
                }
            } else {
                graphics.drawString(this.font, "§8Wilderness", infoX, infoY + 22, 0xFF888888);
            }
        }

        // Coordinates display
        graphics.drawString(this.font, "§7Pos: §f" + playerChunkX + ", " + playerChunkZ,
                           guiLeft + guiWidth - 80, guiTop + 24, COLOR_TEXT);
    }

    private int getChunkColor(ChunkData data) {
        if (data == null) {
            return 0xFF555555; // Wilderness - dark gray
        }

        if (data.isPlayerNation) {
            return 0xFF4CAF50; // Your nation - green
        } else if (data.isAlly) {
            return 0xFF2196F3; // Ally - blue
        } else if (data.isEnemy) {
            return 0xFFF44336; // Enemy - red
        } else {
            return 0xFFFF9800; // Other nation - orange
        }
    }

    private long chunkKey(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check if click is within map area
        int mapX = guiLeft + 15;
        int mapY = guiTop + 35;
        int mapWidth = MAP_SIZE * cellSize;
        int mapHeight = MAP_SIZE * cellSize;

        if (mouseX >= mapX && mouseX < mapX + mapWidth &&
            mouseY >= mapY && mouseY < mapY + mapHeight) {

            int halfSize = MAP_SIZE / 2;
            int clickedCellX = (int) ((mouseX - mapX) / cellSize) - halfSize;
            int clickedCellZ = (int) ((mouseY - mapY) / cellSize) - halfSize;

            selectedChunkX = playerChunkX + clickedCellX;
            selectedChunkZ = playerChunkZ + clickedCellZ;
            hasSelection = true;

            updateButtonStates();
            return true;
        }

        return false;
    }

    private void updateButtonStates() {
        if (!hasSelection || !dataLoaded) {
            claimButton.active = false;
            unclaimButton.active = false;
            infoButton.active = false;
            return;
        }

        ChunkData selected = chunkMap.get(chunkKey(selectedChunkX, selectedChunkZ));

        // Can claim if wilderness and player has permission
        claimButton.active = (selected == null && playerNation != null);

        // Can unclaim if own nation's chunk
        unclaimButton.active = (selected != null && selected.isPlayerNation && selected.canManage);

        // Can always view info
        infoButton.active = true;
    }

    private void claimSelectedChunk() {
        if (hasSelection) {
            pendingActionChunkKey = chunkKey(selectedChunkX, selectedChunkZ);
            pendingActionTime = System.currentTimeMillis();
            NetworkHandler.sendToServer(new ChunkActionPacket(
                ChunkActionPacket.Action.CLAIM, selectedChunkX, selectedChunkZ, null));
            // Disable buttons while action is pending
            claimButton.active = false;
            unclaimButton.active = false;
        }
    }

    private void unclaimSelectedChunk() {
        if (hasSelection) {
            pendingActionChunkKey = chunkKey(selectedChunkX, selectedChunkZ);
            pendingActionTime = System.currentTimeMillis();
            NetworkHandler.sendToServer(new ChunkActionPacket(
                ChunkActionPacket.Action.UNCLAIM, selectedChunkX, selectedChunkZ, null));
            // Disable buttons while action is pending
            claimButton.active = false;
            unclaimButton.active = false;
        }
    }

    private void showChunkInfo() {
        // Open the chunk info screen for the selected chunk
        if (hasSelection && this.minecraft != null) {
            this.minecraft.setScreen(new ChunkInfoScreen(selectedChunkX, selectedChunkZ));
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when map data is received
    public void updateMapData(int playerX, int playerZ, String playerNation, Map<Long, ChunkData> chunks) {
        this.playerChunkX = playerX;
        this.playerChunkZ = playerZ;
        this.playerNation = playerNation;
        this.chunkMap = chunks;
        this.dataLoaded = true;
        this.pendingActionChunkKey = -1; // Clear pending state

        // Reload terrain for the correct position
        this.terrainLoaded = false;
        loadTerrainData();

        updateButtonStates();
    }

    // Called when a claim/unclaim action succeeds
    public void onChunkUpdated(int chunkX, int chunkZ, ChunkData newData) {
        long key = chunkKey(chunkX, chunkZ);
        if (newData != null) {
            chunkMap.put(key, newData);
        } else {
            chunkMap.remove(key);
        }
        updateButtonStates();
    }

    // Called by network handler when auto-claim state is synced
    public void updateAutoClaimState(boolean enabled, boolean canUse, String cityName) {
        this.autoClaimEnabled = enabled;
        this.canUseAutoClaim = canUse;
        this.autoClaimCityName = cityName;
    }

    /**
     * Data class for chunk information
     */
    public static class ChunkData {
        public String nationName;
        public String cityName;
        public boolean isPlayerNation;
        public boolean isAlly;
        public boolean isEnemy;
        public boolean canManage;

        public ChunkData(String nationName, String cityName, boolean isPlayerNation,
                         boolean isAlly, boolean isEnemy, boolean canManage) {
            this.nationName = nationName;
            this.cityName = cityName;
            this.isPlayerNation = isPlayerNation;
            this.isAlly = isAlly;
            this.isEnemy = isEnemy;
            this.canManage = canManage;
        }
    }
}

