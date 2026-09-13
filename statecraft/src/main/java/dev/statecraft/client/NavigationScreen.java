package dev.statecraft.client;

import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class NavigationScreen extends Screen {
    private final MenuCategory category;
    private int offset;

    NavigationScreen() {
        this(null);
    }

    NavigationScreen(MenuCategory category) {
        super(Component.literal(category == null ? "StateCraft" : category.title()));
        this.category = category;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(560, width - 24);
        int left = (width - panelWidth) / 2;
        int tileWidth = (panelWidth - 8) / 2;
        int rows = Math.max(1, (height - 87) / 36);
        int capacity = rows * 2;
        int total;
        if (category == null) {
            List<MenuCategory> categories = Arrays.stream(MenuCategory.values())
                    .filter(group -> !group.pages(MenuRegistry.pages()).isEmpty()).toList();
            total = categories.size();
            offset = Math.min(offset, Math.max(0, total - capacity));
            for (int i = 0; i < capacity && i + offset < total; i++) {
                MenuCategory group = categories.get(i + offset);
                addRenderableWidget(Button.builder(Component.literal(group.title()),
                                ignored -> minecraft.setScreen(new NavigationScreen(group)))
                        .tooltip(Tooltip.create(Component.literal(group.description() + "\n"
                                + group.pages(MenuRegistry.pages()).size() + " sections")))
                        .bounds(left + (i % 2) * (tileWidth + 8), 44 + (i / 2) * 36, tileWidth, 30).build());
            }
        } else {
            List<MenuPage> pages = category.pages(MenuRegistry.pages());
            total = pages.size();
            offset = Math.min(offset, Math.max(0, total - capacity));
            for (int i = 0; i < capacity && i + offset < total; i++) {
                MenuPage page = pages.get(i + offset);
                addRenderableWidget(Button.builder(Component.literal(page.title()),
                                ignored -> minecraft.setScreen(new ManagementScreen(page)))
                        .tooltip(Tooltip.create(Component.literal(page.title() + "\n" + page.actions().size() + " actions")))
                        .bounds(left + (i % 2) * (tileWidth + 8), 44 + (i / 2) * 36, tileWidth, 30).build());
            }
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("Previous"), ignored -> {
            offset = Math.max(0, offset - capacity);
            rebuildWidgets();
        }).bounds(left, height - 28, 80, 20).build());
        previous.active = offset > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next"), ignored -> {
            offset = Math.min(Math.max(0, total - capacity), offset + capacity);
            rebuildWidgets();
        }).bounds(left + 84, height - 28, 80, 20).build());
        next.active = offset + capacity < total;
        addRenderableWidget(Button.builder(Component.literal(category == null ? "Close" : "All sections"), ignored -> onClose())
                .bounds(left + panelWidth - 100, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEC121923);
        graphics.drawCenteredString(font, title, width / 2, 11, 0x71D6C1);
        String subtitle = category == null ? "Choose an area to get started" : category.description();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(subtitle, width - 28), width / 2, 27, 0xB7C9D9);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(category == null ? null : new NavigationScreen());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
