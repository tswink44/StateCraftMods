package com.statecraft.client.gui;

import com.statecraft.StateCraft;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Main menu screen for StateCraft - Central Hub
 * Provides navigation to all other screens with a clean button layout
 */
public class MainMenuScreen extends StateCraftScreen {

    private static final ResourceLocation ARROW_ICON = new ResourceLocation(StateCraft.MOD_ID, "textures/gui/arrow_icon.png");

    // Cached data from server
    private String nationName = null;
    private String stateName = null;
    private String cityName = null;
    private int totalChunks = 0;
    private int totalMembers = 0;
    private boolean isLeader = false;
    private boolean isAdmin = false;
    private boolean dataLoaded = false;
    private boolean inNation = false;

    // Menu buttons
    private Button profileButton;
    private Button mailboxButton;
    private Button companyButton;
    private Button chunkButton;
    private Button cityButton;
    private Button stateButton;
    private Button nationButton;
    private Button mapButton;
    private Button invitesButton;

    public MainMenuScreen() {
        super(Component.literal("StateCraft Menu"));
        this.guiWidth = 300; this.guiHeight = 280;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = guiWidth - 40;
        int buttonHeight = 20;
        int startX = guiLeft + 20;
        int startY = guiTop + 30;
        int spacing = 24;

        // Request data from server
        requestData();

        // === Personal Section ===
        // My Profile button
        profileButton = this.addRenderableWidget(createMenuButton(
            startX, startY,
            buttonWidth, buttonHeight,
            "My Profile",
            btn -> openProfileScreen()
        ));

        // Mailbox (Invitations)
        mailboxButton = this.addRenderableWidget(createMenuButton(
            startX, startY + spacing,
            buttonWidth, buttonHeight,
            "My Mail",
            btn -> openInvitesScreen()
        ));

        // My Company (placeholder for future)
        companyButton = this.addRenderableWidget(createMenuButton(
            startX, startY + spacing * 2,
            buttonWidth, buttonHeight,
            "My Company",
            btn -> {} // Placeholder
        ));
        companyButton.active = false; // Not implemented yet

        // === Territory Section ===
        int sectionY = startY + spacing * 3 + 10;

        // Chunk Info
        chunkButton = this.addRenderableWidget(createMenuButton(
            startX, sectionY,
            buttonWidth, buttonHeight,
            "Chunk",
            btn -> openChunkScreen()
        ));

        // City Info
        cityButton = this.addRenderableWidget(createMenuButton(
            startX, sectionY + spacing,
            buttonWidth, buttonHeight,
            "City",
            btn -> openCityScreen()
        ));

        // State Info
        stateButton = this.addRenderableWidget(createMenuButton(
            startX, sectionY + spacing * 2,
            buttonWidth, buttonHeight,
            "State",
            btn -> openStateScreen()
        ));

        // Nation Info
        nationButton = this.addRenderableWidget(createMenuButton(
            startX, sectionY + spacing * 3,
            buttonWidth, buttonHeight,
            "Nation",
            btn -> openNationScreen()
        ));

        // === Additional Tools ===
        int toolsY = sectionY + spacing * 4 + 10;

        // Chunk Map
        mapButton = this.addRenderableWidget(createMenuButton(
            startX, toolsY,
            buttonWidth, buttonHeight,
            "Chunk Map",
            btn -> openMapScreen()
        ));

        // Close button
        this.addRenderableWidget(createButton(
            this.width / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Close"),
            btn -> this.onClose()
        ));
    }

    /**
     * Create a styled menu button with consistent appearance
     */
    private Button createMenuButton(int x, int y, int width, int height, String text, Button.OnPress onPress) {
        return Button.builder(Component.literal(text), onPress)
            .pos(x, y)
            .size(width, height)
            .build();
    }

    private void requestData() {
        // Request current player's nation data from server
        NetworkHandler.sendToServer(new RequestNationDataPacket());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render section headers
        int leftMargin = guiLeft + 15;
        int startY = guiTop + 30;
        int spacing = 24;

        // Divider under title
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Personal section label (rendered above the buttons area)
        // The buttons themselves serve as the interface

        // Territory section divider
        int sectionY = startY + spacing * 3 + 5;
        renderDivider(graphics, guiLeft + 10, sectionY - 4, guiWidth - 20);

        // Tools section divider
        int toolsY = sectionY + spacing * 4 + 5;
        renderDivider(graphics, guiLeft + 10, toolsY - 4, guiWidth - 20);

        // Update button labels based on context
        updateButtonLabels();
    }

    private void updateButtonLabels() {
        if (dataLoaded) {
            // Update city button with city name if in one
            if (cityName != null && !cityName.isEmpty()) {
                cityButton.setMessage(Component.literal("City: " + cityName));
                cityButton.active = true;
            } else {
                cityButton.setMessage(Component.literal("City (none)"));
                cityButton.active = false;
            }

            // Update state button with state name if in one
            if (stateName != null && !stateName.isEmpty()) {
                stateButton.setMessage(Component.literal("State: " + stateName));
                stateButton.active = true;
            } else {
                stateButton.setMessage(Component.literal("State (none)"));
                stateButton.active = false;
            }

            // Update nation button with nation name if in one
            if (inNation && nationName != null && !nationName.isEmpty()) {
                nationButton.setMessage(Component.literal("Nation: " + nationName));
                nationButton.active = true;
            } else {
                nationButton.setMessage(Component.literal("Nation (none)"));
                // Allow opening to create nation
                nationButton.active = true;
            }
        }
    }

    private void openProfileScreen() {
        // TODO: Implement profile screen
        // For now, show player stats/info
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§7Profile screen coming soon!"));
        }
    }

    private void openNationScreen() {
        if (inNation && nationName != null) {
            this.minecraft.setScreen(new NationInfoScreen(nationName));
        } else {
            this.minecraft.setScreen(new CreateNationScreen());
        }
    }

    private void openStateScreen() {
        if (stateName != null && !stateName.isEmpty() && nationName != null) {
            this.minecraft.setScreen(new StateInfoScreen(nationName, stateName));
        }
    }

    private void openCityScreen() {
        if (cityName != null && !cityName.isEmpty() && nationName != null && stateName != null) {
            this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, cityName));
        }
    }

    private void openChunkScreen() {
        if (this.minecraft != null && this.minecraft.player != null) {
            int chunkX = this.minecraft.player.chunkPosition().x;
            int chunkZ = this.minecraft.player.chunkPosition().z;
            this.minecraft.setScreen(new ChunkInfoScreen(chunkX, chunkZ));
        }
    }

    private void openMapScreen() {
        this.minecraft.setScreen(new ChunkMapScreen());
    }

    private void openInvitesScreen() {
        this.minecraft.setScreen(new InvitationsScreen());
    }

    // Called by network handler when data is received
    public void updateNationData(String nationName, String stateName, String cityName,
                                  int chunks, int members, boolean leader, boolean admin) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.totalChunks = chunks;
        this.totalMembers = members;
        this.isLeader = leader;
        this.isAdmin = admin;
        this.inNation = nationName != null && !nationName.isEmpty();
        this.dataLoaded = true;
    }

    /**
     * Update context from chunk data (when player is in claimed chunk but may not be in nation)
     */
    public void updateContext(String nationName, String stateName, String cityName) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.inNation = nationName != null && !nationName.isEmpty();
        this.dataLoaded = true;
    }

    // Called if player is not in a nation
    public void setNoNation() {
        this.inNation = false;
        this.dataLoaded = true;
    }
}

