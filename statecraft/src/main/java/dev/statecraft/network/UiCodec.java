package dev.statecraft.network;

import dev.statecraft.api.ui.*;
import dev.statecraft.api.form.FormSchema;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

final class UiCodec {
    private UiCodec() {}

    static void operation(FriendlyByteBuf buffer, OperationRef operation) {
        buffer.writeUUID(operation.world());
        buffer.writeUUID(operation.id());
    }

    static OperationRef operation(FriendlyByteBuf buffer) {
        return new OperationRef(buffer.readUUID(), buffer.readUUID());
    }

    static void text(FriendlyByteBuf buffer, UiText text) {
        buffer.writeUtf(text.key(), 192);
        buffer.writeUtf(text.fallback(), 30_000);
        buffer.writeVarInt(text.arguments().size());
        text.arguments().forEach(value -> buffer.writeUtf(value, 2048));
    }

    static UiText text(FriendlyByteBuf buffer) { return new Reader(buffer, 30_000).text(); }

    static void query(FriendlyByteBuf buffer, UiQuery query) {
        buffer.writeUtf(query.page(), 96);
        entity(buffer, query.entity());
        buffer.writeUtf(query.search(), 80);
        buffer.writeVarInt(query.offset());
    }

    static UiQuery query(FriendlyByteBuf buffer) { return new Reader(buffer, 1024).query(); }

    private static void entity(FriendlyByteBuf buffer, EntityRef entity) {
        buffer.writeUtf(entity.namespace(), 48);
        buffer.writeByte(entity.kind().ordinal());
        buffer.writeUtf(entity.id(), 256);
    }

    static void selection(FriendlyByteBuf buffer, ActionSelection selection) {
        buffer.writeUtf(selection.page(), 96);
        buffer.writeUtf(selection.template(), 4096);
        FormCodec.writeValues(buffer, selection.values());
        buffer.writeUtf(selection.command(), 4096);
    }

    static ActionSelection selection(FriendlyByteBuf buffer) { return new Reader(buffer, 13_500).selection(); }

    static void view(FriendlyByteBuf buffer, UiView view) {
        text(buffer, view.title());
        text(buffer, view.body());
        buffer.writeVarInt(view.rows().size());
        for (UiRow row : view.rows()) {
            text(buffer, row.title());
            text(buffer, row.detail());
            entity(buffer, row.entity());
        }
        buffer.writeVarInt(view.actions().size());
        for (UiAction action : view.actions()) {
            buffer.writeUtf(action.page(), 96);
            buffer.writeUtf(action.template(), 4096);
            text(buffer, action.label());
            FormCodec.writeValues(buffer, action.values());
            buffer.writeBoolean(action.enabled());
            text(buffer, action.disabledReason());
        }
        buffer.writeVarInt(view.offset());
        buffer.writeBoolean(view.more());
        text(buffer, view.emptyHint());
    }

    static UiView view(FriendlyByteBuf buffer) { return new Reader(buffer, 80_000).view(); }

    static void quote(FriendlyByteBuf buffer, PreviewQuote quote) {
        operation(buffer, quote.operation());
        buffer.writeLong(quote.expiresAt());
        ActionPreview preview = quote.preview();
        text(buffer, preview.title());
        buffer.writeVarInt(preview.lines().size());
        for (ActionPreview.Line line : preview.lines()) {
            text(buffer, line.label());
            text(buffer, line.value());
            buffer.writeBoolean(line.material());
        }
        text(buffer, preview.warning());
        buffer.writeUtf(preview.stateKey(), 4096);
    }

    static PreviewQuote quote(FriendlyByteBuf buffer) { return new Reader(buffer, 50_000).quote(); }

    static void errors(FriendlyByteBuf buffer, Map<String, UiText> errors) {
        buffer.writeVarInt(errors.size());
        errors.forEach((key, value) -> {
            buffer.writeUtf(key, 64);
            text(buffer, value);
        });
    }

    static Map<String, UiText> errors(FriendlyByteBuf buffer) {
        Reader reader = new Reader(buffer, 50_000);
        int count = count(buffer, 16);
        Map<String, UiText> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (result.putIfAbsent(reader.string(64), reader.text()) != null) {
                throw new DecoderException("Duplicate form error field.");
            }
        }
        return Map.copyOf(result);
    }

    static int count(FriendlyByteBuf buffer, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) throw new DecoderException("Invalid UI collection or page bound.");
        return value;
    }

    static <T extends Enum<T>> T enumeration(FriendlyByteBuf buffer, T[] values) {
        int index = buffer.readUnsignedByte();
        if (index >= values.length) throw new DecoderException("Invalid UI enumeration.");
        return values[index];
    }

    private static final class Reader {
        private final FriendlyByteBuf buffer;
        private int remaining;

        Reader(FriendlyByteBuf buffer, int budget) {
            this.buffer = buffer;
            remaining = budget;
        }

        String string(int maximum) {
            String text = buffer.readUtf(maximum);
            remaining -= text.length();
            if (remaining < 0) throw new DecoderException("UI text exceeds its aggregate budget.");
            return text;
        }

        UiText text() {
            String key = string(192);
            String fallback = string(30_000);
            int count = count(buffer, 16);
            List<String> arguments = new ArrayList<>(count);
            for (int index = 0; index < count; index++) arguments.add(string(2048));
            return new UiText(key, fallback, arguments);
        }

        EntityRef entity() {
            return new EntityRef(string(48), enumeration(buffer, EntityRef.Kind.values()), string(256));
        }

        UiQuery query() {
            return new UiQuery(string(96), entity(), string(80), count(buffer, 100_000));
        }

        Map<String, String> values() {
            int count = count(buffer, 16);
            Map<String, String> values = new LinkedHashMap<>();
            int total = 0;
            for (int index = 0; index < count; index++) {
                String key = string(64);
                String value = string(FormSchema.MAX_VALUE_LENGTH);
                total += value.length();
                if (values.putIfAbsent(key, value) != null || total > 4096) {
                    throw new DecoderException("Invalid UI action values.");
                }
            }
            return Map.copyOf(values);
        }

        ActionSelection selection() {
            return new ActionSelection(string(96), string(4096), values(), string(4096));
        }

        UiView view() {
            UiText title = text();
            UiText body = text();
            int rowCount = count(buffer, UiView.MAX_ROWS);
            List<UiRow> rows = new ArrayList<>(rowCount);
            for (int index = 0; index < rowCount; index++) rows.add(new UiRow(text(), text(), entity()));
            int actionCount = count(buffer, UiView.MAX_ACTIONS);
            List<UiAction> actions = new ArrayList<>(actionCount);
            for (int index = 0; index < actionCount; index++) {
                actions.add(new UiAction(string(96), string(4096), text(), values(), buffer.readBoolean(), text()));
            }
            return new UiView(title, body, rows, actions, count(buffer, 100_000), buffer.readBoolean(), text());
        }

        PreviewQuote quote() {
            OperationRef operation = operation(buffer);
            long expiresAt = buffer.readLong();
            UiText title = text();
            int count = count(buffer, ActionPreview.MAX_LINES);
            List<ActionPreview.Line> lines = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                lines.add(new ActionPreview.Line(text(), text(), buffer.readBoolean()));
            }
            return new PreviewQuote(operation, expiresAt, new ActionPreview(title, lines, text(), string(4096)));
        }
    }
}
