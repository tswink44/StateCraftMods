package dev.statecraft.economy.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.statecraft.api.UserError;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

final class StrictJson {
    private StrictJson() {}

    static JsonObject read(Reader source) throws IOException {
        JsonReader reader = new JsonReader(source);
        reader.setLenient(false);
        JsonElement element = element(reader, 0);
        if (reader.peek() != JsonToken.END_DOCUMENT || !element.isJsonObject()) {
            throw new UserError("Expected exactly one JSON object.");
        }
        return element.getAsJsonObject();
    }

    private static JsonElement element(JsonReader reader, int depth) throws IOException {
        if (depth > 32) throw new UserError("JSON nesting exceeds 32 levels.");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                Set<String> keys = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!keys.add(name)) throw new UserError("Duplicate JSON key: " + name);
                    if (keys.size() > 100_000) throw new UserError("JSON object is too large.");
                    object.add(name, element(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= 10_000) throw new UserError("JSON array is too large.");
                    array.add(element(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new UserError("Unexpected token in JSON: " + reader.peek());
        };
    }
}
