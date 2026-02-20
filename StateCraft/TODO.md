# StateCraft — TODO & Feature Suggestions

*Generated from codebase analysis — February 19, 2026*

---

## 🔴 BUGS (Must Fix)

### 1. Diplomacy Policies Stored But Not Fully Applied
**Severity: High** — War/Alliance/Peace declarations are one-sided.

`DECLARE_WAR`, `DECLARE_PEACE`, `FORM_ALLIANCE`, and `BREAK_ALLIANCE` in `applyPolicyChange()` only modify the **declaring** nation's allies/enemies sets. The **target** nation is never notified or updated. For example, if Nation A declares war on Nation B:
- Nation A's `enemies` set includes Nation B ✅
- Nation B's `enemies` set does **not** include Nation A ❌
- No notification is sent to Nation B
- Open borders check on Nation B side won't block Nation A players

This means war/alliance is completely asymmetric and the target nation has no awareness.

**Fix:** When applying `DECLARE_WAR`, also call `targetNation.addEnemy(declaringNation.getId())`. Same for alliances (both sides) and peace (both sides remove). Send mail notifications to the target nation's leader.

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

### 8. Legislature `DECLARE_WAR` Policy Still One-Sided (Bug #1 Partial)
**Severity: High** — The `DIPLOMATIC_CRISIS` emergency power now correctly declares war bilaterally (both nations updated). However, the normal legislature path via `applyPolicyChange()` → `DECLARE_WAR` case still only calls `nation.addEnemy(enemyId)` on the declaring nation. The target nation's `enemies` set is not updated, so standard legislature war declarations remain asymmetric. Same issue exists for `DECLARE_PEACE`, `FORM_ALLIANCE`, and `BREAK_ALLIANCE`.

**Fix:** Update the `DECLARE_WAR`, `DECLARE_PEACE`, `FORM_ALLIANCE`, and `BREAK_ALLIANCE` cases in `LegislatureManager.applyPolicyChange()` to modify both nations and send mail notifications. This is the remaining portion of original Bug #1.

---

## 🟡 INCOMPLETE / STUBBED FEATURES

### 9. No State/City Elections
Elections only exist for nation leadership. There is no election system for:
- **State Governor** — Appointed by nation leader only, no democratic option
- **City Mayor** — Set at creation, no way to change via election

The `ElectionManager` only handles `nationId`-keyed elections.

### 10. No State/City Disband Commands
`/sc nation disband` exists, but there are no commands or GUI buttons to disband a state or city. `State.removeCity()` exists as a method but is never called from any command or GUI handler. This means orphaned cities/states can never be cleaned up by players.

### 11. No Leadership Transfer Commands
A nation leader cannot transfer leadership to another player (they can only disband or wait for an election). Similarly, governors and mayors have no transfer mechanism. The only way to change a governor/mayor is through the `/sc admin` commands or officer appointment screens.

### 12. PvP Not Controlled by Nation Relationships
`ProtectionHandler.onAttackEntity()` has a comment `"// Allow PvP based on nation relationships (future enhancement)"` but currently allows all player-vs-player combat in claimed chunks regardless of war/peace status. There's no PvP protection in allied/own territory.

### 13. No Unread Mail Count / Notification on Login
When a player logs in, they see contract and election notifications but are **never** notified about unread mail. The `MailManager` has mailboxes but `PlayerJoinHandler` doesn't check for unread messages.

