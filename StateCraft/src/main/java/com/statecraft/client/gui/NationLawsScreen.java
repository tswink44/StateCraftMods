package com.statecraft.client.gui;

import com.statecraft.network.NetworkHandler;
import com.statecraft.network.packets.RequestNationLawsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Screen showing current laws and policies of a nation
 * Has two tabs: Policies (live values) and Enacted Laws (codex)
 * Accessible by all citizens to see the nation's rules
 */
public class NationLawsScreen extends StateCraftScreen {

    private final String nationName;
    private boolean dataLoaded = false;
    private int scrollOffset = 0;
    private static final int VISIBLE_ROWS = 10;
    private static final int ROW_HEIGHT = 18;

    // Tab state
    private Tab currentTab = Tab.POLICIES;

    private enum Tab {
        POLICIES("Policies"),
        ENACTED("Enacted Laws");

        final String displayName;
        Tab(String displayName) { this.displayName = displayName; }
    }

    // Policy data from server
    private List<PolicyEntry> policies = new ArrayList<>();

    // Enacted laws from codex
    private List<EnactedLawEntry> enactedLaws = new ArrayList<>();

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

        // Tab buttons
        int tabY = guiTop + 25;
        int tabWidth = 100;
        int tabSpacing = 5;
        int startX = guiLeft + 15;

        this.addRenderableWidget(createButton(
            startX, tabY, tabWidth, 16,
            Component.literal("Policies"),
            btn -> switchTab(Tab.POLICIES)
        ));

        this.addRenderableWidget(createButton(
            startX + tabWidth + tabSpacing, tabY, tabWidth, 16,
            Component.literal("Enacted Laws"),
            btn -> switchTab(Tab.ENACTED)
        ));

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 28,
            80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    private void switchTab(Tab tab) {
        this.currentTab = tab;
        this.scrollOffset = 0;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderDivider(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20);

        int contentX = guiLeft + 15;
        int contentY = guiTop + 48;

        if (!dataLoaded) {
            graphics.drawString(this.font, "§7Loading laws...", contentX, contentY, COLOR_TEXT);
            return;
        }

        // Draw tab indicator
        renderDivider(graphics, guiLeft + 10, contentY - 3, guiWidth - 20);

        switch (currentTab) {
            case POLICIES -> renderPoliciesTab(graphics, contentX, contentY, mouseX, mouseY);
            case ENACTED -> renderEnactedLawsTab(graphics, contentX, contentY, mouseX, mouseY);
        }
    }

