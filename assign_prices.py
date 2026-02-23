"""
assign_prices.py — Assign sell prices to all modpack items for StateCraft Trading Hubs.

Rules:
  1. Block variants (stairs, slabs, walls, fences, buttons, etc.) are OMITTED — only base blocks get prices.
  2. Infer prices from existing data when possible (bulk containers, cooked variants, processed forms).
  3. Furniture has sub-tier pricing (seating, storage, appliances, décor, etc.).
"""
import json
import re
import os
from collections import defaultdict

# ═══════════════════════════════════════════════════════════════════════
# 1. LOAD EXISTING DATA
# ═══════════════════════════════════════════════════════════════════════
CONFIG_PATH = "StateCraft/run/config/statecraft-item-values.json"
ITEMS_PATH = "items_clean.txt"
OMITTED_PATH = "items_omitted.txt"

with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    existing_data = json.load(f)

existing_prices = {}
for cat, vals in existing_data.items():
    if cat == "_comment":
        continue
    if isinstance(vals, dict):
        existing_prices.update(vals)

print(f"Existing priced items: {len(existing_prices)}")

with open(ITEMS_PATH, "r", encoding="utf-8") as f:
    all_items = [line.strip() for line in f if line.strip()]

all_items_set = set(all_items)
print(f"Total items in modpack: {len(all_items)}")

# ═══════════════════════════════════════════════════════════════════════
# 2. OMISSION RULES
# ═══════════════════════════════════════════════════════════════════════

OMIT_NAMESPACES = {
    "itemfilters": "Non-physical quest/filter system items",
    "ftbquests": "Non-physical quest system items",
    "numismatics": "Deprecated currency mod",
    "numismatics_utils": "Deprecated currency utilities",
    "copycats": "Copycat blocks (derive appearance, no intrinsic value)",
    "domum_ornamentum": "Colony decoration schematic system items",
    "perfectplushies": "Cosmetic-only plushie items",
    "perfectplushieapi": "Plushie API items",
    "crafting_on_a_stick": "Utility mod items (portable crafting)",
    "toms_storage": "Storage utility mod",
    "rsinfinitybooster": "Refined Storage addon utility",
    "refinedstorageaddons": "Refined Storage addon utility",
    "online_detector": "Utility redstone mod",
    "dragonlib": "Library mod (no gameplay items)",
    "moonlight": "Library mod (no gameplay items)",
    "questsadditions": "Quest system addon",
    "irons_rpg_tweaks": "RPG config mod",
    "fcl": "Library mod",
    "labels": "Utility labels mod",
    "patchouli": "Guidebook mod",
    "plonk": "Item placement utility",
    "strawstatues": "Decorative armor stand poses (utility)",
    "solonion": "Nutrition tracking mod (utility HUD)",
    "cb_microblock": "Microblock mod items (technical)",
    "buildinggadgets2": "Building gadget utility tools",
    "translocators": "Item/fluid transport utility",
    "constructionwand": "Building utility tools",
    "portable_blueprints": "Blueprint utility items",
    "trading_floor": "Trading floor mod item (meta)",
    "shippingbin": "Shipping bin mod item (economy meta)",
    "toolbelt": "Tool belt utility",
    "simplemagnets": "Magnet utility items",
    "bountiful": "Bounty quest system items",
    "croptania": "Botania crop addon (technical seeds)",
    "littlejoys": "Tiny decorative items (negligible value)",
    "sewingkit": "Sewing utility items",
    "botania_seeds": "Botania seed variants (technical addon)",
    "fsmm": "Currency system mod items",
    "stardew_fishing": "Fishing minigame mod items",
    "dew_drop_farmland_growth": "Farmland growth utility blocks",
    "dew_drop_watering_cans": "Watering can utility items",
    "quality_food": "Food quality indicator (NBT-dependent)",
    "create_central_kitchen": "Create cooking automation (utility)",
    "create_factory_logistics": "Create logistics utility",
    "pipez": "Pipe transport utility",
    "customnpcs": "NPC customization items (admin tools)",
}

OMIT_PATTERNS_COMPILED = [
    (re.compile(r".*_spawn_egg$"), "Creative-only spawn egg"),
    (re.compile(r"minecraft:(command_block|chain_command_block|repeating_command_block)"), "Creative-only command block"),
    (re.compile(r"minecraft:(structure_block|structure_void|barrier|jigsaw|bedrock|debug_stick|knowledge_book)"), "Creative-only / unobtainable"),
    (re.compile(r"minecraft:light$"), "Creative-only light block"),
    (re.compile(r"minecraft:air$"), "Non-physical air block"),
    (re.compile(r"minecraft:bundle$"), "Incomplete vanilla feature"),
    (re.compile(r"minecraft:petrified_oak_slab"), "Unobtainable legacy item"),
    (re.compile(r"minecraft:budding_amethyst"), "Non-obtainable (silk touch doesn't work)"),
    (re.compile(r"minecraft:infested_.*"), "Infested blocks (silverfish traps, no value)"),
    (re.compile(r"minecraft:reinforced_deepslate"), "Unobtainable ancient city block"),
    (re.compile(r"minecraft:player_head"), "NBT-dependent player head"),
    (re.compile(r"minecraft:potion$"), "NBT-dependent (many variants)"),
    (re.compile(r"minecraft:splash_potion"), "NBT-dependent (many variants)"),
    (re.compile(r"minecraft:lingering_potion"), "NBT-dependent (many variants)"),
    (re.compile(r"minecraft:tipped_arrow"), "NBT-dependent (many variants)"),
    (re.compile(r"minecraft:suspicious_(sand|gravel)"), "Archaeology trigger block"),
    (re.compile(r"minecraft:filled_map"), "NBT-dependent filled map"),
    (re.compile(r"minecraft:written_book"), "NBT-dependent written book"),
    (re.compile(r"simplehats:(?!hatbag_).*"), "Cosmetic-only hat"),
    (re.compile(r"everycomp:.*"), "Auto-generated cross-mod variant"),
    (re.compile(r"sophisticatedbackpacks:.*upgrade.*"), "Backpack upgrade (utility)"),
    (re.compile(r"sophisticatedstorage:.*upgrade.*"), "Storage upgrade (utility)"),
    (re.compile(r"refinedstorage:.*"), "Digital storage system (utility)"),
    (re.compile(r"functionalstorage:.*"), "Storage system (utility)"),
    (re.compile(r"minecolonies:blockhut.*"), "Colony building hut (colony-specific)"),
]

