package dev.statecraft.economy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;
import dev.statecraft.economy.ItemValues;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class EconomyFiles {
    public record Stack(String item, int count, String nbt) {}
    public record Offer(int level, Stack buy, Stack secondBuy, Stack sell, int maxUses, int xp, float priceMultiplier) {}
    public record Trades(String source, String profession, String merchant, List<Offer> offers) {}
    public record Loaded(ItemValues values, List<Trades> trades, String revision) {}
    public record PriceUpdate(Loaded loaded, JsonObject json) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String RESOURCE_ROOT = "/data/statecraft_economy/";
    private final Path directory;
    private final Predicate<String> items;
    private final Predicate<String> professions;
    private final ToIntFunction<String> stackSize;

    public EconomyFiles(Path directory, Predicate<String> items, Predicate<String> professions, ToIntFunction<String> stackSize) {
        this.directory = directory;
        this.items = items;
        this.professions = professions;
        this.stackSize = stackSize;
    }

    public Path directory() { return directory; }

    public void copyMissingDefaults() throws IOException {
        Files.createDirectories(directory.resolve("trades"));
        copy("default_item_values.json", directory.resolve("item_values.json"));
        copy("trades/index.json", directory.resolve("trades").resolve("index.json"));
        JsonObject bundled;
        try (InputStream stream = resource("trades/index.json");
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            bundled = StrictJson.read(reader);
        }
        for (String file : index(bundled)) copy("trades/" + file, directory.resolve("trades").resolve(file));
    }

    private static InputStream resource(String name) throws IOException {
        InputStream stream = EconomyFiles.class.getResourceAsStream(RESOURCE_ROOT + name);
        if (stream == null) throw new IOException("Required bundled economy resource is missing: " + name);
        return stream;
    }

    private static void copy(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        try (InputStream stream = resource(resource)) { Files.copy(stream, target); }
    }

    public Loaded load() throws IOException {
        JsonObject prices = read(directory.resolve("item_values.json"));
        ItemValues values = values(prices);
        List<Trades> trades = new ArrayList<>();
        Set<String> merchants = new HashSet<>();
        StringBuilder revision = new StringBuilder();
        for (String file : index(read(directory.resolve("trades").resolve("index.json")))) {
            JsonObject definition = read(directory.resolve("trades").resolve(file));
            try {
                Trades parsed = trades(file, definition);
                if (parsed.merchant() != null && !merchants.add(parsed.merchant())) throw new UserError("Duplicate custom merchant ID.");
                trades.add(parsed);
                revision.append(file).append('\n').append(definition).append('\n');
            } catch (RuntimeException failure) {
                throw new UserError("Rejected trades/" + file + ": " + failure.getMessage());
            }
        }
        return new Loaded(values, List.copyOf(trades), hash(revision.toString()));
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public PriceUpdate planPrice(String item, Long cents) throws IOException {
        Loaded loaded = load();
        Map<String, Long> prices = new LinkedHashMap<>(loaded.values().prices());
        if (cents == null) {
            if (prices.remove(item) == null) throw new UserError("That item has no configured price.");
        } else {
            if (loaded.values().isCurrency(item)) throw new UserError("Currency cannot receive a Trading Hub price.");
            Money.positive(cents);
            prices.put(item, cents);
        }
        ItemValues values = new ItemValues(prices, loaded.values().currency(), items);
        JsonObject document = new JsonObject();
        document.add("prices", GSON.toJsonTree(new java.util.TreeMap<>(values.prices())));
        document.add("currencyItems", GSON.toJsonTree(new java.util.TreeMap<>(values.currency())));
        return new PriceUpdate(new Loaded(values, loaded.trades(), loaded.revision()), document);
    }

    public void writePrices(PriceUpdate update) throws IOException {
        Path target = directory.resolve("item_values.json");
        Path staged = directory.resolve("item_values.new-" + UUID.randomUUID() + ".json");
        byte[] bytes = (GSON.toJson(update.json()) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            try (FileChannel file = FileChannel.open(staged, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private JsonObject read(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > 2_097_152) throw new IOException("Missing or oversized economy configuration: " + file.getFileName());
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return StrictJson.read(reader);
        } catch (RuntimeException | IOException failure) {
            throw new IOException("Rejected economy JSON " + file.getFileName() + ": " + failure.getMessage(), failure);
        }
    }

    private ItemValues values(JsonObject root) {
        keys(root, Set.of("prices", "currencyItems"));
        return new ItemValues(moneyMap(object(root, "prices")), moneyMap(object(root, "currencyItems")), items);
    }

    private static Map<String, Long> moneyMap(JsonObject object) {
        Map<String, Long> values = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> values.put(entry.getKey(), Money.positive(integer(entry.getValue(), entry.getKey()))));
        return values;
    }

    private static List<String> index(JsonObject root) {
        keys(root, Set.of("files"));
        JsonElement element = root.get("files");
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > 256) throw new UserError("Trade index requires a files array (maximum 256).");
        List<String> files = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonElement file : element.getAsJsonArray()) {
            String name = string(file, "filename");
            if (!name.matches("[a-z0-9_-][a-z0-9_.-]*\\.json") || name.contains("..") || !unique.add(name)) {
                throw new UserError("Trade filenames must be unique local JSON basenames, without path traversal.");
            }
            files.add(name);
        }
        return List.copyOf(files);
    }

    private Trades trades(String source, JsonObject root) {
        keys(root, Set.of("profession", "merchant", "trades"));
        String profession = nullable(root.get("profession"));
        String merchant = nullable(root.get("merchant"));
        if ((profession == null) == (merchant == null)) throw new UserError("Specify exactly one profession or merchant.");
        if (profession != null && (!profession.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || !professions.test(profession))) {
            throw new UserError("Unknown villager profession: " + profession);
        }
        if (merchant != null && !merchant.matches("[a-z0-9_-]{1,64}")) throw new UserError("Invalid custom merchant ID.");
        JsonElement array = root.get("trades");
        if (array == null || !array.isJsonArray() || array.getAsJsonArray().size() > 1024) throw new UserError("Expected a trades array (maximum 1024 offers).");
        List<Offer> result = new ArrayList<>();
        for (JsonElement element : array.getAsJsonArray()) {
            if (!element.isJsonObject()) throw new UserError("Every trade must be an object.");
            JsonObject trade = element.getAsJsonObject();
            keys(trade, Set.of("level", "buy", "secondBuy", "sell", "maxUses", "xp", "priceMultiplier"));
            int level = bounded(trade, "level", 1, 1, 5);
            int maxUses = bounded(trade, "maxUses", 16, 1, 100_000);
            int xp = bounded(trade, "xp", 2, 0, 10_000);
            float multiplier = 0.05F;
            if (trade.has("priceMultiplier")) {
                JsonElement rate = trade.get("priceMultiplier");
                if (!rate.isJsonPrimitive() || !rate.getAsJsonPrimitive().isNumber()) throw new UserError("priceMultiplier must be a number.");
                multiplier = rate.getAsFloat();
                if (!Float.isFinite(multiplier) || multiplier < 0 || multiplier > 1) throw new UserError("priceMultiplier must be in [0,1].");
            }
            result.add(new Offer(level, stack(object(trade, "buy")),
                    trade.has("secondBuy") && !trade.get("secondBuy").isJsonNull() ? stack(object(trade, "secondBuy")) : null,
                    stack(object(trade, "sell")), maxUses, xp, multiplier));
        }
        return new Trades(source, profession, merchant, List.copyOf(result));
    }

    private Stack stack(JsonObject object) {
        keys(object, Set.of("item", "count", "nbt"));
        String id = string(object.get("item"), "item");
        if (!items.test(id) || id.equals("minecraft:air")) throw new UserError("Unknown trade item: " + id);
        int count = bounded(object, "count", 1, 1, Math.min(1024, stackSize.applyAsInt(id)));
        String nbt = object.has("nbt") ? string(object.get("nbt"), "nbt") : "";
        if (nbt.length() > 65_536) throw new UserError("Trade NBT exceeds 65536 characters.");
        return new Stack(id, count, nbt);
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement object = root.get(key);
        if (object == null || !object.isJsonObject()) throw new UserError("Expected object: " + key);
        return object.getAsJsonObject();
    }

    private static long integer(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]{1,18}")) throw new UserError(field + " must be a non-negative integer.");
        try { return Long.parseLong(value.getAsString()); }
        catch (NumberFormatException failure) { throw new UserError(field + " is too large."); }
    }

    private static int bounded(JsonObject object, String field, int fallback, int minimum, int maximum) {
        long value = object.has(field) ? integer(object.get(field), field) : fallback;
        if (value < minimum || value > maximum) throw new UserError(field + " must be in [" + minimum + "," + maximum + "].");
        return (int) value;
    }

    private static String nullable(JsonElement value) { return value == null || value.isJsonNull() ? null : string(value, "text"); }
    private static String string(JsonElement value, String name) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new UserError("Expected string: " + name);
        return value.getAsString();
    }
    private static void keys(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key)) throw new UserError("Unknown JSON field: " + key);
    }
}
