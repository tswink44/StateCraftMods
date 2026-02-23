package com.statecraft.economy.client.screen;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.RequestTaxReportPacket;
import com.statecraft.economy.network.packets.SyncTaxReportPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tax Report Screen - displays comprehensive property tax information for the player.
 * Shows breakdown by jurisdiction (nation/state/city) with valuation details.
 */
public class TaxReportScreen extends Screen {

    // Colors (matching StateCraft Economy style)
    private static final int COLOR_PRIMARY = 0xFF4A90D9;
    private static final int COLOR_SECONDARY = 0xFF2ECC71;
    private static final int COLOR_WARNING = 0xFFE74C3C;
    private static final int COLOR_GOLD = 0xFFFFAA00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_PANEL = 0xDD1A1A2E;
    private static final int COLOR_BORDER = 0xFF3D3D5C;
    private static final int COLOR_ROW_EVEN = 0x40404060;
    private static final int COLOR_ROW_ODD = 0x30303050;
    private static final int COLOR_ROW_HOVER = 0x60505080;
    private static final int COLOR_HEADER_BG = 0xAA303050;
    private static final int COLOR_NATION = 0xFFD984FF;
    private static final int COLOR_STATE = 0xFF5BC0DE;
    private static final int COLOR_CITY = 0xFF5CB85C;

    // Layout
    private int guiLeft;
    private int guiTop;
    private int guiWidth = 420;
    private int guiHeight = 280;

    // Scrolling
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private static final int ROW_HEIGHT = 14;
    private int contentAreaTop;
    private int contentAreaBottom;

    // Data from server
    private SyncTaxReportPacket.TaxReportData reportData;
    private boolean dataLoaded = false;
    private boolean dataRequested = false;

    // Expanded state for hierarchy
    private final List<String> expandedNations = new ArrayList<>();
    private final List<String> expandedStates = new ArrayList<>();
    private final List<String> expandedCities = new ArrayList<>();

    // Back navigation
    private final Screen parentScreen;

    public TaxReportScreen(Screen parent) {
        super(Component.literal("Property Tax Report"));
        this.parentScreen = parent;
    }

    public TaxReportScreen() {
        this(null);
    }

