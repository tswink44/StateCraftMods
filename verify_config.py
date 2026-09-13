"""Read-only structure check for StateCraft 2.x item_values.json files."""

import argparse
import json
from pathlib import Path
import re

from tools.generate_assets import positive_int


MAX_CENTS = 9_000_000_000_000_000
ITEM_ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9_./-]+")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def load_values(path: Path) -> dict:
    with path.open(encoding="utf-8") as source:
        values = json.load(source, object_pairs_hook=unique_object)
    if not isinstance(values, dict) or set(values) != {"prices", "currencyItems"}:
        raise ValueError("Expected the StateCraft 2.x prices and currencyItems maps, not a legacy category file.")
    for name, entries in values.items():
        if not isinstance(entries, dict):
            raise ValueError(f"{name} must be an object.")
        for item, cents in entries.items():
            if ITEM_ID.fullmatch(item) is None or item == "minecraft:air":
                raise ValueError(f"Invalid item ID in {name}: {item}")
            if not positive_int(cents) or cents > MAX_CENTS:
                raise ValueError(f"{name}[{item}] must be positive integer cents, at most {MAX_CENTS}.")
    if not values["currencyItems"]:
        raise ValueError("At least one physical currency denomination is required.")
    overlap = values["prices"].keys() & values["currencyItems"].keys()
    if overlap:
        raise ValueError(f"Currency cannot also have a sale price: {', '.join(sorted(overlap))}")
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", type=Path, help="Explicit path to a current item_values.json file")
    parser.add_argument("--item", help="Display the configured integer-cent value of one item")
    args = parser.parse_args()
    try:
        values = load_values(args.config)
        if args.item:
            matching = [(name, entries[args.item]) for name, entries in values.items() if args.item in entries]
            if not matching:
                raise ValueError(f"No value is configured for {args.item}.")
            for name, cents in matching:
                print(f"{args.item} [{name}] = {cents} cents")
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(f"Valid structure: {len(values['prices'])} sale prices, {len(values['currencyItems'])} currency items.")
    print("Item registration and gameplay compatibility must still be verified by the server.")


if __name__ == "__main__":
    main()
