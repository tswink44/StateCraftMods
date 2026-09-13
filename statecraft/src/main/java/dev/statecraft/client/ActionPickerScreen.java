package dev.statecraft.client;

import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.client.state.SearchState;
import dev.statecraft.client.state.UiPresentation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class ActionPickerScreen extends Screen {
    private record Option(MenuPage page, MenuPage.Action action, UiAction seed) {
        Component label() { return seed == null ? ClientText.action(page.id(), action) : ClientText.of(seed.label()); }
    }
    private final ManagementScreen parent;
    private final MenuPage page;
    private final SearchState state;
    private final List<Button> rows = new ArrayList<>();
    private RetainedEditBox search;
    private List<Option> available = List.of();
    private Button previous;
    private Button next;

    ActionPickerScreen(ManagementScreen parent, MenuPage page) {
        super(ClientText.tr("gui.statecraft.actions.title", "%s — Actions", ClientText.page(page).getString()));
        this.parent = parent;
        this.page = page;
        state = parent.state().actionSearch();
    }
    @Override
    protected void init() {
        if (search != null) state.selection(search.selection());
        int left = Math.max(12, width / 2 - 240);
        int listWidth = Math.min(480, width - 24);
        search = new RetainedEditBox(font, left, 34, listWidth, 20, ClientText.tr("gui.statecraft.actions.search", "Find an action"));
        search.setMaxLength(80);
        search.setHint(ClientText.tr("gui.statecraft.actions.search_hint", "Find an action..."));
        search.setValue(state.text());
        search.restore(state.selection());
        search.setResponder(value -> {
            state.text(value);
            updateRows();
        });
        addRenderableWidget(search);
        rows.clear();
        int count = Math.max(1, (height - 111) / 30);
        for (int i = 0; i < count; i++) {
            final int index = i;
            rows.add(addRenderableWidget(UiButton.create(Component.empty(), ignored -> choose(state.offset() + index))
                    .bounds(left, 62 + i * 30, listWidth, 26).build()));
        }
        int buttonWidth = (listWidth - 8) / 3;
        previous = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.previous", "Previous"), ignored -> {
            state.offset(state.offset() - rows.size());
            updateRows();
        }).bounds(left, height - 28, buttonWidth, 20).build());
        next = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.next", "Next"), ignored -> {
            state.offset(state.offset() + rows.size());
            updateRows();
        }).bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        updateRows();
        setInitialFocus(search);
    }

    private void updateRows() {
        if (rows.isEmpty() || previous == null) return;
        var options = new ArrayList<Option>();
        var contextual = new HashSet<String>();
        if (parent.state().view() != null) {
            for (UiAction seed : parent.state().view().actions()) {
                contextual.add(seed.page() + "\n" + seed.template());
                try {
                    MenuPage target = MenuRegistry.get(seed.page());
                    var action = target.actions().stream().filter(candidate -> candidate.command().equals(seed.template())).findFirst();
                    options.add(new Option(target, action.orElse(null), seed));
                } catch (UserError ignored) {
                    options.add(new Option(null, null, seed));
                }
            }
        }
        if (UiPresentation.includePageActions(parent.state().query(), parent.state().view())) {
            for (MenuPage.Action action : page.actions()) {
                if (!contextual.contains(page.id() + "\n" + action.command())) options.add(new Option(page, action, null));
            }
        }
        String term = state.text().toLowerCase(Locale.ROOT);
        available = options.stream().filter(option -> (option.label().getString() + " "
                + (option.action() == null ? option.seed().template() : option.action().command()))
                .toLowerCase(Locale.ROOT).contains(term)).toList();
        state.offset(Math.min(state.offset(), Math.max(0, (available.size() - 1) / rows.size() * rows.size())));
        for (int i = 0; i < rows.size(); i++) {
            Button row = rows.get(i);
            int index = state.offset() + i;
            row.visible = index < available.size();
            row.active = row.visible;
            if (!row.visible) continue;
            Option option = available.get(index);
            Component label = option.label();
            if (option.action() == null || option.seed() != null && !option.seed().enabled()) {
                label = ClientText.tr("gui.statecraft.action.disabled_label", "Unavailable · %s", label.getString())
                        .copy().withStyle(style -> style.withColor(UiTheme.WARNING & 0xFFFFFF));
            }
            row.setMessage(label);
            row.setTooltip(Tooltip.create(option.action() == null ? ClientText.tr("gui.statecraft.navigation.version",
                    "This section is not registered. Install matching client/server modules.") : option.seed() != null && !option.seed().enabled()
                    ? ClientText.of(option.seed().disabledReason()) : option.label()));
        }
        previous.active = state.offset() > 0;
        next.active = state.offset() + rows.size() < available.size();
    }
    private void choose(int index) {
        if (index < 0 || index >= available.size()) return;
        Option option = available.get(index);
        if (option.action() == null) {
            minecraft.setScreen(new InformationScreen(this, option.label(), ClientText.tr("gui.statecraft.navigation.version",
                    "This section is not registered. Install matching client/server modules.")));
        } else if (option.seed() != null && !option.seed().enabled()) {
            minecraft.setScreen(new InformationScreen(this, option.label(), ClientText.of(option.seed().disabledReason())));
        } else if (option.action().intent() == ActionIntent.NAVIGATION) {
            ClientHooks.open(CommandLine.split(option.action().command()).get(1));
        } else {
            minecraft.setScreen(new ActionFormScreen(parent, this, option.page(), option.action(),
                    option.seed() == null ? Map.of() : option.seed().values()));
        }
    }
    @Override
    public void tick() { search.tick(); }
    @Override
    public void removed() { if (search != null) state.selection(search.selection()); }
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && search.isFocused()) {
            if (available.size() == 1) choose(0);
            else if (!available.isEmpty()) setFocused(rows.get(0));
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 12);
        Component status = available.isEmpty() ? ClientText.tr("gui.statecraft.actions.empty", "No matching actions. Try another search.")
                : ClientText.tr("gui.statecraft.actions.page", "Actions %s–%s of %s. Unavailable actions explain why.",
                        state.offset() + 1, Math.min(available.size(), state.offset() + rows.size()), available.size());
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), width - 24), 12, height - 43, UiTheme.MUTED, false);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { minecraft.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
}
