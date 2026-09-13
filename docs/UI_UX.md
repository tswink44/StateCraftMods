# StateCraft client UI and recovery

## Entry points and navigation

Version 2.3 introduces dedicated personal and government blades. These do not use the generic search/results panel described below for legacy and auxiliary sections.

**My Dashboard** shows name (not clickable), actual nation/state/city citizenship, personal wallet and bank-deposit accounts, positive company shareholdings, and distinct cities containing privately owned chunks. City labels use `[CITY] (State/Nation)`. Clicking citizenship, a company or a property city opens that exact overview; clicking a balance opens its scoped ATM, never the bank company's asset account. Property outside a city is labeled explicitly and links to its state or nation. Each holdings section pages independently, and Back retains those page selections. Personal mail and invitation acceptance are available from the dashboard's **Personal inbox** button.

**Government overviews** contain name, saved flag/description, leadership, permitted treasury information, child governments or claims, and officers. States show cities; cities show their territory. Treasury amounts retain existing access controls. Actions are grouped by the actual government's powers. Invitation and officer management are inside **Settings**, which is visible only to authorized government managers. The old Citizens & Roles, Officers, Invitations and Communications tabs are hidden; their command definitions remain for compatibility and authorization.

Official mail is reached through the selected government's **Official inbox**, and each request rechecks authority over that particular mailbox. Personal inbox data remains private even from operators.

**National territory maps** offer claim, nation-to-state allocation and state-to-city allocation modes. They display player-centered, loaded surface terrain with ownership overlays. Unloaded terrain is identified rather than guessed or force-loaded. Cell selection itself performs no mutation; reviewed batches are sent to the server with exact hidden identifiers.

**Crafting recipes** have a graphical guide with ingredient/output item slots and live grid layouts, rather than generated command text. Shared buttons, cards, headers, hover/focus states and financial color cues are used across auxiliary screens. Color does not replace text, keyboard access or status explanations.

**N still opens the categorized StateCraft menu.** **Overview & Help** now lists only **My Dashboard** and **Help**. Help opens command help and crafting recipes as sub-pages, not separate category entries. StateCraft, Player Profiles, Your Operations and Economy Pending Work are hidden from navigation, and there is no permanent Activity tab. The dashboard (`statecraft:dashboard`) requests bounded, sender-scoped personal overview data; it does not download unrestricted domain models. Internal profile/receipt/detail routes remain available where needed.

### Player-facing information

Ordinary views, dropdowns, maps and reviews display names, roles, readable coordinates, percentages and dates instead of UUIDs, account keys, raw enum values or millisecond timestamps. Missing historical names use descriptive fallbacks, not identifiers. Private message and law text is not scrubbed or rewritten.

Identity remains separate from presentation: selecting a row or dropdown still sends its exact hidden reference. Two records with the same name are not merged, and changing a displayed label does not change authorization or monetary ownership. Technical details remain available in explicit operator diagnostics and the optional Advanced controls.

Sections and detail panels share the management screen:

- **Search** applies a server-side, bounded name/ID filter. Previous/Next preserve that filter.
- Select a row and choose **Open details**, double-click it, or focus the results panel and press Enter. The client sends `UiQuery.detail(row.entity())`; it does not extract action targets from display text.
- **Actions** includes the server's contextual, prefilled actions before the section's registered actions, without repeating the same generic action. A prefilled action uses its exact page, template, and values. An unavailable action opens its explanation instead of submitting anything. These explanations stay in the action picker rather than cluttering the ordinary results.
- **Back** restores the preceding category, page, or entity, including committed search, pagination, scroll, selected row, advanced input, and intentional query output. **Sections** returns to categorized navigation without removing the previous view from history.
- **Close** in categorized navigation returns directly to the game without discarding its workspace or pending operations.
- **My dashboard** remains available from sections. Loan links use the same typed detail flow: the server supplies current autopay mode, next due date, principal/interest/status, separately labeled original terms, and authorized repayment/autopay actions. The client never infers current autopay from historical agreement prose.
- **Map** opens the nearby territory map without replacing the current section's view state.

