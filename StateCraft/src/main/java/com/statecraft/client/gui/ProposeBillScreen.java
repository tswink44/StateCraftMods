package com.statecraft.client.gui;

import com.statecraft.legislature.PolicyType;
import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.ProposeBillPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for proposing new legislation
 * Uses dropdown menus for policy selection to avoid overlapping UI elements
 */
public class ProposeBillScreen extends StateCraftScreen {

    private final String nationName;

    // Input fields
    private EditBox titleField;
    private EditBox descriptionField;
    private EditBox policyValueField;

    // Policy selection - dropdown approach
    private PolicyType.Category selectedCategory = null;
    private PolicyType selectedPolicy = null;
    private boolean showCategoryDropdown = false;
    private boolean showPolicyDropdown = false;
    private int dropdownScrollOffset = 0;
    private static final int DROPDOWN_MAX_VISIBLE = 6;

    // Boolean toggle state (for BOOLEAN policies)
    private boolean booleanToggleValue = false;

    // TEXT policy value (stored separately to persist across screen changes)
    private String textPolicyValue = "";

    // Current bill policy changes
    private final Map<PolicyType, String> policyChanges = new HashMap<>();

    // Added policies scroll
    private int addedPoliciesScrollOffset = 0;
    private static final int MAX_VISIBLE_ADDED = 3;

    // Error message
    private String errorMessage = null;
    private int errorTicks = 0;

