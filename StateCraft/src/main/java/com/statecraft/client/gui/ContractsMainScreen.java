package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestContractsPacket;
import com.statecraft.network.packets.SyncContractsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Government Contracts screen - Hub for viewing and managing contracts
 * Shows different tabs: Open Bidding, Pending Approval, Active, My Contracts, History
 */
public class ContractsMainScreen extends StateCraftScreen {

    private final String nationName;

    // Data from server
    private boolean dataLoaded = false;
    private boolean isLegislatureMember = false;
    private boolean isNationLeader = false;
    private double nationTreasuryBalance = 0;
    private List<SyncContractsPacket.ContractSummary> openBidding = new ArrayList<>();
    private List<SyncContractsPacket.ContractSummary> pendingApproval = new ArrayList<>();
    private List<SyncContractsPacket.ContractSummary> activeContracts = new ArrayList<>();
    private List<SyncContractsPacket.ContractSummary> myContracts = new ArrayList<>();
    private List<SyncContractsPacket.ContractSummary> history = new ArrayList<>();

    // UI State
    private Tab currentTab = Tab.OPEN_BIDDING;
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_CONTRACTS = 4;

    // Dynamic action buttons
    private List<Button> contractButtons = new ArrayList<>();

    private enum Tab {
        OPEN_BIDDING("Bidding", "Open for bids"),
        PENDING("Pending", "Awaiting approval"),
        ACTIVE("Active", "In progress"),
        MY_CONTRACTS("My Work", "Your contracts"),
        HISTORY("History", "Completed/Cancelled");

        final String shortName;
        final String description;
        Tab(String shortName, String description) {
            this.shortName = shortName;
            this.description = description;
        }
    }

    public ContractsMainScreen(String nationName) {
        super(Component.literal("Government Contracts"));
        this.nationName = nationName;
        this.guiWidth = 360;
        this.guiHeight = 280;
    }

