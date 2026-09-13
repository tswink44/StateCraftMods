# Legacy tooling

`sync_registry_values.py` belongs to the old category-price/Numismatics workflow.
It is not part of the StateCraft 2.x mod build or configuration reload.
Numismatics registry units are not StateCraft integer cents; the script explicitly
rejects the current `prices` / `currencyItems` format.

Both input paths are required. By default it only reports matching literal
`{ item: "namespace:id", value: integer }` entries. It does not evaluate JavaScript
or change computed prices. `--output` must name a new file; input files and existing
outputs are never overwritten.

```powershell
python tools\legacy\sync_registry_values.py legacy-prices.json globalRegistry.js
python tools\legacy\sync_registry_values.py legacy-prices.json globalRegistry.js --output reviewed-registry.js
```

Use the root `verify_config.py <path>` for current StateCraft 2.x item-value
structure checks. Do not use the legacy adapter to assign current server prices.
