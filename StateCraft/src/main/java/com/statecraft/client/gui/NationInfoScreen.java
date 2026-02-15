package com.statecraft.client.gui;

import com.statecraft.client.FlagTextureManager;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.JoinCitizenshipPacket;
import com.statecraft.network.packets.LeaveCitizenshipPacket;
import com.statecraft.network.packets.RequestNationDetailsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing detailed nation information
 */
public class NationInfoScreen extends StateCraftScreen {

    private static final int FLAG_SIZE = 24;

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
    private boolean isMember = false;
    private String flagUrl = "";
    private List<String> stateNames = new ArrayList<>();
    private List<String> allyNames = new ArrayList<>();
    private List<String> enemyNames = new ArrayList<>();

    public NationInfoScreen(String nationName) {
        super(Component.literal("Nation: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 300; this.guiHeight = 240;
    }

    private Button settingsButton;
    private Button mailButton;
    private Button leaveButton;
    private Button joinButton;
    private Button legislatureButton;

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestNationDetailsPacket(nationName));

        // Two rows of buttons
        int row1Y = guiTop + guiHeight - 52;
        int row2Y = guiTop + guiHeight - 28;
        int buttonWidth = 55;
        int buttonSpacing = 60;
        int startX = guiLeft + 10;

        // === Row 1: Navigation buttons (always visible) ===

        // Members button
        this.addRenderableWidget(createButton(
            startX, row1Y, buttonWidth, 20,
            Component.literal("Members"),
            btn -> openMembersScreen()
        ));

        // States button
        this.addRenderableWidget(createButton(
            startX + buttonSpacing, row1Y, buttonWidth, 20,
            Component.literal("States"),
            btn -> openStatesScreen()
        ));

        // Elections button
        this.addRenderableWidget(createButton(
            startX + buttonSpacing * 2, row1Y, buttonWidth, 20,
            Component.literal("§6Vote"),
            btn -> openElectionsScreen()
        ));

        // Legislature button (only for legislature members - governors/officers)
        legislatureButton = this.addRenderableWidget(createButton(
            startX + buttonSpacing * 3, row1Y, buttonWidth, 20,
            Component.literal("§bLaws"),
            btn -> openLegislatureScreen()
        ));
        legislatureButton.visible = false;

        // === Row 2: Admin/action buttons ===

        // Mailbox button (admin only) - hidden until we know permissions
        mailButton = this.addRenderableWidget(createButton(
            startX, row2Y, buttonWidth, 20,
            Component.literal("§eMail"),
            btn -> openMailboxScreen()
        ));
        mailButton.visible = false;

        // Settings button (admin only) - hidden until we know permissions
        settingsButton = this.addRenderableWidget(createButton(
            startX + buttonSpacing, row2Y, buttonWidth, 20,
            Component.literal("Settings"),
            btn -> openSettingsScreen()
        ));
        settingsButton.visible = false;

        // Leave button (hidden for leaders) - hidden until we know permissions
        leaveButton = this.addRenderableWidget(createButton(
            startX + buttonSpacing * 2, row2Y, buttonWidth, 20,
            Component.literal("§cLeave"),
            btn -> leaveNation()
        ));
        leaveButton.visible = false;

        // Join button (only visible for non-members when nation is open)
        joinButton = this.addRenderableWidget(createButton(
            startX + buttonSpacing * 2, row2Y, buttonWidth, 20,
            Component.literal("§aJoin"),
            btn -> joinNation()
        ));
        joinButton.visible = false;

        // Back button (always visible, right side)
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 55, row2Y, 45, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render flag in top-left corner above the title bar
        renderFlag(graphics);

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

        // Description at bottom (above the two button rows)
        if (!description.isEmpty()) {
            int descY = guiTop + guiHeight - 75;
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

    private void openElectionsScreen() {
        this.minecraft.setScreen(new ElectionScreen());
    }

    private void openLegislatureScreen() {
        this.minecraft.setScreen(new LegislatureScreen(nationName));
    }

    private void openSettingsScreen() {
        this.minecraft.setScreen(new NationSettingsScreen(nationName));
    }

    private void openMailboxScreen() {
        this.minecraft.setScreen(new GovMailboxScreen(GovMailboxScreen.EntityType.NATION, nationName, ""));
    }

    private void leaveNation() {
        // Send leave request to server
        NetworkHandler.sendToServer(LeaveCitizenshipPacket.leaveNation(nationName));
        // Return to nations list
        this.minecraft.setScreen(new NationsListScreen());
    }

    private void joinNation() {
        // Send join request to server
        NetworkHandler.sendToServer(JoinCitizenshipPacket.joinNation(nationName));
        // Refresh the screen to show updated status
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationsListScreen());
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
                // Draw the flag texture
                graphics.blit(texture, flagX, flagY, 0, 0, FLAG_SIZE, FLAG_SIZE, FLAG_SIZE, FLAG_SIZE);
            } else if (FlagTextureManager.getInstance().isLoading(flagUrl)) {
                // Show loading indicator
                graphics.drawCenteredString(this.font, "...", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF888888);
            } else {
                // No flag or failed to load
                graphics.drawCenteredString(this.font, "?", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF666666);
            }
        } else {
            // No flag URL set - show placeholder
            graphics.drawCenteredString(this.font, "N", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF4A90D9);
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           long balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin, boolean isMember,
                           List<String> stateNames, List<String> allies, List<String> enemies,
                           String flagUrl) {
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
        this.isMember = isMember;
        this.stateNames = stateNames;
        this.allyNames = allies;
        this.enemyNames = enemies;
        this.flagUrl = flagUrl != null ? flagUrl : "";
        this.dataLoaded = true;

        // Show settings and mail buttons only for admins
        if (settingsButton != null) {
            settingsButton.visible = isAdmin;
        }
        if (mailButton != null) {
            mailButton.visible = isAdmin;
        }
        // Show legislature button for members (actual access is checked server-side)
        if (legislatureButton != null) {
            legislatureButton.visible = isMember;
        }
        // Show leave button only for members who are not the leader
        if (leaveButton != null) {
            leaveButton.visible = isMember && !isLeader;
        }
        // Show join button only for non-members when nation is open
        if (joinButton != null) {
            joinButton.visible = !isMember && isOpen;
        }
    }

    // Overload for backward compatibility (without isMember)
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           long balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin,
                           List<String> stateNames, List<String> allies, List<String> enemies,
                           String flagUrl) {
        updateData(states, maxStates, cities, chunks, members, balance, open, desc, leader,
                   isLeader, isAdmin, isAdmin, stateNames, allies, enemies, flagUrl);
    }

    // Overload for backward compatibility (without flagUrl)
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           long balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin, boolean isMember,
                           List<String> stateNames, List<String> allies, List<String> enemies) {
        updateData(states, maxStates, cities, chunks, members, balance, open, desc, leader,
                   isLeader, isAdmin, isMember, stateNames, allies, enemies, "");
    }

    // Overload for backward compatibility (without isMember or flagUrl)
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           long balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin,
                           List<String> stateNames, List<String> allies, List<String> enemies) {
        updateData(states, maxStates, cities, chunks, members, balance, open, desc, leader,
                   isLeader, isAdmin, isAdmin, stateNames, allies, enemies, "");
    }
}

