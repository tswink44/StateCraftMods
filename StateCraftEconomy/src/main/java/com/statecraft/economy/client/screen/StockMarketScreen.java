package com.statecraft.economy.client.screen;

import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.RequestStockListingsPacket;
import com.statecraft.economy.network.packets.StockMarketActionPacket;
import com.statecraft.economy.network.packets.SyncStockListingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Stock Market Screen — browse, buy, sell, and manage share listings.
 * Three tabs: Browse, My Listings, Sell.
 */
public class StockMarketScreen extends Screen {

    // Colors matching the existing StateCraft UI style
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
    private static final int COLOR_TAB_ACTIVE = 0xFF4A90D9;
    private static final int COLOR_TAB_INACTIVE = 0xFF555570;
    private static final int COLOR_GOLD = 0xFFFFD700;

    private enum Tab { BROWSE, MY_LISTINGS, SELL }
    private Tab currentTab = Tab.BROWSE;

    // Layout
    private int guiLeft, guiTop;
    private int guiWidth = 420;
    private int guiHeight = 260;

    // Tab buttons
    private static final int TAB_HEIGHT = 16;
    private int tabY;

    // Listing data from server
    private List<SyncStockListingsPacket.ListingEntry> browseEntries = new ArrayList<>();
    private List<SyncStockListingsPacket.ListingEntry> myEntries = new ArrayList<>();
    private List<SyncStockListingsPacket.CompanyShareInfo> playerShares = new ArrayList<>();

    // Browse tab
    private EditBox searchBox;
    private int browseScrollOffset = 0;
    private static final int ROW_HEIGHT = 22;
    private int contentTop, contentBottom, visibleRows;

    // Sell tab
    private EditBox priceBox;
    private EditBox quantityBox;
    private int selectedCompanyIndex = -1;

    // Buy dialog
    private boolean showBuyDialog = false;
    private SyncStockListingsPacket.ListingEntry buyTarget = null;
    private EditBox buyQuantityBox;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    public StockMarketScreen() {
        super(Component.literal("Stock Market"));
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        tabY = guiTop + 20;

        contentTop = tabY + TAB_HEIGHT + 4;
        contentBottom = guiTop + guiHeight - 8;
        visibleRows = (contentBottom - contentTop) / ROW_HEIGHT;

        initWidgets();

        // Request initial data
        NetworkHandler.sendToServer(new RequestStockListingsPacket(false, ""));
    }

