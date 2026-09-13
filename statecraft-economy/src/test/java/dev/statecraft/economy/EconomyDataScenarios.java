package dev.statecraft.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.statecraft.api.UserError;
import dev.statecraft.economy.data.EconomyFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.statecraft.economy.EconomyRegressionScenarios.*;
import static dev.statecraft.economy.TestWorld.*;

public final class EconomyDataScenarios {
    private EconomyDataScenarios() {}

    public static void allBundledTradeDefinitionsAndPricesLoad() throws IOException {
        try (Fixture fixture = new Fixture()) {
            EconomyFiles.Loaded loaded = fixture.files.load();
            eq(22, loaded.trades().size());
            eq(13, loaded.trades().stream().filter(t -> t.profession() != null).count());
            eq(9, loaded.trades().stream().filter(t -> t.merchant() != null).count());
            eq(25, loaded.values().sellPrice("minecraft:wheat"));
            eq(7, loaded.values().currency().size());
            check(loaded.trades().stream().filter(t -> t.merchant() != null).map(EconomyFiles.Trades::merchant).toList()
                    .containsAll(List.of("banker", "bard", "barkeeper", "botanist", "market", "baker", "winemaker", "storage_smith", "ribbit")),
                    "custom merchant is missing");
        }
    }

    public static void missingDefaultsNeverOverwriteOperatorEdits() throws IOException {
        try (Fixture fixture = new Fixture()) {
            String original = Files.readString(fixture.values());
            Files.writeString(fixture.values(), original.replace("\"minecraft:wheat\": 25", "\"minecraft:wheat\": 75"));
            fixture.files.copyMissingDefaults();
            eq(75, fixture.files.load().values().sellPrice("minecraft:wheat"));
        }
    }

    public static void malformedJsonIsRejectedWithoutChangingLiveData() throws IOException {
        try (Fixture fixture = new Fixture()) {
            TestWorld w = new TestWorld();
            EconomyFiles.Loaded valid = fixture.files.load();
            w.engine.reconfigure(w.config, valid.values());
            Files.writeString(fixture.values(), "{\"prices\":{\"minecraft:wheat\":1.5},\"currencyItems\":{\"statecraft_economy:currency_1\":100}}");
            expect(UserError.class, () -> loadUnchecked(fixture.files));
            eq(25, w.engine.values().sellPrice("minecraft:wheat"));
            Files.writeString(fixture.values(), "{\"prices\":{\"minecraft:wheat\":25,\"minecraft:wheat\":99},\"currencyItems\":{\"statecraft_economy:currency_1\":100}}");
            expect(IllegalStateException.class, () -> loadUnchecked(fixture.files));
            eq(25, w.engine.values().sellPrice("minecraft:wheat"));
            Files.writeString(fixture.values(), "{\"prices\":{\"missingmod:item\":25},\"currencyItems\":{\"statecraft_economy:currency_1\":100}}");
            expect(UserError.class, () -> loadUnchecked(fixture.files));
        }
    }

    public static void malformedTradeAndPathTraversalRejectWholeReload() throws IOException {
        try (Fixture fixture = new Fixture()) {
            EconomyFiles.Loaded original = fixture.files.load();
            Path farmer = fixture.root.resolve("trades").resolve("farmer.json");
            String valid = Files.readString(farmer);
            Files.writeString(farmer, "{\"profession\":\"minecraft:farmer\",\"merchant\":null,\"trades\":[{\"level\":6,\"buy\":{\"item\":\"minecraft:wheat\",\"count\":1},\"sell\":{\"item\":\"statecraft_economy:currency_1\",\"count\":1}}]}");
            expect(UserError.class, () -> loadUnchecked(fixture.files));
            Files.writeString(farmer, valid.replaceFirst("\"count\":\\s*[0-9]+", "\"count\": 0"));
            expect(UserError.class, () -> loadUnchecked(fixture.files));
            Files.writeString(farmer, valid);
            eq(original.revision(), fixture.files.load().revision());
            Files.writeString(fixture.root.resolve("trades").resolve("index.json"), "{\"files\":[\"../outside.json\"]}");
            expect(UserError.class, () -> loadUnchecked(fixture.files));
        }
    }

