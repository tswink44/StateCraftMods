package dev.statecraft.client;

import dev.statecraft.client.state.EditSelection;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class RetainedEditBox extends EditBox {
    private int anchor;

    RetainedEditBox(Font font, int x, int y, int width, int height, Component label) {
        super(font, x, y, width, height, label);
    }

    @Override
    public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        anchor = Math.max(0, Math.min(getValue().length(), position));
    }

    EditSelection selection() { return new EditSelection(getCursorPosition(), anchor, isFocused()); }
    void restore(EditSelection selection) {
        EditSelection bounded = selection.bounded(getValue());
        setCursorPosition(bounded.cursor());
        setHighlightPos(bounded.anchor());
    }
}
