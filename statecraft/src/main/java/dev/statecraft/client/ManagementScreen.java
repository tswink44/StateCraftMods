package dev.statecraft.client;

import dev.statecraft.api.MenuPage;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.api.ui.UiView;
import dev.statecraft.client.state.PendingOperations;
import dev.statecraft.client.state.SectionFilter;
import dev.statecraft.client.state.SectionLayout;
import dev.statecraft.client.state.UiPresentation;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import dev.statecraft.network.SuiteNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ManagementScreen extends Screen {
    private static final Map<ViewState, SectionFilter> FILTERS = new WeakHashMap<>();
    private final ViewState state;
    private final MenuPage page;
    private final UiScope scope;
    private final SectionFilter filter;
    private RetainedEditBox search;
    private RetainedEditBox command;
    private TextPanel results;
    private Button status;
    private Button navigationAction;
    private Button previous;
    private Button next;
    private Button open;
    private Button copy;
    private Button run;
    private Button refresh;
    private UiView shownView;
    private String shownOutput;
    private ViewState.Content shownContent;
    private UiText shownPlaceholder;

    public ManagementScreen(MenuPage page) {
        this(ClientHooks.workspace().navigation().open(UiQuery.page(page.id())));
    }
    ManagementScreen(ViewState state) {
        super(ClientText.page(MenuRegistry.get(state.query().page())));
        this.state = state;
        page = MenuRegistry.get(state.query().page());
        scope = ClientHooks.scope();
        filter = FILTERS.computeIfAbsent(state, value -> new SectionFilter(value.query().search(), value.searchSelection()));
    }
    MenuPage page() { return page; }
    ViewState state() { return state; }

    @Override
    protected void init() {
        saveEditors();
        search = null;
        command = null;
        run = null;
        int full = width - 24;
        var navigation = SectionLayout.row(12, full, 4, 4);
        SectionLayout layout = SectionLayout.of(height, filter.open(), state.advancedOpen());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(navigation.get(0).x(), 8, navigation.get(0).width(), 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.sections", "Sections"), ignored -> ClientHooks.sections(null))
                .bounds(navigation.get(1).x(), 8, navigation.get(1).width(), 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.dashboard", "My dashboard"),
                        ignored -> ClientHooks.navigate(UiQuery.page("statecraft:dashboard")))
                .bounds(navigation.get(2).x(), 8, navigation.get(2).width(), 20).build());
        navigationAction = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.close", "Close"), ignored -> {
                    if (ClientHooks.needsAttention()) ClientHooks.operations(this);
                    else minecraft.setScreen(null);
                })
                .bounds(navigation.get(3).x(), 8, navigation.get(3).width(), 20).build());
        addRenderableWidget(UiButton.create(filter.open() ? ClientText.tr("gui.statecraft.filter.hide", "Hide filter")
                        : state.query().search().isEmpty() ? ClientText.tr("gui.statecraft.filter.show", "Filter")
                        : ClientText.tr("gui.statecraft.filter.active", "Filtered"), ignored -> {
                    saveEditors();
                    filter.toggle();
                    rebuildWidgets();
                }).bounds(width - 90, 31, 78, 16).build());
        status = addRenderableWidget(UiButton.create(Component.empty(), ignored -> minecraft.setScreen(
                        new InformationScreen(this, ClientText.tr("gui.statecraft.status", "Status"), statusMessage())))
                .bounds(12, 50, full, 16).build());
        if (filter.open()) {
            search = new RetainedEditBox(font, 12, 72, full - 76, 20,
                    ClientText.tr("gui.statecraft.search.section", "Search this section"));
            search.setMaxLength(80);
            search.setHint(ClientText.tr("gui.statecraft.search.section_hint", "Search by name..."));
            search.setValue(filter.text());
            search.restore(filter.selection());
            search.setResponder(filter::text);
            addRenderableWidget(search);
            addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.filter.apply", "Apply"), ignored -> search())
                    .bounds(width - 84, 72, 72, 20).primary().build());
        }
        results = addRenderableWidget(new TextPanel(font, 12, layout.contentTop(), full, layout.contentHeight(),
                entry -> state.select(entry.entity(), entry.copy()), ClientHooks::openEntity, state::scroll));
        previous = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.previous", "Previous"), ignored -> page(-UiView.PAGE_SIZE))
                .bounds(navigation.get(0).x(), layout.pagingY(), navigation.get(0).width(), 20).build());
        next = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.next", "Next"), ignored -> page(UiView.PAGE_SIZE))
                .bounds(navigation.get(1).x(), layout.pagingY(), navigation.get(1).width(), 20).build());
        open = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.open_details", "Open details"), ignored -> results.openSelected())
                .bounds(navigation.get(2).x(), layout.pagingY(), navigation.get(2).width(), 20).build());
        copy = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.copy_row", "Copy row"), ignored -> results.copySelected())
                .bounds(navigation.get(3).x(), layout.pagingY(), navigation.get(3).width(), 20).build());
        int tools = state.advancedOpen() ? 6 : 4;
        var tool = SectionLayout.row(12, full, tools, 4);
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.actions", "Actions"), ignored ->
                        minecraft.setScreen(new ActionPickerScreen(this, page)))
                .bounds(tool.get(0).x(), layout.toolsY(), tool.get(0).width(), 20).primary().build());
        refresh = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.refresh", "Refresh"), ignored -> refresh())
                .bounds(tool.get(1).x(), layout.toolsY(), tool.get(1).width(), 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.map", "Map"), ignored ->
                        minecraft.setScreen(new TerritoryMapScreen(this)))
                .bounds(tool.get(2).x(), layout.toolsY(), tool.get(2).width(), 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr(state.advancedOpen() ? "gui.statecraft.advanced.hide" : "gui.statecraft.advanced.show",
                        state.advancedOpen() ? "Less" : "Advanced"), ignored -> {
                    saveEditors();
                    state.advancedOpen(!state.advancedOpen());
                    rebuildWidgets();
                }).bounds(tool.get(3).x(), layout.toolsY(), tool.get(3).width(), 20).build());
        if (state.advancedOpen()) {
            addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.copy_all", "Copy all"), ignored -> results.copyAll())
                    .bounds(tool.get(4).x(), layout.toolsY(), tool.get(4).width(), 20).build());
            addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.copy_id", "Copy ID"), ignored -> results.copyId())
                    .bounds(tool.get(5).x(), layout.toolsY(), tool.get(5).width(), 20).build());
            command = new RetainedEditBox(font, 12, layout.commandY(), full - 88, 20,
                ClientText.tr("gui.statecraft.advanced.command", "Advanced command"));
            command.setMaxLength(4096);
            command.setHint(ClientText.tr("gui.statecraft.advanced.hint", "Advanced command without /sc or /sce"));
            command.setValue(state.advanced());
            command.restore(state.advancedSelection());
            command.setResponder(state::advanced);
            addRenderableWidget(command);
            run = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.advanced.run", "Review / run"),
                    ignored -> submit(command.getValue())).bounds(width - 96, layout.commandY(), 84, 20).build());
        }
        rebuildContent();
        if (command != null && state.advancedSelection().focused()) setInitialFocus(command);
        else if (search != null && filter.selection().focused()) setInitialFocus(search);
        else setInitialFocus(results);
        if (state.automaticRefresh()) ClientHooks.refresh(state);
        controls();
    }

    private void saveEditors() {
        if (search != null) {
            filter.text(search.getValue());
            filter.selection(search.selection());
            state.searchSelection(search.selection());
        }
        if (command != null) {
            state.advanced(command.getValue());
            state.advancedSelection(command.selection());
        }
    }
    private void search() {
        if (search == null) return;
        filter.text(search.getValue());
        filter.selection(search.selection());
        state.searchSelection(search.selection());
        state.query(new UiQuery(state.query().page(), state.query().entity(), search.getValue(), 0));
        ClientHooks.requestView(state);
        rebuildContent();
    }
    private void page(int step) {
        state.query(new UiQuery(state.query().page(), state.query().entity(), state.query().search(),
                Math.max(0, Math.min(100_000, state.query().offset() + step))));
        ClientHooks.requestView(state);
        rebuildContent();
    }
    private void refresh() {
        if (state.content() == ViewState.Content.RAW && state.explicit() != null) {
            minecraft.setScreen(new TransactionReviewScreen(this, this, state.explicit(), ActionIntent.RAW, null));
        } else {
            ClientHooks.refresh(state);
        }
    }
    public void submit(String line) {
        if (!ClientHooks.current(scope) || line.isBlank() || state.pending() >= 0 || rawPending() != null) return;
        try {
            ActionSelection selection = ActionSelection.raw(page.id(), line);
            if (selection.intent() == ActionIntent.QUERY) ClientHooks.query(state, selection);
            else minecraft.setScreen(new TransactionReviewScreen(this, this, selection, ActionIntent.RAW, null));
        } catch (UserError failure) {
            state.feedback(ActionOutcome.REJECTED, UiText.literal(failure.getMessage()));
        }
    }
    public void reply(SuiteNetwork.ActionResponse response) {
        if (ClientHooks.current(scope) && scope.world().equals(response.world()) && response.id() == state.pending()
                && page.id().equals(response.page())) {
            state.accept(response.id(), state.revision());
            displayResult(response.success(), response.text());
        }
    }
    void displayResult(boolean success, String text) {
        state.feedback(success ? ActionOutcome.COMPLETED : ActionOutcome.REJECTED, UiText.literal(text));
    }
    private PendingOperations.Entry rawPending() {
        if (!ClientHooks.current(scope)) return null;
        return ClientHooks.workspace().operations().entries().stream()
                .filter(entry -> !entry.terminal() && entry.selection() != null && entry.intent() == ActionIntent.RAW
                        && entry.selection().page().equals(page.id())).findFirst().orElse(null);
    }

    private Component statusMessage() {
        if (!ClientHooks.recoveryError().fallback().isEmpty()) return ClientText.of(ClientHooks.recoveryError());
        PendingOperations.Entry raw = rawPending();
        if (raw != null) return ClientText.outcome(raw.outcome()).copy().append(" ").append(raw.text());
        if (state.pending() >= 0) return ClientText.tr("gui.statecraft.loading", "Loading from server...");
        if (!state.banner().fallback().isEmpty()) return ClientText.outcome(state.outcome()).copy().append(": ")
                .append(ClientText.of(state.banner()));
        if (state.content() == ViewState.Content.RAW) return ClientText.tr("gui.statecraft.advanced.retained",
                "Advanced result retained. Refresh requests a new review; it never repeats the command automatically.");
        return ClientText.tr("gui.statecraft.results.hint", "Select a row, then Open details; right-click copies the full row.");
    }

    private void rebuildContent() {
        var entries = new ArrayList<TextPanel.Entry>();
        results.legacyCopy(state.advancedOpen() && state.content() == ViewState.Content.RAW);
        if (state.content() != ViewState.Content.TYPED) {
            String template = state.content() == ViewState.Content.QUERY && state.explicit() != null
                    ? state.explicit().template() : "";
            for (String line : state.output().split("\\R", -1)) entries.add(TextPanel.Entry.text(Component.literal(line),
                    UiPresentation.taxTone(page.id(), template, line)));
        } else if (state.view() == null) {
            entries.add(TextPanel.Entry.text(ClientText.of(state.placeholder())));
        } else {
            UiView view = state.view();
            if (!view.body().fallback().isEmpty() || !view.body().key().isEmpty()) {
                String[] bodyLines = ClientText.of(view.body()).getString().split("\\R", -1);
                for (int index = 0; index < bodyLines.length; index++) {
                    String line = bodyLines[index];
                    int separator = line.indexOf(": ");
                    UiPresentation.Tone tone = UiPresentation.detailTone(state.query().entity(), view.body(), index, bodyLines.length, line);
                    if (separator > 0 && separator < 48) {
                        entries.add(TextPanel.Entry.field(Component.literal(line.substring(0, separator)),
                                Component.literal(line.substring(separator + 2)), tone));
                    } else entries.add(TextPanel.Entry.text(Component.literal(line)));
                }
            }
            for (var row : view.rows()) {
                Component caption = ClientText.of(row.title()).copy().append("\n").append(
                        ClientText.of(row.detail()).copy().withStyle(style -> style.withColor(UiTheme.MUTED & 0xFFFFFF)));
                String text = caption.getString();
                entries.add(new TextPanel.Entry(caption, text, row.entity(), UiPresentation.entityTone(page.id(), row.entity()),
                        UiPresentation.icon(row.entity().kind())));
            }
            if (view.rows().isEmpty() && !view.emptyHint().fallback().isEmpty()) {
                entries.add(TextPanel.Entry.text(ClientText.of(view.emptyHint())));
            }
            if (entries.isEmpty()) entries.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.results.empty", "No matching entries.")));
        }
        results.content(List.copyOf(entries), state.scroll(), state.selected(), state.selectedText());
        shownView = state.view();
        shownOutput = state.output();
        shownContent = state.content();
        shownPlaceholder = state.placeholder();
    }
    private void controls() {
        UiTheme.status(status, statusMessage());
        navigationAction.setMessage(ClientHooks.needsAttention() ? ClientText.tr("gui.statecraft.attention", "Attention")
                : ClientText.tr("gui.statecraft.close", "Close"));
        previous.active = state.content() == ViewState.Content.TYPED && state.pending() < 0 && state.query().offset() > 0;
        next.active = state.content() == ViewState.Content.TYPED && state.pending() < 0 && state.view() != null && state.view().more()
                && state.query().offset() < 100_000;
        open.active = results.selection() != null && results.selection().entity().present();
        copy.active = results.selection() != null;
        refresh.active = state.pending() < 0 && rawPending() == null;
        refresh.setMessage(state.content() == ViewState.Content.RAW
                ? ClientText.tr("gui.statecraft.review.again", "Review again") : ClientText.tr("gui.statecraft.refresh", "Refresh"));
        PendingOperations.Entry raw = rawPending();
        if (command != null) {
            command.setEditable(raw == null);
            if (raw != null && !command.getValue().equals(raw.selection().command())) command.setValue(raw.selection().command());
            run.active = raw == null && state.pending() < 0 && !command.getValue().isBlank();
        }
    }
    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        if (search != null) search.tick();
        if (command != null) command.tick();
        if (shownView != state.view() || !java.util.Objects.equals(shownOutput, state.output()) || shownContent != state.content()
                || state.view() == null && !java.util.Objects.equals(shownPlaceholder, state.placeholder())) rebuildContent();
        controls();
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        Component heading = state.content() == ViewState.Content.TYPED && state.view() != null
                ? ClientText.of(state.view().title()) : title;
        UiTheme.header(graphics, heading, width - 84, 34);
        Component hint = results.copied() ? ClientText.tr("gui.statecraft.copied", "Copied. Control+V to paste.")
                : page.id().equals("economy:tax") && state.content() != ViewState.Content.RAW ? Component.empty()
                        .append(ClientText.tr("gui.statecraft.tax.legend.paid", "Paid").copy().withStyle(style -> style.withColor(UiTheme.POSITIVE & 0xFFFFFF)))
                        .append("  ·  ")
                        .append(ClientText.tr("gui.statecraft.tax.legend.assessed", "Assessed").copy().withStyle(style -> style.withColor(UiTheme.WARNING & 0xFFFFFF)))
                        .append("  ·  ")
                        .append(ClientText.tr("gui.statecraft.tax.legend.outstanding", "Outstanding").copy().withStyle(style -> style.withColor(UiTheme.NEGATIVE & 0xFFFFFF)))
                : ClientText.tr("gui.statecraft.results.open_hint", "Select an entry to view its details and available actions.");
        var hintLines = font.split(hint, width - 24);
        if (!hintLines.isEmpty()) graphics.drawString(font, hintLines.get(0), 12, height - 12, UiTheme.MUTED, false);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(statusMessage()); }
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (search != null && search.isFocused()) { search(); return true; }
            if (command != null && command.isFocused()) { submit(command.getValue()); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override
    public void removed() {
        saveEditors();
        ClientHooks.cancelView(state);
    }
    @Override
    public void onClose() { ClientHooks.back(); }
    @Override
    public boolean isPauseScreen() { return false; }
}
