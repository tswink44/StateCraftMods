"""
Update globalRegistry.js sell values to match statecraft-item-values.json config.
Only updates items that exist in both files (overlap).
"""
import json
import re

CONFIG_PATH = r"StateCraft\run\config\statecraft-item-values.json"
REGISTRY_PATH = "globalRegistry.js"

# 1. Load our config values (flat map: item_id -> price)
with open(CONFIG_PATH, "r", encoding="utf-8") as f:
    config = json.load(f)

config_values = {}
for cat, vals in config.items():
    if cat == "_comment" or not isinstance(vals, dict):
        continue
    for item_id, price in vals.items():
        config_values[item_id] = price

print(f"Loaded {len(config_values)} items from config")

# 2. Read globalRegistry.js
with open(REGISTRY_PATH, "r", encoding="utf-8") as f:
    content = f.read()

# 3. Find and replace all { item: "xxx", value: NNN } patterns
# Matches patterns like:
#   { item: "minecraft:coal", value: 8 }
#   { item: "minecraft:coal", value: 8},
# Also handles type/value patterns for plorts etc.
pattern = re.compile(
    r'(\{\s*item:\s*["\'])([\w:]+)(["\'],\s*value:\s*)(\d+)(\s*[,}])'
)

updated = 0
not_found = 0
matches_total = 0

def replace_value(match):
    global updated, not_found, matches_total
    matches_total += 1
    prefix = match.group(1)
    item_id = match.group(2)
    middle = match.group(3)
    old_value = match.group(4)
    suffix = match.group(5)

    if item_id in config_values:
        new_value = int(config_values[item_id])
        if new_value != int(old_value):
            updated += 1
            return f"{prefix}{item_id}{middle}{new_value}{suffix}"
    else:
        not_found += 1

    return match.group(0)

new_content = pattern.sub(replace_value, content)

# 4. Write back
with open(REGISTRY_PATH, "w", encoding="utf-8") as f:
    f.write(new_content)

print(f"Total item entries found: {matches_total}")
print(f"Updated: {updated}")
print(f"Not in config (unchanged): {not_found}")
print(f"Already matching (unchanged): {matches_total - updated - not_found}")

