import json

d = json.load(open(r'C:\Users\tswin\IdeaProjects\StateCraftMods\StateCraft\run\config\statecraft-item-values.json', 'r', encoding='utf-8'))
items_without_colon = [k for cat, vals in d.items() if isinstance(vals, dict) for k in vals if ':' not in k]
print(f'Items without colon separator: {len(items_without_colon)}')
for x in items_without_colon[:10]:
    print(f'  {x}')

# Count total items
total = sum(len(v) for v in d.values() if isinstance(v, dict))
print(f'\nTotal items: {total}')

# Check specific item
for cat, vals in d.items():
    if isinstance(vals, dict) and 'society:double_aged_dewy_star' in vals:
        print(f'\nsociety:double_aged_dewy_star found in [{cat}] = {vals["society:double_aged_dewy_star"]}')

