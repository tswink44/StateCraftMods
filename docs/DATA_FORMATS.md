# StateCraft resource and economy data formats

Minecraft **1.20.1 / Forge**, resource and datapack format **15**. All supplied
32×32 RGBA artwork is original pixel art generated with the Python standard
library; there are no downloaded images, fonts, or additional dependencies.

Run from the project root:

```powershell
python tools\generate_assets.py
python tools\generate_assets.py --check
```

The first command deterministically rebuilds the owned assets/data; `--check`
does not write files. The reviewable generator is the source of truth for
bundled JSON and textures. Validation checks JSON schemas, known 1.20.1 IDs,
models/textures, recipes/unlocks, block drops/tool tags, PNG signatures, sizes,
CRCs and pixels, trade stacks, exact exchanges, and representative conversion
prices. It also detects generated-file drift.

## Item values: integer cents

Bundled defaults: `data\statecraft_economy\default_item_values.json`.
The server copies defaults on first use to
`config\statecraft_economy\item_values.json`; edit that runtime file to customize
a server rather than editing its installed JAR.

```json
{
  "prices": {
    "minecraft:wheat": 25,
    "minecraft:iron_ingot": 45
  },
  "currencyItems": {
    "statecraft_economy:currency_1": 100,
    "statecraft_economy:currency_10": 1000
  }
}
```

Both maps associate registered item IDs with **positive integer cents per
item**: `$1 = 100`, `$10 = 1000`, `$1,000,000 = 100000000`. The example is
abbreviated; bundled defaults register all seven physical denominations.
Do not use floating-point dollar values or put currency items in `prices`.

The root helper accepts an explicit path and never edits it:

```powershell
python verify_config.py statecraft-economy\run\config\statecraft_economy\item_values.json
python verify_config.py statecraft-economy\run\config\statecraft_economy\item_values.json --item minecraft:wheat
```

It rejects duplicate keys, malformed maps/IDs, non-integer or out-of-range cents,
and currency/sale-price overlap. It checks structure, not whether another mod
actually registered an item; the server remains authoritative. Old category-price
and Numismatics registry scripts are separated under `tools\legacy` and must not
be used to convert current cent-denominated prices implicitly.

`prices` defines Trading Hub sale proceeds before applicable policy deductions.
Unlisted items have no default Hub sale value. `currencyItems` defines physical
money, not an additional goods-sale table. Defaults are deliberately modest,
configurable server policy, **not** a simulated market or a promise of economic
balance. Compactable resources have consistent values; representative crafting
and smelting outputs do not increase the priced inputs' value. Crop growth,
mining, drops, and other productive gameplay can still earn money. Recheck
recipes and every shop when changing values, adding mods, discounts, or trades.

## Runtime villager and merchant trades

Bundled trade definitions live in `data\statecraft_economy\trades`.
`index.json` is the bootstrap manifest:

```json
{"files":["farmer.json","banker.json"]}
```

The real manifest lists all **13** standard professions and **9** custom
merchants. Standard names are `armorer`, `butcher`, `cartographer`, `cleric`,
`farmer`, `fisherman`, `fletcher`, `leatherworker`, `librarian`, `mason`,
`shepherd`, `toolsmith`, and `weaponsmith`. Custom merchant names are `banker`,
`bard`, `barkeeper`, `botanist`, `market`, `baker`, `winemaker`, `storage_smith`,
and `ribbit`.

The manifest and definitions are copied to
`config\statecraft_economy\trades`. The runtime loader reads that folder's
`index.json`; update its `files` list when adding or removing definitions.
A standard file:

```json
{
  "profession": "minecraft:farmer",
  "merchant": null,
  "trades": [
    {
      "level": 1,
      "buy": {"item": "minecraft:wheat", "count": 4},
      "sell": {"item": "statecraft_economy:currency_1", "count": 1},
      "maxUses": 16,
      "xp": 2,
      "priceMultiplier": 0.05
    }
  ]
}
```

`buy` is what the **player pays**; `sell` is what the **player receives**.
Optional `secondBuy` is another required input stack with the same
`{"item":"namespace:id","count":1}` shape. Counts must be integers from 1 to 64
and must respect the item's actual stack limit (for example, tools/stew: 1;
ender pearls/honey bottles: 16). `level` is villager level 1–5, `maxUses` is a
positive restock limit, `xp` is nonnegative villager experience, and
`priceMultiplier` is the vanilla demand/reputation price multiplier, not a
percentage tax. Bundled normal offers explicitly supply these fields, using
16/12/8/4 uses according to the goods and 2/5/10 XP for levels 1/2/3.

