package dev.statecraft.network;

import dev.statecraft.StateCraft;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.api.UserError;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.form.FormSchema;
import dev.statecraft.client.ClientHooks;
import dev.statecraft.runtime.ServerRuntime;
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
    private static final String PROTOCOL = "3";
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
    }

    public static void request(int requestId, String page, String command) {
        CHANNEL.sendToServer(new ActionRequest(requestId, page, command));
    }

    public static void requestForm(int requestId, String page, String template, Map<String, String> values, FormQuery query) {
        CHANNEL.sendToServer(new FormRequest(requestId, page, template, values, query));
    }

    public static void open(ServerPlayer player, String page) {
        MenuRegistry.get(page);
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

    public record ActionRequest(int id, String page, String command) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUtf(page, 96);
            buffer.writeUtf(command, CommandLine.MAX_LENGTH);
        }

        static ActionRequest decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt();
            if (id < 0) {
                throw new DecoderException("Invalid StateCraft request identifier.");
            }
            return new ActionRequest(id, buffer.readUtf(96), buffer.readUtf(CommandLine.MAX_LENGTH));
        }

        private static void handle(ActionRequest message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                if (!LIMITER.allow(player.getUUID(), System.nanoTime())) {
                    send(player, new ActionResponse(message.id, message.page, false, "Too many requests. Wait a moment."));
                    return;
                }
                try {
                    MenuRegistry.get(message.page);
                    String namespace = message.page.substring(0, message.page.indexOf(':'));
                    ServerRuntime.Reply reply = StateCraft.runtime().invoke(player, namespace, message.command);
                    send(player, new ActionResponse(message.id, message.page, reply.success(), reply.text()));
                } catch (UserError e) {
                    send(player, new ActionResponse(message.id, message.page, false, e.getMessage()));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record ActionResponse(int id, String page, boolean success, String text) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeUtf(page, 96);
            buffer.writeBoolean(success);
            buffer.writeUtf(text, 30_000);
        }

        private static ActionResponse decode(FriendlyByteBuf buffer) {
            return new ActionResponse(buffer.readVarInt(), buffer.readUtf(96), buffer.readBoolean(), buffer.readUtf(30_000));
        }

        private static void handle(ActionResponse message, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.reply(message)));
            ctx.setPacketHandled(true);
        }
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
                        buffer.readUtf(128), buffer.readInt(), buffer.readVarInt()));
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
