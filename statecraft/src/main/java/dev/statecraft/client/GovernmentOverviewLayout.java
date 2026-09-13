package dev.statecraft.client;

import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.ui.ClaimMapMode;
import dev.statecraft.api.ui.GovernmentOverview;
import dev.statecraft.api.ui.PersonalDashboard;
import java.util.ArrayList;
import java.util.List;

/** Logical content coordinates are independent of Minecraft and the retained scroll position. */
public record GovernmentOverviewLayout(Rect viewport, Rect identity, List<Rect> facts, Rect description,
                                       List<Rect> shortcuts, Section actions, Section children, Section officers, int contentHeight) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double x, double y) {
            return x >= this.x && x < right() && y >= this.y && y < bottom();
        }
    }

    public record Section(Rect bounds, List<Rect> rows, Rect previous, Rect next) {
        public Section { rows = List.copyOf(rows); }
    }

    public GovernmentOverviewLayout {
        facts = List.copyOf(facts);
        shortcuts = List.copyOf(shortcuts);
    }

    public static GovernmentOverviewLayout of(int width, int height, boolean parent, boolean description,
                                              int childCount, int officerCount, int groupCount) {
        return of(width, height, parent, description, childCount, officerCount, groupCount, 0);
    }

    public static GovernmentOverviewLayout of(int width, int height, boolean parent, boolean description,
                                              int childCount, int officerCount, int groupCount, int shortcutCount) {
        if (width < 240 || height < 180 || childCount < 0 || childCount > PersonalDashboard.PAGE_SIZE
                || officerCount < 0 || officerCount > PersonalDashboard.PAGE_SIZE
                || groupCount < 0 || groupCount > GovernmentOverview.MAX_GROUPS || shortcutCount < 0 || shortcutCount > 3) {
            throw new IllegalArgumentException("Invalid government overview layout.");
        }
        int full = width - 24;
        Rect viewport = new Rect(12, 54, full, height - 90);
        Rect identity = new Rect(12, 0, full, 66);
        List<Rect> facts = grid(12, 74, full, width >= 560 ? 3 : 2, parent ? 3 : 2, 54, 8);
        int y = facts.get(facts.size() - 1).bottom() + 8;
        Rect prose = description ? new Rect(12, y, full, 40) : null;
        if (prose != null) y = prose.bottom() + 8;
        List<Rect> shortcuts = grid(12, y, full, Math.max(1, Math.min(shortcutCount, width >= 620 ? 3 : 2)), shortcutCount, 42, 6);
        if (!shortcuts.isEmpty()) y = shortcuts.get(shortcuts.size() - 1).bottom() + 8;
        int columns = width >= 720 ? 4 : width >= 520 ? 3 : 2;
        List<Rect> categories = grid(20, y + 26, full - 16, columns, groupCount, 42, 6);
        int actionsHeight = categories.isEmpty() ? 58 : categories.get(categories.size() - 1).bottom() - y + 8;
        Section actions = new Section(new Rect(12, y, full, actionsHeight), categories, null, null);
        y = actions.bounds().bottom() + 8;
        int rosterWidth = width >= 620 ? (full - 8) / 2 : full;
        Section children = roster(12, y, rosterWidth, childCount);
        Section officers = roster(width >= 620 ? 20 + rosterWidth : 12,
                width >= 620 ? y : children.bounds().bottom() + 8, rosterWidth, officerCount);
        return new GovernmentOverviewLayout(viewport, identity, facts, prose, shortcuts, actions, children, officers,
                Math.max(children.bounds().bottom(), officers.bounds().bottom()) + 8);
    }

    public static List<ClaimMapMode> mapModes(Kind kind, boolean management) {
        if (!management) return List.of();
        return switch (kind) {
            case NATION -> List.of(ClaimMapMode.CLAIM, ClaimMapMode.ASSIGN_STATE);
            case STATE -> List.of(ClaimMapMode.ASSIGN_CITY);
            case CITY -> List.of();
        };
    }

    public int maxScroll() { return Math.max(0, contentHeight - viewport.height()); }
    public int clampScroll(int scroll) { return Math.max(0, Math.min(maxScroll(), scroll)); }
    public int screenY(int logicalY, int scroll) { return viewport.y() + logicalY - clampScroll(scroll); }
    public boolean fullyVisible(Rect rect, int scroll) {
        int y = screenY(rect.y(), scroll);
        return y >= viewport.y() && y + rect.height() <= viewport.bottom();
    }

    public static List<Rect> actionSlots(int width, int height) {
        if (width < 240 || height < 180) throw new IllegalArgumentException("Invalid action menu layout.");
        int columns = width >= 600 ? 2 : 1;
        int rows = Math.max(1, (height - 126 + 6) / 48);
        return grid(12, 78, width - 24, columns, rows * columns, 42, 6);
    }

    private static Section roster(int x, int y, int width, int count) {
        List<Rect> rows = grid(x + 8, y + 26, width - 16, 1, count, 32, 4);
        int paging = y + 26 + Math.max(1, count) * 36 + 4;
        int half = (width - 20) / 2;
        return new Section(new Rect(x, y, width, paging - y + 28), rows,
                new Rect(x + 8, paging, half, 20), new Rect(x + 12 + half, paging, half, 20));
    }

    private static List<Rect> grid(int x, int y, int width, int columns, int count, int height, int gap) {
        var rows = new ArrayList<Rect>();
        int cell = (width - gap * (columns - 1)) / columns;
        for (int i = 0; i < count; i++) {
            int column = i % columns;
            rows.add(new Rect(x + column * (cell + gap), y + i / columns * (height + gap),
                    column == columns - 1 ? width - column * (cell + gap) : cell, height));
        }
        return List.copyOf(rows);
    }
}
