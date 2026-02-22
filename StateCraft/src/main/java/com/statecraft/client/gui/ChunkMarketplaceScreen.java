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

    private static final int MAP_SIZE = 17; // 17x17 chunk grid (matches ChunkMapScreen)
    private static final int MIN_CELL_SIZE = 10;
    private static final int MAX_CELL_SIZE = 20;
    private static final int TERRAIN_RESOLUTION = 8; // Sample 8x8 points per chunk for better building clarity

    // View mode
    private boolean mapMode = false;
    private boolean terrainMode = true; // Default to terrain
    private int cellSize = 10;

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

    // Selected chunk for sidebar display
    private ChunkListing selectedListing = null;
    private int selectedChunkX = Integer.MIN_VALUE;
    private int selectedChunkZ = Integer.MIN_VALUE;

    // Buy confirmation popup
    private boolean showBuyConfirmation = false;
    private ChunkListing confirmationListing = null;

    // Map data - chunks for sale in visible area
    private java.util.Map<Long, ChunkListing> mapListings = new java.util.HashMap<>();

    // Buttons
    private Button toggleViewButton;
    private Button toggleTerrainButton;
    private Button scrollUpButton;
    private Button scrollDownButton;
    private Button confirmBuyButton;
    private Button cancelBuyButton;

    // Sidebar width
    private static final int SIDEBAR_WIDTH = 80;

    public ChunkMarketplaceScreen() {
        super(Component.literal("Chunk Marketplace"));
        // Initial size - will be recalculated in init() for map mode
        this.guiWidth = 280;
        this.guiHeight = 200;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return true;
    }

    @Override
    protected void init() {
        // Get player position first
        if (this.minecraft != null && this.minecraft.player != null) {
            playerChunkX = this.minecraft.player.chunkPosition().x;
            playerChunkZ = this.minecraft.player.chunkPosition().z;
        }

        // Calculate appropriate dimensions based on mode
        if (mapMode) {
            // Match ChunkMapScreen sizing approach
            // Horizontal: 15px left padding + map + 10px gap + SIDEBAR_WIDTH + 15px right padding
            int sidebarTotal = 10 + SIDEBAR_WIDTH + 15; // gap + sidebar + right padding
            int availableWidth = this.width - 60 - sidebarTotal; // 60 for centering margins
            // Vertical: 35px top (title+divider+mode text) + map + 30px legend area + 28px back button
            int availableHeight = this.height - 95; // 95 for top + bottom

            // Calculate max cell size that fits
            int maxCellsWidth = availableWidth / MAP_SIZE;
            int maxCellsHeight = availableHeight / MAP_SIZE;
            cellSize = Math.min(maxCellsWidth, maxCellsHeight);
            cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, cellSize));

            // Set GUI size based on calculated cell size
            this.guiWidth = 15 + MAP_SIZE * cellSize + sidebarTotal;
            this.guiHeight = MAP_SIZE * cellSize + 95; // 35 top + map + 30 legend + 30 back button
        } else {
            // List mode - compact size
            this.guiWidth = 280;
            this.guiHeight = 200;
            cellSize = 10;
        }

        super.init();

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
                int blockStep = 16 / TERRAIN_RESOLUTION;

                try {
                    for (int z = 0; z < TERRAIN_RESOLUTION; z++) {
                        for (int x = 0; x < TERRAIN_RESOLUTION; x++) {
                            int worldX = chunkX * 16 + x * blockStep + blockStep / 2;
                            int worldZ = chunkZ * 16 + z * blockStep + blockStep / 2;

                            colors[z * TERRAIN_RESOLUTION + x] = getTerrainColor(level, worldX, worldZ);
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

    private int getTerrainColor(Level level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        pos.setY(y);

        // Go down to find first non-air block
        while (y > level.getMinBuildHeight() && level.getBlockState(pos).isAir()) {
            y--;
            pos.setY(y);
        }

        BlockState state = level.getBlockState(pos);
        return getBlockColor(state);
    }

    private int getBlockColor(BlockState state) {
        // Map common blocks to colors - improved for building clarity (matches ChunkMapScreen)
        var block = state.getBlock();
        String blockId = state.getBlock().getDescriptionId();

        // Water
        if (block == Blocks.WATER) return 0xFF3B6FCF;

        // Lava
        if (block == Blocks.LAVA) return 0xFFFF6600;

        // === BUILDING MATERIALS ===
        if (blockId.contains("stone_brick") || blockId.contains("stonebrick")) return 0xFFB0B8C0;
        if (block == Blocks.BRICKS || blockId.contains("brick")) return 0xFFB54A32;

        // Concrete
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
            return 0xFFC0C0C0;
        }

        // Wool and carpet
        if (blockId.contains("wool") || blockId.contains("carpet")) {
            if (blockId.contains("white")) return 0xFFE9ECEC;
            if (blockId.contains("black")) return 0xFF1D1D21;
            return 0xFFD0D0D0;
        }

        // Glass
        if (blockId.contains("glass")) return 0xFF88D4E5;

        // Planks
        if (blockId.contains("plank")) {
            if (blockId.contains("oak")) return 0xFFB8945F;
            if (blockId.contains("spruce")) return 0xFF73533B;
            if (blockId.contains("birch")) return 0xFFD5C98D;
            if (blockId.contains("jungle")) return 0xFFB58857;
            if (blockId.contains("acacia")) return 0xFFA85D3D;
            if (blockId.contains("dark_oak")) return 0xFF4F3218;
            if (blockId.contains("crimson")) return 0xFF7E3A56;
            if (blockId.contains("warped")) return 0xFF2B6963;
            return 0xFFAA8855;
        }

        // Other building materials
        if (blockId.contains("polished")) return 0xFFA0A0A0;
        if (blockId.contains("quartz")) return 0xFFECE5DD;
        if (blockId.contains("prismarine")) return 0xFF63A495;
        if (blockId.contains("purpur")) return 0xFFA77BA7;
        if (blockId.contains("nether_brick")) return 0xFF2C151A;

        // Metal/gem blocks
        if (blockId.contains("copper") && !blockId.contains("ore")) {
            if (blockId.contains("oxidized")) return 0xFF52A384;
            if (blockId.contains("weathered")) return 0xFF6C9C6E;
            if (blockId.contains("exposed")) return 0xFF9F7B65;
            return 0xFFBF6B46;
        }
        if (block == Blocks.IRON_BLOCK) return 0xFFDBDBDB;
        if (block == Blocks.GOLD_BLOCK) return 0xFFF9D627;
        if (block == Blocks.DIAMOND_BLOCK) return 0xFF6BE8E4;
        if (block == Blocks.EMERALD_BLOCK) return 0xFF17DD62;

        // === NATURAL BLOCKS ===
        if (block == Blocks.GRASS_BLOCK || block == Blocks.TALL_GRASS ||
            block == Blocks.FERN || blockId.contains("grass") || blockId.contains("fern")) {
            return 0xFF4A7A35;
        }

        if (blockId.contains("leaves")) return 0xFF3A6025;
        if (blockId.contains("log") || blockId.contains("wood")) return 0xFF5B4028;

        // Sand
        if (block == Blocks.SAND || block == Blocks.SANDSTONE) return 0xFFD4C483;
        if (block == Blocks.RED_SAND || block == Blocks.RED_SANDSTONE) return 0xFFB5633A;

        // Stone types
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

        // Snow and ice
        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW) return 0xFFEEEEEE;
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) return 0xFFAADDFF;

        // Terracotta
        if (blockId.contains("terracotta")) return 0xFF9B5B3B;

        // Nether
        if (block == Blocks.NETHERRACK) return 0xFF6B3030;
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL) return 0xFF5B4B3B;
        if (block == Blocks.CRIMSON_NYLIUM) return 0xFF8B2020;
        if (block == Blocks.WARPED_NYLIUM) return 0xFF207B7B;

        // End
        if (block == Blocks.END_STONE) return 0xFFDBD8A0;

        // Misc
        if (block == Blocks.MYCELIUM) return 0xFF8B7B8B;
        if (block == Blocks.GRAVEL) return 0xFF9B9B9B;
        if (block == Blocks.DIRT_PATH) return 0xFF9B7D4A;
        if (state.is(net.minecraft.tags.BlockTags.FLOWERS)) return 0xFFFF6B6B;
        if (block == Blocks.WHEAT) return 0xFFD4B364;
        if (block == Blocks.CARROTS || block == Blocks.POTATOES) return 0xFF4CAF50;


        // Default
        return 0xFF707070;
    }

    private void buildUI() {
        this.clearWidgets();

        int centerX = this.width / 2;

        if (!showBuyConfirmation) {
            // Toggle view button (List/Map)
            String toggleText = mapMode ? "List View" : "Map View";
            toggleViewButton = this.addRenderableWidget(Button.builder(
                Component.literal(toggleText),
                btn -> toggleView()
            ).pos(guiLeft + guiWidth - 75, guiTop + 6).size(65, 14).build());

            if (mapMode) {
                // Terrain toggle button (in map mode) - positioned in sidebar area
                int mapRight = guiLeft + 15 + MAP_SIZE * cellSize + 10;
                String terrainText = terrainMode ? "Grid" : "Terrain";
                toggleTerrainButton = this.addRenderableWidget(Button.builder(
                    Component.literal(terrainText),
                    btn -> toggleTerrain()
                ).pos(mapRight, guiTop + 35).size(SIDEBAR_WIDTH, 18).build());
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
            ).pos(guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 24).size(80, 20).build());
        } else {
            // Buy confirmation popup buttons
            int popupCenterX = this.width / 2;
            int popupY = this.height / 2;

            confirmBuyButton = this.addRenderableWidget(Button.builder(
                Component.literal("§aConfirm Purchase"),
                btn -> confirmPurchase()
            ).pos(popupCenterX - 70, popupY + 30).size(140, 20).build());

            cancelBuyButton = this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> cancelPurchase()
            ).pos(popupCenterX - 40, popupY + 55).size(80, 20).build());
        }
    }

    private void toggleView() {
        mapMode = !mapMode;
        // Reinitialize to recalculate dimensions for new mode
        this.init();
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
        // Buy confirmation popup
        if (showBuyConfirmation && confirmationListing != null) {
            renderBuyConfirmationPopup(graphics);
            return;
        }

        // Divider under title (only span map area if in map mode)
        int dividerWidth = mapMode ? MAP_SIZE * cellSize + 5 : guiWidth - 20;
        renderDivider(graphics, guiLeft + 10, guiTop + 22, dividerWidth);

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

    private void renderBuyConfirmationPopup(GuiGraphics graphics) {
        int popupWidth = 220;
        int popupHeight = 120;
        int popupX = (this.width - popupWidth) / 2;
        int popupY = (this.height - popupHeight) / 2;

        // Background
        graphics.fill(popupX - 2, popupY - 2, popupX + popupWidth + 2, popupY + popupHeight + 2, 0xFF222244);
        graphics.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xDD1A1A2E);

        // Title
        graphics.drawCenteredString(this.font, "§6Confirm Purchase", this.width / 2, popupY + 8, 0xFFFFFFFF);

        // Chunk info
        String chunkText = "Chunk: (" + confirmationListing.chunkX + ", " + confirmationListing.chunkZ + ")";
        graphics.drawCenteredString(this.font, chunkText, this.width / 2, popupY + 28, 0xFFCCCCCC);

        // Price
        String priceText = "Price: §a" + formatPrice(confirmationListing.price);
        graphics.drawCenteredString(this.font, priceText, this.width / 2, popupY + 44, 0xFFFFFFFF);

        // Seller
        String sellerText = "Seller: " + (confirmationListing.isGovernment ? "§9Government" : "§e" + confirmationListing.ownerName);
        graphics.drawCenteredString(this.font, sellerText, this.width / 2, popupY + 58, 0xFFCCCCCC);
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
        graphics.drawString(this.font, "§7Value", startX + 210, startY + 3, 0xFFCCCCCC);
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
            String owner = listing.isGovernment ? "§9" + (listing.cityName.isEmpty() ? "Gov" : truncate(listing.cityName, 8)) : "§e" + truncate(listing.ownerName, 8);
            graphics.drawString(this.font, owner, startX + 70, y + 4, 0xFFFFFFFF);

            // Price
            String price = formatPrice(listing.price);
            graphics.drawString(this.font, price, startX + 140, y + 4, 0xFF00FF00);

            // Valuation (total chunk value)
            String valuation = listing.valuation > 0 ? "§e" + formatPrice(listing.valuation) : "§80";
            graphics.drawString(this.font, valuation, startX + 210, y + 4, 0xFFFFFFFF);
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
        int mapX = guiLeft + 15;
        int mapY = guiTop + 35;
        int mapPixelSize = MAP_SIZE * cellSize;

        // Map background (matches ChunkMapScreen)
        graphics.fill(mapX - 1, mapY - 1, mapX + mapPixelSize + 1, mapY + mapPixelSize + 1, 0xFF333333);

        // Render chunks
        int halfSize = MAP_SIZE / 2;
        int hoveredChunkX = Integer.MIN_VALUE;
        int hoveredChunkZ = Integer.MIN_VALUE;

        for (int dz = -halfSize; dz <= halfSize; dz++) {
            for (int dx = -halfSize; dx <= halfSize; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                int cellX = mapX + (dx + halfSize) * cellSize;
                int cellY = mapY + (dz + halfSize) * cellSize;

                long key = chunkKey(chunkX, chunkZ);
                ChunkListing listing = mapListings.get(key);

                if (terrainMode && terrainLoaded) {
                    // Render terrain
                    renderTerrainChunk(graphics, cellX, cellY, key);
                } else {
                    // Render grid mode - gray for all chunks (fill entire cell)
                    graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, 0xFF505050);
                }

                // Overlay for sale status (fill entire cell)
                if (listing != null) {
                    int overlayColor = listing.isGovernment ? 0x663366CC : 0x66CCAA33;
                    graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, overlayColor);
                }

                // Check if hovered
                if (mouseX >= cellX && mouseX < cellX + cellSize &&
                    mouseY >= cellY && mouseY < cellY + cellSize) {
                    hoveredChunkX = chunkX;
                    hoveredChunkZ = chunkZ;
                    // Hover highlight
                    graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, 0x44FFFFFF);
                }

                // Selected chunk highlight - draw border inside the cell
                if (chunkX == selectedChunkX && chunkZ == selectedChunkZ) {
                    int borderColor = 0xFFFFFF00;
                    graphics.fill(cellX, cellY, cellX + cellSize, cellY + 1, borderColor);
                    graphics.fill(cellX, cellY + cellSize - 1, cellX + cellSize, cellY + cellSize, borderColor);
                    graphics.fill(cellX, cellY, cellX + 1, cellY + cellSize, borderColor);
                    graphics.fill(cellX + cellSize - 1, cellY, cellX + cellSize, cellY + cellSize, borderColor);
                }

                // Player position marker
                if (dx == 0 && dz == 0) {
                    graphics.drawCenteredString(this.font, "§l@", cellX + cellSize / 2, cellY + cellSize / 2 - 4, 0xFFFFFFFF);
                }
            }
        }

        // Right sidebar - positioned below the terrain toggle button
        int sidebarX = mapX + mapPixelSize + 10;
        int sidebarY = mapY + 24; // Below terrain toggle button
        int sidebarWidth = guiLeft + guiWidth - sidebarX - 10;
        int sidebarHeight = mapPixelSize - 24;

        // Sidebar background
        graphics.fill(sidebarX, sidebarY, sidebarX + sidebarWidth, sidebarY + sidebarHeight, 0xAA333344);

        // Sidebar title
        graphics.drawString(this.font, "§6Selected", sidebarX + 4, sidebarY + 4, 0xFFFFFFFF);

        if (selectedListing != null) {
            int infoY = sidebarY + 18;

            // Chunk coords
            graphics.drawString(this.font, "§7Chunk:", sidebarX + 4, infoY, 0xFFAAAAAA);
            graphics.drawString(this.font, "(" + selectedListing.chunkX + "," + selectedListing.chunkZ + ")", sidebarX + 4, infoY + 10, 0xFFFFFFFF);
            infoY += 24;

            // Owner
            graphics.drawString(this.font, "§7Seller:", sidebarX + 4, infoY, 0xFFAAAAAA);
            String ownerText = selectedListing.isGovernment ? "§9" + (selectedListing.cityName.isEmpty() ? "Gov" : truncate(selectedListing.cityName, 10)) : "§e" + truncate(selectedListing.ownerName, 10);
            graphics.drawString(this.font, ownerText, sidebarX + 4, infoY + 10, 0xFFFFFFFF);
            infoY += 24;

            // Price
            graphics.drawString(this.font, "§7Price:", sidebarX + 4, infoY, 0xFFAAAAAA);
            graphics.drawString(this.font, "§a" + formatPrice(selectedListing.price), sidebarX + 4, infoY + 10, 0xFFFFFFFF);
            infoY += 24;

            // Valuation
            graphics.drawString(this.font, "§7Valuation:", sidebarX + 4, infoY, 0xFFAAAAAA);
            String valText = selectedListing.valuation > 0 ? "§e" + formatPrice(selectedListing.valuation) : "§80";
            graphics.drawString(this.font, valText, sidebarX + 4, infoY + 10, 0xFFFFFFFF);
            infoY += 28;

            // Instruction
            graphics.drawString(this.font, "§8Dbl-click", sidebarX + 4, infoY, 0xFF666666);
            graphics.drawString(this.font, "§8to buy", sidebarX + 4, infoY + 10, 0xFF666666);
        } else if (hoveredChunkX != Integer.MIN_VALUE) {
            // Show hovered chunk info
            long hoveredKey = chunkKey(hoveredChunkX, hoveredChunkZ);
            ChunkListing hoveredListing = mapListings.get(hoveredKey);

            int infoY = sidebarY + 18;
            graphics.drawString(this.font, "§7Chunk:", sidebarX + 4, infoY, 0xFFAAAAAA);
            graphics.drawString(this.font, "(" + hoveredChunkX + "," + hoveredChunkZ + ")", sidebarX + 4, infoY + 10, 0xFFFFFFFF);
            infoY += 24;

            if (hoveredListing != null) {
                graphics.drawString(this.font, "§aFor Sale", sidebarX + 4, infoY, 0xFF00FF00);
                infoY += 14;
                graphics.drawString(this.font, "§a" + formatPrice(hoveredListing.price), sidebarX + 4, infoY, 0xFFFFFFFF);
            } else {
                graphics.drawString(this.font, "§8Not for sale", sidebarX + 4, infoY, 0xFF888888);
            }
        } else {
            graphics.drawString(this.font, "§8Click a chunk", sidebarX + 4, sidebarY + 20, 0xFF888888);
            graphics.drawString(this.font, "§8to select", sidebarX + 4, sidebarY + 32, 0xFF888888);
        }

        // Legend at bottom (anchored below the map)
        int legendX = mapX;
        int mapBottom = mapY + mapPixelSize;
        int legendY = mapBottom + 4;
        graphics.drawString(this.font, "§7Legend:", legendX, legendY, 0xFFCCCCCC);
        legendY += 12;
        graphics.fill(legendX, legendY + 1, legendX + 8, legendY + 9, 0xFF3366CC);
        graphics.drawString(this.font, "§9Gov", legendX + 12, legendY, 0xFFCCCCCC);

        graphics.fill(legendX + 50, legendY + 1, legendX + 58, legendY + 9, 0xFFCCAA33);
        graphics.drawString(this.font, "§ePlayer", legendX + 62, legendY, 0xFFCCCCCC);

        // Coordinates display
        graphics.drawString(this.font, "§7Pos: §f" + playerChunkX + ", " + playerChunkZ,
                           guiLeft + guiWidth - 90, guiTop + 24, 0xFFFFFFFF);

        // Mode indicator
        String modeText = terrainMode ? "§aTerrain" : "§7Grid";
        graphics.drawString(this.font, modeText, guiLeft + 15, guiTop + 24, 0xFFFFFFFF);
    }

    private void renderTerrainChunk(GuiGraphics graphics, int cellX, int cellY, long key) {
        int[] colors = terrainCache.get(key);

        if (colors != null) {
            // Render terrain pixels - ensure we fill the entire cell (same approach as ChunkMapScreen)
            for (int tz = 0; tz < TERRAIN_RESOLUTION; tz++) {
                for (int tx = 0; tx < TERRAIN_RESOLUTION; tx++) {
                    int color = colors[tz * TERRAIN_RESOLUTION + tx];
                    // Calculate pixel bounds - ensure last pixel extends to cell edge
                    int px1 = cellX + (tx * cellSize) / TERRAIN_RESOLUTION;
                    int py1 = cellY + (tz * cellSize) / TERRAIN_RESOLUTION;
                    int px2 = cellX + ((tx + 1) * cellSize) / TERRAIN_RESOLUTION;
                    int py2 = cellY + ((tz + 1) * cellSize) / TERRAIN_RESOLUTION;
                    graphics.fill(px1, py1, px2, py2, color);
                }
            }
        } else {
            // Fallback if no terrain data - fill entire cell
            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, 0xFF505050);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showBuyConfirmation) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

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

            long currentTime = System.currentTimeMillis();
            long key = chunkKey(clickedChunkX, clickedChunkZ);
            ChunkListing clickedListing = mapListings.get(key);

            // Check for double-click
            if (clickedChunkX == lastClickChunkX && clickedChunkZ == lastClickChunkZ &&
                currentTime - lastClickTime < DOUBLE_CLICK_TIME) {
                // Double-click on a for-sale chunk - show buy confirmation
                if (clickedListing != null) {
                    showBuyConfirmation = true;
                    confirmationListing = clickedListing;
                    buildUI();
                }
                lastClickTime = 0;
                lastClickChunkX = Integer.MIN_VALUE;
                lastClickChunkZ = Integer.MIN_VALUE;
            } else {
                // Single click - select chunk
                lastClickTime = currentTime;
                lastClickChunkX = clickedChunkX;
                lastClickChunkZ = clickedChunkZ;

                selectedChunkX = clickedChunkX;
                selectedChunkZ = clickedChunkZ;
                selectedListing = clickedListing;
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
                // Open the chunk market screen via reflection (Economy mod)
                try {
                    Class<?> screenClass = Class.forName("com.statecraft.economy.client.screen.ChunkMarketScreen");
                    var constructor = screenClass.getConstructor(int.class, int.class);
                    var screen = constructor.newInstance(listing.chunkX, listing.chunkZ);
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
                return true;
            }
        }

        return false;
    }

    private void confirmPurchase() {
        if (confirmationListing != null) {
            // Send purchase request via economy mod
            try {
                Class<?> packetClass = Class.forName("com.statecraft.economy.network.packets.ChunkMarketPacket");
                Class<?> actionClass = Class.forName("com.statecraft.economy.network.packets.ChunkMarketPacket$Action");
                Object purchaseAction = java.lang.Enum.valueOf((Class<Enum>) actionClass, "PURCHASE");

                var constructor = packetClass.getConstructor(actionClass, int.class, int.class);
                var packet = constructor.newInstance(purchaseAction, confirmationListing.chunkX, confirmationListing.chunkZ);

                Class<?> networkClass = Class.forName("com.statecraft.economy.network.NetworkHandler");
                var sendMethod = networkClass.getMethod("sendToServer", Object.class);
                sendMethod.invoke(null, packet);

                if (this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(
                        Component.literal("§aPurchase request sent..."));
                }
            } catch (Exception e) {
                if (this.minecraft.player != null) {
                    this.minecraft.player.sendSystemMessage(
                        Component.literal("§cStateCraft Economy mod required for purchases"));
                }
            }
        }

        showBuyConfirmation = false;
        confirmationListing = null;
        buildUI();

        // Refresh data
        NetworkHandler.sendToServer(new RequestMarketplaceDataPacket(playerChunkX, playerChunkZ));
    }

    private void cancelPurchase() {
        showBuyConfirmation = false;
        confirmationListing = null;
        buildUI();
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
        public final double valuation;

        public ChunkListing(int chunkX, int chunkZ, String ownerName, boolean isGovernment, double price, String cityName, double valuation) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ownerName = ownerName;
            this.isGovernment = isGovernment;
            this.price = price;
            this.cityName = cityName;
            this.valuation = valuation;
        }
    }
}

