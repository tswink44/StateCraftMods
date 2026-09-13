# Implementation Fixes and UI/UX Follow-up

**Review date:** September 12, 2026

**Implementation completed:** September 13, 2026, StateCraft **2.2.0**

**Scope:** The seven follow-up fixes and the full UI/UX feature roadmap.

All **13 original review items remain implemented**. All **seven follow-up findings** and **nine UI/UX roadmap features** below are now implemented as well. The original observations are retained as historical context, not an outstanding defect list.

Current controls and guarantees are described in [the UI guide](docs/UI_UX.md) and [world-data recovery](docs/WORLD_DATA.md). Matching 2.2 client/server JARs are required for protocol 4. A manual graphical/narrator playthrough remains separate from the implemented automated coverage.

| Follow-up item | Implemented resolution |
| --- | --- |
| U1 | Typed outcomes, server-issued reviewed operation IDs, durable prepared/final receipts, scoped local references, same-ID reconciliation, and audited operator recovery of unknown receipts. |
| U2 | Filtering updates existing action rows without recreating the focused search input; caret/selection and explicit empty states are preserved. |
| U3 | Fixed-footer form layouts compute stable page boundaries and preserve focus across resize; field errors occupy reserved space. |
| U4 | Unsupported map cells are shaded, explained, and excluded from selection/export; valid claims open typed detail references. |
| W1 | Expected collection refusals preserve purchase success and explain queued delivery rather than suggesting payment failed. |
| W2 | Current autopay and next-due state are shown separately from original terms, with authorized repayment/autopay actions. |
| W3 | Authorized historical bid inspection remains available after a former contractor company closes. |

The further storage/journal architecture discussion at the end remains a future scaling consideration; it was not part of the requested UI/UX feature roadmap.

## Original review resolution

| Item | Implemented change |
| --- | --- |
| F1 | Bank reserve decisions include elapsed interest using saved deposit terms without mutating the ledger during reserve planning. |
| F2 | Loan application and approval both evaluate current borrower exposure, including relevant pending applications without double-counting them. |
| F3 | Mandatory property-tax arrears have independent, bounded collection work, including former owners after their final sale or repossession. |
| F4 | Raw and prefixed player-account identifiers use consistent canonical UUID rules; shortened identifiers cannot silently become different recipients. |
| F5 | Existing contracts retain a settlement path after `maxContractChunks` is reduced; ownership, reservation, and escrow protections remain enforced. |
| F6 | Per the selected policy, legislation is required for national settings only. Authorized state/city officials retain local policy control; tax inheritance is unchanged. |
| F7 | Superior-office removal restrictions account for the caller's authority, with the same eligibility rule used by commands and forms. |
| F8 | Active territorial treaty terms receive stronger load/audit and settlement safeguards; malformed references produce actionable failures rather than null dereferences. |
| F9 | The world store prepares financial integration locks before every snapshot, including direct saves and both backup paths. |
| F10 | Economy console operations use the same persistence-aware execution gateway as player and core console operations. |
| I1 | Map construction performs at most 289 canonical point lookups and caches local hierarchy data. Explicit domain mutation callbacks avoid full snapshots for genuinely read-only requests. Activity timestamps remain included in periodic and forced saves. |
| I2 | The combined launcher uses the root wrapper and correct project. Current value inspection takes explicit paths; the Numismatics adapter is isolated under `tools\legacy`, rejects current cent-denominated input, and previews without overwriting input files. |
| I3 | Generated Gradle caches and private Copilot IDE migration state are no longer tracked. Local files and intentionally shared IDE settings remain intact. |

The original observations remain in [IMPLEMENTATION_REVIEW.md](IMPLEMENTATION_REVIEW.md) as a historical baseline. Gameplay documentation was updated alongside the affected behavior.

## Additional UI/UX findings

These historical findings describe pre-existing behavior discovered in the follow-up pass and fixed in 2.2. Source line references below refer to the pre-fix review snapshot. **P2** denoted a substantive correctness/reliability concern; **P3** denoted a lower-priority usability or edge-case issue.

| ID | Priority | Finding |
| --- | --- | --- |
| U1 | P2 | An uncertain server transaction outcome is treated as an ordinary correctable form error |
| U2 | P3 | Action search replaces its focused input widget on every edit |
| U3 | P3 | Previous-field navigation does not return to the previous page boundary |
| U4 | P3 | The map allows clicks on cells outside the supported coordinate range |

### U1. Preserve uncertainty across the server response and form state

**Locations:** `statecraft\src\main\java\dev\statecraft\network\SuiteNetwork.java:113-130`; `statecraft\src\main\java\dev\statecraft\client\ActionFormScreen.java:160-161,285-303,318-324`

The action protocol exposes only a success boolean and display text. If a payment changes memory but persistence fails, the server correctly warns the player not to repeat it. However, `actionResult()` processes that response like any other rejected input: it refreshes choices and eventually enables Confirm again. Only the client-side timeout path sets `uncertain = true`.

