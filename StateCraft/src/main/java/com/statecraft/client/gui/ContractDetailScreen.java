package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.ContractActionPacket;
import com.statecraft.network.packets.SyncContractsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Screen for viewing detailed contract information
 * Shows milestones, bids (for pending approval), progress, and action buttons
 */
public class ContractDetailScreen extends StateCraftScreen {

    private final String nationName;
    private final SyncContractsPacket.ContractSummary contract;
    private final boolean isLegislatureMember;
    private final boolean isNationLeader;

    // Tab for different detail views
    private DetailTab currentTab = DetailTab.INFO;
    private int bidScrollOffset = 0;
    private static final int MAX_VISIBLE_BIDS = 4;

    // Selected bid for approval
    private String selectedBidId = null;

    private enum DetailTab {
        INFO("Information"),
        BIDS("Bids"),
        PROGRESS("Progress");

        final String name;
        DetailTab(String name) { this.name = name; }
    }

    public ContractDetailScreen(String nationName, SyncContractsPacket.ContractSummary contract,
                                 boolean isLegislatureMember, boolean isNationLeader) {
        super(Component.literal("Contract: " + contract.getContractNumber()));
        this.nationName = nationName;
        this.contract = contract;
        this.isLegislatureMember = isLegislatureMember;
        this.isNationLeader = isNationLeader;
        this.guiWidth = 360;
        this.guiHeight = 280;
    }

