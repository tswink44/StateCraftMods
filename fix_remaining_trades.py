"""
Fix remaining numismatics references in trade JSON files:
1. Convert coin references in additionalRequest fields to statecraft_economy bills
2. Remove entire trades where the offered item is a numismatics block/item (not a coin)
"""
import json
import os

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
    if total_value <= 0:
        return "statecraft_economy:bill_1", 1

    best = None
    best_diff = float('inf')

    for bill_value, bill_key in BILLS:
        amount = round(total_value / bill_value)
        if amount < 1:
            amount = 1
        if amount > 64:
            amount = 64
        actual = amount * bill_value
        diff = abs(actual - total_value)
        if diff < best_diff or (diff == best_diff and amount == 1):
            best_diff = diff
            best = (bill_key, amount)

    return best

def is_numismatics_item(item_key):
    """Check if an item key is from numismatics or numismatics_utils mods."""
    return item_key.startswith("numismatics:") or item_key.startswith("numismatics_utils:")

def is_numismatics_coin(item_key):
    """Check if an item key is a numismatics coin (has a value)."""
    return item_key in COIN_VALUES

def convert_coin_entry(entry):
    """Convert a coin entry to the equivalent bill. Returns True if converted."""
    item_key = entry.get("itemKey", "")
    if is_numismatics_coin(item_key):
        coin_value = COIN_VALUES[item_key]
        amount = entry.get("amount", 1)
        total_value = coin_value * amount
        new_key, new_amount = find_best_bill(total_value)
        bill_val = int(new_key.split("_")[-1])
        print(f"    Converted: {item_key} x{amount} (${total_value}) -> {new_key} x{new_amount} (${new_amount * bill_val})")
        entry["itemKey"] = new_key
        entry["amount"] = new_amount
        return True
    return False

def process_file(filepath):
    print(f"\nProcessing: {os.path.basename(filepath)}")
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)

    trades = data.get("trades", [])
    new_trades = []
    removed = 0
    converted = 0

    for trade in trades:
        # Check if the offered item is a numismatics non-coin item -> remove entire trade
        offer_key = trade.get("offer", {}).get("itemKey", "")
        if is_numismatics_item(offer_key) and not is_numismatics_coin(offer_key):
            print(f"  REMOVED trade offering: {offer_key}")
            removed += 1
            continue

        # Convert coin references in offer, request, additionalRequest
        for field in ["offer", "request", "additionalRequest"]:
            if field in trade:
                if convert_coin_entry(trade[field]):
                    converted += 1

        new_trades.append(trade)

    if removed > 0 or converted > 0:
        data["trades"] = new_trades
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  -> Removed {removed} trades, converted {converted} coin entries")
    else:
        print(f"  -> No changes needed")

def main():
    trades_dir = r"C:\Users\tswin\IdeaProjects\StateCraftMods\StateCraftEconomy\Trades"
    for root, dirs, files in os.walk(trades_dir):
        for filename in sorted(files):
            if filename.endswith('.json'):
                process_file(os.path.join(root, filename))

if __name__ == "__main__":
    main()

