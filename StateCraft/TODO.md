# StateCraft — TODO & Feature Suggestions

*Generated from codebase analysis — February 19, 2026*
*Updated: February 20, 2026 — Diplomacy framework implemented*

---

## 🔴 BUGS (Must Fix)

### ~~1. Diplomacy Policies Stored But Not Fully Applied~~ ✅ FIXED
~~**Severity: High** — War/Alliance/Peace declarations are one-sided.~~

~~`DECLARE_WAR`, `DECLARE_PEACE`, `FORM_ALLIANCE`, and `BREAK_ALLIANCE` in `applyPolicyChange()` only modify the **declaring** nation's allies/enemies sets. The **target** nation is never notified or updated.~~

**Fixed:** Implemented a full `DiplomacyManager` singleton that handles all diplomatic actions bilaterally. All four legislature policy cases (`DECLARE_WAR`, `DECLARE_PEACE`, `FORM_ALLIANCE`, `BREAK_ALLIANCE`) in `LegislatureManager.applyPolicyChange()` now route through `DiplomacyManager` instead of directly modifying nation enemy/ally sets. War declarations update both nations' enemy sets and send mail notifications. Peace and alliance use a proposal/acceptance workflow requiring the target nation leader's consent. Alliance breaks are immediate and bilateral. A new `DiplomacyScreen` GUI (accessible from Country Management) allows leaders to manage all diplomatic actions, view nation relationships, and handle inbound/outbound proposals.

### ~~2. Emergency Powers Are Defined But Have Zero Mechanical Effect~~ ✅ FIXED
~~**Severity: High** — Entire emergency power system is non-functional.~~

~~All 5 emergency powers (`MARTIAL_LAW`, `ECONOMIC_EMERGENCY`, `DIPLOMATIC_CRISIS`, `EMERGENCY_TAX`, `SUCCESSION_CRISIS`) exist only as enum values and can be "invoked" via the legislature, but no code checks for active emergency powers anywhere.~~

**Fixed:** All 5 emergency powers now have full mechanical effects, plus a new Executive Actions GUI accessible only to the nation leader.

**Implementation:**
- **`EmergencyPowerManager.java`** — New singleton handling all mechanical effects. `activatePower()` applies immediate effects. `revokePower()` reverses ongoing effects. `onPowerExpired()` cleans up when timed powers expire (called from `LegislatureManager.tick()`).
- **`MARTIAL_LAW`** — `ProtectionHandler.canInteract()` now checks `EmergencyPowerManager.isMartialLawActive(nationId)`. When active, ALL foreigners are blocked from building/interacting in the nation's territory regardless of open borders. Foreign players currently in the territory receive a warning. Denied message shows `§c§l[MARTIAL LAW]` prefix. Duration: 72 hours.
- **`ECONOMIC_EMERGENCY`** — Economy mod checks `StateCraftIntegration.isEconomicEmergencyActive(nationId)` via reflection to `EmergencyPowerManager`. When active, all treasury withdrawals and transfers from NATION/STATE/CITY accounts are blocked except for the nation leader. Enforced in both `ATMTransactionPacket` withdraw path and `transferFromEntity()`. Duration: 48 hours.
- **`DIPLOMATIC_CRISIS`** — Instantly calls `addEnemy()` on BOTH the declaring and target nation (bilateral). Sends war declaration mail to target nation leader. Notifies all members of both nations. Permanent/instant effect with 14-day cooldown.
- **`EMERGENCY_TAX`** — One-time 10% levy on all citizen balances. Iterates all nation members (members, officers, admins, leader), withdraws 10% via `IntegrationRegistry.withdrawFromPlayer()`, deposits total to nation treasury via `IntegrationRegistry.depositToNation()`. Each taxed citizen receives a chat notification. Instant effect with 60-day cooldown. Double-taxation protection via timestamp tracking.
- **`SUCCESSION_CRISIS`** — Stores original leader, sets new temporary leader via `nation.setLeaderId()`, grants admin rights. Original leader restored on expiration or revocation. Duration: 168 hours (7 days). Temporary leader receives notification.
- **Executive Actions GUI** — New `ExecutiveActionsScreen` accessible from Country Management (leader only). Shows all 5 powers with status (Available/Active/Cooldown), remaining time, description, and invoke/revoke buttons. Powers requiring targets (DIPLOMATIC_CRISIS, SUCCESSION_CRISIS) show a target input field. Confirmation dialog for non-targeted powers. Scrollable list with color-coded status backgrounds.
- **Network packets** — `RequestEmergencyPowerDataPacket` (C→S), `InvokeEmergencyPowerPacket` (C→S with action INVOKE/REVOKE and optional target), `SyncEmergencyPowerDataPacket` (S→C with per-power status, remaining time, and result messages).
- **`CountryManagementScreen`** — Added "⚡ Executive Actions" menu row (visible only to the nation leader) below the Contracts row with a gap separator.
- **`LegislatureManager`** — Emergency power expiration now calls `EmergencyPowerManager.onPowerExpired()` to reverse mechanical effects before deactivating.

### 3. `IMPORT_TARIFF` Stored in PolicyType But No Handler in `applyPolicyChange()`
**Severity: Medium** — Listed in `BugsAndFeaturesList` as known issue.

`PolicyType.IMPORT_TARIFF` is defined but has no `case` in `LegislatureManager.applyPolicyChange()`, no field on `Nation` to store the value, and no marketplace to enforce it against. Even the storage step fails silently.

### 4. `CITIZENSHIP_REQUIREMENTS` Policy Not Applied
**Severity: Low** — Policy exists in `PolicyType` but has no `case` in `applyPolicyChange()` and no field on `Nation`. The value is never stored or displayed anywhere.

### 5. `MINIMUM_WAGE` Policy Not Applied
**Severity: Low** — Same as above. Listed as roleplay-only in `BugsAndFeaturesList`.

### ~~6. `Nation.balance` Field Is a Legacy `long` — Disconnected from Economy~~ ✅ FIXED
~~**Severity: Low** — `Nation` has a `long balance` field with `deposit(long)` and `withdraw(long)` methods. These are completely separate from the `EconomyManager` treasury system which uses `BankAccount` objects with `double` balances. The `Nation.balance` field is saved/loaded but never used by any code path. It's dead weight that could confuse future development.~~