After an operator restores saving, the old form can submit the same payment again. The warning text helps, but the UI does not enforce the distinction between an invalid request and an operation that may already have completed.

**Improvement:** Introduce structured action outcomes such as rejected, completed, and outcome-uncertain. Keep the original values visible, but block resubmission of an uncertain operation until its result has been reconciled. Do not infer this state by matching English error text. Durable operation IDs would also make recovery after lost replies safer.

### U2. Keep the search widget alive while updating action results

**Location:** `statecraft\src\main\java\dev\statecraft\client\ActionPickerScreen.java:30-38`

The search responder calls `rebuildWidgets()` for every edit. That creates another `EditBox`, restoring the search string but not the previous caret or selection. Editing an existing search term therefore cannot reliably preserve the input state where the player was typing.

**Improvement:** Keep the focused search widget and update only the matching action rows and pagination controls. Preserve caret/selection across genuine layout rebuilds such as resizing, and provide an explicit empty-results message.

### U3. Remember page boundaries for variable-length forms

**Location:** `statecraft\src\main\java\dev\statecraft\client\ActionFormScreen.java:66-76,118-128`

Previous fields subtracts the number of fields shown on the current page, even when that count differs from the preceding page. For a five-field action at GUI height 270, the first page shows three fields and the second shows two. Navigation goes from offset `0` to `3`, but Previous returns to `1`, not `0`.

Multiline inputs and error messages also change page capacity. Values are retained, but players encounter overlapping pages and extra backward steps instead of predictable navigation.

**Improvement:** Store prior page-start offsets, or replace field pagination with a scrollable form that keeps the footer fixed. Reconcile the visible range when errors or window size change.

### U4. Treat out-of-world map cells as unavailable, not wilderness

**Location:** `statecraft\src\main\java\dev\statecraft\client\TerritoryMapScreen.java:108-129,141-151`

The client draws the full radius grid even when the player's center lies within eight chunks of the supported coordinate boundary. Cells beyond that boundary look like wilderness and remain clickable. Clicking one constructs an invalid `ChunkKey`, whose `UserError` is not handled by the screen.

The bounded server lookup introduced for I1 correctly avoids these coordinates, but it does not alter this pre-existing client click behavior.

**Improvement:** Shade these cells as outside the supported world range, exclude them from selection/export targets, and use the shared `ChunkKey.MAX_COORDINATE` bound instead of allowing an exception to escape the mouse handler.

## Additional workflow findings

| ID | Priority | Finding |
| --- | --- | --- |
| W1 | P2 | A committed marketplace purchase is reported as failed when automatic collection is refused |
| W2 | P2 | Loan details show original autopay consent without the current repayment mode |
| W3 | P2 | Historical contract bids disappear from the selector after the contractor company closes |

### W1. Confirm the purchase independently of automatic item collection

**Location:** `statecraft-economy\src\main\java\dev\statecraft\economy\EconomyCommands.java:240-246`

The purchase commits payment and adds the bought items to the delivery queue before attempting automatic collection. If collection is refused, its error suppresses the purchase confirmation and makes the entire command appear unsuccessful.

In the reproduced refusal sequence, the first attempt reduced the buyer's balance from 1,000 to 900 cents, credited the seller 100 cents, and queued one item, but returned `Inventory changed.`. Repeating the apparent failed purchase produced balances of 800/200 cents and two queued items. Both items could subsequently be collected. This is misleading transaction status and repeat-purchase risk, not loss of the queued items.

**Improvement:** Once purchase settlement commits, return purchase confirmation. Treat expected collection refusals as pending delivery, with the reason and an explicit instruction/action to collect queued items. Unexpected collection failures should still be surfaced and logged, but must not imply that payment was rolled back. This can be improved using the existing success response before undertaking the larger structured-outcome work in U1.

### W2. Separate current autopay state from original contract terms

**Locations:** `statecraft-economy\src\main\java\dev\statecraft\economy\Banking.java:499-512`; `statecraft-economy\src\main\java\dev\statecraft\economy\EconomyCommands.java:430-434`

Changing autopay updates `loan.autoPay`, while `loan show` continues to display the original terms string's `automatic payments=` value without a separate current-mode field.

After an automatically paid loan is changed with `loan autopay <id> false`, the actual flag becomes false, but the displayed terms still say `automatic payments=true`. At the next installment no automatic payment occurs, even when wallet funds are sufficient. A borrower returning later to inspect the loan can therefore mistake the original agreement text for the current repayment setup.

**Improvement:** Show the current autopay mode prominently, including in loan-selector details, and explicitly label the retained terms as the original agreement. Do not rewrite historical contract terms just to update today's operating status.

### W3. Keep authorized historical bid inspection available

