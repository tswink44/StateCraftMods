package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestMarketplaceDataPacket;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marketplace screen showing chunks for sale
 * Has two modes: List view and Map view (with terrain option)
 */
public class ChunkMarketplaceScreen extends StateCraftScreen {

    private static final int MAP_SIZE = 17; // 17x17 chunk grid
    private static final int MIN_CELL_SIZE = 10;
    private static final int MAX_CELL_SIZE = 20;
    private static final int TERRAIN_RESOLUTION = 8; // Sample 8x8 points per chunk

    // View mode
    private boolean mapMode = false;
    private boolean terrainMode = false;
    private int cellSize = 14;

    // Player position (center of map)
    private int playerChunkX = 0;
    private int playerChunkZ = 0;

    // List of chunks for sale
    private List<ChunkListing> listings = new ArrayList<>();
    private boolean dataLoaded = false;

    // Terrain color cache
    private Map<Long, int[]> terrainCache = new HashMap<>();
    private boolean terrainLoaded = false;

    // List scroll
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 8;

    // Double-click tracking for map
    private long lastClickTime = 0;
    private int lastClickChunkX = Integer.MIN_VALUE;
    private int lastClickChunkZ = Integer.MIN_VALUE;
    private static final long DOUBLE_CLICK_TIME = 400;

    // Map data - chunks for sale in visible area
    private java.util.Map<Long, ChunkListing> mapListings = new java.util.HashMap<>();

    // Buttons
    private Button toggleViewButton;
    private Button toggleTerrainButton;
    private Button scrollUpButton;
    private Button scrollDownButton;

