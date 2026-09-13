package dev.statecraft.client;

import dev.statecraft.api.HelpNavigation;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.client.state.UiPresentation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

final class HelpScreen extends Screen {
    HelpScreen() {
        super(ClientText.tr("gui.statecraft.help.title", "Help"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(460, width - 32);
        int left = (width - panelWidth) / 2;
        Button commands = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.help.commands", "Commands"),
                        ignored -> ClientHooks.navigate(UiQuery.page(HelpNavigation.COMMANDS)))
                .bounds(left, 60, panelWidth, 48).primary().icon(UiPresentation.Icon.BOOK)
                .tooltip(Tooltip.create(ClientText.tr("gui.statecraft.help.commands_hint", "Browse command help and examples."))).build());
        commands.active = HelpNavigation.available(MenuRegistry.pages(), HelpNavigation.COMMANDS);
        Button recipes = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.help.recipes", "Recipes"),
                        ignored -> ClientHooks.navigate(UiQuery.page(HelpNavigation.RECIPES)))
                .bounds(left, 122, panelWidth, 48).icon(UiPresentation.Icon.PAPER).build());
        recipes.active = HelpNavigation.available(MenuRegistry.pages(), HelpNavigation.RECIPES);
        recipes.setTooltip(Tooltip.create(recipes.active
                ? ClientText.tr("gui.statecraft.help.recipes_hint", "View crafting grids, ingredients and outputs.")
                : ClientText.tr("gui.statecraft.help.recipes_unavailable", "Install StateCraft Economy to view its crafting recipes.")));
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left, height - 28, 90, 20).build());
        setInitialFocus(commands);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 12);
        graphics.drawCenteredString(font, ClientText.tr("gui.statecraft.help.choose", "Command help and crafting recipes"),
                width / 2, 34, UiTheme.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { ClientHooks.back(); }
    @Override
    public boolean isPauseScreen() { return false; }
}
