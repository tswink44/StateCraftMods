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
    private boolean isOfficer = false;
    private boolean dataLoaded = false;
    private boolean inNation = false;

    // Menu buttons
    private Button profileButton;
    private Button mailboxButton;
    private Button companyButton;
    private Button chunkButton;
    private Button marketplaceButton;
    private Button cityButton;
    private Button stateButton;
    private Button nationButton;
    private Button mapButton;
    private Button invitesButton;

    // Compact menu items (for custom rendering)
    private List<MenuEntry> menuEntries = new ArrayList<>();

    public MainMenuScreen() {
        super(Component.literal("StateCraft"));
        this.guiWidth = 260;
        this.guiHeight = 218;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false; // We render our own title in renderContent
    }

    @Override
    protected void init() {
        super.init();

        // Compact dimensions
        int buttonWidth = guiWidth - 50;  // Leave room for arrow button
        int arrowBtnWidth = 22;
        int buttonHeight = 14;
        int startX = guiLeft + 10;
        int arrowX = guiLeft + guiWidth - 32;
        int startY = guiTop + 24;
        int spacing = 18;

        // Request data from server
        requestData();

        menuEntries.clear();

        // === Personal Section ===
        int row = 0;

        // My Profile
        menuEntries.add(new MenuEntry("My Profile", startY + spacing * row, true));
        profileButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openProfileScreen()
        ));
        row++;

        // My Mail
        menuEntries.add(new MenuEntry("My Mail", startY + spacing * row, true));
        mailboxButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openMailboxScreen()
        ));
        row++;

        // My Company
        menuEntries.add(new MenuEntry("My Company", startY + spacing * row, true));
        companyButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openCompanyScreen()
        ));
        row++;

        // === Territory Section (with gap) ===
        row++; // Add a small gap

        // Chunk
        menuEntries.add(new MenuEntry("Chunk", startY + spacing * row, true));
        chunkButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openChunkScreen()
        ));
        row++;

        // Marketplace
        menuEntries.add(new MenuEntry("\u2692 Marketplace", startY + spacing * row, true));
        marketplaceButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openMarketplaceScreen()
        ));
        row++;

        // City
        menuEntries.add(new MenuEntry("City", startY + spacing * row, true));
        cityButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openCityScreen()
        ));
        row++;

        // State
        menuEntries.add(new MenuEntry("State", startY + spacing * row, true));
        stateButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openStateScreen()
        ));
        row++;

        // Nation
        menuEntries.add(new MenuEntry("Nation", startY + spacing * row, true));
        nationButton = this.addRenderableWidget(createCompactArrowButton(
            arrowX, startY + spacing * row, arrowBtnWidth, buttonHeight,
            btn -> openNationScreen()
        ));
        row++;
    }

    /**
     * Create a compact arrow button (small square with arrow icon)
     */
    private Button createCompactArrowButton(int x, int y, int width, int height, Button.OnPress onPress) {
        return Button.builder(Component.literal("→"), onPress)
            .pos(x, y)
            .size(width, height)
            .build();
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
        int startX = guiLeft + 10;
        int spacing = 18;

        // Render each menu entry as a row with gray background
        for (MenuEntry entry : menuEntries) {
            // Row background
            int rowColor = entry.active ? 0xAA808080 : 0xAA606060;
            graphics.fill(startX, entry.y, guiLeft + guiWidth - 10, entry.y + 14, rowColor);

            // Border around row
            graphics.fill(startX, entry.y, guiLeft + guiWidth - 10, entry.y + 1, 0xFF505050);
            graphics.fill(startX, entry.y + 13, guiLeft + guiWidth - 10, entry.y + 14, 0xFF404040);

            // Text (left-aligned)
            int textColor = entry.active ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawString(this.font, entry.text, startX + 4, entry.y + 3, textColor);
        }

        // Render title row with special styling
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, guiLeft + guiWidth - 10, guiTop + 20, 0xFF404040);
        graphics.drawString(this.font, "StateCraft", startX + 4, guiTop + 9, 0xFFFFFFFF);

        // Title icon placeholder (SC box)
        int iconX = guiLeft + guiWidth - 32;
        int iconY = guiTop + 6;
        graphics.fill(iconX, iconY, iconX + 22, iconY + 14, 0xFF1E90FF);
        graphics.drawCenteredString(this.font, "SC", iconX + 11, iconY + 3, 0xFFFFFFFF);
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
        this.minecraft.setScreen(new ProfileScreen());
    }

    private void openCompanyScreen() {
        this.minecraft.setScreen(new CompanyListScreen());
    }

    private void openNationScreen() {
        // Open the NationsListScreen which shows all nations
        this.minecraft.setScreen(new NationsListScreen());
    }

    private void openStateScreen() {
        if (nationName == null || nationName.isEmpty()) {
            showMessage("§cYou are not part of a nation.");
            return;
        }
        // Open the MyStatesScreen which lists all states the player is a citizen of
        this.minecraft.setScreen(new MyStatesScreen(nationName));
    }

    private void openCityScreen() {
        if (nationName == null || nationName.isEmpty()) {
            showMessage("§cYou are not part of a nation.");
            return;
        }
        // Open the MyCitiesScreen which lists all cities the player is a resident of
        this.minecraft.setScreen(new MyCitiesScreen(nationName));
    }

    private void showMessage(String message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    private void openChunkScreen() {
        if (this.minecraft != null && this.minecraft.player != null) {
            int chunkX = this.minecraft.player.chunkPosition().x;
            int chunkZ = this.minecraft.player.chunkPosition().z;
            this.minecraft.setScreen(new ChunkInfoScreen(chunkX, chunkZ));
        }
    }

    private void openMarketplaceScreen() {
        this.minecraft.setScreen(new ChunkMarketplaceScreen());
    }

    private void openMapScreen() {
        this.minecraft.setScreen(new ChunkMapScreen());
    }

    private void openInvitesScreen() {
        this.minecraft.setScreen(new InvitationsScreen());
    }

    private void openMailboxScreen() {
        this.minecraft.setScreen(new MailInboxScreen());
    }

    // Called by network handler when data is received
    public void updateNationData(String nationName, String stateName, String cityName,
                                  int chunks, int members, boolean leader, boolean officer) {
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.totalChunks = chunks;
        this.totalMembers = members;
        this.isLeader = leader;
        this.isOfficer = officer;
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

    /**
     * Helper class for menu entries
     */
    private static class MenuEntry {
        final String text;
        final int y;
        final boolean active;

        MenuEntry(String text, int y, boolean active) {
            this.text = text;
            this.y = y;
            this.active = active;
        }
    }
}