    public ProposeBillScreen(String nationName) {
        super(Component.literal("Propose Legislation"));
        this.nationName = nationName;
        this.guiWidth = 320;
        this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        int fieldX = guiLeft + 75;
        int fieldWidth = guiWidth - 90;
        int y = guiTop + 25;

        // Title field
        this.titleField = new EditBox(this.font, fieldX, y, fieldWidth, 14, Component.literal("Title"));
        this.titleField.setMaxLength(64);
        this.titleField.setHint(Component.literal("Bill title..."));
        this.addRenderableWidget(this.titleField);

        // Description field
        y += 18;
        this.descriptionField = new EditBox(this.font, fieldX, y, fieldWidth, 14, Component.literal("Description"));
        this.descriptionField.setMaxLength(256);
        this.descriptionField.setHint(Component.literal("Brief description..."));
        this.addRenderableWidget(this.descriptionField);

        // Policy value field (next to policy dropdown)
        y += 38;
        this.policyValueField = new EditBox(this.font, guiLeft + guiWidth - 75, y, 60, 14, Component.literal("Value"));
        this.policyValueField.setMaxLength(32);
        this.policyValueField.setHint(Component.literal("Value"));
        this.addRenderableWidget(this.policyValueField);

        // Add Policy button
        y += 18;
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 85, y, 70, 14,
            Component.literal("§a+ Add"),
            btn -> addSelectedPolicy()
        ));

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 25;

        // Submit button
        this.addRenderableWidget(createButton(
            guiLeft + 10, buttonY, 70, 18,
            Component.literal("§aSubmit"),
            btn -> submitBill()
        ));

        // Clear button
        this.addRenderableWidget(createButton(
            guiLeft + 85, buttonY, 55, 18,
            Component.literal("Clear"),
            btn -> clearForm()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 65, buttonY, 55, 18,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = guiTop + 25;

        // Labels
        graphics.drawString(this.font, "§7Title:", guiLeft + 12, y + 3, COLOR_TEXT);
        y += 18;
        graphics.drawString(this.font, "§7Desc:", guiLeft + 12, y + 3, COLOR_TEXT);

        // Divider after basic info
        y += 20;
        renderDivider(graphics, guiLeft + 5, y, guiWidth - 10);

        // Policy Selection Section
        y += 8;
        graphics.drawString(this.font, "§6Add Policy:", guiLeft + 12, y, COLOR_PRIMARY);

        // Category dropdown button
        y += 14;
        int dropdownWidth = 90;
        String categoryText = selectedCategory != null ? selectedCategory.getDisplayName() : "Category...";
        renderDropdownButton(graphics, guiLeft + 12, y, dropdownWidth, categoryText, mouseX, mouseY, showCategoryDropdown);

        // Policy dropdown button (only if category selected)
        int policyDropdownX = guiLeft + 12 + dropdownWidth + 5;
        int policyDropdownWidth = 100;
        if (selectedCategory != null) {
            String policyText = selectedPolicy != null ? truncate(selectedPolicy.getDisplayName(), 12) : "Policy...";
            renderDropdownButton(graphics, policyDropdownX, y, policyDropdownWidth, policyText, mouseX, mouseY, showPolicyDropdown);
        }

        // Value input area - depends on policy type
        if (selectedPolicy != null) {
            PolicyType.ValueType valueType = selectedPolicy.getValueType();

            if (valueType == PolicyType.ValueType.BOOLEAN) {
                // Hide the text field, show toggle button
                policyValueField.visible = false;
                int toggleX = guiLeft + guiWidth - 75;
                int toggleY = y;
                boolean hovered = mouseX >= toggleX && mouseX < toggleX + 60 && mouseY >= toggleY && mouseY < toggleY + 14;
                int bgColor = hovered ? 0xFF4A4A6A : 0xFF2A2A4A;
                graphics.fill(toggleX, toggleY, toggleX + 60, toggleY + 14, bgColor);
                graphics.fill(toggleX, toggleY, toggleX + 60, toggleY + 1, 0xFF5A5A7A);
                graphics.fill(toggleX, toggleY + 13, toggleX + 60, toggleY + 14, 0xFF1A1A2A);
                String boolText = booleanToggleValue ? "§aTrue" : "§cFalse";
                graphics.drawCenteredString(this.font, boolText, toggleX + 30, toggleY + 3, COLOR_TEXT);
            } else if (valueType == PolicyType.ValueType.TEXT) {
                // For TEXT type, show "Edit..." button that opens a larger text editor
                policyValueField.visible = false;
                int btnX = guiLeft + guiWidth - 75;
                int btnY = y;
                boolean hovered = mouseX >= btnX && mouseX < btnX + 60 && mouseY >= btnY && mouseY < btnY + 14;
                int bgColor = hovered ? 0xFF4A6A4A : 0xFF2A4A2A;
                graphics.fill(btnX, btnY, btnX + 60, btnY + 14, bgColor);
                graphics.fill(btnX, btnY, btnX + 60, btnY + 1, 0xFF5A7A5A);
                graphics.fill(btnX, btnY + 13, btnX + 60, btnY + 14, 0xFF1A2A1A);
                String btnText = textPolicyValue.isEmpty() ? "§eEdit..." : "§aEdited";
                graphics.drawCenteredString(this.font, btnText, btnX + 30, btnY + 3, COLOR_TEXT);
            } else {
                // Show normal value field
                policyValueField.visible = true;
                // Set the hint in the field itself to avoid text overlap with Add button
                policyValueField.setHint(Component.literal(getValueHint(selectedPolicy)));
            }
        } else {
            policyValueField.visible = true;
        }

        // Policy description (when a policy is selected)
        if (selectedPolicy != null) {
            int descY = y + 34; // Below the Add button row (y + 18 for button row + 16 for spacing)
            String desc = selectedPolicy.getDescription();
            // Wrap description text to fit within full width minus margins
            int maxWidth = guiWidth - 30;
            List<String> wrappedLines = wrapText(desc, maxWidth);
            graphics.drawString(this.font, "§8" + wrappedLines.get(0), guiLeft + 12, descY, 0xFF888888);
            if (wrappedLines.size() > 1) {
                graphics.drawString(this.font, "§8" + wrappedLines.get(1), guiLeft + 12, descY + 10, 0xFF888888);
            }
        }

        // Render dropdowns (on top of everything else, so render last)
        // These are rendered after the rest so they appear on top

        // Divider before added policies
        int policiesY = guiTop + 140;
        renderDivider(graphics, guiLeft + 5, policiesY, guiWidth - 10);

        // Added Policies Section
        policiesY += 8;
        graphics.drawString(this.font, "§6Bill Policies §7(" + policyChanges.size() + "):", guiLeft + 12, policiesY, COLOR_PRIMARY);
        policiesY += 14;

        if (policyChanges.isEmpty()) {
            graphics.drawString(this.font, "§8No policies added yet", guiLeft + 15, policiesY, 0xFFAAAAAA);
        } else {
            List<Map.Entry<PolicyType, String>> entries = new ArrayList<>(policyChanges.entrySet());
            int endIdx = Math.min(addedPoliciesScrollOffset + MAX_VISIBLE_ADDED, entries.size());

            for (int i = addedPoliciesScrollOffset; i < endIdx; i++) {
                Map.Entry<PolicyType, String> entry = entries.get(i);
                String display = "§7• §f" + truncate(entry.getKey().getDisplayName(), 15) + ": §a" + formatValue(entry.getKey(), entry.getValue());
                graphics.drawString(this.font, display, guiLeft + 15, policiesY, COLOR_TEXT);

                // Remove button (X)
                int removeX = guiLeft + guiWidth - 25;
                boolean hoverRemove = mouseX >= removeX && mouseX < removeX + 12 && mouseY >= policiesY - 1 && mouseY < policiesY + 10;
                graphics.drawString(this.font, hoverRemove ? "§c✕" : "§8✕", removeX, policiesY, COLOR_TEXT);

                policiesY += 12;
            }

            // Scroll indicators for added policies
            if (entries.size() > MAX_VISIBLE_ADDED) {
                if (addedPoliciesScrollOffset > 0) {
                    graphics.drawString(this.font, "§7▲", guiLeft + guiWidth - 15, guiTop + 140, 0xFF888888);
                }
                if (addedPoliciesScrollOffset + MAX_VISIBLE_ADDED < entries.size()) {
                    graphics.drawString(this.font, "§7▼", guiLeft + guiWidth - 15, guiTop + 165, 0xFF888888);
                }
            }
        }

        // Error message at bottom
        if (errorMessage != null && errorTicks > 0) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage,
                this.width / 2, guiTop + guiHeight - 40, COLOR_WARNING);
        }

        // Render dropdowns last so they appear on top
        y = guiTop + 25 + 18 + 20 + 8 + 14; // recalc y position for dropdowns
        if (showCategoryDropdown) {
            renderCategoryDropdown(graphics, guiLeft + 12, y + 14, dropdownWidth, mouseX, mouseY);
        }
        if (showPolicyDropdown && selectedCategory != null) {
            renderPolicyDropdown(graphics, policyDropdownX, y + 14, policyDropdownWidth, mouseX, mouseY);
        }
    }

    private void renderDropdownButton(GuiGraphics graphics, int x, int y, int width, String text, int mouseX, int mouseY, boolean isOpen) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        int bgColor = isOpen ? 0xFF4A4A6A : (hovered ? 0xFF3A3A5A : 0xFF2A2A4A);

        graphics.fill(x, y, x + width, y + 14, bgColor);
        graphics.fill(x, y, x + width, y + 1, 0xFF5A5A7A); // top border
        graphics.fill(x, y + 13, x + width, y + 14, 0xFF1A1A2A); // bottom border

        graphics.drawString(this.font, text, x + 3, y + 3, 0xFFFFFFFF);
        graphics.drawString(this.font, isOpen ? "▲" : "▼", x + width - 10, y + 3, 0xFFAAAAAA);
    }

    private void renderCategoryDropdown(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        PolicyType.Category[] categories = PolicyType.Category.values();
        int height = Math.min(categories.length, DROPDOWN_MAX_VISIBLE) * 12 + 4;

        // Background
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF1A1A2A);
        graphics.fill(x, y, x + width, y + height, 0xFF2A2A4A);

        int itemY = y + 2;
        for (int i = 0; i < Math.min(categories.length, DROPDOWN_MAX_VISIBLE); i++) {
            PolicyType.Category cat = categories[i];
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + 12;

            if (hovered) {
                graphics.fill(x, itemY, x + width, itemY + 12, 0xFF4A4A6A);
            }

            String color = cat == selectedCategory ? "§e" : "§f";
            graphics.drawString(this.font, color + cat.getDisplayName(), x + 3, itemY + 2, COLOR_TEXT);
            itemY += 12;
        }
    }

    private void renderPolicyDropdown(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        List<PolicyType> policies = getPoliciesForCategory(selectedCategory);
        if (policies.isEmpty()) return;

        int visibleCount = Math.min(policies.size() - dropdownScrollOffset, DROPDOWN_MAX_VISIBLE);
        int height = visibleCount * 12 + 4;

        // Background
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF1A1A2A);
        graphics.fill(x, y, x + width, y + height, 0xFF2A2A4A);

        int itemY = y + 2;
        for (int i = dropdownScrollOffset; i < dropdownScrollOffset + visibleCount; i++) {
            PolicyType policy = policies.get(i);
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + 12;
            boolean alreadyAdded = policyChanges.containsKey(policy);

            if (hovered && !alreadyAdded) {
                graphics.fill(x, itemY, x + width, itemY + 12, 0xFF4A4A6A);
            }

            String color = alreadyAdded ? "§8" : (policy == selectedPolicy ? "§e" : "§f");
            String check = alreadyAdded ? "§a✓" : " ";
            graphics.drawString(this.font, check + color + truncate(policy.getDisplayName(), 11), x + 3, itemY + 2, COLOR_TEXT);
            itemY += 12;
        }

        // Scroll indicators
        if (dropdownScrollOffset > 0) {
            graphics.drawString(this.font, "§7▲", x + width - 10, y + 2, 0xFF888888);
        }
        if (dropdownScrollOffset + DROPDOWN_MAX_VISIBLE < policies.size()) {
            graphics.drawString(this.font, "§7▼", x + width - 10, y + height - 12, 0xFF888888);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    /**
     * Wrap text to fit within a given pixel width, returning up to 2 lines
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (this.font.width(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    // Single word too long, truncate it
                    lines.add(truncate(word, maxWidth / 6));
                    currentLine = new StringBuilder();
                }
                // Only keep 2 lines max
                if (lines.size() >= 2) break;
            }
        }

        if (currentLine.length() > 0 && lines.size() < 2) {
            lines.add(currentLine.toString());
        }

        if (lines.isEmpty()) {
            lines.add("");
        }

        return lines;
    }

    private List<PolicyType> getPoliciesForCategory(PolicyType.Category category) {
        List<PolicyType> result = new ArrayList<>();
        if (category == null) return result;
        for (PolicyType policy : PolicyType.values()) {
            if (policy.getCategory() == category) {
                result.add(policy);
            }
        }
        return result;
    }

    private String getValueHint(PolicyType policy) {
        return switch (policy.getValueType()) {
            case BOOLEAN -> "true/false";
            case INTEGER -> (int)policy.getMinValue() + "-" + (int)policy.getMaxValue();
            case PERCENTAGE -> "0-100%";
            case CURRENCY -> "$amount";
            case TEXT -> "text";
            case NATION_TARGET -> "nation";
        };
    }

    private String formatValue(PolicyType policy, String value) {
        try {
            return switch (policy.getValueType()) {
                case BOOLEAN -> value.equalsIgnoreCase("true") ? "Yes" : "No";
                case PERCENTAGE -> String.format("%.0f%%", Double.parseDouble(value) * 100);
                case CURRENCY -> "$" + value;
                default -> value;
            };
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = guiTop + 25 + 18 + 20 + 8 + 14; // dropdown button Y position
            int dropdownWidth = 90;
            int policyDropdownX = guiLeft + 12 + dropdownWidth + 5;
            int policyDropdownWidth = 100;

            // Boolean toggle click
            if (selectedPolicy != null && selectedPolicy.getValueType() == PolicyType.ValueType.BOOLEAN) {
                int toggleX = guiLeft + guiWidth - 75;
                int toggleY = y;
                if (mouseX >= toggleX && mouseX < toggleX + 60 && mouseY >= toggleY && mouseY < toggleY + 14) {
                    booleanToggleValue = !booleanToggleValue;
                    return true;
                }
            }

            // TEXT edit button click - open text editor screen
            if (selectedPolicy != null && selectedPolicy.getValueType() == PolicyType.ValueType.TEXT) {
                int btnX = guiLeft + guiWidth - 75;
                int btnY = y;
                if (mouseX >= btnX && mouseX < btnX + 60 && mouseY >= btnY && mouseY < btnY + 14) {
                    // Open text editor screen
                    this.minecraft.setScreen(new LongTextEditorScreen(
                        selectedPolicy.getDisplayName(),
                        textPolicyValue,
                        text -> {
                            textPolicyValue = text;
                            this.minecraft.setScreen(this);
                        },
                        () -> this.minecraft.setScreen(this)
                    ));
                    return true;
                }
            }

            // Category dropdown button click
            if (mouseX >= guiLeft + 12 && mouseX < guiLeft + 12 + dropdownWidth &&
                mouseY >= y && mouseY < y + 14) {
                showCategoryDropdown = !showCategoryDropdown;
                showPolicyDropdown = false;
                dropdownScrollOffset = 0;
                return true;
            }

            // Policy dropdown button click
            if (selectedCategory != null && mouseX >= policyDropdownX && mouseX < policyDropdownX + policyDropdownWidth &&
                mouseY >= y && mouseY < y + 14) {
                showPolicyDropdown = !showPolicyDropdown;
                showCategoryDropdown = false;
                dropdownScrollOffset = 0;
                return true;
            }

            // Category dropdown item click
            if (showCategoryDropdown) {
                int dropY = y + 14 + 2;
                PolicyType.Category[] categories = PolicyType.Category.values();
                for (int i = 0; i < Math.min(categories.length, DROPDOWN_MAX_VISIBLE); i++) {
                    if (mouseX >= guiLeft + 12 && mouseX < guiLeft + 12 + dropdownWidth &&
                        mouseY >= dropY && mouseY < dropY + 12) {
                        selectedCategory = categories[i];
                        selectedPolicy = null;
                        showCategoryDropdown = false;
                        dropdownScrollOffset = 0;
                        return true;
                    }
                    dropY += 12;
                }
                // Clicked outside dropdown, close it
                showCategoryDropdown = false;
            }

            // Policy dropdown item click
            if (showPolicyDropdown && selectedCategory != null) {
                List<PolicyType> policies = getPoliciesForCategory(selectedCategory);
                int dropY = y + 14 + 2;
                int visibleCount = Math.min(policies.size() - dropdownScrollOffset, DROPDOWN_MAX_VISIBLE);

                for (int i = dropdownScrollOffset; i < dropdownScrollOffset + visibleCount; i++) {
                    if (mouseX >= policyDropdownX && mouseX < policyDropdownX + policyDropdownWidth &&
                        mouseY >= dropY && mouseY < dropY + 12) {
                        PolicyType policy = policies.get(i);
                        if (!policyChanges.containsKey(policy)) {
                            selectedPolicy = policy;
                            policyValueField.setValue("");
                            policyValueField.setFocused(true);
                        }
                        showPolicyDropdown = false;
                        return true;
                    }
                    dropY += 12;
                }
                // Clicked outside dropdown, close it
                showPolicyDropdown = false;
            }

            // Remove policy click (X button)
            if (!policyChanges.isEmpty()) {
                List<Map.Entry<PolicyType, String>> entries = new ArrayList<>(policyChanges.entrySet());
                int policiesY = guiTop + 125 + 8 + 14;
                int endIdx = Math.min(addedPoliciesScrollOffset + MAX_VISIBLE_ADDED, entries.size());

                for (int i = addedPoliciesScrollOffset; i < endIdx; i++) {
                    int removeX = guiLeft + guiWidth - 25;
                    if (mouseX >= removeX && mouseX < removeX + 12 && mouseY >= policiesY - 1 && mouseY < policiesY + 10) {
                        policyChanges.remove(entries.get(i).getKey());
                        return true;
                    }
                    policiesY += 12;
                }
            }

            // Close dropdowns if clicking elsewhere
            if (showCategoryDropdown || showPolicyDropdown) {
                showCategoryDropdown = false;
                showPolicyDropdown = false;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Scroll policy dropdown
        if (showPolicyDropdown && selectedCategory != null) {
            List<PolicyType> policies = getPoliciesForCategory(selectedCategory);
            int maxScroll = Math.max(0, policies.size() - DROPDOWN_MAX_VISIBLE);
            if (delta > 0) {
                dropdownScrollOffset = Math.max(0, dropdownScrollOffset - 1);
            } else {
                dropdownScrollOffset = Math.min(maxScroll, dropdownScrollOffset + 1);
            }
            return true;
        }

        // Scroll added policies list
        int policiesY = guiTop + 125;
        if (mouseY >= policiesY && mouseY < guiTop + guiHeight - 30) {
            int maxScroll = Math.max(0, policyChanges.size() - MAX_VISIBLE_ADDED);
            if (delta > 0) {
                addedPoliciesScrollOffset = Math.max(0, addedPoliciesScrollOffset - 1);
            } else {
                addedPoliciesScrollOffset = Math.min(maxScroll, addedPoliciesScrollOffset + 1);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void addSelectedPolicy() {
        if (selectedPolicy == null) {
            showError("Select a policy first");
            return;
        }

        String value;

        // Handle different value types
        if (selectedPolicy.getValueType() == PolicyType.ValueType.BOOLEAN) {
            // Use the toggle state
            value = String.valueOf(booleanToggleValue);
        } else if (selectedPolicy.getValueType() == PolicyType.ValueType.TEXT) {
            // Use the stored text value (set by the text editor)
            value = textPolicyValue.trim();
            if (value.isEmpty()) {
                showError("Enter text using the Edit button");
                return;
            }
        } else {
            value = policyValueField.getValue().trim();
            if (value.isEmpty()) {
                showError("Enter a value");
                return;
            }
        }

        // Convert percentage input (e.g., "25" -> "0.25")
        if (selectedPolicy.getValueType() == PolicyType.ValueType.PERCENTAGE) {
            try {
                double pct = Double.parseDouble(value.replace("%", ""));
                if (pct > 1) {
                    value = String.valueOf(pct / 100.0);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (!selectedPolicy.isValidValue(value)) {
            showError("Invalid value for " + selectedPolicy.getDisplayName());
            return;
        }

        policyChanges.put(selectedPolicy, value);
        selectedPolicy = null;
        policyValueField.setValue("");
        textPolicyValue = "";
        booleanToggleValue = false;
    }

    private void submitBill() {
        String title = titleField.getValue().trim();
        String description = descriptionField.getValue().trim();

        if (title.isEmpty()) {
            showError("Enter a bill title");
            return;
        }

        if (title.length() < 5) {
            showError("Title must be at least 5 characters");
            return;
        }

        if (policyChanges.isEmpty()) {
            showError("Add at least one policy change");
            return;
        }

        // Convert to string map for packet
        Map<String, String> policyMap = new HashMap<>();
        for (Map.Entry<PolicyType, String> entry : policyChanges.entrySet()) {
            policyMap.put(entry.getKey().name(), entry.getValue());
        }

        NetworkHandler.sendToServer(new ProposeBillPacket(nationName, title, description, policyMap));

        // Return to legislature screen
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§aBill submitted for debate!"));
        }
        goBack();
    }

    private void clearForm() {
        titleField.setValue("");
        descriptionField.setValue("");
        policyChanges.clear();
        selectedCategory = null;
        selectedPolicy = null;
        policyValueField.setValue("");
        booleanToggleValue = false;
        textPolicyValue = "";
        errorMessage = null;
        addedPoliciesScrollOffset = 0;
    }

    private void showError(String message) {
        this.errorMessage = message;
        this.errorTicks = 60; // 3 seconds
    }

    @Override
    public void tick() {
        super.tick();
        if (errorTicks > 0) {
            errorTicks--;
        }
    }

    private void goBack() {
        this.minecraft.setScreen(new LegislatureScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }
}

