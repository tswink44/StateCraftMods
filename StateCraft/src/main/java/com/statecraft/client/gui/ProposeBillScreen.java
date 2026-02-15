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
 * Allows selecting policy changes and writing bill title/description
 */
public class ProposeBillScreen extends StateCraftScreen {

    private final String nationName;

    // Input fields
    private EditBox titleField;
    private EditBox descriptionField;

    // Policy selection
    private PolicyType.Category selectedCategory = PolicyType.Category.TAXATION;
    private PolicyType selectedPolicy = null;
    private EditBox policyValueField;

    // Current bill policy changes
    private final Map<PolicyType, String> policyChanges = new HashMap<>();

    // Scrolling for policy list
    private int policyScrollOffset = 0;
    private static final int MAX_VISIBLE_POLICIES = 5;

    // Error message
    private String errorMessage = null;
    private int errorTicks = 0;

    public ProposeBillScreen(String nationName) {
        super(Component.literal("Propose Legislation"));
        this.nationName = nationName;
        this.guiWidth = 340;
        this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        int fieldX = guiLeft + 80;
        int fieldWidth = guiWidth - 95;
        int y = guiTop + 28;

        // Title field
        this.titleField = new EditBox(this.font, fieldX, y, fieldWidth, 16, Component.literal("Title"));
        this.titleField.setMaxLength(64);
        this.titleField.setHint(Component.literal("Bill title..."));
        this.addRenderableWidget(this.titleField);

        // Description field (multiline would be nice but EditBox is single line)
        y += 22;
        this.descriptionField = new EditBox(this.font, fieldX, y, fieldWidth, 16, Component.literal("Description"));
        this.descriptionField.setMaxLength(256);
        this.descriptionField.setHint(Component.literal("Brief description..."));
        this.addRenderableWidget(this.descriptionField);

        // Category buttons
        y += 28;
        int catBtnWidth = 50;
        int catX = guiLeft + 10;

        for (PolicyType.Category category : PolicyType.Category.values()) {
            final PolicyType.Category cat = category;
            String label = category.getDisplayName();
            if (label.length() > 6) label = label.substring(0, 6);

            Button catBtn = this.addRenderableWidget(createButton(
                catX, y, catBtnWidth, 14,
                Component.literal(label),
                btn -> selectCategory(cat)
            ));
            catX += catBtnWidth + 2;
            if (catX + catBtnWidth > guiLeft + guiWidth - 10) {
                catX = guiLeft + 10;
                y += 16;
            }
        }

        // Policy value field (appears when a policy is selected)
        this.policyValueField = new EditBox(this.font, guiLeft + guiWidth - 80, guiTop + guiHeight - 95, 65, 14, Component.literal("Value"));
        this.policyValueField.setMaxLength(32);
        this.policyValueField.setVisible(false);
        this.addRenderableWidget(this.policyValueField);

        // Add Policy button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 85, guiTop + guiHeight - 76, 70, 16,
            Component.literal("§aAdd Policy"),
            btn -> addSelectedPolicy()
        ));

        // Bottom buttons
        int buttonY = guiTop + guiHeight - 28;

        // Submit button
        this.addRenderableWidget(createButton(
            guiLeft + 15, buttonY, 80, 20,
            Component.literal("§aSubmit Bill"),
            btn -> submitBill()
        ));

        // Clear button
        this.addRenderableWidget(createButton(
            guiLeft + 100, buttonY, 60, 20,
            Component.literal("Clear"),
            btn -> clearForm()
        ));

        // Cancel button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 75, buttonY, 60, 20,
            Component.literal("Cancel"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = guiTop + 28;

        // Labels
        graphics.drawString(this.font, "§7Title:", guiLeft + 15, y + 4, COLOR_TEXT);
        y += 22;
        graphics.drawString(this.font, "§7Description:", guiLeft + 15, y + 4, COLOR_TEXT);
        y += 28;

        // Divider
        renderDivider(graphics, guiLeft + 10, y - 5, guiWidth - 20);

        // Category indicator
        y += 18; // Skip category buttons row
        if (selectedCategory != null) {
            y += 4;
            graphics.drawString(this.font, "§6" + selectedCategory.getDisplayName() + " Policies:",
                guiLeft + 15, y, COLOR_PRIMARY);
        }
        y += 14;

        // Policy list for selected category
        renderPolicyList(graphics, guiLeft + 15, y, mouseX, mouseY);

        // Selected policies summary
        int summaryY = guiTop + guiHeight - 115;
        renderDivider(graphics, guiLeft + 10, summaryY - 3, guiWidth - 20);

        graphics.drawString(this.font, "§6Bill Policies §7(" + policyChanges.size() + "):",
            guiLeft + 15, summaryY, COLOR_PRIMARY);
        summaryY += 12;

        if (policyChanges.isEmpty()) {
            graphics.drawString(this.font, "§8No policies added yet", guiLeft + 20, summaryY, 0xFFAAAAAA);
        } else {
            int count = 0;
            for (Map.Entry<PolicyType, String> entry : policyChanges.entrySet()) {
                if (count >= 2) {
                    graphics.drawString(this.font, "§8..." + (policyChanges.size() - 2) + " more",
                        guiLeft + 20, summaryY, 0xFFAAAAAA);
                    break;
                }
                String display = "§7• " + entry.getKey().getDisplayName() + ": §f" + formatValue(entry.getKey(), entry.getValue());
                if (this.font.width(display.replaceAll("§.", "")) > guiWidth - 40) {
                    display = this.font.plainSubstrByWidth(display.replaceAll("§.", ""), guiWidth - 50) + "...";
                }
                graphics.drawString(this.font, display, guiLeft + 20, summaryY, COLOR_TEXT);
                summaryY += 10;
                count++;
            }
        }

        // Error message
        if (errorMessage != null && errorTicks > 0) {
            graphics.drawCenteredString(this.font, "§c" + errorMessage,
                this.width / 2, guiTop + guiHeight - 45, COLOR_WARNING);
        }
    }

    private void renderPolicyList(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        List<PolicyType> policies = getPoliciesForCategory(selectedCategory);

        if (policies.isEmpty()) {
            graphics.drawString(this.font, "§8No policies in this category", x, y, 0xFFAAAAAA);
            return;
        }

        int listHeight = MAX_VISIBLE_POLICIES * 14;
        int endIndex = Math.min(policyScrollOffset + MAX_VISIBLE_POLICIES, policies.size());

        for (int i = policyScrollOffset; i < endIndex; i++) {
            PolicyType policy = policies.get(i);
            boolean isSelected = policy == selectedPolicy;
            boolean isHovered = mouseX >= x && mouseX < x + 180 && mouseY >= y && mouseY < y + 12;
            boolean alreadyAdded = policyChanges.containsKey(policy);

            // Background for selection/hover
            if (isSelected) {
                graphics.fill(x - 2, y - 1, x + 180, y + 11, 0x44FFFFFF);
            } else if (isHovered && !alreadyAdded) {
                graphics.fill(x - 2, y - 1, x + 180, y + 11, 0x22FFFFFF);
            }

            String color = alreadyAdded ? "§8" : (isSelected ? "§e" : "§f");
            String checkmark = alreadyAdded ? "§a✓ " : "  ";
            graphics.drawString(this.font, checkmark + color + policy.getDisplayName(), x, y, COLOR_TEXT);

            y += 14;
        }

        // Scroll indicators
        if (policyScrollOffset > 0) {
            graphics.drawString(this.font, "§7▲", x + 185, guiTop + 115, 0xFF888888);
        }
        if (policyScrollOffset + MAX_VISIBLE_POLICIES < policies.size()) {
            graphics.drawString(this.font, "§7▼", x + 185, guiTop + 115 + listHeight - 14, 0xFF888888);
        }

        // Show value input hint when policy is selected
        if (selectedPolicy != null) {
            policyValueField.setVisible(true);
            String hint = getValueHint(selectedPolicy);
            graphics.drawString(this.font, "§7Value: " + hint, guiLeft + guiWidth - 150, guiTop + guiHeight - 92, 0xFFAAAAAA);
        } else {
            policyValueField.setVisible(false);
        }
    }

    private List<PolicyType> getPoliciesForCategory(PolicyType.Category category) {
        List<PolicyType> result = new ArrayList<>();
        for (PolicyType policy : PolicyType.values()) {
            if (policy.getCategory() == category) {
                result.add(policy);
            }
        }
        return result;
    }

    private String getValueHint(PolicyType policy) {
        return switch (policy.getValueType()) {
            case BOOLEAN -> "(true/false)";
            case INTEGER -> "(" + (int)policy.getMinValue() + "-" + (int)policy.getMaxValue() + ")";
            case PERCENTAGE -> "(0-" + (int)(policy.getMaxValue() * 100) + "%)";
            case CURRENCY -> "($)";
            case TEXT -> "(text)";
            case NATION_TARGET -> "(nation name)";
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
            // Check if clicking on a policy in the list
            int listX = guiLeft + 15;
            int listY = guiTop + 115;
            List<PolicyType> policies = getPoliciesForCategory(selectedCategory);

            int endIndex = Math.min(policyScrollOffset + MAX_VISIBLE_POLICIES, policies.size());
            for (int i = policyScrollOffset; i < endIndex; i++) {
                if (mouseX >= listX && mouseX < listX + 180 && mouseY >= listY && mouseY < listY + 12) {
                    PolicyType policy = policies.get(i);
                    if (!policyChanges.containsKey(policy)) {
                        selectedPolicy = policy;
                        policyValueField.setValue("");
                        policyValueField.setFocused(true);
                    }
                    return true;
                }
                listY += 14;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<PolicyType> policies = getPoliciesForCategory(selectedCategory);
        int maxScroll = Math.max(0, policies.size() - MAX_VISIBLE_POLICIES);

        if (delta > 0) {
            policyScrollOffset = Math.max(0, policyScrollOffset - 1);
        } else {
            policyScrollOffset = Math.min(maxScroll, policyScrollOffset + 1);
        }
        return true;
    }

    private void selectCategory(PolicyType.Category category) {
        this.selectedCategory = category;
        this.selectedPolicy = null;
        this.policyScrollOffset = 0;
        this.policyValueField.setValue("");
    }

    private void addSelectedPolicy() {
        if (selectedPolicy == null) {
            showError("Select a policy first");
            return;
        }

        String value = policyValueField.getValue().trim();
        if (value.isEmpty()) {
            showError("Enter a value");
            return;
        }

        // Convert percentage input (e.g., "25" -> "0.25")
        if (selectedPolicy.getValueType() == PolicyType.ValueType.PERCENTAGE) {
            try {
                double pct = Double.parseDouble(value);
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
        policyValueField.setVisible(false);
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
        selectedPolicy = null;
        policyValueField.setValue("");
        errorMessage = null;
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