# Block variant suffixes → OMIT (Requirement #1)
VARIANT_SUFFIXES = [
    "_stairs", "_slab", "_vertical_slab",
    "_wall", "_fence", "_fence_gate",
    "_button", "_pressure_plate",
    "_door", "_trapdoor",
    "_sign", "_hanging_sign",
]

def get_ns(item_id):
    return item_id.split(":")[0] if ":" in item_id else ""

def get_name(item_id):
    return item_id.split(":", 1)[1] if ":" in item_id else item_id

def is_block_variant(name):
    for suffix in VARIANT_SUFFIXES:
        if name.endswith(suffix):
            return True
    return False

def check_omission(item_id):
    ns = get_ns(item_id)
    name = get_name(item_id)
    if ns in OMIT_NAMESPACES:
        return OMIT_NAMESPACES[ns]
    for pattern, reason in OMIT_PATTERNS_COMPILED:
        if pattern.fullmatch(item_id):
            return reason
    if is_block_variant(name):
        return "Block variant (stairs/slab/wall/fence/door/etc.) — only base blocks are priced"
    return None

# ═══════════════════════════════════════════════════════════════════════
# 3. PRICE LOOKUP
# ═══════════════════════════════════════════════════════════════════════
new_prices = {}

def lookup(item_id):
    if item_id in existing_prices:
        return existing_prices[item_id]
    if item_id in VANILLA_PRICES:
        return VANILLA_PRICES[item_id]
    if item_id in new_prices:
        return new_prices[item_id]
    return None

# ═══════════════════════════════════════════════════════════════════════
# 4. INFERENCE ENGINE (Requirement #2)
# ═══════════════════════════════════════════════════════════════════════

def try_infer_price(item_id, name, ns):
    # --- Bulk containers: _crate/_bag/_sack/_ball/_barrel/_bundle/_basket = base × 9 ---
    bulk_suffixes = {
        "_crate": 9, "_bag": 9, "_sack": 9, "_ball": 9,
        "_basket": 9, "_bundle": 9, "_barrel": 9,
    }
    for suffix, mult in bulk_suffixes.items():
        if name.endswith(suffix):
            base_name = name[:-len(suffix)]
            for try_ns in [ns, "minecraft"]:
                for base_suffix in ["", "s", "_leaf", "_leaves"]:
                    base_id = f"{try_ns}:{base_name}{base_suffix}"
                    p = lookup(base_id)
                    if p is not None:
                        return int(p * mult), f"bulk({base_id}×{mult})"

    # --- Storage block from item: item × 9 ---
    if name.endswith("_block"):
        base = name[:-len("_block")]
        for try_ns in [ns, "minecraft"]:
            for base_suffix in ["", "_ingot"]:
                base_id = f"{try_ns}:{base}{base_suffix}"
                p = lookup(base_id)
                if p is not None:
                    return int(p * 9), f"storage_block({base_id}×9)"
            # raw_ blocks
            raw_id = f"{try_ns}:raw_{base.replace('raw_', '')}"
            p = lookup(raw_id)
            if p is not None:
                return int(p * 9), f"raw_block({raw_id}×9)"

    # --- Cooked/roasted/smoked/baked/grilled = raw × 1.5 ---
    for prefix in ["cooked_", "roasted_", "smoked_", "baked_", "grilled_"]:
        if name.startswith(prefix):
            raw_name = name[len(prefix):]
            for try_ns in [ns, "minecraft"]:
                for raw_prefix in ["", "raw_"]:
                    raw_id = f"{try_ns}:{raw_prefix}{raw_name}"
                    p = lookup(raw_id)
                    if p is not None:
                        return int(p * 1.5), f"cooked({raw_id}×1.5)"

    # --- Stripped = same as log ---
    if name.startswith("stripped_"):
        base_name = name[len("stripped_"):]
        for try_ns in [ns, "minecraft"]:
            p = lookup(f"{try_ns}:{base_name}")
            if p is not None:
                return p, f"stripped({try_ns}:{base_name})"

    # --- Waxed = same as unwaxed ---
    if name.startswith("waxed_"):
        base_name = name[len("waxed_"):]
        p = lookup(f"{ns}:{base_name}")
        if p is not None:
            return p, f"waxed({ns}:{base_name})"

    # --- Polished/chiseled/smooth/cut/cracked/mossy = base + 1 ---
    for prefix in ["polished_", "chiseled_", "smooth_", "cut_", "cracked_", "mossy_"]:
        if name.startswith(prefix):
            base_name = name[len(prefix):]
            for try_ns in [ns, "minecraft"]:
                p = lookup(f"{try_ns}:{base_name}")
                if p is not None:
                    return p + 1, f"processed({try_ns}:{base_name}+1)"

    # --- Planks from log ---
    if name.endswith("_planks"):
        wood_type = name[:-len("_planks")]
        for log_suffix in ["_log", "_stem"]:
            for try_ns in [ns, "minecraft"]:
                p = lookup(f"{try_ns}:{wood_type}{log_suffix}")
                if p is not None:
                    return p, f"planks({try_ns}:{wood_type}{log_suffix})"

    # --- Wood/hyphae (bark) = log ---
    if name.endswith("_wood"):
        wood_type = name[:-len("_wood")]
        for try_ns in [ns, "minecraft"]:
            p = lookup(f"{try_ns}:{wood_type}_log")
            if p is not None:
                return p, f"bark({try_ns}:{wood_type}_log)"
    if name.endswith("_hyphae"):
        wood_type = name[:-len("_hyphae")]
        for try_ns in [ns, "minecraft"]:
            p = lookup(f"{try_ns}:{wood_type}_stem")
            if p is not None:
                return p, f"bark({try_ns}:{wood_type}_stem)"

    # --- Color variants ---
    colors = [
        "white_", "orange_", "magenta_", "light_blue_", "yellow_", "lime_",
        "pink_", "gray_", "light_gray_", "cyan_", "purple_", "blue_",
        "brown_", "green_", "red_", "black_",
    ]
    for color in colors:
        if name.startswith(color):
            base_name = name[len(color):]
            for try_ns in [ns, "minecraft"]:
                p = lookup(f"{try_ns}:{base_name}")
                if p is not None:
                    return p, f"color({try_ns}:{base_name})"

    # --- Oxidation variants ---
    for prefix in ["exposed_", "weathered_", "oxidized_"]:
        if name.startswith(prefix):
            base_name = name[len(prefix):]
            p = lookup(f"{ns}:{base_name}")
            if p is not None:
                return p, f"oxidation({ns}:{base_name})"

    # --- Large = base × 4 ---
    if name.startswith("large_"):
        base_name = name[len("large_"):]
        p = lookup(f"{ns}:{base_name}")
        if p is not None:
            return int(p * 4), f"large({ns}:{base_name}×4)"

    # --- Aged = base × 4, double_aged = base × 16 ---
    if name.startswith("double_aged_"):
        base_name = name[len("double_aged_"):]
        p = lookup(f"{ns}:{base_name}")
        if p is not None:
            return int(p * 16), f"double_aged({ns}:{base_name}×16)"
    if name.startswith("aged_"):
        base_name = name[len("aged_"):]
        p = lookup(f"{ns}:{base_name}")
        if p is not None:
            return int(p * 4), f"aged({ns}:{base_name}×4)"

    # --- Nugget = ingot / 9 ---
    if name.endswith("_nugget"):
        ingot_name = name[:-len("_nugget")] + "_ingot"
        for try_ns in [ns, "minecraft"]:
            p = lookup(f"{try_ns}:{ingot_name}")
            if p is not None:
                return max(1, int(p / 9)), f"nugget({try_ns}:{ingot_name}/9)"

    return None, None


