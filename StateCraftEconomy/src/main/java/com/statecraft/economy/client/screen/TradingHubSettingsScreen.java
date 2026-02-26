package com.statecraft.economy.client.screen;

import com.statecraft.economy.block.entity.TradingHubBlockEntity;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.RequestTradingHubSuggestionsPacket;
import com.statecraft.economy.network.packets.TradingHubSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Settings screen for Trading Hub
 * Allows owner/admin to configure:
 * - Profit destination (ATM or currency items)
 * - Profit sharing between multiple players
 */
public class TradingHubSettingsScreen extends Screen {

    // Colors (matching StateCraft style)
    private static final int COLOR_PRIMARY = 0xFF4A90D9;
    private static final int COLOR_SECONDARY = 0xFF2ECC71;
    private static final int COLOR_WARNING = 0xFFE74C3C;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_PANEL = 0xDD1A1A2E;
    private static final int COLOR_BORDER = 0xFF3D3D5C;

    // Layout
    private int guiLeft;
    private int guiTop;
    private int guiWidth = 280;
    private int guiHeight = 220;

    private final TradingHubScreen parentScreen;
    private final TradingHubBlockEntity blockEntity;

    // Settings state
    private boolean depositToATM;
    private List<ProfitShareEntry> profitShares = new ArrayList<>();

    // UI Components
    private Button depositModeButton;
    private EditBox playerNameInput;
    private EditBox percentageInput;
    private Button addShareButton;
    private final List<Button> removeButtons = new ArrayList<>();

    // Scroll state for profit share list
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_SHARES = 4;

    // Error message display
    private String errorMessage = "";
    private int errorMessageTicks = 0;

    // Autocomplete state
    private List<String> companyNameSuggestions = new ArrayList<>();
    private List<String> playerNameSuggestions = new ArrayList<>();
    private List<String> filteredSuggestions = new ArrayList<>();
    private int suggestionScrollOffset = 0;
    private static final int MAX_VISIBLE_SUGGESTIONS = 5;
    private boolean showSuggestions = false;
    private int selectedSuggestionIndex = -1;

    /**
     * Local copy of profit share for editing
     */
    private static class ProfitShareEntry {
        UUID playerUUID;
        String playerName;
        double percentage;
        boolean isCompany;

        ProfitShareEntry(UUID uuid, String name, double percentage) {
            this(uuid, name, percentage, false);
        }

        ProfitShareEntry(UUID uuid, String name, double percentage, boolean isCompany) {
            this.playerUUID = uuid;
            this.playerName = name;
            this.percentage = percentage;
            this.isCompany = isCompany;
        }

        ProfitShareEntry(TradingHubBlockEntity.ProfitShare share) {
            this.playerUUID = share.playerUUID;
            this.playerName = share.playerName;
            this.percentage = share.percentage;
            this.isCompany = share.isCompany;
        }
    }

    public TradingHubSettingsScreen(TradingHubScreen parentScreen, TradingHubBlockEntity blockEntity) {
        super(Component.literal("Trading Hub Settings"));
        this.parentScreen = parentScreen;
        this.blockEntity = blockEntity;

        // Load current settings
        this.depositToATM = blockEntity.isDepositToATM();
        for (TradingHubBlockEntity.ProfitShare share : blockEntity.getProfitShares()) {
            profitShares.add(new ProfitShareEntry(share));
        }
    }

