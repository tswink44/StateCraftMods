package dev.statecraft.client.state;

import java.util.List;
import java.util.stream.IntStream;

public record SectionLayout(int contentTop, int contentHeight, int pagingY, int toolsY, int commandY) {
    public record Cell(int x, int width) {
        public int right() { return x + width; }
    }

    public static SectionLayout of(int height, boolean filtering, boolean advanced) {
        int paging = height - (advanced ? 87 : 61);
        int top = filtering ? 98 : 72;
        return new SectionLayout(top, Math.max(16, paging - top - 6), paging, paging + 24, height - 37);
    }

    public static List<Cell> row(int left, int width, int columns, int gap) {
        if (columns < 1 || width < columns + (columns - 1) * gap || gap < 0) {
            throw new IllegalArgumentException("The controls do not fit in this row.");
        }
        int usable = width - (columns - 1) * gap;
        return IntStream.range(0, columns).mapToObj(index -> {
            int start = index * usable / columns;
            int end = (index + 1) * usable / columns;
            return new Cell(left + start + index * gap, end - start);
        }).toList();
    }
}