# ═══════════════════════════════════════════════════════════════════════
# 5. MATERIAL TIERS (tools/armor)
# ═══════════════════════════════════════════════════════════════════════

MATERIAL_BASE = {
    "wooden": 2, "wood": 2, "stone": 4,
    "leather": 8, "chainmail": 16,
    "iron": 12, "golden": 24, "gold": 24,
    "diamond": 128, "netherite": 512,
    "copper": 6, "lead": 24, "silver": 32, "bismuth": 16,
    "manasteel": 32, "elementium": 96, "terrasteel": 512, "neptunium": 128,
}

TOOL_MULTIPLIER = {
    "sword": 2, "pickaxe": 3, "axe": 3, "shovel": 1, "hoe": 2,
    "helmet": 5, "chestplate": 8, "leggings": 7, "boots": 4,
    "horse_armor": 6, "hammer": 6, "shield": 4,
}

def resolve_tool_armor(name):
    for material, base in MATERIAL_BASE.items():
        if name.startswith(material + "_"):
            remaining = name[len(material) + 1:]
            for tool_type, mult in TOOL_MULTIPLIER.items():
                if remaining == tool_type:
                    return base * mult
    return None


# ═══════════════════════════════════════════════════════════════════════
# 6. FURNITURE SUB-TIERS (Requirement #3)
# ═══════════════════════════════════════════════════════════════════════

FURNITURE_SUBTIERS = [
    # Appliances/electronics (12-24)
    ("television", 20), ("computer", 20), ("monitor", 16),
    ("fridge", 16), ("freezer", 16), ("oven", 16), ("microwave", 14),
    ("washing_machine", 16), ("dishwasher", 16),
    ("radio", 12), ("phone", 12), ("gramophone", 14),
    ("toaster", 10), ("blender", 10),
    ("ceiling_fan", 12), ("fan", 8),
    # Large furniture (10-24)
    ("bunk_bed", 16), ("canopy_bed", 16),
    ("wardrobe", 14), ("armoire", 14), ("dresser", 12),
    ("bathtub", 14), ("shower", 12),
    ("fountain", 18), ("chandelier", 16), ("candelabra", 12),
    ("grand_piano", 24), ("piano", 20),
    ("aquarium", 14), ("fish_tank", 14),
    ("grandfather_clock", 16),
    ("sofa", 10), ("couch", 10), ("loveseat", 10), ("sectional", 12),
    ("cabinet", 10), ("hutch", 12), ("buffet", 12), ("sideboard", 12),
    # Medium furniture (6-10)
    ("dining_table", 10), ("kitchen_table", 10),
    ("coffee_table", 8), ("end_table", 6), ("side_table", 6),
    ("nightstand", 6), ("bedside", 6),
    ("cutting_board", 6), ("table", 8),
    ("desk", 8), ("workstation", 10),
    ("counter", 8), ("island", 10),
    ("drawer", 8), ("chest_of_drawers", 10),
    ("bookshelf", 8), ("bookcase", 10),
    ("shelf", 6), ("display_case", 8), ("display", 6),
    ("mirror", 8), ("vanity", 10),
    ("sink", 8), ("toilet", 8),
    ("grill", 10), ("barbecue", 12),
    ("stove", 10), ("fireplace", 12), ("chimney", 8),
    ("bed", 10), ("sleeping_bag", 8), ("hammock", 8),
    ("bench", 6), ("pew", 6),
    ("chair", 6), ("armchair", 8), ("rocking_chair", 8),
    ("recliner", 10), ("throne", 16),
    ("stool", 4), ("ottoman", 6), ("pouf", 4),
    ("mailbox", 6), ("post_box", 6),
    # Lighting (4-16)
    ("floor_lamp", 8), ("table_lamp", 6), ("wall_lamp", 6),
    ("desk_lamp", 6), ("sconce", 6), ("lamp", 6), ("lantern", 6),
    ("string_lights", 4), ("fairy_lights", 4),
    # Small décor (2-8)
    ("pan", 4), ("pot", 3), ("wok", 4), ("skillet", 4),
    ("plate", 3), ("dish", 3), ("bowl", 2), ("tray", 3),
    ("mug", 3), ("cup", 3), ("goblet", 4), ("tankard", 4),
    ("fork", 2), ("knife", 2), ("spoon", 2),
    ("kettle", 4), ("teapot", 4),
    ("vase", 4), ("flower_pot", 3), ("planter", 4),
    ("picture_frame", 4), ("painting", 8), ("poster", 4), ("frame", 4),
    ("clock", 8), ("alarm_clock", 6),
    ("rug", 4), ("carpet", 2), ("mat", 2), ("runner", 3),
    ("curtain", 4), ("drape", 4), ("blind", 4), ("shutter", 4),
    ("pillow", 4), ("cushion", 4), ("blanket", 6),
    ("basket", 4), ("crate", 4), ("box", 3), ("bin", 3),
    ("trash_can", 4), ("trash", 3),
    ("umbrella", 6), ("parasol", 6), ("awning", 6),
    ("tent", 12),
    ("wallpaper", 3), ("molding", 2), ("trim", 2),
    ("window", 4), ("window_sill", 3),
    ("hedge", 3), ("trellis", 3), ("lattice", 3), ("garden", 4),
    ("statue", 12), ("trophy", 16), ("figurine", 6),
    ("coin_stack", 8), ("globe", 8),
    ("book_stack", 6), ("scroll", 4), ("quill", 3),
    ("candle", 3), ("candlestick", 4), ("torch_holder", 3),
    ("rope", 3), ("ladder", 3),
    ("bell", 6), ("wind_chime", 4), ("chime", 4),
    ("flag", 4), ("banner", 6), ("pennant", 3),
    ("sign", 3), ("plaque", 4),
    ("wreath", 6), ("garland", 4),
    ("toy", 6), ("stuffed", 8), ("doll", 8),
    ("board_game", 10),
]

