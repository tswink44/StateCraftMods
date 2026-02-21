package com.statecraft.client.gui;

import com.statecraft.legislature.PolicyType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Screen for viewing details of an enacted law
 * Shows policy changes, voting record, and full text for custom laws
 */
public class LawDetailScreen extends StateCraftScreen {

    private final String nationName;
    private final LawInfo lawInfo;
    private final Runnable onBack;

    private int scrollOffset = 0;
    private int contentHeight = 0;
    private static final int SCROLL_AREA_HEIGHT = 160;
    private static final int LINE_HEIGHT = 12;

    public LawDetailScreen(String nationName, LawInfo lawInfo, Runnable onBack) {
        super(Component.literal(lawInfo.lawNumber));
        this.nationName = nationName;
        this.lawInfo = lawInfo;
        this.onBack = onBack;
        this.guiWidth = 320;
        this.guiHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int scrollAreaTop = guiTop + 28;
        int scrollAreaBottom = scrollAreaTop + SCROLL_AREA_HEIGHT;
        int leftCol = guiLeft + 15;

        // Enable scissoring to clip content to scroll area
        graphics.enableScissor(guiLeft + 5, scrollAreaTop, guiLeft + guiWidth - 5, scrollAreaBottom);

        int y = scrollAreaTop - (scrollOffset * LINE_HEIGHT);

        // Title
        graphics.drawString(this.font, "§6" + lawInfo.title, leftCol, y, COLOR_PRIMARY);
        y += 14;

        // Author and date
        graphics.drawString(this.font, "§7Author: §f" + lawInfo.authorName, leftCol, y, COLOR_TEXT);
        y += LINE_HEIGHT;

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");
        String dateStr = sdf.format(new Date(lawInfo.enactedTime));
        graphics.drawString(this.font, "§7Enacted: §f" + dateStr, leftCol, y, COLOR_TEXT);
        y += LINE_HEIGHT;

        // Type
        String typeStr = lawInfo.isConstitutionalAmendment ? "§dConstitutional Amendment" : "§bNation Law";
        graphics.drawString(this.font, "§7Type: " + typeStr, leftCol, y, COLOR_TEXT);
        y += 16;

        // Voting record
        renderDivider(graphics, guiLeft + 10, y - 2, guiWidth - 20);
        graphics.drawString(this.font, "§6Voting Record", leftCol, y, COLOR_PRIMARY);
        y += 14;

        graphics.drawString(this.font, "§aYes: §f" + lawInfo.yesVotes +
            "  §cNo: §f" + lawInfo.noVotes +
            "  §7Abstain: §f" + lawInfo.abstainVotes, leftCol, y, COLOR_TEXT);
        y += LINE_HEIGHT;

        if (lawInfo.wasVetoProof) {
            graphics.drawString(this.font, "§e★ Passed with veto-proof majority", leftCol, y, 0xFFFFAA00);
            y += LINE_HEIGHT;
        }
        y += 8;

        // Policy changes (non-custom laws)
        boolean hasRegularPolicies = false;
        boolean hasCustomLaws = false;
        for (Map.Entry<String, String> entry : lawInfo.policyChanges.entrySet()) {
            String policyName = entry.getKey();
            if (isCustomLawPolicy(policyName)) {
                hasCustomLaws = true;
            } else {
                hasRegularPolicies = true;
            }
        }

        if (hasRegularPolicies) {
            renderDivider(graphics, guiLeft + 10, y - 2, guiWidth - 20);
            graphics.drawString(this.font, "§6Policy Changes", leftCol, y, COLOR_PRIMARY);
            y += 14;

            for (Map.Entry<String, String> entry : lawInfo.policyChanges.entrySet()) {
                String policyName = entry.getKey();
                if (isCustomLawPolicy(policyName)) continue; // skip custom laws here
                String value = entry.getValue();
                String displayValue = formatPolicyValue(policyName, value);
                graphics.drawString(this.font, "§7• §f" + policyName + ": §a" + displayValue, leftCol, y, COLOR_TEXT);
                y += LINE_HEIGHT;
            }
            y += 8;
        }

        // Custom Laws section - rendered as readable text blocks
        if (hasCustomLaws) {
            renderDivider(graphics, guiLeft + 10, y - 2, guiWidth - 20);
            graphics.drawString(this.font, "§6Custom Laws", leftCol, y, COLOR_PRIMARY);
            y += 14;

            int lawNumber = 0;
            for (Map.Entry<String, String> entry : lawInfo.policyChanges.entrySet()) {
                String policyName = entry.getKey();
                if (!isCustomLawPolicy(policyName)) continue;
                lawNumber++;

                String lawText = entry.getValue();

                // Sub-panel background for each custom law
                List<String> wrappedLaw = wrapText(lawText, guiWidth - 50);
                int blockHeight = wrappedLaw.size() * LINE_HEIGHT + 8;

                graphics.fill(guiLeft + 12, y - 2, guiLeft + guiWidth - 12, y + blockHeight, 0x44000000);
                graphics.fill(guiLeft + 12, y - 2, guiLeft + 14, y + blockHeight, 0xFF4A90D9); // accent bar

                // Law label
                String label = "§e§l" + getCustomLawLabel(policyName);
                graphics.drawString(this.font, label, leftCol + 5, y, 0xFFFFAA00);
                y += LINE_HEIGHT + 2;

                // Law text wrapped
                for (String line : wrappedLaw) {
                    graphics.drawString(this.font, "§f" + line, leftCol + 5, y, 0xFFDDDDDD);
                    y += LINE_HEIGHT;
                }
                y += 8;
            }
        }

        // Full text (for custom laws)
        if (lawInfo.fullText != null && !lawInfo.fullText.isEmpty()) {
            renderDivider(graphics, guiLeft + 10, y - 2, guiWidth - 20);
            graphics.drawString(this.font, "§6Full Text", leftCol, y, COLOR_PRIMARY);
            y += 14;

            // Word wrap the full text
            List<String> wrappedLines = wrapText(lawInfo.fullText, guiWidth - 40);
            for (String line : wrappedLines) {
                graphics.drawString(this.font, "§7" + line, leftCol, y, 0xFFCCCCCC);
                y += LINE_HEIGHT;
            }
        }

        // Description
        if (lawInfo.description != null && !lawInfo.description.isEmpty()) {
            y += 4;
            renderDivider(graphics, guiLeft + 10, y - 2, guiWidth - 20);
            graphics.drawString(this.font, "§6Description", leftCol, y, COLOR_PRIMARY);
            y += 14;

            List<String> wrappedDesc = wrapText(lawInfo.description, guiWidth - 40);
            for (String line : wrappedDesc) {
                graphics.drawString(this.font, "§7" + line, leftCol, y, 0xFFAAAAAA);
                y += LINE_HEIGHT;
            }
        }

        // Calculate content height for scroll bounds
        contentHeight = y - (scrollAreaTop - (scrollOffset * LINE_HEIGHT));

        graphics.disableScissor();

        // Scroll indicators
        int maxScroll = getMaxScroll();
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 15, scrollAreaTop + 2, 0xFF888888);
        }
        if (scrollOffset < maxScroll) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 15, scrollAreaBottom - 10, 0xFF888888);
        }
    }

    private int getMaxScroll() {
        return Math.max(0, (contentHeight - SCROLL_AREA_HEIGHT) / LINE_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = getMaxScroll();
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            return true;
        } else if (delta < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }

            StringBuilder currentLine = new StringBuilder();
            String[] words = paragraph.split(" ");
            for (String word : words) {
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                if (this.font.width(testLine) > maxWidth) {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        lines.add(word);
                    }
                } else {
                    currentLine = new StringBuilder(testLine);
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }

    private String formatPolicyValue(String policyName, String value) {
        try {
            // Try to find the policy type to format correctly
            for (PolicyType pt : PolicyType.values()) {
                if (pt.getDisplayName().equals(policyName) || pt.name().equals(policyName)) {
                    return switch (pt.getValueType()) {
                        case BOOLEAN -> value.equalsIgnoreCase("true") ? "Yes" : "No";
                        case PERCENTAGE -> String.format("%.0f%%", Double.parseDouble(value) * 100);
                        case CURRENCY -> "$" + value;
                        case INTEGER -> value;
                        default -> value;
                    };
                }
            }
        } catch (Exception ignored) {}
        return value;
    }

    private boolean isCustomLawPolicy(String policyName) {
        for (PolicyType pt : PolicyType.values()) {
            if ((pt.getDisplayName().equals(policyName) || pt.name().equals(policyName))
                && pt.getCategory() == PolicyType.Category.CUSTOM) {
                return true;
            }
        }
        return false;
    }

    private String getCustomLawLabel(String policyName) {
        for (PolicyType pt : PolicyType.values()) {
            if (pt.getDisplayName().equals(policyName) || pt.name().equals(policyName)) {
                return pt.getDisplayName();
            }
        }
        return policyName;
    }

    private void goBack() {
        onBack.run();
    }

    @Override
    public void onClose() {
        goBack();
    }

    /**
     * Data class for law information
     */
    public static class LawInfo {
        public final String lawNumber;
        public final String title;
        public final String description;
        public final String authorName;
        public final long enactedTime;
        public final int yesVotes;
        public final int noVotes;
        public final int abstainVotes;
        public final boolean wasVetoProof;
        public final boolean isConstitutionalAmendment;
        public final Map<String, String> policyChanges;
        public final String fullText;

        public LawInfo(String lawNumber, String title, String description, String authorName,
                       long enactedTime, int yesVotes, int noVotes, int abstainVotes,
                       boolean wasVetoProof, boolean isConstitutionalAmendment,
                       Map<String, String> policyChanges, String fullText) {
            this.lawNumber = lawNumber;
            this.title = title;
            this.description = description;
            this.authorName = authorName;
            this.enactedTime = enactedTime;
            this.yesVotes = yesVotes;
            this.noVotes = noVotes;
            this.abstainVotes = abstainVotes;
            this.wasVetoProof = wasVetoProof;
            this.isConstitutionalAmendment = isConstitutionalAmendment;
            this.policyChanges = policyChanges != null ? policyChanges : new HashMap<>();
            this.fullText = fullText != null ? fullText : "";
        }
    }
}

