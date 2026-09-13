package dev.statecraft.client;

import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.UiAction;
import dev.statecraft.client.state.UiScope;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static dev.statecraft.client.GovernmentOverviewScreen.tr;

public final class GovernmentActionMenuScreen extends Screen {
    private final GovernmentOverviewScreen overview;
    private final String groupId;
    private final UiScope scope;
    private final List<AbstractWidget> actionWidgets = new ArrayList<>();
    private GovernmentOverview shown;
    private GovernmentOverview.Group group;
    private int offset;
    private int capacity = 1;
    private Button previous;
    private Button next;
    private Button refresh;
    private Button status;
    private boolean settingsRequested;
    private GovernmentOverview settingsSnapshot;

    public GovernmentActionMenuScreen(GovernmentOverviewScreen overview, String groupId) {
        super("settings".equals(groupId) ? tr("settings", "Settings") : tr("actions", "Actions"));
        this.overview = overview;
        this.groupId = groupId;
        scope = ClientHooks.scope();
    }

    @Override
    protected void init() {
        if (settings() && !settingsRequested) {
            settingsRequested = true;
            settingsSnapshot = overview.state().governmentOverview();
            overview.refreshOverview();
        }
        shown = overview.state().governmentOverview();
        group = shown == null || settings() && shown == settingsSnapshot ? null
                : shown.groups().stream().filter(value -> groupId.equals(value.id())).findFirst().orElse(null);
        actionWidgets.clear();
        int full = width - 24;
        int half = (full - 4) / 2;
        Button back = addRenderableWidget(UiButton.create(tr("back_overview", "Back to overview"), ignored -> onClose())
                .bounds(12, 8, half, 20).build());
        refresh = addRenderableWidget(UiButton.create(tr("refresh_permissions", "Refresh permissions"), ignored -> refreshPermissions())
                .bounds(16 + half, 8, full - half - 4, 20).build());
        List<GovernmentOverviewLayout.Rect> slots = GovernmentOverviewLayout.actionSlots(Math.max(240, width), Math.max(180, height));
        capacity = slots.size();
        int total = group == null ? 0 : group.actions().size();
        offset = Math.min(Math.max(0, offset / capacity * capacity), Math.max(0, (total - 1) / capacity * capacity));
        if (group != null) {
            for (int index = 0; index < capacity && offset + index < group.actions().size(); index++) {
                UiAction action = group.actions().get(offset + index);
                Component value = ClientText.of(action.label());
                Component explanation = explanation(action);
                Component detail = action.enabled() ? intent(action) : tr("disabled", "Unavailable — open for the reason");
                GovernmentOverviewCard card = new GovernmentOverviewCard(slots.get(index), Component.empty(), value, detail,
                        GovernmentOverviewScreen.groupIcon(group.id()), action.enabled(), explanation, ignored -> choose(action));
                addRenderableWidget(card);
                actionWidgets.add(card);
            }
        }
        status = addRenderableWidget(UiButton.create(Component.empty(), ignored -> minecraft.setScreen(
                        new InformationScreen(this, tr("status", "Status & details"), statusMessage())))
                .bounds(12, height - 47, full, 16).build());
        previous = addRenderableWidget(UiButton.create(tr("previous_actions", "Previous actions"), ignored -> changePage(-capacity))
                .bounds(12, height - 28, half, 20).build());
        next = addRenderableWidget(UiButton.create(tr("next_actions", "Next actions"), ignored -> changePage(capacity))
                .bounds(16 + half, height - 28, full - half - 4, 20).build());
        setInitialFocus(back);
        controls();
    }

    private Component intent(UiAction action) {
        try {
            MenuPage.Action registered = registered(action);
            return switch (registered.intent()) {
                case QUERY -> tr("query_action", "View information");
                case NAVIGATION -> tr("open_section", "Open section");
                default -> tr("review_action", "Uses server review before confirmation");
            };
        } catch (UserError unavailable) {
            return tr("module_missing", "Install matching client/server modules.");
        }
    }

    private Component explanation(UiAction action) {
        Component detail = action.enabled() ? intent(action) : ClientText.of(action.disabledReason());
        return ClientText.of(action.label()).copy().append("\n")
                .append(tr("selected_government", "For %s", shown.name())).append("\n").append(detail);
    }

    private static MenuPage.Action registered(UiAction action) {
        return MenuRegistry.get(action.page()).actions().stream().filter(value -> action.template().equals(value.command()))
                .findFirst().orElseThrow(() -> new UserError("This action is not registered."));
    }