FURNITURE_NAMESPACES = {
    "refurbished_furniture", "furniture", "fantasyfurniture", "tanukidecor",
    "beautify", "nightlights", "whimsy_deco", "cluttered",
    "displaydelight", "display_delight", "chimes",
}

def resolve_furniture_price(name, ns):
    if ns not in FURNITURE_NAMESPACES:
        return None
    for keyword, price in FURNITURE_SUBTIERS:
        if keyword in name:
            return price
    return 4  # generic fallback for furniture mods


# ═══════════════════════════════════════════════════════════════════════
# 7. NAMESPACE DEFAULTS
# ═══════════════════════════════════════════════════════════════════════

NAMESPACE_DEFAULTS = {
    "minecraft": 4, "create": 6, "quark": 4,
    "supplementaries": 5, "twigs": 3, "decorative_blocks": 4,
    "botania": 16,
    "atmospheric": 4, "windswept": 4, "autumnity": 4, "meadow": 5,
    "beachparty": 5, "snowyspirit": 5,
    "vinery": 6, "nethervinery": 8,
    "farmersdelight": 8, "farm_and_charm": 8, "veggiesdelight": 8,
    "bakery": 8, "candlelight": 8, "vintagedelight": 8,
    "brewery": 8, "crabbersdelight": 8, "herbalbrews": 8,
    "refurbished_furniture": 6, "furniture": 6, "fantasyfurniture": 6,
    "tanukidecor": 6, "beautify": 5, "nightlights": 4,
    "whimsy_deco": 5, "cluttered": 4,
    "dramaticdoors": 4, "railways": 8, "minecolonies": 4,
    "betterarcheology": 8, "oreganized": 8, "etcetera": 6, "trials": 8,
    "species": 6, "ribbits": 6, "splendid_slimes": 8,
    "crittersandcompanions": 6, "buzzier_bees": 5,
    "pamhc2trees": 6, "wildernature": 6, "verdantvibes": 5,
    "farmlife": 8, "longwings": 6,
    "relics": 256, "aquaculture": 8, "unusualfishmod": 8,
    "netherdepthsupgrade": 16, "smallships": 16,
    "moreminecarts": 8, "waystones": 16, "clayworks": 3,
    "society": 16, "displaydelight": 4, "comforts": 8,
    "vanillabackport": 4, "immersive_paintings": 8, "waterframes": 8,
    "etched": 6, "zetter": 8, "exposure": 8,
    "legendarycreatures": 32, "rottencreatures": 8, "trofers": 8,
    "kata": 6, "rehooked": 8, "gag": 6, "extra_gauges": 6,
    "torchmaster": 8, "amendments": 4,
    "create_hypertube": 8, "create_enchantment_industry": 12,
    "create_mechanical_extruder": 8, "createrailwaysnavigator": 6,
    "createutilities": 6, "untitledduckmod": 8,
    "golemoverhaul": 8, "hamsters": 8, "snuffles": 8, "snowpig": 8,
    "mysticaloaktree": 32, "simplerecall": 8, "lootr": 8,
    "chimes": 4, "sereneseasons": 4, "sawmill": 6,
    "tradingpost": 8, "naturescompass": 16, "liltractor": 32,
    "sophisticatedstorage": 12, "sophisticatedbackpacks": 12,
    "documents": 4, "farmingforblockheads": 6, "moblassos": 12,
    "structurize": 4, "paraglider": 16, "gamediscs": 64,
    "automobility": 12, "justhammers": 16, "multipiston": 8,
}

# ═══════════════════════════════════════════════════════════════════════
# 8. VANILLA SPECIFIC PRICES
# ═══════════════════════════════════════════════════════════════════════

