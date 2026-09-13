package dev.statecraft.client;

import dev.statecraft.client.state.UiPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class UiButton extends Button {
    private final boolean primary;
    private final UiPresentation.Icon icon;
    private int accent = UiTheme.ACCENT;
    private final boolean customTooltip;

    private UiButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.label, builder.press, DEFAULT_NARRATION);
        primary = builder.primary;
        icon = builder.icon;
        customTooltip = builder.tooltip != null;
        setTooltip(customTooltip ? builder.tooltip : Tooltip.create(builder.label));
    }

    public static Builder create(Component label, Button.OnPress press) { return new Builder(label, press); }

    public void accent(int color) { accent = color; }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        if (!customTooltip) setTooltip(Tooltip.create(message));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        boolean highlighted = isHoveredOrFocused();
        int fill = !active ? UiTheme.BACKGROUND : highlighted ? UiTheme.HOVER
                : primary ? 0xFF23453F : UiTheme.SURFACE;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, fill);
        int edge = active && (highlighted || primary) ? accent : UiTheme.BORDER;
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, edge);
        if (isFocused()) graphics.renderOutline(getX(), getY(), width, height, accent);
        if (primary) graphics.fill(getX(), getY(), getX() + 2, getY() + height, edge);
        int inset = icon == UiPresentation.Icon.NONE ? 5 : 29;
        if (icon != UiPresentation.Icon.NONE) UiTheme.icon(graphics, icon, getX() + 7, getY() + (height - 16) / 2);
        renderScrollingString(graphics, Minecraft.getInstance().font, getMessage(), getX() + inset, getY(),
                getX() + width - 5, getY() + height, active ? primary ? accent : UiTheme.TEXT : UiTheme.MUTED);
    }

    public static final class Builder {
        private final Component label;
        private final Button.OnPress press;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private boolean primary;
        private UiPresentation.Icon icon = UiPresentation.Icon.NONE;
        private Tooltip tooltip;

        private Builder(Component label, Button.OnPress press) {
            this.label = label;
            this.press = press;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            return this;
        }
        public Builder tooltip(Tooltip tooltip) { this.tooltip = tooltip; return this; }
        public Builder primary() { primary = true; return this; }
        public Builder icon(UiPresentation.Icon icon) { this.icon = icon; return this; }
        public UiButton build() { return new UiButton(this); }
    }
}
