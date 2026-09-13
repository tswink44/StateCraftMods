package dev.statecraft.client;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.ResultSelection;
import dev.statecraft.network.SuiteNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

public final class ManagementScreen extends Screen {
    private static final int SIDEBAR = 126;
    private final MenuPage page;
    private final MenuCategory category;
    private String output = "Loading...";
    private boolean success = true;
    private boolean loaded;
    private int pending = -1;
    private int navigationOffset;
    private int scroll;
    private List<FormattedCharSequence> lines = List.of();
    private List<String> rowSources = List.of();
    private String selectedRow = "";
    private String copied = "";
    private EditBox command;
    private Button run;
    private Button refresh;
    private Button actions;
    private long sentAt;

    public ManagementScreen(MenuPage page) {
        super(Component.literal(page.title()));
        this.page = page;
        category = MenuCategory.of(page.id());
        List<MenuPage> pages = category.pages(MenuRegistry.pages());
        int index = pages.indexOf(page);
        navigationOffset = Math.max(0, index - 4);
    }

    @Override
    protected void init() {
        List<MenuPage> pages = category.pages(MenuRegistry.pages());
        int visible = Math.max(1, (height - 118) / 22);
        addRenderableWidget(Button.builder(Component.literal("All sections"),
                        ignored -> minecraft.setScreen(new NavigationScreen()))
                .bounds(8, 8, SIDEBAR - 12, 20).build());
        navigationOffset = Math.min(navigationOffset, Math.max(0, pages.size() - visible));
        for (int i = 0; i < visible && i + navigationOffset < pages.size(); i++) {
            MenuPage entry = pages.get(i + navigationOffset);
            String name = font.plainSubstrByWidth(entry.title(), SIDEBAR - 20);
            Button button = addRenderableWidget(Button.builder(Component.literal(name),
                    ignored -> minecraft.setScreen(new ManagementScreen(entry)))
                    .bounds(8, 49 + i * 22, SIDEBAR - 12, 20).build());
            button.active = !entry.id().equals(page.id());
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("^"), ignored -> {
            navigationOffset = Math.max(0, navigationOffset - visible);
            rebuildWidgets();
        }).bounds(8, height - 61, 52, 20).build());
        previous.active = navigationOffset > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("v"), ignored -> {
            navigationOffset = Math.min(Math.max(0, pages.size() - visible), navigationOffset + visible);
            rebuildWidgets();
        }).bounds(64, height - 61, 50, 20).build());
        next.active = navigationOffset + visible < pages.size();
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(8, height - 28, SIDEBAR - 12, 20).build());

        int contentX = SIDEBAR + 8;
        int contentWidth = Math.max(100, width - contentX - 12);
        int toolbarWidth = Math.min(64, (contentWidth - 8) / 3);
        refresh = addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> submit(page.query()))
                .bounds(contentX, height - 61, toolbarWidth, 20).build());
        actions = addRenderableWidget(Button.builder(Component.literal("Actions"), ignored ->
                        minecraft.setScreen(new ActionPickerScreen(this, page)))
                .bounds(contentX + toolbarWidth + 4, height - 61, toolbarWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Map"), ignored -> minecraft.setScreen(new TerritoryMapScreen(this)))
                .bounds(contentX + (toolbarWidth + 4) * 2, height - 61, toolbarWidth, 20).build());

        String oldCommand = command == null ? "" : command.getValue();
        command = new EditBox(font, contentX, height - 28, contentWidth - 46, 20, Component.literal("Command"));
        command.setMaxLength(4096);
        command.setHint(Component.literal("Command without /sc or /sce"));
        command.setValue(oldCommand);
        addRenderableWidget(command);
        run = addRenderableWidget(Button.builder(Component.literal("Run"), ignored -> submit(command.getValue()))
                .bounds(width - 54, height - 28, 42, 20).build());
        layoutOutput();
        updateButtons();
        if (!loaded) {
            loaded = true;
            submit(page.query());
        }
    }

    MenuPage page() { return page; }

    public void submit(String line) {
        if (line.isBlank() || pending >= 0) {
            return;
        }

        pending = ClientHooks.nextRequest();
        ClientHooks.watch(pending, this);
        sentAt = System.currentTimeMillis();
        output = "Waiting for server...";
        success = true;
        scroll = 0;
        layoutOutput();
        updateButtons();
        SuiteNetwork.request(pending, page.id(), line);
    }

    public void reply(SuiteNetwork.ActionResponse response) {
        if (!response.page().equals(page.id()) || response.id() != pending) {
            return;
        }
        pending = -1;
        displayResult(response.success(), response.text());
    }

    void displayResult(boolean successful, String text) {
        output = text;
        success = successful;
        scroll = 0;
        layoutOutput();
        updateButtons();
    }

    private void updateButtons() {
        if (run != null) {
            run.active = pending < 0;
            refresh.active = pending < 0;
            actions.active = pending < 0;
        }
    }

    private void layoutOutput() {
        if (font != null) {
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            for (String row : output.split("\\R", -1)) {
                List<FormattedCharSequence> parts = font.split(Component.literal(row), Math.max(90, width - SIDEBAR - 28));
                if (parts.isEmpty()) {
                    wrapped.add(FormattedCharSequence.EMPTY);
                    sources.add(row);
                } else {
                    wrapped.addAll(parts);
                    for (int i = 0; i < parts.size(); i++) {
                        sources.add(row);
                    }
                }
            }
            lines = List.copyOf(wrapped);
            rowSources = List.copyOf(sources);
            scroll = Math.min(scroll, Math.max(0, lines.size() - visibleLines()));
        }
    }

    private int visibleLines() {
        return Math.max(1, (height - 113) / 11);
    }

    @Override
    public void tick() {
        command.tick();
        if (pending >= 0 && System.currentTimeMillis() - sentAt > 15_000) {
            ClientHooks.forget(pending);
            pending = -1;
            output = "The server has not replied. The action may have completed; refresh before retrying a payment or purchase.";
            success = false;
            layoutOutput();
            updateButtons();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xE8121923);
        graphics.fill(0, 0, SIDEBAR, height, 0xEA1A2836);
        graphics.fill(SIDEBAR + 4, 31, width - 8, height - 69, 0xB5091018);
        graphics.drawString(font, font.plainSubstrByWidth(category.title(), SIDEBAR - 14),
                8, 35, 0x71D6C1, false);
        graphics.drawString(font, font.plainSubstrByWidth(page.title(), width - SIDEBAR - 20),
                SIDEBAR + 8, 13, 0xF1F5FB, false);
        graphics.enableScissor(SIDEBAR + 6, 33, width - 10, height - 72);
        for (int i = 0; i < visibleLines() && i + scroll < lines.size(); i++) {
            if (!selectedRow.isEmpty() && rowSources.get(i + scroll).equals(selectedRow)) {
                graphics.fill(SIDEBAR + 7, 37 + i * 11, width - 12, 48 + i * 11, 0x554D819A);
            }
            graphics.drawString(font, lines.get(i + scroll), SIDEBAR + 10, 38 + i * 11,
                    success ? 0xDFE9F2 : 0xFF9292, false);
        }
        graphics.disableScissor();
        if (!copied.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth("Copied. Ctrl+V to paste.", width - SIDEBAR - 80),
                    SIDEBAR + 8, height - 80, 0x71D6C1, false);
        }
        if (lines.size() > visibleLines()) {
            graphics.drawString(font, (scroll + 1) + "/" + lines.size(), width - 54, height - 81, 0x8398AB, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= SIDEBAR && mouseY < height - 66) {
            scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visibleLines()), scroll - (int) Math.signum(delta) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && mouseX >= SIDEBAR + 6 && mouseX < width - 10
                && mouseY >= 38 && mouseY < Math.min(height - 81, 38 + visibleLines() * 11)) {
            int row = scroll + (int) ((mouseY - 38) / 11);
            if (row < rowSources.size() && !rowSources.get(row).isBlank()) {
                selectedRow = rowSources.get(row);
                copied = button == 0 ? ResultSelection.identifier(selectedRow) : selectedRow;
                minecraft.keyboardHandler.setClipboard(copied);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && command.isFocused()) {
            submit(command.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { minecraft.setScreen(new NavigationScreen(category)); }
}
