package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestChunkInfoPacket;
import com.statecraft.network.packets.RequestNationDataPacket;
import com.statecraft.network.packets.ToggleAutoClaimPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Screen for managing chunk claims
 */
public class ClaimsManagementScreen extends StateCraftScreen {

    private String nationName = null;
    private String stateName = null;
    private String cityName = null;
    private boolean dataLoaded = false;

    // Current chunk info
    private int currentChunkX = 0;
    private int currentChunkZ = 0;
    private String chunkOwnershipType = ""; // HIERARCHY, PLAYER, UNCLAIMED
    private String chunkOwnerName = "";
    private String chunkNationName = "";
    private String chunkStateName = "";
    private String chunkCityName = "";
    private boolean chunkDataLoaded = false;

    // Auto-claim state (synced from server)
    private boolean autoClaimEnabled = false;
    private boolean canUseAutoClaim = false;
    private String autoClaimCityName = null;

    private Button autoClaimButton;

    public ClaimsManagementScreen() {
        super(Component.literal("Manage Claims"));
        this.guiWidth = 280; this.guiHeight = 300;
    }

    @Override
    protected void init() {
        super.init();

        // Request nation data to get context
        NetworkHandler.sendToServer(new RequestNationDataPacket());

        // Get current chunk coordinates and request chunk info
        if (this.minecraft != null && this.minecraft.player != null) {
            currentChunkX = this.minecraft.player.chunkPosition().x;
            currentChunkZ = this.minecraft.player.chunkPosition().z;
            NetworkHandler.sendToServer(new RequestChunkInfoPacket(currentChunkX, currentChunkZ));
        }

        int centerX = this.width / 2;
        int buttonWidth = 120;
        int startY = guiTop + 130; // Moved down to make room for chunk info
        int spacing = 24;

        // Chunk Info button - shows current chunk details and permits
        this.addRenderableWidget(createButton(
            centerX - buttonWidth / 2, startY,
            buttonWidth, 20,
            Component.literal("Chunk Info"),
            btn -> openChunkInfo()
        ));

        // Claim current chunk
        this.addRenderableWidget(createButton(
            centerX - buttonWidth / 2, startY + spacing,
            buttonWidth, 20,
            Component.literal("Claim This Chunk"),
            btn -> claimCurrentChunk()
        ));

        // Unclaim current chunk
        this.addRenderableWidget(createButton(
            centerX - buttonWidth / 2, startY + spacing * 2,
            buttonWidth, 20,
            Component.literal("Unclaim This Chunk"),
            btn -> unclaimCurrentChunk()
        ));

        // View chunk map
        this.addRenderableWidget(createButton(
            centerX - buttonWidth / 2, startY + spacing * 3,
            buttonWidth, 20,
            Component.literal("View Chunk Map"),
            btn -> openChunkMap()
        ));

        // Auto-claim toggle (admin only)
        autoClaimButton = this.addRenderableWidget(createButton(
            centerX - buttonWidth / 2, startY + spacing * 4,
            buttonWidth, 20,
            Component.literal("Auto-Claim: OFF"),
            btn -> toggleAutoClaim()
        ));
        autoClaimButton.active = false; // Disabled until we know if player can use it

        // Back button
        this.addRenderableWidget(createButton(
            centerX - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Divider under title
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int centerX = this.width / 2;
        int y = guiTop + 30;
        int leftCol = guiLeft + 15;

        // === Current Chunk Section ===
        graphics.drawString(this.font, "§6Current Chunk", leftCol, y, COLOR_PRIMARY);
        y += 14;
        graphics.drawString(this.font, "§7Coordinates: §f" + currentChunkX + ", " + currentChunkZ, leftCol + 10, y, COLOR_TEXT);
        y += 14;

        // Chunk ownership
        if (chunkDataLoaded) {
            if (chunkOwnershipType.equals("UNCLAIMED")) {
                graphics.drawString(this.font, "§7Status: §8Wilderness (Unclaimed)", leftCol + 10, y, COLOR_TEXT);
            } else if (chunkOwnershipType.equals("PLAYER")) {
                graphics.drawString(this.font, "§7Status: §ePrivate Property", leftCol + 10, y, COLOR_TEXT);
                y += 12;
                graphics.drawString(this.font, "§7Owner: §f" + chunkOwnerName, leftCol + 10, y, COLOR_TEXT);
            } else {
                // HIERARCHY - government owned
                graphics.drawString(this.font, "§7Status: §aGovernment Land", leftCol + 10, y, COLOR_TEXT);
                y += 12;
                if (!chunkCityName.isEmpty()) {
                    graphics.drawString(this.font, "§7Claimed by: §a" + chunkCityName, leftCol + 10, y, COLOR_TEXT);
                } else if (!chunkNationName.isEmpty()) {
                    graphics.drawString(this.font, "§7Claimed by: §e" + chunkNationName, leftCol + 10, y, COLOR_TEXT);
                }
            }
        } else {
            graphics.drawString(this.font, "§7Status: §8Loading...", leftCol + 10, y, COLOR_TEXT);
        }

        // Divider before your nation context
        y += 20;
        renderDivider(graphics, guiLeft + 10, y - 4, guiWidth - 20);

        // === Your Nation Context Section ===
        graphics.drawString(this.font, "§6Your Nation", leftCol, y, COLOR_PRIMARY);
        y += 14;

        if (dataLoaded && nationName != null) {
            // Show current context
            graphics.drawString(this.font, "§7Nation: §e" + nationName, leftCol + 10, y, COLOR_TEXT);
            y += 12;

            if (stateName != null && !stateName.isEmpty()) {
                graphics.drawString(this.font, "§7State: §f" + stateName, leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }

            if (cityName != null && !cityName.isEmpty()) {
                graphics.drawString(this.font, "§7City: §a" + cityName, leftCol + 10, y, COLOR_TEXT);
            } else {
                graphics.drawString(this.font, "§7City: §cNone (join a city first)", leftCol + 10, y, COLOR_TEXT);
            }
        } else if (dataLoaded) {
            graphics.drawString(this.font, "§cYou are not in a nation", leftCol + 10, y, 0xFFFF5555);
        } else {
            graphics.drawString(this.font, "§7Loading...", leftCol + 10, y, COLOR_TEXT);
        }

        // Help text at bottom
        renderDivider(graphics, guiLeft + 10, guiTop + guiHeight - 48, guiWidth - 20);

        if (!canUseAutoClaim && dataLoaded && nationName != null) {
            graphics.drawCenteredString(this.font, "§8Auto-claim requires nation admin", centerX, guiTop + guiHeight - 42, 0xFF666666);
        } else {
            graphics.drawCenteredString(this.font, "§8Stand in the chunk you want to claim/unclaim", centerX, guiTop + guiHeight - 42, 0xFF666666);
        }
    }

    private void claimCurrentChunk() {
        if (this.minecraft != null && this.minecraft.player != null) {
            if (cityName != null && !cityName.isEmpty()) {
                // greedyString handles spaces without quotes
                this.minecraft.player.connection.sendCommand("sc chunk claim " + cityName);
            } else {
                this.minecraft.player.connection.sendCommand("sc chunk claim");
            }
        }
    }

    private void unclaimCurrentChunk() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("sc chunk unclaim");
        }
    }

    private void openChunkMap() {
        this.minecraft.setScreen(new ChunkMapScreen());
    }

    private void openChunkInfo() {
        if (this.minecraft != null && this.minecraft.player != null) {
            int chunkX = this.minecraft.player.chunkPosition().x;
            int chunkZ = this.minecraft.player.chunkPosition().z;
            this.minecraft.setScreen(new ChunkInfoScreen(chunkX, chunkZ));
        }
    }

    private void toggleAutoClaim() {
        // Send packet to server to toggle auto-claim
        NetworkHandler.sendToServer(new ToggleAutoClaimPacket(!autoClaimEnabled, cityName));
    }

    private void updateAutoClaimButton() {
        if (autoClaimButton == null) return;

        autoClaimButton.active = canUseAutoClaim;

        if (autoClaimEnabled) {
            String cityText = autoClaimCityName != null ? " (" + autoClaimCityName + ")" : "";
            autoClaimButton.setMessage(Component.literal("Auto-Claim: §aON" + cityText));
        } else {
            if (!canUseAutoClaim) {
                autoClaimButton.setMessage(Component.literal("§8Auto-Claim: §7Admin Only"));
            } else {
                autoClaimButton.setMessage(Component.literal("Auto-Claim: §cOFF"));
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

    // Called by network handler to update context
    public void updateContext(String nationName, String stateName, String cityName) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.dataLoaded = true;
    }

    public void setNoNation() {
        this.dataLoaded = true;
        this.nationName = null;
        this.canUseAutoClaim = false;
        updateAutoClaimButton();
    }

    // Called by network handler when auto-claim state is synced
    public void updateAutoClaimState(boolean enabled, boolean canUse, String cityName) {
        this.autoClaimEnabled = enabled;
        this.canUseAutoClaim = canUse;
        this.autoClaimCityName = cityName;
        updateAutoClaimButton();
    }

    // Called by network handler to update chunk info
    public void updateChunkInfo(String ownershipType, String ownerName, String nationName, String stateName, String cityName) {
        this.chunkOwnershipType = ownershipType;
        this.chunkOwnerName = ownerName;
        this.chunkNationName = nationName;
        this.chunkStateName = stateName;
        this.chunkCityName = cityName;
        this.chunkDataLoaded = true;
    }
}

