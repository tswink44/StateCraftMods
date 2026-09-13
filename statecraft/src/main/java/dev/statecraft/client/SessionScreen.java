package dev.statecraft.client;

import dev.statecraft.client.state.UiPresentation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

final class SessionScreen extends Screen {
    SessionScreen() { super(ClientText.tr("gui.statecraft.session.title", "Connecting to StateCraft")); }
    @Override
    protected void init() {
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.session.check", "Check connection"),
                ignored -> ClientHooks.refreshSession()).bounds(width / 2 - 140, height - 28, 136, 20).primary().build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.close", "Close"), ignored -> onClose())
                .bounds(width / 2 + 4, height - 28, 136, 20).build());
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 16);
        graphics.fill(width / 2 - 24, 48, width / 2 + 24, 96, UiTheme.SURFACE);
        UiTheme.icon(graphics, UiPresentation.Icon.PLAYER, width / 2 - 8, 64);
        int y = 112;
        for (var line : font.split(ClientText.tr("gui.statecraft.session.waiting",
                "Waiting for the server world identity."), width - 32)) {
            graphics.drawString(font, line, 16, y, UiTheme.MUTED, false);
            y += 12;
        }
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { ClientHooks.cancelOpening(); minecraft.setScreen(null); }
    @Override
    public boolean isPauseScreen() { return false; }
}
