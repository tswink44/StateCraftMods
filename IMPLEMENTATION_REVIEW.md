# Implementation Review

**Reviewed:** September 12, 2026

**Baseline:** `master` at `546fc3641e15be3a3f95ee7542cc091525e88b0c`

**Implementation:** StateCraft and StateCraft Economy 2.1.0, Minecraft 1.20.1 / Forge 47.3.22

**Resolution:** All 13 items in this original review have now been addressed. See [IMPLEMENTATION_FOLLOWUP.md](IMPLEMENTATION_FOLLOWUP.md) for the implemented changes, additional findings, and UI/UX-first feature proposals.

This historical review covers governance and economy workflows, shared persistence and server integration, with supporting inspection of networking, forms, client code, and development tooling. Its evidence and line references describe the baseline above, before the fixes were applied.

The report identifies **10 correctness/reliability findings** and **3 additional improvement areas**. The most important themes are financial decisions using out-of-date interest, existing workflows becoming impossible to complete, and persistence paths that bypass shared safeguards. Production-scale performance and graphical-client behavior were not measured.

## Prioritized findings

**P2** means a substantive correctness or reliability issue to schedule for correction. **P3** means a lower-priority tooling or maintenance improvement. Conditions such as optional configuration settings or malformed imported data are stated explicitly below.

| ID | Priority | Finding |
| --- | --- | --- |
| F1 | P2 | Bank reserve decisions omit earned but unprocessed deposit interest |
| F2 | P2 | Loan application and approval can exceed the borrower debt limit |
| F3 | P2 | Mandatory property-tax collection loses former owners after their final sale |
| F4 | P2 | Shortened UUID recipients silently resolve to unintended payment accounts |
| F5 | P2 | Lowering the contract chunk limit blocks existing contract payouts |
| F6 | P2 | Legislation-only mode leaves local tax policies without an update path |
| F7 | P2 | The superior-official removal guard also rejects the superior leader |
| F8 | P2 | Malformed treaty terms pass startup integrity review and crash ratification |
| F9 | P2 | Backups can contain financial integration locks older than their economy data |
| F10 | P2 | Economy console mutations report success before persisting their changes |

### F1. Bank reserve decisions omit earned but unprocessed deposit interest

**Locations:** `statecraft-economy\src\main\java\dev\statecraft\economy\Banking.java:253-285,288-299`; `statecraft-economy\src\main\java\dev\statecraft\economy\EconomyEngine.java:192-198`

Reserve calculations include stored deposit balances and `pendingInterest`, but not interest earned since `lastAccruedAt`. An ordinary authorized bank-to-wallet transfer can therefore consume money already owed to depositors if scheduled accrual has not yet processed the elapsed periods.

For example, with a zero fixed reserve, a 10,000-cent deposit and 1,000 cents of bank capital, ten elapsed 1% interest periods create another 1,000 cents of obligations. A 1,000-cent outflow is allowed before maintenance, leaving 10,000 cents of bank cash against 11,000 cents of liabilities after accrual. Processing maintenance first rejects the same outflow. Authorization should not depend on which of those operations happens first.

**Recommended improvement:** Calculate deposit liabilities through the transaction timestamp before authorizing bank outflows. Reuse that calculation wherever bank spendable funds or reserve requirements are evaluated, while preserving each deposit's contractual interest terms.

### F2. Loan application and approval can exceed the borrower debt limit

**Locations:** `statecraft-economy\src\main\java\dev\statecraft\economy\Banking.java:343-349,411-435`

`requestLoan()` calculates exposure using stored interest without bringing existing loans current. Approval also lacks an aggregate borrower-limit recheck, so an application can remain fundable after the underlying exposure has increased.

With a 1,000-cent borrower limit, an existing 800-cent loan that has earned 40 cents of unprocessed interest still permits a new 200-cent application. Refreshing the first loan afterward does not prevent approval: active debt becomes 1,040 cents. Refreshing interest before the application correctly rejects it.

**Recommended improvement:** Calculate current aggregate exposure at both application and funding, including relevant outstanding applications and interest through the decision timestamp. Use the saved loan terms rather than assuming scheduled maintenance has already updated every loan.

### F3. Mandatory property-tax collection loses former owners after their final sale

**Locations:** `statecraft-economy\src\main\java\dev\statecraft\economy\PropertyMarket.java:138-141,175-177,210-237`; `statecraft-economy\src\main\java\dev\statecraft\economy\Taxation.java:140-155`

When `mandatoryPropertyTaxes=true`, the periodic collection path visits the current owner of each claim. A sale correctly leaves the seller responsible for earlier assessments, but selling their final claim removes that seller from the automatic collection path.

In the observed sequence, the seller retained 900 cents of arrears and received 1,000 cents in sale proceeds. Subsequent maintenance passes left the proceeds untouched and collected nothing for the receiving treasury. The debt is not deleted; it simply stops receiving the mandatory collection attempts needed to settle it.

