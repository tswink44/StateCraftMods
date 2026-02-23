package com.statecraft.economy.config;

import com.google.gson.*;
import com.statecraft.economy.StateCraftEconomy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JSON-based item value registry for Trading Hub sell prices.
 *
 * Loads from: config/statecraft-item-values.json
 *
 * The JSON file is organized by category for easy editing:
 * {
 *   "_comment": "Item sell values for Trading Hubs. Format: \"modid:item\": price",
 *   "ores_and_raw_materials": {
 *     "minecraft:coal": 2.0,
 *     "minecraft:diamond": 100.0
 *   },
 *   "ingots": { ... },
 *   ...
 * }
 *
 * Items not listed have no sell value (cannot be sold).
 * Reload with /eco reloaditems or by restarting the server.
 */
public class ItemValueRegistry {

    private static final ItemValueRegistry INSTANCE = new ItemValueRegistry();

    private final Map<String, Double> itemValues = new ConcurrentHashMap<>();
    private Path configPath;
    private boolean loaded = false;

    private ItemValueRegistry() {}

    public static ItemValueRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize and load the registry.
     * Call during server startup.
     */
    public void init() {
        configPath = FMLPaths.CONFIGDIR.get().resolve("statecraft-item-values.json");
        StateCraftEconomy.LOGGER.info("[ItemValueRegistry] Config path: {}", configPath.toAbsolutePath());

        try {
            boolean exists = Files.exists(configPath);
            long fileSize = exists ? Files.size(configPath) : 0;
            StateCraftEconomy.LOGGER.info("[ItemValueRegistry] File exists: {}, size: {} bytes", exists, fileSize);

            if (exists && fileSize > 100) {
                load();
            } else {
                generateDefaults();
                save();
                StateCraftEconomy.LOGGER.info("Generated default item values at {}", configPath);
            }
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("[ItemValueRegistry] Error during init", e);
            generateDefaults();
        }
    }

    /**
     * Reload from disk. Can be called at runtime via command.
     */
    public void reload() {
        itemValues.clear();
        loaded = false;
        if (Files.exists(configPath)) {
            load();
            StateCraftEconomy.LOGGER.info("Reloaded {} item values from JSON", itemValues.size());
        } else {
            generateDefaults();
            save();
            StateCraftEconomy.LOGGER.info("Regenerated default item values ({} items)", itemValues.size());
        }
        // Also clear the legacy TOML cache
        ItemValueConfig.clearCache();
    }

    /**
     * Receive synced item values from the server (client-side only).
     * Called when the server sends the item value registry to the client on login.
     */
    public void receiveSyncedValues(Map<String, Double> values) {
        itemValues.clear();
        itemValues.putAll(values);
        loaded = true;
        StateCraftEconomy.LOGGER.info("[ItemValueRegistry] Received {} item values from server", values.size());
    }

    /**
     * Get the sell value of a single item.
     */
    public double getItemValue(ItemStack itemStack) {
        if (itemStack.isEmpty()) return 0;
        if (!loaded) init();

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemId == null) return 0;

