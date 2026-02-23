"""
Convert numismatics coin references in trade JSON files to statecraft_economy bills.
"""
import json
import os
import math

# Numismatics coin values
COIN_VALUES = {
    "numismatics:prismatic_coin": 1677721,
    "numismatics:ancient_coin": 26214,
    "numismatics:neptunium_coin": 3276,
    "numismatics:sun": 410,
    "numismatics:crown": 51,
    "numismatics:cog": 6,
    "numismatics:sprocket": 4,
    "numismatics:bevel": 2,
    "numismatics:spur": 1,
}

# StateCraft Economy bills (value -> item key)
BILLS = [
    (1000000, "statecraft_economy:bill_1000000"),
    (100000, "statecraft_economy:bill_100000"),
    (10000, "statecraft_economy:bill_10000"),
    (1000, "statecraft_economy:bill_1000"),
    (100, "statecraft_economy:bill_100"),
    (10, "statecraft_economy:bill_10"),
    (1, "statecraft_economy:bill_1"),
]

def find_best_bill(total_value):
    """Find the best bill denomination and amount for a given total value.
    Returns (bill_key, amount) where amount <= 64 (max stack).
    Picks the denomination that gets closest to the target value with amount 1-64.
    """
    if total_value <= 0:
        return "statecraft_economy:bill_1", 1

    best = None
    best_diff = float('inf')

    for bill_value, bill_key in BILLS:
        # How many of this bill to approximate the value
        amount = round(total_value / bill_value)
        if amount < 1:
            amount = 1
        if amount > 64:
            amount = 64

        actual = amount * bill_value
        diff = abs(actual - total_value)

        # Prefer exact matches, then closest
        if diff < best_diff or (diff == best_diff and amount == 1):
            best_diff = diff
            best = (bill_key, amount)

    return best

def convert_entry(entry):
    """Convert a single offer/request entry if it uses numismatics."""
    item_key = entry.get("itemKey", "")
    if item_key in COIN_VALUES:
        coin_value = COIN_VALUES[item_key]
        amount = entry.get("amount", 1)
        total_value = coin_value * amount

        new_key, new_amount = find_best_bill(total_value)
        entry["itemKey"] = new_key
        entry["amount"] = new_amount
        print(f"  {item_key} x{amount} (${total_value}) -> {new_key} x{new_amount} (${new_amount * int(new_key.split('_')[-1]) if 'bill_' in new_key else '?'})")
    return entry

def process_file(filepath):
    print(f"\nProcessing: {os.path.basename(filepath)}")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)

    changes = 0
    for trade in data.get("trades", []):
        for field in ["offer", "request"]:
            if field in trade:
                old_key = trade[field].get("itemKey", "")
                convert_entry(trade[field])
                if trade[field].get("itemKey", "") != old_key:
                    changes += 1

    if changes > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  -> {changes} entries converted")
    else:
        print(f"  -> No numismatics entries found")

def main():
    trades_dir = r"C:\Users\tswin\IdeaProjects\StateCraftMods\StateCraftEconomy\Trades"

    for root, dirs, files in os.walk(trades_dir):
        for filename in sorted(files):
            if filename.endswith('.json'):
                process_file(os.path.join(root, filename))

if __name__ == "__main__":
    main()