    @Override
    protected void init() {
        super.init();

        // Request data from server
        NetworkHandler.sendToServer(new RequestContractsPacket(nationName));

        int tabY = guiTop + 25;
        int tabWidth = 58;
        int tabSpacing = 3;
        int startX = guiLeft + 15;

        // Tab buttons
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int tabX = startX + i * (tabWidth + tabSpacing);
            this.addRenderableWidget(createButton(
                tabX, tabY, tabWidth, 16,
                Component.literal(tab.shortName),
                btn -> switchTab(tab)
            ));
        }

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        // Create Contract button (only for legislature members/leaders)
        Button createButton = this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 100, 20,
            Component.literal("§a+ New Contract"),
            btn -> openCreateContractScreen()
        ));
        createButton.active = false; // Enabled after data loads

        // Refresh button
        this.addRenderableWidget(createButton(
            guiLeft + 120, buttonY, 60, 20,
            Component.literal("Refresh"),
            btn -> refreshData()
        ));

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
        rebuildContractButtons();
    }

    private void rebuildContractButtons() {
        // Remove old buttons
        for (Button btn : contractButtons) {
            this.removeWidget(btn);
        }
        contractButtons.clear();

        if (!dataLoaded) return;

        List<SyncContractsPacket.ContractSummary> currentList = getCurrentList();
        int buttonBaseY = guiTop + 60;
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_CONTRACTS, currentList.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            SyncContractsPacket.ContractSummary contract = currentList.get(i);
            int listIndex = i - scrollOffset;
            int yOffset = listIndex * 50;

            int btnX = guiLeft + guiWidth - 90;
            int btnY = buttonBaseY + yOffset + 28;

            switch (currentTab) {
                case OPEN_BIDDING:
                    // View Details / Submit Bid button
                    if (!contract.hasPlayerBid()) {
                        Button bidBtn = createButton(btnX, btnY, 75, 14,
                            Component.literal("§aSubmit Bid"),
                            btn -> openSubmitBidScreen(contract));
                        this.addRenderableWidget(bidBtn);
                        contractButtons.add(bidBtn);
                    } else {
                        Button viewBtn = createButton(btnX, btnY, 75, 14,
                            Component.literal("§7View/Edit Bid"),
                            btn -> openSubmitBidScreen(contract));
                        this.addRenderableWidget(viewBtn);
                        contractButtons.add(viewBtn);
                    }
                    break;

                case PENDING:
                    // For legislature: View bids and approve
                    if (isLegislatureMember || isNationLeader) {
                        Button reviewBtn = createButton(btnX, btnY, 75, 14,
                            Component.literal("§eReview Bids"),
                            btn -> openContractDetailScreen(contract));
                        this.addRenderableWidget(reviewBtn);
                        contractButtons.add(reviewBtn);
                    } else {
                        Button viewBtn = createButton(btnX, btnY, 75, 14,
                            Component.literal("§7View"),
                            btn -> openContractDetailScreen(contract));
                        this.addRenderableWidget(viewBtn);
                        contractButtons.add(viewBtn);
                    }
                    break;

                case ACTIVE:
                case MY_CONTRACTS:
                    // View progress
                    Button progressBtn = createButton(btnX, btnY, 75, 14,
                        Component.literal("§bView Progress"),
                        btn -> openContractDetailScreen(contract));
                    this.addRenderableWidget(progressBtn);
                    contractButtons.add(progressBtn);
                    break;

                case HISTORY:
                    // View details
                    Button detailBtn = createButton(btnX, btnY, 75, 14,
                        Component.literal("§7View Details"),
                        btn -> openContractDetailScreen(contract));
                    this.addRenderableWidget(detailBtn);
                    contractButtons.add(detailBtn);
                    break;
            }
        }
    }

    private List<SyncContractsPacket.ContractSummary> getCurrentList() {
        return switch (currentTab) {
            case OPEN_BIDDING -> openBidding;
            case PENDING -> pendingApproval;
            case ACTIVE -> activeContracts;
            case MY_CONTRACTS -> myContracts;
            case HISTORY -> history;
        };
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Treasury balance display
        String treasuryText = String.format("§6Treasury: §a$%.2f", nationTreasuryBalance);
        graphics.drawString(this.font, treasuryText, guiLeft + guiWidth - font.width(treasuryText.replaceAll("§.", "")) - 15, guiTop + 8, COLOR_TEXT);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading contract data...",
                this.width / 2, guiTop + 100, COLOR_TEXT);
            return;
        }

        // Tab description
        int tabContentY = guiTop + 48;
        renderDivider(graphics, guiLeft + 10, tabContentY - 3, guiWidth - 20);

        // Render current tab content
        renderContractList(graphics, tabContentY, mouseX, mouseY);
    }

    private void renderContractList(GuiGraphics graphics, int startY, int mouseX, int mouseY) {
        List<SyncContractsPacket.ContractSummary> currentList = getCurrentList();

        int x = guiLeft + 15;
        int y = startY;

        // Header with count
        String headerText = currentTab.description + " §7(" + currentList.size() + ")";
        graphics.drawString(this.font, "§6" + headerText, x, y, COLOR_PRIMARY);
        y += 14;

        if (currentList.isEmpty()) {
            String emptyMsg = switch (currentTab) {
                case OPEN_BIDDING -> "§8No contracts open for bidding";
                case PENDING -> "§8No contracts awaiting approval";
                case ACTIVE -> "§8No active contracts";
                case MY_CONTRACTS -> "§8You have no contracts as a builder";
                case HISTORY -> "§8No contract history";
            };
            graphics.drawString(this.font, emptyMsg, x, y + 20, 0xFFAAAAAA);
            return;
        }

        // Contract list
        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_CONTRACTS, currentList.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            SyncContractsPacket.ContractSummary contract = currentList.get(i);
            y = renderContractEntry(graphics, contract, x, y, mouseX, mouseY);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲ Scroll Up", guiLeft + guiWidth / 2, startY - 8, 0xFF888888);
        }
        if (scrollOffset + MAX_VISIBLE_CONTRACTS < currentList.size()) {
            graphics.drawCenteredString(this.font, "§7▼ Scroll Down", guiLeft + guiWidth / 2, guiTop + guiHeight - 50, 0xFF888888);
        }
    }

    private int renderContractEntry(GuiGraphics graphics, SyncContractsPacket.ContractSummary contract,
                                     int x, int y, int mouseX, int mouseY) {
        // Contract number and title
        String statusColor = getStatusColor(contract.getStatus());
        String titleLine = "§7" + contract.getContractNumber() + " §f- " + statusColor + contract.getTitle();
        if (titleLine.length() > 50) {
            titleLine = titleLine.substring(0, 47) + "...";
        }
        graphics.drawString(this.font, titleLine, x, y, COLOR_TEXT);
        y += 11;

        // Budget and chunks
        String budgetLine = String.format("§6Budget: §a$%.0f §8| §7%d chunks", contract.getBudget(), contract.getChunkCount());
        if (contract.getBondAmount() > 0) {
            budgetLine += String.format(" §8| §cBond: $%.0f", contract.getBondAmount());
        }
        graphics.drawString(this.font, budgetLine, x + 5, y, 0xFFAAAAAA);
        y += 11;

        // Status-specific info
        switch (currentTab) {
            case OPEN_BIDDING:
                String bidInfo = String.format("§7Bids: %d", contract.getBidCount());
                if (contract.hasPlayerBid()) {
                    bidInfo += " §a(You bid)";
                }
                String timeStr = formatTimeRemaining(contract.getTimeRemaining());
                graphics.drawString(this.font, bidInfo + " §8| §7" + timeStr, x + 5, y, 0xFFAAAAAA);
                break;

            case PENDING:
                graphics.drawString(this.font, String.format("§7%d bids to review", contract.getBidCount()), x + 5, y, 0xFFAAAAAA);
                break;

            case ACTIVE:
            case MY_CONTRACTS:
                // Progress bar
                int barX = x + 5;
                int barY = y;
                int barWidth = 100;
                int barHeight = 8;
                renderProgressBar(graphics, barX, barY, barWidth, barHeight, contract.getProgressPercent() / 100f, COLOR_SECONDARY);
                graphics.drawString(this.font, contract.getProgressPercent() + "% ", barX + barWidth + 5, barY, 0xFFAAAAAA);

                // Contractor name or deadline
                String contractorInfo = contract.getContractorName() != null && !contract.getContractorName().isEmpty()
                    ? "§7Builder: §f" + contract.getContractorName()
                    : "";
                String deadlineStr = formatTimeRemaining(contract.getTimeRemaining());
                graphics.drawString(this.font, contractorInfo + " §8| §7" + deadlineStr + " left", barX + barWidth + 30, barY, 0xFFAAAAAA);
                break;

            case HISTORY:
                graphics.drawString(this.font, "§7By: " + contract.getCreatorName() +
                    (contract.getContractorName() != null && !contract.getContractorName().isEmpty()
                        ? " §8→ §7" + contract.getContractorName() : ""), x + 5, y, 0xFFAAAAAA);
                break;
        }
        y += 14;

        y += 4; // spacing between entries
        return y;
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
            return String.format("%dd %dh left", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm left", hours, minutes);
        }
        return String.format("%dm left", minutes);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<SyncContractsPacket.ContractSummary> currentList = getCurrentList();
        int maxScroll = Math.max(0, currentList.size() - MAX_VISIBLE_CONTRACTS);

        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }

        rebuildContractButtons();
        return true;
    }

    private void openCreateContractScreen() {
        this.minecraft.setScreen(new CreateContractScreen(nationName));
    }

    private void openSubmitBidScreen(SyncContractsPacket.ContractSummary contract) {
        this.minecraft.setScreen(new SubmitBidScreen(nationName, contract));
    }

    private void openContractDetailScreen(SyncContractsPacket.ContractSummary contract) {
        this.minecraft.setScreen(new ContractDetailScreen(nationName, contract, isLegislatureMember, isNationLeader));
    }

    private void refreshData() {
        this.dataLoaded = false;
        NetworkHandler.sendToServer(new RequestContractsPacket(nationName));
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by client packet handler when contract data is received
     */
    public void updateData(SyncContractsPacket packet) {
        this.isLegislatureMember = packet.isLegislatureMember();
        this.isNationLeader = packet.isNationLeader();
        this.nationTreasuryBalance = packet.getNationTreasuryBalance();
        this.openBidding = new ArrayList<>(packet.getOpenBidding());
        this.pendingApproval = new ArrayList<>(packet.getPendingApproval());
        this.activeContracts = new ArrayList<>(packet.getActiveContracts());
        this.myContracts = new ArrayList<>(packet.getMyContracts());
        this.history = new ArrayList<>(packet.getHistory());
        this.dataLoaded = true;

        // Enable create button if authorized
        this.children().stream()
            .filter(w -> w instanceof Button)
            .map(w -> (Button)w)
            .filter(b -> b.getMessage().getString().contains("New Contract"))
            .findFirst()
            .ifPresent(b -> b.active = (isLegislatureMember || isNationLeader));

        rebuildContractButtons();
    }
}

