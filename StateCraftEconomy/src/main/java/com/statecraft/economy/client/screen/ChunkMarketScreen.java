package com.statecraft.economy.client.screen;

import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.ChunkMarketPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for buying and selling chunks
 * Compact row-based layout similar to other StateCraft GUIs
 */
public class ChunkMarketScreen extends Screen {

    // Colors
    private static final int COLOR_PRIMARY = 0xFF4A90D9;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_PANEL = 0xDD1A1A2E;
    private static final int COLOR_BORDER = 0xFF3D3D5C;

    // Layout
    private int guiLeft;
    private int guiTop;
    private int guiWidth = 280;
    private int guiHeight = 260;

    // Chunk coordinates
    private final int chunkX;
    private final int chunkZ;

    // Data from server
    private boolean dataLoaded = false;
    private boolean isClaimed = false;
    private boolean isForSale = false;
    private double salePrice = 0;
    private String sellerName = "";
    private String ownerName = "";
    private String cityName = "";
    private boolean isPrivatelyOwned = false;
    private boolean canListForSale = false;
    private boolean canBuy = false;
    private double valuation = 0;
    private double estimatedTax = 0;

    // Price input for listing
    private EditBox priceInput;
    private boolean showPriceInput = false;

    // Buttons
    private Button listForSaleButton;
    private Button removeFromSaleButton;
    private Button buyButton;
    private Button confirmPriceButton;
    private Button cancelPriceButton;

    // Menu entries
    private List<MenuEntry> menuEntries = new ArrayList<>();

