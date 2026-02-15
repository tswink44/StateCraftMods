package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationLawsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen showing current laws and policies of a nation
 * Accessible by all citizens to see the nation's rules
 */
public class NationLawsScreen extends StateCraftScreen {

    private final String nationName;
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 10;
    private static final int ROW_HEIGHT = 18;

    // Policy data from server
    private List<PolicyEntry> policies = new ArrayList<>();

    public NationLawsScreen(String nationName) {
        super(Component.literal("Nation Laws: " + nationName));
        this.nationName = nationName;
        this.guiWidth = 320;
        this.guiHeight = 260;
    }

    @Override
    protected void init() {
        super.init();

        // Request law data from server
        NetworkHandler.sendToServer(new RequestNationLawsPacket(nationName));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));

        // Scroll buttons
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 25, guiTop + 45,
            20, 20,
            Component.literal("▲"),
            btn -> scroll(-3)
        ));

        this.addRenderableWidget(createButton(
            guiLeft + guiWidth - 25, guiTop + guiHeight - 55,
            20, 20,
            Component.literal("▼"),
            btn -> scroll(3)
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int contentX = guiLeft + 15;
        int contentY = guiTop + 35;

        if (!dataLoaded) {
            graphics.drawString(this.font, "§7Loading laws...", contentX, contentY, COLOR_TEXT);
            return;
        }

        if (policies.isEmpty()) {
            graphics.drawString(this.font, "§7No policies have been set for this nation.", contentX, contentY, COLOR_TEXT);
            return;
        }

        // Draw section headers and policies
        int y = contentY;
        String currentCategory = "";
        int visibleCount = 0;
        int skipped = 0;

        for (PolicyEntry policy : policies) {
            if (skipped < scrollOffset) {
                skipped++;
                continue;
            }

            if (visibleCount >= VISIBLE_ROWS) break;

            // Draw category header if changed
            if (!policy.category.equals(currentCategory)) {
                if (visibleCount > 0) {
                    y += 5; // Extra spacing before new category
                }
                graphics.drawString(this.font, "§6" + policy.category + ":", contentX, y, 0xFFFFAA00);
                y += ROW_HEIGHT;
                visibleCount++;
                currentCategory = policy.category;

                if (visibleCount >= VISIBLE_ROWS) break;
            }

            // Draw policy name and value
            String displayValue = formatPolicyValue(policy);
            graphics.drawString(this.font, "§7  " + policy.name + ":", contentX, y, COLOR_TEXT);
            graphics.drawString(this.font, displayValue, contentX + 160, y, 0xFFFFFFFF);
            y += ROW_HEIGHT;
            visibleCount++;
        }

        // Draw scroll indicator
        if (policies.size() > VISIBLE_ROWS) {
            int totalRows = countTotalRows();
            String scrollText = String.format("§8(%d/%d)", scrollOffset + 1, Math.max(1, totalRows - VISIBLE_ROWS + 1));
            graphics.drawString(this.font, scrollText, guiLeft + guiWidth - 60, guiTop + guiHeight - 45, 0xFF888888);
        }
    }

    private int countTotalRows() {
        int count = 0;
        String currentCategory = "";
        for (PolicyEntry policy : policies) {
            if (!policy.category.equals(currentCategory)) {
                count++; // Category header
                currentCategory = policy.category;
            }
            count++; // Policy row
        }
        return count;
    }

    private String formatPolicyValue(PolicyEntry policy) {
        switch (policy.valueType) {
            case "PERCENTAGE":
                return String.format("§a%.1f%%", policy.numericValue * 100);
            case "CURRENCY":
                return String.format("§a$%.2f", policy.numericValue);
            case "INTEGER":
                return String.format("§a%d", (int) policy.numericValue);
            case "BOOLEAN":
                return policy.numericValue > 0 ? "§aYes" : "§cNo";
            case "TEXT":
                return "§f" + (policy.textValue.isEmpty() ? "§8(not set)" : policy.textValue);
            default:
                return "§f" + policy.textValue;
        }
    }

    private void scroll(int amount) {
        int maxScroll = Math.max(0, countTotalRows() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + amount));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll(delta > 0 ? -2 : 2);
        return true;
    }

    private void goBack() {
        this.minecraft.setScreen(new NationInfoScreen(nationName));
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Called by client packet handler to update policy data
     */
    public void updatePolicies(List<PolicyEntry> policies) {
        this.policies = policies;
        this.dataLoaded = true;
        this.scrollOffset = 0;
    }

    /**
     * Data class for policy entries
     */
    public static class PolicyEntry {
        public final String category;
        public final String name;
        public final String valueType;
        public final double numericValue;
        public final String textValue;

        public PolicyEntry(String category, String name, String valueType, double numericValue, String textValue) {
            this.category = category;
            this.name = name;
            this.valueType = valueType;
            this.numericValue = numericValue;
            this.textValue = textValue != null ? textValue : "";
        }
    }
}

