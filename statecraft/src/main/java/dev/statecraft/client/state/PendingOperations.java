package dev.statecraft.client.state;

import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.PreviewQuote;
import dev.statecraft.runtime.UiMenus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PendingOperations {
    public static final int MAX_PENDING = 128;
    public static final int MAX_RECEIPTS = 64;
    public static final class Entry {
        private final OperationRef operation;
        private final ActionSelection selection;
        private final ActionIntent intent;
        private final UUID origin;
        private ActionOutcome outcome;
        private String text = "";
        private int request = -1;
        private long deadline;
        private boolean inFlight;
        private boolean checking;

        private Entry(OperationRef operation, ActionSelection selection, ActionIntent intent, UUID origin,
                      ActionOutcome outcome) {
            this.operation = operation;
            this.selection = selection;
            this.intent = intent;
            this.origin = origin;
            this.outcome = outcome;
        }
        public OperationRef operation() { return operation; }
        public ActionSelection selection() { return selection; }
        public ActionIntent intent() { return intent; }
        public UUID origin() { return origin; }
        public ActionOutcome outcome() { return outcome; }
        public String text() { return text; }
        public boolean inFlight() { return inFlight; }
        public boolean checking() { return checking; }
        public boolean terminal() { return terminalOutcome(outcome); }
        public boolean retryable() { return outcome == ActionOutcome.READY && selection != null && !inFlight; }
    }
    private final UiScope scope;
    private final Map<OperationRef, Entry> entries = new LinkedHashMap<>();
    private long revision;

    public PendingOperations(UiScope scope) { this.scope = scope; }
    public long revision() { return revision; }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry get(OperationRef operation) { return entries.get(operation); }
    public Optional<OperationRef> tracked(EntityRef entity) {
        if (entity.kind() != EntityRef.Kind.OPERATION) return Optional.empty();
        try {
            OperationRef reference = new OperationRef(scope.world(), UUID.fromString(entity.id()));
            return entries.containsKey(reference) ? Optional.of(reference) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
    public List<OperationRef> pendingReferences() {
        return entries.values().stream().filter(entry -> !entry.terminal()).map(Entry::operation).toList();
    }
    public boolean blocked(OperationRef operation) {
        Entry entry = entries.get(operation);
        return entry != null && !entry.terminal();
    }
    public Entry matching(ActionSelection selection) {
        return entries.values().stream().filter(entry -> !entry.terminal() && selection.equals(entry.selection()))
                .findFirst().orElse(null);
    }
    public Entry blocking(ActionSelection selection) {
        return entries.values().stream().filter(entry -> !entry.terminal() && entry.selection() != null
                && entry.selection().page().equals(selection.page()) && entry.selection().template().equals(selection.template()))
                .findFirst().orElse(null);
    }
    public boolean hasRecoveredUnresolved() {
        return entries.values().stream().anyMatch(entry -> !entry.terminal() && entry.selection() == null);
    }
    public boolean blocksNewReview(String template) {
        return hasRecoveredUnresolved() && !UiMenus.RESOLVE.equals(template) && !UiMenus.RECOVER.equals(template);
    }
    public boolean capacityAvailable() { return pendingReferences().size() < MAX_PENDING; }

    public Entry register(PreviewQuote quote, ActionSelection selection, ActionIntent intent, UUID origin) {
        checkWorld(quote.operation());
        if (!intent.requiresReview()) throw new IllegalArgumentException("Queries do not own operations.");
        Entry existing = entries.get(quote.operation());
        if (existing != null) {
            if (!selection.equals(existing.selection())) throw new IllegalArgumentException("Operation selection changed.");
            return existing;
        }
        if (blocking(selection) != null) throw new IllegalStateException("This action is awaiting reconciliation.");
        requireCapacity();
        Entry entry = new Entry(quote.operation(), selection, intent, origin, ActionOutcome.READY);
        entries.put(entry.operation(), entry);
        revision++;
        return entry;
    }

    public Entry restore(OperationRef operation) {
        checkWorld(operation);
        Entry existing = entries.get(operation);
        if (existing != null) return existing;
        requireCapacity();
        Entry entry = new Entry(operation, null, ActionIntent.MUTATION, null, ActionOutcome.UNCERTAIN);
        entries.put(operation, entry);
        revision++;
        return entry;
    }

    public void submitted(OperationRef operation, int request, long now) {
        Entry entry = entries.get(operation);
        if (entry == null || !entry.retryable()) throw new IllegalStateException("Only an exact ready operation can be sent.");
        begin(entry, request, now, false);
        entry.outcome = ActionOutcome.UNCERTAIN;
    }

    public void checking(OperationRef operation, int request, long now) {
        Entry entry = entries.get(operation);
        if (entry == null || entry.inFlight()) return;
        begin(entry, request, now, true);
    }

    private void begin(Entry entry, int request, long now, boolean checking) {
        entry.request = request;
        entry.deadline = now + RequestTracker.TIMEOUT_MILLIS;
        entry.inFlight = true;
        entry.checking = checking;
        revision++;
    }

    public boolean reply(OperationRef operation, int request, ActionOutcome outcome, String text) {
        if (!scope.world().equals(operation.world())) return false;
        Entry entry = entries.get(operation);
        if (entry == null || entry.terminal()) return false;
        // A terminal receipt remains useful after a timeout or a newer status check.
        if (!terminalOutcome(outcome) && request != entry.request) return false;
        entry.outcome = outcome;
        entry.text = text;
        entry.inFlight = false;
        entry.checking = false;
        if (entry.terminal()) {
            entries.remove(operation);
            entries.put(operation, entry);
        }
        revision++;
        trimReceipts();
        return true;
    }

    public boolean expire(long now) {
        boolean changed = false;
        for (Entry entry : entries.values()) {
            if (!entry.inFlight || now < entry.deadline) continue;
            entry.inFlight = false;
            entry.checking = false;
            if (!entry.terminal()) entry.outcome = ActionOutcome.UNCERTAIN;
            changed = true;
        }
        if (changed) revision++;
        return changed;
    }

    public void disconnected() {
        for (Entry entry : entries.values()) {
            if (entry.inFlight && !entry.terminal()) entry.outcome = ActionOutcome.UNCERTAIN;
            entry.inFlight = false;
            entry.checking = false;
        }
        revision++;
    }

    public static boolean terminalOutcome(ActionOutcome outcome) {
        return outcome == ActionOutcome.COMPLETED || outcome == ActionOutcome.REJECTED
                || outcome == ActionOutcome.REVIEW_REQUIRED;
    }

    private void trimReceipts() {
        var receipts = new ArrayList<OperationRef>();
        entries.values().stream().filter(Entry::terminal).forEach(entry -> receipts.add(entry.operation()));
        for (int i = 0; i < receipts.size() - MAX_RECEIPTS; i++) entries.remove(receipts.get(i));
    }
    private void checkWorld(OperationRef operation) {
        if (!operation.present() || !scope.world().equals(operation.world())) {
            throw new IllegalArgumentException("Operation belongs to another world.");
        }
    }
    private void requireCapacity() {
        if (!capacityAvailable()) throw new IllegalStateException("Reconcile existing operations before starting another.");
    }
}
