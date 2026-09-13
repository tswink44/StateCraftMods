package dev.statecraft.client;

import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.PreviewQuote;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.FormDraft;
import dev.statecraft.client.state.PendingOperations;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.UiPresentation;
import dev.statecraft.network.SuiteNetwork;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class TransactionReviewScreen extends Screen {
    private final ManagementScreen parent;
    private final Screen previous;
    private final ActionSelection selection;
    private final ActionIntent intent;
    private final FormDraft draft;
    private final UiScope scope;
    private PreviewQuote quote;
    private OperationRef operation = OperationRef.NONE;
    private UiText error = UiText.EMPTY;
    private Map<String, UiText> fieldErrors = Map.of();
    private String previousFingerprint = "";
    private boolean changedTerms;
    private boolean initialized;
    private int pending = -1;
    private int scroll;
    private TextPanel card;
    private Button confirm;
    private Button refresh;
    private Button check;
    private Button retry;
    private Button status;
    private Button attention;
    private long operationRevision = -1;

    TransactionReviewScreen(ManagementScreen parent, Screen previous, ActionSelection selection, ActionIntent intent, FormDraft draft) {
        super(ClientText.tr("gui.statecraft.review.title", "Review transaction"));
        this.parent = parent;
        this.previous = previous;
        this.selection = selection;
        this.intent = intent;
        this.draft = draft;
        scope = ClientHooks.scope();
        var existing = ClientHooks.workspace().operations().matching(selection);
        if (existing != null) {
            operation = existing.operation();
            if (draft != null) draft.operation(operation);
        }
    }
    @Override
    protected void init() {
        card = addRenderableWidget(new TextPanel(font, 12, 38, width - 24, height - 139,
                ignored -> {}, ignored -> {}, value -> scroll = value));
        status = addRenderableWidget(UiButton.create(Component.empty(), ignored -> minecraft.setScreen(
                        new InformationScreen(this, ClientText.tr("gui.statecraft.review.status", "Review status"), reason())))
                .bounds(12, height - 92, width - 24, 16).build());
        int buttonWidth = (width - 32) / 3;
        check = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.operations.check", "Check status"),
                        ignored -> ClientHooks.checkOperation(operation))
                .bounds(12, height - 70, buttonWidth, 20).build());
        retry = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.operations.retry", "Retry safely"),
                        ignored -> ClientHooks.retry(operation))
                .bounds(16 + buttonWidth, height - 70, buttonWidth, 20).build());
        attention = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.attention", "Attention"),
                        ignored -> ClientHooks.operations(this))
                .bounds(20 + buttonWidth * 2, height - 70, buttonWidth, 20).build());
        confirm = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.review.confirm", "Confirm reviewed terms"), ignored -> confirm())
                .bounds(12, height - 28, buttonWidth, 20).primary().build());
        refresh = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.review.refresh", "Refresh quote"), ignored -> requestQuote())
                .bounds(16 + buttonWidth, height - 28, buttonWidth, 20).build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(20 + buttonWidth * 2, height - 28, buttonWidth, 20).build());
        buildCard();
        controls();
        setInitialFocus(card);
        if (!initialized) {
            initialized = true;
            if (!operation.present()) requestQuote();
        }
    }
    private PendingOperations.Entry entry() {
        return ClientHooks.current(scope) ? ClientHooks.workspace().operations().get(operation) : null;
    }
    private boolean locked() { return entry() != null && !entry().terminal(); }
    private void requestQuote() {
        if (!ClientHooks.current(scope) || locked() || pending >= 0) return;
        if (!ClientHooks.clock().fresh(ClientHooks.time())) ClientHooks.refreshSession();
        if (quote != null) previousFingerprint = quote.preview().fingerprint();
        quote = null;
        operation = OperationRef.NONE;
        error = UiText.EMPTY;
        fieldErrors = Map.of();
        pending = ClientHooks.preview(selection, this::receive, () -> {
            pending = -1;
            error = UiText.tr("gui.statecraft.review.timeout", "The preview did not arrive. Nothing was submitted. Refresh the quote explicitly.");
            buildCard();
            controls();
        });
        buildCard();
        controls();
    }
    private void receive(SuiteNetwork.PreviewResponse response) {
        if (!ClientHooks.current(scope) || response.id() != pending || !selection.equals(response.selection())) return;
        pending = -1;
        if (response.success() && response.quote() != null && scope.world().equals(response.quote().operation().world())) {
            quote = response.quote();
            error = UiText.EMPTY;
            fieldErrors = Map.of();
            changedTerms = !previousFingerprint.isEmpty() && !previousFingerprint.equals(quote.preview().fingerprint());
            if (draft != null) draft.serverErrors(Map.of());
        } else {
            error = response.error();
            fieldErrors = response.fieldErrors();
            if (draft != null) {
                draft.serverErrors(fieldErrors);
                for (int i = 0; i < draft.state().schema().fields().size(); i++) {
                    if (fieldErrors.containsKey(draft.state().schema().fields().get(i).key())) {
                        draft.firstField(i);
                        draft.focused(draft.state().schema().fields().get(i).key());
                        break;
                    }
                }
            }
        }
        buildCard();
        controls();
    }
    private void confirm() {
        controls();
        if (!confirm.active || quote == null) return;
        try {
            var entry = ClientHooks.submit(quote, selection, intent, parent.state(), draft);
            if (entry != null) operation = entry.operation();
            else error = UiText.tr("gui.statecraft.review.not_sent", "Nothing was submitted. Check the session, recovery storage and quote expiry.");
        } catch (UserError | IllegalArgumentException | IllegalStateException failure) {
            error = UiText.literal(failure.getMessage());
        }
        buildCard();
        controls();
    }
    private Component reason() {
        if (!ClientHooks.current(scope)) return ClientText.tr("gui.statecraft.session.changed", "This form belongs to a different session.");
        var entry = entry();
        if (entry != null) {
            if (!ClientHooks.recoveryError().fallback().isEmpty()) return ClientText.of(ClientHooks.recoveryError());
            if (entry.inFlight()) return entry.checking()
                    ? ClientText.tr("gui.statecraft.operations.checking", "Checking the original operation; no action is being repeated.")
                    : ClientText.tr("gui.statecraft.operation.waiting", "Submitted — waiting for the server. Inputs are locked.");
            return ClientText.outcome(entry.outcome()).copy().append(". ").append(entry.text());
        }
        if (!error.fallback().isEmpty()) return ClientText.of(error);
        if (pending >= 0) return ClientText.tr("gui.statecraft.review.loading", "Preparing server-reviewed terms. Nothing has been submitted.");
        if (!ClientHooks.submissionProblem(selection.template()).fallback().isEmpty()) {
            return ClientText.of(ClientHooks.submissionProblem(selection.template()));
        }
        if (!ClientHooks.clock().fresh(ClientHooks.time())) return ClientText.tr("gui.statecraft.review.clock",
                "Refreshing server time. Confirmation is disabled until the session clock is current.");
        if (quote == null) return ClientText.tr("gui.statecraft.review.no_quote", "Request a server quote before confirming.");
        if (!ClientHooks.clock().validUntil(quote.expiresAt(), ClientHooks.time())) return ClientText.tr(
                "gui.statecraft.review.expired", "Quote expired. Refresh explicitly and review the new terms before confirming.");
        if (changedTerms) return ClientText.tr("gui.statecraft.review.changed",
                "Terms changed. Read the new material terms; confirmation is never automatic.");
        return ClientText.tr("gui.statecraft.review.ready", "Review all terms and warnings before confirming. Back does not submit.");
    }
    private void controls() {
        if (confirm == null) return;
        attention.visible = ClientHooks.needsAttention();
        attention.active = attention.visible;
        boolean fresh = quote != null && ClientHooks.clock().validUntil(quote.expiresAt(), ClientHooks.time());
        confirm.active = ClientHooks.current(scope) && !operation.present() && pending < 0 && fresh
                && error.fallback().isEmpty() && ClientHooks.submissionProblem(selection.template()).fallback().isEmpty();
        refresh.active = ClientHooks.current(scope) && !locked() && pending < 0;
        check.active = locked() && !entry().inFlight();
        retry.active = entry() != null && entry().retryable();
        UiTheme.status(status, reason());
        confirm.setTooltip(Tooltip.create(reason()));
    }
    private void buildCard() {
        if (card == null) return;
        var lines = new ArrayList<TextPanel.Entry>();
        if (quote != null) {
            Component heading = ClientText.of(quote.preview().title());
            lines.add(new TextPanel.Entry(heading, heading.getString(), EntityRef.NONE,
                    UiPresentation.Tone.ACCENT, UiPresentation.Icon.PAPER));
            for (var line : quote.preview().lines()) lines.add(TextPanel.Entry.field(ClientText.of(line.label()), ClientText.of(line.value()),
                    UiPresentation.reviewTone(line.label().key(), line.label().fallback(), line.material())));
            if (!quote.preview().warning().fallback().isEmpty()) lines.add(TextPanel.Entry.text(
                    ClientText.tr("gui.statecraft.review.warning", "Warning: %s", ClientText.of(quote.preview().warning()).getString()),
                    UiPresentation.Tone.WARNING));
        }
        if (intent == ActionIntent.RAW) {
            lines.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.advanced.captured", "Captured advanced command: %s", selection.command())));
            lines.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.advanced.caution",
                    "Advanced commands are conservative: inspect all server warnings. A result is retained for copying; it will not be automatically repeated."),
                    UiPresentation.Tone.WARNING));
        }
        if (changedTerms) lines.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.review.changed",
                "Terms changed. Read the new material terms; confirmation is never automatic."), UiPresentation.Tone.WARNING));
        if (!error.fallback().isEmpty()) lines.add(TextPanel.Entry.text(ClientText.of(error), UiPresentation.Tone.NEGATIVE));
        fieldErrors.forEach((field, message) -> {
            String label = draft == null ? dev.statecraft.api.form.FormBuilder.label(field)
                    : draft.state().schema().field(field).map(dev.statecraft.api.form.FormField::label)
                        .orElse(dev.statecraft.api.form.FormBuilder.label(field));
            lines.add(TextPanel.Entry.field(Component.literal(label), ClientText.of(message), UiPresentation.Tone.NEGATIVE));
        });
        if (entry() != null) {
            lines.add(TextPanel.Entry.text(ClientText.outcome(entry().outcome()), entry().outcome().uncertain()
                    ? UiPresentation.Tone.WARNING : entry().outcome().success() ? UiPresentation.Tone.POSITIVE : UiPresentation.Tone.NORMAL));
            if (!entry().text().isEmpty()) lines.add(TextPanel.Entry.text(Component.literal(entry().text())));
        }
        if (entry() != null && entry().outcome().uncertain()) {
            lines.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.review.recovery",
                    "Do not repeat this action. Check its status or contact an operator for help."), UiPresentation.Tone.WARNING));
        }
        if (quote == null && pending >= 0) lines.add(0, TextPanel.Entry.text(ClientText.tr("gui.statecraft.review.loading",
                "Preparing server-reviewed terms. Nothing has been submitted.")));
        card.content(lines, scroll, EntityRef.NONE, "");
    }
    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        if (!ClientHooks.clock().fresh(ClientHooks.time())) ClientHooks.refreshSession();
        if (operationRevision != ClientHooks.workspace().operations().revision()) {
            operationRevision = ClientHooks.workspace().operations().revision();
            buildCard();
            if (entry() != null && entry().outcome() == ActionOutcome.COMPLETED) {
                minecraft.setScreen(parent);
                return;
            }
        }
        controls();
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 12);
        Component expiry = quote == null ? ClientText.tr("gui.statecraft.review.not_confirmed", "No confirmation sent")
                : ClientText.tr("gui.statecraft.review.expires", "Quote expires in %s seconds",
                        ClientHooks.clock().secondsRemaining(quote.expiresAt(), ClientHooks.time()));
        graphics.drawCenteredString(font, font.plainSubstrByWidth(expiry.getString(), width - 24), width / 2,
                height - 43, quote != null && !ClientHooks.clock().validUntil(quote.expiresAt(), ClientHooks.time())
                        ? UiTheme.WARNING : UiTheme.MUTED);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(reason()); }
    @Override
    public void removed() { ClientHooks.forgetPreview(pending); pending = -1; }
    @Override
    public void onClose() { minecraft.setScreen(previous); }
    @Override
    public boolean isPauseScreen() { return false; }
}
