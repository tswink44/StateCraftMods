package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.AbandonChunkPacket;
import com.statecraft.network.packets.RequestChunkInfoPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Screen showing detailed information about the current chunk
 * Shows ownership, permits, and hierarchy (City -> State -> Nation)
 */
public class ChunkInfoScreen extends StateCraftScreen {

    private final int chunkX;
    private final int chunkZ;

    // Data from server
    private boolean dataLoaded = false;
    private String ownershipType = ""; // HIERARCHY, PLAYER, UNCLAIMED
    private String ownerName = "";
    private String nationName = "";
    private String stateName = "";
    private String cityName = "";
    private boolean canManagePermits = false;
    private boolean isOwner = false; // True if current player owns this chunk
    private List<String> permitHolders = new ArrayList<>();

    private Button abandonButton;

    // Scrolling
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private static final int SCROLL_AREA_HEIGHT = 138;
    private static final int LINE_HEIGHT = 12;

    public ChunkInfoScreen(int chunkX, int chunkZ) {
        super(Component.literal("Chunk Info"));
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.guiWidth = 260; this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        // Request chunk data from server
        NetworkHandler.sendToServer(new RequestChunkInfoPacket(chunkX, chunkZ));

        int centerX = this.width / 2;
        int buttonY = guiTop + guiHeight - 70;
        int buttonWidth = 75;
        int spacing = 80;

        // Row 1: Permits | Claims | Buy/Sell
        // Manage Permits button
        this.addRenderableWidget(createButton(
            guiLeft + 10, buttonY,
            buttonWidth, 18,
            Component.literal("Permits"),
            btn -> openPermitsScreen()
        ));

        // Manage Claims button
        this.addRenderableWidget(createButton(
            guiLeft + 10 + buttonWidth + 5, buttonY,
            buttonWidth, 18,
            Component.literal("Claims"),
            btn -> openClaimsScreen()
        ));

        // Buy/Sell button - opens chunk market screen
        this.addRenderableWidget(createButton(
            guiLeft + 10 + (buttonWidth + 5) * 2, buttonY,
            buttonWidth, 18,
            Component.literal("Buy/Sell"),
            btn -> openMarketScreen()
        ));

        // Row 2: Valuation (left) | Abandon Chunk (right) - side by side, smaller
        int halfWidth = (guiWidth - 25) / 2;

        // Valuation button (only if Economy mod is loaded)
        this.addRenderableWidget(createButton(
            guiLeft + 10, buttonY + 22,
            halfWidth, 18,
            Component.literal("§e$ Valuation"),
            btn -> openValuationScreen()
        ));

        // Abandon button (only visible if player owns the chunk)
        abandonButton = this.addRenderableWidget(createButton(
            guiLeft + 15 + halfWidth, buttonY + 22,
            halfWidth, 18,
            Component.literal("§cAbandon"),
            btn -> abandonChunk()
        ));
        abandonButton.visible = false; // Hidden until we know if player owns it

        // Back button
        this.addRenderableWidget(createButton(
            centerX - 40, guiTop + guiHeight - 25,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        int scrollAreaTop = guiTop + 28;
        int scrollAreaBottom = scrollAreaTop + SCROLL_AREA_HEIGHT;
        int leftCol = guiLeft + 15;

        // Enable scissoring to clip content to scroll area
        graphics.enableScissor(guiLeft + 5, scrollAreaTop, guiLeft + guiWidth - 5, scrollAreaBottom);

        int y = scrollAreaTop - (scrollOffset * LINE_HEIGHT);

        // Chunk coordinates
        graphics.drawString(this.font, "§6Chunk Coordinates", leftCol, y, COLOR_PRIMARY);
        y += 14;
        graphics.drawString(this.font, "§7X: §f" + chunkX + "  §7Z: §f" + chunkZ, leftCol + 10, y, COLOR_TEXT);
        y += 16;

        // Ownership section
        graphics.drawString(this.font, "§6Ownership", leftCol, y, COLOR_PRIMARY);
        y += 14;

        if (ownershipType.equals("UNCLAIMED")) {
            graphics.drawString(this.font, "§8Wilderness (Unclaimed)", leftCol + 10, y, 0xFF888888);
            y += 12;
        } else if (ownershipType.equals("PLAYER")) {
            graphics.drawString(this.font, "§7Type: §ePrivate", leftCol + 10, y, COLOR_TEXT);
            y += 12;
            graphics.drawString(this.font, "§7Owner: §f" + ownerName, leftCol + 10, y, COLOR_TEXT);
            y += 12;
        } else {
            graphics.drawString(this.font, "§7Type: §aGovernment", leftCol + 10, y, COLOR_TEXT);
            y += 12;
            if (!cityName.isEmpty()) {
                graphics.drawString(this.font, "§7City: §f" + cityName, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }
        }
        y += 6;

        // Hierarchy section (if in a nation)
        if (!nationName.isEmpty()) {
            renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
            y += 4;
            graphics.drawString(this.font, "§6Territory Hierarchy", leftCol, y, COLOR_PRIMARY);
            y += 14;

            graphics.drawString(this.font, "§7Nation: §e" + nationName, leftCol + 10, y, COLOR_TEXT);
            y += 12;

            if (!stateName.isEmpty()) {
                graphics.drawString(this.font, "§7State: §f" + stateName, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }

            if (!cityName.isEmpty()) {
                graphics.drawString(this.font, "§7City: §a" + cityName, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }
        }

        // Permits section
        y += 6;
        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 4;
        graphics.drawString(this.font, "§6Building Permits", leftCol, y, COLOR_PRIMARY);
        y += 14;

        if (permitHolders.isEmpty()) {
            graphics.drawString(this.font, "§8No permits granted", leftCol + 10, y, 0xFF888888);
            y += 12;
        } else {
            for (String permitHolder : permitHolders) {
                graphics.drawString(this.font, "§7• §f" + permitHolder, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }
        }

        // Calculate content height for scroll bounds
        contentHeight = y - (scrollAreaTop - (scrollOffset * LINE_HEIGHT));

        graphics.disableScissor();

        // Scroll indicators
        int maxScroll = getMaxScroll();
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 15, scrollAreaTop + 2, 0xFF888888);
        }
        if (scrollOffset < maxScroll) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 15, scrollAreaBottom - 10, 0xFF888888);
        }
    }

    private int getMaxScroll() {
        int overflow = contentHeight - SCROLL_AREA_HEIGHT;
        if (overflow <= 0) return 0;
        return (overflow + LINE_HEIGHT - 1) / LINE_HEIGHT; // ceiling division
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = getMaxScroll();
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void openPermitsScreen() {
        this.minecraft.setScreen(new ChunkPermitsScreen(chunkX, chunkZ));
    }

    private void openClaimsScreen() {
        this.minecraft.setScreen(new ClaimsManagementScreen());
    }

    private void openValuationScreen() {
        this.minecraft.setScreen(new ChunkValuationScreen(chunkX, chunkZ));
    }

    private void abandonChunk() {
        // Send abandon request to server
        NetworkHandler.sendToServer(new AbandonChunkPacket(chunkX, chunkZ));
        // Return to main menu (the chunk info will be stale)
        this.minecraft.setScreen(new MainMenuScreen());
    }

    private void openMarketScreen() {
        // Open the chunk market screen from StateCraftEconomy mod
        // Uses reflection to avoid hard dependency
        try {
            Class<?> screenClass = Class.forName("com.statecraft.economy.client.screen.ChunkMarketScreen");
            var constructor = screenClass.getConstructor(int.class, int.class);
            var screen = constructor.newInstance(chunkX, chunkZ);
            this.minecraft.setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (ClassNotFoundException e) {
            // Economy mod not loaded
            if (this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§cStateCraft Economy mod required for chunk trading"));
            }
        } catch (Exception e) {
            if (this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§cError opening market screen"));
            }
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String ownershipType, String ownerName, String nationName,
                           String stateName, String cityName, boolean canManagePermits,
                           List<String> permitHolders) {
        updateData(ownershipType, ownerName, nationName, stateName, cityName, canManagePermits, permitHolders, false);
    }

    // Overload with isOwner parameter
    public void updateData(String ownershipType, String ownerName, String nationName,
                           String stateName, String cityName, boolean canManagePermits,
                           List<String> permitHolders, boolean isOwner) {
        this.ownershipType = ownershipType;
        this.ownerName = ownerName;
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.canManagePermits = canManagePermits;
        this.permitHolders = new ArrayList<>(permitHolders);
        this.isOwner = isOwner;
        this.dataLoaded = true;

        // Show abandon button only if player owns this chunk
        if (abandonButton != null) {
            abandonButton.visible = isOwner;
        }
    }
}

