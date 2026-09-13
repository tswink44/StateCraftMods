"""Legacy category-price to Numismatics registry adapter; NOT a StateCraft 2.x converter."""

import argparse
import json
from pathlib import Path
import re

ENTRY = re.compile(r'(\{\s*item:\s*["\'])([a-z0-9_.-]+:[a-z0-9_./-]+)(["\'],\s*value:\s*)(\d+)(?=\s*[,}])')


def convert(config: dict, content: str) -> tuple[str, int, int]:
    if not isinstance(config, dict) or "prices" in config or "currencyItems" in config:
        raise ValueError("StateCraft 2.x cents must not be copied into Numismatics units. Supply a legacy category file.")
    values = {}
    for category, entries in config.items():
        if category == "_comment":
            continue
        if not isinstance(entries, dict):
            raise ValueError(f"Legacy category {category} must be an object.")
        for item, price in entries.items():
            if type(price) is not int or price < 0:
                raise ValueError(f"Legacy price for {item} must already be a nonnegative integer in registry units.")
            if item in values and values[item] != price:
                raise ValueError(f"Conflicting legacy prices for {item}.")
            values[item] = price
    matched = 0
    changed = 0

    def replace(match):
        nonlocal matched, changed
        item = match.group(2)
        if item not in values:
            return match.group(0)
        matched += 1
        if values[item] == int(match.group(4)):
            return match.group(0)
        changed += 1
        return match.group(1) + item + match.group(3) + str(values[item])

    result = ENTRY.sub(replace, content)
    if matched == 0:
        raise ValueError("No supported literal item/value entries matched the supplied legacy prices.")
    return result, matched, changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", type=Path, help="Legacy category-price JSON in Numismatics registry units")
    parser.add_argument("registry", type=Path, help="Explicit input globalRegistry.js path")
    parser.add_argument("--output", type=Path, help="Write a NEW output file; otherwise preview counts without writing")
    args = parser.parse_args()
    try:
        config = json.loads(args.config.read_text(encoding="utf-8"))
        result, matched, changed = convert(config, args.registry.read_text(encoding="utf-8"))
        if args.output:
            with args.output.open("x", encoding="utf-8", newline="") as output:
                output.write(result)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(f"{matched} supported entries matched; {changed} values changed. "
          + (f"New output: {args.output}" if args.output else "Preview only; no files written."))


if __name__ == "__main__":
    main()