    private void initWidgets() {
        clearWidgets();

        // Search box for Browse tab
        searchBox = new EditBox(font, guiLeft + 8, contentTop, guiWidth - 90, 14, Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search by company..."));
        searchBox.setResponder(s -> {
            browseScrollOffset = 0;
            NetworkHandler.sendToServer(new RequestStockListingsPacket(false, s));
        });

        if (currentTab == Tab.BROWSE) {
            addRenderableWidget(searchBox);
            // Refresh button
            addRenderableWidget(Button.builder(Component.literal("↻"), b -> {
                NetworkHandler.sendToServer(new RequestStockListingsPacket(false, searchBox.getValue()));
            }).bounds(guiLeft + guiWidth - 78, contentTop, 30, 14).build());

            // My Listings button
            addRenderableWidget(Button.builder(Component.literal("Mine"), b -> {
                switchTab(Tab.MY_LISTINGS);
            }).bounds(guiLeft + guiWidth - 46, contentTop, 38, 14).build());
        }

        if (currentTab == Tab.MY_LISTINGS) {
            addRenderableWidget(Button.builder(Component.literal("← Back"), b -> {
                switchTab(Tab.BROWSE);
            }).bounds(guiLeft + 8, contentTop, 50, 14).build());

            addRenderableWidget(Button.builder(Component.literal("↻"), b -> {
                NetworkHandler.sendToServer(new RequestStockListingsPacket(true, ""));
            }).bounds(guiLeft + guiWidth - 38, contentTop, 30, 14).build());
        }

        if (currentTab == Tab.SELL) {
            int sellY = contentTop + 4;

            // Price per share input
            priceBox = new EditBox(font, guiLeft + 120, sellY + 40, 80, 14, Component.literal("Price"));
            priceBox.setMaxLength(12);
            priceBox.setHint(Component.literal("$/share"));
            addRenderableWidget(priceBox);

            // Quantity input
            quantityBox = new EditBox(font, guiLeft + 120, sellY + 60, 80, 14, Component.literal("Quantity"));
            quantityBox.setMaxLength(8);
            quantityBox.setHint(Component.literal("shares"));
            addRenderableWidget(quantityBox);

            // List button
            addRenderableWidget(Button.builder(Component.literal("List for Sale"), b -> {
                createSellListing();
            }).bounds(guiLeft + 120, sellY + 82, 80, 16).build());
        }

        // Buy dialog
        if (showBuyDialog && buyTarget != null) {
            int dialogX = guiLeft + guiWidth / 2 - 80;
            int dialogY = guiTop + guiHeight / 2 - 40;

            buyQuantityBox = new EditBox(font, dialogX + 10, dialogY + 40, 60, 14, Component.literal("Qty"));
            buyQuantityBox.setMaxLength(8);
            buyQuantityBox.setValue(String.valueOf(buyTarget.getQuantity()));
            addRenderableWidget(buyQuantityBox);

            addRenderableWidget(Button.builder(Component.literal("Buy"), b -> {
                executeBuy();
            }).bounds(dialogX + 80, dialogY + 40, 35, 14).build());

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                showBuyDialog = false;
                buyTarget = null;
                initWidgets();
            }).bounds(dialogX + 118, dialogY + 40, 42, 14).build());
        }
    }

    private void switchTab(Tab tab) {
        currentTab = tab;
        browseScrollOffset = 0;
        initWidgets();

        if (tab == Tab.BROWSE) {
            NetworkHandler.sendToServer(new RequestStockListingsPacket(false, ""));
        } else if (tab == Tab.MY_LISTINGS) {
            NetworkHandler.sendToServer(new RequestStockListingsPacket(true, ""));
        } else if (tab == Tab.SELL) {
            selectedCompanyIndex = -1;
            NetworkHandler.sendToServer(new RequestStockListingsPacket(false, ""));
        }
    }

    /**
     * Called by ClientPacketHandler when listing data is received from server.
     */
    public void updateListings(List<SyncStockListingsPacket.ListingEntry> entries,
                                boolean isMyListingsView,
                                List<SyncStockListingsPacket.CompanyShareInfo> shares) {
        if (isMyListingsView) {
            this.myEntries = entries;
        } else {
            this.browseEntries = entries;
        }
        this.playerShares = shares;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Main panel
        graphics.fill(guiLeft - 1, guiTop - 1, guiLeft + guiWidth + 1, guiTop + guiHeight + 1, COLOR_BORDER);
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, COLOR_PANEL);

        // Title
        graphics.drawCenteredString(font, "§l§6Stock Market", width / 2, guiTop + 6, COLOR_GOLD);

        // Tabs
        renderTabs(graphics, mouseX, mouseY);

        // Content
        switch (currentTab) {
            case BROWSE -> renderBrowseTab(graphics, mouseX, mouseY);
            case MY_LISTINGS -> renderMyListingsTab(graphics, mouseX, mouseY);
            case SELL -> renderSellTab(graphics, mouseX, mouseY);
        }

        // Buy dialog overlay
        if (showBuyDialog && buyTarget != null) {
            renderBuyDialog(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabWidth = guiWidth / 3;
        String[] tabNames = { "Browse", "My Listings", "Sell" };
        Tab[] tabs = Tab.values();

        for (int i = 0; i < tabs.length; i++) {
            int tx = guiLeft + (i * tabWidth);
            boolean active = currentTab == tabs[i];
            boolean hovered = mouseX >= tx && mouseX < tx + tabWidth && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int color = active ? COLOR_TAB_ACTIVE : (hovered ? 0xFF6A6A8C : COLOR_TAB_INACTIVE);
            graphics.fill(tx, tabY, tx + tabWidth, tabY + TAB_HEIGHT, color);
            graphics.fill(tx, tabY, tx + tabWidth, tabY + 1, COLOR_BORDER);
            if (i > 0) graphics.fill(tx, tabY, tx + 1, tabY + TAB_HEIGHT, COLOR_BORDER);

            graphics.drawCenteredString(font, tabNames[i], tx + tabWidth / 2, tabY + 4, COLOR_TEXT);
        }
    }

    private void renderBrowseTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = contentTop + 18;
        int listBottom = contentBottom;

        // Header row
        int hx = guiLeft + 8;
        int hy = listTop;
        graphics.drawString(font, "§7Company", hx, hy, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7Seller", hx + 110, hy, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7Shares", hx + 195, hy, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7$/Share", hx + 245, hy, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7Total", hx + 310, hy, COLOR_TEXT_DIM);

        int rowY = listTop + 12;
        List<SyncStockListingsPacket.ListingEntry> entries = browseEntries;
        int maxVisible = (listBottom - rowY) / ROW_HEIGHT;

        for (int i = browseScrollOffset; i < entries.size() && (i - browseScrollOffset) < maxVisible; i++) {
            SyncStockListingsPacket.ListingEntry entry = entries.get(i);
            int y = rowY + (i - browseScrollOffset) * ROW_HEIGHT;
            boolean hovered = mouseX >= guiLeft + 8 && mouseX < guiLeft + guiWidth - 8
                           && mouseY >= y && mouseY < y + ROW_HEIGHT;

            // Row background
            int rowColor = hovered ? COLOR_ROW_HOVER : ((i % 2 == 0) ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
            graphics.fill(guiLeft + 4, y, guiLeft + guiWidth - 4, y + ROW_HEIGHT, rowColor);

            // Row data
            int textY = y + 3;
            graphics.drawString(font, truncate(entry.getCompanyName(), 14), hx, textY, COLOR_PRIMARY);
            graphics.drawString(font, truncate(entry.getSellerName(), 10), hx + 110, textY, entry.isMine() ? COLOR_INCOMING : COLOR_TEXT);
            graphics.drawString(font, String.valueOf(entry.getQuantity()), hx + 195, textY, COLOR_TEXT);
            graphics.drawString(font, String.format("$%.2f", entry.getPricePerShare()), hx + 245, textY, COLOR_GOLD);
            graphics.drawString(font, String.format("$%.0f", entry.getTotalPrice()), hx + 310, textY, COLOR_GOLD);

            // Date on second line
            String date = DATE_FORMAT.format(new Date(entry.getListedTime()));
            graphics.drawString(font, "§8" + date, hx, textY + 10, COLOR_TEXT_DIM);

            // Buy button (if not own listing)
            if (!entry.isMine()) {
                int btnX = guiLeft + guiWidth - 42;
                boolean btnHovered = mouseX >= btnX && mouseX < btnX + 34 && mouseY >= y + 2 && mouseY < y + 14;
                graphics.fill(btnX, y + 2, btnX + 34, y + 14, btnHovered ? 0xFF3DDB71 : COLOR_INCOMING);
                graphics.drawCenteredString(font, "Buy", btnX + 17, y + 4, COLOR_TEXT);
            }
        }

        // Empty state
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, "§7No share listings found.", width / 2, rowY + 20, COLOR_TEXT_DIM);
        }
    }

    private void renderMyListingsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int listTop = contentTop + 18;

        // Header
        int hx = guiLeft + 8;
        graphics.drawString(font, "§7Company", hx, listTop, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7Shares", hx + 130, listTop, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7$/Share", hx + 195, listTop, COLOR_TEXT_DIM);
        graphics.drawString(font, "§7Status", hx + 275, listTop, COLOR_TEXT_DIM);

        int rowY = listTop + 12;
        int maxVisible = (contentBottom - rowY) / ROW_HEIGHT;

        for (int i = 0; i < myEntries.size() && i < maxVisible; i++) {
            SyncStockListingsPacket.ListingEntry entry = myEntries.get(i);
            int y = rowY + i * ROW_HEIGHT;
            boolean hovered = mouseX >= guiLeft + 8 && mouseX < guiLeft + guiWidth - 8
                           && mouseY >= y && mouseY < y + ROW_HEIGHT;

            int rowColor = hovered ? COLOR_ROW_HOVER : ((i % 2 == 0) ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
            graphics.fill(guiLeft + 4, y, guiLeft + guiWidth - 4, y + ROW_HEIGHT, rowColor);

            int textY = y + 3;
            graphics.drawString(font, truncate(entry.getCompanyName(), 16), hx, textY, COLOR_PRIMARY);
            graphics.drawString(font, String.valueOf(entry.getQuantity()), hx + 130, textY, COLOR_TEXT);
            graphics.drawString(font, String.format("$%.2f", entry.getPricePerShare()), hx + 195, textY, COLOR_GOLD);

            // Status
            int statusColor = switch (entry.getStatus()) {
                case "ACTIVE" -> COLOR_INCOMING;
                case "SOLD" -> COLOR_PRIMARY;
                case "CANCELLED" -> COLOR_OUTGOING;
                default -> COLOR_TEXT_DIM;
            };
            graphics.drawString(font, entry.getStatus(), hx + 275, textY, statusColor);

            // Cancel button for active listings
            if ("ACTIVE".equals(entry.getStatus())) {
                int btnX = guiLeft + guiWidth - 52;
                boolean btnHovered = mouseX >= btnX && mouseX < btnX + 44 && mouseY >= y + 2 && mouseY < y + 14;
                graphics.fill(btnX, y + 2, btnX + 44, y + 14, btnHovered ? 0xFFFF5555 : COLOR_OUTGOING);
                graphics.drawCenteredString(font, "Cancel", btnX + 22, y + 4, COLOR_TEXT);
            }
        }

        if (myEntries.isEmpty()) {
            graphics.drawCenteredString(font, "§7You have no share listings.", width / 2, rowY + 20, COLOR_TEXT_DIM);
        }
    }

    private void renderSellTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int sellY = contentTop + 4;

        // Company selection list
        int listX = guiLeft + 8;
        graphics.drawString(font, "§7Your Shares:", listX, sellY, COLOR_TEXT);

        int companyY = sellY + 12;
        for (int i = 0; i < playerShares.size(); i++) {
            SyncStockListingsPacket.CompanyShareInfo info = playerShares.get(i);
            int y = companyY + i * 16;
            boolean hovered = mouseX >= listX && mouseX < listX + 100 && mouseY >= y && mouseY < y + 14;
            boolean selected = i == selectedCompanyIndex;

            int rowColor = selected ? COLOR_TAB_ACTIVE : (hovered ? COLOR_ROW_HOVER : COLOR_ROW_ODD);
            graphics.fill(listX, y, listX + 105, y + 14, rowColor);

            String label = truncate(info.getCompanyName(), 10) + " §7(" + info.getAvailableToList() + ")";
            graphics.drawString(font, label, listX + 2, y + 3, selected ? COLOR_TEXT : COLOR_TEXT_DIM);
        }

        // Selected company details
        if (selectedCompanyIndex >= 0 && selectedCompanyIndex < playerShares.size()) {
            SyncStockListingsPacket.CompanyShareInfo info = playerShares.get(selectedCompanyIndex);
            int detailX = guiLeft + 120;
            graphics.drawString(font, "§f" + info.getCompanyName(), detailX, sellY, COLOR_TEXT);
            graphics.drawString(font, "§7Owned: §f" + info.getSharesOwned() + " / " + info.getTotalShares(),
                detailX, sellY + 12, COLOR_TEXT);
            graphics.drawString(font, "§7Listed: §e" + info.getSharesListed() +
                " §7Available: §a" + info.getAvailableToList(),
                detailX, sellY + 24, COLOR_TEXT);
        }

        if (playerShares.isEmpty()) {
            graphics.drawCenteredString(font, "§7You don't own shares in any company.",
                width / 2, sellY + 30, COLOR_TEXT_DIM);
        }
    }

    private void renderBuyDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        // Darken background
        graphics.fill(guiLeft, guiTop, guiLeft + guiWidth, guiTop + guiHeight, 0x80000000);

        int dialogX = guiLeft + guiWidth / 2 - 80;
        int dialogY = guiTop + guiHeight / 2 - 40;
        int dialogW = 170;
        int dialogH = 70;

        // Dialog panel
        graphics.fill(dialogX - 1, dialogY - 1, dialogX + dialogW + 1, dialogY + dialogH + 1, COLOR_BORDER);
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, COLOR_PANEL);

        graphics.drawCenteredString(font, "§lBuy Shares", dialogX + dialogW / 2, dialogY + 4, COLOR_GOLD);

        if (buyTarget != null) {
            graphics.drawString(font, "§7" + buyTarget.getCompanyName() + " §f@ $" +
                String.format("%.2f", buyTarget.getPricePerShare()) + "/share",
                dialogX + 6, dialogY + 18, COLOR_TEXT);

            graphics.drawString(font, "§7Available: §f" + buyTarget.getQuantity(),
                dialogX + 6, dialogY + 28, COLOR_TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Tab clicks
            int tabWidth = guiWidth / 3;
            Tab[] tabs = Tab.values();
            for (int i = 0; i < tabs.length; i++) {
                int tx = guiLeft + (i * tabWidth);
                if (mouseX >= tx && mouseX < tx + tabWidth && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                    switchTab(tabs[i]);
                    return true;
                }
            }

            // Buy button clicks in Browse tab
            if (currentTab == Tab.BROWSE && !showBuyDialog) {
                int listTop = contentTop + 30;
                List<SyncStockListingsPacket.ListingEntry> entries = browseEntries;
                int maxVisible = (contentBottom - listTop) / ROW_HEIGHT;

                for (int i = browseScrollOffset; i < entries.size() && (i - browseScrollOffset) < maxVisible; i++) {
                    SyncStockListingsPacket.ListingEntry entry = entries.get(i);
                    int y = listTop + (i - browseScrollOffset) * ROW_HEIGHT;
                    int btnX = guiLeft + guiWidth - 42;

                    if (!entry.isMine() && mouseX >= btnX && mouseX < btnX + 34 && mouseY >= y + 2 && mouseY < y + 14) {
                        buyTarget = entry;
                        showBuyDialog = true;
                        initWidgets();
                        return true;
                    }
                }
            }

            // Cancel button clicks in My Listings tab
            if (currentTab == Tab.MY_LISTINGS) {
                int listTop = contentTop + 30;
                int maxVisible = (contentBottom - listTop) / ROW_HEIGHT;

                for (int i = 0; i < myEntries.size() && i < maxVisible; i++) {
                    SyncStockListingsPacket.ListingEntry entry = myEntries.get(i);
                    if (!"ACTIVE".equals(entry.getStatus())) continue;

                    int y = listTop + i * ROW_HEIGHT;
                    int btnX = guiLeft + guiWidth - 52;

                    if (mouseX >= btnX && mouseX < btnX + 44 && mouseY >= y + 2 && mouseY < y + 14) {
                        NetworkHandler.sendToServer(new StockMarketActionPacket(
                            StockMarketActionPacket.Action.CANCEL, entry.getListingId(), 0));
                        // Refresh
                        NetworkHandler.sendToServer(new RequestStockListingsPacket(true, ""));
                        return true;
                    }
                }
            }

            // Company selection in Sell tab
            if (currentTab == Tab.SELL) {
                int companyY = contentTop + 16;
                int listX = guiLeft + 8;
                for (int i = 0; i < playerShares.size(); i++) {
                    int y = companyY + i * 16;
                    if (mouseX >= listX && mouseX < listX + 105 && mouseY >= y && mouseY < y + 14) {
                        selectedCompanyIndex = i;
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == Tab.BROWSE) {
            browseScrollOffset = Math.max(0, browseScrollOffset - (int) delta);
            int maxScroll = Math.max(0, browseEntries.size() - visibleRows + 2);
            browseScrollOffset = Math.min(browseScrollOffset, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void createSellListing() {
        if (selectedCompanyIndex < 0 || selectedCompanyIndex >= playerShares.size()) return;

        SyncStockListingsPacket.CompanyShareInfo info = playerShares.get(selectedCompanyIndex);

        try {
            double price = Double.parseDouble(priceBox.getValue());
            int quantity = Integer.parseInt(quantityBox.getValue());

            if (price <= 0 || quantity <= 0) return;
            if (quantity > info.getAvailableToList()) return;

            NetworkHandler.sendToServer(new StockMarketActionPacket(
                info.getCompanyId(), quantity, price));

            // Clear fields
            priceBox.setValue("");
            quantityBox.setValue("");
        } catch (NumberFormatException e) {
            // Invalid input, ignore
        }
    }

    private void executeBuy() {
        if (buyTarget == null || buyQuantityBox == null) return;

        try {
            int qty = Integer.parseInt(buyQuantityBox.getValue());
            if (qty <= 0 || qty > buyTarget.getQuantity()) return;

            NetworkHandler.sendToServer(new StockMarketActionPacket(
                StockMarketActionPacket.Action.BUY, buyTarget.getListingId(), qty));
        } catch (NumberFormatException e) {
            // Invalid input, ignore
        }

        showBuyDialog = false;
        buyTarget = null;
        initWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }
}