**Recommended improvement:** Drive arrears collection from outstanding property-tax obligations or a persistent debtor queue, independently of current claim ownership. Keep former owners eligible for collection after sales and repossessions until their obligations are settled.

### F4. Shortened UUID recipients silently resolve to unintended payment accounts

**Locations:** `statecraft-economy\src\main\java\dev\statecraft\economy\EconomyEngine.java:104-109`; `statecraft-economy\src\main\java\dev\statecraft\economy\Ledger.java:44-56`

Java's `UUID.fromString()` accepts some shortened UUID components. Converting its result back to a string before calling `Ledger.account()` hides the malformed original input from the ledger's stricter account validation.

For example, `pay 1-1-1-1-1 1.00` reports a completed payment and sends 100 cents to `player:00000001-0001-0001-0001-000000000001`. A mistyped identifier is thereby transformed into a different account rather than rejected. Supplying the same shortened identifier through the explicit `player:` account path is rejected.

**Recommended improvement:** Require the complete UUID shape before normalization and preserve the remembered-player-name lookup as a separate fallback. Apply one consistent recipient rule across raw UUIDs and prefixed player accounts.

### F5. Lowering the contract chunk limit blocks existing contract payouts

**Location:** `statecraft\src\main\java\dev\statecraft\domain\Commerce.java:819-824`

Contract completion calls `validateContractChunks()`, which applies the current `maxContractChunks` creation ceiling to an already-awarded selection. Lowering that configuration value can therefore prevent settlement of work that was valid when accepted.

Create and award a paid two-chunk contract, submit the work, then reduce the limit to one. Completion fails with `Invalid contract chunk selection.`, leaving the contract `SUBMITTED`, the contractor unpaid, and the money in escrow. Independent operator approval does not bypass this condition; restoring the configuration is needed to complete the unchanged award.

**Recommended improvement:** Separate creation/selection capacity rules from settlement-integrity rules. Existing awards should remain settleable after a limit reduction, while ownership, reservations, accepted-bid identity, and escrow requirements remain enforced.

### F6. Legislation-only mode leaves local tax policies without an update path

**Location:** `statecraft\src\main\java\dev\statecraft\domain\GovernanceEngine.java:569-575`

With `requireLegislationForPolicy=true`, legitimate nonoperator officials cannot directly change state or city tax policies. However, bills accept only nation targets, and enacted national tax rates intentionally do not inherit into states and cities.

A state tax-setting command is rejected as requiring legislation, while proposing the corresponding bill against the state is rejected because the target must be a nation. Enacting a 500-basis-point national tax policy leaves nation/state/city rates at `500/0/0`. Local rates are effectively frozen for ordinary officials instead of becoming legislatively manageable.

**Recommended improvement:** Either scope the direct-setting restriction to governments that have a working legislative route, or add authorized subordinate-government targets to national policy bills. Preserve the existing independent local-tax semantics rather than implicitly introducing inheritance.

### F7. The superior-official removal guard also rejects the superior leader

**Location:** `statecraft\src\main\java\dev\statecraft\domain\GovernanceEngine.java:513-521`

The removal guard compares the target's superior offices with the selected government, without sufficiently accounting for the caller's authority.

For example, a national leader can manage a descendant city, but cannot remove an ordinary city member who is also that nation's officer. The command rejects the action as a subordinate government expelling a superior official, even though the caller is the national leader and removing city membership would preserve the target's national citizenship and office. The form excludes the same target.

**Recommended improvement:** Evaluate the caller's relevant authority alongside the target's ancestor offices, rather than treating every action against a subordinate government as an action by a subordinate official. Share the corrected rule between command authorization and form eligibility, while preserving leadership-transfer safeguards.

### F8. Malformed treaty terms pass startup integrity review and crash ratification

**Location:** `statecraft\src\main\java\dev\statecraft\domain\Integrity.java:311-323`

**Precondition:** This finding requires malformed persisted or imported treaty data, not an ordinary valid command sequence.

An active territorial treaty with a missing/null chunk-term `fromCity` can load without an integrity issue. The proposal's status, signatories, and monetary terms are examined, but the individual territorial terms' required references are not sufficiently covered.

When both ratification ballots pass, settlement dereferences the missing source city and throws `NullPointerException`. The proposal has already become `READY`; the settlement path catches `UserError`, not that unchecked exception. Money and land have not transferred, but the exception can escape scheduled processing instead of placing the bad record into the repair workflow.

**Recommended improvement:** Examine active treaty terms' required fields, canonical chunk keys, and city references during loading and auditing. Make settlement reference handling null-safe so malformed records produce an actionable domain error rather than an unchecked exception.

### F9. Backups can contain financial integration locks older than their economy data

