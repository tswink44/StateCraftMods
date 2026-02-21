package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Company screen — shows company info, shareholders, officers.
 * If the player has no company, shows a "Create Company" form.
 */
public class CompanyScreen extends StateCraftScreen {

    // State
    private boolean dataLoaded = false;
    private boolean hasCompany = false;
    private String companyName = "";
    private String companyId = "";
    private String founderName = "";
    private String description = "";
    private int totalShares = 0;
    private int playerShares = 0;
    private int shareholderCount = 0;
    private int officerCount = 0;
    private String headquartersCity = "";
    private boolean isFounder = false;
    private boolean isOfficer = false;
    private boolean dividendsEnabled = false;
    private double dividendRate = 0.0;
    private String companyType = "GENERAL";
    private List<SyncCompanyDataPacket.ShareholderEntry> shareholders = new ArrayList<>();
    private List<String> officerNames = new ArrayList<>();
    private String resultMessage = "";
    private long resultMessageTime = 0;

    // View mode
    private enum Tab { INFO, SHAREHOLDERS, OFFICERS, MANAGE }
    private Tab currentTab = Tab.INFO;

    // Create mode
    private boolean createMode = false;
    private EditBox nameInput;
    private EditBox sharesInput;
    private EditBox descInput;

    // Management action
    private EditBox actionInput;
    private String pendingAction = null; // track which action needs input

    // Scroll
    private int scrollOffset = 0;

    public CompanyScreen() {
        super(Component.literal("My Company"));
        this.guiWidth = 300;
        this.guiHeight = 230;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        NetworkHandler.sendToServer(new RequestCompanyDataPacket());
        buildUI();
    }

