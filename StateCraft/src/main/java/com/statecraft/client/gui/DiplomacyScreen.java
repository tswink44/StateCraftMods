package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.*;
import com.statecraft.network.packets.SyncDiplomacyDataPacket.NationRelation;
import com.statecraft.network.packets.SyncDiplomacyDataPacket.ProposalEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Diplomacy screen — shows nation relationships, pending proposals,
 * and allows the leader to perform diplomatic actions.
 *
 * Accessible to all nation members from the Country Management screen.
 * Only the nation leader can perform actions (buttons hidden for non-leaders).
 */
public class DiplomacyScreen extends StateCraftScreen {

    private final String nationName;
    private boolean isLeader = false;
    private boolean dataLoaded = false;

    private List<NationRelation> relations = new ArrayList<>();
    private List<ProposalEntry> inboundProposals = new ArrayList<>();
    private List<ProposalEntry> outboundProposals = new ArrayList<>();
    private String resultMessage = "";
    private long resultMessageTime = 0;

    // Scrolling
    private int scrollOffset = 0;
    private static final int ENTRY_HEIGHT = 18;
    private int maxVisible = 6;

    // Tab system: RELATIONS, INBOUND, OUTBOUND
    private enum Tab { RELATIONS, INBOUND, OUTBOUND }
    private Tab currentTab = Tab.RELATIONS;

    // Action confirmation
    private boolean showConfirmation = false;
    private String confirmAction = null;
    private String confirmTargetNation = null;

    // Target input for war/alliance/peace
    private EditBox targetInput;

    public DiplomacyScreen(String nationName) {
        super(Component.literal("Diplomacy"));
        this.nationName = nationName;
        this.guiWidth = 320;
        this.guiHeight = 256;
    }

