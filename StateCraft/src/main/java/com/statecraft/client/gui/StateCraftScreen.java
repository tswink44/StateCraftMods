package com.statecraft.client.gui;

import com.statecraft.StateCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Base screen class for all StateCraft GUIs
 * Provides common styling and utility methods
 */
public abstract class StateCraftScreen extends Screen {
    protected static final ResourceLocation BACKGROUND = new ResourceLocation(StateCraft.MOD_ID, "textures/gui/background.png");

    // Colors
    protected static final int COLOR_PRIMARY = 0xFF4A90D9;      // Blue
    protected static final int COLOR_SECONDARY = 0xFF2ECC71;    // Green
    protected static final int COLOR_WARNING = 0xFFE74C3C;      // Red
    protected static final int COLOR_TEXT = 0xFFFFFFFF;         // White
    protected static final int COLOR_TEXT_DARK = 0xFF333333;    // Dark gray
    protected static final int COLOR_BACKGROUND = 0xCC000000;   // Semi-transparent black
    protected static final int COLOR_PANEL = 0xDD1A1A2E;        // Dark blue panel
    protected static final int COLOR_BORDER = 0xFF3D3D5C;       // Border color

    // Layout
    protected int guiLeft;
    protected int guiTop;
    protected int guiWidth = 256;
    protected int guiHeight = 200;

    protected StateCraftScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - this.guiWidth) / 2;
        this.guiTop = (this.height - this.guiHeight) / 2;
    }


    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render dark background
        this.renderBackground(graphics);

        // Render panel background
        renderPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        // Render title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, guiTop + 8, COLOR_PRIMARY);

        // Render content
        renderContent(graphics, mouseX, mouseY, partialTick);

        // Render widgets (buttons, etc.)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Override this to render screen-specific content
     */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /**
     * Renders a styled panel with border
     */
    protected void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Main panel
        graphics.fill(x, y, x + width, y + height, COLOR_PANEL);

        // Border
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);                    // Top
        graphics.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);  // Bottom
        graphics.fill(x, y, x + 1, y + height, COLOR_BORDER);                   // Left
        graphics.fill(x + width - 1, y, x + width, y + height, COLOR_BORDER);   // Right
    }

    /**
     * Renders a smaller sub-panel
     */
    protected void renderSubPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xAA000000);
        graphics.fill(x, y, x + width, y + 1, 0x55FFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x55000000);
    }

    /**
     * Renders a horizontal divider line
     */
    protected void renderDivider(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, COLOR_BORDER);
    }

    /**
     * Renders a progress bar
     */
    protected void renderProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int color) {
        // Background
        graphics.fill(x, y, x + width, y + height, 0xFF333333);

        // Progress
        int progressWidth = (int) (width * Math.min(1.0f, Math.max(0.0f, progress)));
        if (progressWidth > 0) {
            graphics.fill(x, y, x + progressWidth, y + height, color);
        }

        // Border
        graphics.fill(x, y, x + width, y + 1, 0x55FFFFFF);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x55000000);
    }

    /**
     * Creates a styled button
     */
    protected Button createButton(int x, int y, int width, int height, Component text, Button.OnPress onPress) {
        return Button.builder(text, onPress)
            .pos(x, y)
            .size(width, height)
            .build();
    }

    /**
     * Creates a styled small button
     */
    protected Button createSmallButton(int x, int y, Component text, Button.OnPress onPress) {
        return createButton(x, y, 60, 16, text, onPress);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

