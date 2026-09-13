# Optional map interoperability

## Supported scope

| Map mod | StateCraft support |
| --- | --- |
| **JourneyMap 5.10.3 for Minecraft 1.20.1, Forge** | Live, dimension-specific territory polygon overlays through its public API 1.9. |
| JourneyMap 6.x / API 2 | **Not supported by this adapter.** This is a different plugin API, including on Minecraft 1.20.1. |
| Xaero's Minimap, optionally with Xaero's World Map | Explicit **manual waypoint-text export/import** for nearby city reference points. No automatic polygons or live waypoint synchronization. |
| Neither optional mod installed | No map integration is loaded; the export utility still works without either mod. |

The adapter consumes only `TerritorySnapshot` delivered by the client's Forge
`TerritoryChangedEvent`. It never loads world chunks, queries map tiles, requests
additional territory data, or reads another mod's files. It knows only the
server-supplied window, at most **17 × 17 chunks in one dimension**. It does not
retain territory history across windows, dimensions, or connections.

## JourneyMap build and initialization

The correct Maven coordinate for this target is:

```groovy
compileOnly fg.deobf('info.journeymap:journeymap-api:1.20-1.9-SNAPSHOT')
```

The `1.20` prefix is intentional: TeamJM documents this artifact for **both 1.20
and 1.20.1**. Do not substitute an invented `1.20.1-1.9` artifact or API-2 example.
The repository is `https://jm.gserv.me/repository/maven-public/`.

The parent build must apply the provided script **after** ForgeGradle is applied:

```groovy
project(':statecraft') {
    apply from: file('compat.gradle')
}
```

`statecraft\compat.gradle` adds only the repository and compile-only API. It does
not install JourneyMap for development or make either optional mod mandatory.
Install the **Forge** JourneyMap 5.10.3 build separately for an integration test.
Its published requirements list Java 17 and Forge 47.2.30 for Minecraft 1.20.1;
this suite targets Forge 47.3.22.

**Never shade, bundle, or jar-in-jar the JourneyMap API**, and do not add its
classes to the distributable jar. This is both the API's intended soft-dependency
design and its redistribution restriction.

Initialization is automatic through:

```text
dev.statecraft.compat.StateCraftJourneyMapPlugin
    @journeymap.client.api.ClientPlugin
    implements journeymap.client.api.IClientPlugin
```

This API uses **`@ClientPlugin`**, not `@JourneyMapPlugin`. JourneyMap's client
discovery calls `initialize(IClientAPI)`. That method subscribes to JourneyMap
events and registers the instance's `TerritoryChangedEvent` listener on
`MinecraftForge.EVENT_BUS`. **Do not reference or instantiate this plugin from
StateCraft's common initialization, client initializer, or export button.**
Discovery-only linkage keeps its optional API types out of ordinary client and
dedicated-server class loading.

### Overlay behavior

- Public territory color supplies translucent fill and a visible outline.
  Labels identify the city, state, or nation; rollover text contains the public
  nation/state/city names and IDs plus owner account.
- Adjacent chunks with identical ownership, names, and color are merged into
  rectangular polygons. Irregular regions can have several rectangles and
  internal rectangle seams. Holes and missing chunks are **not filled**.
- Coordinates are actual chunk edges (`chunk × 16` through `(chunk + 1) × 16`),
  including negative coordinates. Each overlay has a stable, dimension-scoped,
  StateCraft-owned ID and an explicit `ResourceKey<Level>`.
- Polygons target JourneyMap's minimap and fullscreen map, not its web map.
  JourneyMap's own overlay acceptance/display settings still apply.
- Every snapshot replacement first removes StateCraft polygon overlays, then
  shows only the current window if its dimension is being mapped.
- `DISPLAY_UPDATE` resubmits the current dimension's polygons.
  `MAPPING_STOPPED` removes polygons and discards the cached snapshot. A mapping
  restart waits for a fresh snapshot instead of resurrecting previous data.
- The parent's logout `TerritorySnapshot.EMPTY` immediately clears polygons,
  including when disconnecting from a non-overworld dimension.
