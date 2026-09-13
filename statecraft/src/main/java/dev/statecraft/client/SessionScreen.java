package dev.statecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

final class SessionScreen extends Screen {
    SessionScreen() { super(ClientText.tr("gui.statecraft.session.title", "Connecting to StateCraft")); }
    @Override
    protected void init() {
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.session.check", "Check connection"),
                ignored -> ClientHooks.refreshSession()).bounds(width / 2 - 140, height - 28, 136, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.close", "Close"), ignored -> onClose())
                .bounds(width / 2 + 4, height - 28, 136, 20).build());
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 24, 0x71D6C1);
        int y = 52;
        for (var line : font.split(ClientText.tr("gui.statecraft.session.waiting",
                "Waiting for the server world identity."), width - 32)) {
            graphics.drawString(font, line, 16, y, 0xDFE9F2, false);
            y += 12;
        }
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { ClientHooks.cancelOpening(); minecraft.setScreen(null); }
    @Override
    public boolean isPauseScreen() { return false; }
}