    private void choose(UiAction action) {
        if (!ClientHooks.current(scope) || overview.state().pending() >= 0) return;
        if (settings() && overview.state().stale()) { refreshPermissions(); return; }
        GovernmentOverview current = overview.state().governmentOverview();
        if (current == null || settings() && current == settingsSnapshot
                || current.groups().stream().filter(value -> groupId.equals(value.id()))
                .noneMatch(value -> value.actions().contains(action))) {
            minecraft.setScreen(new InformationScreen(this, title, unavailable()));
            return;
        }
        if (!action.enabled()) {
            minecraft.setScreen(new InformationScreen(this, ClientText.of(action.label()), ClientText.of(action.disabledReason())));
            return;
        }
        try {
            MenuPage page = MenuRegistry.get(action.page());
            MenuPage.Action command = registered(action);
            if (command.intent() == ActionIntent.NAVIGATION) {
                ClientHooks.open(CommandLine.split(command.command()).get(1));
            } else {
                minecraft.setScreen(new ActionFormScreen(overview, this, page, command, action.values()));
            }
        } catch (UserError unavailable) {
            minecraft.setScreen(new InformationScreen(this, ClientText.of(action.label()),
                    tr("module_missing", "Install matching client/server modules.")));
        }
    }

    private void controls() {
        boolean idle = ClientHooks.current(scope) && overview.state().pending() < 0;
        refresh.active = idle;
        previous.active = idle && offset > 0;
        next.active = idle && group != null && offset + capacity < group.actions().size();
        actionWidgets.forEach(widget -> widget.active = idle);
        Component range = range();
        status.setMessage(overview.state().pending() >= 0 ? tr("refreshing_permissions", "Refreshing current permissions…")
                : !overview.state().banner().fallback().isBlank() ? tr("status", "Status & details") : range);
        status.setTooltip(Tooltip.create(statusMessage()));
        previous.setTooltip(Tooltip.create(range));
        next.setTooltip(Tooltip.create(range));
        refresh.setTooltip(Tooltip.create(overview.state().pending() >= 0 ? tr("refreshing_permissions", "Refreshing current permissions…")
                : tr("permissions_hint", "Refresh this government's overview and permission checks. No action is submitted.")));
    }

    private Component range() {
        return group == null || group.actions().isEmpty() ? unavailable()
                : tr("actions_range", "Actions %s–%s of %s", offset + 1, Math.min(offset + capacity, group.actions().size()), group.actions().size());
    }

    private boolean settings() { return "settings".equals(groupId); }

    private Component unavailable() {
        if (settings()) return overview.state().pending() >= 0
                ? tr("refreshing_permissions", "Refreshing current permissions…")
                : tr("settings_restricted", "Settings require current government management authority. Refresh permissions or return to the overview.");
        return tr("no_actions", "No action categories are available.");
    }

    private void refreshPermissions() {
        if (!ClientHooks.current(scope) || overview.state().pending() >= 0) return;
        if (settings()) settingsSnapshot = overview.state().governmentOverview();
        overview.refreshOverview();
        if (settings()) rebuildWidgets();
    }

    private Component statusMessage() {
        if (overview.state().pending() >= 0) return tr("refreshing_permissions", "Refreshing current permissions…");
        if (!overview.state().banner().fallback().isBlank()) return ClientText.of(overview.state().banner());
        return range().copy().append("\n").append(tr("category_hint", "Open this category. Unavailable actions explain why."));
    }

    private void changePage(int change) {
        if (group == null) return;
        offset = Math.max(0, Math.min(offset + change, Math.max(0, (group.actions().size() - 1) / capacity * capacity)));
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        if (settings() && overview.state().automaticRefresh()) refreshPermissions();
        if (shown != overview.state().governmentOverview()) rebuildWidgets();
        controls();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP) { changePage(-capacity); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) { changePage(capacity); return true; }
        if (key == GLFW.GLFW_KEY_F5) { refreshPermissions(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0 && mouseY >= 78 && mouseY < height - 48) {
            changePage(delta < 0 ? capacity : -capacity);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, group == null ? title : ClientText.of(group.title()), width, 36);
        Component selected = shown == null ? overview.state().pending() >= 0
                ? tr("refreshing_permissions", "Refreshing current permissions…")
                : tr("unavailable", "The overview could not be loaded. Open Status for details, or Refresh to retry.")
                : tr("selected_government", "For %s", shown.name());
        graphics.drawString(font, font.plainSubstrByWidth(selected.getString(), width - 24), 12, 56, UiTheme.MUTED, false);
        if (group == null || group.actions().isEmpty()) {
            int y = 84;
            for (var line : font.split(unavailable(), Math.max(1, width - 24))) {
                if (y + font.lineHeight >= height - 52) break;
                graphics.drawString(font, line, 12, y, UiTheme.MUTED, false);
                y += 12;
            }
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public Component getNarrationMessage() {
        Component heading = group == null ? title : ClientText.of(group.title());
        return heading.copy().append(". ").append(shown == null ? Component.empty()
                : tr("selected_government", "For %s", shown.name())).append(". ").append(statusMessage());
    }

    @Override
    public void onClose() { minecraft.setScreen(overview); }

    @Override
    public void removed() {
        if (settings()) {
            settingsRequested = false;
            if (overview.state().pending() >= 0) ClientHooks.cancelView(overview.state());
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
