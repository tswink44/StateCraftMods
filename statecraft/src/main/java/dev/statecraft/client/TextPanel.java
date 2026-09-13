package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.client.state.CardLayout;
import dev.statecraft.client.state.UiPresentation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class TextPanel extends AbstractWidget {
    record Entry(Component text, String copy, EntityRef entity, UiPresentation.Tone tone, UiPresentation.Icon icon) {
        Entry(Component text, String copy, EntityRef entity) {
            this(text, copy, entity, UiPresentation.Tone.NORMAL, UiPresentation.icon(entity.kind()));
        }
        static Entry text(Component text) { return new Entry(text, text.getString(), EntityRef.NONE); }
        static Entry text(Component text, UiPresentation.Tone tone) {
            return new Entry(text, text.getString(), EntityRef.NONE, tone, UiPresentation.Icon.NONE);
        }
        static Entry field(Component label, Component value, UiPresentation.Tone tone) {
            Component text = label.copy().withStyle(style -> style.withColor(UiTheme.MUTED & 0xFFFFFF)).append("\n")
                    .append(value.copy().withStyle(style -> style.withColor(UiTheme.color(tone) & 0xFFFFFF)));
            return new Entry(text, label.getString() + ": " + value.getString(), EntityRef.NONE, tone, UiPresentation.Icon.NONE);
        }
        static List<Entry> paragraphs(Component text) {
            return java.util.Arrays.stream(text.getString().split("\\R", -1))
                    .map(line -> Entry.text(Component.literal(line))).toList();
        }
    }
    private record Card(List<FormattedCharSequence> lines) {}
    private final Font font;
    private final Consumer<Entry> selected;
    private final Consumer<EntityRef> open;
    private final IntConsumer scrolled;
    private List<Entry> entries = List.of();
    private List<Card> cards = List.of();
    private CardLayout layout = new CardLayout(List.of());
    private int scroll;
    private int selection = -1;
    private long clickedAt;
    private boolean copied;
    private boolean legacyCopy;
    private int tooltipEntry = -1;

    TextPanel(Font font, int x, int y, int width, int height, Consumer<Entry> selected,
              Consumer<EntityRef> open, IntConsumer scrolled) {
        super(x, y, width, Math.max(16, height), ClientText.tr("gui.statecraft.results", "Results"));
        this.font = font;
        this.selected = selected;
        this.open = open;
        this.scrolled = scrolled;
    }

    void content(List<Entry> replacement, int retainedScroll, EntityRef retainedEntity, String retainedText) {
        entries = List.copyOf(replacement);
        var wrapped = new ArrayList<Card>();
        selection = -1;
        tooltipEntry = -1;
        setTooltip(null);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int inset = entry.icon() == UiPresentation.Icon.NONE ? 0 : CardLayout.ICON_WIDTH;
            List<FormattedCharSequence> lines = font.split(entry.text(), Math.max(20, width - 22 - inset));
            wrapped.add(new Card(lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : List.copyOf(lines)));
            if (retainedEntity.present() ? retainedEntity.equals(entry.entity()) : !retainedText.isEmpty()
                    && retainedText.equals(entry.copy())) selection = i;
        }
        cards = List.copyOf(wrapped);
        layout = new CardLayout(cards.stream().map(card -> card.lines().size()).toList());
        scroll = layout.clamp(retainedScroll, height);
        scrolled.accept(scroll);
    }

    Entry selection() { return selection >= 0 && selection < entries.size() ? entries.get(selection) : null; }
    boolean copied() { return copied; }
    void legacyCopy(boolean value) { legacyCopy = value; }
    String allText() { return String.join("\n", entries.stream().map(Entry::copy).toList()); }
    void copyAll() { copy(allText()); }
    void copySelected() { if (selection() != null) copy(selection().copy()); }
    void copyId() {
        if (selection() != null) copy(selection().entity().present() ? selection().entity().id()
                : dev.statecraft.api.ResultSelection.identifier(selection().copy()));
    }
    void openSelected() { if (selection() != null && selection().entity().present()) open.accept(selection().entity()); }
    private void copy(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        copied = true;
    }
    private int maxScroll() { return layout.maxScroll(height); }
    private void scrollTo(int value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        scrolled.accept(scroll);
    }
    private void select(int index, boolean reveal) {
        if (entries.isEmpty()) return;
        selection = Math.max(0, Math.min(entries.size() - 1, index));
        selected.accept(entries.get(selection));
        if (reveal) scrollTo(layout.reveal(selection, scroll, height));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || button != 0 && button != 1) return false;
        int index = layout.indexAt(scroll + (int) mouseY - getY());
        if (index >= 0) {
            int previous = selection;
            select(index, false);
            if (button == 1) copySelected();
            else if (legacyCopy && selection() != null) copyId();
            else if (selection == previous && net.minecraft.Util.getMillis() - clickedAt < 300) openSelected();
            clickedAt = net.minecraft.Util.getMillis();
        }
        return true;
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scrollTo(scroll - (int) Math.signum(delta) * CardLayout.LINE_HEIGHT * 3);
        return true;
    }
    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!isFocused()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_UP -> select(selection - 1, true);
            case GLFW.GLFW_KEY_DOWN -> select(selection + 1, true);
            case GLFW.GLFW_KEY_HOME -> { select(0, true); scrollTo(0); }
            case GLFW.GLFW_KEY_END -> { select(entries.size() - 1, true); scrollTo(maxScroll()); }
            case GLFW.GLFW_KEY_PAGE_UP -> scrollTo(scroll - height);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTo(scroll + height);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> openSelected();
            case GLFW.GLFW_KEY_C -> {
                if (!Screen.hasControlDown()) return false;
                if (Screen.hasShiftDown()) copyAll(); else copySelected();
            }
            default -> { return false; }
        }
        return true;
    }
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int hovered = isMouseOver(mouseX, mouseY) ? layout.indexAt(scroll + mouseY - getY()) : -1;
        int tooltip = hovered >= 0 ? hovered : isFocused() ? selection : -1;
        if (tooltip != tooltipEntry) {
            tooltipEntry = tooltip;
            setTooltip(tooltip < 0 ? null : Tooltip.create(entries.get(tooltip).text()));
        }
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        for (int i = 0; i < cards.size(); i++) {
            CardLayout.Card bounds = layout.cards().get(i);
            int y = getY() + bounds.top() - scroll;
            if (y + bounds.height() <= getY() || y >= getY() + height) continue;
            Entry entry = entries.get(i);
            boolean selected = i == selection;
            int right = getX() + width - 6;
            graphics.fill(getX(), y, right, y + bounds.height(), selected || hovered == i ? UiTheme.HOVER : UiTheme.SURFACE);
            int accent = entry.tone() == UiPresentation.Tone.NORMAL || entry.tone() == UiPresentation.Tone.MUTED
                    ? selected ? UiTheme.ACCENT : UiTheme.BORDER : UiTheme.color(entry.tone());
            graphics.fill(getX(), y, getX() + 2, y + bounds.height(), accent);
            if (selected) graphics.renderOutline(getX(), y, width - 6, bounds.height(), isFocused() ? UiTheme.ACCENT : UiTheme.BORDER);
            int textX = getX() + CardLayout.PADDING;
            if (entry.icon() != UiPresentation.Icon.NONE) {
                UiTheme.icon(graphics, entry.icon(), textX, y + CardLayout.PADDING);
                textX += CardLayout.ICON_WIDTH;
            }
            List<FormattedCharSequence> lines = cards.get(i).lines();
            for (int line = 0; line < lines.size(); line++) {
                int lineY = y + CardLayout.PADDING + line * CardLayout.LINE_HEIGHT;
                if (lineY + font.lineHeight <= getY() || lineY >= getY() + height) continue;
                graphics.drawString(font, lines.get(line), textX, lineY, UiTheme.color(entry.tone()), false);
            }
        }
        graphics.disableScissor();
        if (isFocused() && selection < 0) graphics.fill(getX(), getY(), getX() + 2, getY() + height, UiTheme.ACCENT);
        if (maxScroll() > 0) {
            int thumb = Math.max(8, height * height / layout.extent());
            int y = getY() + (height - thumb) * scroll / maxScroll();
            graphics.fill(getX() + width - 3, getY(), getX() + width - 1, getY() + height, UiTheme.SURFACE);
            graphics.fill(getX() + width - 3, y, getX() + width - 1, y + thumb, UiTheme.ACCENT);
        }
    }
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, selection() == null ? getMessage() : selection().text());
        output.add(NarratedElementType.USAGE, ClientText.tr("gui.statecraft.results.usage",
                "Up and Down select a row. Enter opens details. Right-click or Control+C copies the full row. Control+Shift+C copies all results."));
    }
}
