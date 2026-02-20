package com.statecraft.economy.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirmation dialog shown before executing large transactions.
 * Displays transaction details and requires explicit confirmation.
 * On confirm, executes the pending action and returns to the ATM.
 * On cancel, returns to the ATM without executing.
 */
public class ConfirmTransactionScreen extends Screen {

    // Colors (matching SimpleATMScreen style)
    private static final int COLOR_PRIMARY = 0xFF4A90D9;
    private static final int COLOR_WARNING = 0xFFE74C3C;
    private static final int COLOR_WARNING_BG = 0x40E74C3C;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_PANEL = 0xEE1A1A2E;
    private static final int COLOR_BORDER = 0xFF3D3D5C;
    private static final int COLOR_CONFIRM = 0xFF2ECC71;
    private static final int COLOR_CANCEL = 0xFFE74C3C;

    // Transaction details
    private final String actionType;      // "Deposit", "Withdrawal", "Transfer"
    private final String formattedAmount;
    private final String accountName;     // Source or destination account name
    private final String recipientName;   // For transfers, the recipient name (null otherwise)
    private final Runnable onConfirm;     // Action to execute on confirm
    private final Screen parentScreen;    // ATM screen to return to

    // Layout
    private int panelLeft;
    private int panelTop;
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 150;

    /**
     * Create a confirmation dialog for a large transaction.
     *
     * @param actionType     "Deposit", "Withdrawal", or "Transfer"
     * @param formattedAmount The formatted currency amount (e.g., "$50,000.00")
     * @param accountName    The account being operated on (e.g., "Personal Account", "Nation Treasury")
     * @param recipientName  For transfers, the recipient name; null for deposit/withdraw
     * @param onConfirm      Runnable to execute when user confirms
     * @param parentScreen   The ATM screen to return to on cancel
     */
    public ConfirmTransactionScreen(String actionType, String formattedAmount, String accountName,
                                     String recipientName, Runnable onConfirm, Screen parentScreen) {
        super(Component.literal("Confirm Transaction"));
        this.actionType = actionType;
        this.formattedAmount = formattedAmount;
        this.accountName = accountName;
        this.recipientName = recipientName;
        this.onConfirm = onConfirm;
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelTop = (this.height - PANEL_HEIGHT) / 2;

        int buttonWidth = 90;
        int buttonY = panelTop + PANEL_HEIGHT - 32;
        int centerX = panelLeft + PANEL_WIDTH / 2;

        // Confirm button
        this.addRenderableWidget(Button.builder(Component.literal("§a✓ Confirm"),
            btn -> {
                onConfirm.run();
                // Return to ATM screen
                Minecraft.getInstance().setScreen(parentScreen);
            })
            .bounds(centerX - buttonWidth - 5, buttonY, buttonWidth, 20)
            .build());

        // Cancel button
        this.addRenderableWidget(Button.builder(Component.literal("§c✗ Cancel"),
            btn -> {
                // Return to ATM without executing
                Minecraft.getInstance().setScreen(parentScreen);
            })
            .bounds(centerX + 5, buttonY, buttonWidth, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        this.renderBackground(graphics);

        // Panel
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, COLOR_BORDER);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COLOR_PANEL);

        // Warning header bar
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 22, COLOR_WARNING_BG);
        graphics.fill(panelLeft, panelTop + 21, panelLeft + PANEL_WIDTH, panelTop + 22, COLOR_WARNING);

        // Warning icon and title
        String title = "§c§l⚠ Confirm Large " + actionType;
        graphics.drawCenteredString(this.font, title, panelLeft + PANEL_WIDTH / 2, panelTop + 7, COLOR_WARNING);

        // Transaction details
        int y = panelTop + 32;
        int leftMargin = panelLeft + 16;

        // Amount
        graphics.drawString(this.font, "§7Amount:", leftMargin, y, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§f§l" + formattedAmount, leftMargin + 55, y, COLOR_TEXT);
        y += 14;

        // Account
        graphics.drawString(this.font, "§7Account:", leftMargin, y, COLOR_TEXT_DIM);
        String accountDisplay = accountName;
        int maxAccountWidth = PANEL_WIDTH - 90;
        if (this.font.width(accountDisplay) > maxAccountWidth) {
            accountDisplay = this.font.plainSubstrByWidth(accountDisplay, maxAccountWidth - this.font.width("...")) + "...";
        }
        graphics.drawString(this.font, "§f" + accountDisplay, leftMargin + 55, y, COLOR_TEXT);
        y += 14;

        // Recipient (transfers only)
        if (recipientName != null && !recipientName.isEmpty()) {
            graphics.drawString(this.font, "§7To:", leftMargin, y, COLOR_TEXT_DIM);
            graphics.drawString(this.font, "§f" + recipientName, leftMargin + 55, y, COLOR_TEXT);
            y += 14;
        }

        // Warning message
        y += 4;
        graphics.drawCenteredString(this.font, "§eAre you sure you want to proceed?",
            panelLeft + PANEL_WIDTH / 2, y, COLOR_TEXT_DIM);

        // Render widgets (buttons)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Escape key = cancel, return to ATM
        Minecraft.getInstance().setScreen(parentScreen);
    }
}