    @Override
    protected void init() {
        super.init();

        int tabY = guiTop + 25;
        int tabWidth = 80;
        int tabSpacing = 5;
        int startX = guiLeft + 15;

        // Tab buttons - only show relevant tabs
        this.addRenderableWidget(createButton(
            startX, tabY, tabWidth, 16,
            Component.literal("Info"),
            btn -> switchTab(DetailTab.INFO)
        ));

        // Show bids tab for pending approval contracts
        if (contract.getStatus().equals("PENDING_APPROVAL") || contract.getStatus().equals("BIDDING")) {
            this.addRenderableWidget(createButton(
                startX + tabWidth + tabSpacing, tabY, tabWidth, 16,
                Component.literal("Bids (" + contract.getBidCount() + ")"),
                btn -> switchTab(DetailTab.BIDS)
            ));
        }

        // Show progress tab for active contracts
        if (contract.getStatus().equals("ACTIVE")) {
            this.addRenderableWidget(createButton(
                startX + (tabWidth + tabSpacing) * 2, tabY, tabWidth, 16,
                Component.literal("Progress"),
                btn -> switchTab(DetailTab.PROGRESS)
            ));
        }

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        // Action buttons based on status and role
        addActionButtons(buttonY);

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 70, buttonY, 55, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    private void addActionButtons(int buttonY) {
        int btnX = guiLeft + 15;

        switch (contract.getStatus()) {
            case "BIDDING":
                if (isLegislatureMember || isNationLeader) {
                    // Close bidding early
                    this.addRenderableWidget(createButton(
                        btnX, buttonY, 90, 20,
                        Component.literal("§cClose Bidding"),
                        btn -> closeBidding()
                    ));
                }
                break;

            case "PENDING_APPROVAL":
                if (isLegislatureMember || isNationLeader) {
                    // Approve selected bid (only enabled when a bid is selected)
                    Button approveBtn = this.addRenderableWidget(createButton(
                        btnX, buttonY, 90, 20,
                        Component.literal("§aApprove Bid"),
                        btn -> approveSelectedBid()
                    ));
                    approveBtn.active = false; // Enabled when bid is selected
                }
                break;

            case "ACTIVE":
                // For contractor: update progress, complete milestones
                if (isLegislatureMember || isNationLeader) {
                    this.addRenderableWidget(createButton(
                        btnX, buttonY, 90, 20,
                        Component.literal("§cCancel"),
                        btn -> cancelContract()
                    ));
                }
                break;

            case "DRAFT":
                if (isLegislatureMember || isNationLeader) {
                    this.addRenderableWidget(createButton(
                        btnX, buttonY, 90, 20,
                        Component.literal("§aOpen Bidding"),
                        btn -> openForBidding()
                    ));
                }
                break;
        }
    }

    private void switchTab(DetailTab tab) {
        this.currentTab = tab;
        this.bidScrollOffset = 0;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Title
        String statusColor = getStatusColor(contract.getStatus());
        graphics.drawString(this.font, statusColor + contract.getStatus().replace("_", " "),
            guiLeft + guiWidth - font.width(contract.getStatus().replace("_", " ")) - 15, guiTop + 8, COLOR_TEXT);

        int tabContentY = guiTop + 48;
        renderDivider(graphics, guiLeft + 10, tabContentY - 3, guiWidth - 20);

        switch (currentTab) {
            case INFO -> renderInfoTab(graphics, tabContentY);
            case BIDS -> renderBidsTab(graphics, tabContentY, mouseX, mouseY);
            case PROGRESS -> renderProgressTab(graphics, tabContentY);
        }
    }

    private void renderInfoTab(GuiGraphics graphics, int startY) {
        int x = guiLeft + 15;
        int y = startY;
        int labelWidth = 70;

        // Title
        graphics.drawString(this.font, "§6" + contract.getTitle(), x, y, COLOR_TEXT);
        y += 14;

        // Description
        if (contract.getDescription() != null && !contract.getDescription().isEmpty()) {
            String desc = contract.getDescription();
            if (desc.length() > 80) {
                desc = desc.substring(0, 77) + "...";
            }
            graphics.drawString(this.font, "§7" + desc, x, y, 0xFFAAAAAA);
            y += 14;
        }

        y += 4;
        renderDivider(graphics, x, y, guiWidth - 30);
        y += 6;

        // Contract details in two columns
        int col1X = x;
        int col2X = x + 160;

        // Column 1
        graphics.drawString(this.font, "§7Budget:", col1X, y, 0xFFAAAAAA);
        graphics.drawString(this.font, String.format("§a$%.2f", contract.getBudget()), col1X + labelWidth, y, COLOR_TEXT);
        y += 12;

        graphics.drawString(this.font, "§7Chunks:", col1X, y, 0xFFAAAAAA);
        graphics.drawString(this.font, "§f" + contract.getChunkCount(), col1X + labelWidth, y, COLOR_TEXT);
        y += 12;

        graphics.drawString(this.font, "§7Created by:", col1X, y, 0xFFAAAAAA);
        graphics.drawString(this.font, "§f" + contract.getCreatorName(), col1X + labelWidth, y, COLOR_TEXT);
        y += 12;

        // Reset Y for column 2
        int col2Y = startY + 32;

        if (contract.getBondAmount() > 0) {
            graphics.drawString(this.font, "§7Bond:", col2X, col2Y, 0xFFAAAAAA);
            graphics.drawString(this.font, String.format("§c$%.2f", contract.getBondAmount()), col2X + labelWidth, col2Y, COLOR_TEXT);
            col2Y += 12;
        }

        graphics.drawString(this.font, "§7Bids:", col2X, col2Y, 0xFFAAAAAA);
        graphics.drawString(this.font, "§f" + contract.getBidCount(), col2X + labelWidth, col2Y, COLOR_TEXT);
        col2Y += 12;

        if (contract.getContractorName() != null && !contract.getContractorName().isEmpty()) {
            graphics.drawString(this.font, "§7Contractor:", col2X, col2Y, 0xFFAAAAAA);
            graphics.drawString(this.font, "§b" + contract.getContractorName(), col2X + labelWidth, col2Y, COLOR_TEXT);
            col2Y += 12;
        }

        // Time info
        y += 6;
        if (contract.getTimeRemaining() > 0) {
            String timeLabel = contract.getStatus().equals("BIDDING") ? "Bidding ends:" : "Deadline:";
            graphics.drawString(this.font, "§7" + timeLabel + " §f" + formatTimeRemaining(contract.getTimeRemaining()), x, y + 20, COLOR_TEXT);
        }
    }

    private void renderBidsTab(GuiGraphics graphics, int startY, int mouseX, int mouseY) {
        int x = guiLeft + 15;
        int y = startY;

        List<SyncContractsPacket.BidSummary> bids = contract.getBids();

        graphics.drawString(this.font, "§6Submitted Bids §7(" + bids.size() + ")", x, y, COLOR_PRIMARY);
        y += 14;

        if (bids.isEmpty()) {
            graphics.drawString(this.font, "§8No bids submitted yet", x, y + 10, 0xFFAAAAAA);
            return;
        }

        // Bid list
        int endIndex = Math.min(bidScrollOffset + MAX_VISIBLE_BIDS, bids.size());
        for (int i = bidScrollOffset; i < endIndex; i++) {
            SyncContractsPacket.BidSummary bid = bids.get(i);
            y = renderBidEntry(graphics, bid, x, y, mouseX, mouseY, i);
        }

        // Scroll indicators
        if (bidScrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 25, startY + 5, 0xFF888888);
        }
        if (bidScrollOffset + MAX_VISIBLE_BIDS < bids.size()) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 25, guiTop + guiHeight - 55, 0xFF888888);
        }
    }

    private int renderBidEntry(GuiGraphics graphics, SyncContractsPacket.BidSummary bid, int x, int y,
                                int mouseX, int mouseY, int index) {
        // Selection highlight
        boolean isSelected = bid.getBidId().equals(selectedBidId);
        if (isSelected) {
            graphics.fill(x - 2, y - 2, guiLeft + guiWidth - 15, y + 40, 0x44FFFF00);
        }

        // Bidder name and amount
        String bidColor = isSelected ? "§e" : "§f";
        graphics.drawString(this.font, bidColor + bid.getBidderName() + " §7- §a$" + String.format("%.0f", bid.getBidAmount()), x, y, COLOR_TEXT);
        y += 11;

        // Duration and bond status
        String bondStatus = bid.isBondPaid() ? "§aBond Paid" : "§cNo Bond";
        graphics.drawString(this.font, "§7Duration: §f" + bid.getProposedDays() + " days §8| " + bondStatus, x + 5, y, 0xFFAAAAAA);
        y += 11;

        // Proposal preview
        String proposal = bid.getProposal();
        if (proposal != null && !proposal.isEmpty()) {
            if (proposal.length() > 50) {
                proposal = proposal.substring(0, 47) + "...";
            }
            graphics.drawString(this.font, "§8\"" + proposal + "\"", x + 5, y, 0xFF666666);
        }
        y += 14;

        // Clickable area for selection (for legislature)
        if ((isLegislatureMember || isNationLeader) && contract.getStatus().equals("PENDING_APPROVAL")) {
            int entryTop = y - 36;
            int entryBottom = y;
            if (mouseX >= x - 2 && mouseX <= guiLeft + guiWidth - 15 &&
                mouseY >= entryTop && mouseY <= entryBottom) {
                // Hover highlight
                if (!isSelected) {
                    graphics.fill(x - 2, entryTop, guiLeft + guiWidth - 15, entryBottom, 0x22FFFFFF);
                }
            }
        }

        y += 4;
        return y;
    }

    private void renderProgressTab(GuiGraphics graphics, int startY) {
        int x = guiLeft + 15;
        int y = startY;

        graphics.drawString(this.font, "§6Contract Progress", x, y, COLOR_PRIMARY);
        y += 16;

        // Overall progress bar
        int barWidth = guiWidth - 60;
        int barHeight = 16;
        renderProgressBar(graphics, x, y, barWidth, barHeight, contract.getProgressPercent() / 100f, COLOR_SECONDARY);

        // Progress percentage
        String progressText = contract.getProgressPercent() + "%";
        graphics.drawCenteredString(this.font, "§f" + progressText, x + barWidth / 2, y + 4, COLOR_TEXT);
        y += 24;

        // Milestones
        graphics.drawString(this.font, "§7Milestones:", x, y, 0xFFAAAAAA);
        y += 14;

        int[] milestones = {25, 50, 75, 100};
        String[] milestoneNames = {"Foundation (25%)", "Structure (50%)", "Details (75%)", "Complete (100%)"};

        for (int i = 0; i < milestones.length; i++) {
            boolean completed = contract.getProgressPercent() >= milestones[i];
            String status = completed ? "§a✓ " : "§7○ ";
            String color = completed ? "§a" : (contract.getProgressPercent() >= milestones[i] - 25 ? "§e" : "§7");
            graphics.drawString(this.font, status + color + milestoneNames[i], x + 10, y, COLOR_TEXT);
            y += 12;
        }

        // Contractor info
        y += 10;
        if (contract.getContractorName() != null && !contract.getContractorName().isEmpty()) {
            graphics.drawString(this.font, "§7Contractor: §b" + contract.getContractorName(), x, y, COLOR_TEXT);
            y += 12;
        }

        // Deadline
        if (contract.getTimeRemaining() > 0) {
            String timeColor = contract.getTimeRemaining() < 86400000 ? "§c" : "§f"; // Red if < 1 day
            graphics.drawString(this.font, "§7Deadline: " + timeColor + formatTimeRemaining(contract.getTimeRemaining()) + " remaining", x, y, COLOR_TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle bid selection for pending approval
        if ((isLegislatureMember || isNationLeader) && contract.getStatus().equals("PENDING_APPROVAL")
            && currentTab == DetailTab.BIDS) {

            int startY = guiTop + 62; // Approximate start of bid entries
            List<SyncContractsPacket.BidSummary> bids = contract.getBids();
            int endIndex = Math.min(bidScrollOffset + MAX_VISIBLE_BIDS, bids.size());

            int y = startY;
            for (int i = bidScrollOffset; i < endIndex; i++) {
                int entryHeight = 48; // Approximate height per entry
                if (mouseX >= guiLeft + 15 && mouseX <= guiLeft + guiWidth - 15 &&
                    mouseY >= y && mouseY <= y + entryHeight) {
                    selectedBidId = bids.get(i).getBidId();

                    // Enable approve button
                    this.children().stream()
                        .filter(w -> w instanceof Button)
                        .map(w -> (Button)w)
                        .filter(b -> b.getMessage().getString().contains("Approve"))
                        .findFirst()
                        .ifPresent(b -> b.active = true);

                    return true;
                }
                y += entryHeight;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == DetailTab.BIDS) {
            List<SyncContractsPacket.BidSummary> bids = contract.getBids();
            int maxScroll = Math.max(0, bids.size() - MAX_VISIBLE_BIDS);

            if (delta > 0) {
                bidScrollOffset = Math.max(0, bidScrollOffset - 1);
            } else {
                bidScrollOffset = Math.min(maxScroll, bidScrollOffset + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "DRAFT" -> "§7";
            case "BIDDING" -> "§e";
            case "PENDING_APPROVAL" -> "§6";
            case "ACTIVE" -> "§b";
            case "COMPLETED" -> "§a";
            case "CANCELLED" -> "§c";
            case "FAILED" -> "§4";
            default -> "§7";
        };
    }

    private String formatTimeRemaining(long ms) {
        if (ms <= 0) return "Ended";

        long days = ms / (1000 * 60 * 60 * 24);
        long hours = (ms / (1000 * 60 * 60)) % 24;
        long minutes = (ms / (1000 * 60)) % 60;

        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }

    // Action methods
    private void openForBidding() {
        // Default 7 day bidding period
        long biddingDuration = 7L * 24 * 60 * 60 * 1000;
        NetworkHandler.sendToServer(new ContractActionPacket(
            nationName, contract.getContractId(),
            ContractActionPacket.ActionType.OPEN_BIDDING,
            "", 0, biddingDuration
        ));
        goBack();
    }

    private void closeBidding() {
        NetworkHandler.sendToServer(new ContractActionPacket(
            nationName, contract.getContractId(),
            ContractActionPacket.ActionType.CLOSE_BIDDING
        ));
        goBack();
    }

    private void approveSelectedBid() {
        if (selectedBidId == null) return;

        // Default 30 day deadline
        long deadlineDuration = 30L * 24 * 60 * 60 * 1000;
        NetworkHandler.sendToServer(new ContractActionPacket(
            nationName, contract.getContractId(),
            ContractActionPacket.ActionType.APPROVE_BID,
            selectedBidId, 0, deadlineDuration
        ));
        goBack();
    }

    private void cancelContract() {
        NetworkHandler.sendToServer(new ContractActionPacket(
            nationName, contract.getContractId(),
            ContractActionPacket.ActionType.CANCEL_CONTRACT
        ));
        goBack();
    }

    private void goBack() {
        this.minecraft.setScreen(new ContractsMainScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

