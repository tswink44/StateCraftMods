package dev.statecraft.client;

import dev.statecraft.api.UserError;
import dev.statecraft.client.state.EditSelection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CustomValueScreen extends Screen {
    private final Screen previous;
    private final ActionFormScreen owner;
    private final String key;
    private String value;
    private Component error = Component.empty();
    private EditSelection selection;
    private DraftMultilineBox input;
    private Button use;
    private Button status;

    CustomValueScreen(Screen previous, ActionFormScreen owner, String key, String value) {
        super(ClientText.tr("gui.statecraft.choices.custom_title", "%s — Custom value", owner.field(key).label()));
        this.previous = previous;
        this.owner = owner;
        this.key = key;
        this.value = value;
        selection = EditSelection.end(value);
    }
    @Override
    protected void init() {
        if (input != null) selection = input.selection();
        int panelWidth = Math.min(440, width - 24);
        int left = (width - panelWidth) / 2;
        input = addRenderableWidget(new DraftMultilineBox(font, left, 53, panelWidth, Math.max(32, height - 131),
                Component.literal(owner.field(key).label()), owner.field(key).constraints().maxLength(), value, selection,
                text -> { value = text; error = Component.empty(); controls(); }));
        status = addRenderableWidget(UiButton.create(Component.empty(), ignored -> minecraft.setScreen(new InformationScreen(
                        this, ClientText.tr("gui.statecraft.form.validation", "Form status and field details"), reason())))
                .bounds(left, height - 62, panelWidth, 20).build());
        use = addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.choices.use_custom", "Use value"), ignored -> {
            if (!use.active) return;
            try { owner.selectedCustom(key, value.strip()); }
            catch (UserError invalid) { error = Component.literal(invalid.getMessage()); controls(); }
        }).bounds(left, height - 28, (panelWidth - 4) / 2, 20).primary().build());
        addRenderableWidget(UiButton.create(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left + (panelWidth + 4) / 2, height - 28, (panelWidth - 4) / 2, 20).build());
        controls();
        setInitialFocus(input);
    }
    private Component reason() {
        if (owner.locked()) return ClientText.tr("gui.statecraft.form.locked",
                "Inputs are locked. Use Attention to check the pending action.");
        if (!error.getString().isEmpty()) return error;
        return owner.field(key).constraints().error(value, owner.field(key).label()).map(ClientText::of)
                .orElseGet(() -> ClientText.tr("gui.statecraft.choices.custom_ready", "Ready. Enter inserts a line; Use value returns to the form."));
    }
    private void controls() {
        if (use == null) return;
        use.active = !owner.locked() && error.getString().isEmpty()
                && owner.field(key).constraints().error(value, owner.field(key).label()).isEmpty();
        input.active = !owner.locked();
        UiTheme.status(status, reason());
    }
    @Override
    public void tick() { input.tick(); owner.tickRequests(); controls(); }
    @Override
    public void removed() { if (input != null) selection = input.selection(); }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(reason()); }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 12);
        graphics.drawString(font, font.plainSubstrByWidth(owner.field(key).hint(), width - 24), 12, 34, UiTheme.MUTED, false);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public void onClose() { minecraft.setScreen(previous); }
    @Override
    public boolean isPauseScreen() { return false; }
}