### 14. Container Detection Is String-Based and Fragile
`ProtectionHandler.isContainer()` uses `getDescriptionId().toLowerCase().contains(...)` to detect containers. This misses:
- Modded containers (other mods' chests, storage blocks)
- Some vanilla blocks (`crafter`, `decorated_pot` in newer versions)
- Blocks with non-standard names

Should use the `MenuProvider` interface or `Container` check instead.

### 15. `MAX_CHUNKS_PER_PLAYER` Config Not Enforced
`StateCraftConfig.MAX_CHUNKS_PER_PLAYER` is defined but never checked during chunk purchase/ownership transfer. A player can own unlimited private chunks regardless of this setting.

---

## 🟢 SUGGESTED FEATURE ADDITIONS

### Governance

#### 14. State & City Elections
Extend `ElectionManager` to support:
- Governor elections per state (voters = city mayors + state citizens)
- Mayor elections per city (voters = city residents)
- Configurable term durations per entity level
- State/city-level constitutional policies

#### 15. State Legislature
Currently only nation-level legislature exists. Add state-level legislatures where governors and mayors can vote on state policies (city pass-through rates, state sales tax, etc.). City-level could have resident voting on local issues.

#### 16. Impeachment System
Allow legislature members to initiate impeachment of the nation leader (requires supermajority). Currently the only way to remove a leader is waiting for the next election.

#### 17. Government Roles & Ministries
Let nations define custom roles (Minister of Defense, Treasury Secretary, etc.) with configurable permissions. Currently only Leader, Admin, Officer, and Member tiers exist.

#### 38. Legislature Override of Emergency Powers
The `EmergencyPower` enum mentions "automatic legislature review requirements" but there is no mechanism for the legislature to vote to cancel an active emergency power early. Add a special bill type (e.g., `EMERGENCY_OVERRIDE`) that, if passed by supermajority, immediately deactivates the emergency power and starts its cooldown. This provides a democratic check on executive actions.

#### 39. Configurable Emergency Tax Rate
The `EMERGENCY_TAX` rate is hardcoded at 10%. Make this configurable:
- Default rate in `StateCraftConfig` (e.g., `emergencyTaxRate = 0.10`)
- Legislature policy `EMERGENCY_TAX_RATE` to let nations set their own rate
- Cap at a configurable maximum (e.g., 25%) to prevent abuse

#### 40. Emergency Power Notification History
Track when emergency powers were invoked/revoked and by whom. Show this history in the Executive Actions screen or a sub-screen. Currently the only record is the law codex entry (if one is created), but in-game there's no browsable history of executive actions.

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

#### 22. Bilateral Alliance/War System
Alliances and war should require mutual agreement or at least mutual awareness:
- **Alliance:** Proposing nation sends alliance request → target nation's legislature votes to accept/reject
- **War:** Declaring nation's legislature votes → target nation is automatically notified and set to enemy status on both sides
- **Peace Treaty:** Both nations' legislatures must pass peace bills

Currently alliance/war is completely unilateral (see Bug #1).

#### 23. Trade Agreements Between Nations
Let nations establish trade agreements that:
- Lower tariff rates for specific allies
- Allow shared marketplace access
- Enable cross-border currency transfers at reduced fees

#### 24. Nation Map / Diplomacy Screen
A GUI screen showing all nations, their relationships (ally/enemy/neutral), borders, and basic stats. Currently players must use `/sc nation list` and individual info commands.

### Contracts

#### 25. Contract Completion Verification
Currently, contract milestone approval is manual (leader clicks approve). Add optional automated verification:
- Check that specific block types were placed in designated chunks
- Compare chunk improvement score before/after
- Time-lapse comparison of chunk scans

#### 26. Sub-Contracting
Allow contractors to sub-contract portions of a project to other players, splitting the compensation.

### Mail

#### 27. Unread Mail Notifications on Login
Check `MailManager` for unread messages when player joins and display count. Also send an action bar notification periodically while in-game.

#### 28. Mail Attachments (Currency)
Allow players to send currency as mail attachments. The recipient claims the money when reading the mail. Would integrate with the economy system.

#### 29. Broadcast Mail
Allow nation leaders to send a single message to all nation members at once (currently must send individual messages).

### Permissions

#### 30. Per-Chunk Permission Templates
Allow creating named permission templates (e.g., "Residential", "Embassy", "Market") that can be applied to chunks quickly instead of setting permissions chunk-by-chunk.

#### 31. Time-Based Access
Allow granting temporary permissions to specific players (e.g., a builder gets 7 days of BUILD access to specific chunks, then it auto-revokes).

### Quality of Life

#### 32. City/State/Nation Rename Commands
Currently, only nation name can be changed via constitutional amendment. Add:
- `/sc city rename <newname>` (mayor)
- `/sc state rename <newname>` (governor)

#### 33. City/State Disband Commands
Add `/sc city disband` (mayor) and `/sc state disband` (governor or nation admin). Should handle:
- Unclaim all chunks
- Remove all residents
- Notify affected players
- Clean up economy treasury accounts

#### 34. Invitation Expiry Display
When viewing pending invitations, show the remaining time before they expire. The `Invitation` class tracks expiry but the GUI doesn't display it.

#### 35. Map Minimap Integration
Provide hooks for popular minimap mods (JourneyMap, Xaero's) to display nation borders, chunk ownership, and territory coloring on minimaps.

### Data Integrity

#### 36. Orphan Cleanup on Load
When loading world data, scan for:
- Cities referencing non-existent states
- States referencing non-existent nations
- Chunks referencing non-existent cities
- Player-nation index entries for players not in any nation's member list
Log warnings and clean up orphans automatically.

#### 37. Admin Data Inspection Commands
Add admin commands for debugging:
- `/sc admin audit` — Run integrity check on all data
- `/sc admin stats` — Show detailed server stats (total nations, states, cities, chunks, players, orphans)
- `/sc admin chunk <x> <z> raw` — Show raw NBT/UUID data for a chunk

---

## 📋 PRIORITY ORDER (Recommended)

1. **Bug #8** — Legislature DECLARE_WAR still one-sided (High — only DIPLOMATIC_CRISIS does bilateral, normal legislature path is broken)
2. ~~**Bug #2** — Emergency powers non-functional~~ ✅ FIXED (all 5 powers now have mechanical effects + Executive Actions GUI)
3. ~~**Bug #7** — EmergencyPowerManager state not persisted~~ ✅ FIXED (save/load NBT integrated into LegislatureManager flow)
4. **Bug #1** — Diplomacy one-sided (High — war/alliance broken; Bug #8 is the remaining part)
5. **Bug #3** — Import tariff no handler (Medium — fails silently)
6. **Feature #33** — City/State disband commands (High — no way to clean up)
7. **Feature #11** — Leadership transfer (High — leader locked in position)
8. **Feature #22** — Bilateral diplomacy (High — fixes Bug #1 / #8 properly)
9. **Feature #12** — PvP protection (Medium — war has no combat mechanics)
10. **Feature #13** — Unread mail notification (Medium — easy QoL win)
11. **Feature #9** — State/City elections (Medium — governance gap)
12. **Feature #14** — Container detection fix (Medium — protection bypass risk)
13. **Feature #38** — Legislature override of emergency powers (Medium — democratic check on executive power)

