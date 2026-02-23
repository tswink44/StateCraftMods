package com.statecraft.economy.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Trading Hub settings.
 *
 * Item sell values are now loaded from a separate JSON file:
 *   config/statecraft-item-values.json
 * See {@link ItemValueRegistry} for the JSON-based item value system.
 *
 * This TOML config retains trading hub operational settings (tax rate, enabled, max items).
 * The legacy ITEM_VALUES list in the TOML is kept as a fallback but the JSON file takes priority.
 */
public class ItemValueConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Item values configuration (legacy TOML fallback)
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_VALUES;

    // Trading Hub settings
    public static final ForgeConfigSpec.BooleanValue TRADING_HUB_ENABLED;
    public static final ForgeConfigSpec.DoubleValue SELL_TAX_RATE;
    public static final ForgeConfigSpec.IntValue MAX_ITEMS_PER_TRANSACTION;

    // Cached parsed values (legacy TOML fallback)
    private static Map<String, Double> itemValueCache = null;

    static {
        BUILDER.comment("Trading Hub Configuration").push("trading_hub");

        BUILDER.comment("Enable Trading Hub functionality");
        TRADING_HUB_ENABLED = BUILDER.define("enabled", true);

        BUILDER.comment("Base tax rate on sales (0.0 = no tax, 0.1 = 10% tax)",
                       "Note: Nations can add additional taxes via legislation");
        SELL_TAX_RATE = BUILDER.defineInRange("sellTaxRate", 0.0, 0.0, 0.5);

        BUILDER.comment("Maximum items that can be sold in a single transaction");
        MAX_ITEMS_PER_TRANSACTION = BUILDER.defineInRange("maxItemsPerTransaction", 64, 1, 576);

        BUILDER.comment("Legacy item values (TOML fallback).",
                       "Item values are now primarily loaded from: config/statecraft-item-values.json",
                       "Edit the JSON file for easier configuration.",
                       "This list is only used if the JSON file is missing or an item is not in it.");
        ITEM_VALUES = BUILDER.defineList("itemValues",
            Arrays.asList(
                // Ores and Raw Materials
                "minecraft:coal=2",
                "minecraft:raw_iron=5",
                "minecraft:raw_gold=15",
                "minecraft:raw_copper=3",
                "minecraft:diamond=100",
                "minecraft:emerald=75",
                "minecraft:lapis_lazuli=5",
                "minecraft:redstone=3",
                "minecraft:quartz=4",
                "minecraft:ancient_debris=500",
                "minecraft:netherite_scrap=600",

                // Ingots
                "minecraft:iron_ingot=8",
                "minecraft:gold_ingot=25",
                "minecraft:copper_ingot=5",
                "minecraft:netherite_ingot=2500",

                // Gems and Valuables
                "minecraft:amethyst_shard=10",
                "minecraft:prismarine_shard=8",
                "minecraft:prismarine_crystals=12",
                "minecraft:nether_star=5000",
                "minecraft:heart_of_the_sea=1000",
                "minecraft:totem_of_undying=2000",

                // Food (basic)
                "minecraft:wheat=1",
                "minecraft:carrot=1",
                "minecraft:potato=1",
                "minecraft:beetroot=1",
                "minecraft:melon_slice=1",
                "minecraft:apple=2",
                "minecraft:golden_apple=200",
                "minecraft:enchanted_golden_apple=3000",
                "minecraft:bread=3",
                "minecraft:cooked_beef=4",
                "minecraft:cooked_porkchop=4",
                "minecraft:cooked_chicken=3",
                "minecraft:cooked_mutton=3",
                "minecraft:cooked_salmon=4",
                "minecraft:cooked_cod=3",

                // Wood
                "minecraft:oak_log=1",
                "minecraft:spruce_log=1",
                "minecraft:birch_log=1",
                "minecraft:jungle_log=1",
                "minecraft:acacia_log=1",
                "minecraft:dark_oak_log=1",
                "minecraft:mangrove_log=1",
                "minecraft:cherry_log=2",
                "minecraft:crimson_stem=3",
                "minecraft:warped_stem=3",

                // Building Materials
                "minecraft:cobblestone=0.1",
                "minecraft:stone=0.2",
                "minecraft:deepslate=0.3",
                "minecraft:granite=0.15",
                "minecraft:diorite=0.15",
                "minecraft:andesite=0.15",
                "minecraft:sand=0.2",
                "minecraft:gravel=0.1",
                "minecraft:clay_ball=1",
                "minecraft:brick=2",
                "minecraft:obsidian=10",
                "minecraft:crying_obsidian=25",

                // Mob Drops
                "minecraft:leather=3",
                "minecraft:feather=1",
                "minecraft:string=2",
                "minecraft:slime_ball=5",
                "minecraft:ender_pearl=20",
                "minecraft:blaze_rod=15",
                "minecraft:ghast_tear=50",
                "minecraft:phantom_membrane=30",
                "minecraft:shulker_shell=100",
                "minecraft:rabbit_foot=25",
                "minecraft:wither_skeleton_skull=200",
                "minecraft:dragon_breath=75",
                "minecraft:echo_shard=150",

                // Dyes
                "minecraft:white_dye=2",
                "minecraft:black_dye=2",
                "minecraft:red_dye=2",
                "minecraft:blue_dye=2",
                "minecraft:green_dye=2",
                "minecraft:yellow_dye=2",
                "minecraft:purple_dye=3",
                "minecraft:cyan_dye=3",
                "minecraft:pink_dye=3",
                "minecraft:lime_dye=3",
                "minecraft:orange_dye=3",
                "minecraft:magenta_dye=3",
                "minecraft:light_blue_dye=3",
                "minecraft:brown_dye=3",
                "minecraft:gray_dye=3",
                "minecraft:light_gray_dye=3"
            ),
            obj -> obj instanceof String && ((String) obj).contains("=")
        );

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /**
     * Get the sell value of an item.
     * Checks the JSON registry first, then falls back to the TOML config.
     * @param itemStack The item to get the value of
     * @return The sell value, or 0 if the item has no value
     */
    public static double getItemValue(ItemStack itemStack) {
        if (itemStack.isEmpty()) return 0;

        // Primary: JSON registry
        double jsonValue = ItemValueRegistry.getInstance().getItemValue(itemStack);
        if (jsonValue > 0) return jsonValue;

        // Fallback: legacy TOML config
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
        if (itemId == null) return 0;

        String key = itemId.toString();
        return getItemValueCache().getOrDefault(key, 0.0);
    }

    /**
     * Get the total sell value of a stack
     * @param itemStack The item stack
     * @return The total value (per item value * count)
     */
    public static double getStackValue(ItemStack itemStack) {
        return getItemValue(itemStack) * itemStack.getCount();
    }

    /**
     * Check if an item can be sold
     * @param itemStack The item to check
     * @return true if the item has a sell value > 0
     */
    public static boolean canSell(ItemStack itemStack) {
        return getItemValue(itemStack) > 0;
    }

    /**
     * Get the cached item values from TOML (legacy fallback), rebuilding if necessary
     */
    private static Map<String, Double> getItemValueCache() {
        if (itemValueCache == null) {
            rebuildCache();
        }
        return itemValueCache;
    }

    /**
     * Rebuild the item value cache from TOML config
     */
    public static void rebuildCache() {
        itemValueCache = new HashMap<>();

        List<? extends String> values = ITEM_VALUES.get();
        for (String entry : values) {
            try {
                String[] parts = entry.split("=");
                if (parts.length == 2) {
                    String itemId = parts[0].trim();
                    double value = Double.parseDouble(parts[1].trim());
                    itemValueCache.put(itemId, value);
                }
            } catch (NumberFormatException e) {
                // Skip invalid entries
            }
        }
    }

    /**
     * Clear the cache (call when config is reloaded)
     */
    public static void clearCache() {
        itemValueCache = null;
    }
}