    @Override
    protected void init() {
        super.init();

        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        // Deposit mode toggle button
        depositModeButton = this.addRenderableWidget(Button.builder(
            Component.literal(depositToATM ? "Deposit to Bank Account" : "Give Currency Items"),
            btn -> toggleDepositMode()
        ).bounds(guiLeft + 20, guiTop + 45, guiWidth - 40, 20).build());

        // Add profit share section
        int shareY = guiTop + 130;

        // Player name input
        playerNameInput = new EditBox(this.font, guiLeft + 20, shareY, 100, 18, Component.literal("Name"));
        playerNameInput.setMaxLength(64);
        playerNameInput.setHint(Component.literal("Name or @Company"));
        playerNameInput.setResponder(this::onNameInputChanged);
        addRenderableWidget(playerNameInput);

        // Percentage input
        percentageInput = new EditBox(this.font, guiLeft + 125, shareY, 50, 18, Component.literal("Percentage"));
        percentageInput.setMaxLength(5);
        percentageInput.setHint(Component.literal("%"));
        percentageInput.setFilter(s -> s.matches("[0-9]*\\.?[0-9]*"));
        addRenderableWidget(percentageInput);

        // Add button
        addShareButton = this.addRenderableWidget(Button.builder(
            Component.literal("+"),
            btn -> addProfitShare()
        ).bounds(guiLeft + 180, shareY, 20, 18).build());

        // Save and cancel buttons
        this.addRenderableWidget(Button.builder(
            Component.literal("Save"),
            btn -> saveSettings()
        ).bounds(guiLeft + 20, guiTop + guiHeight - 30, 80, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            btn -> cancel()
        ).bounds(guiLeft + guiWidth - 100, guiTop + guiHeight - 30, 80, 20).build());

        rebuildShareButtons();

        // Request autocomplete suggestions from server
        NetworkHandler.sendToServer(new RequestTradingHubSuggestionsPacket());
    }

    private void rebuildShareButtons() {
        // Remove existing remove buttons
        for (Button btn : removeButtons) {
            removeWidget(btn);
        }
        removeButtons.clear();

        // Add remove buttons for each profit share
        int startY = guiTop + 75;
        int visibleCount = Math.min(profitShares.size() - scrollOffset, MAX_VISIBLE_SHARES);

        for (int i = 0; i < visibleCount; i++) {
            int shareIndex = i + scrollOffset;
            int y = startY + i * 14;

            Button removeBtn = this.addRenderableWidget(Button.builder(
                Component.literal("×"),
                btn -> removeProfitShare(shareIndex)
            ).bounds(guiLeft + guiWidth - 35, y, 14, 12).build());
            removeButtons.add(removeBtn);
        }
    }

    private void toggleDepositMode() {
        depositToATM = !depositToATM;
        depositModeButton.setMessage(Component.literal(
            depositToATM ? "Deposit to Bank Account" : "Give Currency Items"
        ));
    }

    private void addProfitShare() {
        String rawName = playerNameInput.getValue().trim();
        String percentStr = percentageInput.getValue().trim();

        if (rawName.isEmpty()) {
            setErrorMessage("Enter a player name or @CompanyName");
            return;
        }

        if (percentStr.isEmpty()) {
            setErrorMessage("Please enter a percentage");
            return;
        }

        // Detect company entries via @ prefix
        boolean isCompany = rawName.startsWith("@");
        String displayName = isCompany ? rawName.substring(1).trim() : rawName;

        if (displayName.isEmpty()) {
            setErrorMessage("Enter a name after @");
            return;
        }

        try {
            double percentage = Double.parseDouble(percentStr) / 100.0; // Convert from display % to decimal
            if (percentage <= 0) {
                setErrorMessage("Percentage must be greater than 0");
                return;
            }
            if (percentage > 1.0) {
                setErrorMessage("Percentage cannot exceed 100%");
                return;
            }

            // Calculate current total excluding this entry if updating
            double currentTotal = 0;
            ProfitShareEntry existingEntry = null;
            for (ProfitShareEntry entry : profitShares) {
                if (entry.playerName.equalsIgnoreCase(displayName) && entry.isCompany == isCompany) {
                    existingEntry = entry;
                } else {
                    currentTotal += entry.percentage;
                }
            }

            // Check if adding this would exceed 100%
            if (currentTotal + percentage > 1.0 + 0.001) { // Small epsilon for floating point
                setErrorMessage(String.format("Total share would be %.0f%% (max 100%%)", (currentTotal + percentage) * 100));
                return;
            }

            // Check if entry already exists in list
            if (existingEntry != null) {
                // Update existing entry
                existingEntry.percentage = percentage;
                playerNameInput.setValue("");
                percentageInput.setValue("");
                rebuildShareButtons();
                return;
            }

            // For new entries, we'll use a placeholder UUID
            // The server will resolve the actual UUID when saving
            UUID placeholderUUID = UUID.nameUUIDFromBytes(displayName.toLowerCase().getBytes());
            profitShares.add(new ProfitShareEntry(placeholderUUID, displayName, percentage, isCompany));

            playerNameInput.setValue("");
            percentageInput.setValue("");
            rebuildShareButtons();
        } catch (NumberFormatException e) {
            setErrorMessage("Invalid percentage format");
        }
    }

