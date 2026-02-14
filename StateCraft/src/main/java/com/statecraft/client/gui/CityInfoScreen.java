package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestCityDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing detailed city information and chunk management
 */
public class CityInfoScreen extends StateCraftScreen {

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
    private List<String> residentNames = new ArrayList<>();

    public CityInfoScreen(String nationName, String stateName, String cityName) {
        super(Component.literal("City: " + cityName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.cityName = cityName;
        this.guiWidth = 280; this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestCityDetailsPacket(nationName, stateName, cityName));

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 75;

        // Claim Chunk button
        Button claimBtn = this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, buttonWidth, 20,
            Component.literal("Claim Chunk"),
            btn -> claimCurrentChunk()
        ));

        // View Map button
        this.addRenderableWidget(createButton(
            guiLeft + 15 + buttonWidth + 5, buttonY, 60, 20,
            Component.literal("Map"),
            btn -> openChunkMap()
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 75, buttonY, 60, 20,
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

    private void goBack() {
        this.minecraft.setScreen(new CitiesListScreen(nationName, stateName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String mayorName, int chunkCount, int residentCount,
                           boolean isMayor, boolean canManage, List<String> residentNames) {
        this.mayorName = mayorName;
        this.chunkCount = chunkCount;
        this.residentCount = residentCount;
        this.isMayor = isMayor;
        this.canManage = canManage;
        this.residentNames = residentNames;
        this.dataLoaded = true;
    }
}

