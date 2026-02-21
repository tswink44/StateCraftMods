package com.statecraft.client.gui;

import com.statecraft.client.FlagTextureManager;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.LeaveCitizenshipPacket;
import com.statecraft.network.packets.RequestStateDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing detailed state information
 */
public class StateInfoScreen extends StateCraftScreen {

    private static final int FLAG_SIZE = 24;

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
    private boolean isNationLeader = false;
    private String flagUrl = "";
    private List<String> cityNames = new ArrayList<>();

    public StateInfoScreen(String nationName, String stateName) {
        super(Component.literal("State: " + stateName));
        this.nationName = nationName;
        this.stateName = stateName;
        this.guiWidth = 280; this.guiHeight = 200;
    }

    private Button settingsButton;
    private Button mailButton;
    private Button leaveButton;
    private Button appointButton;

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestStateDetailsPacket(nationName, stateName));

        int buttonY = guiTop + guiHeight - 30;
        int buttonWidth = 42;
        int spacing = 46;
        int startX = guiLeft + 6;

        // Cities button
        this.addRenderableWidget(createButton(
            startX, buttonY, buttonWidth, 20,
            Component.literal("Cities"),
            btn -> openCitiesScreen()
        ));

        // Mailbox button - hidden until we know permissions
        mailButton = this.addRenderableWidget(createButton(
            startX + spacing, buttonY, buttonWidth, 20,
            Component.literal("§eMail"),
            btn -> openMailboxScreen()
        ));
        mailButton.visible = false;

        // Settings button - hidden until we know permissions
        settingsButton = this.addRenderableWidget(createButton(
            startX + spacing * 2, buttonY, buttonWidth, 20,
            Component.literal("Settings"),
            btn -> openSettingsScreen()
        ));
        settingsButton.visible = false;

        // Appoint Governor button - hidden until we know if player is nation leader
        appointButton = this.addRenderableWidget(createButton(
            startX + spacing * 3, buttonY, buttonWidth, 20,
            Component.literal("§bAppoint"),
            btn -> openAppointScreen()
        ));
        appointButton.visible = false;

        // Leave button - hidden until we know permissions (not governor)
        leaveButton = this.addRenderableWidget(createButton(
            startX + spacing * 3, buttonY, buttonWidth, 20,
            Component.literal("§cLeave"),
            btn -> leaveState()
        ));
        leaveButton.visible = false;

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 45, buttonY, 38, 20,
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


    private void openSettingsScreen() {
        this.minecraft.setScreen(new StateSettingsScreen(nationName, stateName));
    }

    private void openAppointScreen() {
        this.minecraft.setScreen(new AppointScreen(nationName, stateName, null,
            AppointScreen.AppointType.GOVERNOR,
            () -> this.minecraft.setScreen(new StateInfoScreen(nationName, stateName))));
    }

    private void openMailboxScreen() {
        this.minecraft.setScreen(new GovMailboxScreen(GovMailboxScreen.EntityType.STATE, stateName, nationName));
    }

    private void leaveState() {
        // Send leave request to server
        NetworkHandler.sendToServer(LeaveCitizenshipPacket.leaveState(nationName, stateName));
        // Return to my states screen
        this.minecraft.setScreen(new MyStatesScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new MyStatesScreen(nationName));
    }

    private void renderFlag(GuiGraphics graphics) {
        int flagX = guiLeft + 4;
        int flagY = guiTop + 4;

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
            graphics.drawCenteredString(this.font, "S", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF4A90D9);
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(String governorName, int cityCount, int chunkCount, int memberCount,
                           boolean isGovernor, boolean canManage, List<String> cityNames, String flagUrl) {
        updateData(governorName, cityCount, chunkCount, memberCount, isGovernor, canManage, cityNames, flagUrl, false);
    }

    public void updateData(String governorName, int cityCount, int chunkCount, int memberCount,
                           boolean isGovernor, boolean canManage, List<String> cityNames, boolean isNationLeader) {
        updateData(governorName, cityCount, chunkCount, memberCount, isGovernor, canManage, cityNames, "", isNationLeader);
    }

    public void updateData(String governorName, int cityCount, int chunkCount, int memberCount,
                           boolean isGovernor, boolean canManage, List<String> cityNames, String flagUrl,
                           boolean isNationLeader) {
        this.governorName = governorName;
        this.cityCount = cityCount;
        this.chunkCount = chunkCount;
        this.memberCount = memberCount;
        this.isGovernor = isGovernor;
        this.canManage = canManage;
        this.cityNames = cityNames;
        this.flagUrl = flagUrl != null ? flagUrl : "";
        this.isNationLeader = isNationLeader;
        this.dataLoaded = true;

        // Show settings and mail buttons only for governors/admins
        if (settingsButton != null) {
            settingsButton.visible = canManage;
        }
        if (mailButton != null) {
            mailButton.visible = canManage;
        }
        // Show appoint button for nation leaders (they can appoint governors)
        if (appointButton != null) {
            appointButton.visible = isNationLeader;
        }
        // Show leave button for non-governors (but not if appoint is shown in same slot)
        if (leaveButton != null) {
            leaveButton.visible = !isGovernor && !isNationLeader;
        }
    }

    // Overload for backward compatibility
    public void updateData(String governorName, int cityCount, int chunkCount, int memberCount,
                           boolean isGovernor, boolean canManage, List<String> cityNames) {
        updateData(governorName, cityCount, chunkCount, memberCount, isGovernor, canManage, cityNames, "", false);
    }
}