    private void buildUI() {
        this.clearWidgets();

        int startX = guiLeft + 10;

        if (createMode) {
            buildCreateUI();
            return;
        }

        if (!dataLoaded || !hasCompany) {
            // Show "Create Company" button at bottom
            this.addRenderableWidget(createButton(
                guiLeft + guiWidth / 2 - 60, guiTop + guiHeight - 55, 120, 18,
                Component.literal("§aCreate Company"),
                btn -> enterCreateMode()
            ));
        } else {
            // Tab buttons
            int tabY = guiTop + 22;
            int tabW = 55;
            int tabX = startX;

            this.addRenderableWidget(createButton(tabX, tabY, tabW, 14,
                Component.literal(currentTab == Tab.INFO ? "§f§nInfo" : "Info"),
                btn -> switchTab(Tab.INFO)));
            this.addRenderableWidget(createButton(tabX + tabW + 3, tabY, tabW + 10, 14,
                Component.literal(currentTab == Tab.SHAREHOLDERS ? "§f§nShares" : "Shares"),
                btn -> switchTab(Tab.SHAREHOLDERS)));
            this.addRenderableWidget(createButton(tabX + (tabW + 3) * 2 + 10, tabY, tabW, 14,
                Component.literal(currentTab == Tab.OFFICERS ? "§f§nOfficers" : "Officers"),
                btn -> switchTab(Tab.OFFICERS)));

            if (isFounder || isOfficer) {
                this.addRenderableWidget(createButton(tabX + (tabW + 3) * 3 + 10, tabY, tabW, 14,
                    Component.literal(currentTab == Tab.MANAGE ? "§f§nManage" : "Manage"),
                    btn -> switchTab(Tab.MANAGE)));
            }

            // Management tab input and action buttons
            if (currentTab == Tab.MANAGE && (isFounder || isOfficer)) {
                buildManageUI();
            }
        }

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 30, guiTop + guiHeight - 28, 60, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    private void buildCreateUI() {
        int startX = guiLeft + 15;
        int y = guiTop + 40;

        nameInput = new EditBox(this.font, startX, y, guiWidth - 30, 16, Component.literal("Name"));
        nameInput.setMaxLength(24);
        nameInput.setHint(Component.literal("Company name (3-24 chars)"));
        this.addRenderableWidget(nameInput);
        y += 24;

        sharesInput = new EditBox(this.font, startX, y, 100, 16, Component.literal("Shares"));
        sharesInput.setMaxLength(7);
        sharesInput.setValue("1000");
        sharesInput.setHint(Component.literal("Shares"));
        this.addRenderableWidget(sharesInput);
        y += 24;

        descInput = new EditBox(this.font, startX, y, guiWidth - 30, 16, Component.literal("Description"));
        descInput.setMaxLength(100);
        descInput.setHint(Component.literal("Description (optional)"));
        this.addRenderableWidget(descInput);
        y += 30;

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 80, y, 70, 18,
            Component.literal("§aCreate"),
            btn -> submitCreate()
        ));
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 + 10, y, 70, 18,
            Component.literal("Cancel"),
            btn -> { createMode = false; buildUI(); }
        ));
    }

    private void buildManageUI() {
        int y = guiTop + guiHeight - 80;
        int startX = guiLeft + 10;

        actionInput = new EditBox(this.font, startX, y, guiWidth - 80, 14, Component.literal("Input"));
        actionInput.setMaxLength(40);
        actionInput.setHint(Component.literal("Player name / value"));
        this.addRenderableWidget(actionInput);

        // Quick action buttons
        int btnY = guiTop + 42;
        int btnW = 85;

        if (isFounder) {
            this.addRenderableWidget(createButton(startX, btnY, btnW, 14,
                Component.literal("§a+ Officer"),
                btn -> { pendingAction = "ADD_OFFICER"; actionInput.setHint(Component.literal("Player name")); }));

            this.addRenderableWidget(createButton(startX + btnW + 4, btnY, btnW, 14,
                Component.literal("§c- Officer"),
                btn -> { pendingAction = "REMOVE_OFFICER"; actionInput.setHint(Component.literal("Player name")); }));

            btnY += 18;

            this.addRenderableWidget(createButton(startX, btnY, btnW, 14,
                Component.literal("§eToggle Div"),
                btn -> sendAction(CompanyActionPacket.Action.TOGGLE_DIVIDENDS, "", 0, 0, "")));

            this.addRenderableWidget(createButton(startX + btnW + 4, btnY, btnW, 14,
                Component.literal("§eSet Rate"),
                btn -> { pendingAction = "SET_RATE"; actionInput.setHint(Component.literal("Rate (e.g. 0.05 = 5%)")); }));

            btnY += 18;

            this.addRenderableWidget(createButton(startX, btnY, btnW, 14,
                Component.literal("§7Rename"),
                btn -> { pendingAction = "RENAME"; actionInput.setHint(Component.literal("New name")); }));

            this.addRenderableWidget(createButton(startX + btnW + 4, btnY, btnW, 14,
                Component.literal("§4Dissolve"),
                btn -> sendAction(CompanyActionPacket.Action.DISSOLVE, "", 0, 0, "")));
        }

        // Transfer shares (available to any shareholder)
        this.addRenderableWidget(createButton(startX + (btnW + 4) * 2, guiTop + 42, btnW, 14,
            Component.literal("§bTransfer"),
            btn -> { pendingAction = "TRANSFER"; actionInput.setHint(Component.literal("Player:Amount")); }));

        // Submit button for pending actions
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 65, y, 55, 14,
            Component.literal("§aSubmit"),
            btn -> submitAction()
        ));
    }

    private void switchTab(Tab tab) {
        currentTab = tab;
        scrollOffset = 0;
        pendingAction = null;
        buildUI();
    }

    private void enterCreateMode() {
        createMode = true;
        buildUI();
    }

    private void submitCreate() {
        String name = nameInput.getValue().trim();
        if (name.length() < 3 || name.length() > 24) return;

        int shares = 1000;
        try {
            shares = Integer.parseInt(sharesInput.getValue().trim());
        } catch (NumberFormatException ignored) {}

        String desc = descInput.getValue().trim();
        NetworkHandler.sendToServer(new CreateCompanyPacket(name, shares, desc));
        createMode = false;
    }

    private void submitAction() {
        if (pendingAction == null || actionInput == null) return;
        String value = actionInput.getValue().trim();
        if (value.isEmpty()) return;

        switch (pendingAction) {
            case "ADD_OFFICER" -> sendAction(CompanyActionPacket.Action.ADD_OFFICER, value, 0, 0, "");
            case "REMOVE_OFFICER" -> sendAction(CompanyActionPacket.Action.REMOVE_OFFICER, value, 0, 0, "");
            case "RENAME" -> sendAction(CompanyActionPacket.Action.RENAME, "", 0, 0, value);
            case "SET_RATE" -> {
                try {
                    double rate = Double.parseDouble(value);
                    sendAction(CompanyActionPacket.Action.SET_DIVIDEND_RATE, "", 0, rate, "");
                } catch (NumberFormatException ignored) {}
            }
            case "TRANSFER" -> {
                // Format: "PlayerName:Amount"
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    try {
                        int amount = Integer.parseInt(parts[1].trim());
                        sendAction(CompanyActionPacket.Action.TRANSFER_SHARES, parts[0].trim(), amount, 0, "");
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        pendingAction = null;
        if (actionInput != null) actionInput.setValue("");
    }

    private void sendAction(CompanyActionPacket.Action action, String target, int intVal, double doubleVal, String strVal) {
        NetworkHandler.sendToServer(new CompanyActionPacket(companyId, action, target, intVal, doubleVal, strVal));
    }

    /**
     * Called by ClientPacketHandler when data arrives.
     */
    public void updateData(SyncCompanyDataPacket packet) {
        this.hasCompany = packet.hasCompany();
        this.companyName = packet.getCompanyName();
        this.companyId = packet.getCompanyId();
        this.founderName = packet.getFounderName();
        this.description = packet.getDescription();
        this.totalShares = packet.getTotalShares();
        this.playerShares = packet.getPlayerShares();
        this.shareholderCount = packet.getShareholderCount();
        this.officerCount = packet.getOfficerCount();
        this.headquartersCity = packet.getHeadquartersCity();
        this.isFounder = packet.isFounder();
        this.isOfficer = packet.isOfficer();
        this.dividendsEnabled = packet.isDividendsEnabled();
        this.dividendRate = packet.getDividendRate();
        this.companyType = packet.getCompanyType();
        this.shareholders = packet.getShareholders();
        this.officerNames = packet.getOfficerNames();
        this.dataLoaded = true;

        if (!packet.getResultMessage().isEmpty()) {
            this.resultMessage = packet.getResultMessage();
            this.resultMessageTime = System.currentTimeMillis();
        }

        buildUI();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = guiLeft + 10;
        int endX = guiLeft + guiWidth - 10;

        // Title bar
        graphics.fill(startX, guiTop + 6, endX, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, endX, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, endX, guiTop + 20, 0xFF404040);

        if (hasCompany) {
            graphics.drawString(this.font, "§f\uD83C\uDFE2 " + companyName, startX + 4, guiTop + 9, 0xFFFFFFFF);
            String typeStr = companyType.equals("BANK") ? "§9[Bank]" : "§7[Company]";
            int typeWidth = this.font.width(typeStr.replaceAll("§.", ""));
            graphics.drawString(this.font, typeStr, endX - typeWidth - 4, guiTop + 9, 0xFFFFFFFF);
        } else {
            graphics.drawString(this.font, "§fMy Company", startX + 4, guiTop + 9, 0xFFFFFFFF);
        }

        if (createMode) {
            graphics.drawString(this.font, "§6Create a New Company", startX + 4, guiTop + 28, 0xFFFFAA00);
            renderResultMessage(graphics);
            return;
        }

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, guiTop + 100, 0xFFAAAAAA);
            return;
        }

        if (!hasCompany) {
            graphics.drawCenteredString(this.font, "§7You don't have a company yet.", this.width / 2, guiTop + 60, 0xFFAAAAAA);
            graphics.drawCenteredString(this.font, "§8Create one to get started!", this.width / 2, guiTop + 75, 0xFF888888);
            renderResultMessage(graphics);
            return;
        }

        // Divider under tabs
        renderDivider(graphics, startX, guiTop + 38, guiWidth - 20);

        switch (currentTab) {
            case INFO -> renderInfoTab(graphics, startX, endX);
            case SHAREHOLDERS -> renderShareholdersTab(graphics, startX, endX);
            case OFFICERS -> renderOfficersTab(graphics, startX, endX);
            case MANAGE -> renderManageTab(graphics, startX, endX);
        }

        renderResultMessage(graphics);
    }

    private void renderInfoTab(GuiGraphics graphics, int startX, int endX) {
        int y = guiTop + 44;

        // Description
        if (!description.isEmpty()) {
            graphics.drawString(this.font, "§7" + description, startX + 4, y, 0xFFAAAAAA);
            y += 14;
        }

        // Founder
        graphics.drawString(this.font, "§7Founder: §f" + founderName, startX + 4, y, 0xFFFFFFFF);
        y += 12;

        // Role
        String role = isFounder ? "§6★ Founder" : (isOfficer ? "§b✦ Officer" : "§e◆ Shareholder");
        graphics.drawString(this.font, "§7Your Role: " + role, startX + 4, y, 0xFFFFFFFF);
        y += 14;

        // Shares
        double pct = totalShares > 0 ? (double) playerShares / totalShares * 100 : 0;
        graphics.drawString(this.font, "§7Your Shares: §f" + playerShares + " §8/ " + totalShares
            + " §7(" + String.format("%.1f%%", pct) + ")", startX + 4, y, 0xFFFFFFFF);
        y += 12;

        graphics.drawString(this.font, "§7Shareholders: §f" + shareholderCount
            + "  §7Officers: §f" + officerCount, startX + 4, y, 0xFFFFFFFF);
        y += 14;

        // HQ
        if (!headquartersCity.isEmpty()) {
            graphics.drawString(this.font, "§7HQ: §f" + headquartersCity, startX + 4, y, 0xFFFFFFFF);
            y += 12;
        }

        // Dividends
        String divStatus = dividendsEnabled
            ? "§aEnabled §7(" + String.format("%.1f%%", dividendRate * 100) + " per cycle)"
            : "§8Disabled";
        graphics.drawString(this.font, "§7Dividends: " + divStatus, startX + 4, y, 0xFFFFFFFF);
    }

    private void renderShareholdersTab(GuiGraphics graphics, int startX, int endX) {
        int y = guiTop + 44;

        graphics.drawString(this.font, "§6Shareholders §7(" + shareholders.size() + ")", startX + 4, y, 0xFFFFFFFF);
        y += 14;

        // Column headers
        graphics.fill(startX, y, endX, y + 12, 0x44606060);
        graphics.drawString(this.font, "§7Name", startX + 4, y + 2, 0xFFCCCCCC);
        graphics.drawString(this.font, "§7Shares", startX + 120, y + 2, 0xFFCCCCCC);
        graphics.drawString(this.font, "§7%", startX + 200, y + 2, 0xFFCCCCCC);
        y += 14;

        for (int i = scrollOffset; i < shareholders.size() && i < scrollOffset + 8; i++) {
            SyncCompanyDataPacket.ShareholderEntry sh = shareholders.get(i);
            int bgColor = (i % 2 == 0) ? 0x22FFFFFF : 0x11FFFFFF;
            graphics.fill(startX, y, endX, y + 12, bgColor);

            graphics.drawString(this.font, "§f" + sh.name, startX + 4, y + 2, 0xFFFFFFFF);
            graphics.drawString(this.font, "§e" + sh.shares, startX + 120, y + 2, 0xFFFFFFFF);
            graphics.drawString(this.font, "§a" + String.format("%.1f%%", sh.percentage * 100), startX + 200, y + 2, 0xFFFFFFFF);
            y += 12;
        }
    }

    private void renderOfficersTab(GuiGraphics graphics, int startX, int endX) {
        int y = guiTop + 44;

        graphics.drawString(this.font, "§6Officers §7(" + officerNames.size() + ")", startX + 4, y, 0xFFFFFFFF);
        y += 14;

        if (officerNames.isEmpty()) {
            graphics.drawString(this.font, "§8No officers appointed", startX + 4, y + 10, 0xFF888888);
            return;
        }

        // Founder always listed first
        graphics.drawString(this.font, "§6★ " + founderName + " §7(Founder)", startX + 4, y, 0xFFFFFFFF);
        y += 14;

        for (String name : officerNames) {
            if (name.equals(founderName)) continue;
            graphics.drawString(this.font, "§b✦ " + name, startX + 4, y, 0xFFFFFFFF);
            y += 12;
        }
    }

    private void renderManageTab(GuiGraphics graphics, int startX, int endX) {
        // Action buttons are rendered as widgets via buildManageUI()
        // Just render the instruction/status here
        int y = guiTop + guiHeight - 95;

        if (pendingAction != null) {
            String actionLabel = switch (pendingAction) {
                case "ADD_OFFICER" -> "§eAdding officer — enter player name:";
                case "REMOVE_OFFICER" -> "§eRemoving officer — enter player name:";
                case "RENAME" -> "§eRenaming — enter new name:";
                case "SET_RATE" -> "§eSetting dividend rate — enter value (e.g. 0.05):";
                case "TRANSFER" -> "§eTransfer shares — enter Player:Amount:";
                default -> "";
            };
            graphics.drawString(this.font, actionLabel, startX + 4, y, 0xFFFFAA00);
        }
    }

    private void renderResultMessage(GuiGraphics graphics) {
        if (!resultMessage.isEmpty() && System.currentTimeMillis() - resultMessageTime < 5000) {
            int msgWidth = this.font.width(resultMessage.replaceAll("§.", ""));
            int msgX = this.width / 2 - msgWidth / 2 - 5;
            int msgY = guiTop + guiHeight - 42;
            graphics.fill(msgX - 3, msgY - 2, msgX + msgWidth + 8, msgY + 12, 0xDD000000);
            graphics.drawCenteredString(this.font, resultMessage, this.width / 2, msgY, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == Tab.SHAREHOLDERS) {
            int maxScroll = Math.max(0, shareholders.size() - 8);
            if (delta > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            else scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void goBack() {
        this.minecraft.setScreen(new MainMenuScreen());
    }

    @Override
    public void onClose() {
        goBack();
    }
}

