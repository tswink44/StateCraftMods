package dev.statecraft.client;

import dev.statecraft.api.GovernanceAccess.Kind;
import dev.statecraft.api.ui.ClaimMapMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GovernmentOverviewLayoutTest {
    @Test
    void layoutsStayInsideTheContentWidthAtEverySupportedScale() {
        for (int width : new int[]{320, 426, 560, 619, 620, 640, 720, 854, 1280}) {
            for (int height : new int[]{240, 360, 480, 720}) {
                for (int count : new int[]{0, 1, 12}) {
                    GovernmentOverviewLayout layout = GovernmentOverviewLayout.of(width, height, true, true, count, count, 8);
                    assertEquals(54, layout.viewport().y());
                    assertEquals(height - 36, layout.viewport().bottom());
                    for (var rect : rectangles(layout)) {
                        assertTrue(rect.width() > 0 && rect.height() > 0);
                        assertTrue(rect.x() >= 12, rect.toString());
                        assertTrue(rect.right() <= width - 12, rect.toString());
                        assertTrue(rect.y() >= 0);
                        assertTrue(rect.bottom() <= layout.contentHeight(), rect.toString());
                    }
                    assertEquals(count, layout.children().rows().size());
                    assertEquals(count, layout.officers().rows().size());
                    assertEquals(8, layout.actions().rows().size());
                }
            }
        }
    }

    @Test
    void sectionsDoNotOverlapAndRostersStackOnlyOnNarrowWindows() {
        for (int width : new int[]{320, 560, 620, 854}) {
            GovernmentOverviewLayout layout = GovernmentOverviewLayout.of(width, 480, true, true, 12, 3, 8);
            assertTrue(layout.identity().bottom() < layout.facts().get(0).y());
            assertTrue(layout.facts().get(layout.facts().size() - 1).bottom() < layout.description().y());
            assertTrue(layout.description().bottom() < layout.actions().bounds().y());
            assertTrue(layout.actions().bounds().bottom() < layout.children().bounds().y());
            if (width < 620) assertTrue(layout.children().bounds().bottom() < layout.officers().bounds().y());
            else assertTrue(layout.children().bounds().right() < layout.officers().bounds().x());
            for (var section : List.of(layout.actions(), layout.children(), layout.officers())) {
                for (var rect : section.rows()) {
                    assertTrue(rect.x() > section.bounds().x());
                    assertTrue(rect.right() < section.bounds().right());
                    assertTrue(rect.y() >= section.bounds().y() + 26);
                    assertTrue(rect.bottom() < section.bounds().bottom());
                }
            }
        }
    }

    @Test
    void everyCardCanBeScrolledFullyIntoViewAtThreeHundredTwentyByTwoHundredForty() {
        GovernmentOverviewLayout layout = GovernmentOverviewLayout.of(320, 240, true, true, 12, 12, 8);
        for (var rect : rectangles(layout)) {
            if (rect.height() > layout.viewport().height()) continue;
            int scroll = layout.clampScroll(rect.y());
            assertTrue(layout.fullyVisible(rect, scroll), rect.toString());
            assertTrue(layout.screenY(rect.y(), scroll) >= layout.viewport().y());
            assertTrue(layout.screenY(rect.y(), scroll) + rect.height() <= layout.viewport().bottom());
        }
        assertEquals(0, layout.clampScroll(-500));
        assertEquals(layout.maxScroll(), layout.clampScroll(Integer.MAX_VALUE));
        assertFalse(layout.fullyVisible(layout.identity(), layout.maxScroll()));
    }

    @Test
    void emptyAndAbsentMetadataHaveValidCompactLayouts() {
        GovernmentOverviewLayout compact = GovernmentOverviewLayout.of(320, 240, false, false, 0, 0, 0);
        assertEquals(2, compact.facts().size());
        assertNull(compact.description());
        assertTrue(compact.actions().rows().isEmpty());
        assertTrue(compact.children().rows().isEmpty());
        assertTrue(compact.officers().rows().isEmpty());
        assertTrue(compact.children().previous().right() < compact.children().next().x());
        assertTrue(compact.officers().next().bottom() < compact.contentHeight());
        GovernmentOverviewLayout large = GovernmentOverviewLayout.of(1280, 1080, false, false, 0, 0, 0);
        assertEquals(0, large.maxScroll());
    }

    @Test
    void pagedActionMenusNeverOverlapHeadingsOrFooter() {
        for (int width : new int[]{320, 426, 599, 600, 854, 1280}) {
            for (int height : new int[]{240, 360, 480, 720}) {
                var slots = GovernmentOverviewLayout.actionSlots(width, height);
                assertFalse(slots.isEmpty());
                for (int i = 0; i < slots.size(); i++) {
                    var slot = slots.get(i);
                    assertTrue(slot.x() >= 12);
                    assertTrue(slot.right() <= width - 12);
                    assertTrue(slot.y() >= 78);
                    assertTrue(slot.bottom() <= height - 48);
                    for (int j = i + 1; j < slots.size(); j++) assertFalse(overlaps(slot, slots.get(j)));
                }
            }
        }
    }

    @Test
    void onlyManagingNationsCanClaimAndAssignmentsDoNotRequireExistingChildren() {
        assertEquals(List.of(ClaimMapMode.CLAIM, ClaimMapMode.ASSIGN_STATE), GovernmentOverviewLayout.mapModes(Kind.NATION, true));
        assertEquals(List.of(ClaimMapMode.ASSIGN_CITY), GovernmentOverviewLayout.mapModes(Kind.STATE, true));
        assertTrue(GovernmentOverviewLayout.mapModes(Kind.CITY, true).isEmpty());
        for (Kind kind : Kind.values()) assertTrue(GovernmentOverviewLayout.mapModes(kind, false).isEmpty());
        GovernmentOverviewLayout emptyNation = GovernmentOverviewLayout.of(320, 240, false, false, 0, 1, 6, 3);
        assertEquals(3, emptyNation.shortcuts().size());
        GovernmentOverviewLayout emptyState = GovernmentOverviewLayout.of(320, 240, true, false, 0, 1, 3, 2);
        assertEquals(2, emptyState.shortcuts().size());
    }

    @Test
    void directMapAndInboxButtonsFitAndScrollBeforeTheCategoryGrid() {
        for (int width : new int[]{320, 560, 620, 854}) {
            for (int count = 0; count <= 3; count++) {
                var layout = GovernmentOverviewLayout.of(width, 240, true, true, 12, 12, 6, count);
                assertEquals(count, layout.shortcuts().size());
                for (var shortcut : layout.shortcuts()) {
                    assertTrue(shortcut.y() > layout.description().bottom());
                    assertTrue(shortcut.bottom() < layout.actions().bounds().y());
                    assertTrue(shortcut.x() >= 12 && shortcut.right() <= width - 12);
                    assertTrue(layout.fullyVisible(shortcut, layout.clampScroll(shortcut.y())));
                }
            }
        }
    }

    private static boolean overlaps(GovernmentOverviewLayout.Rect first, GovernmentOverviewLayout.Rect second) {
        return first.x() < second.right() && first.right() > second.x() && first.y() < second.bottom() && first.bottom() > second.y();
    }

    private static List<GovernmentOverviewLayout.Rect> rectangles(GovernmentOverviewLayout layout) {
        var result = new ArrayList<GovernmentOverviewLayout.Rect>();
        result.add(layout.identity());
        result.addAll(layout.facts());
        result.addAll(layout.shortcuts());
        if (layout.description() != null) result.add(layout.description());
        for (var section : List.of(layout.actions(), layout.children(), layout.officers())) {
            result.add(section.bounds());
            result.addAll(section.rows());
            if (section.previous() != null) result.add(section.previous());
            if (section.next() != null) result.add(section.next());
        }
        return result;
    }
}