VANILLA_PRICES = {
    # Building
    "minecraft:stone": 2, "minecraft:granite": 2, "minecraft:diorite": 2,
    "minecraft:andesite": 2, "minecraft:polished_granite": 3,
    "minecraft:polished_diorite": 3, "minecraft:polished_andesite": 3,
    "minecraft:deepslate": 3, "minecraft:cobbled_deepslate": 3,
    "minecraft:polished_deepslate": 4, "minecraft:calcite": 3, "minecraft:tuff": 2,
    "minecraft:dripstone_block": 4, "minecraft:pointed_dripstone": 3,
    "minecraft:grass_block": 1, "minecraft:dirt": 1, "minecraft:coarse_dirt": 1,
    "minecraft:podzol": 2, "minecraft:rooted_dirt": 2, "minecraft:mud": 2,
    "minecraft:crimson_nylium": 4, "minecraft:warped_nylium": 4,
    "minecraft:cobblestone": 1, "minecraft:mossy_cobblestone": 3,
    "minecraft:obsidian": 16, "minecraft:crying_obsidian": 32,
    "minecraft:sand": 1, "minecraft:red_sand": 2, "minecraft:gravel": 1,
    "minecraft:clay": 4, "minecraft:clay_ball": 1,
    "minecraft:netherrack": 1, "minecraft:basalt": 2, "minecraft:smooth_basalt": 3,
    "minecraft:polished_basalt": 3, "minecraft:blackstone": 2,
    "minecraft:gilded_blackstone": 16, "minecraft:soul_sand": 2, "minecraft:soul_soil": 2,
    "minecraft:magma_block": 4, "minecraft:glowstone": 8,
    "minecraft:end_stone": 4, "minecraft:end_stone_bricks": 5,
    "minecraft:purpur_block": 6, "minecraft:purpur_pillar": 6,
    "minecraft:prismarine": 8, "minecraft:prismarine_bricks": 10,
    "minecraft:dark_prismarine": 12, "minecraft:prismarine_crystals": 20,
    "minecraft:prismarine_shard": 6, "minecraft:sea_lantern": 24,
    "minecraft:sandstone": 2, "minecraft:chiseled_sandstone": 3,
    "minecraft:cut_sandstone": 3, "minecraft:red_sandstone": 3,
    "minecraft:smooth_sandstone": 3, "minecraft:smooth_stone": 3,
    "minecraft:bricks": 4, "minecraft:brick": 1,
    "minecraft:stone_bricks": 3,
    "minecraft:mossy_stone_bricks": 4, "minecraft:cracked_stone_bricks": 3,
    "minecraft:chiseled_stone_bricks": 4, "minecraft:packed_mud": 3,
    "minecraft:mud_bricks": 4, "minecraft:nether_bricks": 4,
    "minecraft:nether_brick": 1,
    "minecraft:chiseled_nether_bricks": 5, "minecraft:cracked_nether_bricks": 4,
    "minecraft:red_nether_bricks": 6, "minecraft:deepslate_bricks": 4,
    "minecraft:cracked_deepslate_bricks": 4, "minecraft:deepslate_tiles": 5,
    "minecraft:cracked_deepslate_tiles": 5, "minecraft:chiseled_deepslate": 5,
    "minecraft:quartz_block": 32, "minecraft:chiseled_quartz_block": 34,
    "minecraft:quartz_bricks": 34, "minecraft:quartz_pillar": 34,
    "minecraft:smooth_quartz": 34, "minecraft:smooth_red_sandstone": 4,
    # Misc blocks
    "minecraft:sponge": 32, "minecraft:wet_sponge": 32,
    "minecraft:glass": 2, "minecraft:tinted_glass": 12,
    "minecraft:bookshelf": 12, "minecraft:chiseled_bookshelf": 14,
    "minecraft:decorated_pot": 16, "minecraft:spawner": 64,
    "minecraft:ice": 2, "minecraft:packed_ice": 16, "minecraft:blue_ice": 48,
    "minecraft:snow": 1, "minecraft:snow_block": 2,
    "minecraft:moss_block": 2, "minecraft:moss_carpet": 1,
    "minecraft:sculk": 8, "minecraft:sculk_vein": 4,
    "minecraft:sculk_sensor": 24, "minecraft:calibrated_sculk_sensor": 48,
    "minecraft:sculk_catalyst": 48, "minecraft:sculk_shrieker": 64,
    # Redstone
    "minecraft:dispenser": 12, "minecraft:dropper": 10, "minecraft:piston": 12,
    "minecraft:sticky_piston": 16, "minecraft:tnt": 24,
    "minecraft:lever": 1, "minecraft:redstone_torch": 2,
    "minecraft:repeater": 6, "minecraft:comparator": 12,
    "minecraft:observer": 16, "minecraft:hopper": 20,
    "minecraft:daylight_detector": 12, "minecraft:note_block": 8,
    "minecraft:tripwire_hook": 4, "minecraft:trapped_chest": 6,
    "minecraft:target": 8, "minecraft:lightning_rod": 8,
    # Utility
    "minecraft:chest": 4, "minecraft:ender_chest": 128, "minecraft:barrel": 4,
    "minecraft:crafting_table": 2, "minecraft:furnace": 4, "minecraft:blast_furnace": 12,
    "minecraft:smoker": 8, "minecraft:stonecutter": 8,
    "minecraft:grindstone": 8, "minecraft:smithing_table": 16,
    "minecraft:anvil": 48, "minecraft:chipped_anvil": 32, "minecraft:damaged_anvil": 16,
    "minecraft:cartography_table": 8, "minecraft:fletching_table": 8,
    "minecraft:loom": 6, "minecraft:composter": 4,
    "minecraft:cauldron": 12, "minecraft:brewing_stand": 16,
    "minecraft:enchanting_table": 128, "minecraft:beacon": 512,
    "minecraft:bell": 64, "minecraft:conduit": 256,
    "minecraft:lodestone": 128, "minecraft:respawn_anchor": 128,
    "minecraft:end_crystal": 64, "minecraft:lectern": 12, "minecraft:jukebox": 24,
    # Plants
    "minecraft:bamboo": 1, "minecraft:sugar_cane": 12,
    "minecraft:kelp": 1, "minecraft:dried_kelp": 2, "minecraft:dried_kelp_block": 18,
    "minecraft:vine": 2, "minecraft:glow_lichen": 4,
    "minecraft:lily_pad": 2, "minecraft:sea_pickle": 4,
    "minecraft:seagrass": 1, "minecraft:hanging_roots": 1,
    "minecraft:big_dripleaf": 3, "minecraft:small_dripleaf": 2,
    "minecraft:spore_blossom": 8, "minecraft:chorus_plant": 4,
    "minecraft:chorus_flower": 8, "minecraft:nether_sprouts": 1,
    "minecraft:weeping_vines": 2, "minecraft:twisting_vines": 2,
    "minecraft:crimson_roots": 1, "minecraft:warped_roots": 1,
    "minecraft:pink_petals": 2, "minecraft:fern": 1, "minecraft:grass": 1,
    "minecraft:dead_bush": 1, "minecraft:cobweb": 8,
    "minecraft:azalea": 4, "minecraft:flowering_azalea": 6,
    # Flowers
    "minecraft:dandelion": 1, "minecraft:poppy": 1, "minecraft:blue_orchid": 2,
    "minecraft:allium": 2, "minecraft:azure_bluet": 1,
    "minecraft:red_tulip": 2, "minecraft:orange_tulip": 2,
    "minecraft:white_tulip": 2, "minecraft:pink_tulip": 2,
    "minecraft:oxeye_daisy": 1, "minecraft:cornflower": 2,
    "minecraft:lily_of_the_valley": 2, "minecraft:wither_rose": 32,
    "minecraft:sunflower": 2, "minecraft:lilac": 2,
    "minecraft:rose_bush": 2, "minecraft:peony": 2,
    # Saplings
    "minecraft:oak_sapling": 2, "minecraft:spruce_sapling": 2,
    "minecraft:birch_sapling": 2, "minecraft:jungle_sapling": 3,
    "minecraft:acacia_sapling": 3, "minecraft:cherry_sapling": 4,
    "minecraft:dark_oak_sapling": 3, "minecraft:mangrove_propagule": 4,
    # Dyes
    "minecraft:white_dye": 2, "minecraft:orange_dye": 2, "minecraft:magenta_dye": 2,
    "minecraft:light_blue_dye": 2, "minecraft:yellow_dye": 2, "minecraft:lime_dye": 2,
    "minecraft:pink_dye": 2, "minecraft:gray_dye": 2, "minecraft:light_gray_dye": 2,
    "minecraft:cyan_dye": 2, "minecraft:purple_dye": 2, "minecraft:blue_dye": 2,
    "minecraft:brown_dye": 2, "minecraft:green_dye": 2, "minecraft:red_dye": 2,
    "minecraft:black_dye": 2, "minecraft:bone_meal": 1,
    "minecraft:ink_sac": 2, "minecraft:glow_ink_sac": 8,
    # Items
    "minecraft:stick": 1, "minecraft:bowl": 1,
    "minecraft:string": 4, "minecraft:gunpowder": 8,
    "minecraft:flint": 2, "minecraft:bone": 2,
    "minecraft:sugar": 3, "minecraft:paper": 3,
    "minecraft:book": 12, "minecraft:writable_book": 16,
    "minecraft:slime_ball": 4, "minecraft:ender_pearl": 16,
    "minecraft:blaze_rod": 16, "minecraft:blaze_powder": 8,
    "minecraft:ghast_tear": 32, "minecraft:magma_cream": 8,
    "minecraft:spider_eye": 4, "minecraft:fermented_spider_eye": 8,
    "minecraft:phantom_membrane": 16, "minecraft:fire_charge": 8,
    "minecraft:arrow": 2, "minecraft:spectral_arrow": 8, "minecraft:snowball": 1,
    "minecraft:bucket": 6, "minecraft:water_bucket": 7,
    "minecraft:lava_bucket": 16, "minecraft:powder_snow_bucket": 8,
    "minecraft:milk_bucket": 8, "minecraft:saddle": 32,
    "minecraft:name_tag": 32, "minecraft:lead": 8,
    "minecraft:map": 8, "minecraft:compass": 12,
    "minecraft:clock": 24, "minecraft:recovery_compass": 64,
    "minecraft:spyglass": 16, "minecraft:glass_bottle": 2,
    "minecraft:nether_star": 2048, "minecraft:end_rod": 8,
    "minecraft:shulker_shell": 64, "minecraft:dragon_breath": 32,
    "minecraft:nautilus_shell": 64, "minecraft:heart_of_the_sea": 256,
    "minecraft:echo_shard": 192, "minecraft:disc_fragment_5": 32,
    "minecraft:brush": 12, "minecraft:rotten_flesh": 1,
    "minecraft:dragon_egg": 3072,
    # Music discs
    "minecraft:music_disc_13": 64, "minecraft:music_disc_cat": 64,
    "minecraft:music_disc_blocks": 64, "minecraft:music_disc_chirp": 64,
    "minecraft:music_disc_far": 64, "minecraft:music_disc_mall": 64,
    "minecraft:music_disc_mellohi": 64, "minecraft:music_disc_stal": 64,
    "minecraft:music_disc_strad": 64, "minecraft:music_disc_ward": 64,
    "minecraft:music_disc_11": 64, "minecraft:music_disc_wait": 64,
    "minecraft:music_disc_otherside": 128, "minecraft:music_disc_5": 128,
    "minecraft:music_disc_pigstep": 256, "minecraft:music_disc_relic": 128,
    # Heads
    "minecraft:skeleton_skull": 24, "minecraft:wither_skeleton_skull": 128,
    "minecraft:zombie_head": 24, "minecraft:creeper_head": 32,
    "minecraft:dragon_head": 4608, "minecraft:piglin_head": 48,
    # Vehicles
    "minecraft:minecart": 16, "minecraft:chest_minecart": 20,
    "minecraft:furnace_minecart": 20, "minecraft:tnt_minecart": 40,
    "minecraft:hopper_minecart": 36,
    "minecraft:rail": 4, "minecraft:powered_rail": 12,
    "minecraft:detector_rail": 8, "minecraft:activator_rail": 8,
    # Smithing templates
    "minecraft:netherite_upgrade_smithing_template": 256,
    "minecraft:sentry_armor_trim_smithing_template": 64,
    "minecraft:dune_armor_trim_smithing_template": 64,
    "minecraft:coast_armor_trim_smithing_template": 64,
    "minecraft:wild_armor_trim_smithing_template": 64,
    "minecraft:ward_armor_trim_smithing_template": 128,
    "minecraft:eye_armor_trim_smithing_template": 128,
    "minecraft:vex_armor_trim_smithing_template": 128,
    "minecraft:tide_armor_trim_smithing_template": 128,
    "minecraft:snout_armor_trim_smithing_template": 128,
    "minecraft:rib_armor_trim_smithing_template": 128,
    "minecraft:spire_armor_trim_smithing_template": 256,
    "minecraft:wayfinder_armor_trim_smithing_template": 128,
    "minecraft:shaper_armor_trim_smithing_template": 128,
    "minecraft:silence_armor_trim_smithing_template": 256,
    "minecraft:raiser_armor_trim_smithing_template": 128,
    "minecraft:host_armor_trim_smithing_template": 128,
    # Copper
    "minecraft:copper_block": 54,
    # Metals
    "minecraft:iron_ingot": 12, "minecraft:gold_ingot": 24,
    "minecraft:copper_ingot": 6, "minecraft:netherite_ingot": 1024,
    "minecraft:iron_nugget": 1, "minecraft:gold_nugget": 3,
    "minecraft:iron_block": 108, "minecraft:gold_block": 216,
    "minecraft:netherite_block": 9216,
    # Ores
    "minecraft:coal_ore": 6, "minecraft:deepslate_coal_ore": 7,
    "minecraft:iron_ore": 10, "minecraft:deepslate_iron_ore": 12,
    "minecraft:copper_ore": 6, "minecraft:deepslate_copper_ore": 7,
    "minecraft:gold_ore": 18, "minecraft:deepslate_gold_ore": 20,
    "minecraft:redstone_ore": 10, "minecraft:deepslate_redstone_ore": 12,
    "minecraft:emerald_ore": 36, "minecraft:deepslate_emerald_ore": 40,
    "minecraft:lapis_ore": 8, "minecraft:deepslate_lapis_ore": 10,
    "minecraft:diamond_ore": 260, "minecraft:deepslate_diamond_ore": 280,
    "minecraft:nether_gold_ore": 12, "minecraft:nether_quartz_ore": 10,
    "minecraft:ancient_debris": 1024,
    # Planks
    "minecraft:oak_planks": 2, "minecraft:spruce_planks": 2,
    "minecraft:birch_planks": 2, "minecraft:jungle_planks": 2,
    "minecraft:acacia_planks": 2, "minecraft:cherry_planks": 2,
    "minecraft:dark_oak_planks": 2, "minecraft:mangrove_planks": 2,
    "minecraft:bamboo_planks": 2, "minecraft:crimson_planks": 4,
    "minecraft:warped_planks": 4, "minecraft:bamboo_mosaic": 3,
    # Wool / terracotta / concrete / glass / candle / bed / banner / carpet
    "minecraft:white_wool": 2, "minecraft:terracotta": 4,
    "minecraft:white_concrete": 3, "minecraft:white_concrete_powder": 3,
    "minecraft:glass_pane": 1, "minecraft:candle": 3,
    "minecraft:white_bed": 8, "minecraft:white_banner": 8,
    "minecraft:white_carpet": 1,
    # Misc
    "minecraft:painting": 8, "minecraft:item_frame": 6, "minecraft:glow_item_frame": 12,
    "minecraft:elytra": 2048, "minecraft:trident": 512,
    "minecraft:farmland": 2,
    "minecraft:mangrove_roots": 3, "minecraft:muddy_mangrove_roots": 3,
    "minecraft:torch": 1, "minecraft:soul_torch": 2,
    "minecraft:lantern": 6, "minecraft:soul_lantern": 8,
    "minecraft:ladder": 2, "minecraft:chain": 6,
    "minecraft:shulker_box": 128,
    # Food (not in globalRegistry)
    "minecraft:suspicious_stew": 12,
}

