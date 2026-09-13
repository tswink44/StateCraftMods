package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class InformationScreen extends Screen {
    private final Screen previous;
    private final Component body;
    private TextPanel panel;
    private int scroll;

    InformationScreen(Screen previous, Component title, Component body) {
        super(title);
        this.previous = previous;
        this.body = body;
    }
    @Override
    protected void init() {
        panel = addRenderableWidget(new TextPanel(font, 12, 34, width - 24, height - 70,
                ignored -> {}, ignored -> {}, value -> scroll = value));
        panel.content(List.of(TextPanel.Entry.text(body)), scroll, EntityRef.NONE, "");
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.copy_all", "Copy all"), ignored -> panel.copyAll())
                .bounds(12, height - 28, (width - 28) / 2, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(16 + (width - 28) / 2, height - 28, (width - 28) / 2, 20).build());
        setInitialFocus(panel);
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(body); }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEF121923);
        graphics.drawCenteredString(font, title, width / 2, 12, 0x71D6C1);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { minecraft.setScreen(previous); }
    @Override
    public boolean isPauseScreen() { return false; }
}
