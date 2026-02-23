import json
import math

path = "StateCraft/run/config/statecraft-item-values.json"

with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

changed = 0
for cat, vals in data.items():
    if cat == "_comment" or not isinstance(vals, dict):
        continue
    for item_id in list(vals.keys()):
        price = vals[item_id]
        if isinstance(price, (int, float)) and price > 100:
            vals[item_id] = math.ceil(price / 10)
            changed += 1

with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print(f"Done. {changed} items adjusted (values over 100 divided by 10, rounded up).")

