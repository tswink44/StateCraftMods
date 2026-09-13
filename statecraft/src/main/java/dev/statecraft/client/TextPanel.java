package dev.statecraft.client;

import dev.statecraft.api.ui.EntityRef;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

final class TextPanel extends AbstractWidget {
    record Entry(Component text, String copy, EntityRef entity) {
        static Entry text(Component text) { return new Entry(text, text.getString(), EntityRef.NONE); }
    }
    private record Line(FormattedCharSequence text, int entry) {}
    private final Font font;
    private final Consumer<Entry> selected;
    private final Consumer<EntityRef> open;
    private final IntConsumer scrolled;
    private List<Entry> entries = List.of();
    private List<Line> lines = List.of();
    private int scroll;
    private int selection = -1;
    private long clickedAt;
    private boolean copied;
    private boolean legacyCopy;

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
        var wrapped = new ArrayList<Line>();
        selection = -1;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            for (var line : font.split(entry.text(), Math.max(20, width - 16))) wrapped.add(new Line(line, i));
            if (entry.text().getString().isEmpty()) wrapped.add(new Line(FormattedCharSequence.EMPTY, i));
            if (retainedEntity.present() ? retainedEntity.equals(entry.entity()) : !retainedText.isEmpty()
                    && retainedText.equals(entry.copy())) selection = i;
        }
        lines = List.copyOf(wrapped);
        scroll = Math.max(0, Math.min(maxScroll(), retainedScroll));
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
    private int visibleLines() { return Math.max(1, (height - 8) / 11); }
    private int maxScroll() { return Math.max(0, lines.size() - visibleLines()); }
    private void scrollTo(int value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
        scrolled.accept(scroll);
    }
    private void select(int index, boolean reveal) {
        if (entries.isEmpty()) return;
        selection = Math.max(0, Math.min(entries.size() - 1, index));
        selected.accept(entries.get(selection));
        if (reveal) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).entry() != selection) continue;
                if (i < scroll || i >= scroll + visibleLines()) scrollTo(i);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || button != 0 && button != 1) return false;
        int line = scroll + (int) ((mouseY - getY() - 4) / 11);
        if (line >= 0 && line < lines.size()) {
            int previous = selection;
            select(lines.get(line).entry(), false);
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
        scrollTo(scroll - (int) Math.signum(delta) * 3);
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
            case GLFW.GLFW_KEY_PAGE_UP -> scrollTo(scroll - visibleLines());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollTo(scroll + visibleLines());
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
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xE6091018);
        graphics.renderOutline(getX(), getY(), width, height, isFocused() ? 0xFF71D6C1 : 0xFF344B60);
        graphics.enableScissor(getX() + 2, getY() + 2, getX() + width - 3, getY() + height - 2);
        for (int i = 0; i < visibleLines() && i + scroll < lines.size(); i++) {
            Line line = lines.get(i + scroll);
            int y = getY() + 4 + i * 11;
            if (line.entry() == selection) {
                graphics.fill(getX() + 2, y - 1, getX() + width - 6, y + 10, 0xFF29475F);
                graphics.drawString(font, ">", getX() + 3, y, 0xFFFFFF, false);
            }
            graphics.drawString(font, line.text(), getX() + 10, y, 0xDFE9F2, false);
        }
        graphics.disableScissor();
        if (maxScroll() > 0) {
            int thumb = Math.max(4, (height - 4) * visibleLines() / lines.size());
            int y = getY() + 2 + (height - 4 - thumb) * scroll / maxScroll();
            graphics.fill(getX() + width - 4, y, getX() + width - 2, y + thumb, 0xFF71D6C1);
        }
    }
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, selection() == null ? getMessage() : selection().text());
        output.add(NarratedElementType.USAGE, ClientText.tr("gui.statecraft.results.usage",
                "Up and Down select a row. Enter opens details. Right-click or Control+C copies the full row. Control+Shift+C copies all results."));
    }
}
