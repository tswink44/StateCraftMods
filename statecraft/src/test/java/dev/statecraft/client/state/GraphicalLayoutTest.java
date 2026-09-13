package dev.statecraft.client.state;

import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.ui.EntityRef;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicalLayoutTest {
    @Test
    void cardsHaveSeparateHitAreasAndReachableFinalLines() {
        CardLayout layout = new CardLayout(List.of(1, 2, 17, 1));
        assertEquals(0, layout.cards().get(0).top());
        for (int i = 0; i < layout.cards().size(); i++) {
            var card = layout.cards().get(i);
            assertTrue(card.height() >= 32);
            assertEquals(i, layout.indexAt(card.top()));
            assertEquals(i, layout.indexAt(card.bottom() - 1));
            assertEquals(-1, layout.indexAt(card.bottom()));
            if (i > 0) assertEquals(CardLayout.GAP, card.top() - layout.cards().get(i - 1).bottom());
        }
        for (int viewport : List.of(16, 49, 107, 220, 480)) {
            int end = layout.clamp(Integer.MAX_VALUE, viewport);
            assertEquals(Math.max(0, layout.extent() - viewport), end);
            assertTrue(end + viewport >= layout.cards().get(3).bottom());
            assertEquals(0, layout.clamp(-10, viewport));
        }
        assertEquals(-1, layout.indexAt(-1));
        assertEquals(0, new CardLayout(List.of()).maxScroll(50));
    }

    @Test
    void keyboardRevealHandlesLargeCardsAndScrollRestoration() {
        CardLayout layout = new CardLayout(List.of(1, 40, 1, 1));
        int top = layout.cards().get(1).top();
        assertEquals(top, layout.reveal(1, 0, 32));
        assertEquals(0, layout.reveal(0, 200, 60));
        int lastScroll = layout.reveal(3, 0, 90);
        assertEquals(layout.cards().get(3).bottom(), lastScroll + 90);
        assertEquals(lastScroll, layout.clamp(lastScroll, 90));
        assertEquals(0, layout.clamp(lastScroll, 1000));
    }

    @Test
    void managementFiltersAndAdvancedControlsFitTheSmallestWindow() {
        for (int height : List.of(240, 270, 360, 480, 720)) {
            for (boolean filter : List.of(false, true)) {
                for (boolean advanced : List.of(false, true)) {
                    SectionLayout layout = SectionLayout.of(height, filter, advanced);
                    assertTrue(layout.contentTop() >= (filter ? 98 : 72));
                    assertTrue(layout.contentHeight() >= 32);
                    assertTrue(layout.contentTop() + layout.contentHeight() + 6 <= layout.pagingY());
                    assertTrue(layout.pagingY() + 20 < layout.toolsY());
                    assertTrue(layout.toolsY() + 20 <= (advanced ? layout.commandY() : height - 12));
                    if (advanced) assertTrue(layout.commandY() + 20 < height - 12);
                }
            }
            assertEquals(26, SectionLayout.of(height, false, false).contentHeight()
                    - SectionLayout.of(height, true, false).contentHeight());
        }
    }

    @Test
    void footerCellsUseAllAvailableWidthWithoutOverlapOrOffScreenButtons() {
        for (int width : List.of(320, 321, 427, 640, 853, 1280)) {
            for (int columns : List.of(2, 3, 4, 6)) {
                var cells = SectionLayout.row(12, width - 24, columns, 4);
                assertEquals(12, cells.get(0).x());
                assertEquals(width - 12, cells.get(cells.size() - 1).right());
                for (int i = 0; i < cells.size(); i++) {
                    assertTrue(cells.get(i).width() >= 46);
                    if (i > 0) assertEquals(4, cells.get(i).x() - cells.get(i - 1).right());
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> SectionLayout.row(12, 10, 6, 4));
    }

    @Test
    void collapsingFilterKeepsItsPrivateUnappliedDraftAndSelection() {
        SectionFilter filter = new SectionFilter("", EditSelection.end(""));
        assertFalse(filter.open());
        filter.toggle();
        filter.text("private bank draft");
        EditSelection selection = new EditSelection(3, 9, true);
        filter.selection(selection);
        filter.toggle();
        assertFalse(filter.open());
        assertEquals("private bank draft", filter.text());
        assertEquals(selection, filter.selection());
        filter.toggle();
        assertEquals(selection, filter.selection());
        SectionFilter otherView = new SectionFilter("", EditSelection.end(""));
        assertFalse(otherView.open());
        assertEquals("", otherView.text());
        assertTrue(new SectionFilter("active", EditSelection.end("active")).open());
    }

    @Test
    void illustrativeIconsComeOnlyFromTypedKindsAndCategories() {
        for (var kind : EntityRef.Kind.values()) assertNotNull(UiPresentation.icon(kind));
        for (var category : MenuCategory.values()) assertNotEquals(UiPresentation.Icon.NONE, UiPresentation.categoryIcon(category));
        assertEquals(UiPresentation.Icon.NONE, UiPresentation.icon(EntityRef.Kind.NONE));
        assertEquals(UiPresentation.Icon.GOVERNMENT, UiPresentation.icon(EntityRef.Kind.GOVERNMENT));
        assertEquals(UiPresentation.Icon.ACCOUNT, UiPresentation.icon(EntityRef.Kind.ACCOUNT));
        assertEquals(UiPresentation.Icon.CLAIM, UiPresentation.icon(EntityRef.Kind.CLAIM));
        assertEquals(UiPresentation.Icon.PAPER, UiPresentation.icon(EntityRef.Kind.BILL));
        assertEquals(UiPresentation.Icon.MAIL, UiPresentation.icon(EntityRef.Kind.MAIL));
    }
}
