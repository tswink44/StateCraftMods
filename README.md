# StateCraft

Two Minecraft **1.20.1 / Forge 47.3.22+ / Java 17** mods implementing the suite described in [FEATURE_SUMMARY.md](FEATURE_SUMMARY.md).

| Artifact | Install |
| --- | --- |
| `statecraft-2.3.1.jar` | Governance, territory protection, elections, legislation, diplomacy, companies, contracts, mail, menus, and borders. Works by itself. |
| `statecraft-economy-2.3.1.jar` | Currency, utility blocks, treasuries, taxes, markets, valuation, banks, loans, and stocks. Requires the matching StateCraft JAR. |

Install the same chosen mods on the **server and every client**. Use Forge 1.20.1, not Fabric, NeoForge, or a different Minecraft release. Economy is optional for core governance; nonzero monetary actions require it. Money is represented as integer cents, not floating-point balances.

Version **2.3.0** adds personal and government overview screens, graphical recipes, and national claiming with optional state/city allocations. Update both mods on clients and the server together. Back up the entire world before upgrading: existing city claims are migrated to their nation while retaining their existing allocations and ownership.

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

**N** opens categorized sub-menus: Governments, Territory, Politics, Companies & Contracts, Banking & Trade, Administration, and Overview & Help. **Back** restores the prior page and selection instead of discarding the workspace.

**Overview & Help** contains only **My Dashboard** and **Help**. Help combines command help and the graphical recipe guide. StateCraft, Player Profiles, Your Operations, Economy Pending Work and the permanent Activity navigation are no longer listed.

**My Dashboard** is a dedicated personal overview, not a search/results window. It shows your name, nation/state/city citizenship, personal wallet and bank-deposit balances, companies in which you hold shares, and cities where you privately own land. Cities are displayed as `[Oakvale] (Westhaven/Arcadia)`. Citizenship, companies and property locations open their exact overview; balances open the corresponding account's ATM. **Personal inbox** opens your mail and pending invitations. Bank-company assets and public treasuries are not counted as your personal deposits.

**Nation, State and City overviews** show the government name, saved flag, leadership, authorized treasury balance, child governments or territory, and officers. Action categories open scoped sub-menus rather than a global command list. **Settings** and invitation management are restricted to that government's authorized officers/leaders. Officers appear on the overview; there are no separate Citizens & Roles, Officers, Invitations or Communications tabs. Official inboxes open from the corresponding government overview.

**Nations claim land.** Their claims may be unassigned to a state or city. Nation officials use the overview's **Claim chunks** and **Assign to states** maps; state officials use **Assign to cities**. Maps are centered on the player and render loaded surface terrain. Select chunks, choose the destination where applicable, and confirm the server-reviewed action. Public land ownership and future sale proceeds follow its state/city allocation; existing player/company private ownership is preserved.

Choose **Actions** for searchable actions and typed forms. Governments, people, accounts, and workflow records are shown by name rather than UUID or account key. Search by name, scroll the results, or change result pages. Values such as page number and current chunk are prefilled; payment amounts remain explicit. Names, descriptions and multiline mail remain editable text.

For example, use **Governments > States > Actions > Create**. The nation selector contains only eligible nations you can administer. The governor selector updates for that nation and offers its eligible citizens, excluding conflicting existing state membership. Changing the nation clears the dependent governor selection. City creation similarly links the selected state to eligible mayors. Empty selectors explain the missing eligibility rather than requiring you to guess an ID.

**Refresh choices** rechecks live form eligibility. Field-specific constraints explain invalid amounts, counts, limits and choices. Mutations still require explicit confirmation of current parties, costs and terms; changing the layout does not bypass those safeguards. Successful mutations refresh the same overview or query. Generic sections use cards and optional filters rather than always-visible search and command controls.

The **Recipe Guide** displays live crafting grids, ingredient item icons and output stacks, including alternate ingredients. Financial views use color cues for costs, outstanding obligations and payments, while keeping their text and amounts readable.

Typed rows open contextual detail panels and prefilled actions through **Open details**, double-click or Enter. **Copy row** and right-click copy the visible information without appending hidden IDs. Technical IDs remain available through **Advanced > Copy ID** and explicit operator diagnostics. Maps show named owners and readable chunk coordinates; their hidden references still open the exact property.

Submitted operations outlive their screens. If a reply is lost or saving fails, **Attention** appears in navigation and relevant forms; use it to **Check status**, not make another payment or purchase. This is contextual recovery, not a permanent Activity tab. **Retry safely** only retries the original unstarted action. For uncertain outcomes, **Copy support details** supplies an operator with the hidden recovery references. Recovery and operator reconciliation retain the existing duplicate-payment protections; physical inventory saves remain a separate recovery boundary.

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
