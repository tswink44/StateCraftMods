package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import net.minecraft.client.gui.GuiGraphics;
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
        panel.content(TextPanel.Entry.paragraphs(body), scroll, EntityRef.NONE, "");
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.copy_all", "Copy all"), ignored -> panel.copyAll())
                .bounds(12, height - 28, (width - 28) / 2, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(16 + (width - 28) / 2, height - 28, (width - 28) / 2, 20).build());
        setInitialFocus(panel);
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(body); }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 12);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { minecraft.setScreen(previous); }
    @Override
    public boolean isPauseScreen() { return false; }
}