Navigation data belongs to a world/player workspace, not a `Screen`. A client session caches up to four world/player workspaces, 32 Back entries and 24 saved section/entity views per workspace. Reopening a saved view preserves its context; returning after disconnect marks read-only data stale. Private drafts and view contents are memory-only. They are not shared between different world UUIDs or players and do not survive a client restart.

## Queries, mutations, and advanced commands

The GUI distinguishes action intent rather than guessing success from English response text:

| Intent | Submission and result |
| --- | --- |
| Query | Uses no operation ID. Shows the requested query's output. Refresh repeats that exact captured query, including its form values, rather than the section default. |
| Navigation | Opens the registered destination locally. It is not a transaction. |
| Mutation | Requires a server quote, followed by explicit confirmation of those terms. Completion updates a separate status banner and refreshes the same current read-only query without clearing its filter, pagination, or selection. |
| Advanced/raw | Conservatively requires server review unless the command is the registered section query. Raw results remain inspectable and copyable. “Review again” requests another review; it never automatically repeats the command. |

The **Advanced** toggle reveals the command box and technical copy tools; they are collapsed by default to leave more room for results. Hiding the controls preserves their draft and selection. Enter in the command box follows the same review/query rules as its button. Registered action forms do not render arbitrary replacement templates.

Typed results offer **Copy row**, which copies the displayed information without an appended ID. Right-click does the same. **Advanced** reveals **Copy ID** and **Copy all** for diagnostic or command use. Left-click identifier copying is restricted to deliberately opened raw/advanced results. Wrapping does not truncate the copied source row.

## Forms and review cards

Forms retain values while moving between action pickers, choice pickers, custom-value editors, reviews, and sections. Up to 24 ordinary drafts are retained per workspace; drafts belonging to pending operations are pinned within the 128-operation bound.

- The footer is fixed. Field pages are computed from actual input heights and reserved field-message space; Previous uses the previous calculated boundary, not the number of fields on the current page.
- At supported GUI sizes starting at **320×240**, multiline inputs, field messages, paging controls, status explanations, and the action footer occupy separate regions.
- Resizing restores the page containing the focused field and its caret/selection. Multiline editing uses Minecraft's text-field editing model with explicit retained selection, rather than reflection into private widget fields.
- Action filtering updates existing row buttons, not the search input. Search caret/selection survive editing and actual screen resizing. Empty searches have explicit no-results messages.
- Server `FormConstraints` set input limits and local whole-number, money, range, required-field, and alternative-token checks (including `all` where offered). These checks supplement server validation; they do not grant eligibility.
- Field errors appear directly below their fields. The focusable status button opens full validation/prerequisite explanations when the inline text is too long. Refresh choices explicitly clears stale preview field errors and asks for current metadata.

