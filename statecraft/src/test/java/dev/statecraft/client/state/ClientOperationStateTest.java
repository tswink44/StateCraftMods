package dev.statecraft.client.state;

import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.PreviewQuote;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.runtime.UiMenus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientOperationStateTest {
    private final UiScope scope = new UiScope(UUID.randomUUID(), UUID.randomUUID());
    private final ActionSelection selection = ActionSelection.form("economy:market", "market buy <id> <quantity>",
            Map.of("id", "listing-1", "quantity", "2"));

    private PreviewQuote quote() {
        return new PreviewQuote(new OperationRef(scope.world(), UUID.randomUUID()), 30_000,
                new ActionPreview(UiText.literal("Buy listing"), List.of(new ActionPreview.Line("Total", "$2.00", true)),
                        UiText.EMPTY, "listing-1:revision-1"));
    }

    @Test
    void requestCorrelationRejectsLateWorldPlayerConnectionAndQueryWithoutConsumingTheWatch() {
        RequestTracker<String> requests = new RequestTracker<>();
        requests.watch(7, scope, 2, "search stone page 2", 100, "receiver", () -> fail("Unexpected timeout"));
        assertTrue(requests.take(7, new UiScope(UUID.randomUUID(), scope.player()), 2, "search stone page 2").isEmpty());
        assertTrue(requests.take(7, new UiScope(scope.world(), UUID.randomUUID()), 2, "search stone page 2").isEmpty());
        assertTrue(requests.take(7, scope, 3, "search stone page 2").isEmpty());
        assertTrue(requests.take(7, scope, 2, "section default").isEmpty());
        assertEquals("receiver", requests.take(7, scope, 2, "search stone page 2").orElseThrow());
        assertTrue(requests.take(7, scope, 2, "search stone page 2").isEmpty());
    }

    @Test
    void abandonedRequestsExpireWithoutAnyScreenTickAndCannotLeakUnboundedWatchers() {
        RequestTracker<String> requests = new RequestTracker<>();
        AtomicInteger timedOut = new AtomicInteger();
        requests.watch(1, scope, 1, "form", 50, "form", timedOut::incrementAndGet);
        requests.expire(15_049);
        assertEquals(1, requests.size());
        requests.expire(15_050);
        assertEquals(1, timedOut.get());
        assertEquals(0, requests.size());
        assertTrue(requests.take(1, scope, 1, "form").isEmpty());
        for (int i = 0; i <= RequestTracker.MAX_REQUESTS; i++) {
            requests.watch(i + 2, scope, 1, "view", 20_000, "view", timedOut::incrementAndGet);
        }
        assertEquals(RequestTracker.MAX_REQUESTS, requests.size());
        assertEquals(2, timedOut.get());
        requests.clear();
        assertEquals(0, requests.size());
    }

    @Test
    void timeoutKeepsCapturedInputsAndOnlyReadyCanRetryTheExactOriginalOperation() {
        PendingOperations operations = new PendingOperations(scope);
        var entry = operations.register(quote(), selection, ActionIntent.MUTATION, UUID.randomUUID());
        OperationRef reference = entry.operation();
        operations.submitted(reference, 1, 100);
        assertTrue(operations.blocked(reference));
        assertFalse(entry.retryable());
        assertFalse(operations.expire(15_099));
        assertTrue(operations.expire(15_100));
        assertEquals(ActionOutcome.UNCERTAIN, entry.outcome());
        assertEquals(selection, entry.selection());
        assertEquals(List.of(reference), operations.pendingReferences());
        assertThrows(IllegalStateException.class, () -> operations.submitted(reference, 2, 20_000));
        operations.checking(reference, 3, 20_000);
        assertTrue(operations.reply(reference, 3, ActionOutcome.READY, "Not executed."));
        assertTrue(entry.retryable());
        operations.submitted(reference, 4, 21_000);
        assertFalse(entry.retryable());
        assertEquals(selection, entry.selection());
        assertEquals(reference, entry.operation());
        assertFalse(operations.reply(reference, 3, ActionOutcome.READY, "Delayed old status."));
        assertTrue(entry.inFlight());
        assertTrue(operations.reply(reference, 4, ActionOutcome.COMPLETED, "Purchased."));
        assertFalse(operations.blocked(reference));
        assertTrue(operations.pendingReferences().isEmpty());
    }

    @Test
    void unknownNeverEnablesResubmissionOrAllowsChangingValuesToStartAReplacement() {
        PendingOperations operations = new PendingOperations(scope);
        var entry = operations.register(quote(), selection, ActionIntent.MUTATION, null);
        operations.submitted(entry.operation(), 1, 0);
        assertTrue(operations.reply(entry.operation(), 1, ActionOutcome.UNKNOWN, "No receipt."));
        assertFalse(entry.retryable());
        assertTrue(operations.blocked(entry.operation()));
        ActionSelection edited = ActionSelection.form(selection.page(), selection.template(),
                Map.of("id", "listing-1", "quantity", "3"));
        assertThrows(IllegalStateException.class, () -> operations.register(quote(), edited, ActionIntent.MUTATION, null));
        operations.checking(entry.operation(), 2, 20_000);
        operations.expire(35_000);
        assertFalse(entry.retryable());
        assertTrue(operations.blocked(entry.operation()));
    }

    @Test
    void terminalLateReceiptsWinOverTimeoutsAndAreRoutedByOperationRatherThanOriginalRequest() {
        PendingOperations operations = new PendingOperations(scope);
        var entry = operations.register(quote(), selection, ActionIntent.MUTATION, null);
        operations.submitted(entry.operation(), 10, 0);
        operations.expire(15_000);
        operations.checking(entry.operation(), 11, 16_000);
        assertFalse(operations.reply(new OperationRef(UUID.randomUUID(), entry.operation().id()), 10,
                ActionOutcome.COMPLETED, "Other world."));
        assertTrue(operations.reply(entry.operation(), 10, ActionOutcome.COMPLETED, "Durable late receipt."));
        assertFalse(operations.reply(entry.operation(), 11, ActionOutcome.UNCERTAIN, "Stale status."));
        assertEquals(ActionOutcome.COMPLETED, entry.outcome());
        assertEquals("Durable late receipt.", entry.text());
    }

    @Test
    void onlyKnownTerminalOutcomesReleaseInputsAndReferences() {
        for (ActionOutcome outcome : ActionOutcome.values()) {
            PendingOperations operations = new PendingOperations(scope);
            var entry = operations.register(quote(), selection, ActionIntent.MUTATION, null);
            operations.submitted(entry.operation(), 1, 0);
            operations.reply(entry.operation(), 1, outcome, outcome.name());
            boolean terminal = outcome == ActionOutcome.COMPLETED || outcome == ActionOutcome.REJECTED
                    || outcome == ActionOutcome.REVIEW_REQUIRED;
            assertEquals(terminal, entry.terminal(), outcome.name());
            assertEquals(!terminal, operations.blocked(entry.operation()), outcome.name());
            assertEquals(terminal ? 0 : 1, operations.pendingReferences().size(), outcome.name());
        }
    }

    @Test
    void reconnectRetainsOperationIdentityAndBlocksASecondContextualFormForTheSameAction() {
        ClientWorkspace workspace = new ClientWorkspace(scope);
        var draft = workspace.draft(selection.page(), selection.template(), selection.values());
        var entry = workspace.operations().register(quote(), draft.capture(), ActionIntent.MUTATION, null);
        draft.operation(entry.operation());
        workspace.operations().submitted(entry.operation(), 8, 0);
        workspace.operations().disconnected();
        var reopened = workspace.draft(selection.page(), selection.template(), Map.of("id", "another-listing"));
        assertSame(draft, reopened);
        assertEquals(selection.values(), reopened.state().values());
        assertEquals(ActionOutcome.UNCERTAIN, entry.outcome());
        assertTrue(workspace.operations().blocked(reopened.operation()));
        ClientWorkspace otherWorld = new ClientWorkspace(new UiScope(UUID.randomUUID(), scope.player()));
        assertTrue(otherWorld.operations().entries().isEmpty());
        assertTrue(otherWorld.draft(selection.page(), selection.template(), Map.of()).state().values().isEmpty());
    }

    @Test
    void restoredReferencesNeverInventPrivateInputsEvenWhenTheServerReportsReady() {
        PendingOperations operations = new PendingOperations(scope);
        OperationRef reference = quote().operation();
        var entry = operations.restore(reference);
        assertNull(entry.selection());
        assertTrue(operations.hasRecoveredUnresolved());
        operations.checking(reference, 6, 0);
        operations.reply(reference, 6, ActionOutcome.READY, "Not executed.");
        assertFalse(entry.retryable());
        assertTrue(operations.hasRecoveredUnresolved());
        operations.checking(reference, 7, 1_000);
        operations.reply(reference, 7, ActionOutcome.REVIEW_REQUIRED, "Expired without execution.");
        assertFalse(operations.hasRecoveredUnresolved());
        assertTrue(operations.pendingReferences().isEmpty());
    }

    @Test
    void inspectingAnotherPlayersReceiptDoesNotCreateAPendingOperationOrBlockAdministrativeRecovery() {
        PendingOperations operations = new PendingOperations(scope);
        EntityRef external = new EntityRef("statecraft", EntityRef.Kind.OPERATION, UUID.randomUUID().toString());
        assertTrue(operations.tracked(external).isEmpty());
        assertTrue(operations.pendingReferences().isEmpty());
        assertFalse(operations.hasRecoveredUnresolved());
        var own = operations.register(quote(), selection, ActionIntent.MUTATION, null);
        EntityRef ownEntity = new EntityRef("statecraft", EntityRef.Kind.OPERATION, own.operation().id().toString());
        assertEquals(own.operation(), operations.tracked(ownEntity).orElseThrow());
        assertTrue(operations.tracked(new EntityRef("economy", EntityRef.Kind.LOAN, own.operation().id().toString())).isEmpty());
        assertTrue(operations.tracked(new EntityRef("statecraft", EntityRef.Kind.OPERATION, "invalid-reference")).isEmpty());
        assertEquals(List.of(own.operation()), operations.pendingReferences());
    }

    @Test
    void onlyRegisteredReconciliationTemplatesBypassRestoredReferenceLocksWithoutDismissingUnknown() {
        PendingOperations operations = new PendingOperations(scope);
        OperationRef original = quote().operation();
        var unresolved = operations.restore(original);
        operations.checking(original, 1, 0);
        operations.reply(original, 1, ActionOutcome.UNKNOWN, "Receipt evicted.");
        assertTrue(operations.blocksNewReview(selection.template()));
        assertTrue(operations.blocksNewReview(""));
        assertTrue(operations.blocksNewReview(UiMenus.RECOVER + " "));
        assertTrue(operations.blocksNewReview("admin operation recover player operation completed reason"));
        assertFalse(operations.blocksNewReview(UiMenus.RESOLVE));
        assertFalse(operations.blocksNewReview(UiMenus.RECOVER));
        assertEquals(ActionOutcome.UNKNOWN, unresolved.outcome());
        assertFalse(unresolved.retryable());
        assertEquals(List.of(original), operations.pendingReferences());

        ActionSelection recovery = ActionSelection.form("statecraft:admin_operations", UiMenus.RECOVER,
                Map.of("player", scope.player().toString(), "operation", original.id().toString(),
                        "resolution", "completed", "reason", "Operator audited the world and inventory history."));
        var reviewed = operations.register(quote(), recovery, ActionIntent.MUTATION, null);
        operations.submitted(reviewed.operation(), 2, 1_000);
        operations.reply(reviewed.operation(), 2, ActionOutcome.COMPLETED, "Audited tombstone persisted.");
        assertEquals(List.of(original), operations.pendingReferences());
        assertTrue(operations.blocksNewReview(selection.template()));
        assertFalse(unresolved.retryable());

        operations.checking(original, 3, 2_000);
        operations.reply(original, 3, ActionOutcome.COMPLETED, "Owner-scoped terminal receipt.");
        assertTrue(operations.pendingReferences().isEmpty());
        assertFalse(operations.blocksNewReview(selection.template()));
    }

    @Test
    void copyReferenceIncludesWorldPlayerAndOperationIdsWithoutPrivateActionInputs() {
        OperationRef operation = quote().operation();
        UiText text = scope.referenceText(operation);
        assertEquals("gui.statecraft.operations.reference_details", text.key());
        assertEquals(List.of(scope.world().toString(), scope.player().toString(), operation.id().toString()), text.arguments());
        assertEquals("World: " + scope.world() + "\nPlayer: " + scope.player() + "\nOperation: " + operation.id(), text.fallback());
        assertFalse(text.fallback().contains(selection.template()));
        assertThrows(IllegalArgumentException.class, () -> scope.referenceText(OperationRef.NONE));
        assertThrows(IllegalArgumentException.class, () -> scope.referenceText(
                new OperationRef(UUID.randomUUID(), operation.id())));
    }

    @Test
    void completingAnOldPendingOperationRetainsItsReceiptWhenHistoryIsFull() {
        PendingOperations operations = new PendingOperations(scope);
        var oldest = operations.register(quote(), selection, ActionIntent.MUTATION, null);
        operations.submitted(oldest.operation(), 1, 0);
        for (int i = 0; i < PendingOperations.MAX_RECEIPTS; i++) {
            var other = operations.register(quote(), ActionSelection.form("economy:market", "other" + i,
                    Map.of()), ActionIntent.MUTATION, null);
            operations.submitted(other.operation(), i + 2, 0);
            operations.reply(other.operation(), i + 2, ActionOutcome.REJECTED, "Not executed.");
        }
        operations.reply(oldest.operation(), 1, ActionOutcome.COMPLETED, "Recovered.");
        assertSame(oldest, operations.get(oldest.operation()));
        assertEquals(PendingOperations.MAX_RECEIPTS, operations.entries().size());
    }
}
