package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.OperationRef;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationPresentationTest {
    @Test
    void ordinaryReceiptsHideAllInternalIdsButPreserveAuditableRecords() {
        UiOperations operations = new UiOperations(new UiOperations.Data(), () -> 1_788_300_000_000L, () -> {});
        Actor owner = new Actor(UUID.randomUUID(), "Alice", false, "minecraft:overworld", 0, 0);
        OperationRef operation = new OperationRef(operations.world(), UUID.randomUUID());
        String entity = UUID.randomUUID().toString();
        operations.begin(owner, operation, "a".repeat(64), "statecraft:companies", "Companies - Create", ActionIntent.MUTATION);
        operations.finish(operation, ActionOutcome.COMPLETED, "Created company [" + entity + "] for player:" + owner.id(), 1);
        var receipt = operations.inspect(owner, operation.id()).orElseThrow();
        String displayed = OperationPresentation.body(receipt, 1, false).fallback() + OperationPresentation.row(receipt, 1, false);
        for (String internal : new String[]{operation.id().toString(), owner.id().toString(), entity, operations.world().toString()}) {
            assertFalse(displayed.contains(internal), displayed);
        }
        assertTrue(displayed.contains("Alice"));
        assertTrue(displayed.contains("Companies - Create"));
        assertTrue(OperationPresentation.body(receipt, 1, true).fallback().contains(operation.id().toString()));
        assertTrue(OperationPresentation.body(receipt, 1, true).fallback().contains(entity));
        assertTrue(receipt.text().contains(entity));
    }

    @Test
    void unconfirmedReceiptsStillWarnAgainstRepeatingTheAction() {
        UiOperations operations = new UiOperations(new UiOperations.Data(), () -> 1000, () -> {});
        Actor owner = new Actor(UUID.randomUUID(), "Alice", false, "minecraft:overworld", 0, 0);
        OperationRef operation = new OperationRef(operations.world(), UUID.randomUUID());
        operations.begin(owner, operation, "b".repeat(64), "economy:atm", "Cash withdrawal", ActionIntent.MUTATION);
        var receipt = operations.inspect(owner, operation.id()).orElseThrow();
        assertTrue(OperationPresentation.result(receipt, 0).contains("Do not repeat"));
        assertEquals(ActionOutcome.UNCERTAIN, receipt.outcome(0));
    }
}