# ═══════════════════════════════════════════════════════════════════════
# 9. PATTERN-BASED PRICING
# ═══════════════════════════════════════════════════════════════════════

def resolve_pattern_price(item_id, name, ns):
    if name.endswith("_wool"): return 2
    if name.endswith("_carpet"): return 1
    if "_terracotta" in name: return 4
    if "_concrete" in name: return 3
    if name.endswith("_glass_pane") or name == "glass_pane": return 1
    if name.endswith("_glass") or name == "glass": return 2
    if "stained_glass" in name: return 3 if "pane" not in name else 1
    if name.endswith("_bed"): return 8
    if name.endswith("_banner"): return 8
    if name.endswith("_leaves"): return 1
    if name.endswith("_sapling"): return 3
    if name.endswith("_seeds") or name.endswith("_seed"): return 1
    if name.endswith("_dye"): return 2
    if any(name.endswith(s) for s in ["_flower", "_tulip", "_daisy", "_orchid"]): return 2
    if name.endswith("_torch") or name == "torch": return 1
    if name.endswith("_lantern"): return 6
    if name.endswith("_candle"): return 3
    if name.endswith("_chest_boat"): return 8
    if name.endswith("_boat") or name.endswith("_raft"): return 4
    if name.endswith("_minecart"): return 16
    if "pottery_sherd" in name: return 80
    if "smithing_template" in name: return 64
    if name.startswith("music_disc_"): return 64
    if name.endswith("_head") or name.endswith("_skull"): return 32
    if name.endswith("_planks"): return 2
    if name.endswith("_wood") or name.endswith("_hyphae"): return 3
    if "shulker_box" in name: return 128
    if name.endswith("_rail") or name.endswith("_track"): return 8
    if name.endswith("_bucket"): return 8
    if name.endswith("_log") or name.endswith("_stem"): return 3
    return None


