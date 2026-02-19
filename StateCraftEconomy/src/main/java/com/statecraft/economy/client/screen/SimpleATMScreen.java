package com.statecraft.economy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.Bank;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.ATMTransactionPacket;
import com.statecraft.economy.network.packets.RequestAccountActivityPacket;
import com.statecraft.economy.network.packets.RequestAccountsPacket;
import com.statecraft.economy.network.packets.RequestTransferRecipientsPacket;
import com.statecraft.economy.network.packets.SyncAccountsPacket;
import com.statecraft.economy.network.packets.SyncTransferRecipientsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Simple ATM GUI without inventory - similar to StateCraft screens
 * Features dropdown account selection with permission verification
 */
public class SimpleATMScreen extends Screen {

    // Colors (matching StateCraft style)
    protected static final int COLOR_PRIMARY = 0xFF4A90D9;      // Blue
    protected static final int COLOR_SECONDARY = 0xFF2ECC71;    // Green
    protected static final int COLOR_WARNING = 0xFFE74C3C;      // Red
    protected static final int COLOR_TEXT = 0xFFFFFFFF;         // White
    protected static final int COLOR_PANEL = 0xDD1A1A2E;        // Dark blue panel
    protected static final int COLOR_BORDER = 0xFF3D3D5C;       // Border color
    protected static final int COLOR_DROPDOWN = 0xFF1A1A2E;     // Dropdown background (fully opaque)
    protected static final int COLOR_DROPDOWN_HOVER = 0xFF353560; // Dropdown hover (fully opaque)

    // Layout
    protected int guiLeft;
    protected int guiTop;
    protected int guiWidth = 280;
    protected int guiHeight = 220;

    // Base dimensions for auto-scaling
    protected int baseGuiWidth = 280;
    protected int baseGuiHeight = 220;
    protected static final int MIN_MARGIN = 20;
    protected static final float MAX_SCALE = 1.0f;  // Never scale up
    protected static final float MIN_SCALE = 0.5f;
    protected float scaleFactor = 1.0f;

    // Screen modes
    private enum ScreenMode {
        MAIN_MENU,
        BANK_SELECT,
        DEPOSIT_INPUT,
        WITHDRAW_INPUT,
        TRANSFER_INPUT,
        BALANCE_DISPLAY
    }

    // Current state
    private ScreenMode currentMode = ScreenMode.MAIN_MENU;
    private UUID selectedBankId;
    private double currentBalance = 0.0;
    private String statusMessage = "";
    private int statusMessageTicks = 0;
    private boolean statusIsError = false;

    // Available accounts (populated from server)
    private List<SyncAccountsPacket.AccountInfo> availableAccounts = new ArrayList<>();
    private int selectedAccountIndex = 0;

    // Dropdown state
    private boolean accountDropdownOpen = false;
    private int dropdownX, dropdownY, dropdownWidth, dropdownHeight;

    // Transfer recipient state
    private enum TransferTargetType {
        PLAYER("Player"),
        NATION("Nation"),
        STATE("State"),
        CITY("City");

