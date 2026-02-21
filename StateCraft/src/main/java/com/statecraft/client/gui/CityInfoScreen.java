package com.statecraft.client.gui;

import com.statecraft.client.FlagTextureManager;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.LeaveCitizenshipPacket;
import com.statecraft.network.packets.RequestCityDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing detailed city information and chunk management
 */
public class CityInfoScreen extends StateCraftScreen {

    private static final int FLAG_SIZE = 24;

    private final String nationName;
    private final String stateName;
    private final String cityName;

    // Data from server
    private boolean dataLoaded = false;
    private String mayorName = "Unknown";
    private int chunkCount = 0;
    private int residentCount = 0;
    private boolean isMayor = false;
    private boolean canManage = false;
    private boolean canAppoint = false;
    private String flagUrl = "";
    private List<String> residentNames = new ArrayList<>();

    public CityInfoScreen(String nationName, String stateName, String cityName) {
        super(Component.literal("City: " + cityName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.guiWidth = 320; this.guiHeight = 220;
    }

    private Button settingsButton;
    private Button mailButton;
    private Button leaveButton;
    private Button appointButton;

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestCityDetailsPacket(nationName, stateName, cityName));

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 35;
        int spacing = 39;
        int startX = guiLeft + 6;

        // Claim Chunk button
        this.addRenderableWidget(createButton(
            startX, buttonY, buttonWidth, 20,
            Component.literal("Claim"),
            btn -> claimCurrentChunk()
        ));

        // View Map button
        this.addRenderableWidget(createButton(
            startX + spacing, buttonY, buttonWidth, 20,
            Component.literal("Map"),
            btn -> openChunkMap()
        ));

        // Chunks list button
        this.addRenderableWidget(createButton(
            startX + spacing * 2, buttonY, buttonWidth + 5, 20,
            Component.literal("Chunks"),
            btn -> openChunksScreen()
        ));

        // Mailbox button - hidden until we know permissions
        mailButton = this.addRenderableWidget(createButton(
            startX + spacing * 3 + 5, buttonY, buttonWidth, 20,
            Component.literal("§eMail"),
            btn -> openMailboxScreen()
        ));
        mailButton.visible = false;

        // Settings button - hidden until we know permissions
        settingsButton = this.addRenderableWidget(createButton(
            startX + spacing * 4 + 5, buttonY, buttonWidth + 10, 20,
            Component.literal("Settings"),
            btn -> openSettingsScreen()
        ));
        settingsButton.visible = false;

        // Appoint Mayor button - hidden until we know if player is governor/leader
        appointButton = this.addRenderableWidget(createButton(
            startX + spacing * 5 + 10, buttonY, buttonWidth + 10, 20,
            Component.literal("§bAppoint"),
            btn -> openAppointScreen()
        ));
        appointButton.visible = false;

        // Leave button - hidden until we know permissions (not mayor)
        leaveButton = this.addRenderableWidget(createButton(
            startX + spacing * 5 + 10, buttonY, buttonWidth, 20,
            Component.literal("§cLeave"),
            btn -> leaveCity()
        ));
        leaveButton.visible = false;

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 42, buttonY, 36, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render flag
        renderFlag(graphics);

        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        int leftCol = guiLeft + 15;
        int rightCol = guiLeft + guiWidth / 2 + 5;
        int y = guiTop + 32;
        int lineHeight = 14;

        // Left column - City Info
        graphics.drawString(this.font, "§6City Info", leftCol, y, COLOR_PRIMARY);
        y += lineHeight;

        graphics.drawString(this.font, "§7Mayor: §f" + mayorName, leftCol + 5, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Chunks: §f" + chunkCount, leftCol + 5, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Residents: §f" + residentCount, leftCol + 5, y, COLOR_TEXT);
        y += lineHeight + 8;

        // Hierarchy breadcrumb
        graphics.drawString(this.font, "§6Location", leftCol, y, COLOR_PRIMARY);
        y += lineHeight;

        graphics.drawString(this.font, "§7Nation: §f" + nationName, leftCol + 5, y, COLOR_TEXT);
        y += 11;
        graphics.drawString(this.font, "§7State: §f" + stateName, leftCol + 5, y, COLOR_TEXT);

        // Right column - Residents
        y = guiTop + 32;
        graphics.drawString(this.font, "§6Residents", rightCol, y, COLOR_PRIMARY);
        y += lineHeight;

        if (residentNames.isEmpty()) {
            graphics.drawString(this.font, "§8No residents", rightCol + 5, y, 0xFFAAAAAA);
        } else {
            int maxShow = 5;
            for (int i = 0; i < Math.min(maxShow, residentNames.size()); i++) {
                String name = residentNames.get(i);
                String prefix = name.equals(mayorName) ? "§6★ " : "§7• ";
                graphics.drawString(this.font, prefix + "§f" + name, rightCol + 5, y, COLOR_TEXT);
                y += 11;
            }
            if (residentNames.size() > maxShow) {
                graphics.drawString(this.font, "§8  +" + (residentNames.size() - maxShow) + " more...", rightCol + 5, y, 0xFFAAAAAA);
            }
        }

        // Role indicator
        if (isMayor) {
            graphics.drawString(this.font, "§6[Mayor]", guiLeft + guiWidth - 55, guiTop + 32, COLOR_PRIMARY);
        } else if (canManage) {
            graphics.drawString(this.font, "§e[Manager]", guiLeft + guiWidth - 65, guiTop + 32, 0xFFFFAA00);
        }

        // Instructions
        int instructY = guiTop + guiHeight - 55;
        renderDivider(graphics, guiLeft + 10, instructY - 5, guiWidth - 20);
        graphics.drawCenteredString(this.font, "§8Stand in a chunk and click 'Claim Chunk' to claim it", this.width / 2, instructY, 0xFF888888);
    }