**Review action** obtains a server quote; it does not execute the action. The review card shows parties, quantities, fees, taxes, totals, funds and warnings with readable labels, without repetitive **Material**/**Information** tags or operation UUIDs. All supplied terms remain available; the material flags and exact identities still participate in the unchanged confirmation checks. The captured request is immutable.

Quotes expire after the server's 30-second validity window. The client uses the server-time offset, disables confirmation at expiry, and refreshes a stale session clock. A quote is never silently refreshed and confirmed. **Refresh quote** is explicit; changed material terms are labeled and must be read and confirmed again. Rejection, changed eligibility, and repricing preserve the original form. Server field errors return to the corresponding fields.

## Submitted operations outlive their screens

The global operation owner is `ClientHooks` and its pure `PendingOperations` model. Closing a screen, pressing Escape, opening the map, or changing section does **not** cancel a submitted server operation.

Submission first retains the exact in-memory selection and writes its minimal recovery reference locally. Only then is the reviewed operation sent. The original form's inputs are locked until a known terminal result. Opening another contextual form for the same registered action returns the captured, locked draft rather than creating a replacement purchase/payment. Advanced commands are similarly locked for their page while an advanced operation is unresolved.

All request watches time out from the central client tick, including forms and views that are no longer visible. A submitted-action timeout is **uncertain**, not an ordinary rejected form. A late terminal receipt can still reconcile the operation, independently of the original page or screen.

**Attention** is shown only when an action is pending or recovery storage needs attention. It is available from navigation, sections and affected forms/reviews; healthy navigation has no Activity entry.

The recovery screen's **Server receipts** button opens the internal `statecraft:operations` route, which is not listed under Overview & Help. Operators can open `statecraft:admin_operations` from Administration. Inspecting another player's authorized receipt does not adopt it as the inspector's pending operation. The server supplies reconciliation actions and eligible-operation choices; ordinary players cannot use administrative resolution.

| Server outcome | Client behavior |
| --- | --- |
| `COMPLETED` | Show the receipt; remove the pending reference; unlock the original form; refresh current read-only data without replacing it with a success string. |
| `REJECTED` | Known not executed. Preserve original values and display the reason; remove the pending reference. |
| `REVIEW_REQUIRED` | Known not executed. Preserve the form and request new terms only when the player explicitly asks for another review. |
| `UNCERTAIN` | Keep the reference and locked inputs. Offer **Check status**, not a new submission. |
| `UNKNOWN` | Keep the reference and locked inputs. An unknown/evicted receipt is not evidence that the action failed. Do not repeat it. |
| `READY` | The original cached operation has not executed. **Retry safely** is enabled only when this client still holds its exact original selection. It sends the same operation ID and values, never a newly invented operation. |

Check status is read-only. Reconnect schedules bounded status checks for restored references, never automatic retries. The client retains up to 128 pending operations and 64 terminal session receipts. It refuses to start another mutation when the pending bound is reached instead of forgetting unresolved purchases.

Replies are correlated with request ID, world/player scope, connection generation, and the expected form/query/selection. An old world, player, request, or query cannot overwrite a new view. Operation replies are routed by world and operation reference; late terminal receipts remain useful after the request timeout.

## Local recovery privacy and storage

Recovery files are stored under:

```text
gameDir\statecraft\pending\<server-issued-world-UUID>\<player-UUID>.pending
```

The bounded, versioned UTF-8 file contains only:

1. `STATECRAFT_PENDING_V1`
2. The world UUID
3. The player UUID
4. One operation UUID per subsequent line

No command templates, values, amounts, recipient names, mail bodies, server addresses, or other private action contents are written into that file. Writes force the replacement file to storage and use a same-directory atomic replacement where supported. A malformed or wrongly scoped file is not silently discarded or overwritten. Storage problems prevent new submissions and expose **Reload recovery** after write access is repaired.

After a restart, the client can reconcile a stored reference but deliberately cannot reconstruct its private request. Even `READY` does not enable a retry without the original in-memory selection. New GUI business mutations are blocked while restored, unidentified operations remain unresolved; queries and navigation remain available. Only the exact registered `UiMenus.RESOLVE` and `UiMenus.RECOVER` administrative forms bypass that restored-reference lock. They still require server authorization, eligible inputs, a reviewed quote, and writable local recovery storage. This exception grants access to review, not permission to execute an administrative command.

### Recovering an unknown or evicted receipt

1. Open **Attention**, select the unresolved action, and choose **Copy support details**. This copies the hidden **world UUID, player UUID, and operation UUID**, never the private command or form body. Give the copied details to the operator; they are not printed in normal navigation.
2. The operator verifies the world and audits the relevant account, inventory, and world history. Existing unresolved receipts use **Reconcile operation**. For a missing or evicted receipt, **Recover unknown receipt** uses the registered template `admin operation recover <player> <operation> <resolution> <reason>`. The player must be an explicit UUID; the audited resolution is `completed` or `not_executed`, with a reason.
3. Recovery creates a durable, owner-scoped terminal tombstone and invalidates any live cached review for that owner/ID. It **never executes, repeats, refunds, or undoes the original command**. Existing final receipts and mismatched owners cannot be overwritten.
4. The affected player then chooses **Check status** on the original local reference. Only that original operation's known terminal receipt clears its lock. Completion of the operator's separate recovery action does not itself dismiss a player's UNKNOWN reference.

UNKNOWN is never dismissed automatically and never enables Retry safely. Do not delete the local reference or repeat the purchase through chat; deleting a local file does not cancel or undo server effects.

### Important save boundary

Operation IDs prevent duplicate execution within the suite's reviewed-operation protocol. They do **not** make physical cash or item actions crash-atomic across Minecraft player-inventory saves and StateCraft world snapshots. A durable suite receipt and a separately saved inventory may represent different points in time after a process crash. An operator may need to inspect both the player's inventory and the relevant world/receipt recovery data. Never treat “unknown,” a lost connection, or a write failure as permission to repeat a physical-cash withdrawal or purchase.

## Map boundaries and accessibility

The nearby map uses `ChunkKey.MAX_COORDINATE` for selectable cells. Unsupported cells are dark with a distinct stripe, have an explicit unavailable explanation, cannot construct a `ChunkKey` from a click, and are excluded from export targets. Selected valid claims expose **Open claim** through a typed claim reference. Supported wilderness exposes **Claim options**, opening the server's wilderness details and authorized claim action with the exact selected chunk prefilled. Both paths use canonical chunk references; inspecting wilderness does not claim it or bypass the form, review, or server eligibility checks.

- **Tab / Shift+Tab:** standard predictable widget focus.
- **Results panel:** Up/Down select rows; Home/End select endpoints; Page Up/Down or the wheel scroll; Enter opens a selected typed entity.
- **Control+C / Control+Shift+C in results:** copy the full selected row / all results.
- **Action search:** Enter opens the sole match or focuses the first matching row; filtering never reconstructs the input.
- **Choice search / section search:** Enter runs that search.
- **Multiline input:** Enter inserts a newline; it never confirms a transaction.
- **Map:** arrow keys select supported nearby cells; right-click copies a valid chunk.
- **Escape / Back:** return to the previous screen or navigation entry; submitted operations remain owned globally.

Status text is not conveyed by color alone. Review lines have readable labels, selected rows use a marker, outcomes have explicit names, and disabled actions expose keyboard-accessible explanations. Standard controls, text panels, and multiline fields supply narration messages. Full status/validation screens also support copying.

## Localization and extension contracts

English framework/menu entries are in `tools\translations\client.json`; the resource generator merges translation fragments into the checked-in core language resource. Resource packs can override:

- `gui.statecraft.*` for framework controls, outcomes, validation, review, navigation, and recovery.
- `gui.statecraft.category.<enum-name-lowercase>` and `.description`.
- `gui.statecraft.page.<namespace>.<page>`.
- `gui.statecraft.action.<namespace>.<page>.<template-slug>`, where the complete registered template is lowercased, every non-`[a-z0-9]` run becomes `_`, and a trailing `_` is removed. For example, `loan repay <loanId> <amountOrAll>` becomes `loan_repay_loanid_amountorall`.

`UiText` keys and string arguments are rendered with `Component.translatable` when the key exists. Its fallback is already fully rendered and is used literally otherwise; the client does not format the fallback again. Player names and server domain prose remain literal unless the server supplies a `UiText` key.

Integration hooks include `ClientHooks.session`, `reply`, `formReply`, `viewReply`, `previewReply`, `dashboardReply`, `governmentReply`, `open`, and `territory`. The retained legacy watch hooks remain available. Protocol **6** adds bounded personal/government overview responses and represents absent state/city allocations explicitly while retaining national identity and named owners. Client and server modules must agree on the protocol. Presentation providers own entity rows, contextual eligibility and friendly guided-query text; the client never derives mutation commands by parsing those rows.

## Regression coverage

The pure tests in `dev.statecraft.client.state` require no Minecraft window or GL context:

```powershell
.\gradlew.bat :statecraft:test --tests "dev.statecraft.client.state.*"
```

Coverage includes request/world/player/connection correlation, abandoned watcher expiry, uncertain/unknown/ready recovery, late receipts, bounded history, captured drafts, ref-only persistence and scope rejection, navigation restoration, query-vs-mutation refresh, raw-result retention, exact page boundaries, focused-field resize anchoring, numeric constraints, search selection state, server-time expiry, and world-edge selection/export math. These tests complement, rather than claim to replace, an in-game visual/narrator pass.