# ═══════════════════════════════════════════════════════════════════════
# 10. MAIN ASSIGNMENT
# ═══════════════════════════════════════════════════════════════════════

omitted = []

def assign_price(item_id):
    ns = get_ns(item_id)
    name = get_name(item_id)

    if item_id in existing_prices:
        return existing_prices[item_id], "already_priced"

    omit_reason = check_omission(item_id)
    if omit_reason:
        return None, omit_reason

    if item_id in VANILLA_PRICES:
        return VANILLA_PRICES[item_id], "vanilla_specific"

    tool_price = resolve_tool_armor(name)
    if tool_price:
        return tool_price, "tools_and_armor"

    inferred, infer_desc = try_infer_price(item_id, name, ns)
    if inferred is not None:
        return max(1, inferred), f"inferred:{infer_desc}"

    furn_price = resolve_furniture_price(name, ns)
    if furn_price is not None:
        return furn_price, "furniture_subtier"

    pattern_price = resolve_pattern_price(item_id, name, ns)
    if pattern_price is not None:
        return pattern_price, "pattern"

    default = NAMESPACE_DEFAULTS.get(ns, 4)
    return default, "namespace_default"


# ═══════════════════════════════════════════════════════════════════════
# 11. PROCESS (two passes for inference chains)
# ═══════════════════════════════════════════════════════════════════════

category_counts = defaultdict(int)
omitted_by_reason = defaultdict(int)

for item_id in all_items:
    if item_id in existing_prices:
        continue
    price, reason = assign_price(item_id)
    if price is None:
        omitted.append((item_id, reason))
        omitted_by_reason[reason] += 1
    else:
        new_prices[item_id] = price
        category_counts[reason] += 1

