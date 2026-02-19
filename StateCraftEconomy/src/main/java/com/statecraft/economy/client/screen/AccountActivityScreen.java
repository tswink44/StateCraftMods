package com.statecraft.economy.client.screen;

import com.statecraft.economy.network.packets.SyncAccountActivityPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Account Activity screen - displays transaction history for any account type.
 * Shows scrollable list of transactions with timestamp, type, amount, description, and initiator.
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

    // Layout
    private int guiLeft;
    private int guiTop;
    private int guiWidth = 340;
    private int guiHeight = 240;

    // Data
    private final String accountType;
    private final String accountName;
    private final String accountId;
    private final List<SyncAccountActivityPacket.ActivityEntry> entries;

    // Scrolling
    private int scrollOffset = 0;
    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 16;
    private int visibleRows;
    private int contentAreaTop;
    private int contentAreaBottom;

    // Date formatter
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    public AccountActivityScreen(String accountType, String accountName, String accountId,
                                  List<SyncAccountActivityPacket.ActivityEntry> entries) {
        super(Component.literal("Account Activity"));
        this.accountType = accountType;
        this.accountName = accountName;
        this.accountId = accountId;
        this.entries = entries;
    }

    @Override
    protected void init() {
        super.init();

        // Auto-size to fit screen
        this.guiWidth = Math.min(340, this.width - 40);
        this.guiHeight = Math.min(240, this.height - 40);

        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        // Calculate content area
        contentAreaTop = guiTop + 38; // After title and column headers
        contentAreaBottom = guiTop + guiHeight - 30; // Before back button
        visibleRows = (contentAreaBottom - contentAreaTop) / ROW_HEIGHT;

        // Back button
        this.addRenderableWidget(Button.builder(Component.literal("< Back"),
            btn -> onClose())
            .bounds(guiLeft + guiWidth / 2 - 40, guiTop + guiHeight - 25, 80, 20)
            .build());
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

        // Divider
        graphics.fill(guiLeft + 8, guiTop + 18, guiLeft + guiWidth - 8, guiTop + 19, COLOR_BORDER);

        // Column headers
        int headerY = guiTop + 22;
        graphics.fill(guiLeft + 8, headerY, guiLeft + guiWidth - 8, headerY + HEADER_HEIGHT, COLOR_HEADER_BG);

        int col1 = guiLeft + 12;  // Date
        int col2 = guiLeft + 68;  // Type
        int col3 = guiLeft + 140; // Amount
        int col4 = guiLeft + 200; // Description / Initiator

        graphics.drawString(this.font, "§nDate", col1, headerY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nType", col2, headerY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nAmount", col3, headerY + 4, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nDetails", col4, headerY + 4, COLOR_TEXT_DIM);

        // Render entries
        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No transactions recorded",
                this.width / 2, contentAreaTop + 20, COLOR_TEXT_DIM);
        } else {
            // Enable scissor to clip content area
            graphics.enableScissor(guiLeft + 8, contentAreaTop, guiLeft + guiWidth - 8, contentAreaBottom);

            int maxScroll = Math.max(0, entries.size() - visibleRows);
            scrollOffset = Math.min(scrollOffset, maxScroll);

            for (int i = 0; i < visibleRows + 1 && (i + scrollOffset) < entries.size(); i++) {
                int entryIndex = i + scrollOffset;
                SyncAccountActivityPacket.ActivityEntry entry = entries.get(entryIndex);

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

                // Description (first line) - truncate if needed
                String desc = entry.description();
                int maxDescWidth = guiWidth - (col4 - guiLeft) - 16;
                if (this.font.width(desc) > maxDescWidth) {
                    desc = this.font.plainSubstrByWidth(desc, maxDescWidth - this.font.width("...")) + "...";
                }
                graphics.drawString(this.font, desc, col4, rowY + 3, COLOR_TEXT);

                // Initiator (second line, smaller)
                if (!entry.initiatorName().isEmpty()) {
                    String initiatorText = "§7by: §f" + entry.initiatorName();
                    graphics.drawString(this.font, initiatorText, col4, rowY + 15, COLOR_TEXT_DIM);
                }
            }

            graphics.disableScissor();

            // Scrollbar
            if (entries.size() > visibleRows) {
                renderScrollbar(graphics, maxScroll);
            }
        }

        // Entry count
        String countText = "§7" + entries.size() + " transaction" + (entries.size() != 1 ? "s" : "");
        graphics.drawString(this.font, countText, guiLeft + 12, guiTop + guiHeight - 22, COLOR_TEXT_DIM);

        // Render widgets (buttons)
        super.render(graphics, mouseX, mouseY, partialTick);
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
        float thumbRatio = (float) visibleRows / entries.size();
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
        if (entries.size() > visibleRows) {
            int maxScroll = entries.size() - visibleRows;
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


