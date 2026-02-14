package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestStateDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing detailed state information
 */
public class StateInfoScreen extends StateCraftScreen {

    private final String nationName;
    private final String stateName;

    // Data from server
    private boolean dataLoaded = false;
    private String governorName = "Unknown";
    private int cityCount = 0;
    private int chunkCount = 0;
    private int memberCount = 0;
    private boolean isGovernor = false;
    private boolean canManage = false;
    private List<String> cityNames = new ArrayList<>();

    public StateInfoScreen(String nationName, String stateName) {
        super(Component.literal("State: " + stateName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.guiWidth = 280; this.guiHeight = 200;
    }

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestStateDetailsPacket(nationName, stateName));

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 70;
        int spacing = 75;
        int startX = guiLeft + 15;

        // Cities button
        this.addRenderableWidget(createButton(
            startX, buttonY, buttonWidth, 20,
            Component.literal("Cities"),
            btn -> openCitiesScreen()
        ));

        // Create City button
        Button createCityBtn = this.addRenderableWidget(createButton(
            startX + spacing, buttonY, buttonWidth, 20,
            Component.literal("New City"),
            btn -> openCreateCityScreen()
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
        int y = guiTop + 32;
        int lineHeight = 14;

        // Governor info
        graphics.drawString(this.font, "§6Governor", leftCol, y, COLOR_PRIMARY);
        y += lineHeight;
        graphics.drawString(this.font, "§7" + governorName, leftCol + 10, y, COLOR_TEXT);
        y += lineHeight + 6;

        // Statistics
        graphics.drawString(this.font, "§6Statistics", leftCol, y, COLOR_PRIMARY);
        y += lineHeight;

        graphics.drawString(this.font, "§7Cities: §f" + cityCount, leftCol + 10, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Chunks: §f" + chunkCount, leftCol + 10, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Residents: §f" + memberCount, leftCol + 10, y, COLOR_TEXT);
        y += lineHeight + 6;

        // Cities list
        graphics.drawString(this.font, "§6Cities", leftCol, y, COLOR_PRIMARY);
        y += lineHeight;

        if (cityNames.isEmpty()) {
            graphics.drawString(this.font, "§8No cities yet", leftCol + 10, y, 0xFFAAAAAA);
        } else {
            for (int i = 0; i < Math.min(3, cityNames.size()); i++) {
                graphics.drawString(this.font, "§7• §f" + cityNames.get(i), leftCol + 10, y, COLOR_TEXT);
                y += 12;
            }
            if (cityNames.size() > 3) {
                graphics.drawString(this.font, "§8  +" + (cityNames.size() - 3) + " more...", leftCol + 10, y, 0xFFAAAAAA);
            }
        }

        // Role indicator
        if (isGovernor) {
            graphics.drawString(this.font, "§6[Governor]", guiLeft + guiWidth - 70, guiTop + 32, COLOR_PRIMARY);
        } else if (canManage) {
            graphics.drawString(this.font, "§e[Manager]", guiLeft + guiWidth - 65, guiTop + 32, 0xFFFFAA00);
        }
    }

    private void openCitiesScreen() {
        this.minecraft.setScreen(new CitiesListScreen(nationName, stateName));
    }

    private void openCreateCityScreen() {
        this.minecraft.setScreen(new CreateCityScreen(nationName, stateName));
    }

    private void goBack() {
        this.minecraft.setScreen(new StatesListScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String governorName, int cityCount, int chunkCount, int memberCount,
                           boolean isGovernor, boolean canManage, List<String> cityNames) {
        this.governorName = governorName;
        this.cityCount = cityCount;
        this.chunkCount = chunkCount;
        this.memberCount = memberCount;
        this.isGovernor = isGovernor;
        this.canManage = canManage;
        this.cityNames = cityNames;
        this.dataLoaded = true;
    }
}