    public static void changedTradesProduceNewLiveOfferRevision() throws IOException {
        try (Fixture fixture = new Fixture()) {
            String before = fixture.files.load().revision();
            Path farmer = fixture.root.resolve("trades").resolve("farmer.json");
            String original = Files.readString(farmer);
            Files.writeString(farmer, original.replaceFirst("\"xp\":\\s*[0-9]+", "\"xp\": 17"));
            EconomyFiles.Loaded changed = fixture.files.load();
            check(!before.equals(changed.revision()), "reload retained the original trade definitions");
            check(changed.trades().stream().filter(t -> t.profession() != null && t.profession().equals("minecraft:farmer"))
                    .flatMap(t -> t.offers().stream()).anyMatch(offer -> offer.xp() == 17), "new trade attributes were ignored");
        }
    }

    public static void priceEditsAreValidatedAndPersistedAtomically() throws IOException {
        try (Fixture fixture = new Fixture()) {
            EconomyFiles.PriceUpdate update = fixture.files.planPrice("minecraft:wheat", 77L);
            eq(25, fixture.files.load().values().sellPrice("minecraft:wheat"));
            fixture.files.writePrices(update);
            eq(77, fixture.files.load().values().sellPrice("minecraft:wheat"));
            expect(UserError.class, () -> {
                try { fixture.files.planPrice(ONE, 1_000_000L); }
                catch (IOException failure) { throw new IllegalStateException(failure); }
            });
            eq(77, fixture.files.load().values().sellPrice("minecraft:wheat"));
            EconomyFiles.PriceUpdate removal = fixture.files.planPrice("minecraft:wheat", null);
            fixture.files.writePrices(removal);
            check(!fixture.files.load().values().prices().containsKey("minecraft:wheat"), "price removal did not persist");
            try (var files = Files.list(fixture.root)) {
                check(files.noneMatch(path -> path.getFileName().toString().startsWith("item_values.new-")), "staged config file was not cleaned");
            }
        }
    }

    public static void financialSnapshotRoundTripsWithoutLosingEscrow() {
        TestWorld w = new TestWorld();
        String bank = w.bank(10_000, 0, 100);
        w.set(OWNER, 1000);
        w.engine.banking.deposit(OWNER, bank, 500);
        String loan = w.loan(BUYER, bank, 2000, BUYER_CLAIM, false, true, false);
        String tag = "{display:{Name:'{\"text\":\"Persistent item\"}'},mod_data:{nested:[1,2,3]}}";
        Inventory inventory = new Inventory().item(0, new ItemLot("minecraft:diamond", tag, 4, 64));
        String market = w.engine.commerce.listMarket(OWNER, 4, 25, inventory);
        String shares = w.engine.commerce.listStock(OWNER, "co", 10, 5);
        w.engine.property.list(OWNER, CLAIM, 5000);
        String contractEscrow = "escrow:contract:" + new UUID(0, 222);
        w.engine.transferBatch(List.of(new dev.statecraft.api.EconomyAccess.Transfer(OWNER.account(), contractEscrow,
                100, "Persistent core contract escrow")));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(w.data);
        EconomyData loaded = gson.fromJson(json, EconomyData.class);
        EconomyEngine restored = new EconomyEngine(loaded, w.governance, w.config, w.values, w.clock, () -> {}, ValuationEnvironment.NEUTRAL, id -> 64);
        w.governance.economy = restored;
        eq(w.engine.balance("bank:co"), restored.balance("bank:co"));
        eq(100, restored.balance(contractEscrow));
        eq(500, restored.banking.balance(OWNER, bank, null).balance());
        eq(tag, loaded.market.get(market).item.snbt());
        eq(4, loaded.market.get(market).remaining); eq(10, loaded.stocks.get(shares).remaining);
        eq(2000, loaded.loans.get(loan).principal);
        check(restored.isClaimEncumbered(CLAIM) && restored.isClaimEncumbered(BUYER_CLAIM), "snapshot lost integration locks");
        restored.commerce.buyMarket(BUYER, market, 1);
        eq(tag, loaded.deliveries.get(BUYER.id().toString()).get(0).item.snbt());
        eq(3, loaded.market.get(market).remaining);
    }

