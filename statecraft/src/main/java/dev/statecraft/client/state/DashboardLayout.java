package dev.statecraft.client.state;

import java.util.ArrayList;
import java.util.List;

public final class DashboardLayout {
    public static final int GAP = 8;
    public static final int TOP = 48;
    public static final int BOTTOM = 44;
    public record Box(int x, int y, int width, int height) {}

    private DashboardLayout() {}

    public static int columns(int screenWidth) { return screenWidth >= 620 ? 2 : 1; }
    public static int width(int screenWidth) { return Math.min(900, screenWidth - 32); }
    public static int left(int screenWidth) { return (screenWidth - width(screenWidth)) / 2; }
    public static int cardWidth(int screenWidth) { return (width(screenWidth) - (columns(screenWidth) - 1) * GAP) / columns(screenWidth); }

    public static List<Box> cards(int screenWidth, int top, List<Integer> heights) {
        int columns = columns(screenWidth), cardWidth = cardWidth(screenWidth), y = top;
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < heights.size(); i += columns) {
            int rowHeight = 0;
            for (int column = 0; column < columns && i + column < heights.size(); column++) {
                rowHeight = Math.max(rowHeight, heights.get(i + column));
            }
            for (int column = 0; column < columns && i + column < heights.size(); column++) {
                boxes.add(new Box(left(screenWidth) + column * (cardWidth + GAP), y, cardWidth, rowHeight));
            }
            y += rowHeight + GAP;
        }
        return List.copyOf(boxes);
    }
}