**Fixed:** Removed the legacy `Nation.balance` field and all associated methods (`getBalance()`, `setBalance()`, `deposit(long)`, `withdraw(long)`). The constructor no longer initializes `balance`. The `save()` method no longer writes the `"balance"` key. The `load()` method silently ignores the old `"balance"` key in existing saves for backward compatibility.

The `SyncNationDataPacket` now carries the real treasury balance as a `double` (was `long`) fetched from `IntegrationRegistry.getNationBalance(nationName)` in the server packet handler, which queries the EconomyManager's `BankAccount`-based treasury system. The `NationInfoScreen` was updated to use `double` for the balance field, all `updateData()` overloads, and the `formatBalance()` method (which now shows decimal cents via `String.format("$%.2f", balance)` for small values).

### ~~7. `EmergencyPowerManager` State Not Persisted~~ ✅ FIXED
~~**Severity: Medium** — `EmergencyPowerManager.successionOriginalLeaders` and `emergencyTaxCollected` are in-memory only. On server restart:~~
~~- **Succession Crisis:** The original leader UUID is lost, so when the emergency power expires, the original leader cannot be restored — the temporary leader remains permanent.~~
~~- **Emergency Tax:** The double-taxation timestamp protection resets, but this is low-risk since the emergency power itself is deactivated and on cooldown.~~

**Fixed:** Added `save()`/`load()` NBT methods to `EmergencyPowerManager`. Both `successionOriginalLeaders` (UUID→UUID) and `emergencyTaxCollected` (UUID→Long) maps are now serialized as `ListTag` entries with `CompoundTag` elements. Integrated into `LegislatureManager.save()` and `LegislatureManager.load()` under the `"emergencyPowerManager"` key, which flows through `NationSavedData` automatically. Backward compatible — existing saves without the key are handled gracefully.

### ~~8. Legislature `DECLARE_WAR` Policy Still One-Sided (Bug #1 Partial)~~ ✅ FIXED
~~**Severity: High** — The `DIPLOMATIC_CRISIS` emergency power now correctly declares war bilaterally (both nations updated). However, the normal legislature path via `applyPolicyChange()` → `DECLARE_WAR` case still only calls `nation.addEnemy(enemyId)` on the declaring nation. The target nation's `enemies` set is not updated, so standard legislature war declarations remain asymmetric. Same issue exists for `DECLARE_PEACE`, `FORM_ALLIANCE`, and `BREAK_ALLIANCE`.~~

