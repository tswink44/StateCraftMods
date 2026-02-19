package com.statecraft.economy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.statecraft.economy.block.entity.TradingHubBlockEntity;
import com.statecraft.economy.gui.TradingHubMenu;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.TradingHubSellPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Trading Hub GUI Screen - Chest-like interface for bulk selling items
 * Uses the standard single chest texture (generic_54.png) with modifications
 *
 * Layout (Y coordinates relative to GUI top):
 * - Title bar: 0-17
 * - Trading hub slots (3 rows): 17-71 (rows at Y=18, 36, 54)
 * - Info bar: 71-84 (13 pixels)
 * - Player inventory: 84-138 (rows at Y=84, 102, 120)
 * - Gap: 138-142
 * - Hotbar: 142-160
 * - Total height: 180 (71 + 13 + 96)
 */
public class TradingHubScreen extends AbstractContainerScreen<TradingHubMenu> {
    private static final ResourceLocation CONTAINER_BACKGROUND =
        new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private Button sellButton;
    private Button settingsButton;
    private String statusMessage = "";
    private int statusMessageTicks = 0;
    private boolean statusSuccess = false;

    public TradingHubScreen(TradingHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        // 71 (title + 3 rows) + 14 (info bar) + 97 (player inv section) = 182
        this.imageHeight = 182;
        // Inventory label Y position
        this.inventoryLabelY = 92;
    }

    @Override
    protected void init() {
        super.init();

        // Sell All button (top right, next to title)
        sellButton = this.addRenderableWidget(Button.builder(
            Component.literal("Sell All"),
            btn -> performSellAll()
        ).bounds(leftPos + imageWidth - 58, topPos + 4, 50, 12).build());

        // Settings button (gear icon next to sell button) - only show if can modify
        if (menu.canModifySettings()) {
            settingsButton = this.addRenderableWidget(Button.builder(
                Component.literal("⚙"),
                btn -> openSettings()
            ).bounds(leftPos + imageWidth - 72, topPos + 4, 12, 12).build());
        }

        updateSellButton();
    }

    private void updateSellButton() {
        int itemCount = menu.getSellableItemCount();
        sellButton.active = itemCount > 0;
    }

    private void performSellAll() {
        int itemCount = menu.getSellableItemCount();
        if (itemCount == 0) {
            setStatusMessage("Nothing to sell!", false);
            return;
        }

        double grossValue = menu.getCurrentSellValue();
        double taxAmount = menu.getCurrentTaxAmount();
        double netValue = grossValue - taxAmount;
        NetworkHandler.sendToServer(new TradingHubSellPacket(menu.getBlockEntity().getBlockPos()));
        setStatusMessage(String.format("Sold %d items for $%.2f!", itemCount, netValue), true);
    }

    private void openSettings() {
        Minecraft.getInstance().setScreen(new TradingHubSettingsScreen(this, menu.getBlockEntity()));
    }

    public void returnFromSettings() {
        Minecraft.getInstance().setScreen(this);
    }

    private void setStatusMessage(String message, boolean success) {
        this.statusMessage = message;
        this.statusMessageTicks = 60;
        this.statusSuccess = success;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateSellButton();
        if (statusMessageTicks > 0) {
            statusMessageTicks--;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // generic_54.png is 176x222:
        // - Y 0-17: title bar (17px)
        // - Y 17-125: 6 rows of container slots (108px)
        // - Y 125-222: player inventory section (97px)
        //
        // We need 3 rows (54px) + info bar (14px) + player inv (97px)

        // Draw title bar + 3 rows of slots (71 pixels from top of texture)
        graphics.blit(CONTAINER_BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 71);

        // Draw info bar area (custom fill between container and player inv)
        graphics.fill(leftPos, topPos + 71, leftPos + imageWidth, topPos + 85, 0xFFC6C6C6);
        graphics.fill(leftPos + 7, topPos + 73, leftPos + 169, topPos + 84, 0xFF4A4A4A);

        // Draw player inventory section (from texture Y=125, which is 97 pixels tall)
        graphics.blit(CONTAINER_BACKGROUND, leftPos, topPos + 85, 0, 125, imageWidth, 97);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title (truncate if too long)
        TradingHubBlockEntity be = menu.getBlockEntity();
        String ownerText = be.getOwnerName().isEmpty() ? "" : " (" + be.getOwnerName() + ")";
        String title = "Trading Hub" + ownerText;
        int maxTitleWidth = imageWidth - 80;
        if (this.font.width(title) > maxTitleWidth) {
            title = "Trading Hub";
        }
        graphics.drawString(this.font, title, 8, 6, 0x404040, false);

        // Info bar text (Y 73-84, center text vertically)
        int itemCount = menu.getSellableItemCount();
        double totalValue = menu.getCurrentSellValue();
        double taxAmount = menu.getCurrentTaxAmount();

        if (itemCount > 0) {
            String valueStr = String.format("%d items = $%.2f", itemCount, totalValue);
            if (taxAmount > 0) {
                valueStr += String.format(" (tax: $%.2f)", taxAmount);
            }
            graphics.drawString(this.font, valueStr, 10, 75, 0x55FF55, false);
        } else {
            graphics.drawString(this.font, "Insert items to sell", 10, 75, 0xAAAAAA, false);
        }

        // Status message (show in title area when selling)
        if (statusMessageTicks > 0 && !statusMessage.isEmpty()) {
            int color = statusSuccess ? 0x55FF55 : 0xFF5555;
            int msgWidth = this.font.width(statusMessage);
            graphics.drawString(this.font, statusMessage, (imageWidth - msgWidth) / 2, 6, color, false);
        }

        // Inventory label
        graphics.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}

