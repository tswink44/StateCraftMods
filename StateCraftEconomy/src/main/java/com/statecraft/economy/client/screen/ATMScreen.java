package com.statecraft.economy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.Bank;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.gui.ATMMenu;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.ATMTransactionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Robust ATM GUI with menu system
 * Features: Main menu, bank selection, account selection, operations
 */
public class ATMScreen extends AbstractContainerScreen<ATMMenu> {
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(StateCraftEconomy.MOD_ID, "textures/gui/atm.png");

    // Screen modes
    private enum ScreenMode {
        MAIN_MENU,
        BANK_SELECT,
        ACCOUNT_SELECT,
        DEPOSIT_CONFIRM,
        WITHDRAW_INPUT,
        TRANSFER_INPUT
    }

    // Account types
    private enum AccountType {
        PERSONAL("Personal Account"),
        NATION("Nation Treasury"),
        STATE("State Treasury"),
        CITY("City Treasury");

        private final String displayName;
        AccountType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // Current state
    private ScreenMode currentMode = ScreenMode.MAIN_MENU;
    private AccountType selectedAccountType = AccountType.PERSONAL;
    private UUID selectedBankId;
    private UUID selectedEntityId; // UUID of selected nation/state/city
    private double currentBalance = 0.0;
    private double selectedAccountBalance = 0.0;
    private String statusMessage = "";
    private int statusMessageTicks = 0;

    // UI Components
    private EditBox amountInput;
    private EditBox recipientInput;
    private final List<Button> menuButtons = new ArrayList<>();

    public ATMScreen(ATMMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.selectedBankId = menu.getBankId();
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Input fields
        amountInput = new EditBox(this.font, x + 10, y + 40, 156, 20, Component.literal("Amount"));
        amountInput.setMaxLength(15);
        amountInput.setVisible(false);
        addRenderableWidget(amountInput);

        recipientInput = new EditBox(this.font, x + 10, y + 65, 156, 20, Component.literal("Recipient"));
        recipientInput.setMaxLength(16);
        recipientInput.setVisible(false);
        addRenderableWidget(recipientInput);

        buildUI();
    }

    private void buildUI() {
        // Clear existing buttons
        menuButtons.forEach(this::removeWidget);
        menuButtons.clear();

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        switch (currentMode) {
            case MAIN_MENU -> buildMainMenu(x, y);
            case BANK_SELECT -> buildBankSelect(x, y);
            case ACCOUNT_SELECT -> buildAccountSelect(x, y);
            case DEPOSIT_CONFIRM -> buildDepositConfirm(x, y);
            case WITHDRAW_INPUT -> buildWithdrawInput(x, y);
            case TRANSFER_INPUT -> buildTransferInput(x, y);
        }
    }

    private void buildMainMenu(int x, int y) {
        amountInput.setVisible(false);
        recipientInput.setVisible(false);

        // Bank selection button (top)
        addMenuButton(Button.builder(Component.literal("Bank: " + getBankName()),
            btn -> switchMode(ScreenMode.BANK_SELECT))
            .bounds(x + 10, y + 15, 156, 20)
            .build());

        // Account selection button
        addMenuButton(Button.builder(Component.literal("Account: " + selectedAccountType.getDisplayName()),
            btn -> switchMode(ScreenMode.ACCOUNT_SELECT))
            .bounds(x + 10, y + 40, 156, 20)
            .build());

        // Operation buttons
        addMenuButton(Button.builder(Component.literal("Deposit"),
            btn -> switchMode(ScreenMode.DEPOSIT_CONFIRM))
            .bounds(x + 10, y + 65, 76, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("Withdraw"),
            btn -> switchMode(ScreenMode.WITHDRAW_INPUT))
            .bounds(x + 90, y + 65, 76, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("Transfer"),
            btn -> switchMode(ScreenMode.TRANSFER_INPUT))
            .bounds(x + 10, y + 90, 76, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("Check Balance"),
            btn -> checkBalance())
            .bounds(x + 90, y + 90, 76, 20)
            .build());
    }

