package dev.statecraft.client;

import dev.statecraft.api.UserError;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CustomValueScreen extends Screen {
    private final Screen previous;
    private final ActionFormScreen owner;
    private final String key;
    private String value;
    private String error = "";

    CustomValueScreen(Screen previous, ActionFormScreen owner, String key, String value) {
        super(Component.literal(owner.field(key).label() + " - Custom value"));
        this.previous = previous;
        this.owner = owner;
        this.key = key;
        this.value = value;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(440, width - 24);
        int left = (width - panelWidth) / 2;
        MultiLineEditBox input = new MultiLineEditBox(font, left, 55, panelWidth, Math.max(40, height - 110),
                Component.literal("Enter a custom value"), Component.literal(owner.field(key).label()));
        input.setCharacterLimit(2048);
        input.setValue(value);
        input.setValueListener(text -> value = text);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Use value"), ignored -> {
            try {
                owner.selectedCustom(key, value.strip());
            } catch (UserError invalid) {
                error = invalid.getMessage();
            }
        }).bounds(left, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(left + panelWidth - 100, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEF121923);
        graphics.drawCenteredString(font, title, width / 2, 12, 0x71D6C1);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(owner.field(key).hint(), width - 24),
                width / 2, 34, 0xA8BECE);
        if (!error.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(error, width - 24), 12, height - 43, 0xFF9292, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(previous); }
    @Override
    public boolean isPauseScreen() { return false; }
}
