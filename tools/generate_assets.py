#!/usr/bin/env python3
"""Rebuild original StateCraft resources using only the Python standard library."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import zlib


ROOT = Path(__file__).resolve().parents[1]
CORE_ASSETS = Path("statecraft", "src", "main", "resources", "assets", "statecraft")
ECONOMY_RESOURCES = Path("statecraft-economy", "src", "main", "resources")
ASSETS = ECONOMY_RESOURCES / "assets" / "statecraft_economy"
DATA = ECONOMY_RESOURCES / "data"
NS = "statecraft_economy"
BLOCKS = ("atm", "trading_hub", "marketplace", "company_vault", "stock_market")
DENOMINATIONS = (1, 10, 100, 1_000, 10_000, 100_000, 1_000_000)
CURRENCIES = tuple(f"currency_{amount}" for amount in DENOMINATIONS)
UTILITIES = ("bank_card", "recipe_guide")
ITEMS = CURRENCIES + UTILITIES
MOD_ITEMS = {f"{NS}:{name}" for name in BLOCKS + ITEMS}
PROFESSIONS = (
    "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
    "fletcher", "leatherworker", "librarian", "mason", "shepherd",
    "toolsmith", "weaponsmith",
)
MERCHANTS = (
    "banker", "bard", "barkeeper", "botanist", "market", "baker",
    "winemaker", "storage_smith", "ribbit",
)
WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry")

# A closed, reviewed 1.20.1 registry subset keeps typos and optional-mod IDs out.
VANILLA_NAMES = {
    "amethyst_block", "amethyst_shard", "andesite", "apple", "arrow",
    "baked_potato", "bamboo", "barrel", "beef", "beetroot", "beetroot_seeds",
    "blaze_powder", "blaze_rod", "bone", "bone_block", "bone_meal", "book",
    "bookshelf", "bowl", "bow", "bread", "brick", "bricks", "brown_mushroom",
    "cactus", "cake", "carrot", "charcoal", "chest", "chicken", "clay",
    "clay_ball", "coal", "coal_block", "cobbled_deepslate", "cobblestone",
    "cocoa_beans", "cod", "comparator", "compass", "cooked_beef",
    "cooked_chicken", "cooked_cod", "cooked_mutton", "cooked_porkchop",
    "cooked_rabbit", "cooked_salmon", "copper_block", "copper_ingot",
    "dandelion", "diamond", "diamond_block", "diamond_boots", "diorite",
    "dirt", "dried_kelp", "dried_kelp_block", "egg", "emerald",
    "emerald_block", "ender_pearl", "feather", "fishing_rod", "flint",
    "flower_pot", "glass", "glass_bottle", "glow_berries", "glow_ink_sac",
    "gold_block", "gold_ingot", "gold_nugget", "granite", "gravel",
    "gunpowder", "hay_block", "honey_bottle", "hopper", "ink_sac",
    "iron_axe", "iron_block", "iron_chestplate", "iron_ingot", "iron_nugget",
    "iron_pickaxe", "iron_shovel", "iron_sword", "jukebox", "kelp",
    "lapis_block", "lapis_lazuli", "leather", "leather_helmet", "lily_pad",
    "map", "melon", "melon_slice", "mushroom_stew", "mutton", "nether_wart",
    "netherrack", "netherite_ingot", "netherite_scrap", "note_block",
    "obsidian", "paper", "polished_andesite", "porkchop", "potato",
    "pumpkin", "pumpkin_pie", "quartz", "quartz_block", "rabbit",
    "rabbit_hide", "raw_copper", "raw_copper_block", "raw_gold",
    "raw_gold_block", "raw_iron", "raw_iron_block", "red_mushroom",
    "redstone", "redstone_block", "rotten_flesh", "saddle", "salmon",
    "sand", "sandstone", "sea_pickle", "seagrass", "shears", "shulker_box",
    "slime_ball", "slime_block", "spider_eye", "stick", "stone", "string",
    "sugar", "sugar_cane", "sweet_berries", "torch", "wheat", "wheat_seeds",
    "white_carpet", "white_wool",
}
VANILLA_NAMES.update(f"{wood}_{part}" for wood in WOODS for part in ("log", "planks", "sapling"))
# Mangrove propagules, not saplings, are registered in vanilla.
VANILLA_NAMES.remove("mangrove_sapling")
VANILLA_NAMES.add("mangrove_propagule")
VANILLA_ITEMS = {f"minecraft:{name}" for name in VANILLA_NAMES}
KNOWN_ITEMS = VANILLA_ITEMS | MOD_ITEMS
UNSTACKABLE = {
    "bow", "diamond_boots", "fishing_rod", "iron_axe", "iron_chestplate",
    "iron_pickaxe", "iron_shovel", "iron_sword", "leather_helmet",
    "mushroom_stew", "saddle", "shears", "shulker_box",
}

PRICES = {
    "wheat": 25,
    "hay_block": 225,
    "carrot": 20,
    "potato": 20,
    "baked_potato": 20,
    "beetroot": 20,
    "melon_slice": 5,
    "melon": 45,
    "pumpkin": 40,
    "pumpkin_pie": 50,
    "sugar_cane": 15,
    "sugar": 10,
    "bamboo": 2,
    "cactus": 8,
    "kelp": 3,
    "dried_kelp": 3,
    "dried_kelp_block": 27,
    "sweet_berries": 10,
    "glow_berries": 20,
    "apple": 20,
    "brown_mushroom": 15,
    "red_mushroom": 15,
    "cocoa_beans": 20,
    "nether_wart": 35,
    "wheat_seeds": 2,
    "beetroot_seeds": 2,
    "bread": 40,
    "beef": 25,
    "cooked_beef": 25,
    "chicken": 20,
    "cooked_chicken": 20,
    "porkchop": 25,
    "cooked_porkchop": 25,
    "mutton": 20,
    "cooked_mutton": 20,
    "rabbit": 30,
    "cooked_rabbit": 30,
    "cod": 15,
    "cooked_cod": 15,
    "salmon": 20,
    "cooked_salmon": 20,
    "egg": 5,
    "honey_bottle": 50,
    "leather": 50,
    "rabbit_hide": 15,
    "string": 10,
    "white_wool": 35,
    "white_carpet": 20,
    "feather": 10,
    "ink_sac": 15,
    "glow_ink_sac": 25,
    "rotten_flesh": 2,
    "bone": 8,
    "bone_meal": 2,
    "bone_block": 18,
    "gunpowder": 15,
    "spider_eye": 8,
    "slime_ball": 25,
    "slime_block": 225,
    "ender_pearl": 125,
    "blaze_rod": 125,
    "blaze_powder": 60,
    "cobblestone": 1,
    "cobbled_deepslate": 1,
    "stone": 1,
    "granite": 1,
    "diorite": 1,
    "andesite": 1,
    "polished_andesite": 1,
    "gravel": 1,
    "sand": 1,
    "sandstone": 4,
    "dirt": 1,
    "netherrack": 1,
    "clay_ball": 4,
    "clay": 16,
    "brick": 4,
    "bricks": 16,
    "flint": 5,
    "obsidian": 50,
    "coal": 20,
    "coal_block": 180,
    "charcoal": 15,
    "raw_iron": 45,
    "raw_iron_block": 405,
    "iron_ingot": 45,
    "iron_nugget": 5,
    "iron_block": 405,
    "raw_gold": 90,
    "raw_gold_block": 810,
    "gold_ingot": 90,
    "gold_nugget": 10,
    "gold_block": 810,
    "raw_copper": 9,
    "raw_copper_block": 81,
    "copper_ingot": 9,
    "copper_block": 81,
    "redstone": 10,
    "redstone_block": 90,
    "lapis_lazuli": 10,
    "lapis_block": 90,
    "quartz": 30,
    "quartz_block": 120,
    "amethyst_shard": 20,
    "amethyst_block": 80,
    "emerald": 200,
    "emerald_block": 1_800,
    "diamond": 500,
    "diamond_block": 4_500,
    "netherite_scrap": 2_500,
    "netherite_ingot": 10_000,
    "stick": 2,
    "paper": 10,
    "torch": 4,
    "chest": 30,
    "glass": 1,
    "glass_bottle": 1,
    "lily_pad": 5,
    "sea_pickle": 10,
    "seagrass": 2,
}
for wood in WOODS:
    PRICES[f"{wood}_log"] = 20
    PRICES[f"{wood}_planks"] = 5
    PRICES[f"{wood}_sapling" if wood != "mangrove" else "mangrove_propagule"] = 10

RECIPES = {
    "atm": {
        "pattern": ["IGI", "IRI", "ICI"],
        "key": {"I": "iron_ingot", "G": "glass", "R": "redstone", "C": "chest"},
        "unlock": "iron_ingot",
    },
    "trading_hub": {
        "pattern": ["IPI", "RCR", "III"],
        "key": {"I": "iron_ingot", "P": "paper", "R": "redstone", "C": "chest"},
        "unlock": "iron_ingot",
    },
    "marketplace": {
        "pattern": ["PPP", "GCG", "III"],
        "key": {"P": "oak_planks", "G": "gold_nugget", "C": "chest", "I": "iron_ingot"},
        "unlock": "chest",
    },
    "company_vault": {
        "pattern": ["III", "ICI", "IRI"],
        "key": {"I": "iron_ingot", "C": "chest", "R": "redstone"},
        "unlock": "iron_ingot",
    },
    "stock_market": {
        "pattern": ["IGI", "RCR", "IPI"],
        "key": {
            "I": "iron_ingot", "G": "glass", "R": "redstone",
            "C": "comparator", "P": "paper",
        },
        "unlock": "comparator",
    },
    "bank_card": {
        "pattern": ["PG", "IR"],
        "key": {"P": "paper", "G": "gold_nugget", "I": "iron_ingot", "R": "redstone"},
        "unlock": "paper",
    },
    "recipe_guide": {
        "ingredients": ["book", "paper"],
        "unlock": "book",
    },
}


def item_id(name: str) -> str:
    if ":" in name:
        return name
    return f"{NS}:{name}" if name in BLOCKS + ITEMS else f"minecraft:{name}"


def stack(name: str, count: int = 1) -> dict:
    return {"item": item_id(name), "count": count}


def offer(level: int, buy: tuple, sell: tuple, *, second: tuple | None = None,
          uses: int = 16, xp: int | None = None, multiplier: float = 0.05) -> dict:
    result = {
        "level": level,
        "buy": stack(*buy),
        "sell": stack(*sell),
        "maxUses": uses,
        "xp": (2, 5, 10, 15, 30)[level - 1] if xp is None else xp,
        "priceMultiplier": multiplier,
    }
    if second is not None:
        result["secondBuy"] = stack(*second)
    return result


TRADES = {
    "armorer": [
        offer(1, ("coal", 5), ("currency_1", 1)),
        offer(2, ("currency_10", 1), ("iron_chestplate", 1), uses=8),
        offer(3, ("currency_10", 2), ("diamond_boots", 1), uses=4),
    ],
    "butcher": [
        offer(1, ("beef", 4), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("cooked_beef", 3), uses=12),
        offer(3, ("currency_1", 2), ("cooked_chicken", 3), uses=12),
    ],
    "cartographer": [
        offer(1, ("paper", 12), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("map", 1), uses=12),
        offer(3, ("currency_1", 4), ("compass", 1), uses=8),
    ],
    "cleric": [
        offer(1, ("rotten_flesh", 50), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("lapis_lazuli", 6), uses=12),
        offer(3, ("currency_10", 1), ("ender_pearl", 3), uses=8),
    ],
    "farmer": [
        offer(1, ("wheat", 4), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("bread", 2), uses=12),
        offer(3, ("currency_1", 3), ("pumpkin_pie", 2), uses=12),
    ],
    "fisherman": [
        offer(1, ("cod", 8), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("cooked_cod", 4), uses=12),
        offer(3, ("currency_1", 5), ("fishing_rod", 1), uses=8),
    ],
    "fletcher": [
        offer(1, ("stick", 64), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("arrow", 12), uses=12),
        offer(3, ("currency_1", 8), ("bow", 1), uses=8),
    ],
    "leatherworker": [
        offer(1, ("leather", 2), ("currency_1", 1)),
        offer(2, ("currency_1", 4), ("leather_helmet", 1), uses=8),
        offer(3, ("currency_10", 1), ("saddle", 1), uses=4),
    ],
    "librarian": [
        offer(1, ("paper", 12), ("currency_1", 1)),
        offer(2, ("currency_1", 4), ("book", 2), uses=12),
        offer(3, ("currency_1", 6), ("bookshelf", 1), second=("book", 1), uses=8),
    ],
    "mason": [
        offer(1, ("clay_ball", 32), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("bricks", 4), uses=12),
        offer(3, ("currency_1", 3), ("polished_andesite", 16), uses=12),
    ],
    "shepherd": [
        offer(1, ("white_wool", 3), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("white_carpet", 4), uses=12),
        offer(3, ("currency_1", 3), ("shears", 1), uses=8),
    ],
    "toolsmith": [
        offer(1, ("raw_iron", 3), ("currency_1", 1)),
        offer(2, ("currency_1", 4), ("iron_pickaxe", 1), uses=8),
        offer(3, ("currency_1", 3), ("iron_shovel", 1), uses=8),
    ],
    "weaponsmith": [
        offer(1, ("iron_ingot", 3), ("currency_1", 1)),
        offer(2, ("currency_1", 4), ("iron_sword", 1), uses=8),
        offer(3, ("currency_1", 5), ("iron_axe", 1), uses=8),
    ],
    "banker": [
        exchange
        for lower, higher in zip(CURRENCIES, CURRENCIES[1:])
        for exchange in (
            offer(1, (lower, 10), (higher, 1), uses=64, xp=0, multiplier=0.0),
            offer(1, (higher, 1), (lower, 10), uses=64, xp=0, multiplier=0.0),
        )
    ],
    "bard": [
        offer(1, ("string", 12), ("currency_1", 1)),
        offer(2, ("currency_1", 4), ("note_block", 1), uses=12),
        offer(3, ("currency_10", 1), ("jukebox", 1), uses=4),
    ],
    "barkeeper": [
        offer(1, ("currency_1", 1), ("glass_bottle", 3), uses=16),
        offer(2, ("currency_1", 2), ("honey_bottle", 1), uses=12),
        offer(3, ("currency_1", 2), ("mushroom_stew", 1), second=("bowl", 1), uses=12),
    ],
    "botanist": [
        offer(1, ("wheat_seeds", 50), ("currency_1", 1)),
        offer(2, ("currency_1", 1), ("dandelion", 4), uses=12),
        offer(3, ("currency_1", 2), ("flower_pot", 2), uses=12),
    ],
    "market": [
        offer(1, ("currency_1", 1), ("oak_sapling", 4), uses=16),
        offer(2, ("currency_1", 2), ("torch", 16), uses=12),
        offer(3, ("currency_1", 2), ("chest", 1), uses=12),
    ],
    "baker": [
        offer(1, ("wheat", 4), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("bread", 2), uses=12),
        offer(3, ("currency_1", 2), ("cake", 1), second=("egg", 1), uses=8),
    ],
    "winemaker": [
        offer(1, ("sweet_berries", 12), ("currency_1", 1)),
        offer(2, ("currency_1", 2), ("glow_berries", 4), uses=12),
        offer(3, ("currency_1", 3), ("honey_bottle", 1), second=("glass_bottle", 1), uses=12),
    ],
    "storage_smith": [
        offer(1, ("currency_1", 2), ("barrel", 1), uses=12),
        offer(2, ("currency_1", 3), ("hopper", 1), uses=8),
        offer(3, ("currency_10", 2), ("shulker_box", 1), second=("chest", 1), uses=4),
    ],
    "ribbit": [
        offer(1, ("currency_1", 1), ("lily_pad", 4), uses=16),
        offer(2, ("currency_1", 1), ("slime_ball", 2), uses=12),
        offer(3, ("currency_1", 2), ("sea_pickle", 4), second=("kelp", 4), uses=12),
    ],
}

SMALL_FONT = {
    "A": ("010", "101", "111", "101", "101"),
    "B": ("110", "101", "110", "101", "110"),
    "C": ("111", "100", "100", "100", "111"),
    "D": ("110", "101", "101", "101", "110"),
    "E": ("111", "100", "110", "100", "111"),
    "H": ("101", "101", "111", "101", "101"),
    "M": ("101", "111", "111", "101", "101"),
    "N": ("101", "111", "111", "111", "101"),
    "O": ("111", "101", "101", "101", "111"),
    "R": ("110", "101", "110", "101", "101"),
    "S": ("111", "100", "111", "001", "111"),
    "T": ("111", "010", "010", "010", "010"),
    "U": ("101", "101", "101", "101", "111"),
    "V": ("101", "101", "101", "101", "010"),
    "X": ("101", "101", "010", "101", "101"),
}
VALUE_FONT = {
    "0": ("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    "1": ("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    "2": ("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    "3": ("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
    "4": ("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    "5": ("11111", "10000", "10000", "11110", "00001", "00001", "11110"),
    "6": ("01110", "10000", "10000", "11110", "10001", "10001", "01110"),
    "7": ("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    "8": ("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    "9": ("01110", "10001", "10001", "01111", "00001", "00001", "01110"),
    "K": ("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    "M": ("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    "$": ("00100", "01111", "10100", "01110", "00101", "11110", "00100"),
}


def rgba(color: str | tuple) -> tuple[int, int, int, int]:
    if isinstance(color, tuple):
        return color if len(color) == 4 else (*color, 255)
    color = color.lstrip("#")
    return tuple(int(color[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(color: str | tuple, delta: int) -> tuple[int, int, int, int]:
    r, g, b, a = rgba(color)
    return (max(0, min(255, r + delta)), max(0, min(255, g + delta)),
            max(0, min(255, b + delta)), a)


class Canvas:
    def __init__(self, color: str | tuple = (0, 0, 0, 0), size: int = 32):
        self.size = size
        self.pixels = [rgba(color)] * (size * size)

    def pixel(self, x: int, y: int, color: str | tuple) -> None:
        if 0 <= x < self.size and 0 <= y < self.size:
            self.pixels[y * self.size + x] = rgba(color)

    def rect(self, x0: int, y0: int, x1: int, y1: int, color: str | tuple) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.pixel(x, y, color)

    def frame(self, x0: int, y0: int, x1: int, y1: int, color: str | tuple) -> None:
        self.rect(x0, y0, x1, y0, color)
        self.rect(x0, y1, x1, y1, color)
        self.rect(x0, y0, x0, y1, color)
        self.rect(x1, y0, x1, y1, color)

    def line(self, x0: int, y0: int, x1: int, y1: int, color: str | tuple) -> None:
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        error = dx + dy
        while True:
            self.pixel(x0, y0, color)
            if x0 == x1 and y0 == y1:
                break
            twice = 2 * error
            if twice >= dy:
                error += dy
                x0 += sx
            if twice <= dx:
                error += dx
                y0 += sy

    def disk(self, x: int, y: int, radius: int, color: str | tuple) -> None:
        for yy in range(y - radius, y + radius + 1):
            for xx in range(x - radius, x + radius + 1):
                if (xx - x) ** 2 + (yy - y) ** 2 <= radius ** 2:
                    self.pixel(xx, yy, color)

    def polygon(self, points: tuple, color: str | tuple) -> None:
        for y in range(min(p[1] for p in points), max(p[1] for p in points) + 1):
            intersections = []
            for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]):
                if y0 <= y < y1 or y1 <= y < y0:
                    intersections.append(x0 + (y - y0) * (x1 - x0) / (y1 - y0))
            intersections.sort()
            for left, right in zip(intersections[::2], intersections[1::2]):
                self.rect(math.ceil(left), y, math.floor(right), y, color)
        for start, end in zip(points, points[1:] + points[:1]):
            self.line(*start, *end, color)

    def text(self, text: str, x: int, y: int, color: str | tuple,
             font: dict = SMALL_FONT, scale: int = 1, centered: bool = False) -> None:
        width = len(next(iter(font.values()))[0])
        if centered:
            x -= ((width + 1) * len(text) * scale - scale) // 2
        for char in text:
            for yy, row in enumerate(font[char]):
                for xx, value in enumerate(row):
                    if value == "1":
                        self.rect(x + xx * scale, y + yy * scale,
                                  x + (xx + 1) * scale - 1, y + (yy + 1) * scale - 1, color)
            x += (width + 1) * scale

    def png(self) -> bytes:
        rows = bytearray()
        for y in range(self.size):
            rows.append(0)
            for pixel in self.pixels[y * self.size:(y + 1) * self.size]:
                rows.extend(pixel)

        def chunk(kind: bytes, payload: bytes) -> bytes:
            return (struct.pack(">I", len(payload)) + kind + payload
                    + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF))

        header = struct.pack(">IIBBBBB", self.size, self.size, 8, 6, 0, 0, 0)
        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b""))


PALETTES = {
    "atm": ("30465f", "152236", "87acc2", "65e4c0"),
    "trading_hub": ("326467", "183439", "a8c2ad", "e9bd64"),
    "marketplace": ("805240", "392e31", "c99e6c", "e4bc72"),
    "company_vault": ("485663", "202b38", "98aab0", "ddb96a"),
    "stock_market": ("283c59", "111f32", "7292b4", "f2ce75"),
}


def metal_case(name: str) -> Canvas:
    base, dark, light, _ = PALETTES[name]
    canvas = Canvas(base)
    canvas.frame(0, 0, 31, 31, dark)
    canvas.line(1, 1, 30, 1, light)
    canvas.line(1, 1, 1, 30, shade(base, 25))
    canvas.line(30, 2, 30, 30, shade(base, -15))
    canvas.line(2, 30, 29, 30, shade(base, -20))
    # Sparse fixed stippling suggests brushed metal without obscuring the symbols.
    for y in range(3, 29):
        for x in range(3, 29):
            if (x * 13 + y * 7) % 71 == 0:
                canvas.pixel(x, y, shade(base, 7))
    for x, y in ((2, 2), (29, 2), (2, 29), (29, 29)):
        canvas.pixel(x, y, light)
        canvas.pixel(x, y + 1, dark)
    return canvas


def arrow(canvas: Canvas, y: int, right: bool, color: str) -> None:
    canvas.rect(8, y, 23, y + 1, color)
    x = 23 if right else 8
    tip = x + 3 if right else x - 3
    canvas.line(x, y - 3, tip, y, color)
    canvas.line(x, y + 4, tip, y + 1, color)


def block_front(name: str) -> Canvas:
    c = metal_case(name)
    _, dark, light, accent = PALETTES[name]
    if name == "atm":
        c.rect(3, 3, 28, 16, dark)
        c.frame(4, 4, 27, 15, light)
        c.rect(5, 5, 26, 14, "173d40")
        c.text("ATM", 16, 7, "acf7d7", centered=True)
        c.line(7, 13, 24, 13, "387e7e")
        c.rect(4, 19, 16, 21, dark)
        c.line(5, 19, 15, 19, accent)
        c.rect(4, 24, 17, 27, dark)
        c.line(6, 25, 15, 25, "b7d9c8")
        c.line(6, 26, 15, 26, "3c766d")
        for y in (19, 22, 25):
            for x in (20, 23, 26):
                c.rect(x, y, x + 1, y + 1, light)
        c.rect(26, 25, 27, 26, accent)
    elif name == "trading_hub":
        c.rect(3, 4, 28, 13, dark)
        c.frame(4, 5, 27, 12, "588b89")
        c.text("TRADE", 16, 6, "d3eee1", centered=True)
        arrow(c, 17, True, accent)
        arrow(c, 23, False, "89dbba")
        c.line(5, 28, 26, 28, dark)
        c.line(6, 29, 25, 29, light)
    elif name == "marketplace":
        c.rect(3, 4, 28, 27, dark)
        c.rect(5, 11, 26, 22, "4b6661")
        for x in range(4, 28, 5):
            stripe = "f0dca4" if ((x - 4) // 5) % 2 == 0 else "c76550"
            c.rect(x, 4, min(x + 4, 27), 10, stripe)
            c.rect(x + 1, 11, min(x + 3, 27), 12, shade(stripe, -12))
            c.line(x, 5, min(x + 4, 27), 5, shade(stripe, 14))
        c.rect(4, 13, 5, 25, accent)
        c.rect(26, 13, 27, 25, accent)
        c.disk(10, 19, 3, "9abd69")
        c.pixel(10, 15, "dfba74")
        c.pixel(9, 17, "d2e48f")
        c.disk(20, 19, 3, "d67a57")
        c.pixel(20, 15, "94b978")
        c.pixel(19, 17, "f1b17b")
        c.rect(5, 23, 26, 27, "a7734c")
        c.line(5, 23, 26, 23, "e2b777")
        c.line(7, 26, 24, 26, "81583e")
        c.line(15, 24, 15, 27, "81583e")
    elif name == "company_vault":
        c.frame(4, 4, 27, 27, dark)
        c.frame(5, 5, 26, 26, light)
        c.disk(16, 16, 10, dark)
        c.disk(16, 16, 9, accent)
        c.disk(16, 16, 8, "72818a")
        c.disk(16, 16, 6, "394c5c")
        for x0, y0, x1, y1 in ((10, 10, 22, 22), (10, 22, 22, 10), (8, 16, 24, 16)):
            c.line(x0, y0, x1, y1, accent)
        c.disk(16, 16, 3, dark)
        c.disk(16, 16, 2, light)
        c.pixel(15, 15, "e3e6d3")
        c.rect(3, 8, 5, 12, accent)
        c.rect(3, 21, 5, 25, accent)
        c.rect(27, 13, 28, 19, dark)
        c.line(28, 14, 28, 18, accent)
    else:
        c.rect(3, 3, 28, 25, dark)
        c.frame(4, 4, 27, 24, light)
        c.rect(5, 5, 26, 23, "122e39")
        for y in (9, 15, 21):
            c.line(6, y, 25, y, "274753")
        for x in (10, 16, 22):
            c.line(x, 6, x, 22, "274753")
        for x, bottom, top in ((7, 21, 16), (11, 20, 14), (15, 21, 16), (19, 16, 10), (23, 13, 7)):
            c.line(x, top - 1, x, bottom, "427566")
            c.rect(x, top, x + 1, bottom - 1, "64ad87")
        points = ((6, 20), (11, 16), (15, 18), (20, 11), (25, 7))
        for start, end in zip(points, points[1:]):
            c.line(*start, *end, "b5e9a3")
        c.line(22, 7, 25, 7, "b5e9a3")
        c.line(25, 7, 25, 10, "b5e9a3")
        c.text("SCX", 16, 26, accent, centered=True)
    return c


def block_side(name: str) -> Canvas:
    c = metal_case(name)
    base, dark, light, accent = PALETTES[name]
    c.frame(4, 4, 27, 27, dark)
    c.line(5, 5, 26, 5, shade(base, 20))
    if name == "marketplace":
        for x in range(4, 28, 5):
            color = "f0dca4" if ((x - 4) // 5) % 2 == 0 else "c76550"
            c.rect(x, 4, min(x + 4, 27), 10, color)
        for y in (13, 17, 21, 25):
            c.line(6, y, 25, y, "b37f54")
            c.line(6, y + 1, 25, y + 1, "624639")
        c.line(8, 11, 8, 26, accent)
        c.line(23, 11, 23, 26, accent)
    elif name == "company_vault":
        c.line(6, 7, 24, 25, shade(base, 18))
        c.line(7, 6, 25, 24, dark)
        c.line(6, 24, 24, 6, shade(base, 18))
        c.line(7, 25, 25, 7, dark)
        for x, y in ((7, 7), (24, 7), (7, 24), (24, 24)):
            c.rect(x, y, x + 1, y + 1, accent)
        c.frame(13, 13, 18, 18, accent)
    elif name == "trading_hub":
        c.rect(6, 8, 25, 21, shade(base, -10))
        arrow(c, 11, True, accent)
        arrow(c, 18, False, light)
        c.line(6, 25, 25, 25, accent)
    else:
        c.rect(10, 9, 21, 16, shade(base, -10))
        c.text("SC" if name == "atm" else "EX", 16, 10, accent, centered=True)
        for y in (20, 23, 26):
            c.line(8, y, 23, y, dark)
            c.line(8, y + 1, 23, y + 1, shade(base, 14))
    return c


def block_top(name: str) -> Canvas:
    c = metal_case(name)
    base, dark, light, accent = PALETTES[name]
    c.frame(4, 4, 27, 27, dark)
    c.frame(5, 5, 26, 26, shade(base, 18))
    if name == "marketplace":
        for x in range(5, 27):
            color = "f0dca4" if ((x - 5) // 4) % 2 == 0 else "c76550"
            c.line(x, 5, x, 26, color)
            c.line(x, 14, x, 17, shade(color, 12))
        c.line(4, 4, 27, 4, accent)
        c.line(4, 27, 27, 27, accent)
    elif name == "trading_hub":
        arrow(c, 11, True, accent)
        arrow(c, 19, False, light)
    elif name == "company_vault":
        c.frame(9, 10, 22, 21, light)
        c.frame(10, 11, 21, 20, dark)
        c.rect(12, 13, 19, 18, base)
        c.line(12, 12, 19, 12, accent)
        for x in (7, 24):
            c.rect(x, 7, x + 1, 8, accent)
            c.rect(x, 23, x + 1, 24, accent)
    else:
        c.rect(9, 9, 22, 21, dark)
        for x in (11, 14, 17, 20):
            c.line(x, 11, x, 19, light)
            c.line(x + 1, 11, x + 1, 19, shade(base, -8))
        c.rect(13, 24, 18, 25, accent)
    return c


CASH_PALETTES = (
    ("77b995", "203f37", "d8f3ca", "1"),
    ("86bbdb", "263e60", "d6f3ef", "10"),
    ("e4c87e", "694329", "fff3c3", "100"),
    ("b89bd1", "452c61", "f1ddff", "1K"),
    ("e2a184", "683735", "ffe5c6", "10K"),
    ("8d9fdd", "303462", "e6e6ff", "100K"),
    ("dfd7a2", "4c4930", "fffbdc", "1M"),
)


def currency_texture(rank: int) -> Canvas:
    base, dark, light, label = CASH_PALETTES[rank]
    c = Canvas()
    c.rect(3, 8, 30, 27, (10, 18, 24, 80))
    c.rect(2, 6, 29, 25, dark)
    c.rect(1, 8, 30, 23, dark)
    c.rect(3, 7, 28, 24, base)
    c.rect(2, 9, 29, 22, base)
    c.line(4, 7, 27, 7, light)
    c.line(3, 8, 3, 23, light)
    c.line(4, 24, 27, 24, shade(base, -28))
    c.rect(5, 11, 26, 20, dark)
    if len(label) == 4:
        c.rect(3, 11, 28, 20, dark)
    c.text(label, 16, 12, light, font=VALUE_FONT, centered=True)
    for x in range(7, 26, 3):
        c.pixel(x, 9, dark)
    c.rect(4, 8, 5, 9, light)
    c.rect(27, 8, 28, 9, light)
    # Tactile-style rank marks distinguish notes without relying on hue.
    start = 16 - ((rank + 1) * 3 - 1) // 2
    for mark in range(rank + 1):
        c.rect(start + mark * 3, 22, start + mark * 3 + 1, 23, dark)
    return c


def bank_card_texture() -> Canvas:
    c = Canvas()
    c.rect(4, 9, 30, 26, (10, 18, 24, 80))
    c.rect(3, 7, 28, 24, "172e45")
    c.rect(2, 9, 29, 22, "172e45")
    c.rect(4, 8, 27, 23, "335b73")
    c.rect(3, 10, 28, 21, "335b73")
    c.line(4, 8, 27, 8, "91c4cc")
    c.rect(4, 10, 27, 11, "dcbc75")
    c.rect(6, 14, 12, 19, "d8b571")
    c.frame(6, 14, 12, 19, "efd99b")
    c.line(8, 15, 8, 18, "90764b")
    c.line(10, 15, 10, 18, "90764b")
    c.line(6, 17, 12, 17, "90764b")
    c.text("SC", 21, 14, "dce8d6", centered=True)
    for x in (6, 12, 18, 24):
        c.line(x, 22, x + 2, 22, "a1d3cb")
    return c


def guide_texture() -> Canvas:
    c = Canvas()
    c.rect(7, 5, 27, 29, (10, 18, 24, 80))
    c.rect(6, 4, 26, 29, "24323e")
    c.rect(8, 24, 25, 28, "e7dcc0")
    c.line(9, 26, 25, 26, "b0a489")
    c.line(9, 28, 24, 28, "c7b898")
    c.rect(5, 3, 25, 25, "214f58")
    c.rect(4, 5, 7, 24, "b38251")
    c.line(5, 5, 5, 23, "e6bf75")
    c.line(8, 4, 24, 4, "72a7a2")
    c.frame(9, 6, 23, 22, "d2b273")
    for y in (9, 13, 17):
        for x in (11, 15, 19):
            c.rect(x, y, x + 2, y + 2, "cce0c5")
            c.pixel(x + 2, y + 2, "7caaa0")
    c.rect(15, 13, 17, 15, "e7c578")
    c.rect(20, 23, 22, 30, "bf6554")
    c.pixel(21, 30, (0, 0, 0, 0))
    c.line(20, 23, 20, 29, "e0936c")
    return c


def core_emblem() -> Canvas:
    c = Canvas()
    c.polygon(((5, 3), (26, 3), (26, 19), (23, 25), (16, 30), (8, 25), (5, 19)), "21384c")
    c.polygon(((7, 5), (24, 5), (24, 19), (21, 24), (16, 27), (10, 23), (7, 19)), "d8b96b")
    c.polygon(((8, 6), (23, 6), (23, 18), (20, 23), (16, 25), (11, 22), (8, 18)), "34726f")
    c.polygon(((10, 11), (16, 7), (22, 11)), "f0dfb0")
    c.line(10, 12, 22, 12, "a7834f")
    for x in (11, 15, 19):
        c.rect(x, 14, x + 1, 19, "f0dfb0")
        c.line(x + 2, 14, x + 2, 19, "84a596")
    c.rect(10, 20, 22, 21, "f0dfb0")
    return c


def economy_emblem() -> Canvas:
    c = Canvas()
    c.disk(16, 17, 14, (10, 18, 24, 80))
    c.disk(15, 15, 14, "664c35")
    c.disk(15, 15, 13, "ddb969")
    c.disk(15, 15, 11, "f2d897")
    c.disk(15, 15, 10, "41685f")
    c.disk(15, 15, 8, "284a46")
    c.text("$", 15, 12, "f5e0a4", font=VALUE_FONT, centered=True)
    for x, y in ((15, 3), (3, 15), (27, 15), (15, 27)):
        c.pixel(x, y, "fff0b8")
    return c


def generate() -> dict[Path, bytes]:
    outputs: dict[Path, bytes] = {}

    def add_json(path: Path, value: dict) -> None:
        outputs[path] = (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")

    def add_texture(path: Path, canvas: Canvas) -> None:
        outputs[path] = canvas.png()

    add_json(CORE_ASSETS / "lang" / "en_us.json", {
        "key.statecraft.menu": "Open StateCraft Menu",
        "key.statecraft.borders": "Cycle Territory Borders",
        "key.categories.statecraft": "StateCraft",
    })
    add_texture(CORE_ASSETS / "textures" / "gui" / "statecraft_emblem.png", core_emblem())
    language = {
        "itemGroup.statecraft_economy": "StateCraft Economy",
        "block.statecraft_economy.atm": "ATM",
        "block.statecraft_economy.trading_hub": "Trading Hub",
        "block.statecraft_economy.marketplace": "Marketplace",
        "block.statecraft_economy.company_vault": "Company Vault",
        "block.statecraft_economy.stock_market": "Stock Market",
        "item.statecraft_economy.bank_card": "Bank Card",
        "item.statecraft_economy.recipe_guide": "Economy Recipe Guide",
    }
    for amount in DENOMINATIONS:
        language[f"item.{NS}.currency_{amount}"] = f"${amount:,} Note"
    for merchant in MERCHANTS:
        language[f"merchant.{NS}.{merchant}"] = merchant.replace("_", " ").title()
    add_json(ASSETS / "lang" / "en_us.json", language)
    add_texture(ASSETS / "textures" / "gui" / "economy_emblem.png", economy_emblem())
    for name in BLOCKS:
        add_json(ASSETS / "blockstates" / f"{name}.json", {
            "variants": {"": {"model": f"{NS}:block/{name}"}},
        })
        add_json(ASSETS / "models" / "block" / f"{name}.json", {
            "parent": "minecraft:block/cube",
            "textures": {
                "particle": f"{NS}:block/{name}_front",
                "down": f"{NS}:block/{name}_side",
                "up": f"{NS}:block/{name}_top",
                "north": f"{NS}:block/{name}_front",
                "south": f"{NS}:block/{name}_side",
                "east": f"{NS}:block/{name}_side",
                "west": f"{NS}:block/{name}_side",
            },
        })
        add_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": f"{NS}:block/{name}"})
        for face, painter in (("front", block_front), ("side", block_side), ("top", block_top)):
            add_texture(ASSETS / "textures" / "block" / f"{name}_{face}.png", painter(name))
        add_json(DATA / NS / "loot_tables" / "blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1.0,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    for name in ITEMS:
        add_json(ASSETS / "models" / "item" / f"{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NS}:item/{name}"},
        })
    for rank, name in enumerate(CURRENCIES):
        add_texture(ASSETS / "textures" / "item" / f"{name}.png", currency_texture(rank))
    add_texture(ASSETS / "textures" / "item" / "bank_card.png", bank_card_texture())
    add_texture(ASSETS / "textures" / "item" / "recipe_guide.png", guide_texture())

    for name, spec in RECIPES.items():
        recipe = {"category": "misc", "group": NS, "result": stack(name)}
        if "pattern" in spec:
            recipe["type"] = "minecraft:crafting_shaped"
            recipe["pattern"] = spec["pattern"]
            recipe["key"] = {key: {"item": item_id(value)} for key, value in spec["key"].items()}
        else:
            recipe["type"] = "minecraft:crafting_shapeless"
            recipe["ingredients"] = [{"item": item_id(value)} for value in spec["ingredients"]]
        add_json(DATA / NS / "recipes" / f"{name}.json", recipe)
        add_json(DATA / NS / "advancements" / "recipes" / f"{name}.json", {
            "parent": "minecraft:recipes/root",
            "criteria": {
                "has_material": {
                    "trigger": "minecraft:inventory_changed",
                    "conditions": {"items": [{"items": [item_id(spec["unlock"])]}]},
                },
                "has_the_recipe": {
                    "trigger": "minecraft:recipe_unlocked",
                    "conditions": {"recipe": f"{NS}:{name}"},
                },
            },
            "requirements": [["has_material", "has_the_recipe"]],
            "rewards": {"recipes": [f"{NS}:{name}"]},
        })
    for relative in (Path("mineable", "pickaxe.json"), Path("needs_iron_tool.json")):
        add_json(DATA / "minecraft" / "tags" / "blocks" / relative, {
            "replace": False, "values": [f"{NS}:{name}" for name in BLOCKS],
        })
    add_json(DATA / NS / "default_item_values.json", {
        "prices": {item_id(name): value for name, value in PRICES.items()},
        "currencyItems": {f"{NS}:currency_{amount}": amount * 100 for amount in DENOMINATIONS},
    })
    add_json(DATA / NS / "trades" / "index.json", {
        "files": [f"{name}.json" for name in PROFESSIONS + MERCHANTS],
    })
    for name in PROFESSIONS + MERCHANTS:
        add_json(DATA / NS / "trades" / f"{name}.json", {
            "profession": f"minecraft:{name}" if name in PROFESSIONS else None,
            "merchant": name if name in MERCHANTS else None,
            "trades": TRADES[name],
        })
    return outputs


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def positive_int(value: object) -> bool:
    return type(value) is int and 0 < value <= 2 ** 63 - 1


def validate_png(data: bytes, name: Path) -> None:
    require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"Invalid PNG signature: {name}")
    offset = 8
    kinds, payloads = [], []
    while offset < len(data):
        require(offset + 12 <= len(data), f"Truncated PNG chunk: {name}")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        require(offset + 12 + length <= len(data), f"Truncated PNG payload: {name}")
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        require(crc == zlib.crc32(kind + payload) & 0xFFFFFFFF, f"PNG CRC mismatch: {name}")
        kinds.append(kind)
        payloads.append(payload)
        offset += length + 12
    require(kinds == [b"IHDR", b"IDAT", b"IEND"], f"Unexpected PNG chunks: {name}")
    width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", payloads[0])
    require((width, height) == (32, 32), f"Expected 32px texture: {name}")
    require((depth, color, compression, filtering, interlace) == (8, 6, 0, 0, 0),
            f"Unexpected PNG encoding: {name}")
    pixels = zlib.decompress(payloads[1])
    require(len(pixels) == height * (1 + width * 4), f"PNG pixel length mismatch: {name}")
    require(all(pixels[y * (1 + width * 4)] == 0 for y in range(height)),
            f"Unexpected PNG row filtering: {name}")
    if "block" in name.parts:
        require(all(pixels[y * 129 + 1 + x * 4 + 3] == 255 for y in range(32) for x in range(32)),
                f"Block textures must be opaque: {name}")
    else:
        alphas = {pixels[y * 129 + 1 + x * 4 + 3] for y in range(32) for x in range(32)}
        require(0 in alphas and 255 in alphas, f"Item/emblem transparency missing: {name}")


def validate_conversion_prices(prices: dict) -> int:
    conversions = [
        ({"wheat": 3}, {"bread": 1}),
        ({"pumpkin": 1, "sugar": 1, "egg": 1}, {"pumpkin_pie": 1}),
        ({"sugar_cane": 1}, {"sugar": 1}),
        ({"sugar_cane": 3}, {"paper": 3}),
        ({"rabbit_hide": 4}, {"leather": 1}),
        ({"string": 4}, {"white_wool": 1}),
        ({"white_wool": 2}, {"white_carpet": 3}),
        ({"bone": 1}, {"bone_meal": 3}),
        ({"blaze_rod": 1}, {"blaze_powder": 2}),
        ({"sand": 1}, {"glass": 1}),
        ({"glass": 3}, {"glass_bottle": 3}),
        ({"clay_ball": 1}, {"brick": 1}),
        ({"brick": 4}, {"bricks": 1}),
        ({"sand": 4}, {"sandstone": 1}),
        ({"quartz": 4}, {"quartz_block": 1}),
        ({"amethyst_shard": 4}, {"amethyst_block": 1}),
        ({"melon_slice": 9}, {"melon": 1}),
        ({"oak_planks": 2}, {"stick": 4}),
        ({"oak_planks": 8}, {"chest": 1}),
        ({"coal": 1, "stick": 1}, {"torch": 4}),
        ({"charcoal": 1, "stick": 1}, {"torch": 4}),
        ({"netherite_scrap": 4, "gold_ingot": 4}, {"netherite_ingot": 1}),
    ]
    for base, block, count in (
        ("wheat", "hay_block", 9), ("dried_kelp", "dried_kelp_block", 9),
        ("bone_meal", "bone_block", 9), ("slime_ball", "slime_block", 9),
        ("coal", "coal_block", 9), ("raw_iron", "raw_iron_block", 9),
        ("iron_ingot", "iron_block", 9), ("iron_nugget", "iron_ingot", 9),
        ("raw_gold", "raw_gold_block", 9), ("gold_ingot", "gold_block", 9),
        ("gold_nugget", "gold_ingot", 9), ("raw_copper", "raw_copper_block", 9),
        ("copper_ingot", "copper_block", 9), ("redstone", "redstone_block", 9),
        ("lapis_lazuli", "lapis_block", 9), ("emerald", "emerald_block", 9),
        ("diamond", "diamond_block", 9), ("clay_ball", "clay", 4),
    ):
        conversions.extend((({base: count}, {block: 1}), ({block: 1}, {base: count})))
    for raw, cooked in (
        ("beef", "cooked_beef"), ("chicken", "cooked_chicken"),
        ("porkchop", "cooked_porkchop"), ("mutton", "cooked_mutton"),
        ("rabbit", "cooked_rabbit"), ("cod", "cooked_cod"),
        ("salmon", "cooked_salmon"), ("potato", "baked_potato"),
        ("kelp", "dried_kelp"), ("raw_iron", "iron_ingot"),
        ("raw_gold", "gold_ingot"), ("raw_copper", "copper_ingot"),
        ("cobblestone", "stone"), ("andesite", "polished_andesite"),
    ):
        conversions.append(({raw: 1}, {cooked: 1}))
    for wood in WOODS:
        conversions.append(({f"{wood}_log": 1}, {f"{wood}_planks": 4}))
        conversions.append(({f"{wood}_log": 1}, {"charcoal": 1}))
    for inputs, results in conversions:
        before = sum(prices[item_id(name)] * count for name, count in inputs.items())
        after = sum(prices[item_id(name)] * count for name, count in results.items())
        require(after <= before, f"Priced crafting/smelting arbitrage: {inputs} -> {results}")
    return len(conversions)


def validate(outputs: dict[Path, bytes]) -> dict[str, int]:
    parsed = {}
    allowed = (CORE_ASSETS, ASSETS, DATA)
    for path, content in outputs.items():
        require(any(path.is_relative_to(prefix) for prefix in allowed), f"Outside owned paths: {path}")
        if path.suffix == ".json":
            parsed[path] = json.loads(content)
        elif path.suffix == ".png":
            validate_png(content, path)
        else:
            raise ValueError(f"Unsupported generated resource: {path}")
    textures = [content for path, content in outputs.items() if path.suffix == ".png"]
    require(len(textures) == len(set(textures)), "Two original textures are unexpectedly identical")

    def known_item(identifier: str) -> None:
        require(identifier in KNOWN_ITEMS, f"Unknown item ID: {identifier}")

    def model_reference(identifier: str) -> None:
        namespace, local = identifier.split(":", 1)
        if namespace == "minecraft":
            require(local in ("block/cube", "item/generated"), f"Unknown vanilla model: {identifier}")
        else:
            require(namespace == NS, f"Unknown model namespace: {identifier}")
            require(ASSETS / "models" / Path(*local.split("/")).with_suffix(".json") in outputs,
                    f"Missing model: {identifier}")

    for path, obj in parsed.items():
        if "models" in path.parts:
            model_reference(obj["parent"])
            for identifier in obj.get("textures", {}).values():
                namespace, local = identifier.split(":", 1)
                require(namespace == NS, f"Unexpected non-original texture: {identifier}")
                require(ASSETS / "textures" / Path(*local.split("/")).with_suffix(".png") in outputs,
                        f"Missing texture: {identifier}")
        if "blockstates" in path.parts:
            require(set(obj["variants"]) == {""}, f"Plain block cannot have facing-only variants: {path}")
            model_reference(obj["variants"][""]["model"])

    for name in BLOCKS + ITEMS:
        require(ASSETS / "models" / "item" / f"{name}.json" in parsed, f"Missing item model: {name}")
        prefix = "block" if name in BLOCKS else "item"
        require(f"{prefix}.{NS}.{name}" in parsed[ASSETS / "lang" / "en_us.json"],
                f"Missing translation: {name}")
    for name in BLOCKS:
        require(ASSETS / "blockstates" / f"{name}.json" in parsed, f"Missing blockstate: {name}")
        loot = parsed[DATA / NS / "loot_tables" / "blocks" / f"{name}.json"]
        require(loot["type"] == "minecraft:block", f"Incorrect loot table type: {name}")
        require(loot["pools"][0]["entries"] == [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                f"Incorrect block self-drop: {name}")
        require(loot["pools"][0]["conditions"] == [{"condition": "minecraft:survives_explosion"}],
                f"Missing explosion handling: {name}")
    for relative in (Path("mineable", "pickaxe.json"), Path("needs_iron_tool.json")):
        tag = parsed[DATA / "minecraft" / "tags" / "blocks" / relative]
        require(tag == {"replace": False, "values": [f"{NS}:{name}" for name in BLOCKS]},
                f"Incorrect pickaxe/tool tag: {relative}")
    core_language = parsed[CORE_ASSETS / "lang" / "en_us.json"]
    require(all(key in core_language for key in (
        "key.statecraft.menu", "key.statecraft.borders", "key.categories.statecraft",
    )), "Missing core keybind translation")

    for name in BLOCKS + UTILITIES:
        path = DATA / NS / "recipes" / f"{name}.json"
        require(path in parsed, f"Missing recipe: {name}")
        recipe = parsed[path]
        require(recipe["result"] == stack(name), f"Unexpected crafting result: {name}")
        if recipe["type"] == "minecraft:crafting_shaped":
            pattern = recipe["pattern"]
            require(1 <= len(pattern) <= 3 and 1 <= len(pattern[0]) <= 3,
                    f"Invalid shaped recipe size: {name}")
            require(all(len(row) == len(pattern[0]) for row in pattern), f"Uneven recipe: {name}")
            symbols = set("".join(pattern)) - {" "}
            require(symbols == set(recipe["key"]), f"Recipe key mismatch: {name}")
            ingredients = recipe["key"].values()
        else:
            require(recipe["type"] == "minecraft:crafting_shapeless", f"Unknown recipe type: {name}")
            ingredients = recipe["ingredients"]
            require(1 <= len(ingredients) <= 9, f"Invalid shapeless recipe size: {name}")
        for ingredient in ingredients:
            known_item(ingredient["item"])
        advancement = parsed[DATA / NS / "advancements" / "recipes" / f"{name}.json"]
        require(advancement["rewards"]["recipes"] == [f"{NS}:{name}"], f"Invalid recipe unlock: {name}")
        require(advancement["criteria"]["has_the_recipe"]["conditions"]["recipe"] == f"{NS}:{name}",
                f"Invalid recipe unlock condition: {name}")
        for ingredient in advancement["criteria"]["has_material"]["conditions"]["items"]:
            for identifier in ingredient["items"]:
                known_item(identifier)
    for path, recipe in parsed.items():
        if path.is_relative_to(DATA / NS / "recipes"):
            require(recipe["result"]["item"] not in {f"{NS}:{name}" for name in CURRENCIES},
                    f"Currency must never be craftable: {path}")

    values = parsed[DATA / NS / "default_item_values.json"]
    require(set(values) == {"prices", "currencyItems"}, "Incorrect item-value schema")
    prices, currency = values["prices"], values["currencyItems"]
    require(currency == {f"{NS}:currency_{amount}": amount * 100 for amount in DENOMINATIONS},
            "Currency values must be exact integer cents")
    require(not prices.keys() & currency.keys(), "Currency may not also have a Trading Hub sale price")
    for registry in (prices, currency):
        for identifier, cents in registry.items():
            known_item(identifier)
            require(positive_int(cents), f"Invalid positive integer cents: {identifier}")
    conversion_count = validate_conversion_prices(prices)

    index = parsed[DATA / NS / "trades" / "index.json"]
    require(index == {"files": [f"{name}.json" for name in PROFESSIONS + MERCHANTS]},
            "Trade index must contain all 13 professions and 9 custom merchants")
    count = 0
    exchanges = set()

    def stack_value(value: dict) -> int:
        return value["count"] * (currency.get(value["item"], prices.get(value["item"], 0)))

    for name in PROFESSIONS + MERCHANTS:
        config = parsed[DATA / NS / "trades" / f"{name}.json"]
        require(set(config) == {"profession", "merchant", "trades"}, f"Invalid trade root schema: {name}")
        require(config["profession"] == (f"minecraft:{name}" if name in PROFESSIONS else None),
                f"Incorrect villager profession: {name}")
        require(config["merchant"] == (name if name in MERCHANTS else None),
                f"Incorrect custom merchant: {name}")
        require(len(config["trades"]) >= 3, f"Insufficient offers: {name}")
        for trade in config["trades"]:
            require(set(trade) in (
                {"level", "buy", "sell", "maxUses", "xp", "priceMultiplier"},
                {"level", "buy", "secondBuy", "sell", "maxUses", "xp", "priceMultiplier"},
            ), f"Invalid trade fields: {name}")
            require(type(trade["level"]) is int and 1 <= trade["level"] <= 5,
                    f"Invalid trade level: {name}")
            require(positive_int(trade["maxUses"]), f"Invalid trade maximum uses: {name}")
            require(type(trade["xp"]) is int and trade["xp"] >= 0, f"Invalid trade XP: {name}")
            multiplier = trade["priceMultiplier"]
            require(type(multiplier) in (int, float) and math.isfinite(multiplier) and 0 <= multiplier <= 1,
                    f"Invalid price multiplier: {name}")
            for role in ("buy", "secondBuy", "sell"):
                if role not in trade:
                    continue
                value = trade[role]
                require(set(value) == {"item", "count"}, f"Invalid stack schema: {name}/{role}")
                known_item(value["item"])
                require(positive_int(value["count"]) and value["count"] <= 64,
                        f"Invalid stack count: {name}/{role}")
                local = value["item"].removeprefix("minecraft:")
                maximum = 1 if local in UNSTACKABLE else 16 if local in {"ender_pearl", "honey_bottle", "egg"} else 64
                require(value["count"] <= maximum, f"Exceeds vanilla item stack size: {name}/{role}")
            inputs = stack_value(trade["buy"]) + (stack_value(trade["secondBuy"]) if "secondBuy" in trade else 0)
            revenue = stack_value(trade["sell"])
            require(revenue <= inputs, f"Trade/default Trading Hub arbitrage: {name}: {trade}")
            if name == "banker":
                require("secondBuy" not in trade and multiplier == 0 and trade["xp"] == 0,
                        "Banker exchanges must not generate XP or variable-price discounts")
                require(trade["buy"]["item"] in currency and trade["sell"]["item"] in currency,
                        "Banker exchanges must use only registered currency")
                require(inputs == revenue, f"Banker exchange changes total cents: {trade}")
                exchanges.add((trade["buy"]["item"], trade["buy"]["count"],
                               trade["sell"]["item"], trade["sell"]["count"]))
            elif trade["buy"]["item"] in currency and trade["sell"]["item"] in prices:
                # Even a discounted primary cost of one note cannot yield a hub profit.
                require(revenue <= currency[trade["buy"]["item"]],
                        f"Insufficient villager-discount resale margin: {name}")
            count += 1
    expected_exchanges = set()
    for low, high in zip(CURRENCIES, CURRENCIES[1:]):
        expected_exchanges.add((f"{NS}:{low}", 10, f"{NS}:{high}", 1))
        expected_exchanges.add((f"{NS}:{high}", 1, f"{NS}:{low}", 10))
    require(exchanges == expected_exchanges, "Missing exact two-way banker denomination exchange")
    return {
        "json": len(parsed), "png": len(textures), "recipes": len(RECIPES),
        "loot_tables": len(BLOCKS), "trade_files": len(PROFESSIONS + MERCHANTS),
        "offers": count, "sale_values": len(prices), "conversion_checks": conversion_count,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate resources without changing files.")
    args = parser.parse_args()
    expected = generate()
    summary = validate(expected)
    if not args.check:
        for path, content in expected.items():
            destination = ROOT / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
    actual = {}
    for path, content in expected.items():
        destination = ROOT / path
        require(destination.is_file(), f"Missing generated file: {path}; run tools\\generate_assets.py")
        actual[path] = destination.read_bytes()
        require(actual[path] == content, f"Generated file drift: {path}; update the generator, then rebuild")
    owned_roots = (ROOT / CORE_ASSETS, ROOT / ASSETS, ROOT / DATA)
    present = {
        path.relative_to(ROOT)
        for folder in owned_roots if folder.exists()
        for path in folder.rglob("*") if path.is_file() and path.suffix in (".json", ".png")
    }
    require(present == set(expected), f"Unexpected generated resource files: {present - set(expected)}")
    require(validate(actual) == summary, "On-disk resource validation mismatch")
    digest = hashlib.sha256()
    for path in sorted(actual, key=lambda value: value.as_posix()):
        digest.update(path.as_posix().encode("utf-8"))
        digest.update(actual[path])
    mode = "Validated" if args.check else "Generated and validated"
    print(f"{mode} {len(expected)} resource files: " + ", ".join(f"{key}={value}" for key, value in summary.items()))
    print(f"Deterministic SHA-256: {digest.hexdigest()}")


if __name__ == "__main__":
    main()
