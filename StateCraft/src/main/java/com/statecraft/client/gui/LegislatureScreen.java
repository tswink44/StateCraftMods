package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.LeaderBillActionPacket;
import com.statecraft.network.packets.RequestLegislatureDataPacket;
import com.statecraft.network.packets.SyncLegislatureDataPacket;
import com.statecraft.network.packets.VoteBillPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Legislature screen showing active bills, voting status, and history
 */
public class LegislatureScreen extends StateCraftScreen {

    private final String nationName;

    // Data from server
    private boolean dataLoaded = false;
    private boolean isLegislatureMember = false;
    private boolean isNationLeader = false;
    private List<SyncLegislatureDataPacket.BillSummary> activeBills = new ArrayList<>();
    private List<SyncLegislatureDataPacket.BillSummary> recentHistory = new ArrayList<>();
    private List<String> votingMemberNames = new ArrayList<>();
    private int totalMembers = 0;

    // Scrolling
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_BILLS = 4; // Reduced to make room for buttons

    // Tab state
    private Tab currentTab = Tab.ACTIVE;

    // Bill action buttons (dynamically created)
    private List<Button> billActionButtons = new ArrayList<>();

    private enum Tab {
        ACTIVE("Active Bills"),
        HISTORY("History"),
        MEMBERS("Members");

        final String displayName;
        Tab(String displayName) { this.displayName = displayName; }
    }

    public LegislatureScreen(String nationName) {
        super(Component.literal("Legislature: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 340;
        this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        // Request data from server
        NetworkHandler.sendToServer(new RequestLegislatureDataPacket(nationName));

        int tabY = guiTop + 25;
        int tabWidth = 70;
        int tabSpacing = 5;
        int startX = guiLeft + 15;

        // Tab buttons
        this.addRenderableWidget(createButton(
            startX, tabY, tabWidth, 16,
            Component.literal("Active"),
            btn -> switchTab(Tab.ACTIVE)
        ));

        this.addRenderableWidget(createButton(
            startX + tabWidth + tabSpacing, tabY, tabWidth, 16,
            Component.literal("History"),
            btn -> switchTab(Tab.HISTORY)
        ));

        this.addRenderableWidget(createButton(
            startX + (tabWidth + tabSpacing) * 2, tabY, tabWidth, 16,
            Component.literal("Members"),
            btn -> switchTab(Tab.MEMBERS)
        ));

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        // Propose Bill button (only for legislature members)
        Button proposeBillButton = this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 90, 20,
            Component.literal("§aPropose Bill"),
            btn -> openProposeBillScreen()
        ));
        // Will be enabled after data load if player is a member
        proposeBillButton.active = false;

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 70, buttonY, 55, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
        rebuildBillButtons();
    }

