package dev.statecraft.client;

import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormState;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.FormDraft;
import dev.statecraft.client.state.FormPages;
import dev.statecraft.client.state.SearchState;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.network.SuiteNetwork;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ActionFormScreen extends Screen {
    private final ManagementScreen parent;
    private final Screen previous;
    private final MenuPage page;
    private final MenuPage.Action action;
    private final CommandTemplate template;
    private final FormDraft draft;
    private final FormState state;
    private final UiScope scope;
    private final Map<String, AbstractWidget> inputs = new LinkedHashMap<>();
    private FormPages pages;
    private FormPages.Page visible;
    private int pageIndex;
    private int pending = -1;
    private long refreshAt;
    private boolean initialized;
    private boolean loaded;
    private boolean metadataFailed;
    private boolean keepingOwner;
    private UiText error = UiText.EMPTY;
    private Button confirm;
    private Button refresh;
    private Button explanation;
    private Button attention;

    ActionFormScreen(ManagementScreen parent, Screen previous, MenuPage.Action action) {
        this(parent, previous, parent.page(), action, Map.of());
    }
    ActionFormScreen(ManagementScreen parent, Screen previous, MenuPage page, MenuPage.Action action, Map<String, String> seed) {
        super(ClientText.action(page.id(), action));
        this.parent = parent;
        this.previous = previous;
        this.page = page;
        this.action = action;
        scope = ClientHooks.scope();
        template = new CommandTemplate(action.command());
        draft = ClientHooks.workspace().draft(page.id(), action.command(), seed);
        state = draft.state();
        loaded = template.fields().isEmpty() || !state.schema().fields().isEmpty();
    }

    @Override
    protected void init() {
        saveEditors();
        inputs.clear();
        int left = Math.max(12, width / 2 - 220);
        int fieldWidth = Math.min(440, width - 24);
        pages = new FormPages(state.schema().fields(), height);
        pageIndex = pages.pageContaining(draft.layoutAnchor());
        visible = pages.page(pageIndex);
        draft.firstField(visible.start());
        AbstractWidget initialFocus = null;
        for (FormPages.Slot slot : visible.slots()) {
            FormField field = state.schema().fields().get(slot.index());
            AbstractWidget input;
            if (field.kind() == FormField.Kind.CHOICE) {
                Component caption = state.label(field.key()).isEmpty()
                        ? field.choices().isEmpty() && !field.allowCustom()
                            ? ClientText.tr("gui.statecraft.choices.none_eligible", "No eligible options")
                            : ClientText.tr("gui.statecraft.choices.choose", "Choose...")
                        : Component.literal(state.label(field.key()));
                input = UiButton.create(ClientText.tr("gui.statecraft.choices.dropdown", "%s ▾", caption.getString()), ignored -> {
                    if (locked()) return;
                    keepingOwner = true;
                    draft.focused(field.key());
                    minecraft.setScreen(new ChoicePickerScreen(this, field.key()));
                }).bounds(left, slot.inputY(), fieldWidth, slot.inputHeight()).build();
            } else if (field.kind() == FormField.Kind.MULTILINE) {
                input = new DraftMultilineBox(font, left, slot.inputY(), fieldWidth, slot.inputHeight(),
                        Component.literal(field.label()), field.constraints().maxLength(), state.value(field.key()),
                        draft.selection(field.key()), value -> changed(field.key(), value));
            } else {
                RetainedEditBox box = new RetainedEditBox(font, left, slot.inputY(), fieldWidth, slot.inputHeight(),
                        Component.literal(field.label()));
                box.setMaxLength(field.constraints().maxLength());
                box.setValue(state.value(field.key()));
                box.restore(draft.selection(field.key()));
                box.setResponder(value -> changed(field.key(), value));
                input = box;
            }
            input.setTooltip(Tooltip.create(fieldDescription(field)));
            addRenderableWidget(input);
            inputs.put(field.key(), input);
            if (field.key().equals(draft.focused())) initialFocus = input;
        }
        explanation = addRenderableWidget(UiButton.create(Component.empty(), ignored -> minecraft.setScreen(
                        new InformationScreen(this, ClientText.tr("gui.statecraft.form.validation", "Form status and field details"),
                                validationDetails())))
                .bounds(left, height - 88, fieldWidth, 20).build());
        int buttonWidth = (fieldWidth - 8) / 3;
        Button back = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.form.previous", "Previous fields"), ignored -> {
            changePage(pages.page(pageIndex - 1).start());
        }).bounds(left, height - 62, buttonWidth, 20).build());
        back.active = pageIndex > 0;
        Button next = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.form.next", "Next fields"), ignored -> {
            changePage(pages.page(pageIndex + 1).start());
        }).bounds(left + buttonWidth + 4, height - 62, buttonWidth, 20).build());
        next.active = pageIndex + 1 < pages.pages().size();
        attention = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.attention", "Attention"),
                        ignored -> ClientHooks.operations(this))
                .bounds(left + (buttonWidth + 4) * 2, height - 62, buttonWidth, 20).build());
        confirm = addRenderableWidget(UiButton.create(action.intent() == ActionIntent.QUERY
                        ? ClientText.tr("gui.statecraft.form.run_query", "Run query") : ClientText.tr("gui.statecraft.form.review", "Review action"),
                        ignored -> review()).bounds(left, height - 28, buttonWidth, 20).primary().build());
        refresh = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.form.refresh_choices", "Refresh choices"),
                        ignored -> {
                            draft.serverErrors(Map.of());
                            request(FormQuery.INITIAL);
                        })
                .bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        if (initialFocus != null && initialFocus.active) setInitialFocus(initialFocus);
        else if (!inputs.isEmpty()) setInitialFocus(inputs.values().iterator().next());
        controls();
        if (!initialized) {
            initialized = true;
            if (!template.fields().isEmpty() && !locked()) request(FormQuery.INITIAL);
        }
    }

    private void saveEditors() {
        inputs.forEach((key, widget) -> {
            if (widget instanceof RetainedEditBox box) draft.selection(key, box.selection());
            else if (widget instanceof DraftMultilineBox box) draft.selection(key, box.selection());
            if (widget.isFocused()) draft.focused(key);
        });
    }
    private void changePage(int first) {
        saveEditors();
        draft.firstField(first);
        draft.focused("");
        inputs.clear();
        rebuildWidgets();
    }
    private void rebuildForm() {
        saveEditors();
        inputs.clear();
        rebuildWidgets();
    }
    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        saveEditors();
        inputs.clear();
        super.resize(minecraft, width, height);
    }
    private void changed(String key, String value) {
        if (locked()) return;
        error = UiText.EMPTY;
        draft.edited(key);
        if (!state.change(key, value).isEmpty() || metadataFailed) refreshAt = ClientHooks.time() + 300;
        controls();
    }
    boolean locked() {
        return !ClientHooks.current(scope) || ClientHooks.workspace().operations().blocked(draft.operation());
    }
    private Component reason() {
        if (!ClientHooks.current(scope)) return ClientText.tr("gui.statecraft.session.changed", "This form belongs to a different session.");
        if (locked()) {
            var operation = ClientHooks.workspace().operations().get(draft.operation());
            return operation.inFlight() ? ClientText.tr("gui.statecraft.operation.waiting",
                    "Submitted — waiting for the server. Inputs are locked.")
                    : ClientText.outcome(operation.outcome()).copy().append(". ").append(ClientText.tr(
                            "gui.statecraft.form.locked", "Inputs are locked. Use Attention to check the pending action."));
        }
        if (!error.fallback().isEmpty()) return ClientText.of(error);
        if (pending >= 0 || refreshAt != 0) return ClientText.tr("gui.statecraft.form.loading", "Loading eligible choices...");
        if (!loaded || metadataFailed) return ClientText.tr("gui.statecraft.form.retry_metadata", "Refresh choices before continuing.");
        if (!draft.errors().isEmpty()) return ClientText.tr("gui.statecraft.form.correct_fields",
                "Correct %s field(s). Open this status for the full explanations.", draft.errors().size());
        if (action.intent().requiresReview() && !ClientHooks.submissionProblem(action.command()).fallback().isEmpty()) {
            return ClientText.of(ClientHooks.submissionProblem(action.command()));
        }
        var last = ClientHooks.workspace().operations().get(draft.operation());
        if (last != null && last.terminal() && !last.outcome().success()) {
            return ClientText.outcome(last.outcome()).copy().append(": ").append(last.text());
        }
        return ClientText.tr("gui.statecraft.form.ready", "Fields %s–%s of %s. Ready for %s.",
                visible.start() + (visible.end() > 0 ? 1 : 0), visible.end(), state.schema().fields().size(),
                action.intent() == ActionIntent.QUERY ? ClientText.tr("gui.statecraft.form.query", "query").getString()
                        : ClientText.tr("gui.statecraft.form.server_review", "server review").getString());
    }
    private void controls() {
        if (confirm == null) return;
        attention.visible = ClientHooks.needsAttention();
        attention.active = attention.visible;
        confirm.active = loaded && !metadataFailed && pending < 0 && refreshAt == 0 && !locked()
                && draft.errors().isEmpty() && (!action.intent().requiresReview() || ClientHooks.submissionProblem(action.command()).fallback().isEmpty());
        UiTheme.status(explanation, reason());
        confirm.setTooltip(Tooltip.create(reason()));
        refresh.active = !locked() && pending < 0 && !template.fields().isEmpty();
        inputs.forEach((key, input) -> {
            FormField field = field(key);
            boolean dependencies = field.dependencies().stream().noneMatch(parent -> state.value(parent).isBlank());
            if (input instanceof RetainedEditBox box) box.setEditable(!locked() && dependencies);
            else input.active = !locked() && dependencies;
            if (input instanceof Button) input.active &= !metadataFailed && pending < 0 && refreshAt == 0
                    && (field.allowCustom() || !field.choices().isEmpty() || field.more());
            input.setTooltip(Tooltip.create(fieldDescription(field)));
        });
    }
    void request(FormQuery query) {
        if (locked()) return;
        ClientHooks.forgetForm(pending);
        pending = -1;
        refreshAt = 0;
        error = UiText.EMPTY;
        Map<String, String> requested = state.values();
        try {
            FormContext.validateValues(action.command(), requested);
        } catch (UserError failure) {
            metadataFailed = true;
            error = UiText.literal(failure.getMessage());
            controls();
            notifyPicker();
            return;
        }
        long revision = state.dependencyRevision();
        pending = ClientHooks.requestForm(page.id(), action.command(), requested, query,
                response -> receive(response, requested, revision), () -> {
                    pending = -1;
                    metadataFailed = true;
                    error = UiText.tr("gui.statecraft.form.timeout", "Choices could not be loaded. Use Refresh choices to retry.");
                    controls();
                    notifyPicker();
                });
        controls();
        notifyPicker();
    }
    private void receive(SuiteNetwork.FormResponse response, Map<String, String> requested, long revision) {
        if (!ClientHooks.current(scope) || response.id() != pending || !response.page().equals(page.id())
                || !response.command().equals(action.command())) return;
        pending = -1;
        if (locked()) return;
        if (revision != state.dependencyRevision()) { request(FormQuery.INITIAL); return; }
        if (response.success()) {
            if (!response.schema().fields().stream().map(FormField::key).toList().equals(template.fields())) {
                loaded = false;
                metadataFailed = true;
                error = UiText.tr("gui.statecraft.form.incompatible", "The server returned an incompatible form. Install matching mod versions.");
            } else {
                state.apply(response.schema(), requested);
                loaded = true;
                metadataFailed = false;
                error = UiText.EMPTY;
            }
        } else {
            metadataFailed = true;
            error = UiText.literal(response.error());
        }
        if (minecraft.screen == this) rebuildForm();
        notifyPicker();
        controls();
    }

    FormField field(String key) { return state.schema().field(key).orElseThrow(); }
    SearchState search(String key) { return draft.search(key); }
    boolean waiting() { return pending >= 0; }
    boolean choicesReady() { return loaded && !metadataFailed && pending < 0 && !locked(); }
    String error() { return ClientText.of(error).getString(); }
    String value(String key) { return state.value(key); }

    void selected(String key, FormChoice choice) {
        if (locked()) return;
        state.select(key, choice);
        draft.edited(key);
        error = UiText.EMPTY;
        minecraft.setScreen(this);
        request(FormQuery.INITIAL);
    }
    void selectedCustom(String key, String value) {
        if (locked()) return;
        var failure = field(key).constraints().error(value, field(key).label());
        if (failure.isPresent()) throw new UserError(ClientText.of(failure.get()).getString());
        state.selectCustom(key, value);
        draft.edited(key);
        error = UiText.EMPTY;
        minecraft.setScreen(this);
        request(FormQuery.INITIAL);
    }
    void returnFromPicker() {
        minecraft.setScreen(this);
        if (!locked()) request(FormQuery.INITIAL);
    }
    private void notifyPicker() {
        if (minecraft != null && minecraft.screen instanceof ChoicePickerScreen picker && picker.owner() == this) picker.updated();
    }
    private void review() {
        controls();
        if (!confirm.active) return;
        try {
            template.render(state.values());
            ActionSelection selection = draft.capture();
            if (action.intent() == ActionIntent.QUERY) {
                ClientHooks.query(parent.state(), selection);
                minecraft.setScreen(parent);
            } else {
                minecraft.setScreen(new TransactionReviewScreen(parent, this, selection, action.intent(), draft));
            }
        } catch (UserError failure) {
            error = UiText.literal(failure.getMessage());
            controls();
        }
    }
    void tickRequests() {
        if (refreshAt != 0 && ClientHooks.time() >= refreshAt && !locked()) request(FormQuery.INITIAL);
        controls();
    }
    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        inputs.values().forEach(widget -> {
            if (widget instanceof RetainedEditBox box) box.tick();
            else if (widget instanceof DraftMultilineBox box) box.tick();
        });
        tickRequests();
    }
    private Component fieldDescription(FormField field) {
        Component text = Component.literal(field.label() + "\n" + field.hint());
        UiText failure = draft.errors().get(field.key());
        if (failure != null) text = text.copy().append("\n").append(ClientText.of(failure));
        List<String> missing = field.dependencies().stream().filter(key -> state.value(key).isBlank())
                .map(key -> state.schema().field(key).map(FormField::label).orElse(key)).toList();
        if (!missing.isEmpty()) text = text.copy().append("\n").append(ClientText.tr(
                "gui.statecraft.field.depends", "Choose these fields first: %s", String.join(", ", missing)));
        return text;
    }
    private Component validationDetails() {
        Component text = reason();
        for (FormField field : state.schema().fields()) text = text.copy().append("\n\n").append(fieldDescription(field));
        return text;
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(reason()); }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        int left = Math.max(12, width / 2 - 220);
        int fieldWidth = Math.min(440, width - 24);
        UiTheme.header(graphics, title, width, 10);
        graphics.drawString(font, font.plainSubstrByWidth(ClientText.page(page).getString(), width - 24), 12, 28, UiTheme.MUTED, false);
        Map<String, UiText> errors = draft.errors();
        for (FormPages.Slot slot : visible.slots()) {
            FormField field = state.schema().fields().get(slot.index());
            graphics.fill(left - 4, slot.labelY() - 4, left + fieldWidth + 4, slot.messageY() + 12, UiTheme.SURFACE);
            graphics.fill(left - 4, slot.labelY() - 4, left - 2, slot.messageY() + 12,
                    errors.containsKey(field.key()) ? UiTheme.NEGATIVE : UiTheme.BORDER);
            graphics.drawString(font, font.plainSubstrByWidth(field.label(), fieldWidth), left, slot.labelY(), UiTheme.TEXT, false);
            Component hint = errors.containsKey(field.key()) ? ClientText.tr("gui.statecraft.field.error", "Error: %s",
                    ClientText.of(errors.get(field.key())).getString()) : Component.literal(field.hint());
            graphics.drawString(font, font.plainSubstrByWidth(hint.getString(), fieldWidth), left, slot.messageY(),
                    errors.containsKey(field.key()) ? UiTheme.NEGATIVE : UiTheme.MUTED, false);
        }
        if (!loaded || template.fields().isEmpty()) {
            Component message = !loaded ? ClientText.tr("gui.statecraft.form.loading", "Loading eligible choices...")
                    : ClientText.tr("gui.statecraft.form.no_fields", "This action needs no additional fields. Continue to run the query or request a server review.");
            int y = 54;
            for (var line : font.split(message, fieldWidth)) {
                if (y + 10 >= height - FormPages.FOOTER_HEIGHT) break;
                graphics.drawString(font, line, left, y, UiTheme.TEXT, false);
                y += 12;
            }
        }
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void removed() {
        saveEditors();
        inputs.clear();
        if (!keepingOwner) {
            ClientHooks.forgetForm(pending);
            pending = -1;
            refreshAt = 0;
        }
        keepingOwner = false;
    }
    @Override
    public void onClose() {
        ClientHooks.forgetForm(pending);
        pending = -1;
        refreshAt = 0;
        minecraft.setScreen(previous);
    }
    @Override
    public boolean isPauseScreen() { return false; }
}