        String key = itemId.toString();
        double value = itemValues.getOrDefault(key, 0.0);
        if (value == 0.0 && !itemStack.isEmpty()) {
            // Log misses at debug level to help debug without spamming
            StateCraftEconomy.LOGGER.debug("[ItemValueRegistry] No value found for '{}' (total loaded: {})", key, itemValues.size());
        }
        return value;
    }

    /**
     * Get the total sell value of a stack (per-item value × count).
     */
    public double getStackValue(ItemStack itemStack) {
        return getItemValue(itemStack) * itemStack.getCount();
    }

    /**
     * Check if an item can be sold (has a value > 0).
     */
    public boolean canSell(ItemStack itemStack) {
        return getItemValue(itemStack) > 0;
    }

    /**
     * Get all registered item values (for display/debugging).
     */
    public Map<String, Double> getAllValues() {
        if (!loaded) init();
        return Collections.unmodifiableMap(itemValues);
    }

    /**
     * Set an item's value at runtime. Persists on next save.
     */
    public void setItemValue(String itemId, double value) {
        if (value <= 0) {
            itemValues.remove(itemId);
        } else {
            itemValues.put(itemId, value);
        }
    }

    /**
     * Save current values to disk.
     */
    public void save() {
        if (configPath == null) return;

        try {
            // Group items by category for readability
            JsonObject root = new JsonObject();
            root.addProperty("_comment",
                "Item sell values for StateCraft Trading Hubs. " +
                "Format: \"modid:item\": price. Items not listed cannot be sold. " +
                "Reload in-game with: /eco reloaditems");

            // Sort items into categories
            Map<String, Map<String, Double>> categories = buildCategorizedMap();

            for (Map.Entry<String, Map<String, Double>> category : categories.entrySet()) {
                JsonObject categoryObj = new JsonObject();
                // Sort items within each category alphabetically
                new TreeMap<>(category.getValue()).forEach(categoryObj::addProperty);
                root.add(category.getKey(), categoryObj);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(configPath, gson.toJson(root));
        } catch (IOException e) {
            StateCraftEconomy.LOGGER.error("Failed to save item values JSON", e);
        }
    }

    // ==================== Loading ====================

    private void load() {
        try {
            String json = Files.readString(configPath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            itemValues.clear();

            for (Map.Entry<String, JsonElement> category : root.entrySet()) {
                String key = category.getKey();
                if (key.startsWith("_")) continue; // Skip comments

                JsonElement value = category.getValue();
                if (value.isJsonObject()) {
                    // Category object: { "minecraft:coal": 2.0, ... }
                    for (Map.Entry<String, JsonElement> item : value.getAsJsonObject().entrySet()) {
                        try {
                            double price = item.getValue().getAsDouble();
                            if (price > 0) {
                                itemValues.put(item.getKey(), price);
                            }
                        } catch (Exception e) {
                            StateCraftEconomy.LOGGER.warn("Invalid item value entry: {}={}", item.getKey(), item.getValue());
                        }
                    }
                } else if (value.isJsonPrimitive()) {
                    // Flat format: "minecraft:coal": 2.0 (top level)
                    try {
                        double price = value.getAsDouble();
                        if (price > 0) {
                            itemValues.put(key, price);
                        }
                    } catch (Exception ignored) {}
                }
            }

            loaded = true;
            StateCraftEconomy.LOGGER.info("Loaded {} item values from {}", itemValues.size(), configPath.getFileName());
            // Debug: verify sample items
            StateCraftEconomy.LOGGER.info("[ItemValueRegistry] Sample check - society:double_aged_dewy_star = {}",
                itemValues.getOrDefault("society:double_aged_dewy_star", -1.0));
            StateCraftEconomy.LOGGER.info("[ItemValueRegistry] Sample check - minecraft:diamond = {}",
                itemValues.getOrDefault("minecraft:diamond", -1.0));
            StateCraftEconomy.LOGGER.info("[ItemValueRegistry] File size: {} bytes", configPath.toFile().length());
        } catch (Exception e) {
            StateCraftEconomy.LOGGER.error("Failed to load item values from JSON, using defaults", e);
            generateDefaults();
            loaded = true;
        }
    }

    // ==================== Categorization ====================

    private Map<String, Map<String, Double>> buildCategorizedMap() {
        Map<String, Map<String, Double>> categories = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : itemValues.entrySet()) {
            String category = categorizeItem(entry.getKey());
            categories.computeIfAbsent(category, k -> new LinkedHashMap<>())
                .put(entry.getKey(), entry.getValue());
        }

        return categories;
    }

    private String categorizeItem(String itemId) {
        if (itemId.contains("raw_") || itemId.contains("_ore") || itemId.contains("coal")
            || itemId.contains("diamond") || itemId.contains("emerald") || itemId.contains("lapis")
            || itemId.contains("redstone") || itemId.contains("quartz") || itemId.contains("debris")
            || itemId.contains("netherite_scrap") || itemId.contains("copper")) {
            if (itemId.contains("ingot")) return "ingots";
            return "ores_and_raw_materials";
        }
        if (itemId.contains("ingot")) return "ingots";
        if (itemId.contains("_shard") || itemId.contains("nether_star") || itemId.contains("heart_of")
            || itemId.contains("totem") || itemId.contains("prismarine") || itemId.contains("amethyst")
            || itemId.contains("echo_shard")) return "gems_and_valuables";
        if (itemId.contains("cooked_") || itemId.contains("bread") || itemId.contains("wheat")
            || itemId.contains("carrot") || itemId.contains("potato") || itemId.contains("beetroot")
            || itemId.contains("melon") || itemId.contains("apple") || itemId.contains("golden_apple")
            || itemId.contains("enchanted_golden")) return "food";
        if (itemId.contains("_log") || itemId.contains("_stem") || itemId.contains("_wood")
            || itemId.contains("_plank")) return "wood";
        if (itemId.contains("cobblestone") || itemId.contains("stone") || itemId.contains("deepslate")
            || itemId.contains("granite") || itemId.contains("diorite") || itemId.contains("andesite")
            || itemId.contains("sand") || itemId.contains("gravel") || itemId.contains("clay")
            || itemId.contains("brick") || itemId.contains("obsidian")) return "building_materials";
        if (itemId.contains("leather") || itemId.contains("feather") || itemId.contains("string")
            || itemId.contains("slime") || itemId.contains("ender_pearl") || itemId.contains("blaze")
            || itemId.contains("ghast") || itemId.contains("phantom") || itemId.contains("shulker")
            || itemId.contains("rabbit") || itemId.contains("skull") || itemId.contains("dragon_breath")
            || itemId.contains("gunpowder") || itemId.contains("bone") || itemId.contains("spider_eye")
            || itemId.contains("rotten_flesh") || itemId.contains("ink_sac")) return "mob_drops";
        if (itemId.contains("_dye")) return "dyes";
        if (itemId.contains("_helmet") || itemId.contains("_chestplate") || itemId.contains("_leggings")
            || itemId.contains("_boots")) return "armor";
        if (itemId.contains("_sword") || itemId.contains("_pickaxe") || itemId.contains("_axe")
            || itemId.contains("_shovel") || itemId.contains("_hoe") || itemId.contains("bow")
            || itemId.contains("crossbow") || itemId.contains("trident") || itemId.contains("shield")) return "tools_and_weapons";
        return "miscellaneous";
    }

    // ==================== Defaults ====================

    private void generateDefaults() {
        itemValues.clear();

        // Ores and Raw Materials
        put("minecraft:coal", 2);
        put("minecraft:raw_iron", 5);
        put("minecraft:raw_gold", 15);
        put("minecraft:raw_copper", 3);
        put("minecraft:diamond", 100);
        put("minecraft:emerald", 75);
        put("minecraft:lapis_lazuli", 5);
        put("minecraft:redstone", 3);
        put("minecraft:quartz", 4);
        put("minecraft:ancient_debris", 500);
        put("minecraft:netherite_scrap", 600);

        // Ingots
        put("minecraft:iron_ingot", 8);
        put("minecraft:gold_ingot", 25);
        put("minecraft:copper_ingot", 5);
        put("minecraft:netherite_ingot", 2500);

        // Gems and Valuables
        put("minecraft:amethyst_shard", 10);
        put("minecraft:prismarine_shard", 8);
        put("minecraft:prismarine_crystals", 12);
        put("minecraft:nether_star", 5000);
        put("minecraft:heart_of_the_sea", 1000);
        put("minecraft:totem_of_undying", 2000);
        put("minecraft:echo_shard", 150);

        // Food
        put("minecraft:wheat", 1);
        put("minecraft:carrot", 1);
        put("minecraft:potato", 1);
        put("minecraft:beetroot", 1);
        put("minecraft:melon_slice", 1);
        put("minecraft:apple", 2);
        put("minecraft:golden_apple", 200);
        put("minecraft:enchanted_golden_apple", 3000);
        put("minecraft:bread", 3);
        put("minecraft:cooked_beef", 4);
        put("minecraft:cooked_porkchop", 4);
        put("minecraft:cooked_chicken", 3);
        put("minecraft:cooked_mutton", 3);
        put("minecraft:cooked_salmon", 4);
        put("minecraft:cooked_cod", 3);

        // Wood
        put("minecraft:oak_log", 1);
        put("minecraft:spruce_log", 1);
        put("minecraft:birch_log", 1);
        put("minecraft:jungle_log", 1);
        put("minecraft:acacia_log", 1);
        put("minecraft:dark_oak_log", 1);
        put("minecraft:mangrove_log", 1);
        put("minecraft:cherry_log", 2);
        put("minecraft:crimson_stem", 3);
        put("minecraft:warped_stem", 3);

        // Building Materials
        put("minecraft:cobblestone", 0.1);
        put("minecraft:stone", 0.2);
        put("minecraft:deepslate", 0.3);
        put("minecraft:granite", 0.15);
        put("minecraft:diorite", 0.15);
        put("minecraft:andesite", 0.15);
        put("minecraft:sand", 0.2);
        put("minecraft:gravel", 0.1);
        put("minecraft:clay_ball", 1);
        put("minecraft:brick", 2);
        put("minecraft:obsidian", 10);
        put("minecraft:crying_obsidian", 25);

        // Mob Drops
        put("minecraft:leather", 3);
        put("minecraft:feather", 1);
        put("minecraft:string", 2);
        put("minecraft:slime_ball", 5);
        put("minecraft:ender_pearl", 20);
        put("minecraft:blaze_rod", 15);
        put("minecraft:ghast_tear", 50);
        put("minecraft:phantom_membrane", 30);
        put("minecraft:shulker_shell", 100);
        put("minecraft:rabbit_foot", 25);
        put("minecraft:wither_skeleton_skull", 200);
        put("minecraft:dragon_breath", 75);
        put("minecraft:gunpowder", 3);
        put("minecraft:bone", 2);
        put("minecraft:spider_eye", 3);
        put("minecraft:rotten_flesh", 0.5);
        put("minecraft:ink_sac", 2);
        put("minecraft:glow_ink_sac", 5);

        // Dyes
        put("minecraft:white_dye", 2);
        put("minecraft:black_dye", 2);
        put("minecraft:red_dye", 2);
        put("minecraft:blue_dye", 2);
        put("minecraft:green_dye", 2);
        put("minecraft:yellow_dye", 2);
        put("minecraft:purple_dye", 3);
        put("minecraft:cyan_dye", 3);
        put("minecraft:pink_dye", 3);
        put("minecraft:lime_dye", 3);
        put("minecraft:orange_dye", 3);
        put("minecraft:magenta_dye", 3);
        put("minecraft:light_blue_dye", 3);
        put("minecraft:brown_dye", 3);
        put("minecraft:gray_dye", 3);
        put("minecraft:light_gray_dye", 3);

        // Tools and Weapons (crafted value)
        put("minecraft:iron_sword", 20);
        put("minecraft:iron_pickaxe", 28);
        put("minecraft:iron_axe", 28);
        put("minecraft:iron_shovel", 12);
        put("minecraft:diamond_sword", 210);
        put("minecraft:diamond_pickaxe", 310);
        put("minecraft:diamond_axe", 310);
        put("minecraft:diamond_shovel", 110);
        put("minecraft:bow", 8);
        put("minecraft:crossbow", 15);
        put("minecraft:shield", 10);
        put("minecraft:trident", 500);

        // Armor (crafted value)
        put("minecraft:iron_helmet", 40);
        put("minecraft:iron_chestplate", 64);
        put("minecraft:iron_leggings", 56);
        put("minecraft:iron_boots", 32);
        put("minecraft:diamond_helmet", 500);
        put("minecraft:diamond_chestplate", 800);
        put("minecraft:diamond_leggings", 700);
        put("minecraft:diamond_boots", 400);

        // Miscellaneous
        put("minecraft:glass", 0.5);
        put("minecraft:glass_bottle", 1);
        put("minecraft:book", 5);
        put("minecraft:name_tag", 50);
        put("minecraft:saddle", 30);
        put("minecraft:elytra", 10000);
        put("minecraft:enchanted_book", 100);
        put("minecraft:experience_bottle", 15);
        put("minecraft:honey_bottle", 8);
        put("minecraft:honeycomb", 5);
        put("minecraft:nautilus_shell", 50);
        put("minecraft:turtle_egg", 25);
        put("minecraft:sponge", 20);
        put("minecraft:sea_lantern", 15);
        put("minecraft:glowstone", 10);
        put("minecraft:end_crystal", 500);

        loaded = true;
    }

    private void put(String itemId, double value) {
        itemValues.put(itemId, value);
    }
}

