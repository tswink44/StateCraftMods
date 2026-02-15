package com.statecraft.economy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.statecraft.economy.StateCraftEconomy;
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
 * Features:
 * - 27 slot inventory (like a single chest)
 * - Settings button (opens settings screen)
 * - Sell All button to sell entire inventory
 * - Real-time value display
 */
public class TradingHubScreen extends AbstractContainerScreen<TradingHubMenu> {
    // Use generic chest texture as base
    private static final ResourceLocation CONTAINER_BACKGROUND =
        new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private Button sellButton;
    private Button settingsButton;
    private String statusMessage = "";
    private int statusMessageTicks = 0;
    private boolean statusSuccess = false;

    public TradingHubScreen(TradingHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Same dimensions as a single chest (3 rows)
        this.imageWidth = 176;
        this.imageHeight = 166;
        // Adjust label positions
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Sell All button (bottom right of trading hub inventory)
        sellButton = this.addRenderableWidget(Button.builder(
            Component.literal("Sell All"),
            btn -> performSellAll()
        ).bounds(x + imageWidth - 58, y + 4, 50, 12).build());

        // Settings button (gear icon next to sell button) - only show if can modify
        if (menu.canModifySettings()) {
            settingsButton = this.addRenderableWidget(Button.builder(
                Component.literal("⚙"),
                btn -> openSettings()
            ).bounds(x + imageWidth - 72, y + 4, 14, 12).build());
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

        double value = menu.getCurrentSellValue();

        // Send sell packet to server
        NetworkHandler.sendToServer(new TradingHubSellPacket(menu.getBlockEntity().getBlockPos()));

        setStatusMessage(String.format("Sold %d items for $%.2f!", itemCount, value), true);
    }

    private void openSettings() {
        // Open the settings screen
        Minecraft.getInstance().setScreen(new TradingHubSettingsScreen(this, menu.getBlockEntity()));
    }

    /**
     * Return to main trading hub screen from settings
     */
    public void returnFromSettings() {
        Minecraft.getInstance().setScreen(this);
    }

    private void setStatusMessage(String message, boolean success) {
        this.statusMessage = message;
        this.statusMessageTicks = 60; // Show for 3 seconds
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
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Draw chest-like background (modified for 3 rows)
        // Top part (title area)
        graphics.blit(CONTAINER_BACKGROUND, x, y, 0, 0, imageWidth, 17);

        // Container slots (3 rows)
        graphics.blit(CONTAINER_BACKGROUND, x, y + 17, 0, 17, imageWidth, 54);

        // Gap between container and player inventory
        graphics.fill(x, y + 71, x + imageWidth, y + 83, 0xFFC6C6C6);

        // Player inventory section
        graphics.blit(CONTAINER_BACKGROUND, x, y + 83, 0, 126, imageWidth, 96);

        // Draw info bar background (between trading hub slots and player inventory)
        graphics.fill(x + 7, y + 72, x + 169, y + 82, 0xFF4A4A4A);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title
        TradingHubBlockEntity be = menu.getBlockEntity();
        String ownerText = be.getOwnerName().isEmpty() ? "" : " (" + be.getOwnerName() + ")";
        graphics.drawString(this.font, "Trading Hub" + ownerText, 8, 6, 0x404040, false);

        // Value summary in info bar
        int itemCount = menu.getSellableItemCount();
        double totalValue = menu.getCurrentSellValue();
        double taxAmount = menu.getCurrentTaxAmount();

        if (itemCount > 0) {
            String valueStr = String.format("%d items = $%.2f", itemCount, totalValue);
            if (taxAmount > 0) {
                valueStr += String.format(" (tax: $%.2f)", taxAmount);
            }
            graphics.drawString(this.font, valueStr, 10, 74, 0x55FF55, false);
        } else {
            graphics.drawString(this.font, "Insert items to sell", 10, 74, 0xAAAAAA, false);
        }

        // Status message (centered above player inventory)
        if (statusMessageTicks > 0 && !statusMessage.isEmpty()) {
            int color = statusSuccess ? 0x55FF55 : 0xFF5555;
            int msgWidth = this.font.width(statusMessage);
            graphics.drawString(this.font, statusMessage,
                (imageWidth - msgWidth) / 2, 62, color, false);
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

