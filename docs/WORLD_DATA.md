# World data and recovery

StateCraft uses `<world>\statecraft\world.json`, a human-readable JSON snapshot with `schemaVersion`, `revision`, `savedAt`, and named `sections`. The current snapshot schema is **1**, independently of the mod version **2.1.0**. The UI changes in 2.1 do not migrate or rewrite the domain schema.

Both mods register their mutable models with the core world store. Governance, economy, share reservations, and integration locks are serialized into **one snapshot**, so a save cannot update the stock ledger in one mod file while retaining a different government/share state in another. Sections belonging to an absent optional module are retained unchanged.

Writes are serialized on the server thread, written to a new temporary file, flushed, and then atomically replaced where the filesystem supports atomic replacement. The prior complete snapshot is kept as `world.previous.json`. A platform that does not support atomic moves uses a replacement move after the temporary file has been fully written.

Successful player command and GUI actions flush their state before replying. Periodic processing is flushed every 30 seconds, at world save, and on server stop. The store skips identical snapshots. Minecraft player inventories are still saved by Minecraft, so back up the entire world rather than only this JSON file.

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