**Locations:** `statecraft\src\main\java\dev\statecraft\runtime\ServerRuntime.java:178-185,215-241,312-317`; `statecraft\src\main\java\dev\statecraft\persistence\WorldStore.java:161-168`

`flush()` refreshes the persisted integration locks before saving. Both manual and periodic backup paths instead call `store.backup()` directly. That method saves the current registered models, including an integration-lock object that may still represent the previous flush.

If a scheduled financial operation makes an account or claim newly encumbered between flushes, a backup can contain the new economy state but the old offline protections. Restoring that snapshot without Economy can permit destructive governance actions that should remain blocked. The offline implementation reads the saved lock sets, and the government deletion guard skips the live-balance check when Economy is unavailable.

The command gateway's later flush can repair the primary snapshot after a manual backup, but it cannot repair the already-created backup file. The periodic path can also leave the primary snapshot temporarily inconsistent until the next normal flush.

**Recommended improvement:** Centralize snapshot preparation so every save and backup refreshes integration metadata immediately before serialization. Route both backup entry points through that shared preparation rather than relying on a subsequent flush.

### F10. Economy console mutations report success before persisting their changes

**Location:** `statecraft-economy\src\main\java\dev\statecraft\economy\forge\StateCraftEconomy.java:281-289`

The non-player command path calls `engine.execute()`, marks the runtime dirty, and immediately sends success. Unlike both the player gateway and the core console path, it neither flushes the snapshot nor examines the persistence outcome before acknowledging the operation.

A successful console balance change can therefore remain memory-only until another action, world save, or the normal 600-tick flush. A crash during that window loses an acknowledged change. A newly occurring disk problem is also not surfaced to the caller when the operation reports success.

**Recommended improvement:** Route console and player mutations through a common execution/persistence gateway. Persist before acknowledging success and preserve the existing paused-state and uncertain-outcome messaging when a write fails.

## Additional improvement areas

These are separate from the behavioral defects above.

### I1. Bound large-world work on the server thread

**Priority:** P2 scalability improvement; impact has not been benchmarked.

**Locations:** `statecraft\src\main\java\dev\statecraft\runtime\ServerRuntime.java:148-150,302-306,323-345`; `statecraft\src\main\java\dev\statecraft\persistence\WorldStore.java:127-130`

Territory synchronization rebuilds a government lookup and scans all claims for each player, including the recurring five-second refresh. The eight-chunk radius bounds the response to 289 cells, but it does not bound the work required to construct that response. Work grows with total governments and claims multiplied by online players.

Separately, every successful gateway command marks the runtime dirty and flushes. `WorldStore.save()` deep-copies stored sections and serializes every live section before deciding that nothing changed. Genuinely read-only menu refreshes can therefore incur world-size serialization work on the server thread.

**Recommended improvement:** Use dimension/chunk indexes or bounded coordinate lookups for map construction, and introduce reliable mutation/revision tracking to avoid unnecessary full snapshots. Do not simply exempt commands by name: some apparently read-only operations update interest or other state and must retain their persistence behavior.

### I2. Update or clearly retire obsolete helper scripts

**Priority:** P3

**Locations:** `run-combined.ps1:1-12`; `sync_registry_values.py:8-12`; `verify_config.py:3`

The combined-run helper changes into `StateCraft` and invokes a module-local wrapper that does not exist. Its explanation also describes the old integration direction: the current build loads both mods through the economy project's run configuration, not the core project's run configuration.

The price helpers retain old development/configuration paths, including a developer-specific absolute path. These scripts are not dependable entry points for a fresh checkout of the current layout.

**Recommended improvement:** Have the combined launcher invoke the root wrapper's `:statecraft-economy:runClient` task from a directory anchored to `$PSScriptRoot`. Update retained price helpers to accept explicit current-format input paths, or move them into a clearly documented legacy-tools area.

### I3. Stop tracking generated Gradle cache and lock state

**Priority:** P3

**Locations:** `.gitignore:1-5`; `.gradle\buildOutputCleanup\cache.properties:1-2`; `.gradle\buildOutputCleanup\buildOutputCleanup.lock`

Generated `.gradle` files remain tracked despite the ignore rule. The committed cleanup cache identifies Gradle 9.0.0, while the current wrapper uses 8.8; running the documented build updates this tracked metadata and its binary lock. This produces unrelated worktree changes and unnecessary machine-specific churn.

**Recommended improvement:** Remove generated Gradle cache and lock files from version control while keeping the ignore rules. Review tracked private IDE state separately, preserving any intentionally shared project settings.

## Recommended implementation order

1. Correct financial decision-time calculations and recipient handling: F1-F4.
2. Keep valid governance workflows operable and reject malformed state safely: F5-F8.
3. Unify persistence entry points: F9-F10, then address the scaling and tooling improvements.

Documented constraints such as roleplay-only policies and separate Minecraft player-inventory persistence are not counted as new defects in this report.
