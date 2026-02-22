package com.statecraft.economy.client.screen;

import com.statecraft.economy.network.packets.SyncAccountActivityPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Account Activity screen - displays transaction history for any account type.
 * Shows scrollable list of transactions with timestamp, type, amount, description, and initiator.
 * Includes filter buttons for transaction type and a text search box.
 * Accessible from the ATM's main menu.
 */
public class AccountActivityScreen extends Screen {

    // Colors (matching SimpleATMScreen/StateCraft style)
    private static final int COLOR_PRIMARY = 0xFF4A90D9;
    private static final int COLOR_INCOMING = 0xFF2ECC71;
    private static final int COLOR_OUTGOING = 0xFFE74C3C;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_PANEL = 0xDD1A1A2E;
    private static final int COLOR_BORDER = 0xFF3D3D5C;
    private static final int COLOR_ROW_EVEN = 0x40404060;
    private static final int COLOR_ROW_ODD = 0x30303050;
    private static final int COLOR_ROW_HOVER = 0x60505080;
    private static final int COLOR_HEADER_BG = 0xAA303050;
    private static final int COLOR_FILTER_ACTIVE = 0xFF4A90D9;
    private static final int COLOR_FILTER_INACTIVE = 0xFF555570;
    private static final int COLOR_FILTER_HOVER = 0xFF6A6A8C;

    // Layout
    private int guiLeft;
    private int guiTop;
    private int guiWidth = 400;
    private int guiHeight = 240;

    // Data
    private final String accountType;
    private final String accountName;
    private final String accountId;
    private final List<SyncAccountActivityPacket.ActivityEntry> allEntries;
    private List<SyncAccountActivityPacket.ActivityEntry> filteredEntries;

    // Scrolling
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 16;
    private int visibleRows;
    private int contentAreaTop;
    private int contentAreaBottom;

    // Date formatter
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    // ==================== Filtering ====================

    /**
     * Filter categories that group related transaction types together.
     */
    private enum FilterCategory {
        ALL("All", null),
        DEPOSITS("Deposits", Set.of("DEPOSIT", "NATION_DEPOSIT", "CITY_DEPOSIT")),
        WITHDRAWALS("Withdrawals", Set.of("WITHDRAWAL", "NATION_WITHDRAWAL", "CITY_WITHDRAWAL")),
        TRANSFERS("Transfers", Set.of("TRANSFER_IN", "TRANSFER_OUT")),
        TAX("Tax", Set.of("TAX")),
        DIVIDENDS("Dividends", Set.of("DIVIDEND")),
        BANKING("Banking", Set.of("INTEREST", "LOAN_REPAYMENT")),
        TRADES("Trades", Set.of("PURCHASE", "SALE")),
        MARKETPLACE("Market", Set.of("MARKETPLACE_PURCHASE", "MARKETPLACE_SALE", "IMPORT_TARIFF")),
        FEES("Fees", Set.of("FEE"));

        final String label;
        final Set<String> types; // null = matches all

        FilterCategory(String label, Set<String> types) {
            this.label = label;
            this.types = types;
        }

        boolean matches(String type) {
            return types == null || types.contains(type);
        }
    }

    private FilterCategory activeFilter = FilterCategory.ALL;
    private String searchText = "";
    private EditBox searchBox;

    // Filter button layout (computed in init)
    private final List<FilterButtonInfo> filterButtons = new ArrayList<>();

    private record FilterButtonInfo(FilterCategory category, int x, int y, int width, int height) {}

    public AccountActivityScreen(String accountType, String accountName, String accountId,
                                  List<SyncAccountActivityPacket.ActivityEntry> entries) {
        super(Component.literal("Account Activity"));
        this.accountType = accountType;
        this.accountName = accountName;
        this.accountId = accountId;
        this.allEntries = entries;
        this.filteredEntries = new ArrayList<>(entries);
    }

