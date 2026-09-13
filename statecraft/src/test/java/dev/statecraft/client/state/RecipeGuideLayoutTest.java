package dev.statecraft.client.state;

import dev.statecraft.client.state.RecipeGuideLayout.Rect;
import dev.statecraft.client.state.RecipeGuideLayout.Selection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeGuideLayoutTest {
    @Test
    void twoByTwoPatternsKeepTheirOwnRowStrideInsideTheThreeByThreeGrid() {
        assertEquals(List.of(0, 1, -1, 2, 3, -1, -1, -1, -1), RecipeGuideLayout.shapedSlots(2, 2));
        assertEquals(List.of(0, -1, -1, 1, -1, -1, 2, -1, -1), RecipeGuideLayout.shapedSlots(1, 3));
        assertEquals(List.of(0, 1, 2, -1, -1, -1, -1, -1, -1), RecipeGuideLayout.shapedSlots(3, 1));
    }

    @Test
    void emptyCellsInsideAPatternAreNotCollapsedOrReplacedByPadding() {
        List<String> ingredients = List.of("paper", "", "", "gold");
        List<String> rendered = RecipeGuideLayout.shapedSlots(2, 2).stream()
                .map(index -> index < 0 ? "padding" : ingredients.get(index)).toList();
        assertEquals(List.of("paper", "", "padding", "", "gold", "padding", "padding", "padding", "padding"), rendered);
        assertEquals(1, RecipeGuideLayout.shapedSlots(2, 2).get(1));
        assertEquals(2, RecipeGuideLayout.shapedSlots(2, 2).get(3));
    }

    @Test
    void fullPatternsAndNineShapelessIngredientsUseEverySlotExactlyOnce() {
        List<Integer> full = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(full, RecipeGuideLayout.shapedSlots(3, 3));
        assertEquals(full, RecipeGuideLayout.shapelessSlots(9));
        assertEquals(List.of(0, 1, -1, -1, -1, -1, -1, -1, -1), RecipeGuideLayout.shapelessSlots(2));
        assertEquals(List.of(-1, -1, -1, -1, -1, -1, -1, -1, -1), RecipeGuideLayout.shapelessSlots(0));
    }

    @Test
    void largerOrMalformedPatternsMustUseAnExplicitNonGridFallback() {
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.shapedSlots(4, 2));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.shapedSlots(2, 4));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.shapedSlots(0, 2));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.shapedSlots(2, -1));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.shapelessSlots(10));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.shapelessSlots(-1));
    }

    @Test
    void allSlotsLabelsMaterialPagesAndNavigationFitAtTheMinimumAndLargerSizes() {
        for (int width : List.of(320, 321, 360, 427, 640, 854, 1280, 3840)) {
            for (int height : List.of(240, 241, 270, 360, 480, 720, 2160)) {
                RecipeGuideLayout layout = new RecipeGuideLayout(width, height);
                Rect screen = new Rect(0, 0, width, height);
                inside(screen, layout.frame());
                inside(layout.frame(), layout.selector());
                inside(layout.frame(), layout.body());
                assertTrue(layout.headerY() + 9 <= layout.selector().y());
                assertTrue(layout.selector().bottom() < layout.body().y());
                assertTrue(layout.body().bottom() < layout.previous().y());
                assertTrue(layout.previous().bottom() < layout.footerButton(0).y());
                assertTrue(layout.gridLabelY() >= layout.body().y());
                assertTrue(layout.gridLabelY() + 9 < layout.grid().y());
                assertTrue(layout.methodY() > layout.grid().bottom());
                assertTrue(layout.methodY() + 20 <= layout.body().bottom() - 8);
                inside(layout.diagram(), layout.grid());
                inside(layout.diagram(), layout.output());
                inside(layout.diagram(), layout.station());
                inside(layout.diagram(), layout.arrow());
                assertFalse(overlaps(layout.diagram(), layout.legend()));
                assertFalse(overlaps(layout.grid(), layout.arrow()));
                assertFalse(overlaps(layout.arrow(), layout.output()));
                for (int slot = 0; slot < 9; slot++) {
                    Rect bounds = layout.gridSlot(slot);
                    inside(layout.grid(), bounds);
                    assertEquals(20, bounds.width());
                    assertEquals(20, bounds.height());
                    for (int other = 0; other < slot; other++) assertFalse(overlaps(bounds, layout.gridSlot(other)));
                }
                assertTrue(layout.materialsPerPage() >= 3 && layout.materialsPerPage() <= 9);
                for (int row = 0; row < layout.materialsPerPage(); row++) {
                    inside(layout.legend(), layout.materialRow(row));
                    assertTrue(layout.materialRow(row).bottom() <= layout.materialPrevious().y() - 4);
                    if (row > 0) assertFalse(overlaps(layout.materialRow(row - 1), layout.materialRow(row)));
                }
                inside(layout.legend(), layout.materialPrevious());
                inside(layout.legend(), layout.materialNext());
                inside(layout.legend(), layout.materialCounter());
                assertFalse(overlaps(layout.materialPrevious(), layout.materialCounter()));
                assertFalse(overlaps(layout.materialCounter(), layout.materialNext()));
                for (int row = 0; row < layout.choicesPerPage(); row++) {
                    inside(layout.body(), layout.choiceRow(row));
                    if (row > 0) assertFalse(overlaps(layout.choiceRow(row - 1), layout.choiceRow(row)));
                }
                List<Rect> navigation = new ArrayList<>(List.of(layout.previous(), layout.counter(), layout.next()));
                for (int i = 0; i < 3; i++) navigation.add(layout.footerButton(i));
                for (int i = 0; i < navigation.size(); i++) {
                    inside(layout.frame(), navigation.get(i));
                    for (int other = 0; other < i; other++) assertFalse(overlaps(navigation.get(i), navigation.get(other)));
                }
            }
        }
    }

    @Test
    void largeWindowsDoNotMagnifyItemSlotsAndTinyWindowsUseTheScreensFallback() {
        RecipeGuideLayout large = new RecipeGuideLayout(3840, 2160);
        assertEquals(760, large.frame().width());
        assertEquals(368, large.frame().height());
        assertEquals(60, large.grid().width());
        assertEquals(24, large.output().width());
        assertThrows(IllegalArgumentException.class, () -> new RecipeGuideLayout(319, 240));
        assertThrows(IllegalArgumentException.class, () -> new RecipeGuideLayout(320, 239));
        assertThrows(IndexOutOfBoundsException.class, () -> large.gridSlot(9));
        assertThrows(IndexOutOfBoundsException.class, () -> large.gridSlot(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> large.materialRow(large.materialsPerPage()));
        assertThrows(IndexOutOfBoundsException.class, () -> large.choiceRow(large.choicesPerPage()));
        assertThrows(IndexOutOfBoundsException.class, () -> large.footerButton(3));
    }

    @Test
    void shortLastPagesReturnToRealPageBoundariesWithoutSkippingRecipes() {
        assertEquals(3, RecipeGuideLayout.pageCount(7, 3));
        assertEquals(0, RecipeGuideLayout.pageStart(0, 7, 3));
        assertEquals(3, RecipeGuideLayout.pageStart(1, 7, 3));
        assertEquals(6, RecipeGuideLayout.pageStart(2, 7, 3));
        assertEquals(6, RecipeGuideLayout.pageStart(100, 7, 3));
        assertEquals(0, RecipeGuideLayout.pageStart(-10, 7, 3));
        assertEquals(1, RecipeGuideLayout.pageForIndex(3, 3));
        assertEquals(2, RecipeGuideLayout.pageForIndex(6, 3));
        assertEquals(1, RecipeGuideLayout.pageCount(0, 3));
        assertEquals(0, RecipeGuideLayout.pageStart(8, 0, 3));
        assertEquals(0, RecipeGuideLayout.pageForIndex(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.pageCount(7, 0));
        assertThrows(IllegalArgumentException.class, () -> RecipeGuideLayout.pageForIndex(1, 0));
    }

    @Test
    void resizingReopensThePickerPageContainingTheSelectedRecipe() {
        Selection<String> selection = new Selection<>();
        selection.reconcile(List.of("a", "b", "c", "d", "e", "f", "g"));
        selection.select("f");
        for (int height : List.of(240, 270, 360, 720)) {
            RecipeGuideLayout layout = new RecipeGuideLayout(320, height);
            int size = layout.choicesPerPage();
            int page = RecipeGuideLayout.pageForIndex(selection.index(), size);
            int start = RecipeGuideLayout.pageStart(page, 7, size);
            assertTrue(start <= selection.index() && selection.index() < start + size);
            assertEquals("f", selection.selected().orElseThrow());
        }
    }

    @Test
    void recipeReloadsRetainIdsRatherThanOldObjectsOrOldSortPositions() {
        Selection<String> selection = new Selection<>();
        selection.reconcile(List.of("a", "b", "c", "d"));
        assertTrue(selection.select("c"));
        selection.reconcile(List.of("new", "d", "c", "b", "a"));
        assertEquals("c", selection.selected().orElseThrow());
        assertEquals(2, selection.index());
        selection.reconcile(List.of("a", "c", "new"));
        assertEquals("c", selection.selected().orElseThrow());
        assertEquals(1, selection.index());
        assertFalse(selection.select("removed"));
        assertEquals("c", selection.selected().orElseThrow());
    }

    @Test
    void removingTheSelectedRecipeClampsItsAnchorAndAnEmptySyncHasNoStaleTarget() {
        Selection<String> selection = new Selection<>();
        selection.reconcile(List.of("a", "b", "c"));
        selection.select("c");
        selection.reconcile(List.of("a", "b"));
        assertEquals("b", selection.selected().orElseThrow());
        assertEquals(1, selection.index());
        selection.reconcile(List.of());
        assertTrue(selection.selected().isEmpty());
        assertEquals(-1, selection.index());
        assertFalse(selection.step(1));
        selection.reconcile(List.of("b", "new"));
        assertEquals("b", selection.selected().orElseThrow());
        assertEquals(0, selection.index());
    }

    @Test
    void selectionCannotEscapeItsBoundsOrMutateTheSyncedKeySnapshot() {
        Selection<String> selection = new Selection<>();
        assertFalse(selection.select(null));
        List<String> source = new ArrayList<>(List.of("a", "b", "c"));
        selection.reconcile(source);
        assertFalse(selection.select(null));
        source.clear();
        assertFalse(selection.step(-1));
        assertTrue(selection.step(Integer.MAX_VALUE));
        assertEquals("c", selection.selected().orElseThrow());
        assertFalse(selection.step(1));
        assertTrue(selection.step(Integer.MIN_VALUE));
        assertEquals("a", selection.selected().orElseThrow());
        assertFalse(selection.select("a"));
    }

    @Test
    void alternativeCyclingHandlesSingleEmptyAndMultipleIngredients() {
        assertEquals(-1, RecipeGuideLayout.alternativeIndex(0, 0));
        assertEquals(-1, RecipeGuideLayout.alternativeIndex(1200, -1));
        assertEquals(0, RecipeGuideLayout.alternativeIndex(Long.MAX_VALUE, 1));
        assertEquals(0, RecipeGuideLayout.alternativeIndex(0, 3));
        assertEquals(0, RecipeGuideLayout.alternativeIndex(1199, 3));
        assertEquals(1, RecipeGuideLayout.alternativeIndex(1200, 3));
        assertEquals(2, RecipeGuideLayout.alternativeIndex(2400, 3));
        assertEquals(0, RecipeGuideLayout.alternativeIndex(3600, 3));
        assertEquals(2, RecipeGuideLayout.alternativeIndex(-1, 3));
    }

    private static void inside(Rect outer, Rect inner) {
        assertTrue(inner.width() > 0 && inner.height() > 0, inner.toString());
        assertTrue(inner.x() >= outer.x() && inner.y() >= outer.y()
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom(),
                () -> inner + " outside " + outer);
    }

    private static boolean overlaps(Rect first, Rect second) {
        return first.x() < second.right() && first.right() > second.x()
                && first.y() < second.bottom() && first.bottom() > second.y();
    }
}