    private void setErrorMessage(String message) {
        this.errorMessage = message;
        this.errorMessageTicks = 80; // Show for 4 seconds
    }

    private void removeProfitShare(int index) {
        if (index >= 0 && index < profitShares.size()) {
            profitShares.remove(index);
            if (scrollOffset > 0 && scrollOffset >= profitShares.size()) {
                scrollOffset = Math.max(0, profitShares.size() - MAX_VISIBLE_SHARES);
            }
            rebuildShareButtons();
        }
    }

    private void saveSettings() {
        // Validate total shares don't exceed 100%
        double total = profitShares.stream().mapToDouble(s -> s.percentage).sum();
        if (total > 1.0 + 0.001) { // Small epsilon for floating point
            setErrorMessage(String.format("Total shares (%.0f%%) exceed 100%%!", total * 100));
            return;
        }

    // Build packet data
    List<TradingHubSettingsPacket.ShareData> shareData = new ArrayList<>();
    for (ProfitShareEntry entry : profitShares) {
        shareData.add(new TradingHubSettingsPacket.ShareData(entry.playerName, entry.percentage, entry.isCompany));
    }

        // Send settings to server
        NetworkHandler.sendToServer(new TradingHubSettingsPacket(
            blockEntity.getBlockPos(),
            depositToATM,
            shareData
        ));

        // Return to main screen
        parentScreen.returnFromSettings();
    }

    private void cancel() {
        parentScreen.returnFromSettings();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Panel background
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, COLOR_PANEL);

