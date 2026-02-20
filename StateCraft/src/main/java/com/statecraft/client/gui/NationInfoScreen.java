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
 * Screen showing detailed nation information using horizontal info rows
 * and a simplified button bar at the bottom.
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
    private double balance = 0;
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

    // Buttons that have conditional visibility
    private Button managementButton;
    private Button leaveButton;
    private Button joinButton;

    public NationInfoScreen(String nationName) {
        super(Component.literal("Nation: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 280;
        this.guiHeight = 230;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false; // We render our own title row
    }

    @Override
    protected void init() {
        super.init();

        // Request detailed data
        NetworkHandler.sendToServer(new RequestNationDetailsPacket(nationName));

        // Bottom button bar
        int buttonY = guiTop + guiHeight - 28;
        int startX = guiLeft + 10;

        // Members button (always visible)
        this.addRenderableWidget(createButton(
            startX, buttonY, 55, 20,
            Component.literal("Members"),
            btn -> openMembersScreen()
        ));

        // States button (always visible)
        this.addRenderableWidget(createButton(
            startX + 58, buttonY, 50, 20,
            Component.literal("States"),
            btn -> openStatesScreen()
        ));

        // Management button (visible for members)
        managementButton = this.addRenderableWidget(createButton(
            startX + 111, buttonY, 72, 20,
            Component.literal("§bManagement"),
            btn -> openManagementScreen()
        ));
        managementButton.visible = false; // Hidden until we know if member

        // Leave button (visible for members who are not the leader)
        leaveButton = this.addRenderableWidget(createButton(
            startX + 186, buttonY, 40, 20,
            Component.literal("§cLeave"),
            btn -> leaveNation()
        ));
        leaveButton.visible = false;

        // Join button (only visible for non-members when nation is open)
        joinButton = this.addRenderableWidget(createButton(
            startX + 186, buttonY, 40, 20,
            Component.literal("§aJoin"),
            btn -> joinNation()
        ));
        joinButton.visible = false;

        // Back button (always visible, right side)
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 50, buttonY, 40, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render flag in top-left corner above the title bar
        renderFlag(graphics);

        int startX = guiLeft + 10;
        int rowEnd = guiLeft + guiWidth - 10;
        int rowHeight = 14;

        // Title row
        graphics.fill(startX, guiTop + 6, rowEnd, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, rowEnd, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, rowEnd, guiTop + 20, 0xFF404040);
        graphics.drawString(this.font, "Nation: " + nationName, startX + 4, guiTop + 9, 0xFFFFFF00);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 80, COLOR_TEXT);
            return;
        }

        int y = guiTop + 24;
        int spacing = 16;

        // Leader row
        renderInfoRow(graphics, startX, rowEnd, y, "§7Leader", "§f" + leaderName);
        y += spacing;

        // Status row
        String statusText = isOpen ? "§aOpen" : "§cClosed";
        renderInfoRow(graphics, startX, rowEnd, y, "§7Status", statusText);
        y += spacing;

        // Balance row
        renderInfoRow(graphics, startX, rowEnd, y, "§7Treasury", "§e" + formatBalance(balance));
        y += spacing;

        // Gap
        y += 4;

        // Statistics section header
        graphics.fill(startX, y, rowEnd, y + rowHeight, 0xAA606060);
        graphics.fill(startX, y, rowEnd, y + 1, 0xFF505050);
        graphics.fill(startX, y + rowHeight - 1, rowEnd, y + rowHeight, 0xFF404040);
        graphics.drawString(this.font, "§6Statistics", startX + 4, y + 3, COLOR_PRIMARY);
        y += spacing;

        // States row
        renderInfoRow(graphics, startX, rowEnd, y, "§7States", "§f" + stateCount + "/" + maxStates);
        y += spacing;

        // Cities row
        renderInfoRow(graphics, startX, rowEnd, y, "§7Cities", "§f" + cityCount);
        y += spacing;

        // Chunks row
        renderInfoRow(graphics, startX, rowEnd, y, "§7Chunks", "§f" + chunkCount);
        y += spacing;

        // Members row
        renderInfoRow(graphics, startX, rowEnd, y, "§7Members", "§f" + memberCount);
        y += spacing;

        // Gap
        y += 4;

        // Diplomacy section header
        graphics.fill(startX, y, rowEnd, y + rowHeight, 0xAA606060);
        graphics.fill(startX, y, rowEnd, y + 1, 0xFF505050);
        graphics.fill(startX, y + rowHeight - 1, rowEnd, y + rowHeight, 0xFF404040);
        graphics.drawString(this.font, "§6Diplomacy", startX + 4, y + 3, COLOR_PRIMARY);
        y += spacing;

        // Allies row
        String alliesText = allyNames.isEmpty() ? "§8None" : "§a" + String.join(", ", allyNames);
        renderInfoRow(graphics, startX, rowEnd, y, "§7Allies", alliesText);
        y += spacing;

        // Enemies row
        String enemiesText = enemyNames.isEmpty() ? "§8None" : "§c" + String.join(", ", enemyNames);
        renderInfoRow(graphics, startX, rowEnd, y, "§7Enemies", enemiesText);
    }

    /**
     * Render a single info row with a label on the left and value on the right
     */
    private void renderInfoRow(GuiGraphics graphics, int left, int right, int y, String label, String value) {
        int rowHeight = 14;
        // Row background
        graphics.fill(left, y, right, y + rowHeight, 0xAA808080);
        // Top/bottom borders
        graphics.fill(left, y, right, y + 1, 0xFF505050);
        graphics.fill(left, y + rowHeight - 1, right, y + rowHeight, 0xFF404040);
        // Label (left-aligned)
        graphics.drawString(this.font, label, left + 4, y + 3, COLOR_TEXT);
        // Value (right-aligned)
        int valueWidth = this.font.width(value.replaceAll("§.", ""));
        graphics.drawString(this.font, value, right - valueWidth - 6, y + 3, COLOR_TEXT);
    }

    private String formatBalance(double balance) {
        if (balance >= 1000000) {
            return String.format("$%.1fM", balance / 1000000.0);
        } else if (balance >= 1000) {
            return String.format("$%.1fK", balance / 1000.0);
        }
        return String.format("$%.2f", balance);
    }

    private void openMembersScreen() {
        this.minecraft.setScreen(new MembersListScreen(nationName));
    }

    private void openStatesScreen() {
        this.minecraft.setScreen(new StatesListScreen(nationName));
    }

    private void openManagementScreen() {
        this.minecraft.setScreen(new CountryManagementScreen(nationName, isAdmin, isMember, isLeader));
    }

    private void leaveNation() {
        NetworkHandler.sendToServer(LeaveCitizenshipPacket.leaveNation(nationName));
        this.minecraft.setScreen(new NationsListScreen());
    }

    private void joinNation() {
        NetworkHandler.sendToServer(JoinCitizenshipPacket.joinNation(nationName));
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
                graphics.blit(texture, flagX, flagY, 0, 0, FLAG_SIZE, FLAG_SIZE, FLAG_SIZE, FLAG_SIZE);
            } else if (FlagTextureManager.getInstance().isLoading(flagUrl)) {
                graphics.drawCenteredString(this.font, "...", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF888888);
            } else {
                graphics.drawCenteredString(this.font, "?", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF666666);
            }
        } else {
            graphics.drawCenteredString(this.font, "N", flagX + FLAG_SIZE / 2, flagY + FLAG_SIZE / 2 - 4, 0xFF4A90D9);
        }
    }

    @Override
    public void onClose() {
        goBack();
    }

    // Called by network handler when data is received
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           double balance, boolean open, String desc, String leader,
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

        // Show management button for members
        if (managementButton != null) {
            managementButton.visible = isMember;
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
                           double balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin,
                           List<String> stateNames, List<String> allies, List<String> enemies,
                           String flagUrl) {
        updateData(states, maxStates, cities, chunks, members, balance, open, desc, leader,
                   isLeader, isAdmin, isAdmin, stateNames, allies, enemies, flagUrl);
    }

    // Overload for backward compatibility (without flagUrl)
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           double balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin, boolean isMember,
                           List<String> stateNames, List<String> allies, List<String> enemies) {
        updateData(states, maxStates, cities, chunks, members, balance, open, desc, leader,
                   isLeader, isAdmin, isMember, stateNames, allies, enemies, "");
    }

    // Overload for backward compatibility (without isMember or flagUrl)
    public void updateData(int states, int maxStates, int cities, int chunks, int members,
                           double balance, boolean open, String desc, String leader,
                           boolean isLeader, boolean isAdmin,
                           List<String> stateNames, List<String> allies, List<String> enemies) {
        updateData(states, maxStates, cities, chunks, members, balance, open, desc, leader,
                   isLeader, isAdmin, isAdmin, stateNames, allies, enemies, "");
    }
}

