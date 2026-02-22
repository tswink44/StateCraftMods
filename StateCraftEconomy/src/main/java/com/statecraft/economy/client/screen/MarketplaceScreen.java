package com.statecraft.economy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.MarketplaceActionPacket;
import com.statecraft.economy.network.packets.RequestMarketListingsPacket;
import com.statecraft.economy.network.packets.SyncMarketListingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Marketplace Screen — browse, buy, sell, and manage listings.
 * Three tabs: Browse, My Listings, Sell.
 */
public class MarketplaceScreen extends Screen {

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
    private static final int COLOR_TAB_HOVER = 0xFF6A6A8C;
    private static final int COLOR_BUTTON = 0xFF2ECC71;
    private static final int COLOR_BUTTON_CANCEL = 0xFFE74C3C;
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
    private List<SyncMarketListingsPacket.ListingEntry> browseEntries = new ArrayList<>();
    private List<SyncMarketListingsPacket.ListingEntry> myEntries = new ArrayList<>();

    // Browse tab
    private EditBox searchBox;
    private int browseScrollOffset = 0;
    private static final int ROW_HEIGHT = 22;
    private int contentTop, contentBottom, visibleRows;
    private RequestMarketListingsPacket.SortMode sortMode = RequestMarketListingsPacket.SortMode.NEWEST;

    // Sell tab
    private EditBox priceBox;
    private EditBox quantityBox;
    private int selectedInventorySlot = -1;

    // Buy dialog
    private boolean showBuyDialog = false;
    private SyncMarketListingsPacket.ListingEntry buyTarget = null;
    private EditBox buyQuantityBox;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    public MarketplaceScreen() {
        super(Component.literal("Marketplace"));
    }

    @Override
    protected void init() {
        super.init();
        this.guiWidth = Math.min(420, this.width - 20);
        this.guiHeight = Math.min(280, this.height - 20);
        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;
        this.tabY = guiTop + 18;

        rebuildUI();

        // Request initial listings from server
        NetworkHandler.sendToServer(new RequestMarketListingsPacket("", sortMode, false));
    }

    private void rebuildUI() {
        clearWidgets();
        showBuyDialog = false;

        int contentStartY = tabY + TAB_HEIGHT + 4;

        switch (currentTab) {
            case BROWSE -> initBrowseTab(contentStartY);
            case MY_LISTINGS -> initMyListingsTab(contentStartY);
            case SELL -> initSellTab(contentStartY);
        }

        // Close button
        this.addRenderableWidget(Button.builder(Component.literal("×"),
            btn -> onClose())
            .bounds(guiLeft + guiWidth - 16, guiTop + 2, 14, 14)
            .build());
    }

    private void initBrowseTab(int startY) {
        // Search box
        int searchW = Math.min(140, guiWidth / 3);
        searchBox = new EditBox(this.font, guiLeft + 8, startY, searchW, 14, Component.literal("Search"));
        searchBox.setMaxLength(50);
        searchBox.setHint(Component.literal("Search items..."));
        searchBox.setResponder(text -> {
            browseScrollOffset = 0;
            NetworkHandler.sendToServer(new RequestMarketListingsPacket(text, sortMode, false));
        });
        this.addRenderableWidget(searchBox);

        // Sort buttons
        int sortX = guiLeft + 8 + searchW + 6;
        int btnW = 50;
        this.addRenderableWidget(Button.builder(Component.literal("Newest"),
            btn -> { sortMode = RequestMarketListingsPacket.SortMode.NEWEST; refreshBrowse(); })
            .bounds(sortX, startY, btnW, 14).build());
        this.addRenderableWidget(Button.builder(Component.literal("$ Low"),
            btn -> { sortMode = RequestMarketListingsPacket.SortMode.PRICE_LOW; refreshBrowse(); })
            .bounds(sortX + btnW + 2, startY, btnW, 14).build());
        this.addRenderableWidget(Button.builder(Component.literal("$ High"),
            btn -> { sortMode = RequestMarketListingsPacket.SortMode.PRICE_HIGH; refreshBrowse(); })
            .bounds(sortX + (btnW + 2) * 2, startY, btnW, 14).build());
        this.addRenderableWidget(Button.builder(Component.literal("A-Z"),
            btn -> { sortMode = RequestMarketListingsPacket.SortMode.NAME_AZ; refreshBrowse(); })
            .bounds(sortX + (btnW + 2) * 3, startY, btnW - 10, 14).build());

        contentTop = startY + 18;
        contentBottom = guiTop + guiHeight - 8;
        visibleRows = (contentBottom - contentTop) / ROW_HEIGHT;
    }

