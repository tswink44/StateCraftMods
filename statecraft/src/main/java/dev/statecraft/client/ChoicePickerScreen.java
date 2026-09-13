package dev.statecraft.client;

import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.client.state.SearchState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class ChoicePickerScreen extends Screen {
    private final ActionFormScreen owner;
    private final String key;
    private final SearchState state;
    private final List<Button> rows = new ArrayList<>();
    private RetainedEditBox search;
    private long queryAt;
    private int scroll;
    private boolean initialized;
    private Button previous;
    private Button next;
    private Button custom;
    private Button status;

    ChoicePickerScreen(ActionFormScreen owner, String key) {
        super(ClientText.tr("gui.statecraft.choices.title", "Choose %s", owner.field(key).label()));
        this.owner = owner;
        this.key = key;
        state = owner.search(key);
    }
    ActionFormScreen owner() { return owner; }
    @Override
    protected void init() {
        if (search != null) state.selection(search.selection());
        int left = Math.max(12, width / 2 - 220);
        int panelWidth = Math.min(440, width - 24);
        search = new RetainedEditBox(font, left, 35, panelWidth, 20, ClientText.tr("gui.statecraft.choices.search", "Search choices"));
        search.setMaxLength(80);
        search.setHint(ClientText.tr("gui.statecraft.choices.search_hint", "Search names or IDs..."));
        search.setValue(state.text());
        search.restore(state.selection());
        search.setResponder(value -> {
            state.text(value);
            queryAt = ClientHooks.time() + 300;
            updated();
        });
        addRenderableWidget(search);
        int visible = Math.max(1, (height - 156) / 23);
        rows.clear();
        for (int row = 0; row < visible; row++) {
            final int index = row;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                List<FormChoice> choices = owner.field(key).choices();
                if (owner.choicesReady() && queryAt == 0 && scroll + index < choices.size()) owner.selected(key, choices.get(scroll + index));
            }).bounds(left, 62 + row * 23, panelWidth, 20).build()));
        }
        status = addRenderableWidget(Button.builder(Component.empty(), ignored -> minecraft.setScreen(new InformationScreen(
                        this, title, statusText().copy().append("\n").append(owner.field(key).hint()))))
                .bounds(left, height - 88, panelWidth, 20).build());
        custom = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.choices.custom", "Custom value..."), ignored -> useCustom())
                .bounds(left, height - 62, panelWidth, 20).build());
        int buttonWidth = (panelWidth - 8) / 3;
        previous = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.previous", "Previous"), ignored -> query(
                        Math.max(0, owner.field(key).offset() - FormSchema.PAGE_SIZE)))
                .bounds(left, height - 28, buttonWidth, 20).build());
        next = addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.next", "Next"),
                        ignored -> query(owner.field(key).offset() + FormSchema.PAGE_SIZE))
                .bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(ClientText.tr("gui.statecraft.back", "Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        updated();
        setInitialFocus(search);
        if (!initialized) {
            initialized = true;
            query(state.offset());
        }
    }
    private void query(int offset) {
        queryAt = 0;
        scroll = 0;
        state.offset(offset);
        owner.request(new FormQuery(key, state.text(), offset));
    }
    void updated() {
        if (search == null || previous == null) return;
        FormField field = owner.field(key);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, field.choices().size() - rows.size())));
        for (int row = 0; row < rows.size(); row++) {
            Button button = rows.get(row);
            int index = scroll + row;
            button.visible = index < field.choices().size();
            button.active = button.visible && owner.choicesReady() && queryAt == 0;
            if (button.visible) {
                FormChoice choice = field.choices().get(index);
                button.setMessage(Component.literal(choice.label()));
                button.setTooltip(Tooltip.create(Component.literal(choice.label() + "\n" + choice.detail() + "\n" + choice.value())));
            }
        }
        previous.active = owner.choicesReady() && queryAt == 0 && field.offset() > 0;
        next.active = owner.choicesReady() && queryAt == 0 && field.more();
        custom.visible = field.allowCustom();
        custom.active = field.allowCustom() && owner.choicesReady() && queryAt == 0;
        status.setMessage(statusText());
    }
    private Component statusText() {
        FormField field = owner.field(key);
        if (owner.locked()) return ClientText.tr("gui.statecraft.form.locked",
                "Inputs are locked. Use Operations to check status or retry the same ready operation.");
        if (owner.waiting() || queryAt != 0) return ClientText.tr("gui.statecraft.choices.loading", "Loading matching choices...");
        if (!owner.error().isEmpty()) return Component.literal(owner.error());
        if (field.choices().isEmpty()) return ClientText.tr("gui.statecraft.choices.empty",
                "No matching eligible choices. Try another search or check the field prerequisites.");
        return ClientText.tr("gui.statecraft.choices.page", "Page %s | %s–%s of %s | Scroll for more",
                field.offset() / FormSchema.PAGE_SIZE + 1, scroll + 1,
                Math.min(field.choices().size(), scroll + rows.size()), field.choices().size());
    }
    private void useCustom() {
        if (custom.active) minecraft.setScreen(new CustomValueScreen(this, owner, key,
                state.text().isBlank() ? owner.value(key) : state.text()));
    }
    @Override
    public void tick() {
        search.tick();
        owner.tickRequests();
        if (queryAt != 0 && ClientHooks.time() >= queryAt) query(0);
        updated();
    }
    @Override
    public void removed() { if (search != null) state.selection(search.selection()); }
    @Override
    public Component getNarrationMessage() { return title.copy().append(". ").append(statusText()); }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEF121923);
        graphics.drawCenteredString(font, title, width / 2, 12, 0x71D6C1);
        super.render(graphics, mouseX, mouseY, delta);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY < height - 91 && mouseY >= 60) {
            scroll = Math.max(0, Math.min(Math.max(0, owner.field(key).choices().size() - rows.size()),
                    scroll - (int) Math.signum(delta) * 2));
            updated();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && search.isFocused()) {
            query(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public void onClose() { owner.returnFromPicker(); }
    @Override
    public boolean isPauseScreen() { return false; }
}
