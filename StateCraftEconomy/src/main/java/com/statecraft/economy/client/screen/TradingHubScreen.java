package com.statecraft.economy.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.statecraft.economy.StateCraftEconomy;
import com.statecraft.economy.config.ItemValueConfig;
import com.statecraft.economy.gui.TradingHubMenu;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.TradingHubSellPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Trading Hub GUI Screen
 * Allows players to sell items for currency
 */
public class TradingHubScreen extends AbstractContainerScreen<TradingHubMenu> {
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(StateCraftEconomy.MOD_ID, "textures/gui/trading_hub.png");

    // Using generic container texture as fallback
    private static final ResourceLocation FALLBACK_TEXTURE =
        new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private Button sellButton;
    private String statusMessage = "";
    private int statusMessageTicks = 0;
    private boolean statusSuccess = false;

    public TradingHubScreen(TradingHubMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Sell button
        sellButton = this.addRenderableWidget(Button.builder(
            Component.literal("Sell"),
            btn -> performSell()
        ).bounds(x + 110, y + 32, 50, 20).build());

        updateSellButton();
    }

    private void updateSellButton() {
        ItemStack sellStack = menu.getSlot(0).getItem();
        boolean canSell = !sellStack.isEmpty() && ItemValueConfig.canSell(sellStack);
        sellButton.active = canSell;
    }

    private void performSell() {
        ItemStack sellStack = menu.getSlot(0).getItem();
        if (sellStack.isEmpty() || !ItemValueConfig.canSell(sellStack)) {
            setStatusMessage("Nothing to sell!", false);
            return;
        }

        double value = menu.getCurrentSellValue();

        // Send sell packet to server
        NetworkHandler.sendToServer(new TradingHubSellPacket(menu.getBlockEntity().getBlockPos()));

        setStatusMessage(String.format("Sold for $%.2f!", value), true);
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

        // Draw background - use a dark rectangle since we may not have custom texture yet
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF8B8B8B);

        // Draw title area
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + 16, 0xFF3F3F3F);

        // Draw sell slot area (highlighted)
        int slotX = x + 79;
        int slotY = y + 34;
        graphics.fill(slotX - 1, slotY - 1, slotX + 18, slotY + 18, 0xFF373737);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);

        // Draw info area
        graphics.fill(x + 7, y + 55, x + 169, y + 78, 0xFF4A4A4A);

        // Draw player inventory area
        graphics.fill(x + 7, y + 83, x + 169, y + 141, 0xFF8B8B8B);
        graphics.fill(x + 7, y + 141, x + 169, y + 163, 0xFF8B8B8B);

        // Draw inventory slot backgrounds
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                int sx = x + 8 + col * 18;
                int sy = y + 84 + row * 18;
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            }
        }
        // Hotbar
        for (int col = 0; col < 9; ++col) {
            int sx = x + 8 + col * 18;
            int sy = y + 142;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title
        graphics.drawString(this.font, "Trading Hub", 8, 6, 0xFFFFFF, false);

        // Item info
        ItemStack sellStack = menu.getSlot(0).getItem();
        if (!sellStack.isEmpty()) {
            double perItem = ItemValueConfig.getItemValue(sellStack);
            double total = menu.getCurrentSellValue();
            double tax = menu.getCurrentTaxAmount();

            if (perItem > 0) {
                // Per item value
                graphics.drawString(this.font,
                    String.format("Per item: $%.2f", perItem),
                    10, 58, 0xAAAAAA, false);

                // Total value (after tax)
                String totalStr = String.format("Total: $%.2f", total);
                if (tax > 0) {
                    totalStr += String.format(" (tax: $%.2f)", tax);
                }
                graphics.drawString(this.font, totalStr, 10, 68, 0x55FF55, false);
            } else {
                graphics.drawString(this.font, "This item cannot be sold", 10, 63, 0xFF5555, false);
            }
        } else {
            graphics.drawString(this.font, "Insert items to sell", 10, 58, 0x888888, false);
            graphics.drawString(this.font, "Value shown before selling", 10, 68, 0x666666, false);
        }

        // Status message
        if (statusMessageTicks > 0 && !statusMessage.isEmpty()) {
            int color = statusSuccess ? 0x55FF55 : 0xFF5555;
            int msgWidth = this.font.width(statusMessage);
            graphics.drawString(this.font, statusMessage,
                (imageWidth - msgWidth) / 2, 22, color, false);
        }

        // Inventory label
        graphics.drawString(this.font, this.playerInventoryTitle, 8, 73, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}