    @Override
    protected void init() {
        super.init();

        // Auto-size to fit screen
        this.guiWidth = Math.min(420, this.width - 40);
        this.guiHeight = Math.min(300, this.height - 40);

        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        // Content area
        contentAreaTop = guiTop + 85;
        contentAreaBottom = guiTop + guiHeight - 35;

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("< Back"),
            btn -> {
                if (parentScreen != null) {
                    Minecraft.getInstance().setScreen(parentScreen);
                } else {
                    onClose();
                }
            })
            .bounds(guiLeft + guiWidth / 2 - 50, guiTop + guiHeight - 28, 100, 20)
            .build());

        // Request data from server
        if (!dataRequested) {
            NetworkHandler.sendToServer(new RequestTaxReportPacket());
            dataRequested = true;
        }
    }

    /**
     * Called when server sends tax report data
     */
    public void setReportData(SyncTaxReportPacket.TaxReportData data) {
        this.reportData = data;
        this.dataLoaded = true;

        // Expand all by default for easier viewing
        if (data != null && data.nations() != null) {
            for (var nation : data.nations()) {
                expandedNations.add(nation.name());
                for (var state : nation.states()) {
                    expandedStates.add(nation.name() + ":" + state.name());
                }
            }
        }

        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        if (reportData == null || reportData.nations() == null) {
            maxScroll = 0;
            return;
        }

        int totalRows = 0;
        for (var nation : reportData.nations()) {
            totalRows++; // Nation header
            if (expandedNations.contains(nation.name())) {
                for (var state : nation.states()) {
                    totalRows++; // State header
                    String stateKey = nation.name() + ":" + state.name();
                    if (expandedStates.contains(stateKey)) {
                        for (var city : state.cities()) {
                            totalRows++; // City header
                            String cityKey = stateKey + ":" + city.name();
                            if (expandedCities.contains(cityKey)) {
                                totalRows += Math.min(city.chunks().size(), 10); // Chunks (max 10)
                                if (city.chunks().size() > 10) totalRows++; // "+N more" row
                            }
                        }
                    }
                }
            }
        }

        int visibleRows = (contentAreaBottom - contentAreaTop) / ROW_HEIGHT;
        maxScroll = Math.max(0, totalRows - visibleRows);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Panel background
        renderPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        // Title
        graphics.drawCenteredString(this.font, "§6§l╔═══ PROPERTY TAX REPORT ═══╗",
            this.width / 2, guiTop + 6, COLOR_GOLD);

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading tax data...",
                this.width / 2, guiTop + guiHeight / 2, COLOR_TEXT_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (reportData == null || reportData.totalChunks() == 0) {
            graphics.drawCenteredString(this.font, "§7You don't own any taxable property.",
                this.width / 2, guiTop + guiHeight / 2, COLOR_TEXT_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Summary section
        int summaryY = guiTop + 22;
        int col1 = guiLeft + 15;
        int col2 = guiLeft + guiWidth / 2 + 10;

        graphics.drawString(this.font, "§7Total Properties: §f" + reportData.totalChunks() + " chunks",
            col1, summaryY, COLOR_TEXT);
        graphics.drawString(this.font, "§7Total Value: §e" + formatCurrency(reportData.totalValue()),
            col2, summaryY, COLOR_TEXT);

        summaryY += 12;
        graphics.drawString(this.font, "§7Est. Tax/Period: §c" + formatCurrency(reportData.totalTax()),
            col1, summaryY, COLOR_TEXT);
        graphics.drawString(this.font, "§7Tax Period: §f" + reportData.taxPeriod(),
            col2, summaryY, COLOR_TEXT);

        summaryY += 12;
        graphics.drawString(this.font, "§7Current Balance: §f" + formatCurrency(reportData.balance()),
            col1, summaryY, COLOR_TEXT);
        graphics.drawString(this.font, "§7Next Collection: §f" + reportData.nextCollection(),
            col2, summaryY, COLOR_TEXT);

        // Affordability indicator
        summaryY += 12;
        int periodsAffordable = reportData.periodsAffordable();
        String affordStr;
        int affordColor;
        if (periodsAffordable <= 0) {
            affordStr = "§c§l⚠ CRITICAL: Cannot afford next tax payment!";
            affordColor = COLOR_WARNING;
        } else if (periodsAffordable <= 3) {
            affordStr = "§e⚠ Warning: Funds cover ~" + periodsAffordable + " period(s)";
            affordColor = 0xFFFFFF00;
        } else {
            affordStr = "§a✓ Funds cover ~" + periodsAffordable + " tax periods";
            affordColor = COLOR_SECONDARY;
        }
        graphics.drawString(this.font, affordStr, col1, summaryY, affordColor);

        // Divider
        int dividerY = summaryY + 14;
        graphics.fill(guiLeft + 10, dividerY, guiLeft + guiWidth - 10, dividerY + 1, COLOR_BORDER);

        // Section header
        graphics.drawString(this.font, "§6§l--- Breakdown by Jurisdiction ---",
            guiLeft + 15, dividerY + 4, COLOR_GOLD);

        // Scrollable content area
        graphics.enableScissor(guiLeft + 5, contentAreaTop, guiLeft + guiWidth - 5, contentAreaBottom);

        int rowY = contentAreaTop - (scrollOffset * ROW_HEIGHT);
        int rowIndex = 0;

        for (var nation : reportData.nations()) {
            // Nation row
            if (rowY >= contentAreaTop - ROW_HEIGHT && rowY < contentAreaBottom) {
                boolean nationExpanded = expandedNations.contains(nation.name());
                boolean hovered = mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

                if (hovered) {
                    graphics.fill(guiLeft + 10, rowY, guiLeft + guiWidth - 10, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                }

                String arrow = nationExpanded ? "§7▼ " : "§7▶ ";
                graphics.drawString(this.font, arrow + "§d§l" + nation.name() +
                    " §7(" + formatCurrency(nation.totalValue()) + ", " + formatCurrency(nation.totalTax()) + " tax)",
                    guiLeft + 15, rowY + 2, COLOR_NATION);
            }
            rowY += ROW_HEIGHT;

            if (expandedNations.contains(nation.name())) {
                for (var state : nation.states()) {
                    // State row
                    if (rowY >= contentAreaTop - ROW_HEIGHT && rowY < contentAreaBottom) {
                        String stateKey = nation.name() + ":" + state.name();
                        boolean stateExpanded = expandedStates.contains(stateKey);
                        boolean hovered = mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10
                            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

                        if (hovered) {
                            graphics.fill(guiLeft + 10, rowY, guiLeft + guiWidth - 10, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                        }

                        String arrow = stateExpanded ? "§7▼ " : "§7▶ ";
                        graphics.drawString(this.font, "  " + arrow + "§b" + state.name() +
                            " §7(" + formatCurrency(state.totalValue()) + ", " + formatCurrency(state.totalTax()) + " tax)",
                            guiLeft + 20, rowY + 2, COLOR_STATE);
                    }
                    rowY += ROW_HEIGHT;

                    String stateKey = nation.name() + ":" + state.name();
                    if (expandedStates.contains(stateKey)) {
                        for (var city : state.cities()) {
                            // City row
                            if (rowY >= contentAreaTop - ROW_HEIGHT && rowY < contentAreaBottom) {
                                String cityKey = stateKey + ":" + city.name();
                                boolean cityExpanded = expandedCities.contains(cityKey);
                                boolean hovered = mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10
                                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

                                if (hovered) {
                                    graphics.fill(guiLeft + 10, rowY, guiLeft + guiWidth - 10, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
                                }

                                String arrow = cityExpanded ? "§7▼ " : "§7▶ ";
                                graphics.drawString(this.font, "    " + arrow + "§a" + city.name() +
                                    " §7[" + city.chunks().size() + " chunks, " +
                                    String.format("%.1f%%", city.taxRate() * 100) + " rate]",
                                    guiLeft + 25, rowY + 2, COLOR_CITY);
                            }
                            rowY += ROW_HEIGHT;

                            String cityKey = stateKey + ":" + city.name();
                            if (expandedCities.contains(cityKey)) {
                                int chunkCount = 0;
                                for (var chunk : city.chunks()) {
                                    if (chunkCount >= 10) {
                                        // Show "+N more" row
                                        if (rowY >= contentAreaTop - ROW_HEIGHT && rowY < contentAreaBottom) {
                                            int remaining = city.chunks().size() - 10;
                                            graphics.drawString(this.font, "      §8+" + remaining + " more chunks...",
                                                guiLeft + 30, rowY + 2, COLOR_TEXT_DIM);
                                        }
                                        rowY += ROW_HEIGHT;
                                        break;
                                    }

                                    if (rowY >= contentAreaTop - ROW_HEIGHT && rowY < contentAreaBottom) {
                                        String dimAbbrev = abbreviateDimension(chunk.dimension());
                                        graphics.drawString(this.font, "      §8(" + chunk.chunkX() + ", " + chunk.chunkZ() + ") " + dimAbbrev +
                                            " §7val:§f" + formatCurrency(chunk.totalValue()) +
                                            " §7tax:§c" + formatCurrency(chunk.estimatedTax()),
                                            guiLeft + 30, rowY + 2, COLOR_TEXT_DIM);
                                    }
                                    rowY += ROW_HEIGHT;
                                    chunkCount++;
                                }
                            }
                        }
                    }
                }
            }
        }

        graphics.disableScissor();

        // Scroll indicator
        if (maxScroll > 0) {
            int scrollBarHeight = contentAreaBottom - contentAreaTop;
            int thumbHeight = Math.max(20, scrollBarHeight * scrollBarHeight / ((maxScroll + scrollBarHeight / ROW_HEIGHT) * ROW_HEIGHT));
            int thumbY = contentAreaTop + (int)((scrollBarHeight - thumbHeight) * ((float)scrollOffset / maxScroll));

            // Track
            graphics.fill(guiLeft + guiWidth - 12, contentAreaTop, guiLeft + guiWidth - 8, contentAreaBottom, 0x40FFFFFF);
            // Thumb
            graphics.fill(guiLeft + guiWidth - 12, thumbY, guiLeft + guiWidth - 8, thumbY + thumbHeight, 0xAAFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && dataLoaded && reportData != null) {
            // Check if clicking on expandable rows
            int rowY = contentAreaTop - (scrollOffset * ROW_HEIGHT);

            for (var nation : reportData.nations()) {
                // Check nation click
                if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT &&
                    mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10) {
                    toggleExpanded(expandedNations, nation.name());
                    calculateMaxScroll();
                    return true;
                }
                rowY += ROW_HEIGHT;

                if (expandedNations.contains(nation.name())) {
                    for (var state : nation.states()) {
                        // Check state click
                        if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT &&
                            mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10) {
                            String stateKey = nation.name() + ":" + state.name();
                            toggleExpanded(expandedStates, stateKey);
                            calculateMaxScroll();
                            return true;
                        }
                        rowY += ROW_HEIGHT;

                        String stateKey = nation.name() + ":" + state.name();
                        if (expandedStates.contains(stateKey)) {
                            for (var city : state.cities()) {
                                // Check city click
                                if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT &&
                                    mouseX >= guiLeft + 10 && mouseX < guiLeft + guiWidth - 10) {
                                    String cityKey = stateKey + ":" + city.name();
                                    toggleExpanded(expandedCities, cityKey);
                                    calculateMaxScroll();
                                    return true;
                                }
                                rowY += ROW_HEIGHT;

                                String cityKey = stateKey + ":" + city.name();
                                if (expandedCities.contains(cityKey)) {
                                    rowY += Math.min(city.chunks().size(), 10) * ROW_HEIGHT;
                                    if (city.chunks().size() > 10) rowY += ROW_HEIGHT;
                                }
                            }
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleExpanded(List<String> list, String key) {
        if (list.contains(key)) {
            list.remove(key);
        } else {
            list.add(key);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= guiLeft && mouseX < guiLeft + guiWidth &&
            mouseY >= contentAreaTop && mouseY < contentAreaBottom) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Background
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        // Border
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
    }

    private String formatCurrency(double amount) {
        if (amount >= 1_000_000) {
            return String.format("$%.1fM", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("$%.1fK", amount / 1_000);
        }
        return String.format("$%.2f", amount);
    }

    private String abbreviateDimension(String dimension) {
        if (dimension == null) return "?";
        if (dimension.contains("overworld")) return "OW";
        if (dimension.contains("the_nether")) return "Nether";
        if (dimension.contains("the_end")) return "End";
        int lastColon = dimension.lastIndexOf(':');
        return lastColon >= 0 ? dimension.substring(lastColon + 1) : dimension;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