    public ChunkMarketScreen(int chunkX, int chunkZ) {
        super(Component.literal("Chunk Market"));
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    protected void init() {
        super.init();

        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;

        // Request chunk market info from server
        NetworkHandler.sendToServer(new ChunkMarketPacket(
            ChunkMarketPacket.Action.REQUEST_INFO, chunkX, chunkZ));

        buildUI();
    }

    private void buildUI() {
        this.clearWidgets();
        menuEntries.clear();

        int startX = guiLeft + 10;
        int arrowX = guiLeft + guiWidth - 32;
        int startY = guiTop + 24;
        int spacing = 18;
        int buttonHeight = 14;
        int row = 0;

        // Chunk coordinates row
        menuEntries.add(new MenuEntry("Chunk: (" + chunkX + ", " + chunkZ + ")", startY + spacing * row, false));
        row++;

        // Status row
        String status = !isClaimed ? "Wilderness (Unclaimed)" :
                       isForSale ? "For Sale" :
                       isPrivatelyOwned ? "Private Property" : "Government Land";
        menuEntries.add(new MenuEntry("Status: " + status, startY + spacing * row, false));
        row++;

        // Owner/City row
        if (isClaimed) {
            if (isPrivatelyOwned && !ownerName.isEmpty()) {
                menuEntries.add(new MenuEntry("Owner: " + ownerName, startY + spacing * row, false));
            } else if (!cityName.isEmpty()) {
                menuEntries.add(new MenuEntry("City: " + cityName, startY + spacing * row, false));
            }
            row++;
        }

        // Price row (if for sale)
        if (isForSale) {
            String priceStr = EconomyManager.getInstance().formatCurrency(salePrice);
            menuEntries.add(new MenuEntry("Price: " + priceStr, startY + spacing * row, false));
            row++;

            if (!sellerName.isEmpty()) {
                menuEntries.add(new MenuEntry("Seller: " + sellerName, startY + spacing * row, false));
                row++;
            }
        }

        // Valuation and Tax rows (always show for claimed chunks)
        if (isClaimed) {
            String valStr = EconomyManager.getInstance().formatCurrency(valuation);
            menuEntries.add(new MenuEntry("Valuation: " + valStr, startY + spacing * row, false));
            row++;

            String taxStr = EconomyManager.getInstance().formatCurrency(estimatedTax);
            menuEntries.add(new MenuEntry("Est. Tax: " + taxStr + "/cycle", startY + spacing * row, false));
            row++;
        }

        row++; // Gap before buttons

        // Action buttons area
        int buttonY = guiTop + guiHeight - 80;
        int buttonWidth = 120;
        int centerX = this.width / 2;

        if (!showPriceInput) {
            // List for Sale button
            if (canListForSale && !isForSale) {
                listForSaleButton = this.addRenderableWidget(Button.builder(
                    Component.literal("List for Sale"),
                    btn -> showPriceInputUI()
                ).pos(centerX - buttonWidth / 2, buttonY).size(buttonWidth, 20).build());
            }

            // Remove from Sale button
            if (isForSale && canListForSale) {
                removeFromSaleButton = this.addRenderableWidget(Button.builder(
                    Component.literal("Remove Listing"),
                    btn -> removeFromSale()
                ).pos(centerX - buttonWidth / 2, buttonY).size(buttonWidth, 20).build());
                buttonY += 24;
            }

            // Buy button
            if (isForSale && canBuy) {
                String buyText = "Buy for " + EconomyManager.getInstance().formatCurrency(salePrice);
                buyButton = this.addRenderableWidget(Button.builder(
                    Component.literal(buyText),
                    btn -> purchaseChunk()
                ).pos(centerX - buttonWidth / 2, buttonY).size(buttonWidth, 20).build());
                buttonY += 24;
            }
        } else {
            // Price input mode
            priceInput = new EditBox(this.font, centerX - 50, buttonY, 100, 18, Component.literal("Price"));
            priceInput.setHint(Component.literal("Enter price..."));
            priceInput.setFilter(s -> s.isEmpty() || s.matches("[0-9.]*"));
            this.addRenderableWidget(priceInput);
            buttonY += 24;

            confirmPriceButton = this.addRenderableWidget(Button.builder(
                Component.literal("Confirm"),
                btn -> confirmListForSale()
            ).pos(centerX - 65, buttonY).size(60, 20).build());

            cancelPriceButton = this.addRenderableWidget(Button.builder(
                Component.literal("Cancel"),
                btn -> cancelPriceInput()
            ).pos(centerX + 5, buttonY).size(60, 20).build());
        }

        // Back button
        this.addRenderableWidget(Button.builder(
            Component.literal("Back"),
            btn -> openMarketplaceScreen()
        ).pos(centerX - 40, guiTop + guiHeight - 28).size(80, 20).build());
    }

    private void showPriceInputUI() {
        showPriceInput = true;
        buildUI();
    }

    private void cancelPriceInput() {
        showPriceInput = false;
        buildUI();
    }

    private void confirmListForSale() {
        if (priceInput == null) return;

        String priceText = priceInput.getValue().trim();
        if (priceText.isEmpty()) {
            return;
        }

        try {
            double price = Double.parseDouble(priceText);
            if (price <= 0) {
                return;
            }

            // Send list for sale packet
            NetworkHandler.sendToServer(new ChunkMarketPacket(
                ChunkMarketPacket.Action.LIST_FOR_SALE, chunkX, chunkZ, price));

            showPriceInput = false;
            // Server will send back updated info
        } catch (NumberFormatException e) {
            // Invalid price
        }
    }

    private void removeFromSale() {
        NetworkHandler.sendToServer(new ChunkMarketPacket(
            ChunkMarketPacket.Action.REMOVE_FROM_SALE, chunkX, chunkZ));
    }

    private void purchaseChunk() {
        NetworkHandler.sendToServer(new ChunkMarketPacket(
            ChunkMarketPacket.Action.PURCHASE, chunkX, chunkZ));
    }

    /**
     * Open the chunk marketplace screen via reflection (StateCraft mod)
     */
    private void openMarketplaceScreen() {
        try {
            Class<?> screenClass = Class.forName("com.statecraft.client.gui.ChunkMarketplaceScreen");
            var constructor = screenClass.getConstructor();
            var screen = constructor.newInstance();
            this.minecraft.setScreen((Screen) screen);
        } catch (ClassNotFoundException e) {
            // StateCraft not available, just close
            this.onClose();
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Error opening marketplace screen", e);
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Render panel background
        renderPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        // Render title row
        int startX = guiLeft + 10;
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 20, 0xAA808080);
        graphics.fill(startX, guiTop + 6, guiLeft + guiWidth - 10, guiTop + 7, 0xFF505050);
        graphics.fill(startX, guiTop + 19, guiLeft + guiWidth - 10, guiTop + 20, 0xFF404040);
        graphics.drawString(this.font, "Chunk Market", startX + 4, guiTop + 9, 0xFFFFFFFF);

        // Title icon ($ box for market)
        int iconX = guiLeft + guiWidth - 32;
        int iconY = guiTop + 6;
        graphics.fill(iconX, iconY, iconX + 22, iconY + 14, 0xFF2E8B57);
        graphics.drawCenteredString(this.font, "$", iconX + 11, iconY + 3, 0xFFFFFFFF);

        // Render loading or content
        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "Loading...", this.width / 2, guiTop + 80, 0xFFAAAAAA);
        } else {
            // Render menu entries
            for (MenuEntry entry : menuEntries) {
                int rowColor = entry.active ? 0xAA808080 : 0xAA606060;
                graphics.fill(startX, entry.y, guiLeft + guiWidth - 10, entry.y + 14, rowColor);
                graphics.fill(startX, entry.y, guiLeft + guiWidth - 10, entry.y + 1, 0xFF505050);
                graphics.fill(startX, entry.y + 13, guiLeft + guiWidth - 10, entry.y + 14, 0xFF404040);

                int textColor = entry.active ? 0xFFFFFFFF : 0xFFCCCCCC;
                graphics.drawString(this.font, entry.text, startX + 4, entry.y + 3, textColor);
            }

            // Price input label
            if (showPriceInput) {
                graphics.drawCenteredString(this.font, "Enter sale price:", this.width / 2, guiTop + guiHeight - 100, 0xFFFFFFFF);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Called by client packet handler when info is received
    public void updateMarketInfo(boolean isClaimed, boolean isForSale, double salePrice,
                                  String sellerName, String ownerName, String cityName,
                                  boolean isPrivatelyOwned, boolean canListForSale, boolean canBuy,
                                  double valuation, double estimatedTax) {
        this.isClaimed = isClaimed;
        this.isForSale = isForSale;
        this.salePrice = salePrice;
        this.sellerName = sellerName;
        this.ownerName = ownerName;
        this.cityName = cityName;
        this.isPrivatelyOwned = isPrivatelyOwned;
        this.canListForSale = canListForSale;
        this.canBuy = canBuy;
        this.valuation = valuation;
        this.estimatedTax = estimatedTax;
        this.dataLoaded = true;

        // Rebuild UI with new data
        buildUI();
    }

    private static class MenuEntry {
        final String text;
        final int y;
        final boolean active;

        MenuEntry(String text, int y, boolean active) {
            this.text = text;
            this.y = y;
            this.active = active;
        }
    }
}

