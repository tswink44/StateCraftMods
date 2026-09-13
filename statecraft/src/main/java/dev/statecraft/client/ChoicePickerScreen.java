package dev.statecraft.client;

import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class ChoicePickerScreen extends Screen {
    private final ActionFormScreen owner;
    private final String key;
    private final List<Button> rows = new ArrayList<>();
    private EditBox search;
    private String searchText = "";
    private long queryAt;
    private int scroll;
    private boolean initialized;
    private Button previous;
    private Button next;
    private Button custom;

    ChoicePickerScreen(ActionFormScreen owner, String key) {
        super(Component.literal("Choose " + owner.field(key).label()));
        this.owner = owner;
        this.key = key;
    }

    ActionFormScreen owner() { return owner; }

    @Override
    protected void init() {
        int left = Math.max(12, width / 2 - 220);
        int panelWidth = Math.min(440, width - 24);
        search = new EditBox(font, left, 35, panelWidth, 20, Component.literal("Search choices"));
        search.setMaxLength(80);
        search.setHint(Component.literal("Search names or IDs..."));
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            queryAt = System.currentTimeMillis() + 300;
            rows.forEach(button -> button.active = false);
            previous.active = false;
            next.active = false;
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        int visible = Math.max(1, (height - 132) / 23);
        rows.clear();
        for (int row = 0; row < visible; row++) {
            final int index = row;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), ignored -> {
                List<FormChoice> choices = owner.field(key).choices();
                if (scroll + index < choices.size()) {
                    owner.selected(key, choices.get(scroll + index));
                }
            }).bounds(left, 62 + row * 23, panelWidth, 20).build()));
        }
        int buttonWidth = (panelWidth - 8) / 3;
        previous = addRenderableWidget(Button.builder(Component.literal("Previous page"), ignored -> query(
                        Math.max(0, owner.field(key).offset() - FormSchema.PAGE_SIZE)))
                .bounds(left, height - 28, buttonWidth, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal("Next page"),
                        ignored -> query(owner.field(key).offset() + FormSchema.PAGE_SIZE))
                .bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        custom = addRenderableWidget(Button.builder(Component.literal("Custom value..."), ignored -> useCustom())
                .bounds(left, height - 54, Math.min(180, panelWidth), 20).build());
        updated();
        if (!initialized) {
            initialized = true;
            query(0);
        }
    }

    private void query(int offset) {
        queryAt = 0;
        scroll = 0;
        owner.request(new FormQuery(key, searchText, offset));
    }

    void updated() {
        if (search == null || previous == null) {
            return;
        }
        FormField field = owner.field(key);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, field.choices().size() - rows.size())));
        for (int row = 0; row < rows.size(); row++) {
            Button button = rows.get(row);
            int index = scroll + row;
            button.visible = index < field.choices().size();
            button.active = button.visible && owner.choicesReady() && queryAt == 0;
            if (button.visible) {
                FormChoice choice = field.choices().get(index);
                button.setMessage(Component.literal(font.plainSubstrByWidth(choice.label(), button.getWidth() - 14)));
                button.setTooltip(Tooltip.create(Component.literal(choice.label() + "\n" + choice.detail() + "\n" + choice.value())));
            }
        }
        previous.active = owner.choicesReady() && queryAt == 0 && field.offset() > 0;
        next.active = owner.choicesReady() && queryAt == 0 && field.more();
        custom.visible = field.allowCustom();
        custom.active = field.allowCustom() && owner.choicesReady() && queryAt == 0;
    }

    private void useCustom() {
        if (custom.active) {
            minecraft.setScreen(new CustomValueScreen(this, owner, key,
                    searchText.isBlank() ? owner.value(key) : searchText));
        }
    }

    @Override
    public void tick() {
        search.tick();
        owner.tickRequests();
        if (queryAt != 0 && System.currentTimeMillis() >= queryAt) {
            query(0);
        }
        updated();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEF121923);
        graphics.drawCenteredString(font, title, width / 2, 12, 0x71D6C1);
        FormField field = owner.field(key);
        String status;
        if (owner.waiting() || queryAt != 0) {
            status = "Loading matching choices...";
        } else if (!owner.error().isEmpty()) {
            status = owner.error();
        } else if (field.choices().isEmpty()) {
            status = searchText.isEmpty() ? field.hint() : "No matching eligible choices. Try another search.";
        } else {
            status = "Page " + (field.offset() / FormSchema.PAGE_SIZE + 1) + " | "
                    + (scroll + 1) + "-" + Math.min(field.choices().size(), scroll + rows.size())
                    + " of " + field.choices().size() + " | Scroll";
        }
        int left = Math.max(12, width / 2 - 220);
        graphics.drawString(font, font.plainSubstrByWidth(status, Math.min(440, width - 24)),
                left, height - 69, owner.error().isEmpty() ? 0xA8BECE : 0xFF9292, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, owner.field(key).choices().size() - rows.size()),
                scroll - (int) Math.signum(delta) * 2));
        updated();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER && search.isFocused()) {
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
