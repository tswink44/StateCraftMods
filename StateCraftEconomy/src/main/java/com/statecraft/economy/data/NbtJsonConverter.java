package com.statecraft.economy.data;

import com.google.gson.*;
import net.minecraft.nbt.*;

import java.util.Map;

/**
 * Converts between Minecraft NBT CompoundTags and Gson JsonObjects.
 * Copy of StateCraft's NbtJsonConverter for the Economy module.
 *
 * @see com.statecraft.data.NbtJsonConverter
 */
public final class NbtJsonConverter {

    private NbtJsonConverter() {}

    // ======================== NBT → JSON ========================

    public static JsonObject toJson(CompoundTag tag) {
        JsonObject obj = new JsonObject();
        for (String key : tag.getAllKeys()) {
            Tag nbt = tag.get(key);
            if (nbt != null) {
                obj.add(key, tagToJson(nbt));
            }
        }
        return obj;
    }

    private static JsonElement tagToJson(Tag nbt) {
        return switch (nbt.getId()) {
            case Tag.TAG_COMPOUND -> toJson((CompoundTag) nbt);
            case Tag.TAG_LIST -> listTagToJson((ListTag) nbt);
            case Tag.TAG_STRING -> new JsonPrimitive(nbt.getAsString());
            case Tag.TAG_BYTE -> wrapTyped("byte", ((ByteTag) nbt).getAsByte());
            case Tag.TAG_SHORT -> wrapTyped("short", ((ShortTag) nbt).getAsShort());
            case Tag.TAG_INT -> wrapTyped("int", ((IntTag) nbt).getAsInt());
            case Tag.TAG_LONG -> wrapTyped("long", ((LongTag) nbt).getAsLong());
            case Tag.TAG_FLOAT -> wrapTyped("float", ((FloatTag) nbt).getAsFloat());
            case Tag.TAG_DOUBLE -> wrapTyped("double", ((DoubleTag) nbt).getAsDouble());
            case Tag.TAG_BYTE_ARRAY -> byteArrayToJson((ByteArrayTag) nbt);
            case Tag.TAG_INT_ARRAY -> intArrayToJson((IntArrayTag) nbt);
            case Tag.TAG_LONG_ARRAY -> longArrayToJson((LongArrayTag) nbt);
            default -> new JsonPrimitive(nbt.getAsString());
        };
    }

    private static JsonElement listTagToJson(ListTag list) {
        JsonArray arr = new JsonArray();
        for (Tag element : list) {
            arr.add(tagToJson(element));
        }
        return arr;
    }

    private static JsonObject wrapTyped(String type, Number value) {
        JsonObject obj = new JsonObject();
        obj.addProperty("_nbtType", type);
        obj.addProperty("_value", value);
        return obj;
    }

    private static JsonElement byteArrayToJson(ByteArrayTag tag) {
        JsonObject obj = new JsonObject();
        obj.addProperty("_nbtType", "byteArray");
        JsonArray arr = new JsonArray();
        for (byte b : tag.getAsByteArray()) arr.add(b);
        obj.add("_value", arr);
        return obj;
    }

    private static JsonElement intArrayToJson(IntArrayTag tag) {
        JsonObject obj = new JsonObject();
        obj.addProperty("_nbtType", "intArray");
        JsonArray arr = new JsonArray();
        for (int i : tag.getAsIntArray()) arr.add(i);
        obj.add("_value", arr);
        return obj;
    }

    private static JsonElement longArrayToJson(LongArrayTag tag) {
        JsonObject obj = new JsonObject();
        obj.addProperty("_nbtType", "longArray");
        JsonArray arr = new JsonArray();
        for (long l : tag.getAsLongArray()) arr.add(l);
        obj.add("_value", arr);
        return obj;
    }

    // ======================== JSON → NBT ========================

    public static CompoundTag fromJson(JsonObject obj) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            tag.put(entry.getKey(), jsonToTag(entry.getValue()));
        }
        return tag;
    }

    private static Tag jsonToTag(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("_nbtType") && obj.has("_value")) {
                return typedWrapperToTag(obj);
            }
            return fromJson(obj);
        }
        if (element.isJsonArray()) {
            return jsonArrayToListTag(element.getAsJsonArray());
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isString()) return StringTag.valueOf(prim.getAsString());
            if (prim.isNumber()) return IntTag.valueOf(prim.getAsInt());
            if (prim.isBoolean()) return ByteTag.valueOf(prim.getAsBoolean() ? (byte) 1 : (byte) 0);
        }
        return StringTag.valueOf(element.toString());
    }

    private static Tag typedWrapperToTag(JsonObject obj) {
        String type = obj.get("_nbtType").getAsString();
        JsonElement value = obj.get("_value");
        return switch (type) {
            case "byte" -> ByteTag.valueOf(value.getAsByte());
            case "short" -> ShortTag.valueOf(value.getAsShort());
            case "int" -> IntTag.valueOf(value.getAsInt());
            case "long" -> LongTag.valueOf(value.getAsLong());
            case "float" -> FloatTag.valueOf(value.getAsFloat());
            case "double" -> DoubleTag.valueOf(value.getAsDouble());
            case "byteArray" -> byteArrayFromJson(value.getAsJsonArray());
            case "intArray" -> intArrayFromJson(value.getAsJsonArray());
            case "longArray" -> longArrayFromJson(value.getAsJsonArray());
            default -> StringTag.valueOf(value.getAsString());
        };
    }

    private static ListTag jsonArrayToListTag(JsonArray arr) {
        ListTag list = new ListTag();
        for (JsonElement el : arr) list.add(jsonToTag(el));
        return list;
    }

    private static ByteArrayTag byteArrayFromJson(JsonArray arr) {
        byte[] bytes = new byte[arr.size()];
        for (int i = 0; i < arr.size(); i++) bytes[i] = arr.get(i).getAsByte();
        return new ByteArrayTag(bytes);
    }

    private static IntArrayTag intArrayFromJson(JsonArray arr) {
        int[] ints = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) ints[i] = arr.get(i).getAsInt();
        return new IntArrayTag(ints);
    }

    private static LongArrayTag longArrayFromJson(JsonArray arr) {
        long[] longs = new long[arr.size()];
        for (int i = 0; i < arr.size(); i++) longs[i] = arr.get(i).getAsLong();
        return new LongArrayTag(longs);
    }
}