- An API exception or linkage failure disables this adapter for that client run,
  logs a warning, and attempts to remove its polygons. It does not disable
  StateCraft or export functionality.

## Xaero waypoint export

An explicit user action can call the mod-independent utility:

```java
Path destination = gameDirectory.resolve("statecraft").resolve("exports").resolve("xaero");
Path exported = dev.statecraft.compat.XaeroWaypointExporter.exportXaero(snapshot, destination);
```

The public signature is
`static Path exportXaero(TerritorySnapshot snapshot, Path destinationDirectory) throws IOException`.
The caller supplies its current snapshot and a **StateCraft-owned** destination
directory, handles `IOException`, and displays the returned path. This module
does not add a GUI button or initiate exports from snapshot events or logout.

### What is exported

- One point per city ID, also scoped by its nation, state, and dimension.
  Territory without a city ID is skipped.
- **These are approximate reference points, not canonical city centers or
  capitals.** The snapshot has no city-center coordinates or terrain heights.
  The exporter selects the visible claimed chunk nearest that city's visible
  chunk centroid, breaking ties by X then Z. Its waypoint is that chunk's center:
  `x = chunkX × 16 + 8`, `z = chunkZ × 16 + 8`.
- **Y is always the placeholder 64. Do not use these exports as safe teleport
  targets.** Reference points can move as the received window changes.
- Records use the publicly documented legacy, 12-field Xaero waypoint layout:

  ```text
  waypoint:<name>:SC:<x>:64:<z>:11:false:0:gui.xaero_default:false:0
  ```

  These are normal enabled points in the default waypoint set. Color index 11
  is the light-blue color used by the published format examples, **not**
  StateCraft's arbitrary RGB territory color.
- Labels are bounded and sanitized to ASCII; delimiter characters, control
  characters, line separators, and formatting characters cannot inject fields
  or records. A `[SC-<digest>]` suffix identifies the city stably without copying
  unsafe raw IDs into fields. This suffix is a label, **not a native Xaero
  deduplication ID**.
- Dimension identifiers become bounded ASCII basenames plus a full SHA-256
  fingerprint. Neither dimensions nor city IDs become directory paths.
  Sanitization collisions such as `mod:a/b` versus `mod:a_b` remain distinct.
- The UTF-8 file includes informational comment lines and ends in `.txt`.
  Re-export replaces the same dimension's previous file rather than appending;
  an empty snapshot produces no waypoint records. Other dimensions' exports
  are untouched. Export files are static artifacts, not erased on logout.
- Writes use a new sibling staging file and an atomic rename where supported,
  with a replace-rename fallback. Existing symbolic-link output files are
  refused. No automatic writes target `XaeroWaypoints` or `xaero\minimap`.

### Manual import workflow

1. In Xaero's **Minimap**, create a recognizable temporary waypoint in the
   intended world/server **and dimension**, so Xaero creates the correct data
   file. World Map alone is not the waypoint manager.
2. Export through the explicit StateCraft action, then **exit Minecraft** before
   editing Xaero's data. Back up its waypoint files.
3. Locate the file containing your temporary waypoint. Older versions store
   data under the game directory's `XaeroWaypoints`; newer versions can use
   `xaero\minimap`. Sub-world and dimension naming varies: **do not derive the
   destination by renaming StateCraft's export file**.
4. Verify that the export's `# Dimension:` matches. Compare a saved normal
   waypoint with the exported record format. Copy **only the `waypoint:` lines**
   into that dimension's existing waypoint file on new lines. Preserve its
   existing headers, sets, and other records; do not replace the entire file.
5. Restart Minecraft and view the default waypoint set. Verify location and
   dimension before relying on any point. If refreshing a previous import,
   manually remove its old `[SC-...]` records first to avoid stale/duplicate
   points. Xaero's waypoint UI can organize imported points afterward.

There is no general Xaero polygon API used here, no reflective access to its
internals, and no proprietary Xaero code or dependency in StateCraft. The format
reference is a **public community interoperability example**, not an official
versioned Xaero developer API. This implementation does not claim compatibility
with an unspecified future file-format change.

