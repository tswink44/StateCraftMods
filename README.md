# StateCraft

Two Minecraft **1.20.1 / Forge 47.3.22+ / Java 17** mods implementing the suite described in [FEATURE_SUMMARY.md](FEATURE_SUMMARY.md).

| Artifact | Install |
| --- | --- |
| `statecraft-2.2.0.jar` | Governance, territory protection, elections, legislation, diplomacy, companies, contracts, mail, menus, and borders. Works by itself. |
| `statecraft-economy-2.2.0.jar` | Currency, utility blocks, treasuries, taxes, markets, valuation, banks, loans, and stocks. Requires the matching StateCraft JAR. |

Install the same chosen mods on the **server and every client**. Use Forge 1.20.1, not Fabric, NeoForge, or a different Minecraft release. Economy is optional for core governance; nonzero monetary actions require it. Money is represented as integer cents, not floating-point balances.

Version **2.2** adds protocol-4 contextual views, server-reviewed operations, and recovery receipts. Update clients and the server together; do not mix 2.1 and 2.2 JARs. Existing governance/economy saves remain readable in snapshot schema 1, with a new core-owned `ui_operations` section.

## Build and run

Set `JAVA_HOME` to a **JDK 17** installation. A newer default Java installation is not sufficient for this Minecraft version.

```powershell
.\gradlew.bat packageMods
```

The two reobfuscated, installable JARs are written to **`build\mods`**. Each subproject also produces its own JAR in its `build\libs` directory. Gradle 8.8 is bootstrapped by the checksum-verified wrapper; the first build downloads Minecraft, Forge, and development dependencies.

```powershell
.\gradlew.bat test
.\gradlew.bat :statecraft:runGameTestServer
.\gradlew.bat :statecraft-economy:runGameTestServer
.\gradlew.bat :statecraft-economy:runClient
```

The economy development runs load **both** mods. `:statecraft:runClient` runs core alone. `runServer` is also available in each subproject; review and accept Minecraft's EULA yourself before operating a normal server. Development worlds live in the relevant subproject's `run` directory.

`.\run-combined.ps1` launches the economy development client with both mods, using the root wrapper even when invoked from another directory. Add `-DryRun` to display the command without launching Minecraft.

## In game

**N** opens categorized sub-menus: Governments, Territory, Politics, Companies & Contracts, Banking & Trade, Communications, Administration, and Overview & Help. **My dashboard** brings authorized pending work and recent operations together. Select an area, then a section. **Back** restores the prior page/entity, search, pagination, scroll and selection rather than discarding the workspace.

Choose **Actions** for searchable actions and typed forms. Names and descriptions remain editable text; governments, people, accounts, workflow IDs, and fixed choices use searchable selectors where applicable. Search by name or ID, scroll the results, or change result pages. Values such as page number and current chunk are prefilled; payment amounts remain explicit. Multiline fields support mail and descriptions.

For example, use **Governments > States > Actions > Create**. The nation selector contains only eligible nations you can administer. The governor selector updates for that nation and offers its eligible citizens, excluding conflicting existing state membership. Changing the nation clears the dependent governor selection. City creation similarly links the selected state to eligible mayors. Empty selectors explain the missing eligibility rather than requiring you to guess an ID.

**Refresh choices** rechecks live form eligibility. Field-specific constraints explain invalid amounts, counts, limits and choices. Mutations use **Review action**, then an explicit confirmation of the server's current parties, costs and terms. Reviews expire after 30 seconds, and changed material terms require another review. Successful mutations show a separate result banner and refresh the same section query; query actions keep their requested results. The command box remains available for advanced commands without `/sc` or `/sce`, with conservative review before potentially mutating commands.

Typed rows open contextual detail panels and prefilled actions through **Open details**, double-click or Enter. Copy controls and right-click retain full row/ID copying; plain advanced-command results retain their original identifier-copy behavior. Valid map cells expose their dimension-qualified chunk key and claimed cells can open their detail panel.

Submitted operations outlive their screens. If a reply is lost or saving fails, use **Operations > Check status**, not another payment or purchase. Matching operation IDs replay receipts, not effects. Minimal world/player-scoped recovery references survive reconnects without storing private form or mail contents. Unknown/uncertain operations remain locked until reconciled; operators can audit and resolve them without replaying the original action. Physical inventory saves remain a separate recovery boundary.

**B** cycles borders: off, chunks, cities, states, nations. The **Map** button shows nearby claims, their full political hierarchy, and private property owners. Menus and borders never force-load distant chunks.

Key bindings are remappable in Minecraft's Controls menu, including when another mod also uses **N** or **B**. Territory snapshots use at most 289 dimension-qualified claim lookups per refresh, rather than scanning every claim in the world.

Optional JourneyMap integration targets **JourneyMap 5.10.3 / API 1.9** (not JourneyMap 6). The map's **Export Xaero** button writes nearby city waypoints for **manual** import; it does not inject polygons into Xaero. Exported waypoint heights use Y=64, not surveyed terrain heights.

`/statecraft` and `/sc` open the core menu. `/sc gui <page>` opens a specific registered page; tab completion lists page IDs. `/sc borders` cycles the client border mode. Economy commands use `/statecrafteconomy` and `/sce`. Right-click the ATM, Trading Hub, Marketplace, Company Vault, or Stock Market to access its services.

Detailed command references and settings:

- [UI navigation, review cards, keyboard controls and recovery](docs/UI_UX.md)
- [Governance, claims, politics, companies, and mail](docs/GOVERNANCE.md)
- [Economy, cash, taxes, markets, banking, and stocks](docs/ECONOMY.md)
- [Item values, merchant definitions, recipes, and assets](docs/DATA_FORMATS.md)
- [Optional map interoperability](docs/MAP_INTEGRATION.md)
- [World persistence, backups, and recovery](docs/WORLD_DATA.md)

## Configuration and administration

Forge server configuration is per-world, under **`<world>\serverconfig`**. Copy desired initial server TOMLs into the server's `defaultconfigs` directory for newly created worlds. The generated TOMLs contain the mod's gameplay settings; the command references describe their units and validation. Custom item prices and merchant JSON live under **`config\statecraft_economy`** and can be reloaded at runtime.

Core operator tools require permission level 2. In addition to the governance reference's administrative commands, `/sc admin save`, `/sc admin backup`, and `/sc admin reload` provide an immediate save, retained backup, and actual disk configuration reload. These commands also work from the dedicated-server console.

Do not edit world JSON while the server is running. Stop the server and back up the entire world before upgrading or importing data. Keep Minecraft's ordinary player/world backups together with the StateCraft snapshot.

## Project layout

`statecraft` contains the shared public integration API, governance domain, common persistence, Forge event protection, server-validated networking, and client UI. `statecraft-economy` depends on that API without embedding another copy of StateCraft. Domain tests exercise business rules without starting a graphical client; Forge GameTests exercise dedicated-server initialization and registered content.

Textures are original, generated by `tools\generate_assets.py`; the empty Forge test structure is generated by `tools\generate_test_structure.py`. Generated PNGs, JSON resources, and the test structure are included so an ordinary build does not require Python.

The repository originally contained the feature summary only, not previous mod binaries, source code, or world saves. Compatibility with unspecified historical save formats is not assumed; the supported import format is documented in the world-data reference.
