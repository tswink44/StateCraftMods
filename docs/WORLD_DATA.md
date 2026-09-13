# World data and recovery

StateCraft uses `<world>\statecraft\world.json`, a human-readable JSON snapshot with `schemaVersion`, `revision`, `savedAt`, and named `sections`. The outer snapshot schema remains **1** in mod version **2.3.0**. The governance section migrates from schema **1 to 2** for nationally owned claims with optional state/city assignments; the economy section remains compatible. The core also owns a versioned `ui_operations` section with a stable world UUID and bounded operation receipts. Hiding identifiers in ordinary screens does not remove them from these records or alter their ownership.

Before migrating an existing snapshot, the server copies its exact persisted bytes to a `before-national-claims-v2-...json` file under `statecraft\backups`. This upgrade backup does not serialize partially migrated models and is not pruned by the daily ten-backup rotation. Existing valid city claims retain their nation/state/city allocation, private/public title, permits and improvements. Invalid legacy references are not guessed away. Do not downgrade a migrated world without restoring an appropriate full-world backup.

Both mods register their mutable models with the core world store. Governance, economy, share reservations, integration locks, and UI operation results are serialized into **one snapshot**, so a save cannot update the stock ledger in one mod file while retaining a different government/share state in another. Sections belonging to an absent optional module are retained unchanged.

Writes are serialized on the server thread, written to a new temporary file, flushed, and then atomically replaced where the filesystem supports atomic replacement. The prior complete snapshot is kept as `world.previous.json`. A platform that does not support atomic moves uses a replacement move after the temporary file has been fully written.

Player, GUI, and console commands share one execution gateway. Mutating actions flush their state before replying, including persistent side effects that precede a rejected action. Genuinely read-only requests do not trigger full-world serialization. Domain mutation callbacks also cover scheduled changes, public integration operations, and queries that accrue interest or mark mail read; this is not a command-name whitelist.

Activity timestamps such as last-seen and the last governance tick remain current in memory and are included in the forced snapshots every 30 seconds, at world save, and on server stop. Explicit saves and backups also include them. The store skips identical snapshots. Minecraft player inventories are still saved by Minecraft, so back up the entire world rather than only this JSON file.

## Reviewed UI operations

GUI mutations and advanced GUI commands first obtain a server review. The server issues an operation UUID scoped to this world's UUID, binds it to the precise inputs, and expires the review after 30 seconds. A fresh review must agree on material costs, counterparties, items and terms at submission. Informational balances do not by themselves invalidate a quote; authorization and affordability are still enforced at execution.

Before starting an operation, the server persists a `PREPARED` receipt. The final outcome is then saved with the operation's domain effects in the same snapshot. A repeated matching ID returns its receipt instead of repeating the command. The final review and execution use a shared decision instant so an interest-period boundary cannot silently change a quoted amount between those steps.

After a timeout or disk failure, use the operation's **Check status** flow. A completed result is only confirmed when its snapshot revision has been saved. A `PREPARED` or uncertain result after a crash is not automatically replayed. Unknown/expired history IDs cannot start actions: execution requires their original live server-issued review or an existing receipt.

Receipts are bounded to 10,000 records, with a seven-day terminal-receipt retention target and earlier compaction under capacity pressure. Unresolved receipts are not pruned automatically; each player can have at most 128 unresolved operations. Retention is not a promise of permanent financial history. Use the ordinary account/history views and whole-world backups for longer-term investigation.

Operators can reconcile a retained uncertain operation **after independently inspecting account, world and inventory state**:

```text
/sc admin operation resolve <operationUUID> completed <reason>
/sc admin operation resolve <operationUUID> not_executed <reason>
```

This records a visible audit reason and outcome. It never replays, refunds, or undoes the original command. Restore disk writes with `/sc admin save` first when persistence is paused. Operation administration is also available through the UI; ordinary players can inspect only their own receipts.

If a client's retained reference outlived the server's bounded receipt history, operators can audit the account/world/inventory evidence and record a terminal recovery receipt:

```text
/sc admin operation recover <playerUUID> <operationUUID> completed <reason>
/sc admin operation recover <playerUUID> <operationUUID> not_executed <reason>
```

Use the affected player's UUID and the exact operation UUID from their recovery screen. A live original review must expire first; another player's receipt or an already final result cannot be overwritten. This creates an owner-scoped audit tombstone, never reconstructs or executes the old inputs, and lets the client use **Check status** to unlock its workspace. Do not guess an outcome just to dismiss a warning.

These guarantees cover duplicate reviewed UI submissions, not an atomic transaction across Minecraft player inventory saves and the suite snapshot. Cash/item operations interrupted across that boundary may require manual reconciliation. Native chat and console commands keep their normal execution behavior; their explicit repeated invocation is a new command, not a replay of a GUI operation ID.

## Backups

`/sc admin backup` writes a retained snapshot under `<world>\statecraft\backups`. The server also creates a backup on its first periodic pass and daily thereafter. Ten timestamped backups are retained; `world.previous.json` is separate.

The store refuses to overwrite malformed JSON, null data sections, unsupported schema versions, or a missing primary file when a previous snapshot exists. It does not silently reset corrupt worlds or automatically assume an older backup is current. Startup errors identify the offending file.

If an I/O failure occurs while running, the error is logged, operators are notified, and suite actions are paused. An action may already have changed memory before a write fails; **do not repeat a payment or purchase**. Fix the filesystem problem and run `/sc admin save` to persist the in-memory state and resume.

To restore a backup:

1. Stop the server. Preserve the failed files and the rest of the world for diagnosis.
2. Select a known-good backup of the **whole world**, including Minecraft player data. When repairing only the suite snapshot, understand that older ledger data may not match newer player inventories.
3. Copy the chosen complete suite snapshot to `<world>\statecraft\world.json`.
4. Restart with matching mod versions and use `/sc admin audit` to inspect consistency.

Automatic financial integration locks are saved with the snapshot. If Economy is temporarily removed, funded government/company accounts and economically encumbered claims remain protected from destructive governance actions. Reinstall Economy to resolve their balances or obligations.

Integration locks are refreshed by snapshot preparation inside the world store itself. Direct saves, manual backups, and periodic backups therefore capture the same financial protections as their accompanying economy data, even when scheduled processing changed an obligation since the last regular save.

## Compatible legacy import

When no current or previous snapshot exists, the store can import:

| Existing file | New section |
| --- | --- |
| `<world>\statecraft\government.json` | `governance` |
| `<world>\statecraft\economy.json` | `economy` |

Each file must contain a JSON object matching that module's current data-model fields. Import wraps those sections in the versioned snapshot and retains the original files. This is a structural import, **not an undocumented converter for every previous StateCraft release**. No historical implementation or sample save was supplied with the feature summary. Convert other formats explicitly and test a copy of the world before using them.

## Protection scope

Server event handlers enforce block placement/breaking, tool and bucket use, block/container and entity interaction, melee/projectile PvP, explosion protection, mob block destruction, and piston movement across property boundaries. Multi-block placements check every affected position. Creative mode does not itself grant a bypass; the core's operator bypass must be authorized.

A chunk's `PLACE` permit allows ordinary block placement and block-tool use without granting container access. Right-click handling keeps block interaction denied while the placement/tool event independently enforces that permit.

As with other Forge event-based claim mods, a third-party mod that modifies world state directly without the corresponding Forge events needs its own integration. Test automation, quarry, combat, and world-editing mods against claims before enabling them on a public server.
