package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.SubmitContractBidPacket;
import com.statecraft.network.packets.SyncContractsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Screen for submitting a bid on a government contract
 * Players can set their bid amount, proposed timeline, and provide a proposal
 */
public class SubmitBidScreen extends StateCraftScreen {

    private final String nationName;
    private final SyncContractsPacket.ContractSummary contract;

    // Input fields
    private EditBox bidAmountField;
    private EditBox proposedDaysField;
    private EditBox proposalField;

    // UI state
    private String errorMessage = "";
    private boolean hasExistingBid;

    public SubmitBidScreen(String nationName, SyncContractsPacket.ContractSummary contract) {
        super(Component.literal("Submit Bid"));
        this.nationName = nationName;
        this.contract = contract;
        this.hasExistingBid = contract.hasPlayerBid();
        this.guiWidth = 320;
        this.guiHeight = 280;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = guiWidth - 40;
        int x = guiLeft + 20;
        int y = guiTop + 60;

        // Bid amount field
        bidAmountField = new EditBox(this.font, x, y, 120, 18, Component.literal("Bid Amount"));
        bidAmountField.setMaxLength(12);
        bidAmountField.setHint(Component.literal("Your bid $"));
        bidAmountField.setFilter(this::isValidNumberInput);
        // Default to contract budget
        bidAmountField.setValue(String.format("%.0f", contract.getBudget()));
        this.addRenderableWidget(bidAmountField);

        // Proposed days field
        proposedDaysField = new EditBox(this.font, x + 170, y, 80, 18, Component.literal("Days"));
        proposedDaysField.setMaxLength(4);
        proposedDaysField.setHint(Component.literal("Days"));
        proposedDaysField.setFilter(this::isValidIntegerInput);
        proposedDaysField.setValue("30"); // Default 30 days
        this.addRenderableWidget(proposedDaysField);
        y += 35;

        // Proposal field (multi-line simulation using EditBox)
        proposalField = new EditBox(this.font, x, y, fieldWidth, 18, Component.literal("Proposal"));
        proposalField.setMaxLength(500);
        proposalField.setHint(Component.literal("Describe your plan and qualifications..."));
        this.addRenderableWidget(proposalField);
        y += 28;

        // Show bond requirement info
        if (contract.getBondAmount() > 0) {
            y += 20;
        }

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        // Submit bid button
        String buttonText = hasExistingBid ? "§eUpdate Bid" : "§aSubmit Bid";
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 120, buttonY, 105, 20,
            Component.literal(buttonText),
            btn -> submitBid()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 60, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    private boolean isValidNumberInput(String s) {
        if (s.isEmpty()) return true;
        try {
            if (s.equals(".") || s.endsWith(".")) {
                String test = s.equals(".") ? "0" : s.substring(0, s.length() - 1);
                Double.parseDouble(test);
                return true;
            }
            double val = Double.parseDouble(s);
            return val >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidIntegerInput(String s) {
        if (s.isEmpty()) return true;
        try {
            int val = Integer.parseInt(s);
            return val > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void submitBid() {
        // Validate bid amount
        double bidAmount;
        try {
            String bidStr = bidAmountField.getValue().isEmpty() ? "0" : bidAmountField.getValue();
            bidAmount = Double.parseDouble(bidStr);
            if (bidAmount <= 0) {
                errorMessage = "Bid amount must be greater than $0";
                return;
            }
            if (bidAmount > contract.getBudget() * 2) {
                errorMessage = "Bid cannot exceed 200% of budget";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid bid amount";
            return;
        }

        // Validate proposed days
        int proposedDays;
        try {
            String daysStr = proposedDaysField.getValue().isEmpty() ? "0" : proposedDaysField.getValue();
            proposedDays = Integer.parseInt(daysStr);
            if (proposedDays <= 0) {
                errorMessage = "Proposed days must be at least 1";
                return;
            }
            if (proposedDays > 365) {
                errorMessage = "Proposed duration cannot exceed 365 days";
                return;
            }
        } catch (NumberFormatException e) {
            errorMessage = "Invalid number of days";
            return;
        }

        // Validate proposal
        String proposal = proposalField.getValue().trim();
        if (proposal.isEmpty()) {
            errorMessage = "Please provide a proposal description";
            return;
        }
        if (proposal.length() < 10) {
            errorMessage = "Proposal must be at least 10 characters";
            return;
        }

        // Send packet
        NetworkHandler.sendToServer(new SubmitContractBidPacket(
            nationName,
            contract.getContractId(),
            bidAmount,
            proposal,
            proposedDays
        ));

        // Return to contracts list
        goBack();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + 20;
        int y = guiTop + 25;

        // Contract summary header
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        // Contract info
        graphics.drawString(this.font, "§6Contract: §f" + contract.getContractNumber(), x, y, COLOR_TEXT);
        y += 12;

        String titleDisplay = contract.getTitle();
        if (titleDisplay.length() > 40) {
            titleDisplay = titleDisplay.substring(0, 37) + "...";
        }
        graphics.drawString(this.font, "§7" + titleDisplay, x, y, 0xFFAAAAAA);
        y += 16;

        // Budget info
        graphics.drawString(this.font, String.format("§7Budget: §a$%.2f §8| §7%d chunks",
            contract.getBudget(), contract.getChunkCount()), x, y, COLOR_TEXT);
        y += 14;

        // Field labels
        graphics.drawString(this.font, "§7Your Bid ($):", x, y, 0xFFAAAAAA);
        graphics.drawString(this.font, "§7Timeline (days):", x + 170, y, 0xFFAAAAAA);
        y += 35;

        graphics.drawString(this.font, "§7Your Proposal:", x, y, 0xFFAAAAAA);
        y += 50;

        // Bond requirement info
        if (contract.getBondAmount() > 0) {
            renderDivider(graphics, x, y, guiWidth - 40);
            y += 8;

            graphics.drawString(this.font, "§cBond Required:", x, y, COLOR_WARNING);
            y += 12;
            graphics.drawString(this.font, String.format("§7This contract requires a §c$%.2f§7 bond.", contract.getBondAmount()), x, y, 0xFFAAAAAA);
            y += 11;
            graphics.drawString(this.font, "§8The bond will be held during the contract and", x, y, 0xFF666666);
            y += 10;
            graphics.drawString(this.font, "§8returned upon successful completion.", x, y, 0xFF666666);
        }

        // Bid comparison info
        y = guiTop + guiHeight - 75;
        renderDivider(graphics, x, y, guiWidth - 40);
        y += 6;

        if (contract.getBidCount() > 0) {
            String bidInfo = hasExistingBid
                ? "§7You have an existing bid. " + (contract.getBidCount() - 1) + " other bid(s)."
                : "§7" + contract.getBidCount() + " bid(s) already submitted.";
            graphics.drawString(this.font, bidInfo, x, y, 0xFFAAAAAA);
        } else {
            graphics.drawString(this.font, "§7Be the first to bid on this contract!", x, y, 0xFFAAAAAA);
        }

        // Compensation type
        y += 12;
        graphics.drawString(this.font, "§8Payment: " + getCompensationDescription(), x, y, 0xFF666666);

        // Error message
        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage, this.width / 2, guiTop + guiHeight - 45, COLOR_WARNING);
        }
    }

    private String getCompensationDescription() {
        // This info would come from the contract, but for now use a default
        return "Milestone payments at 25%, 50%, 75%, 100%";
    }

    private void goBack() {
        this.minecraft.setScreen(new ContractsMainScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