A custom file uses `"profession": null` and `"merchant": "banker"` (or another
listed custom key). A merchant key selects an economy shop; it does **not**
register a new villager profession or spawn a third-party NPC. Custom shops
use vanilla goods: for example, the winemaker offers berries and honey bottles,
the bard musical blocks, and Ribbit wetland supplies. No external mod is
required, and no new wine, instrument, or creature item is implied.

The banker has **12** exact exchanges: `10 × lower note ↔ 1 × next note` for
each adjacent denomination, never illegal stacks of 100 notes. Its offers use
`maxUses: 64`, `xp: 0`, and `priceMultiplier: 0.0`. The banker uses a vanilla
Wandering Trader with explicit offers, avoiding villager/hero/gossip discounts.
Cash-only exchanges are checked against configured denomination values before
reload. Exchange execution must remain fixed-value: do not apply additional
discounts or markups to currency exchange. Default shop purchases
cannot be immediately resold to the Hub for a profit, including at a
one-primary-note discount floor for Hub-valued goods.

These are **runtime mod configuration**, not vanilla datapack recipe/trade
types. Use the economy configuration reload facility (or restart the server)
after runtime edits. Vanilla `/reload` by itself is not the runtime trade
configuration loader. Existing villagers may retain previously generated
offers; use newly generated offers when checking changed profession defaults.

## Crafting, loot, tags, and recipe guide

Crafting uses normal 1.20.1 datapack JSON under
`data\statecraft_economy\recipes\<registry_id>.json`. A datapack with format 15
can override a recipe at that path; vanilla `/reload` applies datapack changes.
There are **no currency recipes**, including compression/decompression.
Exchange notes with the banker or use account deposit/withdrawal instead.

Shaped example (`statecraft_economy:bank_card`):

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "group": "statecraft_economy",
  "pattern": ["PG", "IR"],
  "key": {
    "P": {"item": "minecraft:paper"},
    "G": {"item": "minecraft:gold_nugget"},
    "I": {"item": "minecraft:iron_ingot"},
    "R": {"item": "minecraft:redstone"}
  },
  "result": {"item": "statecraft_economy:bank_card", "count": 1}
}
```

The Recipe Guide is shapeless: `"type": "minecraft:crafting_shapeless"`,
`"ingredients": [{"item":"minecraft:book"},{"item":"minecraft:paper"}]`,
and `"result": {"item":"statecraft_economy:recipe_guide","count":1}`.
All seven recipes yield one item and have recipe-book unlock advancements.

The Recipe Guide reads loaded recipes and ingredient alternatives, including
datapack overrides. Bundled layouts (rows separated by `/`):

| Result | Layout | Materials |
|---|---|---|
| ATM | `IGI / IRI / ICI` | 6 iron ingots, glass, redstone, chest |
| Trading Hub | `IPI / RCR / III` | 5 iron ingots, paper, 2 redstone, chest |
| Marketplace | `PPP / GCG / III` | 3 oak planks, 2 gold nuggets, chest, 3 iron ingots |
| Company Vault | `III / ICI / IRI` | 7 iron ingots, chest, redstone |
| Stock Market | `IGI / RCR / IPI` | 4 iron ingots, glass, 2 redstone, comparator, paper |
| Bank Card | `PG / IR` | paper, gold nugget, iron ingot, redstone |
| Recipe Guide | shapeless | book, paper |

`I` = iron ingot, `R` = redstone, `C` = chest except **Stock Market**
(`C` = comparator). `P` = paper except **Marketplace** (`P` = oak planks).
`G` = glass for ATM/Stock Market and gold nugget for Marketplace/Bank Card.
These are exact items, not interchangeable plank or glass tags.

All five metal blocks have explosion-aware self-drop loot tables and additive
`minecraft:mineable/pickaxe` and `minecraft:needs_iron_tool` block tags.
The Java blocks must require the correct tool for drops for iron-tier
harvesting to be enforced. Blockstates use the empty variant because these
plain blocks have no facing property: the decorative front always faces world
north, with separate original side and top textures.

## Client assets

All registered economy items/blocks have models and `en_us` display names.
The creative-tab translation key is `itemGroup.statecraft_economy`.
Notes display `1`, `10`, `100`, `1K`, `10K`, `100K`, and `1M`, use seven
different palettes, and carry one through seven rank marks.

Core translations are `key.statecraft.menu` (Open StateCraft Menu),
`key.statecraft.borders` (Cycle Territory Borders), and
`key.categories.statecraft` (StateCraft). Java registers the default **N** and
**B** keys; language resources do not assign keys. Optional original 32px
branding textures are available to client screens as
`statecraft:textures/gui/statecraft_emblem.png` and
`statecraft_economy:textures/gui/economy_emblem.png`.
