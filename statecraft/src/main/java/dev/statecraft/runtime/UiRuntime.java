package dev.statecraft.runtime;

import dev.statecraft.StateCraft;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormValidation;
import dev.statecraft.api.ui.*;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class UiRuntime {
    public static final long QUOTE_MILLIS = 30_000;
    private static final int MAX_QUOTES_PER_PLAYER = 16;

    @FunctionalInterface
    public interface PreviewProvider {
        ActionPreview preview(ServerPlayer player, ActionSelection selection);
    }

    public static final class FormRejected extends RuntimeException {
        private final Map<String, UiText> errors;
        FormRejected(Map<String, UiText> errors) {
            super("Correct the highlighted fields and refresh their eligible choices.");
            this.errors = Map.copyOf(errors);
        }
        public Map<String, UiText> errors() { return errors; }
    }

    private static final class CachedReview {
        final ActionSelection selection;
        final PreviewQuote quote;
        final String hash;
        final long createdNanos = System.nanoTime();
        boolean invalidated;

        CachedReview(ActionSelection selection, PreviewQuote quote, String hash) {
            this.selection = selection;
            this.quote = quote;
            this.hash = hash;
        }
    }

    private final ServerRuntime runtime;
    private final UiOperations operations;
    private final Map<String, UiProvider> providers = new LinkedHashMap<>();
    private final Map<String, PreviewProvider> reviewers = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, CachedReview>> quotes = new LinkedHashMap<>();

    public UiRuntime(ServerRuntime runtime, UiOperations.Data data) throws IOException {
        this.runtime = runtime;
        try {
            operations = new UiOperations(data, runtime.clock()::millis, runtime::markDirty);
        } catch (IllegalStateException malformed) {
            throw new IOException("Invalid ui_operations section in " + runtime.store().directory().resolve("world.json")
                    + ". Preserve the snapshot and repair or restore it.", malformed);
        }
    }

    public UUID world() { return operations.world(); }
    public UiOperations operations() { return operations; }

    public void register(String namespace, UiProvider provider, PreviewProvider reviewer) {
        if (!namespace.matches("[a-z][a-z0-9_]*") || providers.containsKey(namespace)) {
            throw new IllegalArgumentException("Duplicate or invalid presentation provider: " + namespace);
        }
        providers.put(namespace, java.util.Objects.requireNonNull(provider));
        reviewers.put(namespace, java.util.Objects.requireNonNull(reviewer));
    }

    public void forget(UUID player) { quotes.remove(player); }

    public PreviewQuote preview(ServerPlayer player, ActionSelection selection) {
        thread();
        runtime.requireWritable();
        ActionIntent intent = selection.intent();
        if (!intent.requiresReview()) throw new UserError("This action does not require a transaction review.");
        ActionPreview preview;
        try (var decision = runtime.clock().freeze()) {
            runtime.advanceGovernance();
            preview = buildPreview(player, selection);
        } finally {
            if (runtime.isWritable()) runtime.flush();
        }
        runtime.requireWritable();
        var playerQuotes = quotes.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashMap<>());
        playerQuotes.values().removeIf(review -> System.nanoTime() - review.createdNanos > 300_000_000_000L);
        while (playerQuotes.size() >= MAX_QUOTES_PER_PLAYER) {
            playerQuotes.remove(playerQuotes.keySet().iterator().next());
        }
        UUID id;
        do { id = UUID.randomUUID(); } while (operations.contains(id) || playerQuotes.containsKey(id));
        PreviewQuote quote = new PreviewQuote(new OperationRef(world(), id),
                Math.addExact(runtime.clock().millis(), QUOTE_MILLIS), preview);
        playerQuotes.put(id, new CachedReview(selection, quote, requestHash(selection)));
        return quote;
    }

    private ActionPreview buildPreview(ServerPlayer player, ActionSelection selection) {
        if (!selection.template().isEmpty()) {
            var schema = runtime.describeForm(player, selection.page(), selection.template(), selection.values(), FormQuery.INITIAL);
            Map<String, UiText> errors = FormValidation.errors(schema, selection.values());
            if (!errors.isEmpty()) throw new FormRejected(errors);
        }
        if (selection.template().equals(UiMenus.RESOLVE) || selection.template().equals(UiMenus.RECOVER)) {
            return reconciliationPreview(ServerRuntime.actor(player), selection);
        }
        if (selection.template().isEmpty()) {
            return new ActionPreview(text("review.advanced", "Review advanced command"),
                    List.of(new ActionPreview.Line("Command", selection.rendered(), true)),
                    text("review.advanced_warning", "Detailed pricing is unavailable for an advanced command. Use a guided action for itemized costs."),
                    selection.rendered());
        }
        PreviewProvider provider = reviewers.get(selection.namespace());
        if (provider == null) throw new UserError("This module has not provided a server-side action review.");
        long before = runtime.mutationRevision();
        ActionPreview result;
        try {
            result = java.util.Objects.requireNonNull(provider.preview(player, selection));
        } finally {
            if (runtime.mutationRevision() != before) {
                runtime.rejectMutatingPreview();
            }
        }
        return result;
    }

    public ServerRuntime.Reply execute(ServerPlayer player, ActionSelection selection, OperationRef operation) {
        thread();
        Actor actor = ServerRuntime.actor(player);
        String hash = requestHash(selection);
        if (operation.present()) {
            var existing = operations.own(actor, operation);
            if (existing.isPresent()) return receiptReply(existing.get(), hash);
            if (!world().equals(operation.world())) return new ServerRuntime.Reply(ActionOutcome.UNKNOWN,
                    "This review belongs to a different world and will not be executed here.");
        }
        ActionIntent intent = selection.intent();
        if (!intent.requiresReview()) {
            if (operation.present()) return new ServerRuntime.Reply(ActionOutcome.UNCERTAIN,
                    "This operation must be reconciled using its original inputs, not reused for a query.");
            if (intent == ActionIntent.NAVIGATION) return new ServerRuntime.Reply(ActionOutcome.REJECTED, "Use the navigation action to open that section.");
            validateForm(player, selection);
            return runtime.invoke(player, selection.namespace(), selection.rendered());
        }
        if (!operation.present()) return new ServerRuntime.Reply(ActionOutcome.REVIEW_REQUIRED, "Review this action before submitting it.");
        var playerQuotes = quotes.get(player.getUUID());
        CachedReview cached = playerQuotes == null ? null : playerQuotes.get(operation.id());
        if (cached == null) return unknown();
        if (!cached.hash.equals(hash)) return new ServerRuntime.Reply(ActionOutcome.UNCERTAIN,
                "An operation ID cannot be reused for changed inputs. Check the original operation's status.");
        runtime.requireWritable();
        if (expired(cached)) {
            playerQuotes.remove(operation.id());
            return recordNoExecution(actor, operation, cached, ActionOutcome.REVIEW_REQUIRED,
                    "The review expired. Nothing was submitted; request a fresh review.");
        }

        try (var decision = runtime.clock().freeze()) {
            runtime.advanceGovernance();
            ActionPreview current = buildPreview(player, selection);
            if (!current.fingerprint().equals(cached.quote.preview().fingerprint())) {
                playerQuotes.remove(operation.id());
                return recordNoExecution(actor, operation, cached, ActionOutcome.REVIEW_REQUIRED,
                        "The costs, parties, items, or terms changed. Nothing was submitted; review the new terms.");
            }
        } catch (UserError | FormRejected rejected) {
            playerQuotes.remove(operation.id());
            return recordNoExecution(actor, operation, cached, ActionOutcome.REJECTED, rejected.getMessage());
        }

        operations.begin(actor, operation, hash, selection.page(), summary(selection), intent);
        playerQuotes.remove(operation.id());
        runtime.saveNow();
        if (!runtime.isWritable()) {
            operations.finish(operation, ActionOutcome.REJECTED, "No action was started because its durable reservation failed.", nextRevision());
            return new ServerRuntime.Reply(ActionOutcome.UNCERTAIN,
                    "The operation reservation could not be confirmed on disk. No action was started; ask an operator to restore saving and check this operation.");
        }

        ServerRuntime.Reply result;
        try (var decision = runtime.clock().freeze()) {
            // A durable reservation may take time. Settle due policies before the final comparison,
            // then use the same decision instant for quote calculation and the command.
            runtime.advanceGovernance();
            if (expired(cached)) {
                result = new ServerRuntime.Reply(ActionOutcome.REVIEW_REQUIRED, "The review expired before execution. No action was started.");
            } else {
                ActionPreview finalReview = buildPreview(player, selection);
                result = finalReview.fingerprint().equals(cached.quote.preview().fingerprint())
                        ? runtime.invokeDeferred(player, selection.namespace(), selection.rendered())
                        : new ServerRuntime.Reply(ActionOutcome.REVIEW_REQUIRED,
                                "The reviewed terms changed before execution. No action was started; review them again.");
            }
        } catch (UserError | FormRejected rejected) {
            result = new ServerRuntime.Reply(ActionOutcome.REJECTED, rejected.getMessage());
        } catch (RuntimeException failure) {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            StateCraft.LOGGER.error("Reviewed operation {} failed [{}] for {}", operation.id(), reference, player.getUUID(), failure);
            result = new ServerRuntime.Reply(ActionOutcome.UNCERTAIN,
                    "This operation needs reconciliation (" + reference + "). Do not repeat it.");
        }
        operations.finish(operation, result.outcome(), result.text(), nextRevision());
        if (runtime.isWritable()) runtime.saveNow();
        if (!runtime.isWritable()) return new ServerRuntime.Reply(ActionOutcome.UNCERTAIN,
                "The action may have completed, but its result is not confirmed on disk. Restore saving and check this operation; do not repeat it.");
        return result;
    }

    private ServerRuntime.Reply recordNoExecution(Actor actor, OperationRef operation, CachedReview review,
                                                   ActionOutcome outcome, String message) {
        operations.begin(actor, operation, review.hash, review.selection.page(), summary(review.selection), review.selection.intent());
        operations.finish(operation, outcome, message, nextRevision());
        if (runtime.isWritable()) runtime.saveNow();
        return new ServerRuntime.Reply(outcome, message);
    }

    private void validateForm(ServerPlayer player, ActionSelection selection) {
        if (selection.template().isEmpty()) return;
        var schema = runtime.describeForm(player, selection.page(), selection.template(), selection.values(), FormQuery.INITIAL);
        var errors = FormValidation.errors(schema, selection.values());
        if (!errors.isEmpty()) throw new FormRejected(errors);
    }

    public ServerRuntime.Reply status(ServerPlayer player, OperationRef operation) {
        thread();
        var receipt = operations.own(ServerRuntime.actor(player), operation);
        if (receipt.isPresent()) return receiptReply(receipt.get(), receipt.get().requestHash());
        if (!world().equals(operation.world())) return unknown();
        var cached = quotes.getOrDefault(player.getUUID(), new LinkedHashMap<>()).get(operation.id());
        if (cached == null) return unknown();
        return expired(cached)
                ? new ServerRuntime.Reply(ActionOutcome.REVIEW_REQUIRED, "The original review expired without execution. Request a new review.")
                : new ServerRuntime.Reply(ActionOutcome.READY, "The original reviewed action has not started. It may be retried using the same operation ID.");
    }

    public String operationPage(ServerPlayer player, OperationRef operation) {
        return operations.own(ServerRuntime.actor(player), operation).map(UiOperations.Receipt::page).orElse("statecraft:operations");
    }

    public ActionIntent operationIntent(ServerPlayer player, OperationRef operation) {
        return operations.own(ServerRuntime.actor(player), operation).map(UiOperations.Receipt::intent).orElse(ActionIntent.MUTATION);
    }

    private ServerRuntime.Reply receiptReply(UiOperations.Receipt receipt, String requestHash) {
        if (!receipt.requestHash().equals(requestHash)) return new ServerRuntime.Reply(ActionOutcome.UNCERTAIN,
                "This operation ID belongs to different inputs. Reconcile the original operation instead of resubmitting.");
        ActionOutcome outcome = receipt.outcome(runtime.store().revision());
        return new ServerRuntime.Reply(outcome, outcome.uncertain()
                ? "The operation has no confirmed final outcome. Do not repeat it; inspect the related history and ask an operator to reconcile it."
                : receipt.text());
    }

    private ServerRuntime.Reply unknown() {
        return new ServerRuntime.Reply(ActionOutcome.UNKNOWN,
                "No retained receipt or valid original review is available. This ID will not be executed. Inspect account/history records before starting a new action.");
    }

    private boolean expired(CachedReview review) {
        review.invalidated |= System.nanoTime() - review.createdNanos >= QUOTE_MILLIS * 1_000_000L
                || runtime.clock().millis() >= review.quote.expiresAt();
        return review.invalidated;
    }

    public UiView view(ServerPlayer player, UiQuery query) {
        thread();
        MenuRegistry.get(query.page());
        Actor actor = ServerRuntime.actor(player);
        if (query.entity().kind() == EntityRef.Kind.OPERATION) return operationView(actor, operationId(query.entity().id()));
        if (query.page().equals("statecraft:operations") || query.page().equals("statecraft:admin_operations")) {
            return operationList(actor, query.page().equals("statecraft:admin_operations"), query.search(), query.offset());
        }
        if (query.page().equals("statecraft:dashboard") && !query.entity().present()) return dashboard(actor, query);
        runtime.requireWritable();
        UiProvider provider = providers.get(query.namespace());
        if (provider == null) throw new UserError("This module has not provided contextual views.");
        UiView result;
        try (var decision = runtime.clock().freeze()) {
            runtime.advanceGovernance();
            result = validateView(provider.view(new UiContext(actor, query)));
        } finally {
            if (runtime.isWritable()) runtime.flush();
        }
        runtime.requireWritable();
        return result;
    }

    private UiView dashboard(Actor actor, UiQuery query) {
        var rows = new ArrayList<UiRow>();
        boolean more = false;
        if (runtime.isWritable()) {
            try (var decision = runtime.clock().freeze()) {
                runtime.advanceGovernance();
                for (UiProvider provider : providers.values()) {
                    UiView section = validateView(provider.dashboard(actor, query.search(), query.offset()));
                    if (section.rows().size() > UiView.PAGE_SIZE) throw new IllegalStateException("Dashboard providers must page their own tasks.");
                    rows.addAll(section.rows());
                    more |= section.more();
                }
            } finally {
                if (runtime.isWritable()) runtime.flush();
            }
        }
        UiView operationPage = operationList(actor, false, query.search(), query.offset());
        rows.addAll(operationPage.rows());
        more |= operationPage.more();
        return new UiView(text("dashboard.title", "Your dashboard"),
                runtime.isWritable() ? text("dashboard.body", "Authorized pending work and your recent operations. Select a row to inspect it.")
                        : text("dashboard.paused", "World saving is paused. Operation status remains available; ask an operator to restore saving."),
                rows, List.of(new UiAction("statecraft:operations", "gui statecraft:dashboard",
                        text("dashboard.home", "Dashboard"), Map.of())), query.offset(), more,
                text("dashboard.empty", "No matching pending work or recent operations."));
    }

    private UiView operationList(Actor actor, boolean all, String search, int offset) {
        List<UiOperations.Receipt> entries = operations.list(actor, all, search, offset, UiView.PAGE_SIZE + 1);
        List<UiRow> rows = entries.stream().limit(UiView.PAGE_SIZE).map(receipt -> new UiRow(
                receipt.summary(), receipt.outcome(runtime.store().revision()) + " | "
                + receipt.ownerName() + " | " + Instant.ofEpochMilli(receipt.createdAt()),
                new EntityRef("statecraft", EntityRef.Kind.OPERATION, receipt.operation().id().toString()))).toList();
        return new UiView(text(all ? "operations.admin_title" : "operations.title", all ? "Operation administration" : "Your operations"),
                text("operations.help", "Receipts are bounded. An unknown or uncertain ID is never automatically executed again."),
                rows, List.of(), offset, entries.size() > UiView.PAGE_SIZE, text("operations.empty", "No matching operations."));
    }

    private UiView operationView(Actor actor, UUID id) {
        var receipt = operations.inspect(actor, id).orElseThrow(() -> new UserError("This operation is not available to you."));
        var actions = new ArrayList<UiAction>();
        if (receipt.unresolved() && actor.admin()) {
            actions.add(new UiAction("statecraft:operations", UiMenus.RESOLVE,
                    text("operations.resolve_completed", "Confirm completed after audit"),
                    Map.of("operation", id.toString(), "resolution", "completed")));
            actions.add(new UiAction("statecraft:operations", UiMenus.RESOLVE,
                    text("operations.resolve_rejected", "Confirm not executed after audit"),
                    Map.of("operation", id.toString(), "resolution", "not_executed")));
        }
        String body = receipt.summary() + "\nOperation: " + id + "\nOwner: " + receipt.ownerName()
                + "\nOutcome: " + receipt.outcome(runtime.store().revision()) + "\nCreated: " + Instant.ofEpochMilli(receipt.createdAt())
                + "\n" + receiptReply(receipt, receipt.requestHash()).text();
        return new UiView(text("operations.detail", "Operation details"), UiText.literal(body), List.of(), actions, 0, false, UiText.EMPTY);
    }

    private ActionPreview reconciliationPreview(Actor actor, ActionSelection selection) {
        if (!actor.admin()) throw new UserError("Operator permission level 2 is required.");
        UUID id = operationId(selection.values().getOrDefault("operation", ""));
        boolean recovery = selection.template().equals(UiMenus.RECOVER);
        var found = operations.inspect(actor, id);
        if (!recovery && found.isEmpty()) throw new UserError("Unknown operation. Use audited receipt recovery with the affected player UUID.");
        UUID owner = recovery ? playerId(selection.values().getOrDefault("player", "")) : UUID.fromString(found.orElseThrow().owner());
        if (found.isPresent() && (!found.get().owner().equals(owner.toString()) || !found.get().unresolved())) {
            throw new UserError("This operation belongs to another player or already has a final result.");
        }
        requireInactiveReview(owner, id);
        return new ActionPreview(text("operations.resolve_title", "Review manual reconciliation"), List.of(
                new ActionPreview.Line("Operation", id.toString(), true),
                new ActionPreview.Line("Player", found.map(UiOperations.Receipt::ownerName).orElse(owner.toString()), true),
                new ActionPreview.Line("Original action", found.map(UiOperations.Receipt::summary).orElse("Unknown or expired receipt"), true),
                new ActionPreview.Line("Resolution", selection.values().getOrDefault("resolution", ""), true),
                new ActionPreview.Line("Reason", selection.values().getOrDefault("reason", ""), true)),
                text("operations.resolve_warning", "Verify the world, inventory and account history first. This only records the outcome; it does not replay, refund, or undo an action."),
                owner + ":" + found.map(receipt -> receipt.phase() + ":" + receipt.updatedAt()).orElse("missing"));
    }

    public void recover(Actor actor, UUID owner, UUID id, boolean completed, String reason) {
        thread();
        if (!actor.admin()) throw new UserError("Operator permission level 2 is required.");
        requireInactiveReview(owner, id);
        var reviews = quotes.get(owner);
        if (reviews != null) reviews.remove(id);
        operations.recover(actor, owner, id, completed, reason, nextRevision());
    }

    private void requireInactiveReview(UUID owner, UUID id) {
        var reviews = quotes.get(owner);
        CachedReview review = reviews == null ? null : reviews.get(id);
        if (review != null && !expired(review)) {
            throw new UserError("The original review is still active. Let it expire and inspect its status before manually reconciling it.");
        }
    }

    private UiView validateView(UiView view) {
        java.util.Objects.requireNonNull(view);
        for (UiAction action : view.actions()) action.selection().registeredAction();
        return view;
    }

    public static UUID operationId(String id) {
        try {
            UUID parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id) || parsed.equals(new UUID(0, 0))) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException invalid) {
            throw new UserError("Use the complete operation UUID.");
        }
    }

    public static UUID playerId(String id) {
        try {
            UUID parsed = UUID.fromString(id);
            if (!parsed.toString().equals(id)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException invalid) {
            throw new UserError("Use the complete affected player UUID.");
        }
    }

    public static String requestHash(ActionSelection selection) {
        return ActionPreview.digest(CommandLine.quote(selection.page()) + CommandLine.quote(selection.template())
                + CommandLine.quote(selection.rendered()));
    }

    private String summary(ActionSelection selection) {
        return selection.template().isEmpty() ? "Advanced command" : selection.registeredAction().label();
    }

    private long nextRevision() { return Math.addExact(runtime.store().revision(), 1); }
    private void thread() {
        if (!runtime.server().isSameThread()) throw new IllegalStateException("UI actions require the server thread.");
    }
    private static UiText text(String key, String fallback) { return UiText.tr("ui.statecraft.runtime." + key, fallback); }
}
