package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static dev.statecraft.client.GovernmentOverviewScreen.tr;

final class GovernmentOverviewQueryScreen extends Screen {
    private final GovernmentOverviewScreen overview;
    private final GovernmentOverviewQueryState result;
    private final UiScope scope;
    private TextPanel panel;
    private Button refresh;
    private Component shownBody;
    private int scroll;

    GovernmentOverviewQueryScreen(GovernmentOverviewScreen overview, GovernmentOverviewQueryState result) {
        super(overview.queryTitle());
        this.overview = overview;
        this.result = result;
        scope = ClientHooks.scope();
    }

    private ViewState state() { return overview.state(); }

    @Override
    protected void init() {
        panel = addRenderableWidget(new TextPanel(font, 12, 52, width - 24, height - 100,
                ignored -> {}, ignored -> {}, value -> scroll = value));
        int buttonWidth = (width - 32) / 3;
        addRenderableWidget(UiButton.create(tr("copy_result", "Copy result"), ignored -> panel.copyAll())
                .bounds(12, height - 28, buttonWidth, 20).build());
        refresh = addRenderableWidget(UiButton.create(tr("run_query_again", "Run query again"), ignored -> refreshQuery())
                .bounds(16 + buttonWidth, height - 28, buttonWidth, 20)
                .tooltip(Tooltip.create(tr("run_query_hint", "Repeat the selected query without replacing it with the government overview."))).build());
        addRenderableWidget(UiButton.create(tr("back_overview", "Back to overview"), ignored -> onClose())
                .bounds(20 + buttonWidth * 2, height - 28, width - 32 - buttonWidth * 2, 20).build());
        shownBody = null;
        update();
        setInitialFocus(panel);
    }

    private void update() {
        result.observe(state());
        String output = result.body(state());
        Component body = result.phase(state()) == GovernmentOverviewQueryState.Phase.FAILED ? ClientText.of(result.failure())
                : output.isBlank() ? ClientText.of(result.placeholder(state())) : Component.literal(output);
        if (!body.equals(shownBody)) {
            panel.content(TextPanel.Entry.paragraphs(body), scroll, EntityRef.NONE, "");
            shownBody = body;
        }
        refresh.active = ClientHooks.current(scope) && state().pending() < 0
                && state().content() == ViewState.Content.QUERY && state().explicit() != null;
    }

    private void refreshQuery() {
        if (!refresh.active) return;
        ClientHooks.refresh(state());
        update();
    }

    private Component status() {
        return switch (result.phase(state())) {
            case LOADING -> state().output().isBlank() ? tr("loading_query", "Loading the selected action's result from the server…")
                    : tr("query_previous", "Running query… Previous output is retained below.");
            case READY -> tr("query_complete", "Query complete. Back refreshes the government overview.");
            case FAILED -> tr("query_failed", "Query failed. Read the response below, retry, or return to the overview.");
            case INTERRUPTED -> tr("query_interrupted", "The query was interrupted. Run it again, or return to the overview.");
        };
    }

    @Override
    public void tick() {
        if (!ClientHooks.current(scope)) { minecraft.setScreen(null); return; }
        update();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        UiTheme.background(graphics, width, height);
        UiTheme.header(graphics, title, width, 12);
        graphics.drawString(font, font.plainSubstrByWidth(status().getString(), width - 24), 12, 34,
                result.phase(state()) == GovernmentOverviewQueryState.Phase.FAILED ? UiTheme.NEGATIVE : UiTheme.MUTED, false);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(status()); }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_F5) { refreshQuery(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        if (ClientHooks.current(scope)) overview.returnFromQuery();
        else minecraft.setScreen(null);
    }

    @Override
    public void removed() {
        if (state().pending() >= 0) ClientHooks.cancelView(state());
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
