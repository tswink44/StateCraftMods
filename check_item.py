import json

d = json.load(open('StateCraft/run/config/statecraft-item-values.json', 'r', encoding='utf-8'))
found = False
for cat, vals in d.items():
    if isinstance(vals, dict):
        for k, v in vals.items():
            if 'dewy_star' in k:
                print(f"  Found in [{cat}]: {k} = {v}")
                found = True
if not found:
    print("NOT FOUND in config")

# Also check items_clean.txt
with open('items_clean.txt', 'r') as f:
    items = [l.strip() for l in f if 'dewy_star' in l]
print(f"\nIn items_clean.txt: {items}")