    private void buildBankSelect(int x, int y) {
        amountInput.setVisible(false);
        recipientInput.setVisible(false);

        int buttonY = y + 15;
        for (Bank bank : EconomyManager.getInstance().getBankRegistry().getAllBanks()) {
            boolean isSelected = bank.getId().equals(selectedBankId);
            String prefix = isSelected ? ">> " : "";

            addMenuButton(Button.builder(Component.literal(prefix + bank.getDisplayName()),
                btn -> {
                    selectedBankId = bank.getId();
                    switchMode(ScreenMode.MAIN_MENU);
                })
                .bounds(x + 10, buttonY, 156, 20)
                .build());
            buttonY += 25;
        }

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(x + 10, y + 90, 156, 20)
            .build());
    }

    private void buildAccountSelect(int x, int y) {
        amountInput.setVisible(false);
        recipientInput.setVisible(false);

        int buttonY = y + 15;

        // Personal Account (always available)
        addAccountButton(AccountType.PERSONAL, x, buttonY);
        buttonY += 25;

        // TODO: Check if player has nation/state/city access
        // For now, show all options
        addAccountButton(AccountType.NATION, x, buttonY);
        buttonY += 25;

        addAccountButton(AccountType.STATE, x, buttonY);
        buttonY += 25;

        addAccountButton(AccountType.CITY, x, buttonY);

        // Back button
        addMenuButton(Button.builder(Component.literal("< Back"),
            btn -> switchMode(ScreenMode.MAIN_MENU))
            .bounds(x + 10, y + 90, 156, 20)
            .build());
    }

    private void addAccountButton(AccountType type, int x, int buttonY) {
        boolean isSelected = type == selectedAccountType;
        String prefix = isSelected ? ">> " : "";

        addMenuButton(Button.builder(Component.literal(prefix + type.getDisplayName()),
            btn -> {
                selectedAccountType = type;
                switchMode(ScreenMode.MAIN_MENU);
                checkBalance(); // Update balance for new account
            })
            .bounds(x + 10, buttonY, 156, 20)
            .build());
    }

    private void buildDepositConfirm(int x, int y) {
        amountInput.setVisible(true);
        amountInput.setFocused(true);
        recipientInput.setVisible(false);

        addMenuButton(Button.builder(Component.literal("Confirm Deposit"),
            btn -> {
                try {
                    double amount = Double.parseDouble(amountInput.getValue());
                    if (amount > 0) {
                        // Server will check if player has enough currency items
                        NetworkHandler.sendToServer(new ATMTransactionPacket(
                            ATMTransactionPacket.Action.DEPOSIT, amount, ""));
                        setStatusMessage("Depositing " + formatCurrency(amount) + "...", 100);
                        amountInput.setValue("");
                    } else {
                        setStatusMessage("Amount must be positive!", 60);
                    }
                } catch (NumberFormatException e) {
                    setStatusMessage("Invalid amount!", 60);
                }
                switchMode(ScreenMode.MAIN_MENU);
            })
            .bounds(x + 10, y + 65, 156, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("< Cancel"),
            btn -> {
                amountInput.setValue("");
                switchMode(ScreenMode.MAIN_MENU);
            })
            .bounds(x + 10, y + 90, 156, 20)
            .build());
    }

    private void buildWithdrawInput(int x, int y) {
        amountInput.setVisible(true);
        amountInput.setFocused(true);
        recipientInput.setVisible(false);

        addMenuButton(Button.builder(Component.literal("Confirm Withdrawal"),
            btn -> {
                try {
                    double amount = Double.parseDouble(amountInput.getValue());
                    if (amount > 0) {
                        NetworkHandler.sendToServer(new ATMTransactionPacket(
                            ATMTransactionPacket.Action.WITHDRAW, amount, ""));
                        setStatusMessage("Withdrawing " + formatCurrency(amount) + "...", 100);
                        amountInput.setValue("");
                    } else {
                        setStatusMessage("Amount must be positive!", 60);
                    }
                } catch (NumberFormatException e) {
                    setStatusMessage("Invalid amount!", 60);
                }
                switchMode(ScreenMode.MAIN_MENU);
            })
            .bounds(x + 10, y + 65, 156, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("< Cancel"),
            btn -> {
                amountInput.setValue("");
                switchMode(ScreenMode.MAIN_MENU);
            })
            .bounds(x + 10, y + 90, 156, 20)
            .build());
    }

