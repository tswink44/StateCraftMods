package dev.statecraft.runtime;

import dev.statecraft.api.Actor;
import dev.statecraft.api.UserError;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.OperationRef;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class UiOperations {
    public static final int MAX_RECEIPTS = 10_000;
    public static final int MAX_UNRESOLVED_PER_PLAYER = 128;
    public static final long RETENTION_MILLIS = 7L * 86_400_000;
    public enum Phase { PREPARED, COMPLETED, REJECTED, UNCERTAIN, REVIEW_REQUIRED }

    public static final class Data {
        public int version = 1;
        public String worldId = UUID.randomUUID().toString();
        public Map<String, Entry> receipts = new LinkedHashMap<>();
    }

    public static final class Entry {
        public String id;
        public String owner;
        public String ownerName;
        public String page;
        public String requestHash;
        public String summary;
        public ActionIntent intent;
        public Phase phase;
        public String text = "";
        public long createdAt;
        public long updatedAt;
        public long resultRevision;
    }

    public record Receipt(OperationRef operation, String owner, String ownerName, String page,
                          String requestHash, String summary, ActionIntent intent, Phase phase,
                          String text, long createdAt, long updatedAt, long resultRevision) {
        public boolean unresolved() { return phase == Phase.PREPARED || phase == Phase.UNCERTAIN; }
        public ActionOutcome outcome(long savedRevision) {
            if (phase == Phase.PREPARED || phase == Phase.UNCERTAIN
                    || resultRevision == 0 || resultRevision > savedRevision) return ActionOutcome.UNCERTAIN;
            return switch (phase) {
                case COMPLETED -> ActionOutcome.COMPLETED;
                case REJECTED -> ActionOutcome.REJECTED;
                case REVIEW_REQUIRED -> ActionOutcome.REVIEW_REQUIRED;
                default -> ActionOutcome.UNCERTAIN;
            };
        }
    }

    private final Data data;
    private final UUID world;
    private final LongSupplier clock;
    private final Runnable dirty;

    public UiOperations(Data data, LongSupplier clock, Runnable dirty) {
        this.data = java.util.Objects.requireNonNull(data);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.dirty = java.util.Objects.requireNonNull(dirty);
        world = uuid(data.worldId);
        if (world.equals(new UUID(0, 0))) throw new IllegalStateException("The operation world UUID cannot be empty.");
        validate();
    }

    public UUID world() { return world; }

    public boolean contains(UUID id) { return data.receipts.containsKey(id.toString()); }

    public Optional<Receipt> own(Actor actor, OperationRef operation) {
        if (!world.equals(operation.world())) return Optional.empty();
        Entry entry = data.receipts.get(operation.id().toString());
        return entry != null && entry.owner.equals(actor.id().toString()) ? Optional.of(view(entry)) : Optional.empty();
    }

    public Optional<Receipt> inspect(Actor actor, UUID id) {
        Entry entry = data.receipts.get(id.toString());
        return entry != null && (actor.admin() || entry.owner.equals(actor.id().toString()))
                ? Optional.of(view(entry)) : Optional.empty();
    }

    public Receipt begin(Actor actor, OperationRef operation, String hash, String page, String summary, ActionIntent intent) {
        if (!world.equals(operation.world()) || !operation.present() || !intent.requiresReview()) {
            throw new UserError("This operation does not belong to the active world.");
        }
        if (data.receipts.containsKey(operation.id().toString())) {
            throw new UserError("This operation is already recorded; reconcile its existing result.");
        }
        compact();
        long unresolved = data.receipts.values().stream().filter(entry -> entry.owner.equals(actor.id().toString())
                && unresolved(entry.phase)).count();
        if (unresolved >= MAX_UNRESOLVED_PER_PLAYER || data.receipts.size() >= MAX_RECEIPTS) {
            throw new UserError("Too many unresolved operations. Ask an operator to reconcile them before starting another action.");
        }
        Entry entry = new Entry();
        entry.id = operation.id().toString();
        entry.owner = actor.id().toString();
        entry.ownerName = actor.name();
        entry.page = page;
        entry.requestHash = hash;
        entry.summary = shorten(summary, 128);
        entry.intent = intent;
        entry.phase = Phase.PREPARED;
        entry.createdAt = Math.max(0, clock.getAsLong());
        entry.updatedAt = entry.createdAt;
        validateEntry(entry.id, entry);
        data.receipts.put(entry.id, entry);
        dirty.run();
        return view(entry);
    }

    public void finish(OperationRef operation, ActionOutcome outcome, String text, long resultRevision) {
        Entry entry = data.receipts.get(operation.id().toString());
        if (!world.equals(operation.world()) || entry == null || entry.phase != Phase.PREPARED || resultRevision <= 0) {
            throw new IllegalStateException("Only a prepared operation can be completed.");
        }
        entry.phase = switch (outcome) {
            case COMPLETED -> Phase.COMPLETED;
            case REJECTED -> Phase.REJECTED;
            case REVIEW_REQUIRED -> Phase.REVIEW_REQUIRED;
            case UNCERTAIN, UNKNOWN -> Phase.UNCERTAIN;
            default -> throw new IllegalArgumentException("Cannot persist this operation outcome.");
        };
        entry.text = shorten(text, 4096);
        entry.updatedAt = Math.max(entry.createdAt, clock.getAsLong());
        entry.resultRevision = resultRevision;
        dirty.run();
    }

    public void resolve(Actor actor, UUID id, boolean completed, String reason, long resultRevision) {
        if (!actor.admin()) throw new UserError("Operator permission level 2 is required to reconcile an uncertain operation.");
        Entry entry = data.receipts.get(id.toString());
        if (entry == null || !unresolved(entry.phase)) throw new UserError("Only a retained unresolved operation can be reconciled.");
        if (reason == null || reason.isBlank() || reason.length() > 512 || resultRevision <= 0) {
            throw new UserError("Supply a reconciliation reason of 1-512 characters.");
        }
        entry.phase = completed ? Phase.COMPLETED : Phase.REJECTED;
        entry.updatedAt = Math.max(entry.createdAt, clock.getAsLong());
        entry.resultRevision = resultRevision;
        entry.text = shorten("Resolved by operator " + actor.name() + " as "
                + (completed ? "completed" : "not executed") + ": " + reason
                + (entry.text.isEmpty() ? "" : "\nOriginal result: " + entry.text), 4096);
        dirty.run();
    }

    public void recover(Actor actor, UUID owner, UUID id, boolean completed, String reason, long resultRevision) {
        if (!actor.admin()) throw new UserError("Operator permission level 2 is required.");
        if (owner == null || id == null || id.equals(new UUID(0, 0)) || reason == null || reason.isBlank()
                || reason.length() > 512 || resultRevision <= 0) {
            throw new UserError("Supply the affected player UUID, operation UUID, and a reconciliation reason of 1-512 characters.");
        }
        Entry existing = data.receipts.get(id.toString());
        if (existing != null) {
            if (!existing.owner.equals(owner.toString())) throw new UserError("The operation belongs to a different player.");
            resolve(actor, id, completed, reason, resultRevision);
            return;
        }
        compact();
        if (data.receipts.size() >= MAX_RECEIPTS) throw new UserError("Reconcile retained unresolved operations to free receipt capacity first.");
        Entry entry = new Entry();
        entry.id = id.toString();
        entry.owner = owner.toString();
        entry.ownerName = owner.toString();
        entry.page = "statecraft:operations";
        entry.requestHash = dev.statecraft.api.ui.ActionPreview.digest("audited-recovery:" + owner + ":" + id);
        entry.summary = "Audited receipt recovery";
        entry.intent = ActionIntent.MUTATION;
        entry.phase = completed ? Phase.COMPLETED : Phase.REJECTED;
        entry.text = "Unknown receipt audited by operator " + actor.name() + " as "
                + (completed ? "completed" : "not executed") + ": " + reason + "\nNo original command was replayed or undone.";
        entry.createdAt = Math.max(0, clock.getAsLong());
        entry.updatedAt = entry.createdAt;
        entry.resultRevision = resultRevision;
        validateEntry(entry.id, entry);
        data.receipts.put(entry.id, entry);
        dirty.run();
    }

    public List<Receipt> list(Actor actor, boolean allPlayers, String search, int offset, int limit) {
        if (allPlayers && !actor.admin()) throw new UserError("Operator permission is required to inspect other players' operations.");
        if (offset < 0 || offset > 100_000 || limit < 1 || limit > 61 || search.length() > 80) {
            throw new UserError("Invalid operation history page.");
        }
        String needle = search.strip().toLowerCase(Locale.ROOT);
        return data.receipts.values().stream()
                .filter(entry -> allPlayers || entry.owner.equals(actor.id().toString()))
                .filter(entry -> needle.isEmpty() || (entry.summary + " " + entry.id + " " + entry.ownerName + " " + entry.phase)
                        .toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingLong((Entry entry) -> entry.createdAt).reversed().thenComparing(entry -> entry.id))
                .skip(offset).limit(limit).map(this::view).toList();
    }

    public List<Receipt> unresolved(Actor actor) {
        if (!actor.admin()) throw new UserError("Operator permission is required.");
        return data.receipts.values().stream().filter(entry -> unresolved(entry.phase)).map(this::view).toList();
    }

    public void compact() {
        long now = clock.getAsLong();
        long cutoff = now > RETENTION_MILLIS ? now - RETENTION_MILLIS : 0;
        boolean removed = data.receipts.values().removeIf(entry -> !unresolved(entry.phase) && entry.updatedAt < cutoff);
        if (data.receipts.size() >= MAX_RECEIPTS) {
            List<String> oldest = data.receipts.values().stream().filter(entry -> !unresolved(entry.phase))
                    .sorted(Comparator.comparingLong((Entry entry) -> entry.updatedAt).thenComparing(entry -> entry.id))
                    .limit(Math.max(1, data.receipts.size() - MAX_RECEIPTS * 4L / 5))
                    .map(entry -> entry.id).toList();
            oldest.forEach(data.receipts::remove);
            removed |= !oldest.isEmpty();
        }
        if (removed) dirty.run();
    }

    private Receipt view(Entry entry) {
        return new Receipt(new OperationRef(world, uuid(entry.id)), entry.owner, entry.ownerName, entry.page,
                entry.requestHash, entry.summary, entry.intent, entry.phase, entry.text,
                entry.createdAt, entry.updatedAt, entry.resultRevision);
    }

    private void validate() {
        if (data.version != 1 || data.receipts == null || data.receipts.size() > MAX_RECEIPTS) {
            throw new IllegalStateException("Invalid UI operation journal. Preserve it and restore or repair a verified snapshot.");
        }
        data.receipts.forEach(UiOperations::validateEntry);
    }

    private static void validateEntry(String id, Entry entry) {
        if (entry == null || uuid(id).equals(new UUID(0, 0)) || !uuid(id).toString().equals(entry.id)
                || entry.ownerName == null || entry.ownerName.length() > 64
                || entry.page == null || !entry.page.matches("[a-z][a-z0-9_]*:[a-z][a-z0-9_-]*") || entry.page.length() > 96
                || entry.requestHash == null || !entry.requestHash.matches("[0-9a-f]{64}")
                || entry.summary == null || entry.summary.length() > 128 || entry.intent == null || !entry.intent.requiresReview()
                || entry.phase == null || entry.text == null || entry.text.length() > 4096
                || entry.createdAt < 0 || entry.updatedAt < entry.createdAt || entry.resultRevision < 0
                || entry.phase == Phase.PREPARED && entry.resultRevision != 0
                || entry.phase != Phase.PREPARED && entry.resultRevision == 0) {
            throw new IllegalStateException("Invalid UI operation receipt: " + id);
        }
        uuid(entry.owner);
    }

    private static UUID uuid(String text) {
        try {
            UUID value = UUID.fromString(text);
            if (!value.toString().equals(text)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IllegalStateException("Invalid persisted operation UUID.", invalid);
        }
    }

    private static boolean unresolved(Phase phase) { return phase == Phase.PREPARED || phase == Phase.UNCERTAIN; }

    private static String shorten(String text, int maximum) {
        java.util.Objects.requireNonNull(text);
        return text.length() <= maximum ? text : text.substring(0, maximum - 48) + "\nOutput limited; inspect the related section.";
    }
}
