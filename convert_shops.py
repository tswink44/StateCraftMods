"""
Convert numismatics currency items in shop JSONs to StateCraft Economy bills.

Bill denominations: $1, $10, $100, $1,000, $10,000, $100,000, $1,000,000
Max stack size: 64

The numismatics_cost field gives the total cost in base units.
We pick the best bill denomination that fits within 64 count.
Also handles: random_sets[].trades[], second_request fields.
"""
import json
import os
import glob

BILLS = [
    ("statecraft_economy:bill_1000000", 1000000),
    ("statecraft_economy:bill_100000", 100000),
    ("statecraft_economy:bill_10000", 10000),
    ("statecraft_economy:bill_1000", 1000),
    ("statecraft_economy:bill_100", 100),
    ("statecraft_economy:bill_10", 10),
    ("statecraft_economy:bill_1", 1),
]

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

def cost_to_bill(cost):
    """Convert a numismatics_cost to the best (bill_item, count) pair."""
    if cost <= 0:
        return "statecraft_economy:bill_1", 1

    for bill_item, bill_value in BILLS:
        count = cost // bill_value
        if 1 <= count <= 64:
            return bill_item, int(count)

    if cost < 1:
        return "statecraft_economy:bill_1", 1

    return "statecraft_economy:bill_1000000", min(64, cost // 1000000) or 1


def convert_request(request_obj, label="request"):
    """Convert a request/second_request object if it has numismatics items.
    Returns True if converted."""
    item = request_obj.get("item", "")
    if "numismatics:" not in item:
        return False

    count = request_obj.get("count", 1)
    coin_value = COIN_VALUES.get(item, 0)
    total_cost = coin_value * count

    if total_cost <= 0:
        total_cost = count  # fallback

    bill_item, bill_count = cost_to_bill(total_cost)

    old_item = request_obj["item"]
    old_count = count

    request_obj["item"] = bill_item
    request_obj["count"] = bill_count

    bill_value = dict(BILLS).get(bill_item, 1)
    print(f"  [{label}] {old_item} x{old_count} (cost={total_cost}) -> {bill_item} x{bill_count} (=${bill_count * bill_value})")
    return True


def convert_trade(trade):
    """Convert a single trade entry from numismatics to bills."""
    changed = 0

    # Handle main request using numismatics_cost if available
    request = trade.get("request", {})
    item = request.get("item", "")

    if "numismatics:" in item:
        cost = trade.get("numismatics_cost") or trade.get("original_numismatics_cost")
        if cost:
            bill_item, count = cost_to_bill(cost)
            old_item = request["item"]
            old_count = request.get("count", 1)
            request["item"] = bill_item
            request["count"] = count
            if "numismatics_cost" in trade:
                trade["original_numismatics_cost"] = trade.pop("numismatics_cost")
            bill_value = dict(BILLS).get(bill_item, 1)
            print(f"  [request] {old_item} x{old_count} (cost={cost}) -> {bill_item} x{count} (=${count * bill_value})")
            changed += 1
        else:
            # No cost field, compute from coin value
            if convert_request(request, "request"):
                changed += 1

    # Handle second_request
    second_request = trade.get("second_request")
    if second_request and "numismatics:" in second_request.get("item", ""):
        if convert_request(second_request, "second_request"):
            changed += 1

    return changed


def process_trades_list(trades):
    """Process a list of trades, return count of changes."""
    changed = 0
    for trade in trades:
        changed += convert_trade(trade)
    return changed


def process_file(filepath):
    """Process a single shop JSON file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)

    changed = 0

    # Top-level trades
    if "trades" in data:
        changed += process_trades_list(data["trades"])

    # random_sets[].trades[]
    if "random_sets" in data:
        for rs in data["random_sets"]:
            if "trades" in rs:
                changed += process_trades_list(rs["trades"])

    # trade_sets[].trades[] (just in case)
    if "trade_sets" in data:
        for ts in data["trade_sets"]:
            if "trades" in ts:
                changed += process_trades_list(ts["trades"])

    if changed > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write('\n')
        print(f"  -> Updated {changed} trades")

    return changed


def main():
    shops_dir = os.path.join(os.path.dirname(__file__), "shops")
    json_files = glob.glob(os.path.join(shops_dir, "*.json"))

    total_changed = 0
    files_changed = 0

    for filepath in sorted(json_files):
        filename = os.path.basename(filepath)
        print(f"\n=== {filename} ===")
        changed = process_file(filepath)
        total_changed += changed
        if changed > 0:
            files_changed += 1

    print(f"\n{'='*50}")
    print(f"Done! Updated {total_changed} trades across {files_changed} files.")


if __name__ == "__main__":
    main()