    private void renderPoliciesTab(GuiGraphics graphics, int contentX, int contentY, int mouseX, int mouseY) {
        int y = contentY;

        if (policies.isEmpty()) {
            graphics.drawString(this.font, "§7No policies have been set for this nation.", contentX, y, COLOR_TEXT);
            return;
        }

        // Draw section headers and policies
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
            int totalRows = countTotalPolicyRows();
            String scrollText = String.format("§8(%d/%d)", scrollOffset + 1, Math.max(1, totalRows - VISIBLE_ROWS + 1));
            graphics.drawString(this.font, scrollText, guiLeft + guiWidth - 60, guiTop + guiHeight - 45, 0xFF888888);
        }
    }

    private void renderEnactedLawsTab(GuiGraphics graphics, int contentX, int contentY, int mouseX, int mouseY) {
        int y = contentY;

        graphics.drawString(this.font, "§6Enacted Laws §7(" + enactedLaws.size() + ") §8— click to view details",
            contentX, y, COLOR_PRIMARY);
        y += 14;

        if (enactedLaws.isEmpty()) {
            graphics.drawString(this.font, "§8No laws have been enacted yet.", contentX, y + 10, 0xFFAAAAAA);
            return;
        }

        int endIndex = Math.min(scrollOffset + VISIBLE_ROWS, enactedLaws.size());
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");

        for (int i = scrollOffset; i < endIndex; i++) {
            EnactedLawEntry law = enactedLaws.get(i);

            int entryTop = y;
            int entryBottom = y + ROW_HEIGHT;

            // Hover detection
            boolean isHovered = mouseX >= contentX && mouseX < guiLeft + guiWidth - 20 &&
                                mouseY >= entryTop && mouseY < entryBottom;

            // Draw highlight if hovered
            if (isHovered) {
                graphics.fill(contentX - 3, entryTop - 1, guiLeft + guiWidth - 15, entryBottom - 1, 0x33FFFFFF);
            }

            // Status indicator
            String statusPrefix = law.isRepealed ? "§8§m" : "§f";
            String lawType = law.lawNumber.startsWith("EO") ? "§c" : "§b";

            // Law number and title
            String lawLabel = lawType + law.lawNumber + " §7— " + statusPrefix + law.title;
            graphics.drawString(this.font, lawLabel, contentX, y, COLOR_TEXT);

            // Date and author on the right
            String dateStr = sdf.format(new Date(law.enactedTime));
            String metaStr = "§8" + dateStr;
            int metaWidth = this.font.width(metaStr.replaceAll("§.", ""));
            graphics.drawString(this.font, metaStr, guiLeft + guiWidth - metaWidth - 20, y, 0xFF888888);

            y += ROW_HEIGHT;
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(this.font, "§7▲", guiLeft + guiWidth - 15, contentY + 5, 0xFF888888);
        }
        if (scrollOffset + VISIBLE_ROWS < enactedLaws.size()) {
            graphics.drawCenteredString(this.font, "§7▼", guiLeft + guiWidth - 15, guiTop + guiHeight - 50, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && currentTab == Tab.ENACTED && !enactedLaws.isEmpty()) {
            int contentX = guiLeft + 15;
            int y = guiTop + 48 + 14; // After header

            int endIndex = Math.min(scrollOffset + VISIBLE_ROWS, enactedLaws.size());
            for (int i = scrollOffset; i < endIndex; i++) {
                int entryTop = y;
                int entryBottom = y + ROW_HEIGHT;

                if (mouseX >= contentX && mouseX < guiLeft + guiWidth - 20 &&
                    mouseY >= entryTop && mouseY < entryBottom) {

                    EnactedLawEntry law = enactedLaws.get(i);
                    openLawDetail(law);
                    return true;
                }

                y += ROW_HEIGHT;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openLawDetail(EnactedLawEntry law) {
        LawDetailScreen.LawInfo lawInfo = new LawDetailScreen.LawInfo(
            law.lawNumber,
            law.title,
            law.description,
            law.authorName,
            law.enactedTime,
            law.yesVotes,
            law.noVotes,
            law.abstainVotes,
            law.wasVetoProof,
            law.isConstitutionalAmendment,
            law.policyChanges,
            law.fullText
        );

        this.minecraft.setScreen(new LawDetailScreen(nationName, lawInfo, () -> {
            this.minecraft.setScreen(this);
        }));
    }

    private int countTotalPolicyRows() {
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
        int maxScroll;
        if (currentTab == Tab.POLICIES) {
            maxScroll = Math.max(0, countTotalPolicyRows() - VISIBLE_ROWS);
        } else {
            maxScroll = Math.max(0, enactedLaws.size() - VISIBLE_ROWS);
        }
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
    public void updatePolicies(List<PolicyEntry> policies, List<EnactedLawEntry> enactedLaws) {
        this.policies = policies;
        this.enactedLaws = enactedLaws != null ? enactedLaws : new ArrayList<>();
        this.dataLoaded = true;
        this.scrollOffset = 0;
    }

    // Backward-compatible overload
    public void updatePolicies(List<PolicyEntry> policies) {
        updatePolicies(policies, new ArrayList<>());
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

    /**
     * Data class for enacted law entries from the codex
     */
    public static class EnactedLawEntry {
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
        public final boolean isRepealed;
        public final Map<String, String> policyChanges;
        public final String fullText;

        public EnactedLawEntry(String lawNumber, String title, String description, String authorName,
                               long enactedTime, int yesVotes, int noVotes, int abstainVotes,
                               boolean wasVetoProof, boolean isConstitutionalAmendment, boolean isRepealed,
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
            this.isRepealed = isRepealed;
            this.policyChanges = policyChanges != null ? policyChanges : new HashMap<>();
            this.fullText = fullText != null ? fullText : "";
        }
    }
}

