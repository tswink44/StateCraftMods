package dev.statecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.statecraft.StateCraft;
import dev.statecraft.api.BoundaryEdges;
import dev.statecraft.api.MenuCategory;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.TerritoryChangedEvent;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.PreviewQuote;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.UiText;
import dev.statecraft.client.state.ClientWorkspace;
import dev.statecraft.client.state.FormDraft;
import dev.statecraft.client.state.PendingOperations;
import dev.statecraft.client.state.PendingReferenceStore;
import dev.statecraft.client.state.RequestTracker;
import dev.statecraft.client.state.ServerClock;
import dev.statecraft.client.state.UiScope;
import dev.statecraft.client.state.ViewState;
import dev.statecraft.network.SuiteNetwork;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientHooks {
    public static final KeyMapping MENU = new KeyMapping("key.statecraft.menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.statecraft");
    public static final KeyMapping BORDERS = new KeyMapping("key.statecraft.borders", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.statecraft");
    private static final int MAX_WORKSPACES = 4;
    private static final Map<UiScope, ClientWorkspace> WORKSPACES = new LinkedHashMap<>();
    private static final RequestTracker<Consumer<SuiteNetwork.ActionResponse>> RESPONSES = new RequestTracker<>();
    private static final RequestTracker<Consumer<SuiteNetwork.FormResponse>> FORMS = new RequestTracker<>();
    private static final RequestTracker<Consumer<SuiteNetwork.ViewResponse>> VIEWS = new RequestTracker<>();
    private static final RequestTracker<Consumer<SuiteNetwork.PreviewResponse>> PREVIEWS = new RequestTracker<>();
    private static final ArrayDeque<OperationRef> RECONCILE = new ArrayDeque<>();
    private static final ServerClock CLOCK = new ServerClock();
    private static final PendingReferenceStore REFERENCES = new PendingReferenceStore(
            FMLPaths.GAMEDIR.get().resolve("statecraft").resolve("pending"));
    private record FormKey(String page, String command) {}
    private static TerritorySnapshot territory = TerritorySnapshot.EMPTY;
    private static BoundaryEdges.Mode borderMode = BoundaryEdges.Mode.OFF;
    private static ClientWorkspace workspace;
    private static long connection;
    private static int nextRequest;
    private static long sessionRequestedAt;
    private static long nextReconcileAt;
    private static UiText recoveryError = UiText.EMPTY;
    private static boolean recoveryLoaded;
    private static String opening = "";

    private ClientHooks() {}

    public static TerritorySnapshot territory() { return territory; }
    public static BoundaryEdges.Mode borderMode() { return borderMode; }
    public static int nextRequest() { return nextRequest = nextRequest == Integer.MAX_VALUE ? 1 : nextRequest + 1; }
    static ClientWorkspace workspace() { return workspace; }
    static UiScope scope() { return workspace == null ? null : workspace.scope(); }
    static boolean current(UiScope scope) { return scope != null && scope.equals(scope()); }
    static ServerClock clock() { return CLOCK; }
    static long time() { return net.minecraft.Util.getMillis(); }
    static UiText recoveryError() { return recoveryError; }
    static int pendingCount() { return workspace == null ? 0 : workspace.operations().pendingReferences().size(); }

    public static void session(UUID world, long serverTime) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        UiScope announced = new UiScope(world, minecraft.player.getUUID());
        if (workspace != null && !workspace.scope().equals(announced)) return;
        CLOCK.observe(serverTime, time());
        sessionRequestedAt = time();
        if (workspace == null) {
            workspace = WORKSPACES.remove(announced);
            if (workspace == null) workspace = new ClientWorkspace(announced);
            WORKSPACES.put(announced, workspace);
            while (WORKSPACES.size() > MAX_WORKSPACES) WORKSPACES.remove(WORKSPACES.keySet().iterator().next());
            loadRecovery();
            RECONCILE.addAll(workspace.operations().pendingReferences());
            if (pendingCount() > 0) minecraft.player.displayClientMessage(ClientText.tr(
                    "gui.statecraft.operations.restored", "%s pending operation(s) restored. Press N, then Operations; do not repeat them.",
                    pendingCount()), false);
        }
        if (!opening.isEmpty()) {
            String page = opening;
            opening = "";
            open(page);
        }
    }

    static void refreshSession() {
        if (Minecraft.getInstance().player == null) return;
        long now = time();
        if (now - sessionRequestedAt < 3000) return;
        sessionRequestedAt = now;
        SuiteNetwork.requestSession();
    }

    static void loadRecovery() {
        if (workspace == null) return;
        try {
            for (OperationRef operation : REFERENCES.read(workspace.scope())) workspace.operations().restore(operation);
            recoveryLoaded = true;
            recoveryError = UiText.EMPTY;
        } catch (IOException | IllegalArgumentException | IllegalStateException failure) {
            recoveryLoaded = false;
            recoveryFailure(failure);
        }
    }

    private static void recoveryFailure(Exception failure) {
        StateCraft.LOGGER.warn("StateCraft client recovery references are unavailable.", failure);
        recoveryError = UiText.tr("gui.statecraft.operations.storage_error",
                "Recovery references could not be saved or read. No new operation will be sent. Check write access to gameDir\\statecraft, then reload recovery.",
                new String[0]);
    }

    private static boolean persist() {
        if (workspace == null || !recoveryLoaded) return false;
        try {
            REFERENCES.write(workspace.scope(), workspace.operations().pendingReferences());
            recoveryError = UiText.EMPTY;
            return true;
        } catch (IOException failure) {
            recoveryFailure(failure);
            return false;
        }
    }

    static UiText submissionProblem(String template) {
        if (workspace == null) return UiText.tr("gui.statecraft.session.waiting", "Waiting for the server world identity.");
        if (!recoveryLoaded || !recoveryError.fallback().isEmpty()) return recoveryError;
        if (!workspace.operations().capacityAvailable()) return UiText.tr("gui.statecraft.operations.capacity",
                "Reconcile pending operations before starting another (limit 128).");
        if (workspace.operations().blocksNewReview(template)) return UiText.tr("gui.statecraft.operations.recovered_lock",
                "Reconcile restored operations before a new mutation. Original private inputs were not saved, so affected forms cannot be identified safely.");
        return UiText.EMPTY;
    }

    static PendingOperations.Entry submit(PreviewQuote quote, ActionSelection selection, ActionIntent intent,
                                           ViewState origin, FormDraft draft) {
        if (workspace == null || !CLOCK.validUntil(quote.expiresAt(), time())
                || !quote.operation().world().equals(workspace.scope().world())
                || !submissionProblem(selection.template()).fallback().isEmpty()) return null;
        PendingOperations.Entry entry = workspace.operations().register(quote, selection, intent,
                origin == null ? null : origin.id());
        if (draft != null) draft.operation(entry.operation());
        sendSame(entry);
        return entry;
    }

    static boolean retry(OperationRef reference) {
        if (workspace == null) return false;
        PendingOperations.Entry entry = workspace.operations().get(reference);
        return entry != null && entry.retryable() && sendSame(entry);
    }

    private static boolean sendSame(PendingOperations.Entry entry) {
        if (!entry.retryable() || !persist()) return false;
        int id = nextRequest();
        workspace.operations().submitted(entry.operation(), id, time());
        try {
            SuiteNetwork.request(id, workspace.scope().world(), entry.selection(), entry.operation());
        } catch (RuntimeException failure) {
            StateCraft.LOGGER.warn("StateCraft operation reply may be lost; retaining its recovery reference.", failure);
            workspace.operations().reply(entry.operation(), id, ActionOutcome.UNCERTAIN,
                    ClientText.tr("gui.statecraft.operations.transport",
                            "Connection lost while submitting. The outcome is uncertain; check this operation, never submit a replacement.").getString());
            return false;
        }
        return true;
    }

    static void checkOperation(OperationRef reference) {
        if (workspace == null || !reference.world().equals(workspace.scope().world())) return;
        PendingOperations.Entry entry;
        try {
            entry = workspace.operations().restore(reference);
        } catch (IllegalStateException full) {
            return;
        }
        if (entry.inFlight() || entry.terminal()) return;
        int id = nextRequest();
        workspace.operations().checking(reference, id, time());
        try {
            SuiteNetwork.requestOperation(id, reference);
        } catch (RuntimeException failure) {
            workspace.operations().reply(reference, id, ActionOutcome.UNCERTAIN,
                    ClientText.tr("gui.statecraft.operations.status_transport",
                            "Could not reach the server for status. No operation was repeated. Reconnect and check again.").getString());
        }
    }

    public static void reply(SuiteNetwork.ActionResponse response) {
        if (workspace == null || !workspace.scope().world().equals(response.world())) return;
        if (response.operation().present()) {
            if (!workspace.operations().reply(response.operation(), response.id(), response.outcome(), response.text())) return;
            PendingOperations.Entry operation = workspace.operations().get(response.operation());
            persist();
            ViewState origin = operation.origin() == null ? null : workspace.navigation().find(operation.origin());
            if (origin != null) origin.mutationResult(operation.intent(), operation.selection(), response.outcome(), response.text());
            ViewState current = workspace.navigation().current().view();
            if (current != null && current != origin) {
                current.mutationResult(ActionIntent.MUTATION, null, response.outcome(), response.text());
            }
            if (response.outcome() == ActionOutcome.COMPLETED && current != null && current.automaticRefresh()) refresh(current);
            return;
        }
        RESPONSES.take(response.id(), scope(), connection, response.page())
                .or(() -> RESPONSES.take(response.id(), scope(), connection, "*"))
                .ifPresent(receiver -> receiver.accept(response));
    }

    static void refresh(ViewState state) {
        if (state.pending() >= 0 || workspace == null || state.content() == ViewState.Content.RAW) return;
        if (state.content() == ViewState.Content.QUERY && state.explicit() != null) query(state, state.explicit());
        else requestView(state);
    }

    static void requestView(ViewState state) {
        if (workspace == null) { refreshSession(); return; }
        cancelView(state);
        int id = nextRequest();
        UiQuery query = state.query();
        long revision = state.revision();
        state.request(id);
        VIEWS.watch(id, scope(), connection, query, time(), response -> {
            if (!state.accept(response.id(), revision)) return;
            if (response.success()) state.view(response.view());
            else state.failure(response.error());
        }, () -> {
            if (state.accept(id, revision)) state.failure(UiText.tr("gui.statecraft.view.timeout",
                    "The view did not arrive. Refresh to try this same query again."));
        });
        SuiteNetwork.requestView(id, workspace.scope().world(), query);
    }

    static void query(ViewState state, ActionSelection selection) {
        if (workspace == null) return;
        cancelView(state);
        state.explicit(selection);
        int id = nextRequest();
        long revision = state.revision();
        state.request(id);
        RESPONSES.watch(id, scope(), connection, selection.page(), time(), response -> {
            if (!state.accept(response.id(), revision)) return;
            state.queryResult(response.success(), response.text());
            if (!response.success()) state.feedback(response.outcome(), UiText.literal(response.text()));
        }, () -> {
            if (state.accept(id, revision)) state.failure(UiText.tr("gui.statecraft.query.timeout",
                    "The query did not arrive. Refresh repeats the same query, not the section default."));
        });
        SuiteNetwork.request(id, workspace.scope().world(), selection, OperationRef.NONE);
    }

    static void cancelView(ViewState state) {
        int id = state.pending();
        VIEWS.forget(id);
        RESPONSES.forget(id);
        state.cancel(id);
    }

    public static void viewReply(SuiteNetwork.ViewResponse response) {
        if (workspace == null || !scope().world().equals(response.world())) return;
        VIEWS.take(response.id(), scope(), connection, response.query()).ifPresent(receiver -> receiver.accept(response));
    }

    static int preview(ActionSelection selection, Consumer<SuiteNetwork.PreviewResponse> receiver, Runnable timeout) {
        if (workspace == null) return -1;
        int id = nextRequest();
        PREVIEWS.watch(id, scope(), connection, selection, time(), receiver, timeout);
        SuiteNetwork.requestPreview(id, scope().world(), selection);
        return id;
    }
    static void forgetPreview(int id) { PREVIEWS.forget(id); }

    public static void previewReply(SuiteNetwork.PreviewResponse response) {
        if (workspace == null || !scope().world().equals(response.world())) return;
        PREVIEWS.take(response.id(), scope(), connection, response.selection()).ifPresent(receiver -> receiver.accept(response));
    }

    static int requestForm(String page, String template, Map<String, String> values, FormQuery query,
                           Consumer<SuiteNetwork.FormResponse> receiver, Runnable timeout) {
        int id = nextRequest();
        FORMS.watch(id, scope(), connection, new FormKey(page, template), time(), receiver, timeout);
        SuiteNetwork.requestForm(id, page, template, values, query);
        return id;
    }

    public static void formReply(SuiteNetwork.FormResponse response) {
        if (workspace == null) return;
        FORMS.take(response.id(), scope(), connection, new FormKey(response.page(), response.command()))
                .or(() -> FORMS.take(response.id(), scope(), connection, "*")).ifPresent(receiver -> receiver.accept(response));
    }

    public static void watch(int id, ManagementScreen screen) {
        RESPONSES.watch(id, scope(), connection, screen.page().id(), time(), screen::reply, () -> {});
    }
    static void watchAction(int id, Consumer<SuiteNetwork.ActionResponse> receiver) {
        RESPONSES.watch(id, scope(), connection, "*", time(), receiver, () -> {});
    }
    public static void forget(int id) { RESPONSES.forget(id); }
    static void watchForm(int id, Consumer<SuiteNetwork.FormResponse> receiver) {
        FORMS.watch(id, scope(), connection, "*", time(), receiver, () -> {});
    }
    static void forgetForm(int id) { FORMS.forget(id); }

    public static void open(String page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (workspace == null) {
            opening = page;
            minecraft.setScreen(new SessionScreen());
            refreshSession();
            return;
        }
        if (page.equals("statecraft:main")) sections(null);
        else navigate(UiQuery.page(page));
    }

    static void sections(MenuCategory category) {
        if (workspace == null) { open("statecraft:main"); return; }
        workspace.navigation().sections(category == null ? "" : category.name());
        showLocation();
    }
    static void navigate(UiQuery query) {
        if (workspace == null) return;
        try {
            MenuRegistry.get(query.page());
            workspace.navigation().open(query);
            showLocation();
        } catch (UserError failure) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new InformationScreen(minecraft.screen,
                    ClientText.tr("gui.statecraft.navigation.unavailable", "Section unavailable"),
                    ClientText.tr("gui.statecraft.navigation.version", "This section is not registered. Install matching client/server modules.")));
        }
    }
    static void openEntity(EntityRef entity) {
        if (!entity.present() || workspace == null) return;
        if (entity.kind() == EntityRef.Kind.OPERATION) {
            workspace.operations().tracked(entity).ifPresent(ClientHooks::checkOperation);
        }
        navigate(UiQuery.detail(entity));
    }
    static void showLocation() {
        if (workspace == null) return;
        var location = workspace.navigation().current();
        Minecraft.getInstance().setScreen(location.view() == null ? new NavigationScreen(location)
                : new ManagementScreen(location.view()));
    }
    static void back() {
        if (workspace != null && workspace.navigation().back()) showLocation();
        else Minecraft.getInstance().setScreen(null);
    }
    static void operations(Screen previous) {
        if (workspace != null) Minecraft.getInstance().setScreen(new OperationsScreen(previous));
    }
    static void cancelOpening() { opening = ""; }

    public static void territory(TerritorySnapshot snapshot) {
        territory = snapshot;
        MinecraftForge.EVENT_BUS.post(new TerritoryChangedEvent(snapshot));
    }
    public static void cycleBorders() {
        borderMode = borderMode.next();
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                ClientText.tr("gui.statecraft.borders.mode", "StateCraft borders: %s",
                        ClientText.tr("gui.statecraft.borders." + borderMode.name().toLowerCase(java.util.Locale.ROOT),
                                borderMode.label()).getString()), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = time();
        RESPONSES.expire(now);
        FORMS.expire(now);
        VIEWS.expire(now);
        PREVIEWS.expire(now);
        boolean expired = workspace != null && workspace.operations().expire(now);
        if (Minecraft.getInstance().player == null) return;
        if (expired) Minecraft.getInstance().player.displayClientMessage(ClientText.tr(
                "gui.statecraft.operations.timeout_notice",
                "An operation timed out. Do not repeat it; press N, then Operations to check its status."), false);
        if (workspace == null || !CLOCK.fresh(now)) refreshSession();
        if (workspace != null && !RECONCILE.isEmpty() && now >= nextReconcileAt) {
            checkOperation(RECONCILE.removeFirst());
            nextReconcileAt = now + 750;
        }
        while (MENU.consumeClick()) open("statecraft:main");
        while (BORDERS.consumeClick()) cycleBorders();
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        connection++;
        if (workspace != null) {
            workspace.operations().disconnected();
            workspace.navigation().invalidate();
            persist();
        }
        workspace = null;
        recoveryLoaded = false;
        recoveryError = UiText.EMPTY;
        opening = "";
        CLOCK.clear();
        sessionRequestedAt = 0;
        RESPONSES.clear();
        FORMS.clear();
        VIEWS.clear();
        PREVIEWS.clear();
        RECONCILE.clear();
        territory(TerritorySnapshot.EMPTY);
    }

    @Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(MENU);
            event.register(BORDERS);
        }
    }
}