    private void initMyListingsTab(int startY) {
        contentTop = startY + 4;
        contentBottom = guiTop + guiHeight - 8;
        visibleRows = (contentBottom - contentTop) / ROW_HEIGHT;

        // Request my listings
        NetworkHandler.sendToServer(new RequestMarketListingsPacket("", sortMode, true));
    }

    private void initSellTab(int startY) {
        int fieldW = 80;
        int centerX = guiLeft + guiWidth / 2;

        // Inventory grid: title (14px) + 3 rows*18 + 4px gap + 1 row*18 = 14+54+4+18 = 90
        // Selected item info: +6 + 11 + 11 = 28
        int y = startY + 90 + 28 + 4; // Below inventory grid + selected item info

        priceBox = new EditBox(this.font, centerX - fieldW / 2, y, fieldW, 14, Component.literal("Price"));
        priceBox.setMaxLength(12);
        priceBox.setHint(Component.literal("Price/ea"));
        this.addRenderableWidget(priceBox);

        y += 18;
        quantityBox = new EditBox(this.font, centerX - fieldW / 2, y, fieldW, 14, Component.literal("Qty"));
        quantityBox.setMaxLength(6);
        quantityBox.setHint(Component.literal("Quantity"));
        this.addRenderableWidget(quantityBox);

        y += 18;
        this.addRenderableWidget(Button.builder(Component.literal("List Item"),
            btn -> performList())
            .bounds(centerX - 40, y, 80, 18)
            .build());

        contentTop = startY;
        contentBottom = guiTop + guiHeight - 8;
    }

    private void refreshBrowse() {
        browseScrollOffset = 0;
        String query = searchBox != null ? searchBox.getValue() : "";
        NetworkHandler.sendToServer(new RequestMarketListingsPacket(query, sortMode, false));
    }

    // ==================== Data Update ====================

    public void updateListings(List<SyncMarketListingsPacket.ListingEntry> entries, boolean myListings) {
        if (myListings) {
            this.myEntries = new ArrayList<>(entries);
        } else {
            // Apply client-side sorting
            this.browseEntries = new ArrayList<>(entries);
            sortEntries(browseEntries);
        }
    }

    private void sortEntries(List<SyncMarketListingsPacket.ListingEntry> list) {
        switch (sortMode) {
            case NEWEST -> list.sort(Comparator.comparingLong(SyncMarketListingsPacket.ListingEntry::listedTime).reversed());
            case PRICE_LOW -> list.sort(Comparator.comparingDouble(SyncMarketListingsPacket.ListingEntry::pricePerUnit));
            case PRICE_HIGH -> list.sort(Comparator.comparingDouble(SyncMarketListingsPacket.ListingEntry::pricePerUnit).reversed());
            case NAME_AZ -> list.sort(Comparator.comparing(SyncMarketListingsPacket.ListingEntry::itemName));
        }
    }

    // ==================== Render ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        renderPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        // Title
        graphics.drawCenteredString(this.font, "§l§6⚖ Marketplace", this.width / 2, guiTop + 5, COLOR_GOLD);

        // Tabs
        renderTabs(graphics, mouseX, mouseY);

