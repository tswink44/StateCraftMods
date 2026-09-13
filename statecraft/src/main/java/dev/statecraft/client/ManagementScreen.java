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
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import dev.statecraft.network.SuiteNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class ManagementScreen extends Screen {
    private final ViewState state;
    private final MenuPage page;
    private final UiScope scope;
    private RetainedEditBox search;
    private RetainedEditBox command;
    private TextPanel results;
    private Button status;
    private Button operations;
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
    }
    MenuPage page() { return page; }
    ViewState state() { return state; }

    @Override
    protected void init() {
        saveEditors();
        int full = width - 24;
        int small = (full - 12) / 4;
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(12, 8, small, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.sections", "Sections"), ignored -> ClientHooks.sections(null))
                .bounds(16 + small, 8, small, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.dashboard", "My dashboard"),
                        ignored -> ClientHooks.navigate(UiQuery.page("statecraft:dashboard")))
                .bounds(20 + small * 2, 8, small, 20).build());
        operations = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.count", "Operations (%s)",
                        ClientHooks.pendingCount()), ignored -> ClientHooks.operations(this))
                .bounds(24 + small * 3, 8, small, 20).build());
        status = addRenderableWidget(Button.builder(Component.empty(), ignored -> minecraft.setScreen(
                        new InformationScreen(this, ClientText.tr("gui.statecraft.status", "Status"), statusMessage())))
                .bounds(12, 46, full, 12).build());
        search = new RetainedEditBox(font, 12, 62, Math.max(80, full - 76), 20,
                ClientText.tr("gui.statecraft.search.section", "Search this section"));
        search.setMaxLength(80);
        search.setHint(ClientText.tr("gui.statecraft.search.section_hint", "Filter names or IDs..."));
        search.setValue(state.query().search());
        search.restore(state.searchSelection());
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.search", "Search"), ignored -> search())
                .bounds(width - 84, 62, 72, 20).build());
        results = addRenderableWidget(new TextPanel(font, 12, 87, full, height - 189,
                entry -> state.select(entry.entity(), entry.copy()), ClientHooks::openEntity, state::scroll));
        previous = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.previous", "Previous"), ignored -> page(-UiView.PAGE_SIZE))
                .bounds(12, height - 97, small, 20).build());
        next = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.next", "Next"), ignored -> page(UiView.PAGE_SIZE))
                .bounds(16 + small, height - 97, small, 20).build());
        open = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.open_details", "Open details"), ignored -> results.openSelected())
                .bounds(20 + small * 2, height - 97, small, 20).build());
        copy = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.copy_row", "Copy row"), ignored -> results.copySelected())
                .bounds(24 + small * 3, height - 97, small, 20).build());
        int tool = (full - 16) / 5;
        refresh = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.refresh", "Refresh"), ignored -> refresh())
                .bounds(12, height - 73, tool, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.actions", "Actions"), ignored ->
                        minecraft.setScreen(new ActionPickerScreen(this, page)))
                .bounds(16 + tool, height - 73, tool, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.map", "Map"), ignored ->
                        minecraft.setScreen(new TerritoryMapScreen(this)))
                .bounds(20 + tool * 2, height - 73, tool, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.copy_all", "Copy all"), ignored -> results.copyAll())
                .bounds(24 + tool * 3, height - 73, tool, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.copy_id", "Copy ID"), ignored -> results.copyId())
                .bounds(28 + tool * 4, height - 73, tool, 20).build());
        command = new RetainedEditBox(font, 12, height - 49, full - 88, 20,
                ClientText.tr("gui.statecraft.advanced.command", "Advanced command"));
        command.setMaxLength(4096);
        command.setHint(ClientText.tr("gui.statecraft.advanced.hint", "Advanced command without /sc or /sce"));
        command.setValue(state.advanced());
        command.restore(state.advancedSelection());
        command.setResponder(state::advanced);
        addRenderableWidget(command);
        run = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.advanced.run", "Review / run"),
                        ignored -> submit(command.getValue())).bounds(width - 96, height - 49, 84, 20).build());
        rebuildContent();
        if (state.advancedSelection().focused()) setInitialFocus(command);
        else if (state.searchSelection().focused()) setInitialFocus(search);
        else setInitialFocus(results);
        if (state.automaticRefresh()) ClientHooks.refresh(state);
        controls();
    }

    private void saveEditors() {
        if (search != null) state.searchSelection(search.selection());
        if (command != null) {
            state.advanced(command.getValue());
            state.advancedSelection(command.selection());
        }
    }
    private void search() {
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
        results.legacyCopy(state.content() != ViewState.Content.TYPED);
        if (state.content() != ViewState.Content.TYPED) {
            for (String line : state.output().split("\\R", -1)) entries.add(TextPanel.Entry.text(Component.literal(line)));
        } else if (state.view() == null) {
            entries.add(TextPanel.Entry.text(ClientText.of(state.placeholder())));
        } else {
            UiView view = state.view();
            if (!view.body().fallback().isEmpty() || !view.body().key().isEmpty()) {
                entries.add(TextPanel.Entry.text(ClientText.of(view.body())));
            }
            for (var row : view.rows()) {
                Component caption = ClientText.of(row.title()).copy().append("\n").append(ClientText.of(row.detail()));
                String text = caption.getString() + (row.entity().present() ? "\n" + row.entity().id() : "");
                entries.add(new TextPanel.Entry(caption, text, row.entity()));
            }
            if (view.rows().isEmpty() && !view.emptyHint().fallback().isEmpty()) {
                entries.add(TextPanel.Entry.text(ClientText.of(view.emptyHint())));
            }
            for (var action : view.actions()) {
                if (!action.enabled()) entries.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.action.unavailable",
                        "Unavailable: %s — %s", ClientText.of(action.label()).getString(),
                        ClientText.of(action.disabledReason()).getString())));
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
        status.setMessage(statusMessage());
        operations.setMessage(ClientText.tr("gui.statecraft.operations.count", "Operations (%s)", ClientHooks.pendingCount()));
        previous.active = state.content() == ViewState.Content.TYPED && state.pending() < 0 && state.query().offset() > 0;
        next.active = state.content() == ViewState.Content.TYPED && state.pending() < 0 && state.view() != null && state.view().more()
                && state.query().offset() < 100_000;
        open.active = results.selection() != null && results.selection().entity().present();
        copy.active = results.selection() != null;
        refresh.active = state.pending() < 0 && rawPending() == null;
        refresh.setMessage(state.content() == ViewState.Content.RAW
                ? ClientText.tr("gui.statecraft.review.again", "Review again") : ClientText.tr("gui.statecraft.refresh", "Refresh"));
        PendingOperations.Entry raw = rawPending();
        command.setEditable(raw == null);
        if (raw != null && !command.getValue().equals(raw.selection().command())) command.setValue(raw.selection().command());
        run.active = raw == null && state.pending() < 0 && !command.getValue().isBlank();
    }
    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        search.tick();
        command.tick();
        if (shownView != state.view() || !java.util.Objects.equals(shownOutput, state.output()) || shownContent != state.content()
                || state.view() == null && !java.util.Objects.equals(shownPlaceholder, state.placeholder())) rebuildContent();
        controls();
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEC121923);
        Component heading = state.content() == ViewState.Content.TYPED && state.view() != null
                ? ClientText.of(state.view().title()) : title;
        graphics.drawString(font, font.plainSubstrByWidth(heading.getString(), width - 24), 12, 33, 0x71D6C1, false);
        Component hint = results.copied() ? ClientText.tr("gui.statecraft.copied", "Copied. Control+V to paste.")
                : ClientText.tr("gui.statecraft.advanced.review_hint", "Advanced mutations require server review. Back never cancels a submitted operation.");
        graphics.drawString(font, font.plainSubstrByWidth(hint.getString(), width - 24), 12, height - 20, 0xA8BECE, false);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(statusMessage()); }
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (search.isFocused()) { search(); return true; }
            if (command.isFocused()) { submit(command.getValue()); return true; }
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
