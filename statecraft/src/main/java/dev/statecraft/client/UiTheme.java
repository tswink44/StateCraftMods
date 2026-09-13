package dev.statecraft.client;

import dev.statecraft.client.state.UiPresentation;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UiTheme {
    /** Opaque ARGB colors, also accepted by Minecraft's text renderer. */
    public static final int BACKGROUND = 0xFF111920;
    public static final int SURFACE = 0xFF1C2832;
    public static final int HOVER = 0xFF293B47;
    public static final int TEXT = 0xFFE5EDF1;
    public static final int MUTED = 0xFFA6BBC5;
    public static final int ACCENT = 0xFF7BD5BA;
    public static final int BORDER = 0xFF405562;
    public static final int POSITIVE = 0xFF92D7A0;
    public static final int NEGATIVE = 0xFFFF9996;
    public static final int WARNING = 0xFFF0CE83;
    private static final EnumMap<UiPresentation.Icon, ItemStack> ICONS = new EnumMap<>(UiPresentation.Icon.class);

    private UiTheme() {}

    public static void background(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, 2, ACCENT);
    }

    public static void header(GuiGraphics graphics, Component title, int width, int y) {
        var font = Minecraft.getInstance().font;
        graphics.fill(12, y, 15, y + font.lineHeight, ACCENT);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, width - 38)), 21, y, TEXT, false);
    }

    public static int color(UiPresentation.Tone tone) {
        return switch (tone) {
            case NORMAL -> TEXT;
            case MUTED -> MUTED;
            case ACCENT -> ACCENT;
            case POSITIVE -> POSITIVE;
            case NEGATIVE -> NEGATIVE;
            case WARNING -> WARNING;
        };
    }

    public static void status(Button button, Component detail) {
        String line = detail.getString().split("\\R", 2)[0];
        String caption = line.length() <= 80 ? line : line.substring(0, 77) + "...";
        button.setMessage(ClientText.tr("gui.statecraft.status.details", "%s · Details", caption));
        button.setTooltip(Tooltip.create(detail));
    }

    public static void icon(GuiGraphics graphics, UiPresentation.Icon icon, int x, int y) {
        if (icon == UiPresentation.Icon.NONE) return;
        ItemStack stack = ICONS.computeIfAbsent(icon, value -> new ItemStack(switch (value) {
            case GOVERNMENT -> Items.LIME_BANNER;
            case COMPANY -> Items.CHEST;
            case CLAIM -> Items.BRICKS;
            case BALLOT -> Items.MAP;
            case PAPER -> Items.PAPER;
            case BOOK -> Items.BOOK;
            case HANDSHAKE -> Items.WRITABLE_BOOK;
            case MAIL -> Items.WRITTEN_BOOK;
            case ACCOUNT -> Items.EMERALD;
            case BANK -> Items.GOLD_INGOT;
            case LOAN -> Items.IRON_INGOT;
            case TRADE -> Items.EMERALD_BLOCK;
            case DELIVERY -> Items.BARREL;
            case OPERATION -> Items.CLOCK;
            case PLAYER -> Items.COMPASS;
            case NONE -> Items.AIR;
        }));
        graphics.renderItem(stack, x, y);
    }
}