        private final String displayName;
        TransferTargetType(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    private TransferTargetType selectedTransferType = TransferTargetType.PLAYER;
    private List<SyncTransferRecipientsPacket.RecipientInfo> transferRecipients = new ArrayList<>();
    private List<SyncTransferRecipientsPacket.RecipientInfo> filteredRecipients = new ArrayList<>(); // Filtered by search
    private int selectedRecipientIndex = -1;
    private boolean transferTypeDropdownOpen = false;
    private boolean transferRecipientDropdownOpen = false;
    private int typeDropdownX, typeDropdownY, typeDropdownWidth;
    private int recipientDropdownX, recipientDropdownY, recipientDropdownWidth;
    private String lastSearchText = ""; // Track search text changes

    // UI Components
    private EditBox amountInput;
    private EditBox noteInput;
    private EditBox recipientInput;
    private final List<Button> menuButtons = new ArrayList<>();

    public SimpleATMScreen(UUID bankId) {
        super(Component.literal("ATM"));
        this.selectedBankId = bankId != null ? bankId : EconomyManager.getInstance().getBankRegistry().getDefaultBank().getId();
    }

    public SimpleATMScreen() {
        this(null);
    }

    @Override
    protected void init() {
        super.init();

        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        // Amount input field
        amountInput = new EditBox(this.font, guiLeft + 20, guiTop + 70, 140, 20, Component.literal("Amount"));
        amountInput.setMaxLength(15);
        amountInput.setVisible(false);
        amountInput.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*")); // Numbers and decimal only
        addRenderableWidget(amountInput);

        // Recipient input field (for transfers - now a search box)
        recipientInput = new EditBox(this.font, guiLeft + 20, guiTop + 95, 140, 20, Component.literal("Search"));
        recipientInput.setMaxLength(32);
        recipientInput.setVisible(false);
        recipientInput.setHint(Component.literal("Type to search..."));
        addRenderableWidget(recipientInput);

        // Note input field (optional note for deposits/withdrawals)
        noteInput = new EditBox(this.font, guiLeft + 20, guiTop + 100, 140, 20, Component.literal("Note"));
        noteInput.setMaxLength(64);
        noteInput.setVisible(false);
        noteInput.setHint(Component.literal("Optional note..."));
        addRenderableWidget(noteInput);

        // Request available accounts from server
        requestAccounts();
        requestBalance();

        buildUI();
    }

    private void buildUI() {
        // Clear existing buttons
        menuButtons.forEach(this::removeWidget);
        menuButtons.clear();

        amountInput.setVisible(false);
        recipientInput.setVisible(false);
        noteInput.setVisible(false);
        accountDropdownOpen = false;

        switch (currentMode) {
            case MAIN_MENU -> buildMainMenu();
            case BANK_SELECT -> buildBankSelect();
            case DEPOSIT_INPUT -> buildDepositInput();
            case WITHDRAW_INPUT -> buildWithdrawInput();
            case TRANSFER_INPUT -> buildTransferInput();
            case BALANCE_DISPLAY -> buildBalanceDisplay();
        }
    }

    private void buildMainMenu() {
        int centerX = guiLeft + guiWidth / 2;
        int y = guiTop + 40;
        int buttonWidth = 200;

        // Bank selection button
        addMenuButton(Button.builder(Component.literal("Bank: " + getBankName()),
            btn -> switchMode(ScreenMode.BANK_SELECT))
            .bounds(centerX - buttonWidth/2, y, buttonWidth, 20)
            .build());

        y += 25;

        // Account dropdown button (shows currently selected account)
        dropdownX = centerX - buttonWidth/2;
        dropdownY = y;
        dropdownWidth = buttonWidth;
        dropdownHeight = 20;

        addMenuButton(Button.builder(Component.literal("▼ " + getSelectedAccountName()),
            btn -> {
                accountDropdownOpen = !accountDropdownOpen;
            })
            .bounds(dropdownX, dropdownY, dropdownWidth, dropdownHeight)
            .build());

        y += 35;

        // Operation buttons (2x2 grid)
        int halfWidth = 95;
        int leftX = centerX - halfWidth - 3;
        int rightX = centerX + 3;

        addMenuButton(Button.builder(Component.literal("Deposit"),
            btn -> switchMode(ScreenMode.DEPOSIT_INPUT))
            .bounds(leftX, y, halfWidth, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("Withdraw"),
            btn -> switchMode(ScreenMode.WITHDRAW_INPUT))
            .bounds(rightX, y, halfWidth, 20)
            .build());

        y += 25;

        addMenuButton(Button.builder(Component.literal("Transfer"),
            btn -> switchMode(ScreenMode.TRANSFER_INPUT))
            .bounds(leftX, y, halfWidth, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("Check Balance"),
            btn -> {
                requestBalance();
                switchMode(ScreenMode.BALANCE_DISPLAY);
            })
            .bounds(rightX, y, halfWidth, 20)
            .build());

        y += 28;

        // Account Activity button (full width)
        addMenuButton(Button.builder(Component.literal("§eAccount Activity"),
            btn -> requestAccountActivity())
            .bounds(centerX - buttonWidth/2, y, buttonWidth, 20)
            .build());
    }

    private void buildBankSelect() {
        int centerX = guiLeft + guiWidth / 2;
        int y = guiTop + 40;
        int buttonWidth = 200;

        for (Bank bank : EconomyManager.getInstance().getBankRegistry().getAllBanks()) {
            boolean isSelected = bank.getId().equals(selectedBankId);
            String prefix = isSelected ? "§a✓ " : "  ";

            addMenuButton(Button.builder(Component.literal(prefix + bank.getDisplayName()),
                btn -> {
                    selectedBankId = bank.getId();
                    requestBalance();
                    switchMode(ScreenMode.MAIN_MENU);
                })
                .bounds(centerX - buttonWidth/2, y, buttonWidth, 20)
                .build());
            y += 25;
        }

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(centerX - buttonWidth/2, guiTop + guiHeight - 35, buttonWidth, 20)
            .build());
    }

    private void buildDepositInput() {
        int centerX = guiLeft + guiWidth / 2;
        int buttonWidth = 160;

        amountInput.setVisible(true);
        amountInput.setX(centerX - buttonWidth/2);
        amountInput.setY(guiTop + 70);
        amountInput.setWidth(buttonWidth);
        amountInput.setFocused(true);

        // Note input (optional)
        noteInput.setVisible(true);
        noteInput.setX(centerX - buttonWidth/2);
        noteInput.setY(guiTop + 110);
        noteInput.setWidth(buttonWidth);

        // Confirm button
        addMenuButton(Button.builder(Component.literal("§aConfirm Deposit"),
            btn -> performDeposit())
            .bounds(centerX - buttonWidth/2, guiTop + 140, buttonWidth, 20)
            .build());

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(centerX - buttonWidth/2, guiTop + guiHeight - 35, buttonWidth, 20)
            .build());
    }