        // Tab content — skip when buy dialog is open to prevent text bleed-through
        if (!showBuyDialog) {
            switch (currentTab) {
                case BROWSE -> renderBrowseTab(graphics, mouseX, mouseY);
                case MY_LISTINGS -> renderMyListingsTab(graphics, mouseX, mouseY);
                case SELL -> renderSellTab(graphics, mouseX, mouseY);
            }
        }

        // Buy dialog overlay
        if (showBuyDialog && buyTarget != null) {
            renderBuyDialog(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        String[] labels = {"Browse", "My Listings", "Sell"};
        int tabWidth = (guiWidth - 16) / tabs.length;

        for (int i = 0; i < tabs.length; i++) {
            int x = guiLeft + 8 + i * tabWidth;
            boolean active = tabs[i] == currentTab;
            boolean hovered = mouseX >= x && mouseX < x + tabWidth && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? COLOR_TAB_ACTIVE : (hovered ? COLOR_TAB_HOVER : COLOR_TAB_INACTIVE);
            graphics.fill(x, tabY, x + tabWidth - 2, tabY + TAB_HEIGHT, bg);
            if (active) {
                graphics.fill(x, tabY + TAB_HEIGHT - 2, x + tabWidth - 2, tabY + TAB_HEIGHT, 0xFFFFFFFF);
            }

            int labelColor = active ? COLOR_TEXT : COLOR_TEXT_DIM;
            int labelX = x + (tabWidth - 2 - this.font.width(labels[i])) / 2;
            graphics.drawString(this.font, labels[i], labelX, tabY + 4, labelColor);
        }
    }

    private void renderBrowseTab(GuiGraphics graphics, int mouseX, int mouseY) {
        List<SyncMarketListingsPacket.ListingEntry> entries = browseEntries;

        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7No listings found", this.width / 2, contentTop + 20, COLOR_TEXT_DIM);
            return;
        }

        // Column headers
        int headerY = contentTop - 2;
        int x = guiLeft + 8;
        graphics.drawString(this.font, "§nItem", x + 20, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nPrice", x + 140, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nQty", x + 200, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nSeller", x + 240, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nBuy", x + 340, headerY, COLOR_TEXT_DIM);

        int listTop = contentTop + 10;
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        browseScrollOffset = Math.min(browseScrollOffset, maxScroll);

        for (int i = 0; i < visibleRows && (i + browseScrollOffset) < entries.size(); i++) {
            int idx = i + browseScrollOffset;
            var entry = entries.get(idx);
            int rowY = listTop + i * ROW_HEIGHT;

            boolean hovered = mouseX >= guiLeft + 6 && mouseX < guiLeft + guiWidth - 6
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            // Row background
            int rowBg = hovered ? COLOR_ROW_HOVER : (idx % 2 == 0 ? COLOR_ROW_EVEN : COLOR_ROW_ODD);
            graphics.fill(guiLeft + 6, rowY, guiLeft + guiWidth - 6, rowY + ROW_HEIGHT, rowBg);

            // Item icon
            graphics.renderItem(entry.item(), x + 2, rowY + 2);

            // Item name (truncated)
            String name = entry.itemName();
            if (this.font.width(name) > 110) {
                while (this.font.width(name + "..") > 110 && name.length() > 1) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "..";
            }
            graphics.drawString(this.font, name, x + 20, rowY + 7, COLOR_TEXT);

            // Price
            graphics.drawString(this.font, "§a$" + formatNumber(entry.pricePerUnit()), x + 140, rowY + 7, COLOR_INCOMING);

            // Quantity
            graphics.drawString(this.font, String.valueOf(entry.quantity()), x + 200, rowY + 7, COLOR_TEXT);

            // Seller
            String seller = entry.sellerName();
            if (this.font.width(seller) > 90) {
                seller = seller.substring(0, Math.min(seller.length(), 10)) + "..";
            }
            graphics.drawString(this.font, "§7" + seller, x + 240, rowY + 7, COLOR_TEXT_DIM);

            // Buy button area
            boolean buyHovered = mouseX >= x + 335 && mouseX < x + 375 && mouseY >= rowY + 2 && mouseY < rowY + ROW_HEIGHT - 2;
            int buyBg = buyHovered ? 0xFF3DDB83 : COLOR_BUTTON;
            graphics.fill(x + 335, rowY + 3, x + 375, rowY + ROW_HEIGHT - 3, buyBg);
            graphics.drawCenteredString(this.font, "Buy", x + 355, rowY + 6, COLOR_TEXT);
        }

        // Scrollbar
        if (entries.size() > visibleRows) {
            renderScrollbar(graphics, guiLeft + guiWidth - 12, listTop, contentBottom, entries.size(), visibleRows, browseScrollOffset);
        }

        // Count
        graphics.drawString(this.font, "§7" + entries.size() + " listing" + (entries.size() != 1 ? "s" : ""),
            guiLeft + 8, guiTop + guiHeight - 6, COLOR_TEXT_DIM);
    }

