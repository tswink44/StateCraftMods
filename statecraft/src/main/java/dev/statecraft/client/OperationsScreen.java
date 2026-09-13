package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.client.state.PendingOperations;
import dev.statecraft.client.state.UiScope;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class OperationsScreen extends Screen {
    private final Screen previous;
    private final UiScope scope;
    private OperationRef selected = OperationRef.NONE;
    private TextPanel panel;
    private Button check;
    private Button retry;
    private Button details;
    private Button copyReference;
    private Button status;
    private OperationRef copiedReference = OperationRef.NONE;
    private int scroll;
    private long revision = -1;

    OperationsScreen(Screen previous) {
        super(ClientText.tr("gui.statecraft.operations.title", "Operations"));
        this.previous = previous;
        scope = ClientHooks.scope();
    }
    private PendingOperations.Entry entry() {
        return ClientHooks.current(scope) ? ClientHooks.workspace().operations().get(selected) : null;
    }
    @Override
    protected void init() {
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.history", "Server receipts"),
                        ignored -> ClientHooks.navigate(UiQuery.page("statecraft:operations")))
                .bounds(width - 122, 8, 110, 18).build());
        status = addRenderableWidget(Button.builder(Component.empty(), ignored -> minecraft.setScreen(new InformationScreen(
                        this, ClientText.tr("gui.statecraft.operations.help", "Operation recovery"), explanation())))
                .bounds(12, 30, width - 24, 14).build());
        panel = addRenderableWidget(new TextPanel(font, 12, 49, width - 24, height - 120,
                entry -> {
                    if (entry.entity().present()) selected = new OperationRef(scope.world(), UUID.fromString(entry.entity().id()));
                }, ClientHooks::openEntity, value -> scroll = value));
        int buttonWidth = (width - 32) / 3;
        check = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.check", "Check status"),
                        ignored -> ClientHooks.checkOperation(selected))
                .bounds(12, height - 62, buttonWidth, 20).build());
        retry = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.retry", "Retry same ID"),
                        ignored -> ClientHooks.retry(selected))
                .bounds(16 + buttonWidth, height - 62, buttonWidth, 20).build());
        details = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.receipt", "Open receipt"),
                        ignored -> ClientHooks.openEntity(new EntityRef("statecraft", EntityRef.Kind.OPERATION, selected.id().toString())))
                .bounds(20 + buttonWidth * 2, height - 62, buttonWidth, 20).build());
        copyReference = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.copy_ref", "Copy reference"),
                        ignored -> {
                            if (entry() != null) {
                                minecraft.keyboardHandler.setClipboard(ClientText.of(scope.referenceText(selected)).getString());
                                copiedReference = selected;
                                controls();
                            }
                        }).bounds(12, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.operations.reload", "Reload recovery"), ignored -> {
            ClientHooks.loadRecovery();
            updateRows();
        }).bounds(16 + buttonWidth, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(20 + buttonWidth * 2, height - 28, buttonWidth, 20).build());
        updateRows();
        controls();
        setInitialFocus(panel);
    }
    private Component explanation() {
        if (!ClientHooks.recoveryError().fallback().isEmpty()) return ClientText.of(ClientHooks.recoveryError());
        var entry = entry();
        if (entry == null) return ClientText.tr("gui.statecraft.operations.explain",
                "Select an operation. Check status never repeats it. Only READY with captured original inputs permits Retry same ID. Unknown and uncertain receipts must not be repeated.");
        if (entry.inFlight()) return entry.checking()
                ? ClientText.tr("gui.statecraft.operations.checking", "Checking the original operation; no action is being repeated.")
                : ClientText.tr("gui.statecraft.operation.waiting", "Submitted — waiting for the server. Inputs are locked.");
        Component reason = ClientText.outcome(entry.outcome()).copy().append(". ").append(entry.text());
        if (entry.outcome().uncertain()) reason = reason.copy().append("\n").append(ClientText.tr(
                "gui.statecraft.operations.audit_help",
                "Use Copy reference to give an operator your world, player and operation UUIDs. After the operator audits and reconciles or recovers the receipt, use Check status. Do not dismiss UNKNOWN or repeat the original action."));
        if (entry.selection() == null && !entry.terminal()) return reason.copy().append("\n").append(ClientText.tr(
                "gui.statecraft.operations.inputs_not_saved",
                "Private inputs were not saved to disk. After a restart this client can check status, but cannot reconstruct or retry the original request. Do not submit a replacement; ask an operator to inspect the receipt."));
        return reason;
    }
    private void updateRows() {
        if (!ClientHooks.current(scope)) return;
        var rows = new ArrayList<TextPanel.Entry>();
        var entries = ClientHooks.workspace().operations().entries();
        if (!selected.present() && !entries.isEmpty()) selected = entries.get(entries.size() - 1).operation();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            Component status = entry.inFlight() ? entry.checking()
                    ? ClientText.tr("gui.statecraft.operations.checking_short", "Checking status")
                    : ClientText.tr("gui.statecraft.operations.submitted", "Submitted")
                    : ClientText.outcome(entry.outcome());
            Component text = status.copy().append("\n").append(entry.operation().id().toString());
            if (!entry.text().isEmpty()) text = text.copy().append("\n").append(entry.text());
            rows.add(new TextPanel.Entry(text, text.getString(),
                    new EntityRef("statecraft", EntityRef.Kind.OPERATION, entry.operation().id().toString())));
        }
        if (rows.isEmpty()) rows.add(TextPanel.Entry.text(ClientText.tr("gui.statecraft.operations.empty",
                "No pending operations or session receipts. Submitted actions remain here even after their screens close.")));
        panel.content(rows, scroll, selected.present()
                ? new EntityRef("statecraft", EntityRef.Kind.OPERATION, selected.id().toString()) : EntityRef.NONE, "");
        revision = ClientHooks.workspace().operations().revision();
    }
    private void controls() {
        var entry = entry();
        check.active = entry != null && !entry.terminal() && !entry.inFlight();
        retry.active = entry != null && entry.retryable();
        details.active = selected.present();
        copyReference.active = entry != null;
        copyReference.setMessage(selected.present() && selected.equals(copiedReference)
                ? ClientText.tr("gui.statecraft.operations.ids_copied", "IDs copied")
                : ClientText.tr("gui.statecraft.operations.copy_ref", "Copy reference"));
        status.setMessage(explanation());
    }
    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        if (revision != ClientHooks.workspace().operations().revision()) updateRows();
        controls();
    }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(explanation()); }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEF121923);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), width - 144), 12, 12, 0x71D6C1, false);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { minecraft.setScreen(previous); }
    @Override
    public boolean isPauseScreen() { return false; }
}
