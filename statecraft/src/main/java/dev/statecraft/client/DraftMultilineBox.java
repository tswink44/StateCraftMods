package dev.statecraft.client;

import dev.statecraft.client.state.EditSelection;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DraftMultilineBox extends AbstractWidget {
    private record Range(int beginIndex, int endIndex) {}
    private static final class Field extends MultilineTextField {
        Field(Font font, int width) { super(font, width); }
        Range selectedRange() {
            var range = getSelected();
            return new Range(range.beginIndex(), range.endIndex());
        }
        Range lineRange(int index) {
            var range = getLineView(index);
            return new Range(range.beginIndex(), range.endIndex());
        }
    }
    private final Font font;
    private final Field text;
    private int scroll;
    private int ticks;

    DraftMultilineBox(Font font, int x, int y, int width, int height, Component label, int limit,
                      String value, EditSelection selection, Consumer<String> changed) {
        super(x, y, width, height, label);
        this.font = font;
        text = new Field(font, Math.max(20, width - 12));
        text.setCharacterLimit(limit);
        text.setValue(value);
        text.setCursorListener(this::showCursor);
        EditSelection bounded = selection.bounded(value);
        text.setSelecting(false);
        text.seekCursor(Whence.ABSOLUTE, bounded.anchor());
        text.setSelecting(true);
        text.seekCursor(Whence.ABSOLUTE, bounded.cursor());
        text.setSelecting(false);
        text.setValueListener(changed);
        showCursor();
    }

    EditSelection selection() {
        var selected = text.selectedRange();
        return new EditSelection(text.cursor(),
                text.cursor() == selected.beginIndex() ? selected.endIndex() : selected.beginIndex(), isFocused());
    }
    void tick() { ticks++; }
    private int visibleLines() { return Math.max(1, (height - 8) / font.lineHeight); }
    private void showCursor() {
        int line = text.getLineAtCursor();
        if (line < scroll) scroll = line;
        if (line >= scroll + visibleLines()) scroll = line - visibleLines() + 1;
        scroll = Math.max(0, scroll);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!active || !isFocused()) return false;
        text.setSelecting(Screen.hasShiftDown());
        return text.keyPressed(key);
    }
    @Override
    public boolean charTyped(char character, int modifiers) {
        if (!active || !isFocused() || !SharedConstants.isAllowedChatCharacter(character)) return false;
        text.insertText(Character.toString(character));
        return true;
    }
    @Override
    public void onClick(double mouseX, double mouseY) {
        text.setSelecting(Screen.hasShiftDown());
        text.seekCursorToPoint(mouseX - getX() - 4, mouseY - getY() - 4 + scroll * font.lineHeight);
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!active || button != 0 || !isFocused()) return false;
        text.setSelecting(true);
        text.seekCursorToPoint(mouseX - getX() - 4, mouseY - getY() - 4 + scroll * font.lineHeight);
        return true;
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        scroll = Math.max(0, Math.min(Math.max(0, text.getLineCount() - visibleLines()),
                scroll - (int) Math.signum(delta) * 2));
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, 0xFF091018);
        graphics.renderOutline(getX(), getY(), width, height, isFocused() ? 0xFFFFFFFF : 0xFF8292A2);
        graphics.enableScissor(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2);
        String value = text.value();
        var selected = text.selectedRange();
        for (int row = 0; row < visibleLines() && row + scroll < text.getLineCount(); row++) {
            var line = text.lineRange(row + scroll);
            int y = getY() + 4 + row * font.lineHeight;
            int from = Math.max(line.beginIndex(), selected.beginIndex());
            int to = Math.min(line.endIndex(), selected.endIndex());
            if (to > from) {
                int x1 = getX() + 4 + font.width(value.substring(line.beginIndex(), from));
                int x2 = getX() + 4 + font.width(value.substring(line.beginIndex(), to));
                graphics.fill(x1, y - 1, x2, y + font.lineHeight, 0xFF325C7A);
            }
            graphics.drawString(font, value.substring(line.beginIndex(), line.endIndex()), getX() + 4, y,
                    active ? 0xFFE5EDF5 : 0xFF9BA9B7, false);
            if (isFocused() && ticks / 6 % 2 == 0 && row + scroll == text.getLineAtCursor()) {
                int cursor = Math.max(line.beginIndex(), Math.min(line.endIndex(), text.cursor()));
                int x = getX() + 4 + font.width(value.substring(line.beginIndex(), cursor));
                graphics.fill(x, y - 1, x + 1, y + font.lineHeight, 0xFFFFFFFF);
            }
        }
        graphics.disableScissor();
        if (text.getLineCount() > visibleLines()) {
            int y = getY() + 2 + scroll * Math.max(1, height - 6) / text.getLineCount();
            graphics.fill(getX() + width - 4, y, getX() + width - 2, y + 3, 0xFF71D6C1);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(text.value()));
        output.add(NarratedElementType.USAGE,
                ClientText.tr("gui.statecraft.multiline.usage", "Enter inserts a new line. Tab moves to the next control."));
    }
}
