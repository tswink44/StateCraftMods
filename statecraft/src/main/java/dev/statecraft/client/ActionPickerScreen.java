package dev.statecraft.client;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.CommandLine;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ActionPickerScreen extends Screen {
    private final ManagementScreen parent;
    private final MenuPage page;
    private String search = "";
    private int offset;

    ActionPickerScreen(ManagementScreen parent, MenuPage page) {
        super(Component.literal(page.title() + " - Actions"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        int left = Math.max(12, width / 2 - 170);
        int listWidth = Math.min(340, width - 24);
        EditBox filter = new EditBox(font, left, 32, listWidth, 20, Component.literal("Find an action"));
        filter.setHint(Component.literal("Find an action..."));
        filter.setValue(search);
        filter.setResponder(value -> {
            search = value;
            offset = 0;
            rebuildWidgets();
        });
        addRenderableWidget(filter);
        setInitialFocus(filter);
        List<MenuPage.Action> available = page.actions().stream()
                .filter(action -> (action.label() + " " + action.command()).toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT))).toList();
        int count = Math.max(1, (height - 96) / 24);
        offset = Math.min(offset, Math.max(0, available.size() - count));
        for (int i = 0; i < count && i + offset < available.size(); i++) {
            MenuPage.Action action = available.get(i + offset);
            addRenderableWidget(Button.builder(Component.literal(action.label()), ignored -> {
                        List<String> words = CommandLine.split(action.command());
                        if (words.size() == 2 && words.get(0).equals("gui")) {
                            ClientHooks.open(words.get(1));
                        } else {
                            minecraft.setScreen(new ActionFormScreen(parent, this, action));
                        }
                    })
                    .tooltip(Tooltip.create(Component.literal(action.command())))
                    .bounds(left, 60 + i * 24, listWidth, 20).build());
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("Previous"), ignored -> {
            offset = Math.max(0, offset - count);
            rebuildWidgets();
        }).bounds(left, height - 28, 80, 20).build());
        previous.active = offset > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next"), ignored -> {
            offset = Math.min(Math.max(0, available.size() - count), offset + count);
            rebuildWidgets();
        }).bounds(left + 84, height - 28, 80, 20).build());
        next.active = offset + count < available.size();
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(left + listWidth - 80, height - 28, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xE8121923);
        graphics.drawCenteredString(font, title, width / 2, 13, 0x71D6C1);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