        // Border
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + 1, COLOR_BORDER);
        graphics.fill(guiLeft, guiTop + guiHeight - 1, guiLeft + guiWidth, guiTop + guiHeight, COLOR_BORDER);
        graphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + guiHeight, COLOR_BORDER);
        graphics.fill(guiLeft + guiWidth - 1, guiTop, guiLeft + guiWidth, guiTop + guiHeight, COLOR_BORDER);

        // Title
        graphics.drawCenteredString(this.font, "§lTrading Hub Settings", this.width / 2, guiTop + 8, COLOR_PRIMARY);

        // Divider
        graphics.fill(guiLeft + 10, guiTop + 22, guiLeft + guiWidth - 10, guiTop + 23, COLOR_BORDER);

        // Section: Profit Destination
        graphics.drawString(this.font, "§eProfit Destination:", guiLeft + 20, guiTop + 32, COLOR_TEXT);

        // Section: Profit Sharing
        graphics.drawString(this.font, "§eProfit Sharing:", guiLeft + 20, guiTop + 68, COLOR_TEXT);

        // Display profit shares
        int startY = guiTop + 80;
        int visibleCount = Math.min(profitShares.size() - scrollOffset, MAX_VISIBLE_SHARES);

        if (profitShares.isEmpty()) {
            graphics.drawString(this.font, "§7No shares configured", guiLeft + 25, startY, COLOR_TEXT);
            graphics.drawString(this.font, "§7(100% to selling player)", guiLeft + 25, startY + 12, COLOR_TEXT);
        } else {
            for (int i = 0; i < visibleCount; i++) {
                int shareIndex = i + scrollOffset;
                ProfitShareEntry entry = profitShares.get(shareIndex);
                int y = startY + i * 14;

                String namePrefix = entry.isCompany ? "§d@" : "§f";
                String text = String.format("%s%s: §a%.0f%%", namePrefix, entry.playerName, entry.percentage * 100);
                graphics.drawString(this.font, text, guiLeft + 25, y + 2, COLOR_TEXT);
            }

            // Show scroll indicators if needed
            if (scrollOffset > 0) {
                graphics.drawString(this.font, "▲", guiLeft + guiWidth - 25, guiTop + 68, 0xFFAAAAAA);
            }
            if (scrollOffset + MAX_VISIBLE_SHARES < profitShares.size()) {
                graphics.drawString(this.font, "▼", guiLeft + guiWidth - 25, startY + MAX_VISIBLE_SHARES * 14 - 10, 0xFFAAAAAA);
            }

            // Show total percentage
            double total = profitShares.stream().mapToDouble(s -> s.percentage).sum() * 100;
            int totalColor = Math.abs(total - 100) < 0.1 ? 0xFF55FF55 : 0xFFFFAA00;
            graphics.drawString(this.font, String.format("Total: %.0f%%", total), guiLeft + 25, guiTop + 115, totalColor);
        }

        // Add share label
        graphics.drawString(this.font, "§7Add share:", guiLeft + 20, guiTop + 156, COLOR_TEXT);

        // Error message display
        if (errorMessageTicks > 0 && !errorMessage.isEmpty()) {
            int msgWidth = this.font.width(errorMessage);
            graphics.drawString(this.font, "§c" + errorMessage,
                guiLeft + (guiWidth - msgWidth) / 2, guiTop + 172, 0xFFFF5555);
        }

        // Owner info
        String ownerText = blockEntity.getOwnerName().isEmpty() ? "Unowned" : "Owner: " + blockEntity.getOwnerName();
        graphics.drawString(this.font, "§7" + ownerText, guiLeft + 20, guiTop + guiHeight - 45, 0xFFAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Render autocomplete dropdown on top of everything
        if (showSuggestions && !filteredSuggestions.isEmpty() && playerNameInput.isFocused()) {
            int dropdownX = playerNameInput.getX();
            int dropdownW = playerNameInput.getWidth();
            int visibleCount = Math.min(filteredSuggestions.size() - suggestionScrollOffset, MAX_VISIBLE_SUGGESTIONS);
            int dropdownH = visibleCount * 12;
            int dropdownY = playerNameInput.getY() - dropdownH;

            // Background
            graphics.fill(dropdownX - 1, dropdownY - 1, dropdownX + dropdownW + 1, dropdownY + dropdownH + 1, 0xFF3D3D5C);
            graphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + dropdownH, 0xEE1A1A2E);

            for (int i = 0; i < visibleCount; i++) {
                int idx = i + suggestionScrollOffset;
                int y = dropdownY + i * 12;
                boolean isHovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                                    mouseY >= y && mouseY < y + 12;
                boolean isSelected = idx == selectedSuggestionIndex;

                if (isHovered || isSelected) {
                    graphics.fill(dropdownX, y, dropdownX + dropdownW, y + 12, 0x44FFFFFF);
                }

                String name = filteredSuggestions.get(idx);
                int color = name.startsWith("@") ? 0xFFDD88FF : 0xFFFFFFFF;
                graphics.drawString(this.font, name, dropdownX + 3, y + 2, color);
            }

            // Scroll indicators
            if (suggestionScrollOffset > 0) {
                graphics.drawString(this.font, "▲", dropdownX + dropdownW - 10, dropdownY + 1, 0xFFAAAAAA);
            }
            if (suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS < filteredSuggestions.size()) {
                graphics.drawString(this.font, "▼", dropdownX + dropdownW - 10, dropdownY + dropdownH - 10, 0xFFAAAAAA);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (errorMessageTicks > 0) {
            errorMessageTicks--;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Handle suggestion list scrolling
        if (showSuggestions && !filteredSuggestions.isEmpty()) {
            int dropdownX = playerNameInput.getX();
            int dropdownY = playerNameInput.getY() - Math.min(filteredSuggestions.size(), MAX_VISIBLE_SUGGESTIONS) * 12;
            int dropdownW = playerNameInput.getWidth();
            int dropdownH = Math.min(filteredSuggestions.size(), MAX_VISIBLE_SUGGESTIONS) * 12;
            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                mouseY >= dropdownY && mouseY < dropdownY + dropdownH) {
                if (delta > 0 && suggestionScrollOffset > 0) {
                    suggestionScrollOffset--;
                } else if (delta < 0 && suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS < filteredSuggestions.size()) {
                    suggestionScrollOffset++;
                }
                return true;
            }
        }

        if (mouseX >= guiLeft && mouseX < guiLeft + guiWidth &&
            mouseY >= guiTop + 75 && mouseY < guiTop + 130) {
            if (delta > 0 && scrollOffset > 0) {
                scrollOffset--;
                rebuildShareButtons();
            } else if (delta < 0 && scrollOffset + MAX_VISIBLE_SHARES < profitShares.size()) {
                scrollOffset++;
                rebuildShareButtons();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ==================== Autocomplete ====================

    /**
     * Called by network handler when suggestions are received from server
     */
    public void updateSuggestions(List<String> companyNames, List<String> playerNames) {
        this.companyNameSuggestions = companyNames;
        this.playerNameSuggestions = playerNames;
    }

    private void onNameInputChanged(String text) {
        updateFilteredSuggestions(text);
    }

    private void updateFilteredSuggestions(String input) {
        filteredSuggestions.clear();
        suggestionScrollOffset = 0;
        selectedSuggestionIndex = -1;

        if (input.isEmpty()) {
            showSuggestions = false;
            return;
        }

        boolean isCompanyMode = input.startsWith("@");
        String searchText = isCompanyMode ? input.substring(1).toLowerCase(Locale.ROOT) : input.toLowerCase(Locale.ROOT);

        if (searchText.isEmpty() && isCompanyMode) {
            // Show all companies when just "@" is typed
            for (String name : companyNameSuggestions) {
                filteredSuggestions.add("@" + name);
            }
        } else if (isCompanyMode) {
            for (String name : companyNameSuggestions) {
                if (name.toLowerCase(Locale.ROOT).startsWith(searchText)) {
                    filteredSuggestions.add("@" + name);
                }
            }
            // Also add contains matches after startsWith matches
            for (String name : companyNameSuggestions) {
                String entry = "@" + name;
                if (!filteredSuggestions.contains(entry) && name.toLowerCase(Locale.ROOT).contains(searchText)) {
                    filteredSuggestions.add(entry);
                }
            }
        } else {
            for (String name : playerNameSuggestions) {
                if (name.toLowerCase(Locale.ROOT).startsWith(searchText)) {
                    filteredSuggestions.add(name);
                }
            }
            for (String name : playerNameSuggestions) {
                if (!filteredSuggestions.contains(name) && name.toLowerCase(Locale.ROOT).contains(searchText)) {
                    filteredSuggestions.add(name);
                }
            }
        }

        showSuggestions = !filteredSuggestions.isEmpty();
    }

    private void applySuggestion(String suggestion) {
        playerNameInput.setValue(suggestion);
        showSuggestions = false;
        filteredSuggestions.clear();
        // Move focus to the percentage input
        percentageInput.setFocused(true);
        playerNameInput.setFocused(false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if click is on a suggestion
        if (showSuggestions && !filteredSuggestions.isEmpty()) {
            int dropdownX = playerNameInput.getX();
            int dropdownW = playerNameInput.getWidth();
            int visibleCount = Math.min(filteredSuggestions.size() - suggestionScrollOffset, MAX_VISIBLE_SUGGESTIONS);
            int dropdownY = playerNameInput.getY() - visibleCount * 12;

            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                mouseY >= dropdownY && mouseY < dropdownY + visibleCount * 12) {
                int index = (int)(mouseY - dropdownY) / 12 + suggestionScrollOffset;
                if (index >= 0 && index < filteredSuggestions.size()) {
                    applySuggestion(filteredSuggestions.get(index));
                    return true;
                }
            }
            // Clicked outside suggestions — hide them
            showSuggestions = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showSuggestions && !filteredSuggestions.isEmpty() && playerNameInput.isFocused()) {
            // Tab to accept top suggestion
            if (keyCode == 258) { // Tab
                int idx = selectedSuggestionIndex >= 0 ? selectedSuggestionIndex : suggestionScrollOffset;
                if (idx < filteredSuggestions.size()) {
                    applySuggestion(filteredSuggestions.get(idx));
                    return true;
                }
            }
            // Up/Down arrow keys to navigate suggestions
            if (keyCode == 265) { // Up
                if (selectedSuggestionIndex > 0) {
                    selectedSuggestionIndex--;
                    if (selectedSuggestionIndex < suggestionScrollOffset) {
                        suggestionScrollOffset = selectedSuggestionIndex;
                    }
                }
                return true;
            }
            if (keyCode == 264) { // Down
                if (selectedSuggestionIndex < filteredSuggestions.size() - 1) {
                    selectedSuggestionIndex++;
                    if (selectedSuggestionIndex >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                        suggestionScrollOffset = selectedSuggestionIndex - MAX_VISIBLE_SUGGESTIONS + 1;
                    }
                }
                return true;
            }
            // Enter to accept selected suggestion
            if (keyCode == 257 && selectedSuggestionIndex >= 0) { // Enter
                applySuggestion(filteredSuggestions.get(selectedSuggestionIndex));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

