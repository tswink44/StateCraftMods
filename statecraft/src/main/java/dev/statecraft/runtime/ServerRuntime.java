package dev.statecraft.runtime;

import dev.statecraft.StateCraft;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.EconomyAccess;
import dev.statecraft.api.GovernanceAccess;
import dev.statecraft.api.GovernanceAccess.ClaimView;
import dev.statecraft.api.GovernanceAccess.GovernmentView;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormBuilder;
import dev.statecraft.api.form.FormContext;
import dev.statecraft.api.form.FormProvider;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.domain.GovernanceConfig;
import dev.statecraft.domain.GovernanceData;
import dev.statecraft.domain.GovernanceEngine;
import dev.statecraft.domain.GovernanceForms;
import dev.statecraft.network.SuiteNetwork;
import dev.statecraft.persistence.WorldStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class ServerRuntime {
    public record Reply(boolean success, String text) {}

    private final MinecraftServer server;
    private final WorldStore store;
    private final GovernanceEngine governance;
    private final FormProvider governanceForms;
    private final Map<String, FormProvider> formProviders = new LinkedHashMap<>();
    private final IntegrationLocks integrationLocks;
    private final Map<String, BiFunction<ServerPlayer, String, String>> modules = new LinkedHashMap<>();
    private final Map<UUID, String> lastChunk = new HashMap<>();
    private final Map<UUID, TerritorySnapshot> lastSnapshot = new HashMap<>();
    private EconomyAccess economy = EconomyAccess.UNAVAILABLE;
    private boolean dirty = true;
    private IOException saveFailure;
    private long lastBackup;
    private long ticks;

    public ServerRuntime(MinecraftServer server, GovernanceConfig config) throws IOException {
        this.server = server;
        store = new WorldStore(worldDirectory());
        integrationLocks = store.load("integration_locks", IntegrationLocks.class, IntegrationLocks::new);
        economy = integrationLocks.offlineAccess();
        boolean migrating = store.migrated();
        GovernanceData data = store.load("governance", GovernanceData.class, GovernanceData::new);
        governance = new GovernanceEngine(data, config, economy, System::currentTimeMillis);
        governanceForms = new GovernanceForms(governance);
        registerModule("statecraft", (player, command) -> executeCore(actor(player), command));
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
    public void markDirty() { dirty = true; }

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
        return form.build();
    }

    public static Actor actor(ServerPlayer player) {
        return new Actor(player.getUUID(), player.getGameProfile().getName(), player.hasPermissions(2),
                player.level().dimension().location().toString(), player.chunkPosition().x, player.chunkPosition().z);
    }

    public Reply invoke(ServerPlayer player, String namespace, String line) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("StateCraft actions must run on the server thread.");
        }
        try {
            CommandLine.split(line);
            if (saveFailure != null && !(namespace.equals("statecraft")
                    && (line.equals("admin save") || line.equals("admin backup")))) {
                throw new UserError("StateCraft is paused because world data could not be saved. "
                        + "An operator must resolve the disk error and run /sc admin save.");
            }
            BiFunction<ServerPlayer, String, String> handler = modules.get(namespace);
            if (handler == null) {
                throw new UserError("The requested StateCraft module is not installed.");
            }
            String result = handler.apply(player, line);
            markDirty();
            flush();
            if (saveFailure != null) {
                return new Reply(false, "The action changed memory, but saving failed. StateCraft is paused; "
                        + "do not repeat the action. Contact an operator.");
            }
            return new Reply(true, limit(result));
        } catch (UserError e) {
            return new Reply(false, e.getMessage());
        } catch (RuntimeException e) {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            StateCraft.LOGGER.error("StateCraft action failed [{}] for {} in {}", reference, player.getUUID(), namespace, e);
            return new Reply(false, "StateCraft encountered an internal error (" + reference
                    + "). An operator can find details in the server log.");
        }
    }

    public String executeCore(Actor actor, String line) {
        List<String> args = CommandLine.split(line);
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
                        saveFailure = null;
                        return "World snapshot backed up to " + path.getFileName() + ".";
                    } catch (IOException e) {
                        failedSave(e);
                        throw new UserError("Backup failed. See the server log; existing data was retained.");
                    }
                }
                markDirty();
                flush();
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
            refreshIntegrationLocks();
            store.save();
            dirty = false;
            saveFailure = null;
        } catch (IOException e) {
            failedSave(e);
        }
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
        synchronize(player, true);
    }

    public void leave(UUID player) {
        lastChunk.remove(player);
        lastSnapshot.remove(player);
        SuiteNetwork.forget(player);
    }

    public void tick() {
        ticks++;
        if (ticks % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (saveFailure == null) {
            governance.tick(now);
            markDirty();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Actor actor = actor(player);
            String oldChunk = lastChunk.put(player.getUUID(), actor.chunkKey());
            if (!actor.chunkKey().equals(oldChunk)) {
                if (saveFailure == null && governance.autoClaimEnabled(player.getUUID())) {
                    try {
                        governance.autoClaim(actor);
                        markDirty();
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
            flush();
        }
        if (saveFailure == null && now - lastBackup >= 86_400_000L) {
            try {
                store.backup();
                lastBackup = now;
            } catch (IOException e) {
                failedSave(e);
            }
        }
    }

    public void synchronize(ServerPlayer player, boolean force) {
        Actor actor = actor(player);
        int radius = TerritorySnapshot.MAX_RADIUS;
        Map<String, GovernmentView> governments = new HashMap<>();
        governance.governments().forEach(g -> governments.put(g.id(), g));
        List<TerritorySnapshot.Territory> territories = new ArrayList<>();
        for (ClaimView claim : governance.claims()) {
            if (!claim.dimension().equals(actor.dimension())
                    || Math.abs((long) claim.x() - actor.chunkX()) > radius
                    || Math.abs((long) claim.z() - actor.chunkZ()) > radius) {
                continue;
            }
            GovernmentView nation = governments.get(claim.nationId());
            GovernmentView state = governments.get(claim.stateId());
            GovernmentView city = governments.get(claim.cityId());
            if (nation == null || state == null || city == null) {
                StateCraft.LOGGER.error("Orphan claim {} was omitted from map sync; run /sc admin audit.", claim.key());
                continue;
            }
            int color = 0x606060 | (claim.nationId().hashCode() & 0x9F9F9F);
            territories.add(new TerritorySnapshot.Territory(claim.x(), claim.z(), nation.id(), state.id(), city.id(),
                    nation.name(), state.name(), city.name(), claim.ownerAccount(), color, claim.improvements()));
        }
        territories.sort(java.util.Comparator.comparingInt(TerritorySnapshot.Territory::x)
                .thenComparingInt(TerritorySnapshot.Territory::z));
        TerritorySnapshot snapshot = new TerritorySnapshot(actor.dimension(), actor.chunkX(), actor.chunkZ(), radius, territories);
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