# Pass 2: retry inference for non-variant omitted items
retry_found = []
for item_id, reason in omitted:
    if "Block variant" in reason:
        continue
    ns = get_ns(item_id)
    name = get_name(item_id)
    inferred, infer_desc = try_infer_price(item_id, name, ns)
    if inferred is not None:
        retry_found.append((item_id, max(1, inferred), f"inferred_pass2:{infer_desc}"))

for item_id, price, reason in retry_found:
    new_prices[item_id] = price
    category_counts[reason] += 1

omitted = [(i, r) for i, r in omitted if i not in new_prices]
omitted_by_reason = defaultdict(int)
for _, reason in omitted:
    omitted_by_reason[reason] += 1

print(f"\nNewly priced items: {len(new_prices)}")
print(f"Omitted items: {len(omitted)}")

# ═══════════════════════════════════════════════════════════════════════
# 12. WRITE OMITTED
# ═══════════════════════════════════════════════════════════════════════
with open(OMITTED_PATH, "w", encoding="utf-8") as f:
    f.write("# Items omitted from the StateCraft item value registry\n")
    f.write("# These items have no sell value in Trading Hubs\n")
    f.write(f"# Total omitted: {len(omitted)} items\n\n")
    by_reason = defaultdict(list)
    for item_id, reason in omitted:
        by_reason[reason].append(item_id)
    for reason, items in sorted(by_reason.items()):
        f.write(f"\n## {reason} ({len(items)} items)\n")
        for item_id in sorted(items):
            f.write(f"  {item_id}\n")

print(f"Omitted items written to {OMITTED_PATH}")

# ═══════════════════════════════════════════════════════════════════════
# 13. CATEGORIZE AND MERGE
# ═══════════════════════════════════════════════════════════════════════

def categorize_item(item_id, name, ns):
    for mat in MATERIAL_BASE:
        if name.startswith(mat + "_"):
            remaining = name[len(mat) + 1:]
            if remaining in TOOL_MULTIPLIER:
                return "tools_and_armor"
    if any(k in name for k in ["_ore", "_ingot", "_nugget"]):
        return "ores_and_materials"
    if any(k in name for k in ["_planks", "_wood", "_hyphae", "_log", "_stem", "bamboo_block"]):
        return "wood_and_logs"
    if any(k in name for k in ["_wool", "_carpet", "_glass", "_candle", "_bed",
                                "_banner", "_dye", "painting", "item_frame",
                                "_terracotta", "_concrete", "shulker_box"]):
        return "decorative"
    if any(k in name for k in ["_sapling", "_leaves", "_flower", "_seeds", "mushroom",
                                "vine", "moss", "fern", "lily", "seagrass", "kelp",
                                "azalea", "spore", "dripleaf", "petal", "cactus"]):
        return "nature"
    if any(k in name for k in ["_boat", "_raft", "_minecart", "_rail", "_track"]):
        return "transportation"
    if any(k in name for k in ["music_disc", "pottery_sherd", "smithing_template",
                                "_head", "_skull", "echo_shard", "heart_of_the_sea",
                                "nautilus", "nether_star", "elytra", "trident",
                                "totem", "goat_horn", "dragon"]):
        return "rare_loot"
    if ns in FURNITURE_NAMESPACES:
        return "furniture"
    if ns in ["farmersdelight", "farm_and_charm", "veggiesdelight", "bakery",
              "candlelight", "vintagedelight", "brewery", "crabbersdelight",
              "herbalbrews", "beachparty", "snowyspirit", "meadow"]:
        return "food_and_cooking"
    if ns in ["railways", "moreminecarts", "smallships", "automobility"]:
        return "transportation"
    if ns in ["atmospheric", "windswept", "autumnity", "species", "ribbits",
              "verdantvibes", "longwings", "wildernature", "crittersandcompanions",
              "buzzier_bees", "farmlife", "splendid_slimes", "snuffles", "hamsters",
              "snowpig", "untitledduckmod", "golemoverhaul", "legendarycreatures",
              "rottencreatures"]:
        return "nature_and_creatures"
    if ns == "botania": return "botania"
    if ns in ["create", "createutilities", "create_hypertube",
              "create_enchantment_industry", "create_mechanical_extruder"]:
        return "create"
    if ns == "quark": return "quark"
    if ns == "society": return "society"
    return "miscellaneous_new"

new_categories = defaultdict(dict)
for item_id, price in sorted(new_prices.items()):
    ns = get_ns(item_id)
    name = get_name(item_id)
    cat = categorize_item(item_id, name, ns)
    new_categories[cat][item_id] = price

output = {"_comment": existing_data.get("_comment", "")}
for cat, vals in existing_data.items():
    if cat == "_comment": continue
    if isinstance(vals, dict):
        output[cat] = dict(sorted(vals.items()))

CATEGORY_ORDER = [
    "ores_and_materials", "wood_and_logs", "decorative",
    "tools_and_armor", "nature", "nature_and_creatures",
    "furniture", "transportation", "food_and_cooking",
    "rare_loot", "create", "quark", "botania", "society",
    "miscellaneous_new"
]

for cat in CATEGORY_ORDER:
    if cat in new_categories:
        if cat in output:
            output[cat].update(new_categories[cat])
            output[cat] = dict(sorted(output[cat].items()))
        else:
            output[cat] = dict(sorted(new_categories[cat].items()))

for cat, vals in sorted(new_categories.items()):
    if cat not in output:
        output[cat] = dict(sorted(vals.items()))

# ═══════════════════════════════════════════════════════════════════════
# 14. WRITE OUTPUT
# ═══════════════════════════════════════════════════════════════════════
with open(CONFIG_PATH, "w", encoding="utf-8") as f:
    json.dump(output, f, indent=2, ensure_ascii=False)

total_priced = sum(len(v) for v in output.values() if isinstance(v, dict))

print(f"\nFinal JSON written to {CONFIG_PATH}")
print(f"Total priced items: {total_priced}")
print(f"  Previously priced: {len(existing_prices)}")
print(f"  Newly priced:      {len(new_prices)}")
print(f"  Omitted:           {len(omitted)}")

print(f"\nNew items by resolution method:")
for reason, count in sorted(category_counts.items(), key=lambda x: -x[1]):
    print(f"  {reason}: {count}")

print(f"\nOmitted items by reason:")
for reason, count in sorted(omitted_by_reason.items(), key=lambda x: -x[1]):
    print(f"  {reason}: {count}")