    @Override
    protected boolean shouldRenderTitle() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        // Request data from server
        NetworkHandler.sendToServer(new RequestDiplomacyDataPacket(nationName));
    }

    /**
     * Called when the server sends updated diplomacy data.
     */
    public void updateData(SyncDiplomacyDataPacket packet) {
        this.isLeader = packet.isLeader();
        this.relations = packet.getRelations();
        this.inboundProposals = packet.getInboundProposals();
        this.outboundProposals = packet.getOutboundProposals();
        this.dataLoaded = true;

        if (!packet.getResultMessage().isEmpty()) {
            this.resultMessage = packet.getResultMessage();
            this.resultMessageTime = System.currentTimeMillis();
        }

        // Rebuild UI
        rebuildUI();
    }

    private void rebuildUI() {
        this.clearWidgets();
        scrollOffset = 0;

        int tabY = guiTop + 22;
        int tabWidth = 70;
        int tabX = guiLeft + 10;

        // Tab buttons
        this.addRenderableWidget(createButton(tabX, tabY, tabWidth, 14,
            Component.literal(currentTab == Tab.RELATIONS ? "§f§nRelations" : "Relations"),
            btn -> { currentTab = Tab.RELATIONS; rebuildUI(); }));

        this.addRenderableWidget(createButton(tabX + tabWidth + 4, tabY, tabWidth, 14,
            Component.literal((currentTab == Tab.INBOUND ? "§f§n" : "") + "Inbound (" + inboundProposals.size() + ")"),
            btn -> { currentTab = Tab.INBOUND; rebuildUI(); }));

        this.addRenderableWidget(createButton(tabX + 2 * (tabWidth + 4), tabY, tabWidth, 14,
            Component.literal((currentTab == Tab.OUTBOUND ? "§f§n" : "") + "Outbound (" + outboundProposals.size() + ")"),
            btn -> { currentTab = Tab.OUTBOUND; rebuildUI(); }));

        int contentY = guiTop + 42;
        int contentH = guiHeight - 80;
        maxVisible = contentH / ENTRY_HEIGHT;

        switch (currentTab) {
            case RELATIONS -> buildRelationsTab(contentY);
            case INBOUND -> buildInboundTab(contentY);
            case OUTBOUND -> buildOutboundTab(contentY);
        }

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 30, guiTop + guiHeight - 28, 60, 20,
            Component.literal("Back"),
            btn -> goBack()));

        // Leader action bar (bottom area, above Back)
        if (isLeader && currentTab == Tab.RELATIONS) {
            // Target input
            targetInput = new EditBox(this.font, guiLeft + 10, guiTop + guiHeight - 52, 140, 16,
                Component.literal("Target Nation"));
            targetInput.setMaxLength(24);
            targetInput.setHint(Component.literal("Nation name..."));
            this.addRenderableWidget(targetInput);

            // Action buttons
            int btnX = guiLeft + 156;
            int btnY = guiTop + guiHeight - 54;
            this.addRenderableWidget(createButton(btnX, btnY, 48, 16,
                Component.literal("§cWar"), btn -> confirmDiplomacyAction("DECLARE_WAR")));
            this.addRenderableWidget(createButton(btnX + 52, btnY, 48, 16,
                Component.literal("§aPeace"), btn -> confirmDiplomacyAction("PROPOSE_PEACE")));
            this.addRenderableWidget(createButton(btnX, btnY + 18, 48, 16,
                Component.literal("§bAlly"), btn -> confirmDiplomacyAction("PROPOSE_ALLIANCE")));
            this.addRenderableWidget(createButton(btnX + 52, btnY + 18, 48, 16,
                Component.literal("§eBreak"), btn -> confirmDiplomacyAction("BREAK_ALLIANCE")));
        }
    }

    private void buildRelationsTab(int startY) {
        if (relations.isEmpty()) return;

        int visible = Math.min(maxVisible, relations.size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= relations.size()) break;
            // Relations are rendered in renderContent, no widgets needed per-row
        }
    }

    private void buildInboundTab(int startY) {
        if (!isLeader || inboundProposals.isEmpty()) return;

        int visible = Math.min(maxVisible, inboundProposals.size() - scrollOffset);
        int btnWidth = 40;
        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= inboundProposals.size()) break;

            ProposalEntry p = inboundProposals.get(idx);
            int y = startY + i * ENTRY_HEIGHT;

            // Accept button
            this.addRenderableWidget(createButton(
                guiLeft + guiWidth - 10 - btnWidth * 2 - 6, y, btnWidth, 14,
                Component.literal("§a✓"),
                btn -> sendProposalAction(DiplomacyActionPacket.Action.ACCEPT_PROPOSAL, p.proposalId)));

            // Reject button
            this.addRenderableWidget(createButton(
                guiLeft + guiWidth - 10 - btnWidth - 2, y, btnWidth, 14,
                Component.literal("§c✗"),
                btn -> sendProposalAction(DiplomacyActionPacket.Action.REJECT_PROPOSAL, p.proposalId)));
        }
    }

    private void buildOutboundTab(int startY) {
        // Outbound proposals are display-only (no cancel yet)
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = guiLeft + 10;
        int endX = guiLeft + guiWidth - 10;

        // Title row
        graphics.fill(startX, guiTop + 6, endX, guiTop + 20, 0xAA808080);
        graphics.drawString(this.font, "🌐 Diplomacy — " + nationName, startX + 4, guiTop + 9, 0xFFFFFFFF);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "Loading...", guiLeft + guiWidth / 2, guiTop + 80, 0xFFAAAAAA);
            return;
        }

        int contentY = guiTop + 42;

        switch (currentTab) {
            case RELATIONS -> renderRelationsTab(graphics, startX, endX, contentY, mouseX, mouseY);
            case INBOUND -> renderInboundTab(graphics, startX, endX, contentY);
            case OUTBOUND -> renderOutboundTab(graphics, startX, endX, contentY);
        }

        // Result message
        if (!resultMessage.isEmpty() && System.currentTimeMillis() - resultMessageTime < 5000) {
            graphics.drawCenteredString(this.font, resultMessage, guiLeft + guiWidth / 2,
                guiTop + guiHeight - 70, 0xFFFFFF00);
        }
    }

    private void renderRelationsTab(GuiGraphics graphics, int startX, int endX, int startY, int mouseX, int mouseY) {
        if (relations.isEmpty()) {
            graphics.drawCenteredString(this.font, "No other nations exist.",
                guiLeft + guiWidth / 2, startY + 20, 0xFFAAAAAA);
            return;
        }

        int visible = Math.min(maxVisible, relations.size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= relations.size()) break;

            NationRelation rel = relations.get(idx);
            int y = startY + i * ENTRY_HEIGHT;

            // Background
            int bgColor = (idx % 2 == 0) ? 0x33FFFFFF : 0x22FFFFFF;
            graphics.fill(startX, y, endX, y + ENTRY_HEIGHT - 2, bgColor);

            // Status icon and color
            String icon;
            int textColor;
            switch (rel.status) {
                case "AT_WAR" -> { icon = "🔴"; textColor = 0xFFFF4444; }
                case "ALLIED" -> { icon = "🟢"; textColor = 0xFF44FF44; }
                case "TRUCE" -> { icon = "🟡"; textColor = 0xFFFFFF44; }
                default -> { icon = "⚪"; textColor = 0xFFCCCCCC; }
            }

            graphics.drawString(this.font, icon + " " + rel.nationName, startX + 4, y + 4, textColor);

            // Status text on the right
            String statusText = switch (rel.status) {
                case "AT_WAR" -> "§cAt War";
                case "ALLIED" -> "§aAllied";
                case "TRUCE" -> "§eTruce";
                default -> "§7Neutral";
            };
            int statusWidth = this.font.width(statusText.replaceAll("§.", ""));
            graphics.drawString(this.font, statusText, endX - statusWidth - 4, y + 4, 0xFFFFFFFF);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "▲ Scroll Up", guiLeft + guiWidth / 2, startY - 10, 0xFF888888);
        }
        if (scrollOffset + maxVisible < relations.size()) {
            graphics.drawCenteredString(this.font, "▼ Scroll Down", guiLeft + guiWidth / 2,
                startY + maxVisible * ENTRY_HEIGHT, 0xFF888888);
        }
    }

    private void renderInboundTab(GuiGraphics graphics, int startX, int endX, int startY) {
        if (inboundProposals.isEmpty()) {
            graphics.drawCenteredString(this.font, "No pending inbound proposals.",
                guiLeft + guiWidth / 2, startY + 20, 0xFFAAAAAA);
            return;
        }

        int visible = Math.min(maxVisible, inboundProposals.size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= inboundProposals.size()) break;

            ProposalEntry p = inboundProposals.get(idx);
            int y = startY + i * ENTRY_HEIGHT;

            int bgColor = (idx % 2 == 0) ? 0x33FFFFFF : 0x22FFFFFF;
            graphics.fill(startX, y, endX, y + ENTRY_HEIGHT - 2, bgColor);

            String typeIcon = p.type.equals("PEACE") ? "☮" : "🤝";
            String typeText = p.type.equals("PEACE") ? "§ePeace" : "§aAlliance";

            graphics.drawString(this.font, typeIcon + " " + p.otherNationName + " — " + typeText,
                startX + 4, y + 4, 0xFFFFFFFF);

            // Time remaining
            long remaining = p.expiresAt - System.currentTimeMillis();
            if (remaining > 0) {
                long days = remaining / (1000 * 60 * 60 * 24);
                long hours = (remaining % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
                String timeStr = days > 0 ? days + "d " + hours + "h" : hours + "h";
                graphics.drawString(this.font, "§7" + timeStr, startX + 4, y + 14, 0xFF888888);
            }
        }
    }

    private void renderOutboundTab(GuiGraphics graphics, int startX, int endX, int startY) {
        if (outboundProposals.isEmpty()) {
            graphics.drawCenteredString(this.font, "No pending outbound proposals.",
                guiLeft + guiWidth / 2, startY + 20, 0xFFAAAAAA);
            return;
        }

        int visible = Math.min(maxVisible, outboundProposals.size() - scrollOffset);
        for (int i = 0; i < visible; i++) {
            int idx = scrollOffset + i;
            if (idx >= outboundProposals.size()) break;

            ProposalEntry p = outboundProposals.get(idx);
            int y = startY + i * ENTRY_HEIGHT;

            int bgColor = (idx % 2 == 0) ? 0x33FFFFFF : 0x22FFFFFF;
            graphics.fill(startX, y, endX, y + ENTRY_HEIGHT - 2, bgColor);

            String typeIcon = p.type.equals("PEACE") ? "☮" : "🤝";
            String typeText = p.type.equals("PEACE") ? "§ePeace" : "§aAlliance";

            graphics.drawString(this.font, typeIcon + " → " + p.otherNationName + " — " + typeText + " §7(Pending)",
                startX + 4, y + 4, 0xFFFFFFFF);
        }
    }

    // ==================== Actions ====================

    private void confirmDiplomacyAction(String action) {
        if (targetInput == null || targetInput.getValue().trim().isEmpty()) {
            resultMessage = "§cEnter a nation name first!";
            resultMessageTime = System.currentTimeMillis();
            return;
        }

        String targetName = targetInput.getValue().trim();
        DiplomacyActionPacket.Action actionEnum;
        try {
            actionEnum = DiplomacyActionPacket.Action.valueOf(action);
        } catch (IllegalArgumentException e) {
            return;
        }

        NetworkHandler.sendToServer(new DiplomacyActionPacket(nationName, actionEnum, targetName, ""));
        targetInput.setValue("");
    }

    private void sendProposalAction(DiplomacyActionPacket.Action action, String proposalId) {
        NetworkHandler.sendToServer(new DiplomacyActionPacket(nationName, action, "", proposalId));
    }

    // ==================== Scrolling ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listSize = switch (currentTab) {
            case RELATIONS -> relations.size();
            case INBOUND -> inboundProposals.size();
            case OUTBOUND -> outboundProposals.size();
        };

        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            rebuildUI();
        } else if (delta < 0 && scrollOffset + maxVisible < listSize) {
            scrollOffset++;
            rebuildUI();
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void goBack() {
        this.minecraft.setScreen(new CountryManagementScreen(nationName, true, true, isLeader));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

