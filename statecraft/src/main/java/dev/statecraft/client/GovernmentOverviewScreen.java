package dev.statecraft.client;

import dev.statecraft.api.ui.ClaimMapMode;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class GovernmentOverviewScreen extends ManagementScreen {
    private record ScrollingWidget(AbstractWidget widget, GovernmentOverviewLayout.Rect bounds, BooleanSupplier enabled) {}
    private final UiScope overviewScope;
    private final List<AbstractWidget> fixed = new ArrayList<>();
    private final List<ScrollingWidget> scrolling = new ArrayList<>();
    private GovernmentOverviewLayout layout;
    private GovernmentOverview shown;
    private Button status;
    private Button refresh;
    private Button up;
    private Button down;
    private Button actions;
    private final GovernmentOverviewQueryState queryState = new GovernmentOverviewQueryState();
    private boolean queryHandoff;
    private int overviewScroll;

    public GovernmentOverviewScreen(ViewState state) {
        super(state);
        overviewScope = ClientHooks.scope();
    }

    @Override
    protected void init() {
        fixed.clear();
        scrolling.clear();
        shown = state().governmentOverview();
        layout = null;
        if (queryContent()) { queryState.observe(state()); return; }
        int full = width - 24;
        int third = (full - 8) / 3;
        Button back = fixed(UiButton.create(tr("back", "Back"), ignored -> onClose())
                .bounds(12, 8, third, 20).build());
        fixed(UiButton.create(tr("dashboard", "My dashboard"),
                        ignored -> ClientHooks.navigate(dev.statecraft.api.ui.UiQuery.page("statecraft:dashboard")))
                .bounds(16 + third, 8, third, 20).build());
        refresh = fixed(UiButton.create(tr("refresh", "Refresh"), ignored -> refreshOverview())
                .bounds(20 + third * 2, 8, full - third * 2 - 8, 20).build());
        int actionsX = width - 154;
        status = fixed(UiButton.create(Component.empty(), ignored -> information())
                .bounds(12, height - 28, actionsX - 20, 20).build());
        actions = fixed(UiButton.create(tr("actions", "Actions"), ignored -> {
            if (layout != null) scrollTo(layout.actions().bounds().y());
        }).bounds(actionsX, height - 28, 62, 20).build());
        up = fixed(UiButton.create(tr("up", "Up"), ignored -> scrollBy(-scrollStep()))
                .bounds(width - 84, height - 28, 32, 20)
                .tooltip(Tooltip.create(tr("scroll_up", "Scroll up. Page Up and Home also work."))).build());
        down = fixed(UiButton.create(tr("down", "Down"), ignored -> scrollBy(scrollStep()))
                .bounds(width - 44, height - 28, 32, 20)
                .tooltip(Tooltip.create(tr("scroll_down", "Scroll down. Page Down and End also work."))).build());
        if (shown != null) buildOverview(shown);
        setInitialFocus(back);
        if (ClientHooks.current(overviewScope) && state().content() == ViewState.Content.TYPED && state().automaticRefresh()) {
            ClientHooks.refresh(state());
        }
        controls();
    }

    private void buildOverview(GovernmentOverview overview) {
        List<ClaimMapMode> mapModes = GovernmentOverviewLayout.mapModes(overview.kind(), overview.officialInbox() != null);
        layout = GovernmentOverviewLayout.of(Math.max(240, width), Math.max(180, height),
                overview.parent() != null, !overview.description().isBlank(),
                overview.children().entries().size(), overview.officers().entries().size(), overview.groups().size(),
                mapModes.size() + (overview.officialInbox() == null ? 0 : 1));
        Component identity = identity(overview);
        card(layout.identity(), kind(overview), Component.literal(overview.name()),
                tr("flag_value", "Flag: %s", flag(overview)), new ItemStack(Items.WHITE_BANNER), identity,
                ignored -> minecraft.setScreen(new InformationScreen(this, Component.literal(overview.name()), identity)));
        card(layout.facts().get(0), tr("treasury", "Treasury balance"), ClientText.of(overview.treasury()),
                Component.empty(), new ItemStack(Items.GOLD_INGOT),
                tr("treasury_value", "Treasury balance: %s", ClientText.of(overview.treasury()).getString()),
                ignored -> minecraft.setScreen(new InformationScreen(this, tr("treasury", "Treasury balance"),
                        ClientText.of(overview.treasury()))));
        PersonalDashboard.Entry leader = overview.leader();
        card(layout.facts().get(1), tr("leadership", "Leadership"),
                leader == null ? tr("leader_absent", "No leader") : ClientText.of(leader.name()),
                leader == null ? Component.empty() : ClientText.of(leader.detail()), new ItemStack(Items.PLAYER_HEAD),
                leader == null ? tr("leader_absent", "No leader") : entryText(leader),
                ignored -> {
                    if (leader != null) ClientHooks.navigate(leader.target());
                    else information();
                });
        if (overview.parent() != null) {
            PersonalDashboard.Entry parent = overview.parent();
            card(layout.facts().get(2), tr("parent", "Parent government"), ClientText.of(parent.name()),
                    ClientText.of(parent.detail()), new ItemStack(Items.MAP), entryText(parent),
                    ignored -> ClientHooks.navigate(parent.target()));
        }
        if (layout.description() != null) {
            card(layout.description(), Component.empty(), tr("description", "Description"),
                    Component.literal(overview.description()), new ItemStack(Items.PAPER),
                    Component.literal(overview.description()), ignored -> minecraft.setScreen(new InformationScreen(
                            this, tr("description", "Description"), Component.literal(overview.description()))));
        }
        for (int i = 0; i < mapModes.size(); i++) {
            ClaimMapMode mode = mapModes.get(i);
            var bounds = layout.shortcuts().get(i);
            Component label = mapTitle(mode);
            scrolling(new GovernmentOverviewCard(bounds, Component.empty(), label,
                            tr("open_terrain_map", "Open the terrain map"), new ItemStack(Items.MAP), true,
                            label.copy().append("\n").append(tr("unassigned_territory",
                                    "Nations hold claims. State and city assignments may remain unassigned.")),
                            ignored -> openMap(mode)), bounds, () -> state().pending() < 0);
        }
        if (overview.officialInbox() != null) {
            var bounds = layout.shortcuts().get(mapModes.size());
            Component label = tr("official_inbox", "Official inbox");
            scrolling(new GovernmentOverviewCard(bounds, Component.empty(), label,
                            tr("official_inbox_hint", "This government's inbox and sent mail"), new ItemStack(Items.WRITTEN_BOOK), true,
                            label.copy().append("\n").append(tr("selected_government", "For %s", overview.name())),
                            ignored -> openOfficialInbox()), bounds, () -> state().pending() < 0);
        }
        for (int i = 0; i < overview.groups().size(); i++) {
            GovernmentOverview.Group group = overview.groups().get(i);
            var bounds = layout.actions().rows().get(i);
            Component detail = tr("action_count", "%s actions", group.actions().size());
            Component explanation = ClientText.of(group.title()).copy().append("\n")
                    .append(tr("selected_government", "For %s", overview.name())).append("\n")
                    .append(tr("category_hint", "Open this category. Unavailable actions explain why."));
            GovernmentOverviewCard button = new GovernmentOverviewCard(bounds, Component.empty(),
                    ClientText.of(group.title()), detail, groupIcon(group.id()), true, explanation,
                    ignored -> minecraft.setScreen(new GovernmentActionMenuScreen(this, group.id())));
            scrolling(button, bounds, () -> state().pending() < 0);
        }
        roster(overview.children(), layout.children(), GovernmentOverview.Section.CHILDREN);
        roster(overview.officers(), layout.officers(), GovernmentOverview.Section.OFFICERS);
        state().scroll(layout.clampScroll(state().scroll()));
    }

    private void roster(PersonalDashboard.Page page, GovernmentOverviewLayout.Section section, GovernmentOverview.Section id) {
        for (int i = 0; i < page.entries().size(); i++) {
            PersonalDashboard.Entry entry = page.entries().get(i);
            card(section.rows().get(i), Component.empty(), ClientText.of(entry.name()), ClientText.of(entry.detail()),
                    ItemStack.EMPTY, entryText(entry), ignored -> ClientHooks.navigate(entry.target()));
        }
        var previous = section.previous();
        var next = section.next();
        Component range = pageRange(page);
        Button previousButton = UiButton.create(tr("previous", "Previous"), ignored ->
                        page(id, Math.max(0, page.offset() - PersonalDashboard.PAGE_SIZE)))
                .bounds(previous.x(), previous.y(), previous.width(), previous.height())
                .tooltip(Tooltip.create(range)).build();
        Button nextButton = UiButton.create(tr("next", "Next"), ignored -> page(id, page.offset() + PersonalDashboard.PAGE_SIZE))
                .bounds(next.x(), next.y(), next.width(), next.height()).tooltip(Tooltip.create(range)).build();
        scrolling(previousButton, previous, () -> state().pending() < 0 && page.offset() > 0);
        scrolling(nextButton, next, () -> state().pending() < 0 && page.more()
                && page.offset() + PersonalDashboard.PAGE_SIZE <= PersonalDashboard.MAX_OFFSET);
    }

    private void card(GovernmentOverviewLayout.Rect bounds, Component caption, Component value, Component detail,
                      ItemStack icon, Component explanation, Button.OnPress press) {
        scrolling(new GovernmentOverviewCard(bounds, caption, value, detail, icon, true, explanation, press),
                bounds, () -> true);
    }

    private void scrolling(AbstractWidget widget, GovernmentOverviewLayout.Rect bounds, BooleanSupplier enabled) {
        addRenderableWidget(widget);
        scrolling.add(new ScrollingWidget(widget, bounds, enabled));
    }

    private <T extends AbstractWidget> T fixed(T widget) {
        addRenderableWidget(widget);
        fixed.add(widget);
        return widget;
    }

    private void page(GovernmentOverview.Section section, int offset) {
        if (!ClientHooks.current(overviewScope) || state().pending() >= 0) return;
        GovernmentOverview.Request request = state().governmentRequest();
        if (request == null || state().governmentOverview() == null) return;
        if (state().content() != ViewState.Content.TYPED) state().query(state().query());
        state().governmentRequest(request.page(section, offset));
        ClientHooks.refresh(state());
        controls();
    }

    void refreshOverview() {
        if (!ClientHooks.current(overviewScope) || state().pending() >= 0) return;
        if (state().content() != ViewState.Content.TYPED) state().query(state().query());
        ClientHooks.refresh(state());
        controls();
    }

    private void openOfficialInbox() {
        if (!ClientHooks.current(overviewScope) || state().pending() >= 0) return;
        GovernmentOverview current = state().governmentOverview();
        if (current == null || current.officialInbox() == null) return;
        if (state().stale()) { refreshOverview(); return; }
        ClientHooks.navigate(current.officialInbox());
    }

    private void openMap(ClaimMapMode mode) {
        if (!ClientHooks.current(overviewScope) || state().pending() >= 0) return;
        GovernmentOverview current = state().governmentOverview();
        if (current == null || !GovernmentOverviewLayout.mapModes(current.kind(), current.officialInbox() != null).contains(mode)) return;
        if (state().stale()) { refreshOverview(); return; }
        ClientHooks.claimMap(this, current.id(), mode);
    }

    private static Component mapTitle(ClaimMapMode mode) {
        return switch (mode) {
            case CLAIM -> tr("claim_chunks", "Claim chunks");
            case ASSIGN_STATE -> tr("assign_states", "Assign to states");
            case ASSIGN_CITY -> tr("assign_cities", "Assign to cities");
        };
    }

    private void controls() {
        if (status == null) return;
        queryState.observe(state());
        if (state().content() == ViewState.Content.TYPED) overviewScroll = state().scroll();
        boolean current = ClientHooks.current(overviewScope);
        refresh.active = current && state().pending() < 0;
        actions.active = current && layout != null && shown != null && !shown.groups().isEmpty();
        up.active = current && layout != null && state().scroll() > 0;
        down.active = current && layout != null && state().scroll() < layout.maxScroll();
        status.setMessage(state().pending() >= 0 ? tr("loading_short", "Loading…")
                : !state().banner().fallback().isBlank() ? tr("status", "Status & details")
                : tr("overview_details", "Overview details"));
        status.setTooltip(Tooltip.create(statusMessage()));
        for (ScrollingWidget entry : scrolling) {
            entry.widget().setY(layout.screenY(entry.bounds().y(), state().scroll()));
            entry.widget().visible = layout.fullyVisible(entry.bounds(), state().scroll());
            entry.widget().active = current && entry.enabled().getAsBoolean();
            if (!entry.widget().visible && entry.widget().isFocused()) setFocused(status);
        }
    }

    private Component statusMessage() {
        if (state().pending() >= 0) return state().content() == ViewState.Content.QUERY
                ? tr("loading_query", "Loading the selected action's result from the server…")
                : tr("loading", "Loading this government's overview from the server…");
        if (!state().banner().fallback().isBlank()) return ClientText.of(state().banner());
        return tr("scroll_hint", "Open a card or action category. Scroll, Page Up/Down, Home and End move through the overview.");
    }

    Component queryTitle() {
        try {
            return ClientText.action(state().explicit().page(), state().explicit().registeredAction());
        } catch (dev.statecraft.api.UserError unavailable) {
            return tr("result", "Action result");
        }
    }

    private boolean queryContent() {
        return state().content() == ViewState.Content.QUERY && state().explicit() != null;
    }

    private void showQuery() {
        if (!ClientHooks.current(overviewScope)) { minecraft.setScreen(null); return; }
        queryState.observe(state());
        queryHandoff = true;
        minecraft.setScreen(new GovernmentOverviewQueryScreen(this, queryState));
    }

    void returnFromQuery() {
        if (!ClientHooks.current(overviewScope)) { minecraft.setScreen(null); return; }
        GovernmentOverviewQueryState.returnToOverview(state());
        state().scroll(overviewScroll);
        minecraft.setScreen(this);
    }

    private void information() {
        if (queryContent()) { showQuery(); return; }
        Component body = statusMessage();
        if (shown != null) body = identity(shown).copy().append("\n\n").append(body);
        minecraft.setScreen(new InformationScreen(this, tr("overview", "Government overview"), body));
    }

    @Override
    public void tick() {
        if (!ClientHooks.current(overviewScope)) { minecraft.setScreen(null); return; }
        if (queryContent()) { showQuery(); return; }
        if (shown != state().governmentOverview()) rebuildWidgets();
        controls();
    }

    private int scrollStep() { return layout == null ? 72 : Math.max(36, layout.viewport().height() - 36); }
    private void scrollBy(int change) { scrollTo(state().scroll() + change); }
    private void scrollTo(int scroll) {
        if (layout == null) return;
        state().scroll(layout.clampScroll(scroll));
        controls();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (layout != null && layout.viewport().contains(mouseX, mouseY)) {
            scrollBy((int) (-delta * 36));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (queryContent()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) onClose();
            else showQuery();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) { scrollBy(-scrollStep()); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) { scrollBy(scrollStep()); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { scrollTo(0); return true; }
        if (key == GLFW.GLFW_KEY_END && layout != null) { scrollTo(layout.maxScroll()); return true; }
        if (key == GLFW.GLFW_KEY_F5) { refreshOverview(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        if (queryContent()) {
            UiTheme.header(graphics, queryTitle(), width, 12);
            graphics.drawString(font, font.plainSubstrByWidth(tr("query_opening", "Opening the retained query result…").getString(),
                    width - 24), 12, 36, UiTheme.MUTED, false);
            return;
        }
        UiTheme.header(graphics, shown == null ? tr("overview", "Government overview") : Component.literal(shown.name()), width, 36);
        if (layout == null) {
            graphics.fill(12, 54, width - 12, height - 36, UiTheme.SURFACE);
            Component message = state().pending() >= 0 || state().banner().fallback().isBlank()
                    ? tr("loading", "Loading this government's overview from the server…")
                    : tr("unavailable", "The overview could not be loaded. Open Status for details, or Refresh to retry.");
            int y = 70;
            for (var line : font.split(message, Math.max(1, width - 48))) {
                if (y + font.lineHeight >= height - 44) break;
                graphics.drawString(font, line, 24, y, UiTheme.MUTED, false);
                y += 12;
            }
        } else {
            var viewport = layout.viewport();
            graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
            section(graphics, layout.actions(), tr("actions", "Actions"), shown.groups().isEmpty()
                    ? tr("no_actions", "No action categories are available.") : Component.empty());
            section(graphics, layout.children(), tr("roster_title", "%s (%s)", childrenTitle(shown).getString(), shown.children().total()),
                    shown.children().entries().isEmpty() ? childrenEmpty(shown) : Component.empty());
            section(graphics, layout.officers(), tr("roster_title", "%s (%s)", tr("officers", "Leadership & officers").getString(),
                    shown.officers().total()), shown.officers().entries().isEmpty()
                    ? tr("no_officers", "No officers are recorded.") : Component.empty());
            for (ScrollingWidget entry : scrolling) {
                if (entry.widget().visible) entry.widget().render(graphics, mouseX, mouseY, delta);
            }
            graphics.disableScissor();
            if (layout.maxScroll() > 0) {
                int thumb = Math.max(12, viewport.height() * viewport.height() / layout.contentHeight());
                int y = viewport.y() + state().scroll() * (viewport.height() - thumb) / layout.maxScroll();
                graphics.fill(width - 7, viewport.y(), width - 4, viewport.bottom(), UiTheme.SURFACE);
                graphics.fill(width - 7, y, width - 4, y + thumb, UiTheme.ACCENT);
            }
        }
        for (AbstractWidget widget : fixed) widget.render(graphics, mouseX, mouseY, delta);
    }

    private void section(GuiGraphics graphics, GovernmentOverviewLayout.Section section, Component title, Component empty) {
        var bounds = section.bounds();
        int y = layout.screenY(bounds.y(), state().scroll());
        graphics.fill(bounds.x(), y, bounds.right(), y + bounds.height(), UiTheme.BACKGROUND);
        graphics.fill(bounds.x(), y + 19, bounds.right(), y + 20, UiTheme.BORDER);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), bounds.width() - 16),
                bounds.x() + 8, y + 6, UiTheme.ACCENT, false);
        if (!empty.getString().isBlank()) {
            graphics.drawString(font, font.plainSubstrByWidth(empty.getString(), bounds.width() - 16),
                    bounds.x() + 8, y + 34, UiTheme.MUTED, false);
        }
    }

    @Override
    public Component getNarrationMessage() {
        return shown == null ? tr("overview", "Government overview").copy().append(". ").append(statusMessage())
                : identity(shown).copy().append(". ").append(tr("roster_counts", "%s: %s. Leadership and officers: %s.",
                        childrenTitle(shown).getString(), shown.children().total(), shown.officers().total()))
                        .append(". ").append(statusMessage());
    }

    @Override
    public void removed() {
        if (!queryHandoff && state().pending() >= 0) ClientHooks.cancelView(state());
        queryHandoff = false;
    }

    @Override
    public void onClose() {
        if (!ClientHooks.current(overviewScope)) { minecraft.setScreen(null); return; }
        if (queryContent()) returnFromQuery();
        else ClientHooks.back();
    }

    private static Component pageRange(PersonalDashboard.Page page) {
        return page.total() == 0 ? tr("empty_page", "No entries")
                : tr("page_range", "Entries %s–%s of %s", page.offset() + 1, page.offset() + page.entries().size(), page.total());
    }

    private static Component entryText(PersonalDashboard.Entry entry) {
        return ClientText.of(entry.name()).copy().append("\n").append(ClientText.of(entry.detail()));
    }

    private static Component kind(GovernmentOverview overview) {
        return switch (overview.kind()) {
            case NATION -> tr("nation", "Nation overview");
            case STATE -> tr("state", "State overview");
            case CITY -> tr("city", "City overview");
        };
    }

    private static Component childrenTitle(GovernmentOverview overview) {
        return switch (overview.kind()) {
            case NATION -> tr("states", "States");
            case STATE -> tr("cities", "Cities");
            case CITY -> tr("claims", "Assigned chunks");
        };
    }

    private static Component childrenEmpty(GovernmentOverview overview) {
        return switch (overview.kind()) {
            case NATION -> tr("no_states", "No states have been established.");
            case STATE -> tr("no_cities", "No cities have been established.");
            case CITY -> tr("no_claims", "No chunks have been assigned to this city.");
        };
    }

    private static String flag(GovernmentOverview overview) {
        return overview.flag().isBlank() ? tr("flag_absent", "No flag description set").getString() : overview.flag();
    }

    private static Component identity(GovernmentOverview overview) {
        Component result = kind(overview).copy().append(": ").append(overview.name()).append("\n")
                .append(tr("flag_value", "Flag: %s", flag(overview))).append("\n")
                .append(tr("flag_emblem", "The banner is a menu emblem. Your stored flag is descriptive text, not an image.")).append("\n")
                .append(tr("treasury_value", "Treasury balance: %s", ClientText.of(overview.treasury()).getString()));
        if (overview.leader() != null) result = result.copy().append("\n").append(entryText(overview.leader()));
        if (overview.parent() != null) result = result.copy().append("\n")
                .append(tr("parent_value", "Parent government: %s", ClientText.of(overview.parent().name()).getString()));
        if (!overview.description().isBlank()) result = result.copy().append("\n\n").append(overview.description());
        return result;
    }

    static ItemStack groupIcon(String group) {
        return new ItemStack(switch (group) {
            case "diplomacy", "legislature" -> Items.WRITABLE_BOOK;
            case "executive" -> Items.GOLDEN_HELMET;
            case "elections" -> Items.PAPER;
            case "settings" -> Items.COMPARATOR;
            case "contracts" -> Items.BOOK;
            default -> Items.MAP;
        });
    }

    static Component tr(String key, String fallback, Object... values) {
        return ClientText.tr("gui.statecraft.government." + key, fallback, values);
    }
}