    private void buildTransferInput(int x, int y) {
        amountInput.setVisible(true);
        recipientInput.setVisible(true);

        addMenuButton(Button.builder(Component.literal("Confirm Transfer"),
            btn -> {
                try {
                    double amount = Double.parseDouble(amountInput.getValue());
                    String recipient = recipientInput.getValue();
                    if (amount > 0 && !recipient.isEmpty()) {
                        NetworkHandler.sendToServer(new ATMTransactionPacket(
                            ATMTransactionPacket.Action.TRANSFER, amount, recipient));
                        setStatusMessage("Transferring " + formatCurrency(amount) + "...", 100);
                        amountInput.setValue("");
                        recipientInput.setValue("");
                    } else {
                        setStatusMessage("Invalid input!", 60);
                    }
                } catch (NumberFormatException e) {
                    setStatusMessage("Invalid amount!", 60);
                }
                switchMode(ScreenMode.MAIN_MENU);
            })
            .bounds(x + 10, y + 90, 156, 20)
            .build());

        addMenuButton(Button.builder(Component.literal("< Cancel"),
            btn -> {
                amountInput.setValue("");
                recipientInput.setValue("");
                switchMode(ScreenMode.MAIN_MENU);
            })
            .bounds(x + 10, y + 115, 156, 20)
            .build());
    }

    private void addMenuButton(Button button) {
        menuButtons.add(button);
        addRenderableWidget(button);
    }

    private void switchMode(ScreenMode newMode) {
        currentMode = newMode;
        buildUI();
    }

    private void checkBalance() {
        NetworkHandler.sendToServer(new ATMTransactionPacket(
            ATMTransactionPacket.Action.CHECK_BALANCE, 0, ""));
    }

    private String getBankName() {
        Bank bank = EconomyManager.getInstance().getBankRegistry().getBank(selectedBankId);
        return bank != null ? bank.getDisplayName() : "Unknown Bank";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Draw title based on mode
        String title = switch (currentMode) {
            case MAIN_MENU -> getBankName() + " ATM";
            case BANK_SELECT -> "Select Bank";
            case ACCOUNT_SELECT -> "Select Account";
            case DEPOSIT_CONFIRM -> "Deposit Confirmation";
            case WITHDRAW_INPUT -> "Withdraw";
            case TRANSFER_INPUT -> "Transfer";
        };
        graphics.drawString(this.font, title, 8, 6, 0x404040, false);

        // Draw balance
        if (currentMode == ScreenMode.MAIN_MENU) {
            graphics.drawString(this.font, "Balance: " + formatCurrency(currentBalance),
                8, imageHeight - 94, 0x3F3F3F, false);
        }

        // Draw status message
        if (statusMessageTicks > 0) {
            graphics.drawString(this.font, statusMessage, 8, imageHeight - 84, 0x00AA00, false);
        }

        // Draw inventory label
        graphics.drawString(this.font, this.playerInventoryTitle, 8, imageHeight - 94 + 3, 0x404040, false);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (statusMessageTicks > 0) {
            statusMessageTicks--;
        }
        if (amountInput != null) {
            amountInput.tick();
        }
        if (recipientInput != null) {
            recipientInput.tick();
        }
    }

    public void setBalance(double balance) {
        this.currentBalance = balance;
    }

    public void setStatusMessage(String message, int ticks) {
        this.statusMessage = message;
        this.statusMessageTicks = ticks;
    }

    private String formatCurrency(double amount) {
        return String.format("$%.2f", amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amountInput != null && amountInput.isFocused()) {
            return amountInput.keyPressed(keyCode, scanCode, modifiers);
        }
        if (recipientInput != null && recipientInput.isFocused()) {
            return recipientInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