**Location:** `statecraft\src\main\java\dev\statecraft\domain\GovernanceForms.java:552-555`

Complete a company-backed government contract, then legitimately dissolve the unencumbered contractor company. An authorized government official can still inspect the retained bids with `contract bids <id>`, but the Review bids form no longer offers the contract and clears an explicitly supplied ID.

`contractEligible()` requires the old company payee to exist before reaching the read-only bid-inspection authorization. A condition needed for applicable payments is therefore blocking historical inspection, even though the records and command authorization remain valid.

**Improvement:** Evaluate authorized read-only bid inspection before requiring a live payment destination. Keep destination and escrow requirements for mutations that actually depend on them. Historical labels should tolerate a former company while retaining its stable identifier.

## UI/UX-first feature roadmap

All features in this table are delivered in 2.2. The order column preserves their original roadmap priority. Server-side authority, material quote re-evaluation, and submission-time permission checks remain in place.

| Order | Proposal | User benefit | Important implementation boundary |
| --- | --- | --- | --- |
| First | Structured transaction outcomes and operation IDs | Players can recover from delays or failures without guessing whether another click will pay twice. | Requires a versioned packet change and server-side result reconciliation; a client-only retry flag is insufficient. |
| First | Stable navigation and persistent view context | Returning from an action preserves the section, search, page, scroll position, and selected entity. | Store view state separately from screen instances; keep stale server results identifiable and refreshable. |
| Next | Separate action feedback from section data | A successful purchase can show a concise success banner while the updated listing/balance remains visible. | Classify query, navigation, and mutation actions explicitly; blindly refreshing every action would overwrite intentional search results. |
| Next | Transaction review cards and server quotes | Show human-readable parties, account, quantity, fees/taxes, total, and available funds before confirmation. | Quotes must expire or be re-evaluated when prices, permissions, collateral, or inventory change. |
| Next | Contextual detail screens and prefilled actions | Selecting a company, claim, bill, contract, or listing leads directly to relevant actions without copying IDs between screens. | Use typed entity references rather than parsing presentation text; continue rechecking authorization on the server. |
| Next | Field constraints and responsive forms | Money, counts, bounds, missing prerequisites, and input errors become understandable before submission. | Extend server-provided form metadata; local validation supplements rather than replaces domain rules. |
| Next | A focused loan-management panel | Make current autopay, the next due date, outstanding amounts, and repayment actions visible together. | Display live operating state separately from original terms and use the existing authorized commands for changes. |
| Later | A role-aware pending-work dashboard | Bring invitations, bills, contract approvals, loan applications, arrears, and unread mail into one actionable view. | Use bounded, authorized summaries and deep links instead of downloading entire domain models. |
| Later | Localization, narration, and keyboard polish | Improve accessibility and reduce dependence on tooltips, color, and mouse navigation. | Reuse Forge translation components, predictable focus order, and explicit disabled-state explanations. |

### Useful existing foundations

The typed form schema, dependency-aware selections, retained values after rejected input, server-side eligibility, and command authorization are worth preserving. Improvements should build on those mechanisms rather than replacing them with client-only rules.

Transaction recovery for cash and item actions must also account for Minecraft player-inventory persistence, which remains separate from the suite snapshot. Operation IDs alone do not make that cross-save boundary atomic.

The former extension points below were expanded into explicit action intent, typed entity/view/action contracts, constrained fields, reviewed operations, and separately retained client navigation/result state:

- `statecraft\src\main\java\dev\statecraft\api\MenuPage.java:6-15` currently represents actions with only a label and command. Explicit action intent would support safe post-action refresh and richer confirmation flows.
- `statecraft\src\main\java\dev\statecraft\api\form\FormField.java:6-9` distinguishes text, multiline, and choices, but does not carry numeric constraints, field-level errors, or transaction-preview metadata.
- `statecraft\src\main\java\dev\statecraft\client\ManagementScreen.java:51-60,130-144,250` recreates views on navigation and uses one output area for both action feedback and section results.

## Further architecture improvement

Read-only requests no longer pay the previous full-snapshot cost, but a real mutation still serializes the combined world state and rebuilds integration metadata. For substantially larger worlds, consider incremental indexes and a durable transaction journal with periodic coherent snapshots. Do not replace durable acknowledgments with an asynchronous success response: that would reintroduce the persistence inconsistency fixed in F10.

Production-scale throughput has not been measured, so this is a scaling proposal rather than a claim of a demonstrated performance threshold.

## Remaining boundaries

Unknown outcomes still require reconciliation, not optimistic resubmission. Retention is bounded, so audited tombstone recovery is available when a local reference outlives its receipt. Physical inventory and suite saves remain separate; translations cover the UI framework and keyed presentation metadata, while unkeyed server prose remains literal. Large-world storage throughput and interactive visual/narrator usability remain areas for further measurement rather than unverified guarantees.
