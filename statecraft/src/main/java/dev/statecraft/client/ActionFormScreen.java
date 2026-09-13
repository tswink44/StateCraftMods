package dev.statecraft.client;

import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuPage;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormState;
import dev.statecraft.network.SuiteNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ActionFormScreen extends Screen {
    private final ManagementScreen parent;
    private final Screen previous;
    private final MenuPage.Action action;
    private final CommandTemplate template;
    private final FormState state = new FormState();
    private final Map<String, Integer> labels = new LinkedHashMap<>();
    private final Map<String, Integer> hints = new LinkedHashMap<>();
    private final List<AbstractWidget> inputs = new ArrayList<>();
    private int offset;
    private int shown;
    private int pending = -1;
    private int submitting = -1;
    private long requestedAt;
    private long submittedAt;
    private long refreshAt;
    private boolean initialized;
    private boolean loaded;
    private boolean metadataFailed;
    private boolean uncertain;
    private String error = "";
    private Button confirm;
    private Button refresh;

    ActionFormScreen(ManagementScreen parent, Screen previous, MenuPage.Action action) {
        super(Component.literal(action.label()));
        this.parent = parent;
        this.previous = previous;
        this.action = action;
        template = new CommandTemplate(action.command());
        loaded = template.fields().isEmpty();
    }

    @Override
    protected void init() {
        labels.clear();
        hints.clear();
        inputs.clear();
        int left = Math.max(12, width / 2 - 190);
        int fieldWidth = Math.min(380, width - 24);
        int y = 49;
        shown = 0;
        List<FormField> fields = state.schema().fields();
        offset = Math.min(offset, Math.max(0, fields.size() - 1));
        AbstractWidget initialFocus = null;
        for (int i = offset; loaded && i < fields.size(); i++) {
            FormField field = fields.get(i);
            int inputHeight = field.kind() == FormField.Kind.MULTILINE ? 44 : 20;
            int bottom = height - (error.isEmpty() ? 62 : 92);
            if (shown > 0 && y + inputHeight + 12 > bottom) {
                break;
            }
            labels.put(field.key(), y - 11);
            hints.put(field.key(), y + inputHeight + 3);
            AbstractWidget widget;
            if (field.kind() == FormField.Kind.CHOICE) {
                String selected = state.label(field.key());
                String caption = selected.isEmpty() ? field.choices().isEmpty() && !field.allowCustom()
                        ? "No eligible options" : "Choose..." : selected;
                Button choice = Button.builder(Component.literal(font.plainSubstrByWidth(caption, fieldWidth - 30) + "  v"),
                                ignored -> minecraft.setScreen(new ChoicePickerScreen(this, field.key())))
                        .bounds(left, y, fieldWidth, inputHeight).build();
                widget = choice;
            } else if (field.kind() == FormField.Kind.MULTILINE) {
                MultiLineEditBox box = new MultiLineEditBox(font, left, y, fieldWidth, inputHeight,
                        Component.literal(field.label()), Component.literal(field.label()));
                box.setCharacterLimit(2048);
                box.setValue(state.value(field.key()));
                box.setValueListener(value -> changed(field.key(), value));
                widget = box;
            } else {
                EditBox box = new EditBox(font, left, y, fieldWidth, inputHeight, Component.literal(field.label()));
                box.setMaxLength(512);
                box.setValue(state.value(field.key()));
                box.setResponder(value -> changed(field.key(), value));
                widget = box;
            }
            String tooltip = field.hint();
            if (field.kind() == FormField.Kind.CHOICE && !state.value(field.key()).isBlank()) {
                tooltip += "\nSelected: " + state.value(field.key());
            }
            widget.setTooltip(Tooltip.create(Component.literal(tooltip)));
            addRenderableWidget(widget);
            inputs.add(widget);
            if (initialFocus == null && field.kind() != FormField.Kind.CHOICE && state.value(field.key()).isBlank()) {
                initialFocus = widget;
            }
            shown++;
            y += inputHeight + 26;
        }
        if (initialFocus != null) {
            setInitialFocus(initialFocus);
        }
        if (offset > 0 || offset + shown < fields.size()) {
            Button back = addRenderableWidget(Button.builder(Component.literal("Previous fields"), ignored -> {
                offset = Math.max(0, offset - Math.max(1, shown));
                rebuildWidgets();
            }).bounds(left, height - 54, 112, 20).build());
            back.active = offset > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal("Next fields"), ignored -> {
                offset = Math.min(fields.size() - 1, offset + Math.max(1, shown));
                rebuildWidgets();
            }).bounds(left + 116, height - 54, 112, 20).build());
            next.active = offset + shown < fields.size();
        }
        int buttonWidth = (fieldWidth - 8) / 3;
        confirm = addRenderableWidget(Button.builder(Component.literal("Confirm"), ignored -> confirm())
                .bounds(left, height - 28, buttonWidth, 20).build());
        refresh = addRenderableWidget(Button.builder(Component.literal("Refresh choices"), ignored -> {
            error = "";
            request(FormQuery.INITIAL);
        }).bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(left + (buttonWidth + 4) * 2, height - 28, buttonWidth, 20).build());
        updateControls();
        if (!initialized) {
            initialized = true;
            if (!loaded) {
                request(FormQuery.INITIAL);
            }
        }
    }

    private void changed(String key, String value) {
        error = "";
        if (!state.change(key, value).isEmpty() || metadataFailed) {
            refreshAt = System.currentTimeMillis() + 300;
        }
        updateControls();
    }

    private void updateControls() {
        if (confirm == null) {
            return;
        }
        confirm.active = loaded && !metadataFailed && pending < 0 && submitting < 0 && refreshAt == 0 && !uncertain && state.complete();
        confirm.setTooltip(error.isEmpty() ? null : Tooltip.create(Component.literal(error)));
        refresh.active = submitting < 0 && !template.fields().isEmpty();
        for (int i = 0; i < inputs.size(); i++) {
            AbstractWidget input = inputs.get(i);
            FormField field = state.schema().fields().get(offset + i);
            input.active = submitting < 0 && field.dependencies().stream().noneMatch(key -> state.value(key).isBlank());
            if (input instanceof Button) {
                input.active &= !metadataFailed && pending < 0 && refreshAt == 0
                        && (field.allowCustom() || !field.choices().isEmpty() || field.more());
            }
        }
    }

    void request(FormQuery query) {
        if (submitting >= 0) {
            return;
        }
        if (pending >= 0) {
            ClientHooks.forgetForm(pending);
        }
        pending = -1;
        refreshAt = 0;
        Map<String, String> requested = state.values();
        try {
            FormContext.validateValues(action.command(), requested);
        } catch (UserError invalid) {
            metadataFailed = true;
            error = invalid.getMessage();
            updateControls();
            notifyPicker();
            return;
        }
        pending = ClientHooks.nextRequest();
        requestedAt = System.currentTimeMillis();
        long revision = state.dependencyRevision();
        ClientHooks.watchForm(pending, response -> receive(response, requested, revision));
        SuiteNetwork.requestForm(pending, parent.page().id(), action.command(), requested, query);
        updateControls();
        notifyPicker();
    }

    private void receive(SuiteNetwork.FormResponse response, Map<String, String> requested, long dependencyRevision) {
        if (response.id() != pending || !response.page().equals(parent.page().id()) || !response.command().equals(action.command())) {
            return;
        }
        pending = -1;
        if (dependencyRevision != state.dependencyRevision()) {
            request(FormQuery.INITIAL);
            return;
        }
        if (response.success()) {
            if (!response.schema().fields().stream().map(FormField::key).toList().equals(template.fields())) {
                loaded = false;
                metadataFailed = true;
                error = "The server returned an incompatible form. Install matching mod versions.";
            } else {
                state.apply(response.schema(), requested);
                loaded = true;
                metadataFailed = false;
            }
        } else {
            metadataFailed = true;
            error = response.error();
        }
        if (minecraft.screen == this) {
            rebuildWidgets();
        }
        notifyPicker();
        updateControls();
    }

    FormField field(String key) {
        return state.schema().field(key).orElseThrow(() -> new IllegalStateException("Unknown form field: " + key));
    }

    boolean waiting() { return pending >= 0; }
    boolean choicesReady() { return loaded && !metadataFailed && pending < 0; }
    String error() { return error; }

    void selected(String key, FormChoice choice) {
        state.select(key, choice);
        error = "";
        minecraft.setScreen(this);
        request(FormQuery.INITIAL);
    }

    void selectedCustom(String key, String value) {
        state.selectCustom(key, value);
        error = "";
        minecraft.setScreen(this);
        request(FormQuery.INITIAL);
    }

    String value(String key) { return state.value(key); }

    void returnFromPicker() {
        minecraft.setScreen(this);
        request(FormQuery.INITIAL);
    }

    private void notifyPicker() {
        if (minecraft != null && minecraft.screen instanceof ChoicePickerScreen picker && picker.owner() == this) {
            picker.updated();
        }
    }

    private void confirm() {
        if (!confirm.active) {
            return;
        }
        try {
            String command = template.render(state.values());
            submitting = ClientHooks.nextRequest();
            submittedAt = System.currentTimeMillis();
            error = "";
            ClientHooks.watchAction(submitting, this::actionResult);
            SuiteNetwork.request(submitting, parent.page().id(), command);
            updateControls();
        } catch (UserError e) {
            error = e.getMessage();
            rebuildWidgets();
        }
    }

    private void actionResult(SuiteNetwork.ActionResponse response) {
        if (response.id() != submitting || !response.page().equals(parent.page().id())) {
            return;
        }
        submitting = -1;
        parent.displayResult(response.success(), response.text());
        if (response.success()) {
            if (minecraft.screen == this) {
                minecraft.setScreen(parent);
            }
        } else {
            error = response.text();
            if (template.fields().isEmpty()) {
                rebuildWidgets();
            } else {
                request(FormQuery.INITIAL);
            }
        }
        updateControls();
    }

    void tickRequests() {
        long now = System.currentTimeMillis();
        if (pending >= 0 && now - requestedAt > 15000) {
            ClientHooks.forgetForm(pending);
            pending = -1;
            metadataFailed = true;
            error = "Choices could not be loaded. Use Refresh choices to retry.";
            notifyPicker();
            if (minecraft.screen == this) {
                rebuildWidgets();
            }
        }
        if (submitting >= 0 && now - submittedAt > 15000) {
            ClientHooks.forget(submitting);
            submitting = -1;
            uncertain = true;
            error = "The action may have completed. Go Back and refresh the section before retrying.";
            rebuildWidgets();
        }
        if (refreshAt != 0 && now >= refreshAt) {
            request(FormQuery.INITIAL);
        }
        updateControls();
    }

    @Override
    public void tick() {
        inputs.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).forEach(EditBox::tick);
        tickRequests();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xEC121923);
        int left = Math.max(12, width / 2 - 190);
        int fieldWidth = Math.min(380, width - 24);
        graphics.drawCenteredString(font, title, width / 2, 10, 0x71D6C1);
        graphics.drawCenteredString(font, parent.page().title(), width / 2, 24, 0x9DB4C7);
        for (FormField field : state.schema().fields()) {
            Integer y = labels.get(field.key());
            if (y != null) {
                graphics.drawString(font, font.plainSubstrByWidth(field.label(), fieldWidth), left, y, 0xDCE8F1, false);
                graphics.drawString(font, font.plainSubstrByWidth(field.hint(), fieldWidth), left, hints.get(field.key()),
                        0x94A9BC, false);
            }
        }
        if (!loaded && pending >= 0) {
            graphics.drawCenteredString(font, "Loading eligible choices...", width / 2, 55, 0xB7C9D9);
        } else if (template.fields().isEmpty()) {
            int y = 52;
            for (var line : font.split(Component.literal("Confirm this action:\n" + action.command()), width - 32)) {
                graphics.drawString(font, line, 16, y, 0xDFE9F2, false);
                y += 12;
            }
        }
        if (!error.isEmpty()) {
            int y = height - 88;
            for (var line : font.split(Component.literal(error), fieldWidth).stream().limit(3).toList()) {
                graphics.drawString(font, line, left, y, 0xFF9292, false);
                y += 10;
            }
        } else if (submitting >= 0) {
            graphics.drawCenteredString(font, "Submitting to the server...", width / 2, height - 67, 0xB7C9D9);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (submitting >= 0) {
            error = "Waiting for the server. This action has already been submitted.";
            rebuildWidgets();
            return;
        }
        if (pending >= 0) {
            ClientHooks.forgetForm(pending);
            pending = -1;
        }
        minecraft.setScreen(uncertain ? parent : previous);
        if (uncertain) {
            parent.submit(parent.page().query());
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
