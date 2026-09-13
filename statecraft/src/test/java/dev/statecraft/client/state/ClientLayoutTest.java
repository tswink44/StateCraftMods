package dev.statecraft.client.state;

import dev.statecraft.api.Money;
import dev.statecraft.api.form.FormConstraints;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.form.FormValidation;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientLayoutTest {
    private static FormField field(String key, FormField.Kind kind) {
        return new FormField(key, key, kind, "", "", "Long eligibility hint", List.of(), false, List.of(), 0, false);
    }

    @Test
    void backwardPagingReturnsToActualBoundariesWhenTheLastPageIsShorter() {
        var fields = IntStream.range(0, 5).mapToObj(i -> field("f" + i, FormField.Kind.TEXT)).toList();
        FormPages pages = new FormPages(fields, 270);
        assertEquals(List.of(0, 2, 4), pages.pages().stream().map(FormPages.Page::start).toList());
        int last = pages.pageContaining(4);
        assertEquals(4, pages.page(last).start());
        assertEquals(2, pages.page(last - 1).start());
        assertEquals(0, pages.page(last - 2).start());
        assertEquals(5, pages.page(last).end());
    }

    @Test
    void multilineLabelsInputsAndFieldErrorsDoNotOverlapEachOtherOrTheFixedFooter() {
        var fields = List.of(field("body", FormField.Kind.MULTILINE), field("amount", FormField.Kind.TEXT),
                field("purpose", FormField.Kind.MULTILINE), field("account", FormField.Kind.CHOICE), field("count", FormField.Kind.TEXT));
        for (int height : List.of(240, 270, 360, 480)) {
            FormPages pages = new FormPages(fields, height);
            int expected = 0;
            for (var page : pages.pages()) {
                assertEquals(expected, page.start());
                int previousBottom = FormPages.TOP;
                for (var slot : page.slots()) {
                    assertEquals(expected++, slot.index());
                    assertTrue(slot.labelY() >= previousBottom);
                    assertTrue(slot.labelY() + 9 <= slot.inputY());
                    assertTrue(slot.inputY() + slot.inputHeight() < slot.messageY());
                    assertTrue(slot.messageY() + 9 <= height - FormPages.FOOTER_HEIGHT, "height=" + height);
                    previousBottom = slot.messageY() + 9;
                }
                assertEquals(expected, page.end());
            }
            assertEquals(fields.size(), expected);
        }
    }

    @Test
    void resizeReconcilesThePageContainingThePreviouslyFocusedField() {
        var fields = IntStream.range(0, 7).mapToObj(i -> field("f" + i, i % 2 == 0
                ? FormField.Kind.MULTILINE : FormField.Kind.TEXT)).toList();
        FormDraft draft = new FormDraft("statecraft:mail", "mail send <f0> <f1> <f2> <f3> <f4> <f5> <f6>", Map.of());
        draft.state().apply(new FormSchema(fields), Map.of());
        draft.firstField(0);
        draft.focused("f5");
        for (int height : List.of(240, 270, 400)) {
            FormPages pages = new FormPages(fields, height);
            var page = pages.page(pages.pageContaining(draft.layoutAnchor()));
            assertTrue(page.start() <= 5 && page.end() > 5);
        }
        draft.focused("");
        draft.firstField(2);
        assertEquals(2, draft.layoutAnchor());
    }

    @Test
    void emptyFormsStillHaveAStablePageAndFooter() {
        FormPages pages = new FormPages(List.of(), 240);
        assertEquals(1, pages.pages().size());
        assertEquals(0, pages.pageContaining(0));
        assertTrue(pages.page(0).slots().isEmpty());
    }

    @Test
    void technicalControlsStartCollapsedAndPreserveTheirDraftWhenHidden() {
        ViewState view = new ViewState(dev.statecraft.api.ui.UiQuery.page("statecraft:nations"));
        assertFalse(view.advancedOpen());
        view.advancedOpen(true);
        view.advanced("nation info Arcadia");
        assertTrue(view.copy().advancedOpen());
        view.advancedOpen(false);
        assertFalse(view.copy().advancedOpen());
        assertEquals("nation info Arcadia", view.copy().advanced());
    }

    @Test
    void localMoneyIntegerAndAllConstraintsProduceFieldLevelMessagesBeforeReview() {
        FormField amount = new FormField("amount", "Amount", FormField.Kind.TEXT, "", "", "", List.of(), false,
                List.of(), 0, false, FormConstraints.money(1, 10_000).or("all"));
        FormField quantity = new FormField("quantity", "Quantity", FormField.Kind.TEXT, "", "", "", List.of(), false,
                List.of(), 0, false, FormConstraints.integer(1, 64));
        FormSchema schema = new FormSchema(List.of(amount, quantity));
        var errors = FormValidation.errors(schema, Map.of("amount", "1.234", "quantity", "1.5"));
        assertEquals("gui.statecraft.field.money", errors.get("amount").key());
        assertEquals("gui.statecraft.field.integer", errors.get("quantity").key());
        assertTrue(FormValidation.errors(schema, Map.of("amount", "ALL", "quantity", "64")).isEmpty());
        assertEquals("gui.statecraft.field.range", FormValidation.errors(schema,
                Map.of("amount", "100.01", "quantity", "65")).get("amount").key());
        assertTrue(amount.constraints().error("-1", "Amount").isPresent());
        assertTrue(amount.constraints().error("0", "Amount").isPresent());
        assertTrue(FormConstraints.money(0, Money.MAX).error("0.00", "Amount").isEmpty());
        assertTrue(FormConstraints.text(5).error("123456", "Name").isPresent());
    }

    @Test
    void searchFilteringResetsOnlyPaginationAndRetainsTheExplicitCursorAndSelection() {
        SearchState search = new SearchState();
        search.text("bank");
        search.offset(20);
        search.selection(new EditSelection(1, 3, true));
        search.text("bXank");
        assertEquals(0, search.offset());
        assertEquals(new EditSelection(1, 3, true), search.selection());
        search.selection(new EditSelection(2, 4, true));
        assertEquals(search.selection(), search.copy().selection());
        assertEquals(new EditSelection(2, 2, true), search.selection().bounded("ab"));
        assertEquals(new EditSelection(0, 2, false), new EditSelection(-1, 30, false).bounded("ab"));
    }

    @Test
    void serverClockOffsetAndExpiryAreUsedRatherThanTheClientsWallClock() {
        ServerClock clock = new ServerClock();
        assertFalse(clock.validUntil(999_999, 100));
        clock.observe(500_000, 1_000);
        assertEquals(501_000, clock.now(2_000));
        assertEquals(29, clock.secondsRemaining(530_000, 2_000));
        assertTrue(clock.validUntil(530_000, 30_999));
        assertFalse(clock.validUntil(530_000, 31_000));
        assertFalse(clock.fresh(61_001));
        assertFalse(clock.fresh(999));
        clock.clear();
        assertFalse(clock.fresh(2_000));
    }
}