## Verification and sources

Source and artifact verification was performed on **2026-09-12**:

1. [TeamJM's Forge-1.20.x integration guide](https://github.com/TeamJM/journeymap-api/blob/15b07d1e7a71c17c33306f85c18bdf8c426a1870/docs/howto.md)
   explicitly selects `1.20-1.9-SNAPSHOT` for 1.20/1.20.1, documents the repository,
   discovery annotation, and compile-only/no-bundling design.
2. [JourneyMap 5.10.3 Forge-1.20.1 release metadata](https://api.modrinth.com/v2/version/r7FWVNCs)
   explicitly declares API `v1.20-1.9-SNAPSHOT`.
   [JourneyMap 6.0.5 Forge-1.20.1 release metadata](https://api.modrinth.com/v2/version/3pOseiLA)
   declares API 2 and warns that 5.9/5.10 addons do not load or work with 6.
3. [Official Maven metadata](https://jm.gserv.me/repository/maven-public/info/journeymap/journeymap-api/1.20-1.9-SNAPSHOT/maven-metadata.xml)
   resolves to `1.20-1.9-20230608.162656-1`. The published API jar was inspected
   with `javap`, confirming the actual plugin and polygon signatures.
   Its SHA-256 is
   `331f8c56f909ce333a237af24b7db3bd43fd597597bb4119f90c5db247270a69`.
4. Public source contracts at the matching 1.20 release commit:
   [IClientPlugin](https://github.com/TeamJM/journeymap-api/blob/afd8d6e28ba107cf124003124c40d6f3531273bf/src/main/java/journeymap/client/api/IClientPlugin.java),
   [IClientAPI](https://github.com/TeamJM/journeymap-api/blob/afd8d6e28ba107cf124003124c40d6f3531273bf/src/main/java/journeymap/client/api/IClientAPI.java),
   [ClientEvent](https://github.com/TeamJM/journeymap-api/blob/afd8d6e28ba107cf124003124c40d6f3531273bf/src/main/java/journeymap/client/api/event/ClientEvent.java),
   [PolygonOverlay](https://github.com/TeamJM/journeymap-api/blob/afd8d6e28ba107cf124003124c40d6f3531273bf/src/main/java/journeymap/client/api/display/PolygonOverlay.java),
   [MapPolygon](https://github.com/TeamJM/journeymap-api/blob/afd8d6e28ba107cf124003124c40d6f3531273bf/src/main/java/journeymap/client/api/model/MapPolygon.java),
   [ShapeProperties](https://github.com/TeamJM/journeymap-api/blob/afd8d6e28ba107cf124003124c40d6f3531273bf/src/main/java/journeymap/client/api/model/ShapeProperties.java).
5. [MinecraftOnline's public Xaero waypoint records and installation instructions](https://minecraftonline.com/wiki/Minimap_waypoints#Xaero.27s_Minimap)
   demonstrate the exact legacy fields and copy-records/restart workflow.
   [Xaero's official mod description](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap)
   documents waypoint sets, World Map cooperation, version-dependent data
   directory names, backups, and exiting before file edits.

The compatibility JUnit 5 tests cover geometry, bounds, replacement/removal,
mapping restart/logout, representative centers, sanitized labels/IDs/dimensions,
stable output, empty exports, and symbolic-link rejection where the OS permits
creating a test link. Their `@TempDir` factory uses isolated directories under
the project's `build\statecraft-compat-tests`, not the OS temporary directory.
These tests require neither JourneyMap nor Xaero on the runtime classpath.

Run with the parent build after applying `compat.gradle`:

```powershell
.\gradlew.bat :statecraft:test --tests "dev.statecraft.compat.*"
```

An isolated `javac --release 17` / JUnit 5.10.3 run completed with **23 passing
tests and one aborted symlink test** because Windows denied link creation. The
full Forge build belongs to the parent integration. An in-game rendered-map,
Xaero-import, and dedicated-server smoke test was **not** performed here; source,
artifact-signature, and unit-test verification should not be mistaken for those
runtime smoke tests.
