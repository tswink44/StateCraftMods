package dev.statecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class GovernmentOverviewCard extends Button {
    private final Component caption;
    private final Component value;
    private final Component detail;
    private final ItemStack icon;
    private final boolean available;

    GovernmentOverviewCard(GovernmentOverviewLayout.Rect bounds, Component caption, Component value, Component detail,
                           ItemStack icon, boolean available, Component explanation, OnPress press) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                caption.copy().append(caption.getString().isBlank() ? "" : ": ").append(value)
                        .append(detail.getString().isBlank() ? "" : ". ").append(detail),
                press, DEFAULT_NARRATION);
        this.caption = caption;
        this.value = value;
        this.detail = detail;
        this.icon = icon;
        this.available = available;
        setTooltip(Tooltip.create(explanation));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        var font = Minecraft.getInstance().font;
        boolean highlighted = active && isHoveredOrFocused();
        int edge = highlighted ? UiTheme.ACCENT : UiTheme.BORDER;
        graphics.fill(getX(), getY(), getX() + width, getY() + height,
                !active ? UiTheme.BACKGROUND : highlighted ? UiTheme.HOVER : UiTheme.SURFACE);
        graphics.fill(getX(), getY(), getX() + 2, getY() + height, !active || available ? edge : UiTheme.WARNING);
        if (active && isFocused()) graphics.renderOutline(getX(), getY(), width, height, edge);
        int inset = icon.isEmpty() ? 8 : 30;
        if (!icon.isEmpty()) graphics.renderItem(icon, getX() + 8, getY() + (height - 16) / 2);
        int textWidth = Math.max(1, width - inset - 8);
        int y = getY() + (height - (caption.getString().isBlank() ? 22 : 34)) / 2;
        if (!caption.getString().isBlank()) {
            graphics.drawString(font, fit(caption, textWidth), getX() + inset, y, UiTheme.MUTED, false);
            y += 12;
        }
        graphics.drawString(font, fit(value, textWidth), getX() + inset, y,
                !active ? UiTheme.MUTED : available ? UiTheme.TEXT : UiTheme.WARNING, false);
        if (!detail.getString().isBlank()) {
            graphics.drawString(font, fit(detail, textWidth), getX() + inset, y + 12, UiTheme.MUTED, false);
        }
    }

    private static String fit(Component text, int width) {
        var font = Minecraft.getInstance().font;
        String value = text.getString();
        return font.width(value) <= width ? value
                : font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }
}
