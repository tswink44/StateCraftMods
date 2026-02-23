"""
Import trade values from the KubeJS dump into statecraft-item-values.json.
Values are divided by 10 and rounded to the nearest whole number.
"""
import json
import re
import math

DUMP_PATH = "items_2026-02-22T15-03-08-975844800.txt"
CONFIG_PATH = "StateCraft/run/config/statecraft-item-values.json"

# 1. Parse the dump file
dump_prices = {}
with open(DUMP_PATH, "r", encoding="utf-8") as f:
    for line in f:
        m = re.search(r'\[DumpTrades\]\s+(.+?)=(\d+)', line)
        if m:
            item_id = m.group(1).strip()
            value = int(m.group(2))
            new_value = round(value / 10)
            if new_value < 1:
                new_value = 1
            dump_prices[item_id] = new_value

print(f"Parsed {len(dump_prices)} items from dump file")

# 2. Load existing config
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    config = json.load(f)

# 3. Build a flat map of existing items -> their category
item_to_category = {}
for cat, vals in config.items():
    if cat == "_comment" or not isinstance(vals, dict):
        continue
    for item_id in vals:
        item_to_category[item_id] = cat

# 4. Overwrite existing items and add new ones
updated = 0
added = 0
for item_id, price in dump_prices.items():
    if item_id in item_to_category:
        # Update in its existing category
        cat = item_to_category[item_id]
        config[cat][item_id] = price
        updated += 1
    else:
        # Add to a "imported_trade_values" category
        if "imported_trade_values" not in config:
            config["imported_trade_values"] = {}
        config["imported_trade_values"][item_id] = price
        added += 1

# 5. Sort all categories
for cat, vals in config.items():
    if isinstance(vals, dict):
        config[cat] = dict(sorted(vals.items()))

# 6. Write back
with open(CONFIG_PATH, "w", encoding="utf-8") as f:
    json.dump(config, f, indent=2, ensure_ascii=False)

print(f"Updated {updated} existing items")
print(f"Added {added} new items to 'imported_trade_values' category")
print(f"Written to {CONFIG_PATH}")

