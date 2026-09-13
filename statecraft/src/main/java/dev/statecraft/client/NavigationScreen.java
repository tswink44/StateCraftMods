package dev.statecraft.client;

import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.client.state.NavigationState;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

final class NavigationScreen extends Screen {
    private final NavigationState.Location location;
    private final MenuCategory category;
    private Button operations;

    NavigationScreen(NavigationState.Location location) {
        super(location.category().isEmpty() ? ClientText.tr("gui.statecraft.navigation.title", "StateCraft")
                : ClientText.category(MenuCategory.valueOf(location.category())));
        this.location = location;
        category = location.category().isEmpty() ? null : MenuCategory.valueOf(location.category());
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(560, width - 24);
        int left = (width - panelWidth) / 2;
        int tileWidth = (panelWidth - 8) / 2;
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.dashboard", "My dashboard"),
                        ignored -> ClientHooks.navigate(UiQuery.page("statecraft:dashboard")))
                .tooltip(Tooltip.create(ClientText.tr("gui.statecraft.dashboard.hint",
                        "Role-aware pending work, invitations, approvals, bills, loans and unread mail.")))
                .bounds(left, 44, tileWidth, 26).build());
        operations = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.count", "Operations (%s)",
                        ClientHooks.pendingCount()), ignored -> ClientHooks.operations(this))
                .bounds(left + tileWidth + 8, 44, tileWidth, 26).build());
        int rows = Math.max(1, (height - 116) / 34);
        int capacity = rows * 2;
        int total;
        if (category == null) {
            List<MenuCategory> categories = Arrays.stream(MenuCategory.values())
                    .filter(group -> !group.pages(MenuRegistry.pages()).isEmpty()).toList();
            total = categories.size();
            location.offset(Math.min(location.offset(), Math.max(0, (total - 1) / capacity * capacity)));
            for (int i = 0; i < capacity && i + location.offset() < total; i++) {
                MenuCategory group = categories.get(i + location.offset());
                addRenderableWidget(Button.builder(ClientText.category(group), ignored -> ClientHooks.sections(group))
                        .tooltip(Tooltip.create(ClientText.description(group)))
                        .bounds(left + (i % 2) * (tileWidth + 8), 80 + (i / 2) * 34, tileWidth, 28).build());
            }
        } else {
            List<MenuPage> pages = category.pages(MenuRegistry.pages());
            total = pages.size();
            location.offset(Math.min(location.offset(), Math.max(0, (total - 1) / capacity * capacity)));
            for (int i = 0; i < capacity && i + location.offset() < total; i++) {
                MenuPage page = pages.get(i + location.offset());
                addRenderableWidget(Button.builder(ClientText.page(page),
                                ignored -> ClientHooks.navigate(UiQuery.page(page.id())))
                        .bounds(left + (i % 2) * (tileWidth + 8), 80 + (i / 2) * 34, tileWidth, 28).build());
            }
        }
        int buttonWidth = (panelWidth - 12) / 4;
        Button previous = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.previous", "Previous"), ignored -> {
            location.offset(location.offset() - capacity);
            rebuildWidgets();
        }).bounds(left, height - 28, buttonWidth, 20).build());
        previous.active = location.offset() > 0;
        Button next = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.next", "Next"), ignored -> {
            location.offset(location.offset() + capacity);
            rebuildWidgets();
        }).bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        next.active = location.offset() + capacity < total;
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.close", "Close"), ignored -> minecraft.setScreen(null))
                .bounds(left + (buttonWidth + 4) * 3, height - 28, buttonWidth, 20).build());
    }
    @Override
    public void tick() {
        operations.setMessage(ClientText.tr("gui.statecraft.operations.count", "Operations (%s)", ClientHooks.pendingCount()));
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEC121923);
        graphics.drawCenteredString(font, title, width / 2, 10, 0x71D6C1);
        var subtitle = category == null ? ClientText.tr("gui.statecraft.navigation.choose", "Choose an area to get started")
                : ClientText.description(category);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(subtitle.getString(), width - 28), width / 2, 26, 0xB7C9D9);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { ClientHooks.back(); }
    @Override
    public boolean isPauseScreen() { return false; }
}
