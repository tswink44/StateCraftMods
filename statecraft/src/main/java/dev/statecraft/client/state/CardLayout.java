package dev.statecraft.client.state;

import java.util.ArrayList;
import java.util.List;

public final class CardLayout {
    public static final int PADDING = 8;
    public static final int GAP = 6;
    public static final int LINE_HEIGHT = 12;
    public static final int ICON_WIDTH = 24;
    public record Card(int top, int height) {
        public int bottom() { return top + height; }
    }

    private final List<Card> cards;
    private final int extent;

    public CardLayout(List<Integer> lineCounts) {
        var placed = new ArrayList<Card>();
        int top = 0;
        for (int count : lineCounts) {
            int height = Math.max(32, Math.max(1, count) * LINE_HEIGHT + PADDING * 2);
            placed.add(new Card(top, height));
            top += height + GAP;
        }
        cards = List.copyOf(placed);
        extent = Math.max(0, top - (placed.isEmpty() ? 0 : GAP));
    }

    public List<Card> cards() { return cards; }
    public int extent() { return extent; }
    public int maxScroll(int viewport) { return Math.max(0, extent - Math.max(1, viewport)); }
    public int clamp(int scroll, int viewport) { return Math.max(0, Math.min(maxScroll(viewport), scroll)); }
    public int indexAt(int pixel) {
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            if (pixel >= card.top() && pixel < card.bottom()) return i;
        }
        return -1;
    }
    public int reveal(int index, int scroll, int viewport) {
        if (index < 0 || index >= cards.size()) return clamp(scroll, viewport);
        Card card = cards.get(index);
        if (card.top() < scroll || card.height() > viewport && card.top() >= scroll + viewport) {
            return clamp(card.top(), viewport);
        }
        if (card.height() <= viewport && card.bottom() > scroll + viewport) {
            return clamp(card.bottom() - viewport, viewport);
        }
        return clamp(scroll, viewport);
    }
}
