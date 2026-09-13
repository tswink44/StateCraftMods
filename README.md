# StateCraft

Two Minecraft **1.20.1 / Forge 47.3.22+ / Java 17** mods implementing the suite described in [FEATURE_SUMMARY.md](FEATURE_SUMMARY.md).

| Artifact | Install |
| --- | --- |
| `statecraft-2.1.0.jar` | Governance, territory protection, elections, legislation, diplomacy, companies, contracts, mail, menus, and borders. Works by itself. |
| `statecraft-economy-2.1.0.jar` | Currency, utility blocks, treasuries, taxes, markets, valuation, banks, loans, and stocks. Requires the matching StateCraft JAR. |

Install the same chosen mods on the **server and every client**. Use Forge 1.20.1, not Fabric, NeoForge, or a different Minecraft release. Economy is optional for core governance; nonzero monetary actions require it. Money is represented as integer cents, not floating-point balances.

Version 2.1 adds typed UI-choice packets: update clients and the server together. The world-save schema is unchanged.

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

## In game

**N** opens categorized sub-menus: Governments, Territory, Politics, Companies & Contracts, Banking & Trade, Communications, Administration, and Overview & Help. Select an area, then a section. Within a section, the sidebar shows only related sections; **All sections** returns to the category menu. **Back** and Escape navigate upward instead of discarding the whole menu.

Choose **Actions** for searchable actions and typed forms. Names and descriptions remain editable text; governments, people, accounts, workflow IDs, and fixed choices use searchable selectors where applicable. Search by name or ID, scroll the results, or change result pages. Values such as page number and current chunk are prefilled; payment amounts remain explicit. Multiline fields support mail and descriptions.

For example, use **Governments > States > Actions > Create**. The nation selector contains only eligible nations you can administer. The governor selector updates for that nation and offers its eligible citizens, excluding conflicting existing state membership. Changing the nation clears the dependent governor selection. City creation similarly links the selected state to eligible mayors. Empty selectors explain the missing eligibility rather than requiring you to guess an ID.

**Refresh choices** rechecks live form eligibility. Failed actions keep your form values so you can correct them; successful actions return to the section with the result. All choices come from the server and the actual command rechecks permissions at submission, including role changes after opening a form. **Refresh** in a section retrieves its current data; the command box remains available for advanced commands without `/sc` or `/sce`.

Left-click a result row to copy its first ID, account, item ID, or chunk key; right-click to copy the full row. Paste into an action form with **Ctrl+V**. Clicking a map cell copies that cell's dimension-qualified chunk key.

**B** cycles borders: off, chunks, cities, states, nations. The **Map** button shows nearby claims, their full political hierarchy, and private property owners. Menus and borders never force-load distant chunks.

Key bindings are remappable in Minecraft's Controls menu, including when another mod also uses **N** or **B**.

Optional JourneyMap integration targets **JourneyMap 5.10.3 / API 1.9** (not JourneyMap 6). The map's **Export Xaero** button writes nearby city waypoints for **manual** import; it does not inject polygons into Xaero. Exported waypoint heights use Y=64, not surveyed terrain heights.

`/statecraft` and `/sc` open the core menu. `/sc gui <page>` opens a specific registered page; tab completion lists page IDs. `/sc borders` cycles the client border mode. Economy commands use `/statecrafteconomy` and `/sce`. Right-click the ATM, Trading Hub, Marketplace, Company Vault, or Stock Market to access its services.

Detailed command references and settings:

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
