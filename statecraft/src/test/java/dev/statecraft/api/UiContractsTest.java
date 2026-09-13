package dev.statecraft.api;

import dev.statecraft.api.form.*;
import dev.statecraft.api.ui.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiContractsTest {
    @Test
    void intentsAreExplicitAndLegacyActionsAreConservativelyMutating() {
        assertEquals(ActionIntent.MUTATION, new MenuPage.Action("Legacy", "legacy").intent());
        assertTrue(ActionIntent.RAW.requiresReview());
        assertFalse(ActionIntent.QUERY.requiresReview());
        assertThrows(IllegalArgumentException.class, () -> new MenuPage.Action("Unsafe", "pay <amount>", ActionIntent.QUERY, true));
        assertThrows(IllegalArgumentException.class, () -> new MenuPage.Action("Unsafe nav", "gui <page>", ActionIntent.NAVIGATION));
    }

    @Test
    void materialTermsChangeFingerprintsButInformationalBalancesDoNot() {
        ActionPreview first = preview("100", "500", "item-state");
        assertEquals(first.fingerprint(), preview("100", "600", "item-state").fingerprint());
        assertNotEquals(first.fingerprint(), preview("101", "500", "item-state").fingerprint());
        assertNotEquals(first.fingerprint(), preview("100", "500", "changed-item-state").fingerprint());
    }

    @Test
    void fieldConstraintsUseIntegerCentsAndExplicitAlternatives() {
        var money = FormConstraints.money(1, 10_000).or("all");
        assertTrue(money.error("1.25", "Amount").isEmpty());
        assertTrue(money.error("ALL", "Amount").isEmpty());
        assertTrue(money.error("0", "Amount").isPresent());
        assertTrue(money.error("1.001", "Amount").isPresent());
        assertTrue(money.error("100.01", "Amount").isPresent());
        assertTrue(FormConstraints.integer(1, 100).error("1.5", "Quantity").isPresent());
        assertTrue(FormConstraints.text(3).error("four", "Name").isPresent());
    }

    @Test
    void forgedClosedChoicesAndMissingValuesHaveFieldSpecificErrors() {
        FormField field = new FormField("person", "Person", FormField.Kind.CHOICE, "alice", "Alice", "",
                List.of(), false, List.of(new FormChoice("alice", "Alice")), 0, false);
        FormSchema schema = new FormSchema(List.of(field));
        assertTrue(FormValidation.errors(schema, Map.of("person", "alice")).isEmpty());
        assertTrue(FormValidation.errors(schema, Map.of("person", "forged")).containsKey("person"));
        assertTrue(FormValidation.errors(schema, Map.of()).containsKey("person"));
    }

    @Test
    void modelBoundsRejectOversizedAndCrossNamespacePayloads() {
        assertThrows(IllegalArgumentException.class, () -> new UiQuery("statecraft:detail",
                new EntityRef("economy", EntityRef.Kind.LOAN, "loan"), "", 0));
        assertThrows(IllegalArgumentException.class, () -> new OperationRef(UUID.randomUUID(), new UUID(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new UiText("x", "a".repeat(30_000), List.of("b")));
        List<UiRow> rows = java.util.stream.IntStream.range(0, UiView.MAX_ROWS + 1)
                .mapToObj(index -> new UiRow("row", "", EntityRef.NONE)).toList();
        assertThrows(IllegalArgumentException.class, () -> new UiView(UiText.literal("Title"), UiText.EMPTY,
                rows, List.of(), 0, false, UiText.EMPTY));
    }

    private ActionPreview preview(String price, String balance, String state) {
        return new ActionPreview(UiText.literal("Payment"), List.of(
                new ActionPreview.Line("Total", price, true),
                new ActionPreview.Line("Available", balance, false)), UiText.EMPTY, state);
    }
}