**Fixed:** All four legislature diplomacy cases now route through `DiplomacyManager` for bilateral effects (see Bug #1 fix). `DECLARE_WAR` calls `DiplomacyManager.declareWar()`, `DECLARE_PEACE` calls `DiplomacyManager.proposePeace()`, `FORM_ALLIANCE` calls `DiplomacyManager.proposeAlliance()`, `BREAK_ALLIANCE` calls `DiplomacyManager.breakAlliance()`. The `resolveNation()` helper resolves nation names/UUIDs from the policy value string.

---

## 🟡 INCOMPLETE / STUBBED FEATURES

### 9. No State/City Elections
Elections only exist for nation leadership. There is no election system for:
- **State Governor** — Appointed by nation leader only, no democratic option
- **City Mayor** — Set at creation, no way to change via election

The `ElectionManager` only handles `nationId`-keyed elections.

### ~~10. No State/City Disband Commands~~ ✅ FIXED
~~`/sc nation disband` exists, but there are no commands or GUI buttons to disband a state or city. `State.removeCity()` exists as a method but is never called from any command or GUI handler. This means orphaned cities/states can never be cleaned up by players.~~

**Fixed:** Added OP-only admin commands for disbanding states and cities:
- `/sc admin deletestate <nation> <state>` — Force deletes a state, unclaiming all chunks in all its cities, removing cities from indexes, and notifying economy integration. Lists available states on name mismatch.
- `/sc admin deletecity <nation> <state> <city>` — Force deletes a city, unclaiming all its chunks and notifying economy integration. Lists available cities on name mismatch.
- Both commands require OP level 2+ (same as all admin commands). Players are NOT removed from the nation — only territory is cleaned up.
- Added `ChunkClaimManager.disbandState()` and `ChunkClaimManager.disbandCity()` methods that handle chunk index cleanup, city/state index cleanup, and economy integration notifications.

### ~~11. No Leadership Transfer Commands~~ ✅ FIXED
~~A nation leader cannot transfer leadership to another player (they can only disband or wait for an election). Similarly, governors and mayors have no transfer mechanism. The only way to change a governor/mayor is through the `/sc admin` commands or officer appointment screens.~~

**Fixed:** Added OP-only admin commands for transferring all leadership roles:
- `/sc admin setleader <nation> <player>` — Set nation leader. Notifies both old and new leader.
- `/sc admin setgovernor <nation> <state> <player>` — Set state governor. Automatically adds as state citizen. Notifies both old and new governor.
- `/sc admin setmayor <nation> <state> <city> <player>` — Set city mayor. Automatically adds as city resident and state citizen. Notifies both old and new mayor.
- All require OP level 2+ and target must be a member of the nation.

### ~~12. PvP Not Controlled by Nation Relationships~~ ✅ FIXED
~~`ProtectionHandler.onAttackEntity()` has a comment `"// Allow PvP based on nation relationships (future enhancement)"` but currently allows all player-vs-player combat in claimed chunks regardless of war/peace status. There's no PvP protection in allied/own territory.~~

**Fixed:** `ProtectionHandler.onAttackEntity()` now fully implements PvP protection based on nation relationships:
- **Same nation** — PvP blocked (configurable via `pvpProtectSameNation`, default `true`)
- **At war (enemies)** — PvP always allowed
- **Allied nations** — PvP blocked (configurable via `pvpProtectAllies`, default `true`)
- **Neutral / nationless** — Vanilla behavior (PvP allowed)
- Admin bypass respected. Config values defined in `StateCraftConfig` under the `diplomacy` section.

### ~~13. No Unread Mail Count / Notification on Login~~ ✅ FIXED
~~When a player logs in, they see contract and election notifications but are **never** notified about unread mail. The `MailManager` has mailboxes but `PlayerJoinHandler` doesn't check for unread messages.~~

**Fixed:** `PlayerJoinHandler.onPlayerJoin()` now checks `MailManager.getInstance().getPlayerMailbox()` for unread messages and notifies the player on login (e.g., "§e[Mail] §fYou have §e3§f unread messages. Use §e/sc gui§f to read your mail."). Runs for all players, not just nation members. Additionally, `ElectionTickHandler` sends a periodic action bar notification ("§e✉ 3 unread mail") every 5 minutes to all online players with unread mail.

### ~~14. Container Detection Is String-Based and Fragile~~ ✅ FIXED
~~`ProtectionHandler.isContainer()` uses `getDescriptionId().toLowerCase().contains(...)` to detect containers. This misses:~~
~~- Modded containers (other mods' chests, storage blocks)~~
~~- Some vanilla blocks (`crafter`, `decorated_pot` in newer versions)~~
~~- Blocks with non-standard names~~

~~Should use the `MenuProvider` interface or `Container` check instead.~~

**Fixed:** Both `isContainer()` and `isInteractable()` now use type-based class hierarchy checks instead of string matching:
- **`isContainer()`** — First checks `instanceof` against known vanilla container block classes (`ChestBlock`, `BarrelBlock`, `ShulkerBoxBlock`, `HopperBlock`, `DropperBlock`, `DispenserBlock`, `AbstractFurnaceBlock`, `BrewingStandBlock`, `AnvilBlock`, `EnchantmentTableBlock`, `BeaconBlock`, `LecternBlock`). Then falls back to checking whether the `BlockEntity` at the position implements `MenuProvider` or `Container` — this catches all modded containers and any StateCraft Economy blocks automatically.
- **`isInteractable()`** — Uses `instanceof` checks against block classes (`DoorBlock`, `TrapDoorBlock`, `FenceGateBlock`, `ButtonBlock`, `LeverBlock`, `DiodeBlock`, `DaylightDetectorBlock`, `NoteBlock`, `BellBlock`, `BedBlock`, `JukeboxBlock`, `CakeBlock`, `CandleCakeBlock`, `FlowerPotBlock`, `CampfireBlock`, `RespawnAnchorBlock`, `DragonEggBlock`, `CommandBlock`, `StructureBlock`).

### ~~15. `MAX_CHUNKS_PER_PLAYER` Config Not Enforced~~ ✅ FIXED
~~`StateCraftConfig.MAX_CHUNKS_PER_PLAYER` is defined but never checked during chunk purchase/ownership transfer. A player can own unlimited private chunks regardless of this setting.~~

**Fixed:** `MAX_CHUNKS_PER_PLAYER` is now enforced at all chunk ownership transfer points, and is controllable via legislature on a per-nation basis:
- **`Nation.maxChunksPerPlayer`** — New field (default 0 = use server config). Saved/loaded with backward compatibility. Getter/setter added.
- **`PolicyType.MAX_CHUNKS_PER_PLAYER`** — New legislature policy in the Territory category (range 0–10000, INTEGER). Allows nations to set their own per-player chunk limit via legislation.
- **`LegislatureManager.applyPolicyChange()`** — New `MAX_CHUNKS_PER_PLAYER` case calls `nation.setMaxChunksPerPlayer()`.
- **`ChunkClaimManager`** — New methods: `getPlayerOwnedChunkCount(UUID)` counts all player-owned chunks across all nations, `getEffectiveMaxChunksPerPlayer(Nation)` returns the nation's limit (if >0) or falls back to server config, `canPlayerOwnMoreChunks(UUID, Nation)` combines both checks.
- **`ClaimResult.PLAYER_CHUNK_LIMIT`** — New enum value for error reporting.
- **Enforcement points:**
  - `ChunkCommand.transferChunk()` — Checks limit before transferring ownership to target player.
  - `StateCraftIntegration.transferChunkOwnership()` — Checks limit via reflection before setting player owner (economy purchase path).
  - `ChunkMarketManager.purchaseChunk()` — Early check via `StateCraftIntegration.canPlayerOwnMoreChunks()` before processing payment, preventing unnecessary refunds.
  - `StateCraftIntegration.canPlayerOwnMoreChunks()` — New static helper method using reflection to call `ChunkClaimManager.canPlayerOwnMoreChunks()`.
- **Error messages** added to `ChunkCommand` switch statements and `ServerPacketHandler` CLAIM handler for the new `PLAYER_CHUNK_LIMIT` result.

---

## 🟢 SUGGESTED FEATURE ADDITIONS

### Governance

#### 14. State & City Elections
Extend `ElectionManager` to support:
- Governor elections per state (voters = city mayors + state citizens)
- Mayor elections per city (voters = city residents)
- Configurable term durations per entity level
- State/city-level constitutional policies

~~#### 15. State Legislature
Currently only nation-level legislature exists. Add state-level legislatures where governors and mayors can vote on state policies (city pass-through rates, state sales tax, etc.). City-level could have resident voting on local issues.~~
Elections for Governors and Mayors only, no state legislatures (for now)

#### ~~16. Impeachment System~~ ✅ IMPLEMENTED
~~Allow legislature members to initiate impeachment of the nation leader (requires supermajority). Currently the only way to remove a leader is waiting for the next election.~~

**Implemented:** Full impeachment system via the existing constitutional amendment legislature flow:
- **`PolicyType.IMPEACH_LEADER`** — New policy under `CONSTITUTIONAL` category (`ValueType.TEXT` for the impeachment reason). Since it's constitutional, it automatically requires a 2/3 supermajority vote and cannot be vetoed by the leader.
- **Safeguards:**
  - The nation leader **cannot** propose an impeachment bill against themselves (blocked in `ServerPacketHandler` bill creation).
  - Impeachment **must be the sole policy change** in a bill — it cannot be bundled with other changes.
- **`LegislatureManager.handleImpeachment()`** — New method triggered when an impeachment bill is enacted:
  - Removes the current leader from power.
  - Appoints a **caretaker leader** (first officer, or first non-leader governor if no officers) to hold the position temporarily.
  - Triggers an **immediate emergency election** via `ElectionManager.startElection()`.
  - Sends in-game chat notifications to **all online nation members** with the impeachment reason, old leader name, and caretaker name.
  - Sends **mail to the impeached leader** explaining the removal and reason.
  - Sends **mail to the caretaker** explaining their temporary appointment.
  - Full logging of all impeachment actions.

~~#### 17. Government Roles & Ministries
Let nations define custom roles (Minister of Defense, Treasury Secretary, etc.) with configurable permissions. Currently only Leader, Admin, Officer, and Member tiers exist.~~
Declined

#### ~~38. Legislature Override of Emergency Powers~~ ✅ IMPLEMENTED
~~The `EmergencyPower` enum mentions "automatic legislature review requirements" but there is no mechanism for the legislature to vote to cancel an active emergency power early. Add a special bill type (e.g., `EMERGENCY_OVERRIDE`) that, if passed by supermajority, immediately deactivates the emergency power and starts its cooldown. This provides a democratic check on executive actions.~~

**Implemented:** Full legislature oversight of emergency powers with automatic ratification and manual override:

- **`Bill.BillType.EMERGENCY_RATIFICATION`** — New bill type: simple majority, bypasses leader signing, cannot be vetoed.
- **`PolicyType.RATIFY_EMERGENCY_POWER`** — New policy (EMERGENCY category). Auto-created when leader invokes any emergency power. If the legislature votes YES, the power is ratified. If NO, the power is **reversed**.
- **`PolicyType.OVERRIDE_EMERGENCY_POWER`** — New policy (EMERGENCY category). Legislature members can propose this to cancel an active emergency power. Simple majority, bypasses leader. Validates target power is actually active.
- **`PolicyType.Category.EMERGENCY`** — New category for emergency power oversight policies.
- **Auto-ratification flow:**
  - When `EmergencyPowerManager.activatePower()` fires, `createRatificationBill()` auto-creates a ratification bill, skips debate, goes straight to voting.
  - Voting duration adapts to the power's duration (ends before the power expires, min 2 hours).
  - Legislature members are notified in chat and via mail.
- **Ratification failure reversal (`EmergencyPowerManager.reverseUnratifiedPower()`):**
  - **MARTIAL_LAW** — Deactivated, open borders restored.
  - **ECONOMIC_EMERGENCY** — Deactivated, treasury access restored.
  - **DIPLOMATIC_CRISIS** — Power deactivated, but war declaration **cannot** be reversed (by design).
  - **EMERGENCY_TAX** — All collected taxes **refunded** to each citizen from nation treasury. Per-player tax amounts tracked in `emergencyTaxAmounts` map (persisted via NBT save/load).
  - **SUCCESSION_CRISIS** — Original leader restored.
- **Manual override (`EmergencyPowerManager.overrideActivePower()`):** Same reversal logic, triggered when an OVERRIDE bill passes.
- **`Legislature.addActiveBill()` / `addDraftBill()`** — New methods for system-generated bills.
- **Safeguards in `ServerPacketHandler`:**
  - `RATIFY_EMERGENCY_POWER` bills cannot be manually proposed by players (auto-created only).
  - `OVERRIDE_EMERGENCY_POWER` bills must be the sole policy, and the target power must be currently active.
- **`LegislatureManager.tickLegislature()`** — Handles ratification bill outcomes: enacted = power ratified (no-op), failed = power reversed via `EmergencyPowerManager`.
- **NBT persistence** — `emergencyTaxAmounts` (nationId → playerId → amount) saved/loaded alongside existing `successionOriginalLeaders` and `emergencyTaxCollected`.

#### ~~39. Configurable Emergency Tax Rate~~ ✅ IMPLEMENTED
~~The `EMERGENCY_TAX` rate is hardcoded at 10%. Make this configurable:~~
~~- Default rate in `StateCraftConfig` (e.g., `emergencyTaxRate = 0.10`)~~
~~- Legislature policy `EMERGENCY_TAX_RATE` to let nations set their own rate~~
~~- Cap at a configurable maximum (e.g., 25%) to prevent abuse~~

**Implemented:** Emergency tax rate is now fully configurable at both server and per-nation levels:
- **`StateCraftConfig.EMERGENCY_TAX_RATE`** — Server default rate (range 0.01–0.20, default 0.10 = 10%).
- **`StateCraftConfig.MAX_EMERGENCY_TAX_RATE`** — Server-enforced maximum rate nations can set (range 0.01–0.50, default 0.20 = 20%).
- **`Nation.emergencyTaxRate`** — Per-nation rate field (default 0 = use server config). Getter/setter with clamping to server max. Saved/loaded with backward compatibility.
- **`PolicyType.EMERGENCY_TAX_RATE`** — New legislature policy in the ECONOMY category (PERCENTAGE, range 0.01–0.20). Nations can vote to set their own rate.
- **`LegislatureManager.applyPolicyChange()`** — New `EMERGENCY_TAX_RATE` case calls `nation.setEmergencyTaxRate()`.
- **`LegislatureManager.getPassedPolicyValue()`** — Returns `nation.getEmergencyTaxRate()` for economy mod queries.
- **`EmergencyPowerManager.applyEmergencyTax()`** — Now uses `nation.getEmergencyTaxRate()` (falls back to `StateCraftConfig.EMERGENCY_TAX_RATE` if 0). Notification messages show the actual rate percentage dynamically.
- **`EmergencyPower.EMERGENCY_TAX`** description updated to mention configurable rate.

#### ~~40. Emergency Power Notification History~~ ✅ IMPLEMENTED
~~Track when emergency powers were invoked/revoked and by whom. Show this history in the Executive Actions screen or a sub-screen. Currently the only record is the law codex entry (if one is created), but in-game there's no browsable history of executive actions.~~

**Implemented:** Full event history tracking and browsable GUI for emergency power actions:

- **`EmergencyPowerManager.EmergencyPowerEvent`** — New inner class recording: power name, event type, actor name, details, and timestamp. Has `save()`/`load()` NBT serialization methods.
- **`EmergencyPowerManager.EventType`** — New enum: `INVOKED`, `REVOKED`, `EXPIRED`, `RATIFIED`, `NOT_RATIFIED`, `OVERRIDDEN`. Each has a display name.
- **`EmergencyPowerManager.powerHistory`** — New `Map<UUID, List<EmergencyPowerEvent>>` tracking last 50 events per nation (`MAX_HISTORY_ENTRIES = 50`). Most recent first.
- **`recordEvent()`** — Called at all key points:
  - `activatePower()` → `INVOKED` (actor = leader name, details = target if any)
  - `revokePower()` → `REVOKED` (actor = leader name)
  - `onPowerExpired()` → `EXPIRED` (actor = "System")
  - `reverseUnratifiedPower()` → `NOT_RATIFIED` (actor = "Legislature")
  - `overrideActivePower()` → `OVERRIDDEN` (actor = "Legislature")
  - `applyPolicyChange(RATIFY_EMERGENCY_POWER)` → `RATIFIED` (actor = "Legislature")
- **NBT persistence** — `powerHistory` saved/loaded per nation alongside existing data. Corrupt entries skipped gracefully on load.
- **`SyncEmergencyPowerDataPacket`** — Extended with `List<HistoryEntry>` containing pre-resolved display names. New `HistoryEntry` inner class with `powerDisplayName`, `eventType`, `actorName`, `details`, `timestamp`.
- **`ServerPacketHandler.sendEmergencyPowerData()`** — Now builds and includes history entries from `EmergencyPowerManager.getHistory()`, resolving enum names to display names.
- **`ExecutiveActionsScreen`** — New "📜 History" tab alongside "⚡ Powers" tab:
  - Toggle between powers view and history view via tab buttons at bottom.
  - History view shows scrollable list of events (6 visible, scrollable).
  - Each entry shows: color-coded event type badge, power name, actor, timestamp (formatted as "MMM dd HH:mm"), and details.
  - Event type colors: Invoked=red, Revoked=green, Expired=gray, Ratified=blue, Not Ratified=orange, Overridden=yellow.
  - Background tint matches event type for visual scanning.
  - Total event count displayed at bottom right.
  - Separate scroll offset for history vs powers views.
- **`getPlayerName()`** — New utility method in `EmergencyPowerManager` to resolve player UUID to name for history recording.

### Territory

#### 18. Chunk Zoning System
Allow cities to designate chunks as specific zones (Residential, Commercial, Industrial, Government, Park). Zones could affect:
- Tax rates (via economy integration)
- Permitted block types (optional enforcement)
- Visual map coloring in the chunk map GUI

#### 19. Nether/End Dimension Claims
Currently claims work across dimensions but there's no policy for how cross-dimensional territory works. Add config options for:
- Whether Nether/End claims count toward city limits
- Separate nation permissions per dimension
- Dimension-specific chunk value multipliers

#### 20. Disputed Territory / Contested Chunks
When nations are at war, allow chunks at borders to become "contested" where both nations' members can interact (but with reduced protections). Currently war only blocks border access.

#### 21. Wilderness Protection Toggle
Currently unclaimed wilderness is completely unprotected — anyone can modify it. Add a server config option for wilderness protection modes:
- `NONE` (current behavior)
- `NATION_ONLY` (only nation members can modify wilderness)
- `FULL` (wilderness is protected, only breaks for resource gathering)

### Diplomacy

#### ~~22. Bilateral Alliance/War System~~ ✅ IMPLEMENTED
~~Alliances and war should require mutual agreement or at least mutual awareness:~~
~~- **Alliance:** Proposing nation sends alliance request → target nation's legislature votes to accept/reject~~
~~- **War:** Declaring nation's legislature votes → target nation is automatically notified and set to enemy status on both sides~~
~~- **Peace Treaty:** Both nations' legislatures must pass peace bills~~

~~Currently alliance/war is completely unilateral (see Bug #1).~~

**Implemented:** Full `DiplomacyManager` singleton with bilateral diplomatic actions:
- **War:** Immediate bilateral — both nations' enemy sets updated, mail sent to target leader, all members notified. Truces block re-declaration for 48 hours.
- **Peace:** Proposal/acceptance workflow — proposer creates pending peace proposal, target leader must accept via DiplomacyScreen. Acceptance removes enemy status on both sides and creates a 48-hour truce.
- **Alliance:** Proposal/acceptance workflow — proposer creates pending alliance proposal, target leader must accept. Acceptance adds both nations to each other's ally sets.
- **Break Alliance:** Immediate bilateral — both nations' ally sets updated, notification sent.
- **Truces:** 48-hour post-peace truces prevent immediate re-declaration of war. Truces are persisted and auto-expire.
- **Persistence:** `DiplomacyManager.save()`/`load()` integrated into `LegislatureManager` → `NationSavedData` flow.
- **Network:** `DiplomacyActionPacket` (C→S), `RequestDiplomacyDataPacket` (C→S), `SyncDiplomacyDataPacket` (S→C).
- **GUI:** `DiplomacyScreen` with Relations/Inbound/Outbound tabs, action buttons (War/Peace/Alliance/Break Alliance), proposal accept/reject, accessible from Country Management for all members (actions leader-only).
- **Config:** `MAX_DIPLOMACY_PROPOSALS` configurable max outbound proposals per nation (default 5).

#### 23. Trade Agreements Between Nations
Let nations establish trade agreements that:
- Lower tariff rates for specific allies
- Allow shared marketplace access
- Enable cross-border currency transfers at reduced fees

#### ~~24. Nation Map / Diplomacy Screen~~ ✅ IMPLEMENTED
~~A GUI screen showing all nations, their relationships (ally/enemy/neutral), borders, and basic stats. Currently players must use `/sc nation list` and individual info commands.~~

**Implemented:** `DiplomacyScreen` shows all nations with color-coded diplomatic status (AT_WAR = red, TRUCE = yellow, ALLIED = green, NEUTRAL = gray). Three tabs: Relations (all nations + status), Inbound (pending proposals to accept/reject), Outbound (proposals sent). Accessible from Country Management for all nation members. Leader-only action bar with target input and War/Peace/Alliance/Break buttons.

### Contracts

#### 25. Contract Completion Verification
Currently, contract milestone approval is manual (leader clicks approve). Add optional automated verification:
- Check that specific block types were placed in designated chunks
- Compare chunk improvement score before/after
- Time-lapse comparison of chunk scans

#### 26. Sub-Contracting
Allow contractors to sub-contract portions of a project to other players, splitting the compensation.

### Mail

#### ~~27. Unread Mail Notifications on Login~~ ✅ IMPLEMENTED
~~Check `MailManager` for unread messages when player joins and display count. Also send an action bar notification periodically while in-game.~~

**Implemented:** Two-part unread mail notification system:
- **Login notification** — `PlayerJoinHandler.onPlayerJoin()` checks `MailManager.getPlayerMailbox()` for unread messages and sends a chat notification (e.g., "§e[Mail] §fYou have §e3§f unread messages. Use §e/sc gui§f to read your mail."). Runs for ALL players, not just nation members. Wrapped in try/catch for robustness.
- **Periodic action bar** — `ElectionTickHandler` runs `sendUnreadMailActionBar()` every 5 minutes (5 tick cycles × 1 min each). Iterates all online players, checks their mailbox unread count, and sends an action bar message ("§e✉ 3 unread mail") to those with unread messages. Non-intrusive — action bar fades naturally without cluttering chat.

#### ~~28. Mail Attachments (Currency)~~ ✅ IMPLEMENTED
~~Allow players to send currency as mail attachments. The recipient claims the money when reading the mail. Would integrate with the economy system.~~

**Implemented:** Full currency attachment system for player-to-player mail, integrated with the economy system:

- **`Mail.java`** — `attachedCurrency` (double) and `currencyClaimed` (boolean) fields with getters/setters. `hasUnclaimedCurrency()` convenience method. New `CURRENCY_TRANSFER` mail type enum value.
- **Compose GUI (`MailComposeScreen.java`)** — Currency input field (`EditBox`) with numeric-only filter (`[0-9.]*`), validation for negative amounts, minimum $0.01 enforcement, and rounding to 2 decimal places. "Attach $:" label displayed alongside the field.
- **`SendMailPacket.java`** — Carries `attachedCurrency` (double) over the network. Constructor overloads for with/without currency. Encoded/decoded via `writeDouble()`/`readDouble()`.
- **Server-side sending (`ServerPacketHandler.handleSendMail()`)** — Validates economy integration is available, checks sender has sufficient balance via `IntegrationRegistry.getPlayerBalance()`, withdraws from sender via `IntegrationRegistry.withdrawFromPlayer()` before sending, sends mail with currency via `MailManager.sendPlayerMailWithCurrency()`. Full error handling for insufficient funds and failed withdrawals.
- **`MailManager.sendPlayerMailWithCurrency()`** — Creates mail with `CURRENCY_TRANSFER` type and sets attached currency amount. Sender's balance must already be withdrawn before calling.
- **View GUI (`MailViewScreen.java`)** — Displays currency attachment indicator: green "💰 $X.XX attached — click Claim below!" if unclaimed, gray "💰 $X.XX (claimed)" if already claimed. Renders a "💰 Claim $X.XX" button (full-width, green) when unclaimed currency exists.
- **Claiming (`RequestMailDataPacket.Action.CLAIM_CURRENCY`)** — Server-side handler validates mail exists and has unclaimed currency, deposits to recipient via `IntegrationRegistry.depositToPlayer()`, marks `currencyClaimed = true`, sends confirmation/error chat messages. Handles already-claimed and economy-unavailable error cases.
- **Network sync (`SyncMailDataPacket.MailInfo`)** — `attachedCurrency` and `currencyClaimed` fields synced to client for inbox display.
- **Persistence (`MailManager`)** — Currency fields serialized to JSON (`attachedCurrency`, `currencyClaimed`) only when amount > 0. Deserialization is backward-compatible with `has("attachedCurrency")` check.

#### ~~29. Broadcast Mail~~ ✅ IMPLEMENTED
~~Allow nation leaders to send a single message to all nation members at once (currently must send individual messages).~~

**Implemented:** Full broadcast mail system allowing nation leaders and officers to send a single message to all nation members at once:

- **`Mail.MailType.BROADCAST`** — New mail type enum value with magenta color code (`§d`). Broadcast messages display with a distinct `[Broadcast]` subject prefix.
- **`MailManager.sendBroadcastMail()`** — New method that iterates all nation member UUIDs, creates individual `BROADCAST`-type mail for each member (excluding the sender), and delivers via `deliverMail()`. Returns the count of mails delivered. Each recipient gets their own mailbox copy with online notification.
- **`SendMailPacket`** — Extended with `broadcast` boolean field. Encoded/decoded via `writeBoolean()`/`readBoolean()`. New 5-arg constructor `(recipientName, subject, body, attachedCurrency, broadcast)`. Existing constructors default to `broadcast = false` for backward compatibility. `isBroadcast()` getter added.
- **`ServerPacketHandler.handleSendMail()`** — Checks `packet.isBroadcast()` first and delegates to new `handleBroadcastMail()` method. Individual mail path unchanged.
- **`ServerPacketHandler.handleBroadcastMail()`** — New private method that: validates sender is in a nation, verifies sender is leader or officer (`nation.isLeaderOrOfficer()`), checks nation has other members, prepends `§d[Broadcast] §f` to subject, calls `MailManager.sendBroadcastMail()`, sends confirmation with delivery count. Full error handling for non-members, insufficient permissions, and empty nations.
- **`MailComposeScreen`** — New broadcast mode toggle:
  - **Toggle button** — "📢 Broadcast" button in top-right corner of compose screen. Magenta when active, gray when inactive. Clicking rebuilds the UI for the selected mode.
  - **Broadcast mode** — Recipient field hidden and disabled. Currency field hidden and disabled (broadcast doesn't support currency attachments). "To:" label replaced with "§d§oAll Nation Members". Title changes to "§d📢 Broadcast to Nation". Send button shows "§d📢 Broadcast" text.
  - **Normal mode** — Standard compose behavior unchanged. Recipient and currency fields visible and editable.
  - Three constructors: `(recipient)`, `(recipient, subject)`, `(recipient, subject, broadcast)`.
- **`MailCommand`** — New `/sc mail broadcast <message>` subcommand:
  - Verifies sender is in a nation and is leader or officer.
  - Parses message into subject/body (first line or first 30 chars as subject).
  - Prepends `[Broadcast]` tag to subject.
  - Calls `MailManager.sendBroadcastMail()` and reports delivery count.
  - Permission: nation leaders and officers only.

### Permissions

#### 30. Per-Chunk Permission Templates
Allow creating named permission templates (e.g., "Residential", "Embassy", "Market") that can be applied to chunks quickly instead of setting permissions chunk-by-chunk.

#### 31. Time-Based Access
Allow granting temporary permissions to specific players (e.g., a builder gets 7 days of BUILD access to specific chunks, then it auto-revokes).

### Quality of Life

#### ~~32. City/State/Nation Rename Commands~~ ✅ IMPLEMENTED
~~Currently, only nation name can be changed via constitutional amendment. Add:~~
~~- `/sc city rename <newname>` (mayor)~~
~~- `/sc state rename <newname>` (governor)~~

**Implemented:** Rename system with three access paths: GUI settings for mayors/governors, OP-only admin commands for all three entity types, and constitutional amendments for nations.

- **City rename via Settings GUI** — `CitySettingsScreen` has a name field. Mayor, state governor, or nation admin can rename. Server validates uniqueness within the state via `handleUpdateEntitySettings()` CITY case. Already existed.
- **State rename via Settings GUI** — `StateSettingsScreen` has a name field. Governor or nation admin can rename. Server validates uniqueness within the nation via `handleUpdateEntitySettings()` STATE case. Already existed.
- **Nation rename restricted to legislature** — `handleUpdateEntitySettings()` NATION case now **blocks** name changes with message: "Nation name can only be changed via a constitutional amendment in the legislature." `NationSettingsScreen` already shows "Name/Flag/Membership changes require legislature bills" and has no name field. Nation rename via `PolicyType.NATION_NAME` constitutional amendment already existed in `LegislatureManager.applyPolicyChange()`.
- **`/sc admin renamenation <nation> <newname>`** — OP-only (permission level 2+). Validates name length (2-32 chars), checks name uniqueness across all nations. Logs the rename.
- **`/sc admin renamestate <nation> <state> <newname>`** — OP-only. Validates name length, checks uniqueness within nation. Lists available states on name mismatch.
- **`/sc admin renamecity <nation> <state> <city> <newname>`** — OP-only. Validates name length, checks uniqueness within state. Lists available states/cities on name mismatch.
- **`COMMANDS.md`** — Documented all three admin rename commands with usage and notes.

#### ~~33. City/State Disband Commands~~ ✅ IMPLEMENTED
~~Add `/sc city disband` (mayor) and `/sc state disband` (governor or nation admin). Should handle:~~
~~- Unclaim all chunks~~
~~- Remove all residents~~
~~- Notify affected players~~
~~- Clean up economy treasury accounts~~

**Implemented** as OP-only admin commands (`/sc admin deletestate` and `/sc admin deletecity`) rather than player-facing commands. Handles chunk unclaiming, index cleanup, and economy integration notifications.

#### 34. Invitation Expiry Display
When viewing pending invitations, show the remaining time before they expire. The `Invitation` class tracks expiry but the GUI doesn't display it.

#### ~~35. Map Minimap Integration~~ ✅ IMPLEMENTED
~~Provide hooks for popular minimap mods (JourneyMap, Xaero's) to display nation borders, chunk ownership, and territory coloring on minimaps.~~

**Implemented:** Full minimap integration framework with soft-dependency support for JourneyMap and Xaero's Minimap, plus a public API for any minimap mod to query territory data.

- **`MinimapIntegration` interface** (`com.statecraft.integration`) — Contract for minimap mod integrations. Methods: `getMinimapName()`, `isAvailable()`, `onChunkDataUpdated(centerX, centerZ, radius)`, `onDimensionChange()`, `cleanup()`. Registered via `IntegrationRegistry.registerMinimapIntegration()`. Third-party mods can implement this interface and register during mod setup.
- **`IntegrationRegistry`** — Extended with minimap integration support: `registerMinimapIntegration()`, `unregisterMinimapIntegration()`, `getMinimapIntegrations()`, `hasMinimapIntegration()`, `notifyMinimapChunkDataUpdated()`, `notifyMinimapDimensionChange()`, `cleanupMinimapIntegrations()`. All notifications wrapped in try/catch for robustness.
- **`JourneyMapIntegration`** (`com.statecraft.client.integration`) — Soft dependency via reflection (no build dependency). Auto-detects JourneyMap via `ModList.get().isLoaded("journeymap")`. Loads JourneyMap API classes (`PolygonOverlay`, `MapPolygon`, `ShapeProperties`) via `Class.forName()`. Creates territory polygon overlays with color-coded fills: green (own), blue (ally), red (enemy), orange (other). Labels show "Nation / State / City". Tracks active overlay IDs for cleanup. Handles dimension changes.
- **`XaerosMinimapIntegration`** (`com.statecraft.client.integration`) — Soft dependency via reflection. Auto-detects both `xaerominimap` and `xaeroworldmap` mod IDs. Maps to Xaero's built-in color palette indices (green=5, blue=3, red=0, orange=7). Tracks discovered city waypoints to avoid re-creation. `getXaeroColorForInfo()` static helper for external use.
- **`MinimapDataProvider`** (`com.statecraft.client.integration`) — **Public API** for any mod to query StateCraft territory data per-chunk. Static methods:
  - `getTerritoryInfo(chunkX, chunkZ)` → `ChunkTerritoryInfo` with nation/state/city names, diplomatic relationship, and overlay colors.
  - `isClaimed(chunkX, chunkZ)` → boolean.
  - `getOverlayFillColor(chunkX, chunkZ)` / `getOverlayBorderColor(chunkX, chunkZ)` → ARGB int colors.
  - `getNationName(chunkX, chunkZ)` / `getTerritoryLabel(chunkX, chunkZ)` → formatted strings.
  - `getRelationship(chunkX, chunkZ)` → `RelationshipType` enum (OWN, ALLY, ENEMY, NEUTRAL, UNCLAIMED).
  - Pre-defined ARGB color constants for fill (25% opacity) and border (75% opacity).
  - `ChunkTerritoryInfo` immutable data class with `overlayFillColor()`, `overlayBorderColor()`, `relationship()`, `label()`.
- **`MinimapIntegrationLoader`** (`com.statecraft.client.integration`) — Auto-detection: scans for JourneyMap and Xaero's on first call, creates and registers available integrations. Idempotent (runs once). Logs which integrations were loaded or a hint to install a minimap mod.
- **`ChunkBorderCache`** — `updateCache()` now calls `IntegrationRegistry.notifyMinimapChunkDataUpdated()` after writing new data. `clear()` now calls `IntegrationRegistry.notifyMinimapDimensionChange()` on dimension change.
- **`ClientEventHandler`** — Calls `MinimapIntegrationLoader.load()` once during first client tick (after player is ready) to detect and register minimap integrations.

### Data Integrity

#### ~~36. Orphan Cleanup on Load~~ ✅ IMPLEMENTED
~~When loading world data, scan for:~~
~~- Cities referencing non-existent states~~
~~- States referencing non-existent nations~~
~~- Chunks referencing non-existent cities~~
~~- Player-nation index entries for players not in any nation's member list~~
~~Log warnings and clean up orphans automatically.~~

**Implemented:** Automatic data integrity scan on every world load, plus a manual admin command.

- **`ChunkClaimManager.runOrphanCleanup()`** — Central orphan detection and cleanup method. Runs four scans in order:
  1. **`cleanupOrphanedStates()`** — Finds states in `stateIndex` whose `nationId` references a non-existent nation, or states not present in their parent nation's `states` map. Cascade-removes all cities and chunks belonging to orphaned states.
  2. **`cleanupOrphanedCities()`** — Finds cities in `cityIndex` whose `stateId` references a non-existent state, or cities not present in their parent state's `cities` map. Cascade-removes all chunks belonging to orphaned cities.
  3. **`cleanupOrphanedChunks()`** — Iterates all dimension chunk maps in `chunkIndex`, removes chunks whose `cityId` references a non-existent city.
  4. **`cleanupOrphanedPlayerEntries()`** — Iterates `playerNationIndex`, removes entries where the nation doesn't exist or the player is not in `nation.isMember()`.
  - Returns total count of removed entries. Marks data dirty if any orphans found.
  - All orphans logged at WARN level with `[OrphanCleanup]` prefix, including entity names, IDs, and counts.
  - Clean result logged at INFO level: "Data integrity check passed — no orphans found."
- **`NationSavedData.load()`** — Calls `manager.runOrphanCleanup()` after all nations, invitations, elections, legislature, contracts, and companies are loaded. Runs every world load automatically.
- **`/sc admin audit`** — OP-only command (permission level 2+) to manually trigger orphan cleanup at any time. Reports before/after statistics (nation count, chunk count) and number of orphaned entries removed. Directs operator to check server log for detailed breakdown.
- **`COMMANDS.md`** — Documented `/sc admin audit` command.

#### 37. Admin Data Inspection Commands
Add admin commands for debugging:
- ~~`/sc admin audit` — Run integrity check on all data~~ ✅ (implemented as part of #36)
- `/sc admin stats` — Show detailed server stats (total nations, states, cities, chunks, players, orphans)
- `/sc admin chunk <x> <z> raw` — Show raw NBT/UUID data for a chunk

---

## 📋 PRIORITY ORDER (Recommended)

1. ~~**Bug #8** — Legislature DECLARE_WAR still one-sided~~ ✅ FIXED (all 4 diplomacy cases now route through DiplomacyManager)
2. ~~**Bug #2** — Emergency powers non-functional~~ ✅ FIXED (all 5 powers now have mechanical effects + Executive Actions GUI)
3. ~~**Bug #7** — EmergencyPowerManager state not persisted~~ ✅ FIXED (save/load NBT integrated into LegislatureManager flow)
4. ~~**Bug #1** — Diplomacy one-sided~~ ✅ FIXED (full DiplomacyManager with bilateral effects, proposals, truces, GUI)
5. **Bug #3** — Import tariff no handler (Medium — fails silently)
6. ~~**Feature #33** — City/State disband commands~~ ✅ FIXED (OP-only `/sc admin deletestate` and `/sc admin deletecity`)
7. ~~**Feature #11** — Leadership transfer~~ ✅ FIXED (OP-only `/sc admin setleader`, `setgovernor`, `setmayor`)
8. ~~**Feature #22** — Bilateral diplomacy~~ ✅ IMPLEMENTED (DiplomacyManager + DiplomacyScreen + persistence)
9. ~~**Feature #12** — PvP protection~~ ✅ FIXED (already implemented with diplomacy — configurable same-nation/ally PvP blocking, war allows PvP)
10. ~~**Feature #13** — Unread mail notification~~ ✅ FIXED (PlayerJoinHandler now checks mailbox on login)
11. **Feature #9** — State/City elections (Medium — governance gap)
12. ~~**Feature #14** — Container detection fix~~ ✅ FIXED (type-based `instanceof` + `MenuProvider`/`Container` fallback)
13. ~~**Feature #38** — Legislature override of emergency powers~~ ✅ IMPLEMENTED (auto-ratification bills, manual override bills, tax refunds, reversal on failed ratification)
14. ~~**Bug #15** — MAX_CHUNKS_PER_PLAYER not enforced~~ ✅ FIXED (enforced at all transfer points + legislature policy for per-nation control)
15. ~~**Feature #16** — Impeachment system~~ ✅ IMPLEMENTED (constitutional bill type, 2/3 majority, caretaker appointment, emergency election)
16. ~~**Feature #39** — Configurable emergency tax rate~~ ✅ IMPLEMENTED (server config default + max cap, per-nation legislature policy, dynamic rate in notifications)
17. ~~**Feature #40** — Emergency power notification history~~ ✅ IMPLEMENTED (event tracking, NBT persistence, History tab in Executive Actions GUI, color-coded scrollable event log)
18. ~~**Feature #27** — Unread mail notifications on login~~ ✅ IMPLEMENTED (login chat notification + periodic 5-min action bar for all online players)
19. ~~**Feature #28** — Mail attachments (currency)~~ ✅ IMPLEMENTED (currency field in compose GUI, sender balance withdrawal, recipient claim button, economy integration, persistence)
20. ~~**Feature #29** — Broadcast mail~~ ✅ IMPLEMENTED (broadcast toggle in compose GUI, `/sc mail broadcast` command, leader/officer only, delivers to all nation members)
21. ~~**Feature #32** — City/State/Nation rename~~ ✅ IMPLEMENTED (city/state rename via settings GUI for mayors/governors, nation rename blocked to legislature only, OP admin commands for all three)
22. ~~**Feature #35** — Map minimap integration~~ ✅ IMPLEMENTED (MinimapIntegration API, JourneyMap + Xaero's soft-dependency plugins, MinimapDataProvider public API, auto-detection loader, ChunkBorderCache notifications)
23. ~~**Feature #36** — Orphan cleanup on load~~ ✅ IMPLEMENTED (automatic scan on world load, `/sc admin audit` manual trigger, cascade cleanup of states→cities→chunks→player-index)
24. ~~**Feature — Shareholder voting system**~~ ✅ IMPLEMENTED (ShareholderProposal + ShareholderVoteManager, 8 proposal types: dividend rate, issue shares, share buyback, dissolve, convert to/from bank, remove officer, dividend period. Share-weighted votes with 25% quorum + >50% majority. 48h voting window, 24h cooldown per type. Votes tab in Company GUI. Auto-resolution + mail notifications to all shareholders. Persistence via NationSavedData.)

