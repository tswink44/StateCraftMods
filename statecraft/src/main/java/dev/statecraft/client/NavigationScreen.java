package dev.statecraft.client;

import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.client.state.NavigationState;
import dev.statecraft.client.state.UiPresentation;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

final class NavigationScreen extends Screen {
    private final NavigationState.Location location;
    private final MenuCategory category;
    private Button attention;
    private Button dashboard;
    private int headerWidth;
    private int headerTileWidth;

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
        headerWidth = panelWidth;
        headerTileWidth = tileWidth;
        dashboard = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.dashboard", "My dashboard"),
                        ignored -> ClientHooks.navigate(UiQuery.page("statecraft:dashboard")))
                .tooltip(Tooltip.create(ClientText.tr("gui.statecraft.dashboard.hint",
                        "Your citizenship, wallet and bank deposits, company shares, privately owned property and personal inbox.")))
                .bounds(left, 44, tileWidth, 26).primary().icon(UiPresentation.Icon.PLAYER).build());
        attention = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.attention", "Attention"),
                        ignored -> ClientHooks.operations(this))
                .tooltip(Tooltip.create(ClientText.tr("gui.statecraft.attention_hint", "Check a pending action before repeating it.")))
                .bounds(left + tileWidth + 8, 44, tileWidth, 26).icon(UiPresentation.Icon.OPERATION).build());
        updateAttention();
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
                addRenderableWidget(UiButton.create(ClientText.category(group), ignored -> ClientHooks.sections(group))
                        .tooltip(Tooltip.create(ClientText.description(group)))
                        .bounds(left + (i % 2) * (tileWidth + 8), 80 + (i / 2) * 34, tileWidth, 28)
                        .icon(UiPresentation.categoryIcon(group)).build());
            }
        } else {
            List<MenuPage> pages = category.pages(MenuRegistry.pages());
            total = pages.size();
            location.offset(Math.min(location.offset(), Math.max(0, (total - 1) / capacity * capacity)));
            for (int i = 0; i < capacity && i + location.offset() < total; i++) {
                MenuPage page = pages.get(i + location.offset());
                addRenderableWidget(UiButton.create(ClientText.page(page),
                                ignored -> ClientHooks.navigate(UiQuery.page(page.id())))
                        .bounds(left + (i % 2) * (tileWidth + 8), 80 + (i / 2) * 34, tileWidth, 28)
                        .icon(UiPresentation.categoryIcon(category)).build());
            }
        }
        int buttonWidth = (panelWidth - 12) / 4;
        Button previous = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.previous", "Previous"), ignored -> {
            location.offset(location.offset() - capacity);
            rebuildWidgets();
        }).bounds(left, height - 28, buttonWidth, 20).build());
        previous.active = location.offset() > 0;
        Button next = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.next", "Next"), ignored -> {
            location.offset(location.offset() + capacity);
            rebuildWidgets();
        }).bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        next.active = location.offset() + capacity < total;
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.close", "Close"), ignored -> minecraft.setScreen(null))
                .bounds(left + (buttonWidth + 4) * 3, height - 28, buttonWidth, 20).build());
    }
    @Override
    public void tick() {
        updateAttention();
    }
    private void updateAttention() {
        attention.visible = ClientHooks.needsAttention();
        attention.active = attention.visible;
        dashboard.setWidth(attention.visible ? headerTileWidth : headerWidth);
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 10);
        var subtitle = category == null ? ClientText.tr("gui.statecraft.navigation.choose", "Choose an area to get started")
                : ClientText.description(category);
        graphics.drawString(font, font.plainSubstrByWidth(subtitle.getString(), width - 28), 12, 26, UiTheme.MUTED, false);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { ClientHooks.back(); }
    @Override
    public boolean isPauseScreen() { return false; }
}