    private void renderMyListingsTab(GuiGraphics graphics, int mouseX, int mouseY) {
        List<SyncMarketListingsPacket.ListingEntry> entries = myEntries;

        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, "§7You have no active listings", this.width / 2, contentTop + 20, COLOR_TEXT_DIM);
            return;
        }

        int x = guiLeft + 8;
        int headerY = contentTop - 2;
        graphics.drawString(this.font, "§nItem", x + 20, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nPrice", x + 150, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nQty", x + 220, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nListed", x + 260, headerY, COLOR_TEXT_DIM);
        graphics.drawString(this.font, "§nCancel", x + 335, headerY, COLOR_TEXT_DIM);

        int listTop = contentTop + 10;
        for (int i = 0; i < visibleRows && i < entries.size(); i++) {
            var entry = entries.get(i);
            int rowY = listTop + i * ROW_HEIGHT;

            int rowBg = i % 2 == 0 ? COLOR_ROW_EVEN : COLOR_ROW_ODD;
            graphics.fill(guiLeft + 6, rowY, guiLeft + guiWidth - 6, rowY + ROW_HEIGHT, rowBg);

            graphics.renderItem(entry.item(), x + 2, rowY + 2);

            String name = entry.itemName();
            if (this.font.width(name) > 120) {
                while (this.font.width(name + "..") > 120 && name.length() > 1)
                    name = name.substring(0, name.length() - 1);
                name += "..";
            }
            graphics.drawString(this.font, name, x + 20, rowY + 7, COLOR_TEXT);
            graphics.drawString(this.font, "§a$" + formatNumber(entry.pricePerUnit()), x + 150, rowY + 7, COLOR_INCOMING);
            graphics.drawString(this.font, String.valueOf(entry.quantity()), x + 220, rowY + 7, COLOR_TEXT);
            graphics.drawString(this.font, DATE_FORMAT.format(new Date(entry.listedTime())), x + 260, rowY + 7, COLOR_TEXT_DIM);

            // Cancel button
            boolean cancelHovered = mouseX >= x + 335 && mouseX < x + 385 && mouseY >= rowY + 2 && mouseY < rowY + ROW_HEIGHT - 2;
            int cancelBg = cancelHovered ? 0xFFFF5555 : COLOR_BUTTON_CANCEL;
            graphics.fill(x + 335, rowY + 3, x + 385, rowY + ROW_HEIGHT - 3, cancelBg);
            graphics.drawCenteredString(this.font, "Cancel", x + 360, rowY + 6, COLOR_TEXT);
        }

        graphics.drawString(this.font, "§7" + entries.size() + " active listing" + (entries.size() != 1 ? "s" : ""),
            guiLeft + 8, guiTop + guiHeight - 6, COLOR_TEXT_DIM);
    }

    private void renderSellTab(GuiGraphics graphics, int mouseX, int mouseY) {
        int centerX = guiLeft + guiWidth / 2;
        int y = contentTop + 4;

        graphics.drawCenteredString(this.font, "§eSelect an item from your inventory to sell",
            centerX, y, COLOR_TEXT_DIM);

        // Render player inventory grid (for item selection)
        y += 14;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int invCols = 9;
            int slotSize = 18;
            int invStartX = centerX - (invCols * slotSize) / 2;

            // Main inventory (27 slots, 3 rows)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < invCols; col++) {
                    int slot = 9 + row * 9 + col;
                    int sx = invStartX + col * slotSize;
                    int sy = y + row * slotSize;

                    boolean selected = slot == selectedInventorySlot;
                    boolean hovered = mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize;

                    int bg = selected ? COLOR_TAB_ACTIVE : (hovered ? COLOR_ROW_HOVER : 0x80333355);
                    graphics.fill(sx, sy, sx + slotSize, sy + slotSize, bg);
                    graphics.fill(sx, sy, sx + slotSize, sy + 1, COLOR_BORDER);
                    graphics.fill(sx, sy, sx + 1, sy + slotSize, COLOR_BORDER);

                    ItemStack stack = mc.player.getInventory().getItem(slot);
                    if (!stack.isEmpty()) {
                        graphics.renderItem(stack, sx + 1, sy + 1);
                        if (stack.getCount() > 1) {
                            graphics.drawString(this.font, String.valueOf(stack.getCount()),
                                sx + 17 - this.font.width(String.valueOf(stack.getCount())), sy + 9, COLOR_TEXT);
                        }
                    }
                }
            }

            // Hotbar (9 slots)
            int hotbarY = y + 3 * slotSize + 4;
            for (int col = 0; col < invCols; col++) {
                int slot = col;
                int sx = invStartX + col * slotSize;
                int sy = hotbarY;

                boolean selected = slot == selectedInventorySlot;
                boolean hovered = mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize;

                int bg = selected ? COLOR_TAB_ACTIVE : (hovered ? COLOR_ROW_HOVER : 0x80333355);
                graphics.fill(sx, sy, sx + slotSize, sy + slotSize, bg);
                graphics.fill(sx, sy, sx + slotSize, sy + 1, COLOR_BORDER);
                graphics.fill(sx, sy, sx + 1, sy + slotSize, COLOR_BORDER);

                ItemStack stack = mc.player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, sx + 1, sy + 1);
                    if (stack.getCount() > 1) {
                        graphics.drawString(this.font, String.valueOf(stack.getCount()),
                            sx + 17 - this.font.width(String.valueOf(stack.getCount())), sy + 9, COLOR_TEXT);
                    }
                }
            }

            // Selected item info
            if (selectedInventorySlot >= 0) {
                ItemStack selected = mc.player.getInventory().getItem(selectedInventorySlot);
                if (!selected.isEmpty()) {
                    int infoY = hotbarY + slotSize + 6;
                    graphics.drawCenteredString(this.font, "§f" + selected.getHoverName().getString(),
                        centerX, infoY, COLOR_TEXT);
                    graphics.drawCenteredString(this.font, "§7Available: " + selected.getCount(),
                        centerX, infoY + 11, COLOR_TEXT_DIM);
                }
            }
        }
    }

    private void renderBuyDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        // Semi-transparent overlay
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        int dw = 220;
        int dh = 140;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        renderPanel(graphics, dx, dy, dw, dh);
        graphics.drawCenteredString(this.font, "§l§eBuy Item", dx + dw / 2, dy + 8, COLOR_GOLD);

        int y = dy + 24;
        graphics.renderItem(buyTarget.item(), dx + 10, y);
        graphics.drawString(this.font, buyTarget.itemName(), dx + 30, y + 4, COLOR_TEXT);

        y += 20;
        graphics.drawString(this.font, "§7Price: §a$" + formatNumber(buyTarget.pricePerUnit()) + "/ea", dx + 10, y, COLOR_TEXT);
        y += 12;
        graphics.drawString(this.font, "§7Available: §f" + buyTarget.quantity(), dx + 10, y, COLOR_TEXT);
        y += 14;
        graphics.drawString(this.font, "§7Quantity:", dx + 10, y + 3, COLOR_TEXT);

        // Quantity input and buttons rendered by widgets

        // Total cost preview
        if (buyQuantityBox != null) {
            try {
                int qty = Integer.parseInt(buyQuantityBox.getValue());
                qty = Math.min(qty, buyTarget.quantity());
                if (qty > 0) {
                    double cost = buyTarget.pricePerUnit() * qty;
                    y += 20;
                    graphics.drawString(this.font, "§7Total: §e$" + formatNumber(cost), dx + 10, y, COLOR_TEXT);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // ==================== Input ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Buy dialog clicks
        if (showBuyDialog && buyTarget != null) {
            return handleBuyDialogClick(mouseX, mouseY);
        }

        // Tab clicks
        Tab[] tabs = Tab.values();
        int tabWidth = (guiWidth - 16) / tabs.length;
        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            for (int i = 0; i < tabs.length; i++) {
                int x = guiLeft + 8 + i * tabWidth;
                if (mouseX >= x && mouseX < x + tabWidth - 2) {
                    if (currentTab != tabs[i]) {
                        currentTab = tabs[i];
                        browseScrollOffset = 0;
                        selectedInventorySlot = -1;
                        rebuildUI();
                        if (currentTab == Tab.MY_LISTINGS) {
                            NetworkHandler.sendToServer(new RequestMarketListingsPacket("", sortMode, true));
                        } else if (currentTab == Tab.BROWSE) {
                            NetworkHandler.sendToServer(new RequestMarketListingsPacket("", sortMode, false));
                        }
                    }
                    return true;
                }
            }
        }

        // Browse tab: Buy button clicks
        if (currentTab == Tab.BROWSE && !browseEntries.isEmpty()) {
            int x = guiLeft + 8;
            int listTop = contentTop + 10;
            for (int i = 0; i < visibleRows && (i + browseScrollOffset) < browseEntries.size(); i++) {
                int rowY = listTop + i * ROW_HEIGHT;
                if (mouseX >= x + 335 && mouseX < x + 375 && mouseY >= rowY + 2 && mouseY < rowY + ROW_HEIGHT - 2) {
                    openBuyDialog(browseEntries.get(i + browseScrollOffset));
                    return true;
                }
            }
        }

        // My Listings tab: Cancel button clicks
        if (currentTab == Tab.MY_LISTINGS && !myEntries.isEmpty()) {
            int x = guiLeft + 8;
            int listTop = contentTop + 10;
            for (int i = 0; i < visibleRows && i < myEntries.size(); i++) {
                int rowY = listTop + i * ROW_HEIGHT;
                if (mouseX >= x + 335 && mouseX < x + 385 && mouseY >= rowY + 2 && mouseY < rowY + ROW_HEIGHT - 2) {
                    var entry = myEntries.get(i);
                    // Using CANCEL constructor — we distinguish from BUY by passing zero buyQuantity
                    NetworkHandler.sendToServer(new MarketplaceActionPacket(entry.listingId()));
                    return true;
                }
            }
        }

        // Sell tab: inventory slot clicks
        if (currentTab == Tab.SELL && Minecraft.getInstance().player != null) {
            int invCols = 9;
            int slotSize = 18;
            int centerX = guiLeft + guiWidth / 2;
            int invStartX = centerX - (invCols * slotSize) / 2;
            int y = contentTop + 18;

            // Main inventory
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < invCols; col++) {
                    int slot = 9 + row * 9 + col;
                    int sx = invStartX + col * slotSize;
                    int sy = y + row * slotSize;
                    if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                        selectedInventorySlot = slot;
                        return true;
                    }
                }
            }

            // Hotbar
            int hotbarY = y + 3 * slotSize + 4;
            for (int col = 0; col < invCols; col++) {
                int slot = col;
                int sx = invStartX + col * slotSize;
                int sy = hotbarY;
                if (mouseX >= sx && mouseX < sx + slotSize && mouseY >= sy && mouseY < sy + slotSize) {
                    selectedInventorySlot = slot;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openBuyDialog(SyncMarketListingsPacket.ListingEntry entry) {
        showBuyDialog = true;
        buyTarget = entry;

        // Add buy quantity input and buttons
        int dw = 220;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - 140) / 2;

        buyQuantityBox = new EditBox(this.font, dx + 80, dy + 88, 50, 14, Component.literal("Qty"));
        buyQuantityBox.setMaxLength(6);
        buyQuantityBox.setValue(String.valueOf(Math.min(entry.quantity(), 1)));
        this.addRenderableWidget(buyQuantityBox);

        this.addRenderableWidget(Button.builder(Component.literal("Confirm"),
            btn -> confirmBuy())
            .bounds(dx + 20, dy + 112, 80, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"),
            btn -> closeBuyDialog())
            .bounds(dx + 120, dy + 112, 80, 18).build());
    }

    private boolean handleBuyDialogClick(double mouseX, double mouseY) {
        // Let widgets handle their clicks
        return super.mouseClicked(mouseX, mouseY, 0);
    }

    private void confirmBuy() {
        if (buyTarget == null || buyQuantityBox == null) return;
        try {
            int qty = Integer.parseInt(buyQuantityBox.getValue().trim());
            if (qty <= 0) return;
            qty = Math.min(qty, buyTarget.quantity());
            NetworkHandler.sendToServer(new MarketplaceActionPacket(buyTarget.listingId(), qty));
            closeBuyDialog();
        } catch (NumberFormatException ignored) {}
    }

    private void closeBuyDialog() {
        showBuyDialog = false;
        buyTarget = null;
        buyQuantityBox = null;
        rebuildUI();
    }

    private void performList() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || selectedInventorySlot < 0) return;

        ItemStack selected = mc.player.getInventory().getItem(selectedInventorySlot);
        if (selected.isEmpty()) return;

        double price;
        int qty;
        try {
            price = Double.parseDouble(priceBox.getValue().trim());
            qty = Integer.parseInt(quantityBox.getValue().trim());
        } catch (NumberFormatException e) {
            return;
        }

        if (price <= 0 || qty <= 0) return;
        qty = Math.min(qty, selected.getCount());

        NetworkHandler.sendToServer(new MarketplaceActionPacket(selected.copy(), qty, price));
        selectedInventorySlot = -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (showBuyDialog) return true;

        if (currentTab == Tab.BROWSE && browseEntries.size() > visibleRows) {
            int maxScroll = browseEntries.size() - visibleRows;
            browseScrollOffset = Math.max(0, Math.min(maxScroll, browseScrollOffset - (int) delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ==================== Helpers ====================

    private void renderPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int top, int bottom, int total, int visible, int offset) {
        int height = bottom - top;
        graphics.fill(x, top, x + 4, bottom, 0x40FFFFFF);
        float ratio = (float) visible / total;
        int thumbH = Math.max(10, (int)(height * ratio));
        int maxScroll = total - visible;
        float thumbPos = maxScroll > 0 ? (float) offset / maxScroll : 0;
        int thumbY = top + (int)((height - thumbH) * thumbPos);
        graphics.fill(x, thumbY, x + 4, thumbY + thumbH, 0xAAFFFFFF);
    }

    private String formatNumber(double val) {
        if (val >= 1_000_000) return String.format("%.2fM", val / 1_000_000);
        if (val >= 1_000) return String.format("%.2fK", val / 1_000);
        return String.format("%.2f", val);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