    public ChunkMarketplaceScreen() {
        super(Component.literal("Chunk Marketplace"));
        this.guiWidth = 300;
        this.guiHeight = 240;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        // Get player position
        if (this.minecraft != null && this.minecraft.player != null) {
            playerChunkX = this.minecraft.player.chunkPosition().x;
            playerChunkZ = this.minecraft.player.chunkPosition().z;
        }

        // Calculate cell size for map mode
        int availableSize = Math.min(guiWidth - 20, guiHeight - 80);
        cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, availableSize / MAP_SIZE));

        // Request marketplace data from server
        NetworkHandler.sendToServer(new RequestMarketplaceDataPacket(playerChunkX, playerChunkZ));

        // Load terrain data
        loadTerrainData();

        buildUI();
    }

    private void loadTerrainData() {
        if (this.minecraft == null || this.minecraft.level == null) return;

        Level level = this.minecraft.level;
        terrainCache.clear();

        int halfSize = MAP_SIZE / 2;
        for (int dz = -halfSize; dz <= halfSize; dz++) {
            for (int dx = -halfSize; dx <= halfSize; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                long key = chunkKey(chunkX, chunkZ);
                int[] colors = new int[TERRAIN_RESOLUTION * TERRAIN_RESOLUTION];

                try {
                    ChunkAccess chunk = level.getChunk(chunkX, chunkZ);
                    int step = 16 / TERRAIN_RESOLUTION;

                    for (int z = 0; z < TERRAIN_RESOLUTION; z++) {
                        for (int x = 0; x < TERRAIN_RESOLUTION; x++) {
                            int worldX = chunkX * 16 + x * step + step / 2;
                            int worldZ = chunkZ * 16 + z * step + step / 2;

                            int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x * step, z * step);
                            BlockPos pos = new BlockPos(worldX, height, worldZ);
                            BlockState state = level.getBlockState(pos);

                            colors[z * TERRAIN_RESOLUTION + x] = getBlockColor(state);
                        }
                    }
                } catch (Exception e) {
                    // Fill with default color if chunk not loaded
                    for (int i = 0; i < colors.length; i++) {
                        colors[i] = 0xFF505050;
                    }
                }

                terrainCache.put(key, colors);
            }
        }
        terrainLoaded = true;
    }

    private int getBlockColor(BlockState state) {
        var block = state.getBlock();

        // Grass and plants
        if (block == Blocks.GRASS_BLOCK) return 0xFF7CBD6B;
        if (block == Blocks.GRASS || block == Blocks.TALL_GRASS) return 0xFF7CBD6B;
        if (block == Blocks.FERN || block == Blocks.LARGE_FERN) return 0xFF6AAA5A;

        // Trees
        if (block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES) return 0xFF4A8A3A;
        if (block == Blocks.SPRUCE_LEAVES) return 0xFF3A6A3A;
        if (block == Blocks.DARK_OAK_LEAVES) return 0xFF3A5A3A;
        if (block == Blocks.JUNGLE_LEAVES || block == Blocks.ACACIA_LEAVES) return 0xFF5A9A4A;
        if (block == Blocks.MANGROVE_LEAVES) return 0xFF4A7A4A;

        // Wood
        if (state.is(net.minecraft.tags.BlockTags.LOGS)) return 0xFF8B6914;

        // Water
        if (block == Blocks.WATER) return 0xFF3F76E4;

        // Sand and beaches
        if (block == Blocks.SAND) return 0xFFDBCFA3;
        if (block == Blocks.RED_SAND) return 0xFFA95821;

        // Stone and ores
        if (block == Blocks.STONE || block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE)
            return 0xFF808080;

        // Dirt variants
        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT) return 0xFF8B6914;
        if (block == Blocks.PODZOL) return 0xFF6B5344;
        if (block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS) return 0xFF4A4A4A;

        // Snow and ice
        if (block == Blocks.SNOW_BLOCK || block == Blocks.SNOW || block == Blocks.POWDER_SNOW) return 0xFFFFFFFF;
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) return 0xFFA0D0FF;

        // Building blocks
        if (block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) return 0xFF6A6A6A;
        if (block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS) return 0xFF7A7A7A;
        if (state.is(net.minecraft.tags.BlockTags.PLANKS)) return 0xFFBC9862;
        if (block == Blocks.BRICKS) return 0xFF9B5B4A;

        // Terracotta
        if (block == Blocks.TERRACOTTA) return 0xFF9E6246;

        // Concrete and wool (common colors)
        if (block == Blocks.WHITE_CONCRETE || block == Blocks.WHITE_WOOL) return 0xFFCFCFCF;
        if (block == Blocks.BLACK_CONCRETE || block == Blocks.BLACK_WOOL) return 0xFF1D1D21;
        if (block == Blocks.RED_CONCRETE || block == Blocks.RED_WOOL) return 0xFF8E2121;
        if (block == Blocks.BLUE_CONCRETE || block == Blocks.BLUE_WOOL) return 0xFF35399D;

        // Nether blocks
        if (block == Blocks.NETHERRACK) return 0xFF6F3535;
        if (block == Blocks.CRIMSON_NYLIUM) return 0xFF941818;
        if (block == Blocks.WARPED_NYLIUM) return 0xFF167E86;

        // End blocks
        if (block == Blocks.END_STONE) return 0xFFDBDEA1;

        // Paths
        if (block == Blocks.DIRT_PATH) return 0xFF9B7D4A;

        // Flowers (generic bright color)
        if (state.is(net.minecraft.tags.BlockTags.FLOWERS)) return 0xFFFF6B6B;

        // Crops
        if (block == Blocks.WHEAT) return 0xFFD4B364;
        if (block == Blocks.CARROTS || block == Blocks.POTATOES) return 0xFF4CAF50;

        // Mycelium
        if (block == Blocks.MYCELIUM) return 0xFF8B7B8B;

        // Gravel
        if (block == Blocks.GRAVEL) return 0xFF9B9B9B;

        // Default
        return 0xFF707070;
    }

    private void buildUI() {
        this.clearWidgets();

        int centerX = this.width / 2;

        // Toggle view button (List/Map)
        String toggleText = mapMode ? "Show List" : "Show Map";
        toggleViewButton = this.addRenderableWidget(Button.builder(
            Component.literal(toggleText),
            btn -> toggleView()
        ).pos(guiLeft + guiWidth - 75, guiTop + 6).size(65, 14).build());

        if (mapMode) {
            // Terrain toggle button (only in map mode)
            String terrainText = terrainMode ? "Grid" : "Terrain";
            toggleTerrainButton = this.addRenderableWidget(Button.builder(
                Component.literal(terrainText),
                btn -> toggleTerrain()
            ).pos(guiLeft + 10, guiTop + 6).size(50, 14).build());
        }

        if (!mapMode) {
            // List mode - add scroll buttons
            scrollUpButton = this.addRenderableWidget(Button.builder(
                Component.literal("▲"),
                btn -> scroll(-1)
            ).pos(guiLeft + guiWidth - 25, guiTop + 28).size(18, 14).build());

            scrollDownButton = this.addRenderableWidget(Button.builder(
                Component.literal("▼"),
                btn -> scroll(1)
            ).pos(guiLeft + guiWidth - 25, guiTop + guiHeight - 55).size(18, 14).build());
        }

        // Back button
        this.addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> goBack()
        ).pos(centerX - 40, guiTop + guiHeight - 26).size(80, 18).build());
    }

    private void toggleView() {
        mapMode = !mapMode;
        buildUI();
    }

    private void toggleTerrain() {
        terrainMode = !terrainMode;
        buildUI();
    }

    private void scroll(int direction) {
        int maxScroll = Math.max(0, listings.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + direction));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = guiLeft + 10;

        // Render title row
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 80, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 80, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, guiLeft + guiWidth - 80, guiTop + 20, 0xFF404040);
        graphics.drawString(this.font, "Chunk Marketplace", startX + 4, guiTop + 9, 0xFFFFFFFF);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "Loading...", this.width / 2, guiTop + 100, 0xFFAAAAAA);
            return;
        }

        if (mapMode) {
            renderMapView(graphics, mouseX, mouseY);
        } else {
            renderListView(graphics, mouseX, mouseY);
        }
    }

    private void renderListView(GuiGraphics graphics, int mouseX, int mouseY) {
        int startX = guiLeft + 10;
        int startY = guiTop + 28;
        int rowHeight = 18;

        // Header row
        graphics.fill(startX, startY, guiLeft + guiWidth - 30, startY + 14, 0xAA606060);
        graphics.drawString(this.font, "§7Coords", startX + 4, startY + 3, 0xFFCCCCCC);
        graphics.drawString(this.font, "§7Owner", startX + 70, startY + 3, 0xFFCCCCCC);
        graphics.drawString(this.font, "§7Price", startX + 140, startY + 3, 0xFFCCCCCC);
        graphics.drawString(this.font, "§7Tax", startX + 210, startY + 3, 0xFFCCCCCC);
        startY += 16;

        if (listings.isEmpty()) {
            graphics.drawCenteredString(this.font, "§8No chunks for sale nearby", this.width / 2, startY + 40, 0xFF888888);
            return;
        }

        // Render visible listings
        for (int i = 0; i < VISIBLE_ROWS && (scrollOffset + i) < listings.size(); i++) {
            ChunkListing listing = listings.get(scrollOffset + i);
            int y = startY + i * rowHeight;

            // Row background - yellow for player, blue for gov
            int rowColor = listing.isGovernment ? 0xAA3366AA : 0xAAAA8833;
            boolean hovered = mouseX >= startX && mouseX < guiLeft + guiWidth - 30 &&
                             mouseY >= y && mouseY < y + 16;
            if (hovered) {
                rowColor = listing.isGovernment ? 0xCC4477BB : 0xCCBB9944;
            }

            graphics.fill(startX, y, guiLeft + guiWidth - 30, y + 16, rowColor);
            graphics.fill(startX, y + 15, guiLeft + guiWidth - 30, y + 16, 0xFF404040);

            // Coords
            String coords = "(" + listing.chunkX + ", " + listing.chunkZ + ")";
            graphics.drawString(this.font, coords, startX + 4, y + 4, 0xFFFFFFFF);

            // Owner
            String owner = listing.isGovernment ? "§9Gov" : "§e" + truncate(listing.ownerName, 8);
            graphics.drawString(this.font, owner, startX + 70, y + 4, 0xFFFFFFFF);

            // Price
            String price = formatPrice(listing.price);
            graphics.drawString(this.font, price, startX + 140, y + 4, 0xFF00FF00);

            // Tax rate (WIP)
            graphics.drawString(this.font, "§8WIP", startX + 210, y + 4, 0xFF888888);
        }

        // Scroll indicator
        if (listings.size() > VISIBLE_ROWS) {
            int totalPages = (listings.size() + VISIBLE_ROWS - 1) / VISIBLE_ROWS;
            int currentPage = (scrollOffset / VISIBLE_ROWS) + 1;
            graphics.drawString(this.font, "§7" + currentPage + "/" + totalPages,
                guiLeft + guiWidth - 50, guiTop + guiHeight - 70, 0xFFAAAAAA);
        }
    }

    private void renderMapView(GuiGraphics graphics, int mouseX, int mouseY) {
        int mapX = guiLeft + (guiWidth - MAP_SIZE * cellSize) / 2;
        int mapY = guiTop + 30;

        // Map background
        graphics.fill(mapX - 1, mapY - 1, mapX + MAP_SIZE * cellSize + 1, mapY + MAP_SIZE * cellSize + 1, 0xFF333333);

        // Render chunks
        int halfSize = MAP_SIZE / 2;
        for (int dz = -halfSize; dz <= halfSize; dz++) {
            for (int dx = -halfSize; dx <= halfSize; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                int cellX = mapX + (dx + halfSize) * cellSize;
                int cellY = mapY + (dz + halfSize) * cellSize;

                long key = chunkKey(chunkX, chunkZ);
                ChunkListing listing = mapListings.get(key);

                if (terrainMode && terrainLoaded) {
                    // Render terrain with sale overlay
                    renderTerrainChunk(graphics, cellX, cellY, key, listing);
                } else {
                    // Render grid mode
                    renderGridChunk(graphics, cellX, cellY, listing);
                }

                // Player position marker
                if (dx == 0 && dz == 0) {
                    graphics.drawCenteredString(this.font, "§l@", cellX + cellSize / 2, cellY + cellSize / 2 - 4, 0xFFFFFFFF);
                }
            }
        }

        // Legend
        int legendY = mapY + MAP_SIZE * cellSize + 5;
        graphics.fill(guiLeft + 20, legendY, guiLeft + 30, legendY + 10, 0xFF3366CC);
        graphics.drawString(this.font, "§7Gov", guiLeft + 35, legendY + 1, 0xFFCCCCCC);

        graphics.fill(guiLeft + 70, legendY, guiLeft + 80, legendY + 10, 0xFFCCAA33);
        graphics.drawString(this.font, "§7Player", guiLeft + 85, legendY + 1, 0xFFCCCCCC);

        graphics.drawString(this.font, "§7Double-click to view", guiLeft + 140, legendY + 1, 0xFF888888);

        // Mode indicator
        String modeText = terrainMode ? "§aTerrain" : "§7Grid";
        graphics.drawString(this.font, modeText, mapX, guiTop + 22, COLOR_TEXT);
    }

    private void renderGridChunk(GuiGraphics graphics, int cellX, int cellY, ChunkListing listing) {
        int color;
        if (listing != null) {
            // For sale - blue for gov, yellow for player
            color = listing.isGovernment ? 0xFF3366CC : 0xFFCCAA33;
        } else {
            // Not for sale or wilderness - dark gray
            color = 0xFF404040;
        }
        graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, color);
    }

    private void renderTerrainChunk(GuiGraphics graphics, int cellX, int cellY, long key, ChunkListing listing) {
        int[] colors = terrainCache.get(key);

        if (colors != null) {
            int pixelSize = Math.max(1, cellSize / TERRAIN_RESOLUTION);

            for (int z = 0; z < TERRAIN_RESOLUTION; z++) {
                for (int x = 0; x < TERRAIN_RESOLUTION; x++) {
                    int px = cellX + x * pixelSize;
                    int py = cellY + z * pixelSize;
                    int color = colors[z * TERRAIN_RESOLUTION + x];

                    graphics.fill(px, py, px + pixelSize, py + pixelSize, color);
                }
            }
        } else {
            // Fallback if no terrain data
            graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, 0xFF505050);
        }

        // Overlay for sale status - semi-transparent border/tint
        if (listing != null) {
            int overlayColor = listing.isGovernment ? 0x663366CC : 0x66CCAA33;
            graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + cellSize - 1, overlayColor);

            // Draw colored border to make it clearer
            int borderColor = listing.isGovernment ? 0xFF3366CC : 0xFFCCAA33;
            // Top
            graphics.fill(cellX, cellY, cellX + cellSize - 1, cellY + 1, borderColor);
            // Bottom
            graphics.fill(cellX, cellY + cellSize - 2, cellX + cellSize - 1, cellY + cellSize - 1, borderColor);
            // Left
            graphics.fill(cellX, cellY, cellX + 1, cellY + cellSize - 1, borderColor);
            // Right
            graphics.fill(cellX + cellSize - 2, cellY, cellX + cellSize - 1, cellY + cellSize - 1, borderColor);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (mapMode) {
            return handleMapClick(mouseX, mouseY);
        } else {
            return handleListClick(mouseX, mouseY);
        }
    }

    private boolean handleMapClick(double mouseX, double mouseY) {
        int mapX = guiLeft + (guiWidth - MAP_SIZE * cellSize) / 2;
        int mapY = guiTop + 30;
        int mapWidth = MAP_SIZE * cellSize;
        int mapHeight = MAP_SIZE * cellSize;

        if (mouseX >= mapX && mouseX < mapX + mapWidth &&
            mouseY >= mapY && mouseY < mapY + mapHeight) {

            int halfSize = MAP_SIZE / 2;
            int clickedCellX = (int) ((mouseX - mapX) / cellSize) - halfSize;
            int clickedCellZ = (int) ((mouseY - mapY) / cellSize) - halfSize;

            int clickedChunkX = playerChunkX + clickedCellX;
            int clickedChunkZ = playerChunkZ + clickedCellZ;

            long currentTime = System.currentTimeMillis();

            // Check for double-click
            if (clickedChunkX == lastClickChunkX && clickedChunkZ == lastClickChunkZ &&
                currentTime - lastClickTime < DOUBLE_CLICK_TIME) {
                // Double-click - open chunk market screen
                openChunkMarketScreen(clickedChunkX, clickedChunkZ);
                lastClickTime = 0;
                lastClickChunkX = Integer.MIN_VALUE;
                lastClickChunkZ = Integer.MIN_VALUE;
            } else {
                lastClickTime = currentTime;
                lastClickChunkX = clickedChunkX;
                lastClickChunkZ = clickedChunkZ;
            }

            return true;
        }

        return false;
    }

    private boolean handleListClick(double mouseX, double mouseY) {
        int startX = guiLeft + 10;
        int startY = guiTop + 44; // After header
        int rowHeight = 18;

        for (int i = 0; i < VISIBLE_ROWS && (scrollOffset + i) < listings.size(); i++) {
            int y = startY + i * rowHeight;
            if (mouseX >= startX && mouseX < guiLeft + guiWidth - 30 &&
                mouseY >= y && mouseY < y + 16) {

                ChunkListing listing = listings.get(scrollOffset + i);
                openChunkMarketScreen(listing.chunkX, listing.chunkZ);
                return true;
            }
        }

        return false;
    }

    private void openChunkMarketScreen(int chunkX, int chunkZ) {
        // Open chunk market screen via reflection (economy mod)
        try {
            Class<?> screenClass = Class.forName("com.statecraft.economy.client.screen.ChunkMarketScreen");
            var constructor = screenClass.getConstructor(int.class, int.class);
            var screen = constructor.newInstance(chunkX, chunkZ);
            this.minecraft.setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (ClassNotFoundException e) {
            if (this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                    Component.literal("§cStateCraft Economy mod required"));
            }
        } catch (Exception e) {
            if (this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                    Component.literal("§cError opening market screen"));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!mapMode) {
            scroll(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateMarketplaceData(List<ChunkListing> listings) {
        this.listings = new ArrayList<>(listings);
        this.mapListings.clear();

        // Build map lookup
        for (ChunkListing listing : listings) {
            mapListings.put(chunkKey(listing.chunkX, listing.chunkZ), listing);
        }

        this.dataLoaded = true;
        this.scrollOffset = 0;
    }

    private long chunkKey(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    private String formatPrice(double price) {
        if (price >= 1000000) {
            return String.format("$%.1fM", price / 1000000);
        } else if (price >= 1000) {
            return String.format("$%.1fK", price / 1000);
        } else {
            return String.format("$%.0f", price);
        }
    }

    /**
     * Data class for chunk listings
     */
    public static class ChunkListing {
        public final int chunkX;
        public final int chunkZ;
        public final String ownerName;
        public final boolean isGovernment;
        public final double price;
        public final String cityName;

        public ChunkListing(int chunkX, int chunkZ, String ownerName, boolean isGovernment, double price, String cityName) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerName = ownerName;
            this.isGovernment = isGovernment;
            this.price = price;
            this.cityName = cityName;
        }
    }
}