    private void buildWithdrawInput() {
        int centerX = guiLeft + guiWidth / 2;
        int buttonWidth = 160;

        amountInput.setVisible(true);
        amountInput.setX(centerX - buttonWidth/2);
        amountInput.setY(guiTop + 70);
        amountInput.setWidth(buttonWidth);
        amountInput.setFocused(true);

        // Note input (optional)
        noteInput.setVisible(true);
        noteInput.setX(centerX - buttonWidth/2);
        noteInput.setY(guiTop + 110);
        noteInput.setWidth(buttonWidth);

        // Confirm button
        addMenuButton(Button.builder(Component.literal("§aConfirm Withdrawal"),
            btn -> performWithdraw())
            .bounds(centerX - buttonWidth/2, guiTop + 140, buttonWidth, 20)
            .build());

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(centerX - buttonWidth/2, guiTop + guiHeight - 35, buttonWidth, 20)
            .build());
    }
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(centerX - buttonWidth/2, guiTop + guiHeight - 35, buttonWidth, 20)
            .build());
    }

    private void buildTransferInput() {
        int centerX = guiLeft + guiWidth / 2;
        int buttonWidth = 160;

        // Reset recipient when entering transfer screen
        if (selectedRecipientIndex >= filteredRecipients.size()) {
            selectedRecipientIndex = -1;
        }

        // Transfer type dropdown button
        typeDropdownX = centerX - buttonWidth/2;
        typeDropdownY = guiTop + 55;
        typeDropdownWidth = buttonWidth;

        addMenuButton(Button.builder(Component.literal("▼ Type: " + selectedTransferType.getDisplayName()),
            btn -> {
                transferTypeDropdownOpen = !transferTypeDropdownOpen;
                transferRecipientDropdownOpen = false;
            })
            .bounds(typeDropdownX, typeDropdownY, typeDropdownWidth, 20)
            .build());

        // Recipient search box (replaces dropdown)
        recipientInput.setVisible(true);
        recipientInput.setX(centerX - buttonWidth/2);
        recipientInput.setY(guiTop + 85);
        recipientInput.setWidth(buttonWidth);

        // Update dropdown position for search results
        recipientDropdownX = centerX - buttonWidth/2;
        recipientDropdownY = guiTop + 85;
        recipientDropdownWidth = buttonWidth;

        // Amount input
        amountInput.setVisible(true);
        amountInput.setX(centerX - buttonWidth/2);
        amountInput.setY(guiTop + 130);
        amountInput.setWidth(buttonWidth);

        // Confirm button
        addMenuButton(Button.builder(Component.literal("§aConfirm Transfer"),
            btn -> performTransfer())
            .bounds(centerX - buttonWidth/2, guiTop + 158, buttonWidth, 20)
            .build());

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(centerX - buttonWidth/2, guiTop + guiHeight - 25, buttonWidth, 20)
            .build());

        // Request recipients for the selected type if we don't have them
        if (transferRecipients.isEmpty()) {
            requestTransferRecipients(selectedTransferType);
        }

        // Initialize filtered list
        updateFilteredRecipients();
    }

    private void buildBalanceDisplay() {
        int centerX = guiLeft + guiWidth / 2;
        int buttonWidth = 160;

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(centerX - buttonWidth/2, guiTop + guiHeight - 35, buttonWidth, 20)
            .build());
    }

    private void addMenuButton(Button button) {
        menuButtons.add(button);
        addRenderableWidget(button);
    }

    private void switchMode(ScreenMode mode) {
        currentMode = mode;
        amountInput.setValue("");
        noteInput.setValue("");
        recipientInput.setValue("");
        accountDropdownOpen = false;
        transferTypeDropdownOpen = false;
        transferRecipientDropdownOpen = false;
        if (mode != ScreenMode.TRANSFER_INPUT) {
            selectedRecipientIndex = -1;
        }
        buildUI();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render dark background
        this.renderBackground(graphics);

        // Render panel
        renderPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        // Render title
        String title = "§l" + getBankName() + " ATM";
        graphics.drawCenteredString(this.font, title, this.width / 2, guiTop + 8, COLOR_PRIMARY);

        // Render divider
        graphics.fill(guiLeft + 10, guiTop + 22, guiLeft + guiWidth - 10, guiTop + 23, COLOR_BORDER);

        // Only show balance in main menu and balance display modes (not transfer to avoid overlap)
        if (currentMode != ScreenMode.TRANSFER_INPUT) {
            String balanceText = String.format("Balance: §a$%,.2f", currentBalance);
            graphics.drawCenteredString(this.font, balanceText, this.width / 2, guiTop + 28, COLOR_TEXT);
        }

        // Render mode-specific content
        renderModeContent(graphics);

        // Render status message
        if (statusMessageTicks > 0) {
            int color = statusIsError ? COLOR_WARNING : COLOR_SECONDARY;
            graphics.drawCenteredString(this.font, statusMessage, this.width / 2, guiTop + guiHeight - 50, color);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render dropdown on top of everything if open
        if (accountDropdownOpen && currentMode == ScreenMode.MAIN_MENU) {
            renderAccountDropdown(graphics, mouseX, mouseY);
        }

        // Render transfer dropdowns
        if (currentMode == ScreenMode.TRANSFER_INPUT) {
            if (transferTypeDropdownOpen) {
                renderTransferTypeDropdown(graphics, mouseX, mouseY);
            }
            // Always try to render recipient dropdown when search box is visible
            if (recipientInput.isVisible()) {
                renderTransferRecipientDropdown(graphics, mouseX, mouseY);
            }
        }
    }

    private void renderAccountDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        int itemHeight = 20;
        int totalHeight = availableAccounts.size() * itemHeight;
        int dropdownStartY = dropdownY + dropdownHeight;

        // Push pose and translate to higher z-level to render on top of everything
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // Disable depth test to ensure dropdown renders on top
        RenderSystem.disableDepthTest();

        // Dropdown background - use fully opaque colors with black border
        graphics.fill(dropdownX - 1, dropdownStartY - 1, dropdownX + dropdownWidth + 1, dropdownStartY + totalHeight + 1, 0xFF000000);
        graphics.fill(dropdownX, dropdownStartY, dropdownX + dropdownWidth, dropdownStartY + totalHeight, 0xFF1A1A2E);

        // Border
        graphics.fill(dropdownX, dropdownStartY, dropdownX + 1, dropdownStartY + totalHeight, COLOR_BORDER);
        graphics.fill(dropdownX + dropdownWidth - 1, dropdownStartY, dropdownX + dropdownWidth, dropdownStartY + totalHeight, COLOR_BORDER);
        graphics.fill(dropdownX, dropdownStartY + totalHeight - 1, dropdownX + dropdownWidth, dropdownStartY + totalHeight, COLOR_BORDER);

        // Render each account option
        for (int i = 0; i < availableAccounts.size(); i++) {
            SyncAccountsPacket.AccountInfo account = availableAccounts.get(i);
            int optionY = dropdownStartY + (i * itemHeight);

            // Hover highlight
            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                              mouseY >= optionY && mouseY < optionY + itemHeight;
            if (hovered) {
                graphics.fill(dropdownX + 1, optionY, dropdownX + dropdownWidth - 1, optionY + itemHeight, COLOR_DROPDOWN_HOVER);
            }

            // Selected indicator
            boolean isSelected = i == selectedAccountIndex;
            String prefix = isSelected ? "§a✓ " : "  ";

            // Account type icon
            String typeIcon = switch (account.type()) {
                case "PERSONAL" -> "§b♦";
                case "NATION" -> "§6♛";
                case "STATE" -> "§e★";
                case "CITY" -> "§a●";
                default -> "§7○";
            };

            String text = prefix + typeIcon + " " + account.getDisplayName();
            graphics.drawString(this.font, text, dropdownX + 5, optionY + 6, COLOR_TEXT);

            // Show balance on right side
            String balanceStr = String.format("$%,.0f", account.balance());
            int balanceWidth = this.font.width(balanceStr);
            graphics.drawString(this.font, "§7" + balanceStr, dropdownX + dropdownWidth - balanceWidth - 8, optionY + 6, COLOR_TEXT);
        }

        // Re-enable depth test
        RenderSystem.enableDepthTest();

        // Pop the pose to restore normal rendering
        graphics.pose().popPose();
    }

    private void renderTransferTypeDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        int itemHeight = 20;
        TransferTargetType[] types = TransferTargetType.values();
        int totalHeight = types.length * itemHeight;
        int dropdownStartY = typeDropdownY + 20;

        // Push pose and translate to higher z-level to render on top of everything
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // Disable depth test to ensure dropdown renders on top
        RenderSystem.disableDepthTest();

        // Dropdown background - use fully opaque colors with black border
        graphics.fill(typeDropdownX - 1, dropdownStartY - 1, typeDropdownX + typeDropdownWidth + 1, dropdownStartY + totalHeight + 1, 0xFF000000);
        graphics.fill(typeDropdownX, dropdownStartY, typeDropdownX + typeDropdownWidth, dropdownStartY + totalHeight, 0xFF1A1A2E);

        // Border
        graphics.fill(typeDropdownX, dropdownStartY, typeDropdownX + 1, dropdownStartY + totalHeight, COLOR_BORDER);
        graphics.fill(typeDropdownX + typeDropdownWidth - 1, dropdownStartY, typeDropdownX + typeDropdownWidth, dropdownStartY + totalHeight, COLOR_BORDER);
        graphics.fill(typeDropdownX, dropdownStartY + totalHeight - 1, typeDropdownX + typeDropdownWidth, dropdownStartY + totalHeight, COLOR_BORDER);

        // Render each type option
        for (int i = 0; i < types.length; i++) {
            TransferTargetType type = types[i];
            int optionY = dropdownStartY + (i * itemHeight);

            // Hover highlight
            boolean hovered = mouseX >= typeDropdownX && mouseX < typeDropdownX + typeDropdownWidth &&
                              mouseY >= optionY && mouseY < optionY + itemHeight;
            if (hovered) {
                graphics.fill(typeDropdownX + 1, optionY, typeDropdownX + typeDropdownWidth - 1, optionY + itemHeight, COLOR_DROPDOWN_HOVER);
            }

            // Selected indicator
            boolean isSelected = type == selectedTransferType;
            String prefix = isSelected ? "§a✓ " : "  ";

            // Type icon
            String typeIcon = switch (type) {
                case PLAYER -> "§b♦";
                case NATION -> "§6♛";
                case STATE -> "§e★";
                case CITY -> "§a●";
            };

            String text = prefix + typeIcon + " " + type.getDisplayName();
            graphics.drawString(this.font, text, typeDropdownX + 5, optionY + 6, COLOR_TEXT);
        }

        // Re-enable depth test
        RenderSystem.enableDepthTest();

        // Pop the pose to restore normal rendering
        graphics.pose().popPose();
    }

    private void renderTransferRecipientDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        // Only show dropdown if search box has focus and there are results
        if (!recipientInput.isFocused() || filteredRecipients.isEmpty()) {
            return;
        }

        int itemHeight = 20;
        int maxVisible = Math.min(filteredRecipients.size(), 6); // Limit visible items
        int totalHeight = maxVisible * itemHeight;
        int dropdownStartY = recipientDropdownY + 20; // Below search box

        // Push pose and translate to higher z-level to render on top of everything
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        // Disable depth test to ensure dropdown renders on top
        RenderSystem.disableDepthTest();

        // Dropdown background - use fully opaque colors with black border
        graphics.fill(recipientDropdownX - 1, dropdownStartY - 1, recipientDropdownX + recipientDropdownWidth + 1, dropdownStartY + totalHeight + 1, 0xFF000000);
        graphics.fill(recipientDropdownX, dropdownStartY, recipientDropdownX + recipientDropdownWidth, dropdownStartY + totalHeight, 0xFF1A1A2E);

        // Border
        graphics.fill(recipientDropdownX, dropdownStartY, recipientDropdownX + 1, dropdownStartY + totalHeight, COLOR_BORDER);
        graphics.fill(recipientDropdownX + recipientDropdownWidth - 1, dropdownStartY, recipientDropdownX + recipientDropdownWidth, dropdownStartY + totalHeight, COLOR_BORDER);
        graphics.fill(recipientDropdownX, dropdownStartY + totalHeight - 1, recipientDropdownX + recipientDropdownWidth, dropdownStartY + totalHeight, COLOR_BORDER);

        // Render each recipient option
        for (int i = 0; i < maxVisible; i++) {
            SyncTransferRecipientsPacket.RecipientInfo recipient = filteredRecipients.get(i);
            int optionY = dropdownStartY + (i * itemHeight);

            // Hover highlight
            boolean hovered = mouseX >= recipientDropdownX && mouseX < recipientDropdownX + recipientDropdownWidth &&
                              mouseY >= optionY && mouseY < optionY + itemHeight;
            if (hovered) {
                graphics.fill(recipientDropdownX + 1, optionY, recipientDropdownX + recipientDropdownWidth - 1, optionY + itemHeight, COLOR_DROPDOWN_HOVER);
            }

            // Check if this is the selected recipient
            boolean isSelected = selectedRecipientIndex >= 0 &&
                                 selectedRecipientIndex < transferRecipients.size() &&
                                 transferRecipients.get(selectedRecipientIndex).id().equals(recipient.id());
            String prefix = isSelected ? "§a✓ " : "  ";

            String text = prefix + recipient.name();
            graphics.drawString(this.font, text, recipientDropdownX + 5, optionY + 6, COLOR_TEXT);
        }

        // Show "more" indicator if there are more items
        if (filteredRecipients.size() > maxVisible) {
            int moreCount = filteredRecipients.size() - maxVisible;
            String moreText = "§7+" + moreCount + " more...";
            graphics.drawString(this.font, moreText, recipientDropdownX + recipientDropdownWidth - font.width(moreText) - 5, dropdownStartY + totalHeight - itemHeight + 6, COLOR_TEXT);
        }

        // Re-enable depth test
        RenderSystem.enableDepthTest();

        // Pop the pose to restore normal rendering
        graphics.pose().popPose();
    }

    private void renderModeContent(GuiGraphics graphics) {
        switch (currentMode) {
            case DEPOSIT_INPUT -> {
                graphics.drawCenteredString(this.font, "§fEnter deposit amount:", this.width / 2, guiTop + 50, COLOR_TEXT);
                graphics.drawCenteredString(this.font, "§7Note (optional):", this.width / 2, guiTop + 98, 0xFF888888);
                graphics.drawCenteredString(this.font, "§7Currency items will be removed from inventory", this.width / 2, guiTop + 168, 0xFF888888);
            }
            case WITHDRAW_INPUT -> {
                graphics.drawCenteredString(this.font, "§fEnter withdrawal amount:", this.width / 2, guiTop + 50, COLOR_TEXT);
                graphics.drawCenteredString(this.font, "§7Note (optional):", this.width / 2, guiTop + 98, 0xFF888888);
                graphics.drawCenteredString(this.font, "§7Currency items will be added to inventory", this.width / 2, guiTop + 168, 0xFF888888);
            }
            case TRANSFER_INPUT -> {
                // Show balance at top for transfer mode
                String balanceText = String.format("Balance: §a$%,.2f", currentBalance);
                graphics.drawCenteredString(this.font, balanceText, this.width / 2, guiTop + 28, COLOR_TEXT);

                graphics.drawCenteredString(this.font, "§fTransfer Type:", this.width / 2, guiTop + 43, COLOR_TEXT);

                // Search recipient label
                String selectedName = getSelectedRecipientName();
                if (selectedName != null) {
                    graphics.drawCenteredString(this.font, "§7Selected: §a" + selectedName, this.width / 2, guiTop + 115, COLOR_TEXT);
                } else {
                    graphics.drawCenteredString(this.font, "§7Search recipient:", this.width / 2, guiTop + 75, 0xFF888888);
                }
            }
            case BALANCE_DISPLAY -> {
                SyncAccountsPacket.AccountInfo account = getSelectedAccount();
                int y = guiTop + 55;

                graphics.drawCenteredString(this.font, "§6§lAccount Details", this.width / 2, y, COLOR_PRIMARY);
                y += 25;

                graphics.drawCenteredString(this.font, "§7Bank: §f" + getBankName(), this.width / 2, y, COLOR_TEXT);
                y += 18;

                if (account != null) {
                    graphics.drawCenteredString(this.font, "§7Account: §f" + account.getDisplayName(), this.width / 2, y, COLOR_TEXT);
                    y += 18;
                    graphics.drawCenteredString(this.font, String.format("§7Balance: §a$%,.2f", account.balance()), this.width / 2, y, COLOR_TEXT);
                } else {
                    graphics.drawCenteredString(this.font, "§7Account: §fPersonal Account", this.width / 2, y, COLOR_TEXT);
                    y += 18;
                    graphics.drawCenteredString(this.font, String.format("§7Balance: §a$%,.2f", currentBalance), this.width / 2, y, COLOR_TEXT);
                }
            }
        }
    }

    protected void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Main panel
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);

        // Border
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle account dropdown clicks
        if (accountDropdownOpen && currentMode == ScreenMode.MAIN_MENU) {
            int itemHeight = 20;
            int dropdownStartY = dropdownY + dropdownHeight;
            int totalHeight = availableAccounts.size() * itemHeight;

            // Check if clicked inside dropdown
            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                mouseY >= dropdownStartY && mouseY < dropdownStartY + totalHeight) {

                int clickedIndex = (int) ((mouseY - dropdownStartY) / itemHeight);
                if (clickedIndex >= 0 && clickedIndex < availableAccounts.size()) {
                    selectedAccountIndex = clickedIndex;
                    SyncAccountsPacket.AccountInfo account = availableAccounts.get(selectedAccountIndex);
                    currentBalance = account.balance();
                    accountDropdownOpen = false;
                    buildUI(); // Rebuild to update button text
                    return true;
                }
            }
            // Clicked outside dropdown, close it
            accountDropdownOpen = false;
            return true;
        }

        // Handle transfer type dropdown clicks
        if (transferTypeDropdownOpen && currentMode == ScreenMode.TRANSFER_INPUT) {
            int itemHeight = 20;
            TransferTargetType[] types = TransferTargetType.values();
            int totalHeight = types.length * itemHeight;
            int dropdownStartY = typeDropdownY + 20;

            if (mouseX >= typeDropdownX && mouseX < typeDropdownX + typeDropdownWidth &&
                mouseY >= dropdownStartY && mouseY < dropdownStartY + totalHeight) {

                int clickedIndex = (int) ((mouseY - dropdownStartY) / itemHeight);
                if (clickedIndex >= 0 && clickedIndex < types.length) {
                    selectedTransferType = types[clickedIndex];
                    selectedRecipientIndex = -1; // Reset recipient selection
                    transferRecipients.clear(); // Clear old recipients
                    filteredRecipients.clear(); // Clear filtered list
                    recipientInput.setValue(""); // Clear search box
                    lastSearchText = "";
                    transferTypeDropdownOpen = false;
                    requestTransferRecipients(selectedTransferType);
                    buildUI();
                    return true;
                }
            }
            transferTypeDropdownOpen = false;
            return true;
        }

        // Handle transfer recipient dropdown clicks (search results)
        if (currentMode == ScreenMode.TRANSFER_INPUT && recipientInput.isFocused() && !filteredRecipients.isEmpty()) {
            int itemHeight = 20;
            int maxVisible = Math.min(filteredRecipients.size(), 6);
            int totalHeight = maxVisible * itemHeight;
            int dropdownStartY = recipientDropdownY + 20;

            if (mouseX >= recipientDropdownX && mouseX < recipientDropdownX + recipientDropdownWidth &&
                mouseY >= dropdownStartY && mouseY < dropdownStartY + totalHeight) {

                int clickedIndex = (int) ((mouseY - dropdownStartY) / itemHeight);
                if (clickedIndex >= 0 && clickedIndex < maxVisible && clickedIndex < filteredRecipients.size()) {
                    // Find this recipient in the full list to set the correct index
                    SyncTransferRecipientsPacket.RecipientInfo selectedRecipient = filteredRecipients.get(clickedIndex);
                    for (int i = 0; i < transferRecipients.size(); i++) {
                        if (transferRecipients.get(i).id().equals(selectedRecipient.id())) {
                            selectedRecipientIndex = i;
                            break;
                        }
                    }
                    // Update search box with selected name
                    recipientInput.setValue(selectedRecipient.name());
                    recipientInput.setFocused(false);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusMessageTicks > 0) {
            statusMessageTicks--;
        }

        // Update filtered recipients in real-time as user types
        if (currentMode == ScreenMode.TRANSFER_INPUT && recipientInput.isVisible()) {
            String currentSearch = recipientInput.getValue();
            if (!currentSearch.equals(lastSearchText)) {
                lastSearchText = currentSearch;
                updateFilteredRecipients();
            }
        }
    }

    private void updateFilteredRecipients() {
        String searchText = recipientInput.getValue().toLowerCase().trim();
        filteredRecipients.clear();

        if (searchText.isEmpty()) {
            // Show all recipients when search is empty
            filteredRecipients.addAll(transferRecipients);
        } else {
            // Filter by partial match
            for (SyncTransferRecipientsPacket.RecipientInfo recipient : transferRecipients) {
                if (recipient.name().toLowerCase().contains(searchText)) {
                    filteredRecipients.add(recipient);
                }
            }
        }
    }

    private String getSelectedRecipientName() {
        if (selectedRecipientIndex >= 0 && selectedRecipientIndex < transferRecipients.size()) {
            return transferRecipients.get(selectedRecipientIndex).name();
        }
        return null;
    }

    private String getBankName() {
        if (selectedBankId == null) return "Central Bank";
        Bank bank = EconomyManager.getInstance().getBankRegistry().getBank(selectedBankId);
        return bank != null ? bank.getDisplayName() : "Unknown Bank";
    }

    private String getSelectedAccountName() {
        if (availableAccounts.isEmpty()) {
            return "Personal Account";
        }
        if (selectedAccountIndex >= 0 && selectedAccountIndex < availableAccounts.size()) {
            return availableAccounts.get(selectedAccountIndex).getDisplayName();
        }
        return "Personal Account";
    }

    private SyncAccountsPacket.AccountInfo getSelectedAccount() {
        if (availableAccounts.isEmpty()) return null;
        if (selectedAccountIndex >= 0 && selectedAccountIndex < availableAccounts.size()) {
            return availableAccounts.get(selectedAccountIndex);
        }
        return null;
    }

    private void requestAccounts() {
        // Request available accounts from server (will verify permissions)
        if (Minecraft.getInstance().player != null) {
            NetworkHandler.sendToServer(new RequestAccountsPacket());
        }
    }

    private void requestBalance() {
        // Request balance from server
        if (Minecraft.getInstance().player != null) {
            NetworkHandler.sendToServer(new ATMTransactionPacket(
                ATMTransactionPacket.Action.CHECK_BALANCE,
                0,
                ""
            ));
        }
    }

    private void requestAccountActivity() {
        // Request account activity for the currently selected account
        SyncAccountsPacket.AccountInfo selectedAccount = getSelectedAccount();
        String accType;
        String accId;
        if (selectedAccount != null) {
            accType = selectedAccount.type();
            accId = selectedAccount.id();
        } else {
            // Default to personal account
            accType = "PERSONAL";
            accId = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID().toString() : "";
        }
        if (!accId.isEmpty()) {
            NetworkHandler.sendToServer(new RequestAccountActivityPacket(accType, accId));
        }
    }

    private void performDeposit() {
        try {
            double amount = Double.parseDouble(amountInput.getValue());
            if (amount <= 0) {
                showStatus("§cAmount must be positive", true);
                return;
            }

            // Get selected account info to tell server which account to deposit to
            SyncAccountsPacket.AccountInfo selectedAccount = getSelectedAccount();
            String accountTarget = "";
            if (selectedAccount != null && !"PERSONAL".equals(selectedAccount.type())) {
                // Format as TYPE:ID for government accounts
                accountTarget = selectedAccount.type() + ":" + selectedAccount.id();
            }

            NetworkHandler.sendToServer(new ATMTransactionPacket(
                ATMTransactionPacket.Action.DEPOSIT,
                amount,
                accountTarget,
                "",
                noteInput.getValue().trim()
            ));
            showStatus("§aProcessing deposit...", false);
        } catch (NumberFormatException e) {
            showStatus("§cInvalid amount", true);
        }
    }

    private void performWithdraw() {
        try {
            double amount = Double.parseDouble(amountInput.getValue());
            if (amount <= 0) {
                showStatus("§cAmount must be positive", true);
                return;
            }
            if (amount > currentBalance) {
                showStatus("§cInsufficient funds", true);
                return;
            }

            // Get selected account info to tell server which account to withdraw from
            SyncAccountsPacket.AccountInfo selectedAccount = getSelectedAccount();
            String accountTarget = "";
            if (selectedAccount != null && !"PERSONAL".equals(selectedAccount.type())) {
                // Format as TYPE:ID for government accounts
                accountTarget = selectedAccount.type() + ":" + selectedAccount.id();
            }

            NetworkHandler.sendToServer(new ATMTransactionPacket(
                ATMTransactionPacket.Action.WITHDRAW,
                amount,
                accountTarget,
                "",
                noteInput.getValue().trim()
            ));
            showStatus("§aProcessing withdrawal...", false);
        } catch (NumberFormatException e) {
            showStatus("§cInvalid amount", true);
        }
    }

    private void performTransfer() {
        // Validate recipient selection
        if (selectedRecipientIndex < 0 || selectedRecipientIndex >= filteredRecipients.size()) {
            showStatus("§cPlease select a recipient", true);
            return;
        }

        SyncTransferRecipientsPacket.RecipientInfo recipient = filteredRecipients.get(selectedRecipientIndex);

        try {
            double amount = Double.parseDouble(amountInput.getValue());
            if (amount <= 0) {
                showStatus("§cAmount must be positive", true);
                return;
            }
            if (amount > currentBalance) {
                showStatus("§cInsufficient funds", true);
                return;
            }

            // Format recipient target as "type:id" for the server to parse
            String recipientTarget = selectedTransferType.name() + ":" + recipient.id();

            // Build source account string based on selected account
            String sourceAccount = getSelectedSourceAccountString();

            NetworkHandler.sendToServer(new ATMTransactionPacket(
                ATMTransactionPacket.Action.TRANSFER,
                amount,
                recipientTarget,
                sourceAccount
            ));
            showStatus("§aProcessing transfer to " + recipient.name() + "...", false);
        } catch (NumberFormatException e) {
            showStatus("§cInvalid amount", true);
        }
    }

    /**
     * Gets the source account string for the currently selected account.
     * Returns empty string for personal account, or "TYPE:uuid" for government accounts.
     */
    private String getSelectedSourceAccountString() {
        if (availableAccounts.isEmpty() || selectedAccountIndex >= availableAccounts.size()) {
            return ""; // Default to personal account
        }

        SyncAccountsPacket.AccountInfo selectedAccount = availableAccounts.get(selectedAccountIndex);
        if ("PERSONAL".equals(selectedAccount.type())) {
            return ""; // Personal account - no source account string needed
        }

        // Government account - return "TYPE:uuid" format
        return selectedAccount.type() + ":" + selectedAccount.id();
    }

    private void showStatus(String message, boolean isError) {
        this.statusMessage = message;
        this.statusMessageTicks = 60; // 3 seconds
        this.statusIsError = isError;
    }

    public void updateBalance(double balance) {
        this.currentBalance = balance;
    }

    public void updateAvailableAccounts(List<SyncAccountsPacket.AccountInfo> accounts) {
        this.availableAccounts = new ArrayList<>(accounts);
        if (!availableAccounts.isEmpty() && selectedAccountIndex >= availableAccounts.size()) {
            selectedAccountIndex = 0;
        }
        // Update current balance to selected account's balance
        if (!availableAccounts.isEmpty() && selectedAccountIndex < availableAccounts.size()) {
            currentBalance = availableAccounts.get(selectedAccountIndex).balance();
        }
        // Rebuild UI if in main menu to update button text
        if (currentMode == ScreenMode.MAIN_MENU) {
            buildUI();
        }
    }

    private void requestTransferRecipients(TransferTargetType type) {
        RequestTransferRecipientsPacket.RecipientType packetType = switch (type) {
            case PLAYER -> RequestTransferRecipientsPacket.RecipientType.PLAYER;
            case NATION -> RequestTransferRecipientsPacket.RecipientType.NATION;
            case STATE -> RequestTransferRecipientsPacket.RecipientType.STATE;
            case CITY -> RequestTransferRecipientsPacket.RecipientType.CITY;
        };
        NetworkHandler.sendToServer(new RequestTransferRecipientsPacket(packetType));
    }

    public void updateTransferRecipients(String recipientType, List<SyncTransferRecipientsPacket.RecipientInfo> recipients) {
        // Only update if this matches our current selected type
        if (recipientType.equals(selectedTransferType.name())) {
            // Preserve existing selection if possible
            String previouslySelectedId = null;
            if (selectedRecipientIndex >= 0 && selectedRecipientIndex < transferRecipients.size()) {
                previouslySelectedId = transferRecipients.get(selectedRecipientIndex).id();
            }

            this.transferRecipients = new ArrayList<>(recipients);

            // Try to restore previous selection
            this.selectedRecipientIndex = -1;
            if (previouslySelectedId != null) {
                for (int i = 0; i < transferRecipients.size(); i++) {
                    if (transferRecipients.get(i).id().equals(previouslySelectedId)) {
                        selectedRecipientIndex = i;
                        break;
                    }
                }
            }

            // Update filtered list
            updateFilteredRecipients();
            // Rebuild UI if in transfer mode
            if (currentMode == ScreenMode.TRANSFER_INPUT) {
                buildUI();
            }
        }
    }

    public void showTransactionResult(boolean success, String message) {
        showStatus(message, !success);
        if (success) {
            requestBalance(); // Refresh balance
            requestAccounts(); // Refresh accounts
            amountInput.setValue("");
            noteInput.setValue("");
            recipientInput.setValue("");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