    public static void corruptFinancialSnapshotsFailClosed() {
        TestWorld w = new TestWorld();
        w.set(OWNER, 100);
        Gson gson = new Gson();
        final EconomyData negativeBalanceSnapshot = gson.fromJson(gson.toJson(w.data), EconomyData.class);
        negativeBalanceSnapshot.accounts.get(OWNER.account()).balance = -1;
        expect(UserError.class, () -> new EconomyEngine(negativeBalanceSnapshot, w.governance, w.config, w.values, w.clock,
                () -> {}, ValuationEnvironment.NEUTRAL, id -> 64));
        eq(100, w.engine.balance(OWNER.account()));
        final EconomyData wrongSchema = gson.fromJson(gson.toJson(w.data), EconomyData.class);
        wrongSchema.schemaVersion = 99;
        expect(IllegalStateException.class, () -> new EconomyEngine(wrongSchema, w.governance, w.config, w.values, w.clock,
                () -> {}, ValuationEnvironment.NEUTRAL, id -> 64));
    }

    public static void stackMetadataFormatIsExplicitAndBackwardCompatible() {
        Gson gson = new Gson();
        String metadata = "{tag:{custom:1},ForgeCaps:{\"example:charge\":{energy:1234}}}";
        ItemLot complete = new ItemLot("minecraft:diamond", metadata, 1, 64, true);
        ItemLot restored = gson.fromJson(gson.toJson(complete), ItemLot.class);
        check(restored.fullStackData(), "complete stack metadata marker was not persisted");
        eq(metadata, restored.snbt());
        var legacyJson = gson.toJsonTree(complete).getAsJsonObject();
        legacyJson.remove("fullStackData");
        ItemLot legacy = gson.fromJson(legacyJson, ItemLot.class);
        check(!legacy.fullStackData(), "a vanilla item tag was mistaken for authoritative root stack data");
        check(!legacy.sameItem(complete), "the vanilla and full-stack formats were conflated");
        eq(metadata, legacy.snbt());
        expect(UserError.class, () -> new ItemLot("minecraft:diamond", "", 1, 64, true));
    }

    private static EconomyFiles.Loaded loadUnchecked(EconomyFiles files) {
        try { return files.load(); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    private static final class Fixture implements AutoCloseable {
        final Path root;
        final EconomyFiles files;
        Fixture() throws IOException {
            root = Path.of("build", "economy-test-fixtures", UUID.randomUUID().toString());
            Set<String> professions = Set.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
                    "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith");
            files = new EconomyFiles(root,
                    item -> item.matches("(minecraft|statecraft_economy):[a-z0-9_]+") && !item.equals("minecraft:air"),
                    profession -> profession.startsWith("minecraft:") && professions.contains(profession.substring(10)),
                    item -> item.endsWith("potion") || item.endsWith("sword") || item.endsWith("pickaxe") ? 1 : 64);
            files.copyMissingDefaults();
        }
        Path values() { return root.resolve("item_values.json"); }
        @Override public void close() throws IOException {
            if (!Files.exists(root)) return;
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int count = 0;
        List<String> failures = new ArrayList<>();
        for (var method : EconomyDataScenarios.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0) {
                try { method.invoke(null); count++; System.out.println("PASS " + method.getName()); }
                catch (java.lang.reflect.InvocationTargetException failure) {
                    failures.add(method.getName());
                    System.err.println("FAIL " + method.getName());
                    failure.getCause().printStackTrace();
                }
            }
        }
        if (!failures.isEmpty()) throw new AssertionError("Failed data scenarios: " + failures);
        System.out.println(count + " economy data/persistence scenarios passed.");
    }
}
