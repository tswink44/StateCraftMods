package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing detailed nation information
 */
public class NationInfoScreen extends StateCraftScreen {

    private final String nationName;

    // Data from server
    private boolean dataLoaded = false;
    private int stateCount = 0;
    private int maxStates = 5;
    private int cityCount = 0;
    private int chunkCount = 0;
    private int memberCount = 0;
    private long balance = 0;
    private boolean isOpen = false;
    private String description = "";
    private String leaderName = "Unknown";
    private boolean isLeader = false;
    private boolean isAdmin = false;
    private List<String> stateNames = new ArrayList<>();
    private List<String> allyNames = new ArrayList<>();
    private List<String> enemyNames = new ArrayList<>();

    public NationInfoScreen(String nationName) {
        super(Component.literal("Nation: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 300; this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestNationDetailsPacket(nationName));

        int buttonY = guiTop + guiHeight - 30;
        int buttonSpacing = 65;
        int startX = guiLeft + 15;

        // Members button
        this.addRenderableWidget(createButton(
            startX, buttonY, 60, 20,
            Component.literal("Members"),
            btn -> openMembersScreen()
        ));

        // States button
        this.addRenderableWidget(createButton(
            startX + buttonSpacing, buttonY, 60, 20,
            Component.literal("States"),
            btn -> openStatesScreen()
        ));

        // Settings button (admin only)
        Button settingsBtn = this.addRenderableWidget(createButton(
            startX + buttonSpacing * 2, buttonY, 60, 20,
            Component.literal("Settings"),
            btn -> openSettingsScreen()
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
        // Divider under title
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        int leftCol = guiLeft + 15;
        int rightCol = guiLeft + guiWidth / 2 + 10;
        int y = guiTop + 32;
        int lineHeight = 12;

        // Left column - Basic Info
        graphics.drawString(this.font, "§6Basic Info", leftCol, y, COLOR_PRIMARY);
        y += lineHeight + 2;

        graphics.drawString(this.font, "§7Leader: §f" + leaderName, leftCol, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Status: " + (isOpen ? "§aOpen" : "§cClosed"), leftCol, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Balance: §e" + formatBalance(balance), leftCol, y, COLOR_TEXT);
        y += lineHeight + 8;

        // Statistics
        graphics.drawString(this.font, "§6Statistics", leftCol, y, COLOR_PRIMARY);
        y += lineHeight + 2;

        graphics.drawString(this.font, "§7States: §f" + stateCount + "/" + maxStates, leftCol, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Cities: §f" + cityCount, leftCol, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Chunks: §f" + chunkCount, leftCol, y, COLOR_TEXT);
        y += lineHeight;

        graphics.drawString(this.font, "§7Members: §f" + memberCount, leftCol, y, COLOR_TEXT);

        // Right column - Diplomacy & States
        y = guiTop + 32;

        graphics.drawString(this.font, "§6States", rightCol, y, COLOR_PRIMARY);
        y += lineHeight + 2;

        if (stateNames.isEmpty()) {
            graphics.drawString(this.font, "§8No states yet", rightCol, y, 0xFFAAAAAA);
            y += lineHeight;
        } else {
            for (int i = 0; i < Math.min(3, stateNames.size()); i++) {
                graphics.drawString(this.font, "§7• §f" + stateNames.get(i), rightCol, y, COLOR_TEXT);
                y += lineHeight;
            }
            if (stateNames.size() > 3) {
                graphics.drawString(this.font, "§8  +" + (stateNames.size() - 3) + " more...", rightCol, y, 0xFFAAAAAA);
                y += lineHeight;
            }
        }

        y += 6;
        graphics.drawString(this.font, "§6Diplomacy", rightCol, y, COLOR_PRIMARY);
        y += lineHeight + 2;

        // Allies
        graphics.drawString(this.font, "§aAllies: §f" + (allyNames.isEmpty() ? "None" : String.join(", ", allyNames)), rightCol, y, COLOR_TEXT);
        y += lineHeight;

        // Enemies
        graphics.drawString(this.font, "§cEnemies: §f" + (enemyNames.isEmpty() ? "None" : String.join(", ", enemyNames)), rightCol, y, COLOR_TEXT);

        // Description at bottom
        if (!description.isEmpty()) {
            int descY = guiTop + guiHeight - 55;
            renderDivider(graphics, guiLeft + 10, descY - 5, guiWidth - 20);

            // Truncate if too long
            String desc = description;
            if (this.font.width(desc) > guiWidth - 30) {
                desc = this.font.plainSubstrByWidth(desc, guiWidth - 40) + "...";
            }
            graphics.drawString(this.font, "§7" + desc, leftCol, descY, 0xFFCCCCCC);
        }
    }

    private String formatBalance(long balance) {
        if (balance >= 1000000) {
            return String.format("%.1fM", balance / 1000000.0);
        } else if (balance >= 1000) {
            return String.format("%.1fK", balance / 1000.0);
        }
        return String.valueOf(balance);
    }

    private void openMembersScreen() {
        this.minecraft.setScreen(new MembersListScreen(nationName));
    }

    private void openStatesScreen() {
        this.minecraft.setScreen(new StatesListScreen(nationName));
    }

    private void openSettingsScreen() {
        this.minecraft.setScreen(new NationSettingsScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           long balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin,
                           List<String> stateNames, List<String> allies, List<String> enemies) {
        this.stateCount = states;
        this.maxStates = maxStates;
        this.cityCount = cities;
        this.chunkCount = chunks;
        this.memberCount = members;
        this.balance = balance;
        this.isOpen = open;
        this.description = desc;
        this.leaderName = leader;
        this.isLeader = isLeader;
        this.isAdmin = isAdmin;
        this.stateNames = stateNames;
        this.allyNames = allies;
        this.enemyNames = enemies;
        this.dataLoaded = true;
    }
}

