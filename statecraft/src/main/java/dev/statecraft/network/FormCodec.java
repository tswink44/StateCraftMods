package dev.statecraft.network;

import dev.statecraft.api.form.FormChoice;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormSchema;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;

final class FormCodec {
    private FormCodec() {}

    static void writeValues(FriendlyByteBuf buffer, Map<String, String> values) {
        buffer.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buffer.writeUtf(key, 64);
            buffer.writeUtf(value, 2048);
        });
    }

    static Map<String, String> readValues(FriendlyByteBuf buffer) {
        int count = count(buffer, FormSchema.MAX_FIELDS);
        Map<String, String> result = new LinkedHashMap<>();
        int length = 0;
        for (int i = 0; i < count; i++) {
            String key = buffer.readUtf(64);
            String value = buffer.readUtf(2048);
            length += value.length();
            if (result.putIfAbsent(key, value) != null || length > 4096) {
                throw new DecoderException("Invalid StateCraft form values.");
            }
        }
        return Map.copyOf(result);
    }

    static void writeSchema(FriendlyByteBuf buffer, FormSchema schema) {
        buffer.writeVarInt(schema.fields().size());
        for (FormField field : schema.fields()) {
            buffer.writeUtf(field.key(), 64);
            buffer.writeUtf(field.label(), 128);
            buffer.writeByte(field.kind().ordinal());
            buffer.writeUtf(field.value(), 2048);
            buffer.writeUtf(field.selectedLabel(), 128);
            buffer.writeUtf(field.hint(), 384);
            buffer.writeVarInt(field.dependencies().size());
            field.dependencies().forEach(dependency -> buffer.writeUtf(dependency, 64));
            buffer.writeBoolean(field.allowCustom());
            buffer.writeVarInt(field.choices().size());
            for (FormChoice choice : field.choices()) {
                buffer.writeUtf(choice.value(), 256);
                buffer.writeUtf(choice.label(), 128);
                buffer.writeUtf(choice.detail(), 256);
            }
            buffer.writeVarInt(field.offset());
            buffer.writeBoolean(field.more());
        }
    }

    static FormSchema readSchema(FriendlyByteBuf buffer) {
        int size = count(buffer, FormSchema.MAX_FIELDS);
        var fields = new ArrayList<FormField>(size);
        for (int i = 0; i < size; i++) {
            String key = buffer.readUtf(64);
            String label = buffer.readUtf(128);
            int kind = buffer.readUnsignedByte();
            if (kind >= FormField.Kind.values().length) {
                throw new DecoderException("Unknown StateCraft field type.");
            }
            String value = buffer.readUtf(2048);
            String selectedLabel = buffer.readUtf(128);
            String hint = buffer.readUtf(384);
            int dependenciesCount = count(buffer, FormSchema.MAX_FIELDS);
            var dependencies = new ArrayList<String>(dependenciesCount);
            for (int dependency = 0; dependency < dependenciesCount; dependency++) {
                dependencies.add(buffer.readUtf(64));
            }
            boolean custom = buffer.readBoolean();
            int choicesCount = count(buffer, FormSchema.PAGE_SIZE);
            var choices = new ArrayList<FormChoice>(choicesCount);
            for (int choice = 0; choice < choicesCount; choice++) {
                choices.add(new FormChoice(buffer.readUtf(256), buffer.readUtf(128), buffer.readUtf(256)));
            }
            int offset = count(buffer, 1_000_000);
            fields.add(new FormField(key, label, FormField.Kind.values()[kind], value, selectedLabel,
                    hint, dependencies, custom, choices, offset, buffer.readBoolean()));
        }
        return new FormSchema(fields);
    }

    private static int count(FriendlyByteBuf buffer, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) {
            throw new DecoderException("Invalid StateCraft form collection size.");
        }
        return value;
    }
}