    private void claimCurrentChunk() {
        // Send command to claim current chunk for this city
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("sc chunk claim " + cityName);
            // Refresh data
            NetworkHandler.sendToServer(new RequestCityDetailsPacket(nationName, stateName, cityName));
        }
    }

    private void openChunkMap() {
        this.minecraft.setScreen(new ChunkMapScreen());
    }

    private void openChunksScreen() {
        this.minecraft.setScreen(new CityChunksScreen(nationName, stateName, cityName));
    }

    private void openSettingsScreen() {
        this.minecraft.setScreen(new CitySettingsScreen(nationName, stateName, cityName));
    }

    private void openAppointScreen() {
        this.minecraft.setScreen(new AppointScreen(nationName, stateName, cityName,
            AppointScreen.AppointType.MAYOR,
            () -> this.minecraft.setScreen(new CityInfoScreen(nationName, stateName, cityName))));
    }

    private void openMailboxScreen() {
        this.minecraft.setScreen(new GovMailboxScreen(GovMailboxScreen.EntityType.CITY, cityName, nationName + ":" + stateName));
    }

    private void leaveCity() {
        // Send leave request to server
        NetworkHandler.sendToServer(LeaveCitizenshipPacket.leaveCity(nationName, stateName, cityName));
        // Return to my cities screen
        this.minecraft.setScreen(new MyCitiesScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new MyCitiesScreen(nationName));
    }

    private void renderFlag(GuiGraphics graphics) {
        int flagX = guiLeft - FLAG_SIZE - 4;
        int flagY = guiTop - FLAG_SIZE - 4;

        // Draw flag border/background
        graphics.fill(flagX - 2, flagY - 2, flagX + FLAG_SIZE + 2, flagY + FLAG_SIZE + 2, 0xFF333333);
        graphics.fill(flagX - 1, flagY - 1, flagX + FLAG_SIZE + 1, flagY + FLAG_SIZE + 1, 0xFF1A1A2E);

        if (flagUrl != null && !flagUrl.isEmpty()) {
            ResourceLocation texture = FlagTextureManager.getInstance().getFlagTexture(flagUrl);
            if (texture != null) {
                graphics.blit(texture, flagX, flagY, 0, 0, FLAG_SIZE, FLAG_SIZE, FLAG_SIZE, FLAG_SIZE);
            } else if (FlagTextureManager.getInstance().isLoading(flagUrl)) {
                graphics.drawCenteredString(this.font, "...", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF888888);
            } else {
                graphics.drawCenteredString(this.font, "?", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF666666);
            }
        } else {
            graphics.drawCenteredString(this.font, "C", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF4A90D9);
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String mayorName, int chunkCount, int residentCount,
                           boolean isMayor, boolean canManage, List<String> residentNames, String flagUrl) {
        updateData(mayorName, chunkCount, residentCount, isMayor, canManage, residentNames, flagUrl, false);
    }

    public void updateData(String mayorName, int chunkCount, int residentCount,
                           boolean isMayor, boolean canManage, List<String> residentNames, boolean canAppoint) {
        updateData(mayorName, chunkCount, residentCount, isMayor, canManage, residentNames, "", canAppoint);
    }

    public void updateData(String mayorName, int chunkCount, int residentCount,
                           boolean isMayor, boolean canManage, List<String> residentNames, String flagUrl,
                           boolean canAppoint) {
        this.mayorName = mayorName;
        this.chunkCount = chunkCount;
        this.residentCount = residentCount;
        this.isMayor = isMayor;
        this.canManage = canManage;
        this.residentNames = residentNames;
        this.flagUrl = flagUrl != null ? flagUrl : "";
        this.canAppoint = canAppoint;
        this.dataLoaded = true;

        // Show settings and mail buttons only for mayors/managers
        if (settingsButton != null) {
            settingsButton.visible = canManage;
        }
        if (mailButton != null) {
            mailButton.visible = canManage;
        }
        // Show appoint button for governors/leaders (they can appoint mayors)
        if (appointButton != null) {
            appointButton.visible = canAppoint;
        }
        // Show leave button for non-mayors (but not if appoint is shown in same slot)
        if (leaveButton != null) {
            leaveButton.visible = !isMayor && !canAppoint;
        }
    }

    // Overload for backward compatibility
    public void updateData(String mayorName, int chunkCount, int residentCount,
                           boolean isMayor, boolean canManage, List<String> residentNames) {
        updateData(mayorName, chunkCount, residentCount, isMayor, canManage, residentNames, "", false);
    }
}