    /**
     * Rebuild bill action buttons based on current tab and visible bills
     */
    private void rebuildBillButtons() {
        // Remove old bill action buttons
        for (Button btn : billActionButtons) {
            this.removeWidget(btn);
        }
        billActionButtons.clear();

        if (!dataLoaded || currentTab != Tab.ACTIVE) return;

        int buttonY = guiTop + 58;
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_BILLS, activeBills.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            SyncLegislatureDataPacket.BillSummary bill = activeBills.get(i);
            int billIndex = i - scrollOffset;
            int yOffset = billIndex * 37; // Space per bill entry (11+11+11+4)

            // Add vote buttons for VOTING bills (if player is legislature member and hasn't voted)
            if (bill.getStatus().equals("VOTING") && isLegislatureMember && !bill.hasPlayerVoted()) {
                int voteButtonX = guiLeft + guiWidth - 140;
                int voteButtonY = buttonY + yOffset + 22;

                Button yesBtn = createButton(voteButtonX, voteButtonY, 40, 14,
                    Component.literal("§aYes"),
                    btn -> voteOnBill(bill.getBillId(), VoteBillPacket.VoteType.YES));
                Button noBtn = createButton(voteButtonX + 44, voteButtonY, 40, 14,
                    Component.literal("§cNo"),
                    btn -> voteOnBill(bill.getBillId(), VoteBillPacket.VoteType.NO));
                Button abstainBtn = createButton(voteButtonX + 88, voteButtonY, 50, 14,
                    Component.literal("§7Abstain"),
                    btn -> voteOnBill(bill.getBillId(), VoteBillPacket.VoteType.ABSTAIN));

                this.addRenderableWidget(yesBtn);
                this.addRenderableWidget(noBtn);
                this.addRenderableWidget(abstainBtn);
                billActionButtons.add(yesBtn);
                billActionButtons.add(noBtn);
                billActionButtons.add(abstainBtn);
            }

            // Add sign/veto buttons for PASSED bills (if player is leader)
            if (bill.getStatus().equals("PASSED") && isNationLeader) {
                int actionButtonX = guiLeft + guiWidth - 130;
                int actionButtonY = buttonY + yOffset + 22;

                Button signBtn = createButton(actionButtonX, actionButtonY, 55, 14,
                    Component.literal("§a✓ Sign"),
                    btn -> leaderAction(bill.getBillId(), LeaderBillActionPacket.ActionType.SIGN));
                this.addRenderableWidget(signBtn);
                billActionButtons.add(signBtn);

                // Only show veto button if not veto-proof
                // We check needsLeaderAction which should be false for veto-proof bills
                if (bill.needsLeaderAction()) {
                    Button vetoBtn = createButton(actionButtonX + 60, actionButtonY, 55, 14,
                        Component.literal("§c✗ Veto"),
                        btn -> leaderAction(bill.getBillId(), LeaderBillActionPacket.ActionType.VETO));
                    this.addRenderableWidget(vetoBtn);
                    billActionButtons.add(vetoBtn);
                }
            }
        }
    }

    private void voteOnBill(String billId, VoteBillPacket.VoteType voteType) {
        NetworkHandler.sendToServer(new VoteBillPacket(nationName, billId, voteType));
        // Server will automatically send updated legislature data after successful vote
    }

    private void leaderAction(String billId, LeaderBillActionPacket.ActionType actionType) {
        NetworkHandler.sendToServer(new LeaderBillActionPacket(nationName, billId, actionType));
        // Server will automatically send updated legislature data after successful action
    }

    private void refreshData() {
        // Request fresh data
        NetworkHandler.sendToServer(new RequestLegislatureDataPacket(nationName));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading legislature data...",
                this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        // Draw role indicator — citizens can view but only members/leader can propose/vote
        String roleText = isNationLeader ? "§6★ Nation Leader" :
            (isLegislatureMember ? "§b✦ Voting Member" : "§7☉ Citizen Observer");
        graphics.drawString(this.font, roleText, guiLeft + guiWidth - font.width(roleText.replaceAll("§.", "")) - 15, guiTop + 8, 0xFFFFFFFF);

        // Draw tab indicator
        int tabContentY = guiTop + 48;
        renderDivider(graphics, guiLeft + 10, tabContentY - 3, guiWidth - 20);

        switch (currentTab) {
            case ACTIVE -> renderActiveBillsTab(graphics, tabContentY, mouseX, mouseY);
            case HISTORY -> renderHistoryTab(graphics, tabContentY, mouseX, mouseY);
            case MEMBERS -> renderMembersTab(graphics, tabContentY, mouseX, mouseY);
        }
    }

    private void renderActiveBillsTab(GuiGraphics graphics, int startY, int mouseX, int mouseY) {
        int x = guiLeft + 15;
        int y = startY;

        // Header
        graphics.drawString(this.font, "§6Active Bills §7(" + activeBills.size() + ")", x, y, COLOR_PRIMARY);
        y += 14;

        if (activeBills.isEmpty()) {
            graphics.drawString(this.font, "§8No active bills", x, y + 20, 0xFFAAAAAA);
            return;
        }

        // Bill list
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_BILLS, activeBills.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            SyncLegislatureDataPacket.BillSummary bill = activeBills.get(i);
            y = renderBillEntry(graphics, bill, x, y, mouseX, mouseY, true);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ Scroll Up", guiLeft + guiWidth / 2, startY - 8, 0xFF888888);
        }
        if (scrollOffset + MAX_VISIBLE_BILLS < activeBills.size()) {
            graphics.drawCenteredString(this.font, "§7▼ Scroll Down", guiLeft + guiWidth / 2, guiTop + guiHeight - 50, 0xFF888888);
        }
    }

    private void renderHistoryTab(GuiGraphics graphics, int startY, int mouseX, int mouseY) {
        int x = guiLeft + 15;
        int y = startY;

        // Header
        graphics.drawString(this.font, "§6Recent History §7(" + recentHistory.size() + ") §8- click to view", x, y, COLOR_PRIMARY);
        y += 14;

        if (recentHistory.isEmpty()) {
            graphics.drawString(this.font, "§8No bill history yet", x, y + 20, 0xFFAAAAAA);
            return;
        }

        // History list - render with hover detection
        // History entries have 2 lines (title + author) = 11+11+4 = 26px each
        int entryHeight = 26;
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_BILLS, recentHistory.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            SyncLegislatureDataPacket.BillSummary bill = recentHistory.get(i);

            // Check if this entry is hovered
            int entryTop = y;
            int entryBottom = y + entryHeight;
            boolean isHovered = mouseX >= x && mouseX < guiLeft + guiWidth - 30 &&
                                mouseY >= entryTop && mouseY < entryBottom;

            // Draw highlight if hovered
            if (isHovered) {
                graphics.fill(x - 3, entryTop - 1, guiLeft + guiWidth - 25, entryBottom - 1, 0x33FFFFFF);
            }

            y = renderBillEntry(graphics, bill, x, y, mouseX, mouseY, false);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 25, startY + 5, 0xFF888888);
        }
        if (scrollOffset + MAX_VISIBLE_BILLS < recentHistory.size()) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 25, guiTop + guiHeight - 55, 0xFF888888);
        }
    }

    private void renderMembersTab(GuiGraphics graphics, int startY, int mouseX, int mouseY) {
        int x = guiLeft + 15;
        int y = startY;

        // Header
        graphics.drawString(this.font, "§6Legislature Members §7(" + totalMembers + ")", x, y, COLOR_PRIMARY);
        y += 14;

        graphics.drawString(this.font, "§7Voting members are governors and officers", x, y, 0xFFAAAAAA);
        y += 14;

        if (votingMemberNames.isEmpty()) {
            graphics.drawString(this.font, "§8No voting members", x, y + 10, 0xFFAAAAAA);
            return;
        }

        // Members list (2 columns)
        int colWidth = (guiWidth - 40) / 2;
        int col = 0;
        int baseY = y;
        int maxPerCol = 8;
        int count = 0;

        for (String memberName : votingMemberNames) {
            if (count >= maxPerCol * 2) break;

            int drawX = x + (col * colWidth);
            graphics.drawString(this.font, "§7• §f" + memberName, drawX, y, COLOR_TEXT);

            count++;
            if (count % maxPerCol == 0) {
                col++;
                y = baseY;
            } else {
                y += 12;
            }
        }

        if (votingMemberNames.size() > maxPerCol * 2) {
            graphics.drawString(this.font, "§8..." + (votingMemberNames.size() - maxPerCol * 2) + " more",
                x, baseY + (maxPerCol * 12), 0xFFAAAAAA);
        }
    }

    private int renderBillEntry(GuiGraphics graphics, SyncLegislatureDataPacket.BillSummary bill,
                                 int x, int y, int mouseX, int mouseY, boolean showActions) {
        // Bill number and title
        String statusColor = getStatusColor(bill.getStatus());
        graphics.drawString(this.font, "§7" + bill.getBillNumber() + " - " + statusColor + bill.getTitle(), x, y, COLOR_TEXT);
        y += 11;

        // Author and status
        String statusText = bill.getStatus();
        if (bill.needsLeaderAction()) {
            statusText = "§e⚠ Awaiting Leader";
        }
        graphics.drawString(this.font, "§8By: " + bill.getAuthorName() + " | " + statusText, x + 5, y, 0xFFAAAAAA);
        y += 11;

        // Votes and time (for active bills)
        if (showActions && (bill.getStatus().equals("VOTING") || bill.getStatus().equals("DEBATE"))) {
            String timeStr = formatTimeRemaining(bill.getTimeRemaining());
            String voteStr = bill.getStatus().equals("VOTING") ?
                String.format("§aYes: %d §c No: %d", bill.getYesVotes(), bill.getNoVotes()) : "";

            String voteIndicator = "";
            if (bill.getStatus().equals("VOTING")) {
                voteIndicator = bill.hasPlayerVoted() ? " §7[Voted]" : " §e[Vote Now!]";
            }

            graphics.drawString(this.font, "§7" + timeStr + " " + voteStr + voteIndicator, x + 5, y, 0xFFAAAAAA);
            y += 11;
        }

        y += 4; // spacing between entries
        return y;
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "DEBATE" -> "§b";
            case "VOTING" -> "§e";
            case "PASSED" -> "§a";
            case "ENACTED" -> "§2";
            case "VETOED" -> "§c";
            case "FAILED" -> "§4";
            default -> "§7";
        };
    }

    private String formatTimeRemaining(long ms) {
        if (ms <= 0) return "Ended";

        long hours = ms / (1000 * 60 * 60);
        long minutes = (ms / (1000 * 60)) % 60;

        if (hours > 0) {
            return String.format("%dh %dm left", hours, minutes);
        }
        return String.format("%dm left", minutes);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<?> currentList = switch (currentTab) {
            case ACTIVE -> activeBills;
            case HISTORY -> recentHistory;
            case MEMBERS -> votingMemberNames;
        };

        int maxScroll = Math.max(0, currentList.size() - MAX_VISIBLE_BILLS);

        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle clicking on history entries to open detail view
        if (button == 0 && currentTab == Tab.HISTORY && !recentHistory.isEmpty()) {
            int startY = guiTop + 48 + 14; // After tab content header
            int x = guiLeft + 15;
            int entryHeight = 26; // 2 lines (title + author): 11+11+4 spacing

            int endIndex = Math.min(scrollOffset + MAX_VISIBLE_BILLS, recentHistory.size());
            int y = startY;

            for (int i = scrollOffset; i < endIndex; i++) {
                int entryTop = y;
                int entryBottom = y + entryHeight;

                if (mouseX >= x && mouseX < guiLeft + guiWidth - 30 &&
                    mouseY >= entryTop && mouseY < entryBottom) {

                    SyncLegislatureDataPacket.BillSummary bill = recentHistory.get(i);
                    openLawDetail(bill);
                    return true;
                }

                y += entryHeight;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openLawDetail(SyncLegislatureDataPacket.BillSummary bill) {
        // Create LawInfo from BillSummary
        java.util.Map<String, String> policyChanges = new java.util.HashMap<>();
        // Policy changes would need to be sent from server - for now we show what we have
        if (bill.getPolicyChanges() != null) {
            policyChanges = bill.getPolicyChanges();
        }

        LawDetailScreen.LawInfo lawInfo = new LawDetailScreen.LawInfo(
            bill.getBillNumber(),
            bill.getTitle(),
            bill.getDescription(),
            bill.getAuthorName(),
            bill.getEnactedTime(),
            bill.getYesVotes(),
            bill.getNoVotes(),
            bill.getAbstainVotes(),
            bill.isVetoProof(),
            bill.isConstitutionalAmendment(),
            policyChanges,
            bill.getFullText()
        );

        this.minecraft.setScreen(new LawDetailScreen(nationName, lawInfo, () -> {
            this.minecraft.setScreen(this);
        }));
    }

    private void openProposeBillScreen() {
        this.minecraft.setScreen(new ProposeBillScreen(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by network handler when data is received
     */
    public void updateData(boolean isLegislatureMember, boolean isNationLeader,
                           List<SyncLegislatureDataPacket.BillSummary> activeBills,
                           List<SyncLegislatureDataPacket.BillSummary> recentHistory,
                           List<String> votingMemberNames, int totalMembers) {
        this.isLegislatureMember = isLegislatureMember;
        this.isNationLeader = isNationLeader;
        this.activeBills = activeBills;
        this.recentHistory = recentHistory;
        this.votingMemberNames = votingMemberNames;
        this.totalMembers = totalMembers;
        this.dataLoaded = true;

        // Enable propose button if player is a legislature member or nation leader
        // Find and enable the propose button
        this.children().stream()
            .filter(w -> w instanceof Button)
            .map(w -> (Button)w)
            .filter(b -> b.getMessage().getString().contains("Propose"))
            .findFirst()
            .ifPresent(b -> b.active = isLegislatureMember || isNationLeader);

        // Rebuild bill action buttons with new data
        rebuildBillButtons();
    }
}

