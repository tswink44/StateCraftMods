package dev.statecraft.network;

import dev.statecraft.StateCraft;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.api.ui.*;
import dev.statecraft.client.ClientHooks;
import dev.statecraft.runtime.ServerRuntime;
import dev.statecraft.runtime.UiRuntime;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SuiteNetwork {
    private static final String PROTOCOL = "6";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StateCraft.MOD_ID, "suite"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final RequestLimiter LIMITER = new RequestLimiter();

    private SuiteNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, ActionRequest.class, ActionRequest::encode, ActionRequest::decode,
                ActionRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, ActionResponse.class, ActionResponse::encode, ActionResponse::decode,
                ActionResponse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, OpenScreen.class, OpenScreen::encode, OpenScreen::decode,
                OpenScreen::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, TerritoryMessage.class, TerritoryMessage::encode, TerritoryMessage::decode,
                TerritoryMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, BorderCycle.class, (message, buffer) -> {}, buffer -> new BorderCycle(),
                BorderCycle::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, FormRequest.class, FormRequest::encode, FormRequest::decode,
                FormRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, FormResponse.class, FormResponse::encode, FormResponse::decode,
                FormResponse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7, SessionRequest.class, (message, buffer) -> {}, buffer -> new SessionRequest(),
                SessionRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(8, SessionMessage.class, SessionMessage::encode, SessionMessage::decode,
                SessionMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9, ViewRequest.class, ViewRequest::encode, ViewRequest::decode,
                ViewRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(10, ViewResponse.class, ViewResponse::encode, ViewResponse::decode,
                ViewResponse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(11, PreviewRequest.class, PreviewRequest::encode, PreviewRequest::decode,
                PreviewRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(12, PreviewResponse.class, PreviewResponse::encode, PreviewResponse::decode,
                PreviewResponse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(13, OperationStatusRequest.class, OperationStatusRequest::encode, OperationStatusRequest::decode,
                OperationStatusRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(14, DashboardRequest.class, DashboardRequest::encode, DashboardRequest::decode,
                DashboardRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(15, DashboardResponse.class, DashboardResponse::encode, DashboardResponse::decode,
                DashboardResponse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(16, GovernmentRequest.class, GovernmentRequest::encode, GovernmentRequest::decode,
                GovernmentRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(17, GovernmentResponse.class, GovernmentResponse::encode, GovernmentResponse::decode,
                GovernmentResponse::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void request(int requestId, UUID world, ActionSelection selection, OperationRef operation) {
        CHANNEL.sendToServer(new ActionRequest(requestId, world, selection, operation));
    }

    public static void requestSession() { CHANNEL.sendToServer(new SessionRequest()); }
    public static void requestView(int id, UUID world, UiQuery query) { CHANNEL.sendToServer(new ViewRequest(id, world, query)); }
    public static void requestDashboard(int id, UUID world, PersonalDashboard.Request query) {
        CHANNEL.sendToServer(new DashboardRequest(id, world, query));
    }
    public static void requestGovernment(int id, UUID world, GovernmentOverview.Request query) {
        CHANNEL.sendToServer(new GovernmentRequest(id, world, query));
    }
    public static void requestPreview(int id, UUID world, ActionSelection selection) {
        CHANNEL.sendToServer(new PreviewRequest(id, world, selection));
    }
    public static void requestOperation(int id, OperationRef operation) {
        CHANNEL.sendToServer(new OperationStatusRequest(id, operation));
    }

    public static void session(ServerPlayer player) {
        ServerRuntime runtime = StateCraft.runtime();
        send(player, new SessionMessage(runtime.ui().world(), runtime.clock().millis()));
    }

    public static void requestForm(int requestId, String page, String template, Map<String, String> values, FormQuery query) {
        CHANNEL.sendToServer(new FormRequest(requestId, page, template, values, query));
    }

    public static void open(ServerPlayer player, String page) {
        MenuRegistry.get(page);
        session(player);
        send(player, new OpenScreen(page));
    }

    public static void territory(ServerPlayer player, TerritorySnapshot snapshot) {
        send(player, new TerritoryMessage(snapshot));
    }

    public static void cycleBorders(ServerPlayer player) {
        send(player, new BorderCycle());
    }

    public static void forget(UUID player) { LIMITER.forget(player); }
    public static void clear() { LIMITER.clear(); }

    private static <T> void send(ServerPlayer player, T message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public record ActionRequest(int id, UUID world, ActionSelection selection, OperationRef operation) {
        public ActionRequest(int id, String page, String command) {
            this(id, new UUID(0, 0), ActionSelection.raw(page, command), OperationRef.NONE);
        }

        public String page() { return selection.page(); }
        public String command() { return selection.rendered(); }

        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            UiCodec.selection(buffer, selection);
            UiCodec.operation(buffer, operation);
        }

        static ActionRequest decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            if (id < 0) {
                throw new DecoderException("Invalid StateCraft request identifier.");
            }
            return new ActionRequest(id, buffer.readUUID(), UiCodec.selection(buffer), UiCodec.operation(buffer));
        }

        private static void handle(ActionRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                if (!LIMITER.allow(player.getUUID(), System.nanoTime())) {
                    send(player, actionReply(message, message.operation.present() ? ActionOutcome.UNCERTAIN : ActionOutcome.REJECTED,
                            "Too many requests. Wait a moment, then check the original operation's status."));
                    return;
                }
                try {
                    checkWorld(player, message.world);
                    ServerRuntime runtime = StateCraft.runtime();
                    ServerRuntime.Reply reply = runtime.ui().execute(player, message.selection, message.operation);
                    ActionIntent intent = message.operation.present()
                            ? runtime.ui().operationIntent(player, message.operation) : message.selection.intent();
                    send(player, new ActionResponse(message.id, runtime.ui().world(), message.selection.page(),
                            message.operation, intent, reply.outcome(), reply.text()));
                } catch (UserError | UiRuntime.FormRejected rejected) {
                    send(player, actionReply(message, message.operation.present() ? ActionOutcome.UNCERTAIN : ActionOutcome.REJECTED,
                            errorText(rejected.getMessage())));
                } catch (RuntimeException failure) {
                    send(player, actionReply(message, ActionOutcome.UNCERTAIN, internalError("action", player, failure).fallback()));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    private static ActionResponse actionReply(ActionRequest request, ActionOutcome outcome, String text) {
        return new ActionResponse(request.id, StateCraft.runtime().ui().world(), request.selection.page(),
                request.operation, request.operation.present() ? ActionIntent.MUTATION : ActionIntent.QUERY, outcome, text);
    }

    public record ActionResponse(int id, UUID world, String page, OperationRef operation,
                                 ActionIntent intent, ActionOutcome outcome, String text) {
        public ActionResponse(int id, String page, boolean success, String text) {
            this(id, new UUID(0, 0), page, OperationRef.NONE, ActionIntent.QUERY,
                    success ? ActionOutcome.COMPLETED : ActionOutcome.REJECTED, text);
        }
        public boolean success() { return outcome.success(); }

        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            buffer.writeUtf(page, 96);
            UiCodec.operation(buffer, operation);
            buffer.writeByte(intent.ordinal());
            buffer.writeByte(outcome.ordinal());
            buffer.writeUtf(text, 30_000);
        }

        static ActionResponse decode(FriendlyByteBuf buffer) {
            return new ActionResponse(buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(96), UiCodec.operation(buffer),
                    UiCodec.enumeration(buffer, ActionIntent.values()), UiCodec.enumeration(buffer, ActionOutcome.values()),
                    buffer.readUtf(30_000));
        }

        private static void handle(ActionResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.reply(message)));
            ctx.setPacketHandled(true);
        }
    }

    public static final class SessionRequest {
        private static void handle(SessionRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null && LIMITER.allow(player.getUUID(), System.nanoTime())) session(player);
            });
            ctx.setPacketHandled(true);
        }
    }

    public record SessionMessage(UUID world, long serverTime) {
        void encode(FriendlyByteBuf buffer) { buffer.writeUUID(world); buffer.writeLong(serverTime); }
        static SessionMessage decode(FriendlyByteBuf buffer) { return new SessionMessage(buffer.readUUID(), buffer.readLong()); }
        private static void handle(SessionMessage message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.session(message.world, message.serverTime)));
            ctx.setPacketHandled(true);
        }
    }

    public record ViewRequest(int id, UUID world, UiQuery query) {
        void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(id); buffer.writeUUID(world); UiCodec.query(buffer, query); }
        static ViewRequest decode(FriendlyByteBuf buffer) {
            return new ViewRequest(UiCodec.count(buffer, Integer.MAX_VALUE), buffer.readUUID(), UiCodec.query(buffer));
        }
        private static void handle(ViewRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                UiView view = UiView.text("StateCraft", "");
                UiText error = UiText.EMPTY;
                boolean success = false;
                try {
                    limited(player);
                    checkWorld(player, message.world);
                    view = StateCraft.runtime().ui().view(player, message.query);
                    success = true;
                } catch (UserError rejected) {
                    error = UiText.literal(errorText(rejected.getMessage()));
                } catch (RuntimeException failure) {
                    error = internalError("view", player, failure);
                }
                send(player, new ViewResponse(message.id, StateCraft.runtime().ui().world(), message.query, success, error, view));
            });
            ctx.setPacketHandled(true);
        }
    }

    public record ViewResponse(int id, UUID world, UiQuery query, boolean success, UiText error, UiView view) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            UiCodec.query(buffer, query);
            buffer.writeBoolean(success);
            UiCodec.text(buffer, error);
            if (success) UiCodec.view(buffer, view);
        }
        static ViewResponse decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            UUID world = buffer.readUUID();
            UiQuery query = UiCodec.query(buffer);
            boolean success = buffer.readBoolean();
            UiText error = UiCodec.text(buffer);
            return new ViewResponse(id, world, query, success, error, success ? UiCodec.view(buffer) : UiView.text("StateCraft", ""));
        }
        private static void handle(ViewResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.viewReply(message)));
            ctx.setPacketHandled(true);
        }
    }

    public record DashboardRequest(int id, UUID world, PersonalDashboard.Request query) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            DashboardCodec.request(buffer, query);
        }

        static DashboardRequest decode(FriendlyByteBuf buffer) {
            return new DashboardRequest(UiCodec.count(buffer, Integer.MAX_VALUE), buffer.readUUID(), DashboardCodec.request(buffer));
        }

        private static void handle(DashboardRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                PersonalDashboard dashboard = null;
                UiText error = UiText.EMPTY;
                try {
                    limited(player);
                    checkWorld(player, message.world);
                    dashboard = StateCraft.runtime().ui().personalDashboard(player, message.query);
                } catch (UserError denied) {
                    error = UiText.literal(errorText(denied.getMessage()));
                } catch (RuntimeException failure) {
                    error = internalError("dashboard", player, failure);
                }
                send(player, new DashboardResponse(message.id, StateCraft.runtime().ui().world(), message.query,
                        dashboard != null, error, dashboard));
            });
            ctx.setPacketHandled(true);
        }
    }

    public record DashboardResponse(int id, UUID world, PersonalDashboard.Request query, boolean success,
                                    UiText error, PersonalDashboard dashboard) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            DashboardCodec.request(buffer, query);
            buffer.writeBoolean(success);
            UiCodec.text(buffer, error);
            if (success) DashboardCodec.dashboard(buffer, dashboard);
        }

        static DashboardResponse decode(FriendlyByteBuf buffer) {
            int id = UiCodec.count(buffer, Integer.MAX_VALUE);
            UUID world = buffer.readUUID();
            PersonalDashboard.Request query = DashboardCodec.request(buffer);
            boolean success = buffer.readBoolean();
            UiText error = UiCodec.text(buffer);
            return new DashboardResponse(id, world, query, success, error, success ? DashboardCodec.dashboard(buffer) : null);
        }

        private static void handle(DashboardResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.dashboardReply(message)));
            ctx.setPacketHandled(true);
        }
    }

    public record GovernmentRequest(int id, UUID world, GovernmentOverview.Request query) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            GovernmentOverviewCodec.request(buffer, query);
        }

        static GovernmentRequest decode(FriendlyByteBuf buffer) {
            return new GovernmentRequest(UiCodec.count(buffer, Integer.MAX_VALUE), buffer.readUUID(), GovernmentOverviewCodec.request(buffer));
        }

        private static void handle(GovernmentRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                GovernmentOverview overview = null;
                UiText error = UiText.EMPTY;
                try {
                    limited(player);
                    checkWorld(player, message.world);
                    overview = StateCraft.runtime().ui().governmentOverview(player, message.query);
                } catch (UserError denied) {
                    error = UiText.literal(errorText(denied.getMessage()));
                } catch (RuntimeException failure) {
                    error = internalError("government overview", player, failure);
                }
                send(player, new GovernmentResponse(message.id, StateCraft.runtime().ui().world(), message.query,
                        overview != null, error, overview));
            });
            ctx.setPacketHandled(true);
        }
    }

    public record GovernmentResponse(int id, UUID world, GovernmentOverview.Request query, boolean success,
                                     UiText error, GovernmentOverview overview) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            GovernmentOverviewCodec.request(buffer, query);
            buffer.writeBoolean(success);
            UiCodec.text(buffer, error);
            if (success) GovernmentOverviewCodec.overview(buffer, overview);
        }

        static GovernmentResponse decode(FriendlyByteBuf buffer) {
            int id = UiCodec.count(buffer, Integer.MAX_VALUE);
            UUID world = buffer.readUUID();
            var query = GovernmentOverviewCodec.request(buffer);
            boolean success = buffer.readBoolean();
            UiText error = UiCodec.text(buffer);
            return new GovernmentResponse(id, world, query, success, error, success ? GovernmentOverviewCodec.overview(buffer) : null);
        }

        private static void handle(GovernmentResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.governmentReply(message)));
            ctx.setPacketHandled(true);
        }
    }

    public record PreviewRequest(int id, UUID world, ActionSelection selection) {
        void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(id); buffer.writeUUID(world); UiCodec.selection(buffer, selection); }
        static PreviewRequest decode(FriendlyByteBuf buffer) {
            return new PreviewRequest(UiCodec.count(buffer, Integer.MAX_VALUE), buffer.readUUID(), UiCodec.selection(buffer));
        }
        private static void handle(PreviewRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                PreviewQuote quote = null;
                UiText error = UiText.EMPTY;
                Map<String, UiText> errors = Map.of();
                try {
                    limited(player);
                    checkWorld(player, message.world);
                    quote = StateCraft.runtime().ui().preview(player, message.selection);
                } catch (UiRuntime.FormRejected rejected) {
                    error = UiText.tr("gui.statecraft.form.invalid", rejected.getMessage());
                    errors = rejected.errors();
                } catch (UserError rejected) {
                    error = UiText.literal(errorText(rejected.getMessage()));
                } catch (RuntimeException failure) {
                    error = internalError("review", player, failure);
                }
                send(player, new PreviewResponse(message.id, StateCraft.runtime().ui().world(), message.selection,
                        quote != null, error, quote, errors));
            });
            ctx.setPacketHandled(true);
        }
    }

    public record PreviewResponse(int id, UUID world, ActionSelection selection, boolean success, UiText error,
                                  PreviewQuote quote, Map<String, UiText> fieldErrors) {
        public PreviewResponse {
            fieldErrors = Map.copyOf(fieldErrors);
            if (success != (quote != null) || fieldErrors.size() > 16) throw new IllegalArgumentException("Invalid review response.");
        }
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUUID(world);
            UiCodec.selection(buffer, selection);
            buffer.writeBoolean(success);
            UiCodec.text(buffer, error);
            if (success) UiCodec.quote(buffer, quote);
            UiCodec.errors(buffer, fieldErrors);
        }
        static PreviewResponse decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            UUID world = buffer.readUUID();
            ActionSelection selection = UiCodec.selection(buffer);
            boolean success = buffer.readBoolean();
            UiText error = UiCodec.text(buffer);
            PreviewQuote quote = success ? UiCodec.quote(buffer) : null;
            return new PreviewResponse(id, world, selection, success, error, quote, UiCodec.errors(buffer));
        }
        private static void handle(PreviewResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.previewReply(message)));
            ctx.setPacketHandled(true);
        }
    }

    public record OperationStatusRequest(int id, OperationRef operation) {
        void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(id); UiCodec.operation(buffer, operation); }
        static OperationStatusRequest decode(FriendlyByteBuf buffer) {
            return new OperationStatusRequest(UiCodec.count(buffer, Integer.MAX_VALUE), UiCodec.operation(buffer));
        }
        private static void handle(OperationStatusRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                ServerRuntime runtime = StateCraft.runtime();
                ServerRuntime.Reply reply;
                try {
                    limited(player);
                    reply = runtime.ui().status(player, message.operation);
                } catch (UserError rejected) {
                    reply = new ServerRuntime.Reply(ActionOutcome.UNCERTAIN, errorText(rejected.getMessage()));
                } catch (RuntimeException failure) {
                    reply = new ServerRuntime.Reply(ActionOutcome.UNCERTAIN, internalError("operation status", player, failure).fallback());
                }
                send(player, new ActionResponse(message.id, runtime.ui().world(), runtime.ui().operationPage(player, message.operation),
                        message.operation, runtime.ui().operationIntent(player, message.operation), reply.outcome(), reply.text()));
            });
            ctx.setPacketHandled(true);
        }
    }

    private static void limited(ServerPlayer player) {
        if (!LIMITER.allow(player.getUUID(), System.nanoTime())) throw new UserError("Too many requests. Wait a moment before refreshing.");
    }

    private static void checkWorld(ServerPlayer player, UUID world) {
        if (!StateCraft.runtime().ui().world().equals(world)) {
            session(player);
            throw new UserError("The world session changed. Refresh this screen before continuing.");
        }
    }

    private static UiText internalError(String action, ServerPlayer player, RuntimeException failure) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        StateCraft.LOGGER.error("StateCraft {} failed [{}] for {}", action, reference, player.getUUID(), failure);
        return UiText.tr("ui.statecraft.runtime.error", "Could not complete this request (" + reference + "). See the server log.", reference);
    }

    public record OpenScreen(String page) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeUtf(page, 96); }
        private static OpenScreen decode(FriendlyByteBuf buffer) { return new OpenScreen(buffer.readUtf(96)); }
        private static void handle(OpenScreen message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.open(message.page)));
            ctx.setPacketHandled(true);
        }
    }

    public record TerritoryMessage(TerritorySnapshot snapshot) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(snapshot.dimension(), 128);
            buffer.writeInt(snapshot.centerX());
            buffer.writeInt(snapshot.centerZ());
            buffer.writeVarInt(snapshot.radius());
            buffer.writeVarInt(snapshot.territories().size());
            for (TerritorySnapshot.Territory territory : snapshot.territories()) {
                buffer.writeInt(territory.x());
                buffer.writeInt(territory.z());
                buffer.writeUtf(territory.nationId(), 64);
                buffer.writeUtf(territory.stateId(), 64);
                buffer.writeUtf(territory.cityId(), 64);
                buffer.writeUtf(territory.nationName(), 128);
                buffer.writeUtf(territory.stateName(), 128);
                buffer.writeUtf(territory.cityName(), 128);
                buffer.writeUtf(territory.ownerAccount(), 128);
                buffer.writeInt(territory.color());
                buffer.writeVarInt(territory.improvements());
                buffer.writeUtf(territory.ownerName(), 128);
            }
        }

        static TerritoryMessage decode(FriendlyByteBuf buffer) {
            String dimension = buffer.readUtf(128);
            int x = buffer.readInt();
            int z = buffer.readInt();
            int radius = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (radius < 0 || radius > TerritorySnapshot.MAX_RADIUS
                    || count < 0 || count > TerritorySnapshot.MAX_TERRITORIES) {
                throw new DecoderException("Invalid StateCraft territory packet bounds.");
            }
            List<TerritorySnapshot.Territory> territories = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int tx = buffer.readInt();
                int tz = buffer.readInt();
                if (Math.abs((long) tx - x) > radius || Math.abs((long) tz - z) > radius) {
                    throw new DecoderException("StateCraft territory lies outside the requested region.");
                }
                territories.add(new TerritorySnapshot.Territory(tx, tz, buffer.readUtf(64), buffer.readUtf(64),
                        buffer.readUtf(64), buffer.readUtf(128), buffer.readUtf(128), buffer.readUtf(128),
                        buffer.readUtf(128), buffer.readInt(), buffer.readVarInt(), buffer.readUtf(128)));
            }
            return new TerritoryMessage(new TerritorySnapshot(dimension, x, z, radius, territories));
        }

        private static void handle(TerritoryMessage message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.territory(message.snapshot)));
            ctx.setPacketHandled(true);
        }
    }

    public record FormRequest(int id, String page, String command, Map<String, String> values, FormQuery query) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUtf(page, 96);
            buffer.writeUtf(command, CommandLine.MAX_LENGTH);
            FormCodec.writeValues(buffer, values);
            buffer.writeUtf(query.field(), 64);
            buffer.writeUtf(query.search(), 80);
            buffer.writeVarInt(query.offset());
        }

        static FormRequest decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            if (id < 0) {
                throw new DecoderException("Invalid form request identifier.");
            }
            String page = buffer.readUtf(96);
            String command = buffer.readUtf(CommandLine.MAX_LENGTH);
            Map<String, String> values = FormCodec.readValues(buffer);
            return new FormRequest(id, page, command, values,
                    new FormQuery(buffer.readUtf(64), buffer.readUtf(80), buffer.readVarInt()));
        }

        private static void handle(FormRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                if (!LIMITER.allow(player.getUUID(), System.nanoTime())) {
                    send(player, new FormResponse(message.id, message.page, message.command, false,
                            "Too many requests. Wait a moment and refresh choices.", FormSchema.EMPTY));
                    return;
                }
                try {
                    FormSchema schema = StateCraft.runtime().describeForm(player, message.page, message.command,
                            message.values, message.query);
                    send(player, new FormResponse(message.id, message.page, message.command, true, "", schema));
                } catch (UserError e) {
                    send(player, new FormResponse(message.id, message.page, message.command, false,
                            errorText(e.getMessage()), FormSchema.EMPTY));
                } catch (RuntimeException e) {
                    String reference = UUID.randomUUID().toString().substring(0, 8);
                    StateCraft.LOGGER.error("Form options failed [{}] for {} in {}", reference, player.getUUID(), message.page, e);
                    send(player, new FormResponse(message.id, message.page, message.command, false,
                            "Could not load choices (" + reference + "). See the server log.", FormSchema.EMPTY));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record FormResponse(int id, String page, String command, boolean success, String error, FormSchema schema) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUtf(page, 96);
            buffer.writeUtf(command, CommandLine.MAX_LENGTH);
            buffer.writeBoolean(success);
            buffer.writeUtf(error, 512);
            FormCodec.writeSchema(buffer, schema);
        }

        static FormResponse decode(FriendlyByteBuf buffer) {
            return new FormResponse(buffer.readVarInt(), buffer.readUtf(96), buffer.readUtf(CommandLine.MAX_LENGTH),
                    buffer.readBoolean(), buffer.readUtf(512), FormCodec.readSchema(buffer));
        }

        private static void handle(FormResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.formReply(message)));
            ctx.setPacketHandled(true);
        }
    }

    private static String errorText(String error) {
        return error.length() <= 512 ? error : error.substring(0, 509) + "...";
    }

    public static final class BorderCycle {
        private static void handle(BorderCycle message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::cycleBorders));
            ctx.setPacketHandled(true);
        }
    }
}
