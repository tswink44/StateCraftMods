package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionPreview;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.persistence.WorldStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UiOperationsTest {
    @TempDir Path world;
    private final Actor owner = actor(false);
    private final Actor other = actor(false);
    private final Actor operator = actor(true);
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger changes = new AtomicInteger();
    private final String hash = ActionPreview.digest("exact reviewed request");

    public static final class Balance { public long cents = 1000; }

    @Test
    void completedReceiptAndEffectArePersistedInOneSnapshot() throws IOException {
        WorldStore store = new WorldStore(world);
        UiOperations journal = journal(store);
        Balance balance = store.load("balance", Balance.class, Balance::new);
        OperationRef operation = operation(journal);
        journal.begin(owner, operation, hash, "economy:atm", "Transfer", ActionIntent.MUTATION);
        store.save();
        assertEquals(ActionOutcome.UNCERTAIN, journal.own(owner, operation).orElseThrow().outcome(store.revision()));

        balance.cents -= 100;
        journal.finish(operation, ActionOutcome.COMPLETED, "Paid", store.revision() + 1);
        assertEquals(ActionOutcome.UNCERTAIN, journal.own(owner, operation).orElseThrow().outcome(store.revision()));
        store.save();

        WorldStore restored = new WorldStore(world);
        UiOperations reloaded = journal(restored);
        assertEquals(900, restored.load("balance", Balance.class, Balance::new).cents);
        assertEquals(ActionOutcome.COMPLETED, reloaded.own(owner, operation).orElseThrow().outcome(restored.revision()));
        assertEquals("Paid", reloaded.own(owner, operation).orElseThrow().text());
        assertThrows(UserError.class, () -> reloaded.begin(owner, operation, hash, "economy:atm", "Transfer", ActionIntent.MUTATION));
    }

    @Test
    void interruptionAfterReservationLeavesAnUncertainNonReplayableRecord() throws IOException {
        WorldStore store = new WorldStore(world);
        UiOperations journal = journal(store);
        Balance balance = store.load("balance", Balance.class, Balance::new);
        OperationRef operation = operation(journal);
        journal.begin(owner, operation, hash, "economy:atm", "Transfer", ActionIntent.MUTATION);
        store.save();
        balance.cents -= 100;

        WorldStore restored = new WorldStore(world);
        UiOperations reloaded = journal(restored);
        assertEquals(1000, restored.load("balance", Balance.class, Balance::new).cents);
        assertEquals(UiOperations.Phase.PREPARED, reloaded.own(owner, operation).orElseThrow().phase());
        assertEquals(ActionOutcome.UNCERTAIN, reloaded.own(owner, operation).orElseThrow().outcome(restored.revision()));
        assertThrows(UserError.class, () -> reloaded.begin(owner, operation, hash, "economy:atm", "Transfer", ActionIntent.MUTATION));
    }

    @Test
    void receiptsArePrivateAndCrossWorldReferencesCannotMatch() throws IOException {
        WorldStore store = new WorldStore(world);
        UiOperations journal = journal(store);
        OperationRef operation = operation(journal);
        journal.begin(owner, operation, hash, "statecraft:mail", "Send mail", ActionIntent.MUTATION);
        assertTrue(journal.own(other, operation).isEmpty());
        assertTrue(journal.inspect(other, operation.id()).isEmpty());
        assertTrue(journal.inspect(operator, operation.id()).isPresent());
        assertTrue(journal.own(owner, new OperationRef(UUID.randomUUID(), operation.id())).isEmpty());
        assertThrows(UserError.class, () -> journal.list(other, true, "", 0, 20));
        assertThrows(UserError.class, () -> journal.begin(other, operation, hash, "statecraft:mail", "Other", ActionIntent.MUTATION));
    }

    @Test
    void manualResolutionRequiresAnOperatorAndKeepsItsReason() throws IOException {
        WorldStore store = new WorldStore(world);
        UiOperations journal = journal(store);
        OperationRef operation = operation(journal);
        journal.begin(owner, operation, hash, "economy:atm", "Transfer", ActionIntent.MUTATION);
        store.save();
        assertThrows(UserError.class, () -> journal.resolve(owner, operation.id(), true, "guess", store.revision() + 1));
        journal.resolve(operator, operation.id(), false, "Verified no ledger or inventory effect", store.revision() + 1);
        assertEquals(ActionOutcome.UNCERTAIN, journal.own(owner, operation).orElseThrow().outcome(store.revision()));
        store.save();
        var receipt = journal(new WorldStore(world)).own(owner, operation).orElseThrow();
        assertEquals(ActionOutcome.REJECTED, receipt.outcome(store.revision()));
        assertTrue(receipt.text().contains("Verified no ledger or inventory effect"));
        assertThrows(UserError.class, () -> journal.resolve(operator, operation.id(), true, "change history", store.revision() + 1));
    }

    @Test
    void unresolvedQuotaIsExactAndTerminalRecordsDoNotConsumeIt() throws IOException {
        UiOperations journal = journal(new WorldStore(world));
        for (int index = 0; index < UiOperations.MAX_UNRESOLVED_PER_PLAYER; index++) {
            journal.begin(owner, operation(journal), hash, "economy:atm", "Transfer", ActionIntent.MUTATION);
        }
        assertThrows(UserError.class, () -> journal.begin(owner, operation(journal), hash,
                "economy:atm", "Overflow", ActionIntent.MUTATION));
        assertEquals(UiOperations.MAX_UNRESOLVED_PER_PLAYER, journal.unresolved(operator).size());
        var first = journal.unresolved(operator).get(0);
        journal.finish(first.operation(), ActionOutcome.REJECTED, "Not executed", 1);
        journal.begin(owner, operation(journal), hash, "economy:atm", "Replacement", ActionIntent.MUTATION);
        assertEquals(UiOperations.MAX_UNRESOLVED_PER_PLAYER, journal.unresolved(operator).size());
    }

    @Test
    void retentionNeverDropsUnresolvedOperations() throws IOException {
        UiOperations journal = journal(new WorldStore(world));
        OperationRef pending = operation(journal);
        OperationRef complete = operation(journal);
        journal.begin(owner, pending, hash, "economy:atm", "Pending", ActionIntent.MUTATION);
        journal.begin(owner, complete, hash, "economy:atm", "Complete", ActionIntent.MUTATION);
        journal.finish(complete, ActionOutcome.COMPLETED, "Complete", 1);
        now.addAndGet(UiOperations.RETENTION_MILLIS + 1);
        journal.compact();
        assertTrue(journal.own(owner, pending).isPresent());
        assertTrue(journal.own(owner, complete).isEmpty());
    }

    @Test
    void globalCapacityCompactsOnlyTerminalReceiptsAndNeverExceedsItsBound() {
        UiOperations.Data data = new UiOperations.Data();
        for (int index = 0; index < UiOperations.MAX_RECEIPTS; index++) {
            UiOperations.Entry entry = new UiOperations.Entry();
            entry.id = new UUID(1, index + 1L).toString();
            entry.owner = owner.id().toString();
            entry.ownerName = owner.name();
            entry.page = "economy:atm";
            entry.requestHash = hash;
            entry.summary = "Historical transfer";
            entry.intent = ActionIntent.MUTATION;
            entry.phase = UiOperations.Phase.COMPLETED;
            entry.createdAt = now.get();
            entry.updatedAt = now.get();
            entry.resultRevision = 1;
            data.receipts.put(entry.id, entry);
        }
        UiOperations journal = new UiOperations(data, now::get, changes::incrementAndGet);
        OperationRef pending = operation(journal);
        journal.begin(owner, pending, hash, "economy:atm", "New transfer", ActionIntent.MUTATION);
        assertEquals(UiOperations.MAX_RECEIPTS * 4 / 5 + 1, data.receipts.size());
        assertTrue(journal.own(owner, pending).orElseThrow().unresolved());
        assertTrue(data.receipts.size() <= UiOperations.MAX_RECEIPTS);
    }

    @Test
    void invalidJournalIsRejectedInsteadOfDiscarded() {
        UiOperations.Data data = new UiOperations.Data();
        data.worldId = "1-1-1-1-1";
        assertThrows(IllegalStateException.class, () -> new UiOperations(data, now::get, changes::incrementAndGet));
        data.worldId = UUID.randomUUID().toString();
        data.receipts.put(UUID.randomUUID().toString(), null);
        assertThrows(IllegalStateException.class, () -> new UiOperations(data, now::get, changes::incrementAndGet));
        assertEquals(1, data.receipts.size());
    }

    @Test
    void auditedRecoveryCreatesATerminalTombstoneWithoutOriginalInputs() throws IOException {
        WorldStore store = new WorldStore(world);
        UiOperations journal = journal(store);
        UUID missing = UUID.randomUUID();
        OperationRef operation = new OperationRef(journal.world(), missing);
        assertTrue(journal.own(owner, operation).isEmpty());
        assertThrows(UserError.class, () -> journal.recover(owner, owner.id(), missing, true, "unverified", 1));
        journal.recover(operator, owner.id(), missing, true, "Verified completed in account history", 1);
        assertEquals(ActionOutcome.UNCERTAIN, journal.own(owner, operation).orElseThrow().outcome(store.revision()));
        store.save();
        var receipt = journal(new WorldStore(world)).own(owner, operation).orElseThrow();
        assertEquals(ActionOutcome.COMPLETED, receipt.outcome(store.revision()));
        assertTrue(receipt.text().contains("Verified completed in account history"));
        assertTrue(journal.own(other, operation).isEmpty());
        assertThrows(UserError.class, () -> journal.recover(operator, other.id(), missing, false, "wrong owner", 2));
        assertThrows(UserError.class, () -> journal.recover(operator, owner.id(), missing, false, "rewrite outcome", 2));
        assertThrows(UserError.class, () -> journal.begin(owner, operation, hash, "economy:atm", "Repeat", ActionIntent.MUTATION));
    }

    private UiOperations journal(WorldStore store) throws IOException {
        return new UiOperations(store.load("ui_operations", UiOperations.Data.class, UiOperations.Data::new),
                now::get, changes::incrementAndGet);
    }

    private OperationRef operation(UiOperations journal) { return new OperationRef(journal.world(), UUID.randomUUID()); }
    private Actor actor(boolean admin) { return new Actor(UUID.randomUUID(), admin ? "Operator" : "Player", admin, "minecraft:overworld", 0, 0); }
}
