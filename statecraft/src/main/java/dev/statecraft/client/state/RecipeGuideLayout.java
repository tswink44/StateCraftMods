package dev.statecraft.client.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pixel geometry and memory-only selection, independent of Minecraft and rendering. */
public final class RecipeGuideLayout {
    public static final int MIN_WIDTH = 320;
    public static final int MIN_HEIGHT = 240;
    public static final int SLOT_SIZE = 20;
    public static final int MATERIAL_HEIGHT = 22;
    private final Rect frame;
    private final Rect selector;
    private final Rect body;
    private final Rect diagram;
    private final Rect legend;
    private final Rect grid;
    private final Rect output;
    private final Rect previous;
    private final Rect counter;
    private final Rect next;
    private final int footerY;
    private final int materialsPerPage;
    private final int choicesPerPage;

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public RecipeGuideLayout(int width, int height) {
        if (width < MIN_WIDTH || height < MIN_HEIGHT) {
            throw new IllegalArgumentException("Recipe guide requires at least 320 by 240 GUI pixels");
        }
        int frameWidth = Math.min(760, width - 16);
        int frameHeight = Math.min(368, height - 16);
        frame = new Rect((width - frameWidth) / 2, (height - frameHeight) / 2, frameWidth, frameHeight);
        selector = new Rect(frame.x(), frame.y() + 24, frameWidth, 22);
        footerY = frame.bottom() - 20;
        int third = (frameWidth - 8) / 3;
        previous = new Rect(frame.x(), footerY - 26, third, 20);
        next = new Rect(frame.right() - third, previous.y(), third, 20);
        counter = new Rect(previous.right() + 4, previous.y(), next.x() - previous.right() - 8, 20);
        body = new Rect(frame.x(), selector.bottom() + 6, frameWidth, previous.y() - selector.bottom() - 12);
        int innerWidth = frameWidth - 16;
        int diagramWidth = Math.min(260, Math.max(128, (innerWidth - 12) * 45 / 100));
        diagram = new Rect(body.x() + 8, body.y() + 8, diagramWidth, body.height() - 16);
        legend = new Rect(diagram.right() + 12, diagram.y(), innerWidth - diagramWidth - 12, diagram.height());
        grid = new Rect(diagram.x() + (diagramWidth - 124) / 2,
                body.y() + 24 + (body.height() - 120) / 2, 60, 60);
        output = new Rect(grid.x() + 100, grid.y() + 18, 24, 24);
        materialsPerPage = Math.min(9, (legend.height() - 36) / MATERIAL_HEIGHT);
        choicesPerPage = Math.min(8, (body.height() - 8) / 30);
    }

    public Rect frame() { return frame; }
    public int headerY() { return frame.y() + 3; }
    public Rect selector() { return selector; }
    public Rect body() { return body; }
    public Rect diagram() { return diagram; }
    public Rect legend() { return legend; }
    public Rect grid() { return grid; }
    public Rect output() { return output; }
    public Rect station() { return new Rect(grid.x() + 20, output.y(), 24, 24); }
    public Rect arrow() { return new Rect(grid.x() + 68, grid.y() + 26, 24, 8); }
    public int gridLabelY() { return grid.y() - 16; }
    public int methodY() { return grid.bottom() + 7; }
    public Rect previous() { return previous; }
    public Rect counter() { return counter; }
    public Rect next() { return next; }
    public int materialsPerPage() { return materialsPerPage; }
    public int choicesPerPage() { return choicesPerPage; }

    public Rect gridSlot(int slot) {
        if (slot < 0 || slot >= 9) throw new IndexOutOfBoundsException(slot);
        return new Rect(grid.x() + slot % 3 * SLOT_SIZE, grid.y() + slot / 3 * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    public Rect materialRow(int row) {
        if (row < 0 || row >= materialsPerPage) throw new IndexOutOfBoundsException(row);
        return new Rect(legend.x(), legend.y() + 14 + row * MATERIAL_HEIGHT, legend.width(), MATERIAL_HEIGHT);
    }

    public Rect materialPrevious() { return new Rect(legend.x(), legend.bottom() - 18, 20, 18); }
    public Rect materialNext() { return new Rect(legend.right() - 20, legend.bottom() - 18, 20, 18); }
    public Rect materialCounter() { return new Rect(legend.x() + 24, legend.bottom() - 18, legend.width() - 48, 18); }

    public Rect choiceRow(int row) {
        if (row < 0 || row >= choicesPerPage) throw new IndexOutOfBoundsException(row);
        return new Rect(body.x() + 4, body.y() + 4 + row * 30, body.width() - 8, 28);
    }

    public Rect footerButton(int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        int third = (frame.width() - 8) / 3;
        int left = frame.x() + index * (third + 4);
        return new Rect(left, footerY, index == 2 ? frame.right() - left : third, 20);
    }

    /** Empty source cells keep their indices; only padding outside the pattern is -1. */
    public static List<Integer> shapedSlots(int width, int height) {
        if (width < 1 || width > 3 || height < 1 || height > 3) {
            throw new IllegalArgumentException("Not a three-by-three crafting pattern");
        }
        List<Integer> result = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            int column = slot % 3;
            int row = slot / 3;
            result.add(column < width && row < height ? row * width + column : -1);
        }
        return List.copyOf(result);
    }

    public static List<Integer> shapelessSlots(int ingredientCount) {
        if (ingredientCount < 0 || ingredientCount > 9) {
            throw new IllegalArgumentException("Not a three-by-three crafting recipe");
        }
        List<Integer> result = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) result.add(slot < ingredientCount ? slot : -1);
        return List.copyOf(result);
    }

    public static int pageCount(int count, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("Page size must be positive");
        return count <= 0 ? 1 : (count - 1) / pageSize + 1;
    }

    public static int pageForIndex(int index, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("Page size must be positive");
        return Math.max(0, index) / pageSize;
    }

    public static int pageStart(int page, int count, int pageSize) {
        return Math.max(0, Math.min(page, pageCount(count, pageSize) - 1)) * pageSize;
    }

    public static int alternativeIndex(long milliseconds, int alternatives) {
        return alternatives <= 0 ? -1 : (int) Math.floorMod(Math.floorDiv(milliseconds, 1200), (long) alternatives);
    }

    public static final class Selection<K> {
        private List<K> keys = List.of();
        private K retained;
        private int anchor;

        public void reconcile(List<K> replacement) {
            keys = List.copyOf(replacement);
            if (keys.isEmpty()) return;
            int existing = retained == null ? -1 : keys.indexOf(retained);
            anchor = existing >= 0 ? existing : Math.min(anchor, keys.size() - 1);
            retained = keys.get(anchor);
        }

        public boolean select(K key) {
            int index = key == null ? -1 : keys.indexOf(key);
            if (index < 0) return false;
            boolean changed = !Objects.equals(retained, key);
            retained = key;
            anchor = index;
            return changed;
        }

        public boolean step(int direction) {
            if (keys.isEmpty()) return false;
            int target = (int) Math.max(0, Math.min((long) anchor + direction, keys.size() - 1L));
            return select(keys.get(target));
        }

        public Optional<K> selected() { return keys.isEmpty() ? Optional.empty() : Optional.of(retained); }
        public int index() { return keys.isEmpty() ? -1 : anchor; }
    }
}
