package dev.statecraft.runtime;

import dev.statecraft.StateCraft;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormConstraints;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormProvider;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.domain.GovernanceConfig;
import dev.statecraft.domain.GovernanceData;
import dev.statecraft.domain.GovernanceEngine;
import dev.statecraft.domain.GovernanceForms;
import dev.statecraft.domain.GovernancePresentation;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.network.SuiteNetwork;
import dev.statecraft.persistence.WorldStore;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class ServerRuntime {
    public record Reply(ActionOutcome outcome, String text) {
        public Reply(boolean success, String text) { this(success ? ActionOutcome.COMPLETED : ActionOutcome.REJECTED, text); }
        public boolean success() { return outcome.success(); }
    }

    private final MinecraftServer server;
    private final DecisionClock clock = new DecisionClock(Clock.systemUTC());
    private final WorldStore store;
    private final GovernanceEngine governance;
    private final FormProvider governanceForms;
    private final Map<String, FormProvider> formProviders = new LinkedHashMap<>();
    private final IntegrationLocks integrationLocks;
    private final UiRuntime ui;
    private final Map<String, BiFunction<ServerPlayer, String, String>> modules = new LinkedHashMap<>();
    private final Map<UUID, String> lastChunk = new HashMap<>();
    private final Map<UUID, TerritorySnapshot> lastSnapshot = new HashMap<>();
    private EconomyAccess economy = EconomyAccess.UNAVAILABLE;
    private boolean dirty = true;
    private IOException saveFailure;
    private long lastBackup;
    private long ticks;
    private long mutationRevision;
    private int deferredFlushDepth;

    public ServerRuntime(MinecraftServer server, GovernanceConfig config) throws IOException {
        this.server = server;
        store = new WorldStore(worldDirectory(), this::refreshIntegrationLocks);
        integrationLocks = store.load("integration_locks", IntegrationLocks.class, IntegrationLocks::new);
        economy = integrationLocks.offlineAccess();
        boolean migrating = store.migrated();
        GovernanceData data = store.load("governance", GovernanceData.class, GovernanceData::new);
        if (data.schemaVersion < 2 && Files.exists(store.directory().resolve("world.json"))) {
            Path backup = store.backupSnapshot("national-claims-v2");
            StateCraft.LOGGER.info("Preserved the pre-migration StateCraft snapshot at {}", backup);
        }
        governance = new GovernanceEngine(data, config, economy, clock::millis, this::markDirty);
        governanceForms = new GovernanceForms(governance);
        registerModule("statecraft", (player, command) -> executeCore(actor(player), command));
        ui = new UiRuntime(this, store.load("ui_operations", UiOperations.Data.class, UiOperations.Data::new));
        GovernancePresentation presentation = new GovernancePresentation(governance);
        ui.register("statecraft", presentation, (player, selection) -> presentation.preview(actor(player), selection));
        if (migrating) {
            StateCraft.LOGGER.warn("Migrating compatible legacy StateCraft section files. Originals are retained.");
        }
    }

    public MinecraftServer server() { return server; }
    public Path worldDirectory() { return server.getWorldPath(LevelResource.ROOT); }
    public WorldStore store() { return store; }
    public GovernanceAccess governance() { return governance; }
    public GovernanceEngine engine() { return governance; }
    public EconomyAccess economy() { return economy; }
    public DecisionClock clock() { return clock; }
    public UiRuntime ui() { return ui; }
    public void markDirty() { dirty = true; mutationRevision++; }
    long mutationRevision() { return mutationRevision; }

    void advanceGovernance() {
        requireWritable();
        governance.tick(clock.millis());
    }

    void rejectMutatingPreview() {
        IllegalStateException failure = new IllegalStateException("An action preview modified persistent state.");
        StateCraft.LOGGER.error("UI review provider violated the read-only contract; actions are paused.", failure);
        failedSave(new IOException("A UI preview changed persistent data. Inspect the server log before resuming.", failure));
        throw failure;
    }

    public boolean isWritable() { return saveFailure == null; }

    public void requireWritable() {
        if (!isWritable()) {
            throw new UserError("StateCraft is paused after a disk write failure. An operator must run /sc admin save.");
        }
    }

    public void installEconomy(EconomyAccess economy) {
        this.economy = java.util.Objects.requireNonNull(economy);
        governance.setEconomy(economy);
    }

    public void registerModule(String namespace, BiFunction<ServerPlayer, String, String> handler) {
        if (!namespace.matches("[a-z_]+") || modules.putIfAbsent(namespace, handler) != null) {
            throw new IllegalArgumentException("Duplicate or invalid StateCraft module: " + namespace);
        }
    }

    public void registerFormProvider(String namespace, FormProvider provider) {
        if (!namespace.matches("[a-z_]+") || namespace.equals("statecraft")
                || formProviders.putIfAbsent(namespace, java.util.Objects.requireNonNull(provider)) != null) {
            throw new IllegalArgumentException("Duplicate or invalid form provider: " + namespace);
        }
    }

    public FormSchema describeForm(ServerPlayer player, String pageId, String template,
                                   Map<String, String> values, FormQuery query) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Form choices must be resolved on the server thread.");
        }
        var page = MenuRegistry.get(pageId);
        if (page.actions().stream().noneMatch(action -> action.command().equals(template))) {
            throw new UserError("This action is not registered on the server. Install matching mod versions.");
        }
        FormContext context = new FormContext(actor(player), pageId, template, values, query);
        if (!modules.containsKey(context.namespace())) {
            throw new UserError("This module is not installed on the server.");
        }
        FormBuilder form = new FormBuilder(context);
        governanceForms.describe(context, form);
        FormProvider provider = formProviders.get(context.namespace());
        if (provider != null) {
            provider.describe(context, form);
        }
        if (template.equals(UiMenus.RESOLVE) || template.equals(UiMenus.RECOVER)) {
            boolean recover = template.equals(UiMenus.RECOVER);
            var choices = player.hasPermissions(2) ? ui.operations().unresolved(actor(player)).stream().map(receipt -> {
                String label = receipt.summary() + " - " + receipt.ownerName();
                if (label.length() > 110) label = label.substring(0, 107) + "...";
                return new FormChoice(receipt.operation().id().toString(), label + " [" + receipt.operation().id().toString().substring(0, 8) + "]",
                        receipt.phase().name());
            }).toList() : List.<FormChoice>of();
            form.choice("operation", "Operation", "Operators must inspect world, account and inventory history before reconciling.",
                    choices, "", recover ? List.of("player") : List.of(), recover && player.hasPermissions(2));
            if (recover) {
                form.choice("player", "Affected player UUID", "Use the player UUID shown with the original local recovery reference.",
                        player.hasPermissions(2) ? form.choices("player") : List.of(), "", List.of(), player.hasPermissions(2));
                form.constraints("player", FormConstraints.text(36));
                form.constraints("operation", FormConstraints.text(36));
            }
            form.choice("resolution", "Confirmed outcome", "This records an outcome; it never replays, refunds or undoes an action.",
                    List.of(new FormChoice("completed", "Confirmed completed"), new FormChoice("not_executed", "Confirmed not executed")),
                    "", List.of(), false);
            form.constraints("reason", FormConstraints.text(512));
        }
        return form.build();
    }

    public static Actor actor(ServerPlayer player) {
        return new Actor(player.getUUID(), player.getGameProfile().getName(), player.hasPermissions(2),
                player.level().dimension().location().toString(), player.chunkPosition().x, player.chunkPosition().z);
    }

    public Reply invoke(ServerPlayer player, String namespace, String line) {
        return invoke(actor(player), namespace, line, () -> {
            BiFunction<ServerPlayer, String, String> handler = modules.get(namespace);
            if (handler == null) {
                throw new UserError("The requested StateCraft module is not installed.");
            }
            return handler.apply(player, line);
        });
    }

    public Reply invoke(Actor actor, String namespace, String line, Supplier<String> action) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("StateCraft actions must run on the server thread.");
        }
        boolean started = false;
        long beforeAction = mutationRevision;
        Reply reply;
        try {
            List<String> args = CommandLine.split(line);
            boolean recovery = namespace.equals("statecraft") && args.size() == 2
                    && args.get(0).equalsIgnoreCase("admin")
                    && (args.get(1).equalsIgnoreCase("save") || args.get(1).equalsIgnoreCase("backup"));
            if (saveFailure != null && !recovery) {
                throw new UserError("StateCraft is paused because world data could not be saved. "
                        + "An operator must resolve the disk error and run /sc admin save.");
            }
            started = true;
            beforeAction = mutationRevision;
            try (var decision = clock.freeze()) {
                reply = new Reply(true, limit(action.get()));
            } finally {
                // Failed actions may still update profiles, accrue interest, or mark mail read.
                if (saveFailure == null && deferredFlushDepth == 0) {
                    flush();
                }
            }
        } catch (UserError e) {
            boolean partial = deferredFlushDepth > 0 && started && mutationRevision != beforeAction;
            reply = new Reply(partial ? ActionOutcome.UNCERTAIN : ActionOutcome.REJECTED,
                    limit(e.getMessage()) + (partial ? "\nState changed before this refusal. Check this operation before retrying." : ""));
        } catch (RuntimeException e) {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            StateCraft.LOGGER.error("StateCraft action failed [{}] for {} in {}", reference, actor.id(), namespace, e);
            reply = new Reply(ActionOutcome.UNCERTAIN, "StateCraft encountered an internal error (" + reference
                    + "). State may have changed; refresh before retrying. An operator can find details in the server log.");
        }
        return started && saveFailure != null
                ? new Reply(ActionOutcome.UNCERTAIN, "The action may have changed memory, but saving failed. StateCraft is paused; "
                        + "do not repeat the action. Contact an operator.")
                : reply;
    }

    Reply invokeDeferred(ServerPlayer player, String namespace, String line) {
        deferredFlushDepth++;
        try {
            return invoke(player, namespace, line);
        } finally {
            deferredFlushDepth--;
        }
    }

    public String executeCore(Actor actor, String line) {
        List<String> args = CommandLine.split(line);
        if (args.size() >= 7 && args.get(0).equalsIgnoreCase("admin") && args.get(1).equalsIgnoreCase("operation")
                && args.get(2).equalsIgnoreCase("recover")) {
            requireWritable();
            String resolution = args.get(5).toLowerCase(java.util.Locale.ROOT);
            if (!List.of("completed", "not_executed").contains(resolution)) {
                throw new UserError("Use completed or not_executed after independently auditing the operation.");
            }
            ui.recover(actor, UiRuntime.playerId(args.get(3)), UiRuntime.operationId(args.get(4)),
                    resolution.equals("completed"), CommandLine.tail(args, 6));
            return "Audited recovery recorded. No original action was replayed, refunded, or undone.";
        }
        if (args.size() >= 6 && args.get(0).equalsIgnoreCase("admin") && args.get(1).equalsIgnoreCase("operation")
                && args.get(2).equalsIgnoreCase("resolve")) {
            requireWritable();
            String resolution = args.get(4).toLowerCase(java.util.Locale.ROOT);
            if (!List.of("completed", "not_executed").contains(resolution)) {
                throw new UserError("Use completed or not_executed after independently auditing the operation.");
            }
            ui.operations().resolve(actor, UiRuntime.operationId(args.get(3)), resolution.equals("completed"),
                    CommandLine.tail(args, 5), Math.addExact(store.revision(), 1));
            return "Operation outcome recorded. No original command was replayed, refunded, or undone.";
        }
        if (args.size() == 2 && args.get(0).equalsIgnoreCase("admin")) {
            String action = args.get(1).toLowerCase(java.util.Locale.ROOT);
            if (List.of("save", "backup", "reload").contains(action)) {
                if (!actor.admin()) {
                    throw new UserError("Operator permission level 2 is required.");
                }
                if (action.equals("reload")) {
                    reloadGovernance(true);
                    return "StateCraft server configuration reloaded from disk.";
                }
                if (action.equals("backup")) {
                    try {
                        Path path = store.backup();
                        dirty = false;
                        saveFailure = null;
                        return "World snapshot backed up to " + path.getFileName() + ".";
                    } catch (IOException e) {
                        failedSave(e);
                        throw new UserError("Backup failed. See the server log; existing data was retained.");
                    }
                }
                saveNow();
                if (saveFailure != null) {
                    throw new UserError("Saving still fails. Resolve the disk error shown in the server log.");
                }
                return "StateCraft world data saved; actions are enabled.";
            }
        }
        requireWritable();
        return governance.execute(actor, line);
    }

    public void reloadGovernance(boolean disk) {
        try {
            GovernanceConfig config = disk ? StateCraft.CONFIG.reload() : StateCraft.CONFIG.read();
            config.validate();
            governance.configure(config);
            StateCraft.LOGGER.info("StateCraft server configuration reloaded.");
        } catch (RuntimeException e) {
            StateCraft.LOGGER.error("StateCraft configuration was rejected; previous gameplay settings retained.", e);
            if (disk) {
                throw new UserError("Configuration reload rejected: " + e.getMessage());
            }
            notifyOperators("StateCraft configuration reload was rejected; see the server log.");
        }
    }

    public void flush() {
        if (!dirty && saveFailure == null) {
            return;
        }
        try {
            store.save();
            dirty = false;
            saveFailure = null;
        } catch (IOException e) {
            failedSave(e);
        }
    }

    public void saveNow() {
        markDirty();
        flush();
    }

    private void refreshIntegrationLocks() {
        if (!economy.available()) {
            return;
        }
        integrationLocks.accounts.clear();
        integrationLocks.claims.clear();
        governance.governments().forEach(government -> {
            if (economy.isAccountInUse(government.account())) {
                integrationLocks.accounts.add(government.account());
            }
        });
        governance.companies().forEach(company -> {
            if (economy.isAccountInUse(company.account())) {
                integrationLocks.accounts.add(company.account());
            }
        });
        governance.claims().forEach(claim -> {
            if (economy.isClaimEncumbered(claim.key())) {
                integrationLocks.claims.add(claim.key());
            }
        });
    }

    private void failedSave(IOException failure) {
        if (saveFailure == null) {
            StateCraft.LOGGER.error("StateCraft persistence failed; actions are paused until /sc admin save succeeds.", failure);
            notifyOperators("StateCraft cannot save world data; actions are paused. Check the disk/server log.");
        }
        saveFailure = failure;
        dirty = true;
    }

    private void notifyOperators(String text) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
            }
        }
    }

    public void join(ServerPlayer player) {
        governance.login(actor(player));
        markDirty();
        SuiteNetwork.session(player);
        synchronize(player, true);
    }

    public void leave(UUID player) {
        lastChunk.remove(player);
        lastSnapshot.remove(player);
        SuiteNetwork.forget(player);
        ui.forget(player);
    }

    public void tick() {
        ticks++;
        if (ticks % 20 != 0) {
            return;
        }
        long now = clock.millis();
        if (saveFailure == null) {
            governance.tick(now);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Actor actor = actor(player);
            String oldChunk = lastChunk.put(player.getUUID(), actor.chunkKey());
            if (!actor.chunkKey().equals(oldChunk)) {
                if (saveFailure == null && governance.autoClaimEnabled(player.getUUID())) {
                    try {
                        governance.autoClaim(actor);
                    } catch (UserError e) {
                        player.displayClientMessage(Component.literal("Auto-claim: " + e.getMessage())
                                .withStyle(ChatFormatting.RED), true);
                    }
                }
                synchronize(player, true);
            } else if (ticks % 100 == 0) {
                synchronize(player, false);
            }
        }
        if (ticks % 600 == 0) {
            if (isWritable()) ui.operations().compact();
            saveNow();
        }
        if (saveFailure == null && now - lastBackup >= 86_400_000L) {
            try {
                store.backup();
                dirty = false;
                lastBackup = now;
            } catch (IOException e) {
                failedSave(e);
            }
        }
    }

    public void synchronize(ServerPlayer player, boolean force) {
        TerritorySnapshot snapshot = TerritorySnapshots.around(actor(player), governance,
                key -> StateCraft.LOGGER.error("Orphan claim {} was omitted from map sync; run /sc admin audit.", key),
                governance::knownPlayerName);
        if (force || !snapshot.equals(lastSnapshot.get(player.getUUID()))) {
            lastSnapshot.put(player.getUUID(), snapshot);
            SuiteNetwork.territory(player, snapshot);
        }
    }

    public void open(ServerPlayer player, String page) {
        MenuRegistry.get(page);
        SuiteNetwork.open(player, page);
    }

    private static String limit(String text) {
        if (text == null) {
            throw new IllegalStateException("StateCraft command returned no result.");
        }
        return text.length() <= 28_000 ? text
                : text.substring(0, 27_850) + "\nOutput limited. Use a page argument or a more specific query.";
    }
}