    @Override
    protected void init() {
        super.init();

        // Auto-size to fit screen
        this.guiWidth = Math.min(400, this.width - 20);
        this.guiHeight = Math.min(260, this.height - 20);

        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        // --- Filter bar layout (below title, above search) ---
        int filterY = guiTop + 20;
        int filterBarLeft = guiLeft + 8;
        int filterBarRight = guiLeft + guiWidth - 8;
        int availableWidth = filterBarRight - filterBarLeft;

        // Calculate button widths based on label sizes + padding
        filterButtons.clear();
        FilterCategory[] categories = FilterCategory.values();
        int totalLabelWidth = 0;
        for (FilterCategory cat : categories) {
            totalLabelWidth += this.font.width(cat.label);
        }
        int padding = 6; // px padding per button side
        int gap = 2;     // px gap between buttons
        int totalPadding = categories.length * padding * 2 + (categories.length - 1) * gap;

        // Scale down if needed
        double scale = 1.0;
        if (totalLabelWidth + totalPadding > availableWidth) {
            scale = (double)(availableWidth - (categories.length - 1) * gap) / (totalLabelWidth + categories.length * padding * 2);
        }

        int btnX = filterBarLeft;
        for (FilterCategory cat : categories) {
            int btnW = (int)((this.font.width(cat.label) + padding * 2) * scale);
            btnW = Math.max(btnW, 16); // minimum width
            filterButtons.add(new FilterButtonInfo(cat, btnX, filterY, btnW, 12));
            btnX += btnW + gap;
        }

        // --- Search box (below filters) ---
        int searchY = filterY + 15;
        int searchWidth = Math.min(120, availableWidth / 3);
        searchBox = new EditBox(this.font, guiLeft + guiWidth - 8 - searchWidth, searchY, searchWidth, 12,
            Component.literal("Search"));
        searchBox.setMaxLength(30);
        searchBox.setBordered(true);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setValue(searchText);
        searchBox.setResponder(text -> {
            searchText = text;
            applyFilters();
        });
        this.addRenderableWidget(searchBox);

        // Calculate content area (below search + column headers)
        int columnHeaderY = searchY + 16;
        contentAreaTop = columnHeaderY + HEADER_HEIGHT + 2;
        contentAreaBottom = guiTop + guiHeight - 30; // Before back button
        visibleRows = (contentAreaBottom - contentAreaTop) / ROW_HEIGHT;

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("< Back"),
            btn -> onClose())
            .bounds(guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 25, 80, 20)
            .build());

        // Apply current filters
        applyFilters();
    }

    /**
     * Recalculate filteredEntries based on activeFilter and searchText.
     */
    private void applyFilters() {
        String lowerSearch = searchText.toLowerCase().trim();
        filteredEntries = allEntries.stream()
            .filter(e -> activeFilter.matches(e.type()))
            .filter(e -> {
                if (lowerSearch.isEmpty()) return true;
                // Search in description, initiator name, type label, and formatted amount
                return e.description().toLowerCase().contains(lowerSearch)
                    || e.initiatorName().toLowerCase().contains(lowerSearch)
                    || getShortTypeLabel(e.type()).toLowerCase().contains(lowerSearch)
                    || formatCurrency(e.amount()).toLowerCase().contains(lowerSearch);
            })
            .collect(Collectors.toList());

        // Reset scroll when filters change
        scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background
        this.renderBackground(graphics);

        // Panel background
        renderPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        // Title
        String typeIcon = switch (accountType) {
            case "PERSONAL" -> "§b♦ ";
            case "NATION" -> "§6♛ ";
            case "STATE" -> "§e★ ";
            case "CITY" -> "§a● ";
            default -> "";
        };
        String title = typeIcon + "§lAccount Activity: §r" + accountName;
        graphics.drawCenteredString(this.font, title, this.width / 2, guiTop + 6, COLOR_PRIMARY);

        // --- Render filter buttons ---
        for (FilterButtonInfo btn : filterButtons) {
            boolean isActive = btn.category() == activeFilter;
            boolean isHovered = mouseX >= btn.x() && mouseX < btn.x() + btn.width()
                && mouseY >= btn.y() && mouseY < btn.y() + btn.height();

            int bgColor = isActive ? COLOR_FILTER_ACTIVE : (isHovered ? COLOR_FILTER_HOVER : COLOR_FILTER_INACTIVE);
            graphics.fill(btn.x(), btn.y(), btn.x() + btn.width(), btn.y() + btn.height(), bgColor);

            // 1px border on active
            if (isActive) {
                graphics.fill(btn.x(), btn.y() + btn.height() - 1, btn.x() + btn.width(), btn.y() + btn.height(), 0xFFFFFFFF);
            }

            // Label (centered)
            String label = btn.category().label;
            int labelWidth = this.font.width(label);
            int labelX = btn.x() + (btn.width() - labelWidth) / 2;
            int labelColor = isActive ? COLOR_TEXT : COLOR_TEXT_DIM;
            graphics.drawString(this.font, label, labelX, btn.y() + 2, labelColor);
        }

        // "Search:" label next to search box
        int searchLabelX = searchBox.getX() - this.font.width("Search: ") - 2;
        graphics.drawString(this.font, "§7Search:", searchLabelX, searchBox.getY() + 2, COLOR_TEXT_DIM);

        // --- Divider above column headers ---
        int columnHeaderY = searchBox.getY() + 16;
        graphics.fill(guiLeft + 8, columnHeaderY - 2, guiLeft + guiWidth - 8, columnHeaderY - 1, COLOR_BORDER);

        // Column headers
        graphics.fill(guiLeft + 8, columnHeaderY, guiLeft + guiWidth - 8, columnHeaderY + HEADER_HEIGHT, COLOR_HEADER_BG);

        int col1 = guiLeft + 12;  // Date
        int col2 = guiLeft + 68;  // Type
        int col3 = guiLeft + 130; // Amount
        int col4 = guiLeft + 195; // Balance
        int col5 = guiLeft + 260; // Description / Initiator

        graphics.drawString(this.font, "§nDate", col1, columnHeaderY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nType", col2, columnHeaderY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nAmount", col3, columnHeaderY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nBalance", col4, columnHeaderY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nDetails", col5, columnHeaderY + 4, COLOR_TEXT_DIM);

        // Render entries
        if (filteredEntries.isEmpty()) {
            String emptyMsg = allEntries.isEmpty() ? "§7No transactions recorded"
                : "§7No transactions match the current filter";
            graphics.drawCenteredString(this.font, emptyMsg,
                this.width / 2, contentAreaTop + 20, COLOR_TEXT_DIM);
        } else {
            // Enable scissor to clip content area
            graphics.enableScissor(guiLeft + 8, contentAreaTop, guiLeft + guiWidth - 8, contentAreaBottom);

            int maxScroll = Math.max(0, filteredEntries.size() - visibleRows);
            scrollOffset = Math.min(scrollOffset, maxScroll);

            for (int i = 0; i < visibleRows + 1 && (i + scrollOffset) < filteredEntries.size(); i++) {
                int entryIndex = i + scrollOffset;
                SyncAccountActivityPacket.ActivityEntry entry = filteredEntries.get(entryIndex);

                int rowY = contentAreaTop + (i * ROW_HEIGHT);
                if (rowY + ROW_HEIGHT > contentAreaBottom) break;

                // Row background (alternating + hover)
                boolean hovered = mouseX >= guiLeft + 8 && mouseX < guiLeft + guiWidth - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                int rowColor = hovered ? COLOR_ROW_HOVER : (entryIndex % 2 == 0 ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
                graphics.fill(guiLeft + 8, rowY, guiLeft + guiWidth - 8, rowY + ROW_HEIGHT, rowColor);

                // Date
                String dateStr = DATE_FORMAT.format(new Date(entry.timestamp()));
                graphics.drawString(this.font, "§7" + dateStr, col1, rowY + 3, COLOR_TEXT);

                // Transaction type (short label)
                String typeLabel = getShortTypeLabel(entry.type());
                int typeColor = entry.incoming() ? COLOR_INCOMING : COLOR_OUTGOING;
                graphics.drawString(this.font, typeLabel, col2, rowY + 3, typeColor);

                // Amount
                String amountPrefix = entry.incoming() ? "§a+" : "§c-";
                String amountStr = amountPrefix + formatCurrency(entry.amount());
                graphics.drawString(this.font, amountStr, col3, rowY + 3, COLOR_TEXT);

                // Running balance
                double bal = entry.runningBalance();
                int balColor = bal < 0 ? COLOR_OUTGOING : COLOR_TEXT_DIM;
                String balStr = formatCurrency(Math.abs(bal));
                if (bal < 0) balStr = "§c-" + balStr;
                else balStr = "§7" + balStr;
                graphics.drawString(this.font, balStr, col4, rowY + 3, balColor);

                // Description (first line) - truncate if needed
                String desc = entry.description();
                int maxDescWidth = guiWidth - (col5 - guiLeft) - 16;
                if (this.font.width(desc) > maxDescWidth) {
                    desc = this.font.plainSubstrByWidth(desc, maxDescWidth - this.font.width("...")) + "...";
                }
                graphics.drawString(this.font, desc, col5, rowY + 3, COLOR_TEXT);

                // Initiator (second line, smaller)
                if (!entry.initiatorName().isEmpty()) {
                    String initiatorText = "§7by: §f" + entry.initiatorName();
                    graphics.drawString(this.font, initiatorText, col5, rowY + 15, COLOR_TEXT_DIM);
                }
            }

            graphics.disableScissor();

            // Scrollbar
            if (filteredEntries.size() > visibleRows) {
                renderScrollbar(graphics, maxScroll);
            }
        }

        // Entry count (showing filtered vs total when filter is active)
        String countText;
        if (activeFilter == FilterCategory.ALL && searchText.isEmpty()) {
            countText = "§7" + allEntries.size() + " transaction" + (allEntries.size() != 1 ? "s" : "");
        } else {
            countText = "§7" + filteredEntries.size() + " of " + allEntries.size() + " transactions";
        }
        graphics.drawString(this.font, countText, guiLeft + 12, guiTop + guiHeight - 22, COLOR_TEXT_DIM);

        // Render widgets (buttons, search box)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check filter button clicks
        if (button == 0) {
            for (FilterButtonInfo btn : filterButtons) {
                if (mouseX >= btn.x() && mouseX < btn.x() + btn.width()
                    && mouseY >= btn.y() && mouseY < btn.y() + btn.height()) {
                    activeFilter = btn.category();
                    applyFilters();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        // Panel background
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);

        // Border
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);           // Top
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);   // Bottom
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);           // Left
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);   // Right
    }

    private void renderScrollbar(GuiGraphics graphics, int maxScroll) {
        int scrollbarX = guiLeft + guiWidth - 14;
        int scrollbarWidth = 4;
        int scrollableHeight = contentAreaBottom - contentAreaTop;

        // Track
        graphics.fill(scrollbarX, contentAreaTop, scrollbarX + scrollbarWidth, contentAreaBottom, 0x40FFFFFF);

        // Thumb
        float thumbRatio = (float) visibleRows / filteredEntries.size();
        int thumbHeight = Math.max(10, (int) (scrollableHeight * thumbRatio));
        float thumbPos = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = contentAreaTop + (int) ((scrollableHeight - thumbHeight) * thumbPos);

        graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, 0xAAFFFFFF);
    }

    private String getShortTypeLabel(String type) {
        boolean isGov = !"PERSONAL".equals(accountType);
        return switch (type) {
            case "DEPOSIT" -> "Deposit";
            case "WITHDRAWAL" -> "Withdraw";
            case "TRANSFER_IN" -> "Recv";
            case "TRANSFER_OUT" -> "Send";
            case "FEE" -> "Fee";
            case "NATION_DEPOSIT" -> "Gov Dep";
            case "NATION_WITHDRAWAL" -> "Gov Wth";
            case "CITY_DEPOSIT" -> "City Dep";
            case "CITY_WITHDRAWAL" -> "City Wth";
            case "PURCHASE" -> "Purchase";
            case "SALE" -> "Sale";
            case "TAX" -> isGov ? "Tax Rev" : "Tax";
            case "DIVIDEND" -> "Dividend";
            case "INTEREST" -> "Interest";
            case "LOAN_REPAYMENT" -> "Loan Pay";
            case "MARKETPLACE_PURCHASE" -> "Mkt Buy";
            case "MARKETPLACE_SALE" -> "Mkt Sell";
            case "IMPORT_TARIFF" -> "Import";
            default -> type;
        };
    }

    private String formatCurrency(double amount) {
        if (amount >= 1000000) {
            return String.format("$%.2fM", amount / 1000000);
        } else if (amount >= 1000) {
            return String.format("$%.2fK", amount / 1000);
        } else {
            return String.format("$%.2f", amount);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (filteredEntries.size() > visibleRows) {
            int maxScroll = filteredEntries.size() - visibleRows;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // Go back to the ATM screen
        Minecraft.getInstance().setScreen(new SimpleATMScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


