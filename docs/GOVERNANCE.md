# StateCraft governance

This document describes the implemented, Minecraft-independent governance domain.
Forge command registration, server configuration files, persistence/backup I/O,
screens, keybindings, networking and world-event adapters live in the runtime.

## Command conventions

- Use `/statecraft` or `/sc`. Examples below omit that prefix. `execute` accepts
  either prefix, or just the command body.
- Quote multiword names, subjects and descriptions: `nation create "United Cities"`.
  Quotes and backslashes inside a quoted argument can be escaped with `\`.
- Narrative fields (mail bodies, law text, descriptions, reasons and proposals)
  support escaped `\n` and `\t` inside quoted arguments. GUI multiline values use
  these escapes automatically. CR/CRLF normalize to LF. Names, titles, mail
  subjects, identifiers and scalar policies remain single-line; other control
  characters are rejected.
- A government/company reference accepts its name or UUID. Government names are
  unique across **all three levels**, case-insensitively; company names have their
  own namespace. Names use 3–48 characters by default: letters, digits, spaces,
  underscores, periods, apostrophes and hyphens.
- A player reference is a known current name or UUID. Players must have logged in.
  Names refresh on login and on every command. An arbitrary UUID is not an operator.
- Monetary command amounts are **dollars**, nonnegative, with at most two decimal
  places. Persistent amounts and monetary configuration fields are **integer cents**.
  The `baseChunkValue` government policy also takes **cents**, not dollars.
- Chunk keys are `dimension|x|z`, for example `minecraft:overworld|0|0`.
  `here` means the actor's current chunk where supported.
- `<required>` and `[optional]` denote arguments; omit the brackets. `a|b` denotes
  alternatives. `-` is a literal placeholder only where explicitly documented.
- Commands are limited to 4096 characters and 64 arguments. Unknown actions,
  invalid input and expected permission/financial failures raise `UserError`.
  Responses are bounded by `maxCommandOutput`; lists accept one-based pages.
  Pages use up to `pageSize` rows, automatically fewer for long entries so that
  entire rows do not disappear behind packet-output truncation.

`help [section] [page]` documents all families in-game. Sections are `governments`,
`chunks`, `elections`, `legislature`, `executive`, `diplomacy`, `companies`,
`contracts`, `mail`, `profiles` and `admin`. `info` reports world counts and economy
availability. `profile [player]` shows public citizenship, offices and company
participation; only one's own profile includes an unread personal-mail count.

## Governments and citizenship

Hierarchy is **Nation → State → City → Claimed Chunks**. A player has at most one
nation, one state in that nation, and one city in that state. Ancestor citizenship
is required before joining a descendant. These player membership fields are the
canonical index; government member snapshots are derived, not independently edited.

Minimal live setup, using the **same fresh/unaffiliated actor**, unique names, an
unclaimed current chunk, and default zero fees:

```text
/sc nation create GTNation
/sc state create GTNation GTState
/sc city create GTState GTCity
/sc chunk claim GTCity here
```

The creator automatically leads and belongs to all three levels; no join/invite
or `open` setting is needed for this sequence. Claim title starts at the city's
treasury account.

```text
nation create <name> [leader: operator only]
state create <nation> <name> [governor]
city create <state> <name> [mayor]
```

A nation founder must be unaffiliated. Creating a child requires authority over
the parent. The nominated governor/mayor defaults to the actor and must already
belong to the parent, without a conflicting state/city membership.

In the following commands, `<kind>` is `nation`, `state`, `city`, or `government`.
The latter accepts any level; it cannot create a government or infer one's level
when a government reference is omitted.

```text
<kind> list [page]
<kind> info [government]
<kind> members|roles|officers <government> [page]
<kind> join <government>
<kind> invite <government> <player>
<kind> accept <government>
<kind> invitations <government> [page]
<kind> revoke <government> <player>
<kind> decline <government>
<kind> leave [government]
<kind> kick <government> <player>
<kind> leader|appoint <government> <citizen>
<kind> officer <government> <citizen> <add|remove>
<kind> rename|description|tag|flag <government> <text>
<kind> settings <government>
<kind> setting <government> <key> <value|inherit>
<kind> disband <government> [cascade]
```

`join` requires `open=true` or an invitation. `accept` always requires a live
invitation. Invitations are implemented at **all three levels**, expire, are
capacity-limited and cannot skip parent citizenship. Officials see invitations for
their scope; nonofficials see only invitations addressed to themselves.

National leaders and officers manage their nation's descendants. Governors and
state officers manage their state and its cities; mayors/city officers manage only
their city. Siblings do not inherit authority. Appointing leaders/officers and
executive policy actions require the current leader or an ancestor **leader**,
not merely an officer. A subordinate cannot expel a superior official.

Leaving/kicking removes the selected membership and its descendant memberships,
preserving ancestors. **Every affected leadership office must be transferred
first**, including a governor/mayor's office when leaving the nation. Offices do
not automatically disappear to permit abandonment.

Disband without `cascade` requires a leaf with no claims and at most its leader.
Explicit cascade clears the subtree's memberships and public claims rather than
creating orphans. Ordinary disband cannot confiscate privately purchased land.
Even operator deletion checks treasury balances, economic references, pledged
claims, active contracts/bills/elections/diplomacy and property held outside the
deleted subtree. Resolve those obligations first.

Tags use 1–12 letters, digits, `_` or `-`. Flags are descriptive text of at most
128 characters; URL-like text is stored literally, never fetched or executed.
Description length is configurable.

### Mechanically enforced settings

`settings` returns effective, immutable values. `setting ... inherit` removes the
local override.

| Key | Type/default | Inheritance |
| --- | --- | --- |
| `incomeTaxBps`, `salesTaxBps`, `propertyTaxBps`, `tariffBps`, `corporateTaxBps` | integer 0–10000; default `0` | **Local**, never copied from ancestors |
| `baseChunkValue` | integer cents; default `10000` | Inherited |
| `open` | boolean; default `false` | **Local** membership policy |
| `memberAccess` | boolean; default `true` | Inherited |
| `foreignAccess` | boolean; default `false` | Inherited |
| `alliedAccess` | boolean; default `true` | Inherited |
| `enemyAccess` | boolean; default `false` | Inherited |
| `pvp`, `explosions`, `foreignProperty` | boolean; default `false` | Inherited |
| `friendlyFire` | boolean; default `false` | Inherited |
| `citizenLegislature` | boolean; configuration default `false` | Inherited; national legislature uses its nation's value |
| `peaceRatification` | boolean; configuration default `true` | Inherited; either signatory can require ratification |

Local rates prevent the economy from accidentally applying the same inherited
national tax three times when it sums nation/state/city taxes. Permissions and
valuation instead resolve root-to-leaf, with the closest override winning.
Boolean input is exactly `true` or `false`; rates are basis points (100 = 1%).

Direct policy changes are executive administrative actions. With
`requireLegislationForPolicy=true`, nonoperators must use bills for mechanical
policies; direct `open` membership administration remains permitted.
Unknown policies are rejected, not recorded as if they were enforced.

## Territory and protection

```text
chunk claim [city] [chunkKey|here]
chunk unclaim [chunkKey|here]
chunk autoclaim <on|off>
chunk info [chunkKey|here]
chunk list [government|all] [page]
chunk permit <chunkKey|here> <player> <all|none|ACTION,ACTION>
chunk permits [chunkKey|here] [page]
chunk protection [chunkKey|here] [player]
chunk map [radius:1-5]
```

Aliases: `claim`, `unclaim`, and `map` omit the initial `chunk`. Claims belong to
cities, use their own limits/fees, and must share an edge with that city's existing
claims **in the same dimension**. A city's first claim in each dimension may seed
a new territory. Ordinary unclaim cannot split connected territory when enabled.
Autoclaim uses the actor's resident city and exactly the normal permission,
adjacency, limit and fee checks. Expected autoclaim failures raise `UserError`;
already-owned chunks do not charge a second fee. Leaving a city disables autoclaim.

A new claim's **private title** is `city:<UUID>`. Its political city/state/nation
remain separate from private ownership. Buying a claim changes only the title,
clears old permits, and preserves political boundaries and tracked improvements.
Foreign buying is denied by default; enable `foreignProperty` to allow noncitizens.
Player/company/government title accounts must reference existing known entities.

Public city land allows its title managers, named permits, and the configured
citizen/allied/enemy/foreign policy. Privately purchased land allows only its title
managers and explicit permits: public/diplomatic access cannot open a private
house or its containers. Permit changes/listing require title authority. Actions:

```text
BREAK, PLACE, BLOCK_INTERACT, ENTITY_INTERACT, ATTACK
```

`all` grants these five actions; `none` removes the player's permit. A player attack
always uses PvP checks, even if an `ATTACK` permit exists. Permits cannot grant `PVP`.
By default friendly, allied and truce PvP is blocked. `friendlyFire=true` can relax
that diplomatic protection on the claimed land. `pvp` otherwise controls combat;
`warOverridesPvp=true` permits war combat between signatory citizens on either
signatory's land, even if its ordinary PvP setting is off. Wilderness PvP/explosions
have separate configuration. Explosion checks fail closed on malformed claims.

The improvement counter clamps to 0–1,000,000,000 and is independent of political
or private title changes. `chunk info` includes economy/contract/treaty reservations.
`chunk protection` reports decisions; a requested player's PvP diagnosis uses the
requester as opponent. Maps are dimension-local, north up, with a maximum 11×11 area.

## Elections

```text
election list [page]
election status [nation]
election candidates <nation> [page]
election candidate|register <nation>
election withdraw <nation>
election vote <nation> <candidate>
election history <nation> [page]
election start|close|cancel <nation>    # operator only
```

Registration runs until the scheduled start. Registration requires citizenship;
candidate fees are paid once per registration and are nonrefundable on withdrawal,
loss of eligibility, or operator cancellation. Voting freezes the electorate at
opening. A voter must still be a citizen when casting their single ballot; joining
after voting opens does not add eligibility. Already cast ballots survive
expulsion, preventing an incumbent from erasing opposition votes by kicking voters.
Candidates must remain citizens.

Highest valid vote total wins, with ascending candidate UUID as a deterministic tie
break. No eligible candidates or no valid ballots retain the incumbent. The winner
automatically receives the leadership office without losing subsidiary membership
or changing private titles. Histories are bounded.

Times are **real UTC epoch milliseconds**, not Minecraft ticks. Tick catch-up
processes a finite number of transitions and skips missed empty election cycles;
even an extreme clock jump cannot loop through every missed election. Once the
numeric clock is exhausted, the schedule suspends safely.

## Legislature, laws and executive orders

```text
bill list [nation|all] [page]
bill propose <nation> <policy|roleplay> <value|-> <title> <text>
bill amend <nation> <policy|roleplay> <value|-> <title> <text>
bill info|status <billId>
bill revise <billId> <value> <title> <text>
bill vote <billId> <yes|no|abstain>
bill votes <billId> [page]
bill sign <billId>
bill veto <billId> <reason>
bill override <billId>
bill cancel <billId>
bill history <billId> [page]
law list [nation|all] [page]
law read <nation> <lawId>
executive status <nation>
executive emergency <nation> <policy> <value> <reason>
executive rescind <nation>
```

Ordinary lifecycle:

```text
DEBATE → VOTING → FAILED
               → PASSED → signed → ENACTED
                        → VETOED → override ballot → ENACTED or FAILED
                        → EXPIRED if no timely decision
```

Citizens may introduce bills when `allowCitizenBills=true`; otherwise the author
must be a legislator. Legislators are the national leader and national officers,
plus national citizens when `citizenLegislature=true`. Eligibility is frozen per
ballot, with current legislative eligibility required to cast a vote. Operators
do not receive artificial ballots. Votes are once per voter per ballot; opening a
veto override intentionally creates a new ballot.

Quorum counts yes/no/abstain against the frozen electorate. Ordinary bills also
need `yes > no`. Amendments and overrides require the configured percentage of
the **whole electorate** (not just those present), using exact integer basis-point
comparison. The default 6666 threshold lets two of three legislators form a
supermajority; selecting 6667 instead requires all three because 2/3 is below
66.67%. Thus the default permits two officers to override a dissenting executive.

Passing a normal bill does not apply its policy until the current national leader
signs. A veto can be overridden only by a separate supermajority ballot; a passing
override enacts automatically. Unsigned bills and unused override windows expire.
Only authors during debate, or operators, may cancel ordinary bills. Author
revision during debate restarts the entire debate period.

`roleplay -` records a law **without mechanical effects**. Use it for custom,
employment, citizenship or other policies not in the enforced setting table.
Amendments are constitutional records using a stricter ballot. Laws apply typed
settings; no script execution or arbitrary policy fallback exists. Later enacted
settings supersede earlier ones, even after older codex/history entries age out.

Emergency orders require the national leader, a reason, no active order and an
expired cooldown. They temporarily **overlay** one typed policy instead of
overwriting permanent law. Expiry/rescission reveals the current permanent setting,
including laws changed during the emergency. Rescission does not reset cooldown.

## Diplomacy, war and peace

```text
diplomacy status [nation|all] [page]
diplomacy proposals [nation|all] [page]
diplomacy alliance <fromNation> <toNation> [message]
diplomacy accept <proposalId>
diplomacy break <fromNation> <ally>
diplomacy war <fromNation> <toNation> [reason]
diplomacy peace <fromNation> <toNation> <offerAmount> <demandAmount> <chunk=destinationCity,...|-> [message]
diplomacy truce <fromNation> <toNation>
diplomacy terms|info <proposalId> [page]
diplomacy ratify <proposalId> <yes|no|abstain>
diplomacy execute <proposalId>
diplomacy reject <proposalId>
diplomacy cancel <proposalId>
```

National leaders perform executive diplomacy. Alliances require proposal and
counterparty acceptance while neutral. End an alliance before declaring war; a
binding truce cannot be bypassed by breaking an alliance.

Peace proposals require war. `offerAmount` is paid from the proposing nation's
treasury to the receiving nation; `demandAmount` is the reverse payment. Chunk
terms use full keys and destination city references, for example:

```text
diplomacy peace AlphaNation BetaNation 100.00 0 minecraft:overworld|0|0=BetaCity "End the war."
```

Every selected claim must move between the signatories, be publicly city-owned,
have no economic/contract/other treaty pledge, and preserve configured destination
limits and per-dimension connectivity. Proposals reserve both selected chunks and
referenced destination cities until conclusion or expiry. Private property cannot
be confiscated in peace terms. `truce` is shorthand for a zero-money/no-territory
peace proposal, **not** unilateral termination of war.

With `requirePeaceRatification=true`, or if either nation requires it, executive
acceptance creates a debate/vote ratification bill in **each** legislature.
`diplomacy ratify` casts the same ballot as `bill vote`. Both legislatures must pass
before the proposal expires; no extra signature is needed after both executives
already agreed. A failed ratification cancels the other pending ballot.

Final settlement revalidates every term, then uses **one `transferBatch` for all
monetary transfers before any land or war mutation**. Nonzero terms require economy
even at proposal time. If ratified settlement cannot pay, status remains `READY`,
war/claims remain unchanged, and either leader can `execute` a retry before expiry.
Successful settlement changes public title to the destination city, preserves
improvements, clears old permits and starts a binding configured truce. Repeat
acceptance/execution cannot pay twice. Official mail records proposals, acceptance,
ratification, failure, expiry and settlement.

## Companies and shareholder governance

```text
company create <name>
company list [page]
company info <company>
company members|shareholders <company> [page]
company invite <company> <player>
company accept|join <company>
company revoke <company> <player>
company decline <company>
company leave <company>
company kick <company> <player>
company officer <company> <member> <add|remove>
company owner <company> <memberShareholder>
company rename|description <company> <text>
company transfer <company> <player> <shares>
company propose <company> <owner|dividend|roleplay|dissolve> <value|-> <title> <text>
company proposals <company|all> [page]
company proposal <proposalId>
company vote <proposalId> <yes|no|abstain>
company execute <proposalId>
company cancel <proposalId>
company disband <company>
```

Incorporation issues a fixed total supply entirely to the founder. Employment
membership, administrative ownership and equity are separate. Shareholders need
not be employees; employees do not automatically receive shares. Only the owner
and appointed officers manage the treasury. The owner cannot leave without an
ownership transfer. The new administrative owner must be a member shareholder,
within ownership limits, and company economic/contract obligations must permit
the change.

Any shareholder can propose a decision. Types:

| Type | Value | Effect after weighted approval |
| --- | --- | --- |
| `owner` | known member shareholder | Transfer administrative ownership, not equity |
| `dividend` | total dollar amount | Distribute from company treasury to current shareholders |
| `roleplay` | `-` | Retain a decision without mechanical effects |
| `dissolve` | `-` | Dissolve only after financial/property/reservation checks |

Ballot weights are fixed at proposal creation, including reserved shares still
owned. Selling shares does not create another ballot for the buyer. Quorum uses
eligible share weight, and approval requires yes weight greater than no weight.
Approved effects execute automatically; blocked effects remain `READY` for an
explicit retry until the persisted settlement deadline (one original voting
duration after voting closes). Afterwards they expire without paying.

Dividends use current holdings at payment. Integer-cent rounding uses largest
remainders, then ascending shareholder UUID for ties, so every cent is allocated.
All recipients are validated and paid in one atomic batch. Ordinary disband
requires the owner to be the sole shareholder; otherwise use a shareholder
dissolution vote. No dissolution, including operator deletion, can discard reserved
stock, private titles, money, bank/account references or active work.

The economy stock market uses these persistent core operations:

```java
sharesOf(companyId, shareholder)
availableShares(companyId, shareholder)
transferShares(companyId, from, to, quantity)
reserveShares(companyId, owner, reference, quantity)
releaseShares(reference)
settleShares(reference, buyer, quantity)
```

References are unique 1–128 character identifiers (`letters/digits/_.:-`).
Re-reserving identical terms is idempotent; conflicting reuse fails. Release is
idempotent and changes no equity. Partial settlement reduces the reservation and
transfers exactly that many shares; the final settlement removes it. Only
unreserved shares can otherwise transfer. Total issued shares are conserved;
corrupt ledgers stop settlement rather than manufacturing missing equity.

## Government contracts

```text
contract list [government|all] [page]
contract my [page]
contract create <government> <title> <description> <chunkKey,...|here>
contract info <contractId> [page]
contract bid <contractId> <amount> <description> [company:nameOrId]
contract withdraw <contractId>
contract bids <contractId> [page]
contract review <contractId>
contract award <contractId> <bidder>
contract submit <contractId> <completionNote>
contract return <contractId> <reason>
contract complete <contractId>
contract cancel <contractId> <reason>
```

The issuer selects government-owned chunks in its political subtree; private land
and encumbered land are rejected. Selected chunks remain reserved while active.
A bidder chooses only their own player account, or a company they actually manage,
never an arbitrary payment destination. Nonzero bids require economy. Bids may be
updated while open or withdrawn before award. Detailed bid review is visible only
to authorized government officials.

```text
OPEN → REVIEW → AWARDED → SUBMITTED → COMPLETED
                     ↖ returned for correction
Any active state → CANCELLED (refund any escrow)
OPEN without bids / unawarded REVIEW → EXPIRED
```

Review can open early or at the bidding deadline. Award revalidates land, bidder
authority and payee, then transfers the full amount from the issuer into
`escrow:contract:<contract UUID>`. Work is not paid directly at award. The winner
or its company managers submit work; a government official then approves or
requests corrections.

Paid bidders, company participants/shareholders, and the submitting user cannot
award/complete their own paid work, **even as operators**. Completion validates
the recorded award against its accepted bid and pays only that original payee.
Cancellation is allowed to the issuer or selected contractor and refunds only the
issuing government's treasury. Rejected transfers do not advance status or erase
escrow. Awarded/submitted work does not silently time out or lose its escrow;
explicit completion/cancellation is required.

## Personal and official mail

```text
mail inbox [page]
mail sent [page]
mail read <messageId>
mail send|compose <player|government:nameOrId> <subject> <body>
mail reply <messageId> <body>
mail delete <messageId>
mail invitations [page]
mail official inbox|sent <government> [page]
mail official read <government> <messageId>
mail official send|compose <government> <player|government:nameOrId> <subject> <body>
mail official reply <government> <messageId> <body>
mail official delete <government> <messageId>
```

Inbox/sent envelopes are separate persistent copies. Reading/deleting a sender's
copy does not change the recipient's copy. Message-ID knowledge does not grant
access to another mailbox; personal mail commands have **no operator exception**
for inspecting another player's mail. Official inbox/read/compose require scoped
government management authority or explicit operator authority. Official sender
accounts are derived and audited, not supplied freely by the caller.

Players may petition an official mailbox using `government:<name or UUID>`.
System notifications cannot receive replies. Subjects are at most 100 characters;
body and per-box retention limits are configurable. Oldest copies age out
independently. Internal notifications are clipped to limits; oversized player
composition is rejected.

## Operator administration and safe repair

```text
admin bypass <on|off>
admin unclaim <chunkKey|here>
admin delete <nation|state|city> <government> [cascade]
admin delete company <company>
admin leader <government> <knownPlayer>
admin rename <government> <name>
admin reassign <chunkKey|here> <city>
admin owner <chunkKey|here> <account>
admin diagnostics [chunkKey|here]
admin audit [page]
admin repair <preview|apply>
admin history [page]
```

Every command requires the current `Actor.admin`. Protection bypass additionally
requires the explicit persistent toggle and rechecks the **current actor's**
operator status on every action. It is not granted just because a UUID was once
an operator. `bypassEnabled(UUID)` is informational; world adapters must use
`mayAct(Actor, ...)`, never treat a stored toggle alone as permission.

Forced unclaim intentionally ignores adjacency/private-title authorization, but
never financial or active-workflow locks. `reassign` changes a chunk's political
city, deliberately bypasses adjacency, preserves private non-city titles and
converts an old public city title to the new city. `owner` changes only private
title and still validates references, player foreign-property eligibility and locks.
Leader reassignment can safely move a known player into the needed hierarchy, but
does not abandon another office on their behalf.

Audit/repair-preview are read-only apart from normal actor identity refresh, and do
not advance scheduled workflows. Explicit repair clears dangling memberships,
stale officers/invitations, invalid permits/policies and financially inert orphan
public claims/governments. It does **not** invent leadership/share ownership,
release stock reservations, discard private titles, or erase nonzero escrow.
Private/pledged orphan claims remain for explicit reassignment. Ambiguous IDs and
financially inconsistent records remain reported instead of being guessed away.

Construction validates both structure and references. Explicitly null required
containers/fields or an unsupported governance schema raise `UserError` without
clearing or replacing the supplied data. Truly omitted JSON fields still use the
POJO's declared defaults. Orphan references and inconsistent ledgers are retained
in **repair-required mode**, rather than making the repair commands inaccessible:
`info` displays a warning, timers pause, normal commands/property/stock mutations
are refused, and claimed-land actions fail closed except for explicit operator
bypass. Operators can inspect and repair/reassign records; ordinary operation
resumes once the structural validation report is empty. Read-only financial
queries and validation of an identical existing stock reservation remain usable
for economy startup; no additional shares are reserved. Stock cancellation may
still release a reservation as part of an authorized economy recovery.

Expired invitations and reduced configurable population/retention limits are not
misclassified as corrupt loads. They follow normal expiry/retention or constrain
future actions; citizens are not evicted on configuration changes. The optional
`validationIssues()` getter returns an immutable current report for runtime
startup logging and diagnostics. It never resets or discards records.

Once this world has observed an available economy, the persisted `economySeen`
flag rejects destructive changes if only the raw `EconomyAccess.UNAVAILABLE`
sentinel is supplied. A runtime **persistent financial-lock adapter** is accepted
even when its `available()` is false: `isAccountInUse` must protect funded accounts
and active references, and `isClaimEncumbered` must protect collateral/listings.
Thus verified unencumbered cleanup remains possible with core alone, without
treating missing economy as proof that money or collateral does not exist.
Nonzero fees/payments still require an available economy.

Configuration file reload is a runtime responsibility. The domain implements
`configure(GovernanceConfig)`, not a fake success command for reloading Forge files.

## GovernanceConfig defaults

All fields are public, non-final primitive/String instance fields for the runtime's
Forge binding. `validate()` checks ranges; the engine copies validated configuration
so later caller mutation cannot bypass validation. Limit reductions govern new
actions and retention; they do not evict citizens, revoke equity, or confiscate land.
Already stored stage/proposal/emergency deadlines are not retroactively rescheduled.
New phases and future election cycles use the current timing configuration.

### Limits

| Field | Default |
| --- | ---: |
| `maxGovernments` | 512 |
| `maxNations` | 64 |
| `maxStatesPerNation`, `maxCitiesPerState` | 32 each |
| `maxMembersPerNation` | 512 |
| `maxMembersPerState` | 256 |
| `maxMembersPerCity` | 128 |
| `maxOfficers` | 32 |
| `maxClaimsPerCity` | 256 |
| `maxTotalClaims` | 16384 |
| `maxPermitsPerChunk` | 32 |
| `maxInvitationsPerGovernment` | 64 |
| `maxInvitationsPerPlayer` | 32 |
| `maxCompanies` | 256 |
| `maxCompaniesPerPlayer` | 8 |
| `maxCompanyMembers` | 128 |
| `maxCompanyShareholders` | 512 |
| `maxShareReservations` | 4096 |
| `maxCompanyProposals` | 32 |
| `maxActiveBillsPerNation` | 32 |
| `maxLawsPerNation` | 128 |
| `maxDiplomaticProposalsPerNation` | 16 |
| `maxTreatyChunks` | 32 |
| `maxContractsPerGovernment` | 32 |
| `maxContractBids` | 64 |
| `maxContractChunks` | 32 |
| `maxHistory`, `maxMail` | 128 each |
| `pageSize` | 8 |
| `maxCommandOutput` | 16000 characters |
| `maxNameLength` | 48 |
| `maxDescriptionLength` | 1000 |
| `maxMailBodyLength` | 2000 |
| `totalCompanyShares` | 10000 |

Counts must be positive. Supported hard ceilings include 4096 governments/companies,
100000 total claims, 1000 retained history/mail entries, 64 treaty/contract chunks,
256 bids per contract, 20 rows per page and 24000 command-output characters. Output
must be at least 512 characters, names 3–64, descriptions at most 2000, and mail
bodies at most 4000. Share issuance is 1–1,000,000,000. Other count fields are limited
to 1,000,000.

### Money and voting

| Field | Default | Destination/meaning |
| --- | ---: | --- |
| `nationCreationFee` | 0 cents | `feeAccount` |
| `stateCreationFee` | 0 cents | Parent nation treasury |
| `cityCreationFee` | 0 cents | Parent state treasury |
| `claimFee` | 0 cents | Claiming city's treasury |
| `companyCreationFee` | 0 cents | `feeAccount` |
| `electionCandidateFee` | 0 cents | Nation treasury |
| `defaultBaseChunkValue` | 10000 cents | Fallback valuation policy |
| `feeAccount` | `system:statecraft-fees` | Trusted system account; `system:[a-z0-9_.-]{1,64}` |
| `legislativeQuorumBps`, `companyQuorumBps` | 5000 | Minimum participating electorate |
| `amendmentThresholdBps`, `overrideThresholdBps` | 6666 | Minimum affirmative whole-electorate support |

Money fields range from zero through `Money.MAX`. Quorums range 1–10000 basis
points; amendment/override thresholds must be greater than 5000.

### Timing (milliseconds)

| Field | Default |
| --- | ---: |
| `invitationDurationMillis` | 604800000 (7 days) |
| `electionIntervalMillis` | 604800000 (7 days) |
| `electionVotingMillis` | 86400000 (1 day) |
| `debateDurationMillis` | 86400000 |
| `legislativeVotingMillis` | 86400000 |
| `signatureDurationMillis` | 86400000 |
| `emergencyDurationMillis` | 3600000 (1 hour) |
| `emergencyCooldownMillis` | 86400000 |
| `allianceProposalDurationMillis` | 172800000 (2 days) |
| `peaceProposalDurationMillis` | 604800000 |
| `truceDurationMillis` | 86400000 |
| `companyVotingMillis` | 86400000 |
| `contractBiddingMillis`, `contractReviewMillis` | 604800000 each |

Intervals must be 1000 milliseconds through one year. Election voting must be
shorter than the election interval. Emergency cooldown must be at least its duration.

### Boolean defaults

| Fields | Default |
| --- | --- |
| `requireAdjacentClaims`, `requireConnectedClaims` | `true` |
| `defaultOpenMembership` | `false` |
| `defaultMemberAccess`, `defaultAlliedAccess` | `true` |
| `defaultForeignAccess`, `defaultEnemyAccess` | `false` |
| `defaultPvp`, `defaultExplosions`, `defaultForeignProperty` | `false` |
| `wildernessPvp`, `wildernessExplosions` | `true` |
| `warOverridesPvp`, `protectFriendlyPvp` | `true` |
| `citizenLegislature` | `false` |
| `allowCitizenBills` | `true` |
| `requirePeaceRatification` | `true` |
| `requireLegislationForPolicy` | `false` |

## Runtime integration and persistence

Public entry points are:

```java
new GovernanceEngine(GovernanceData data, GovernanceConfig config,
                     EconomyAccess economy, LongSupplier clock)
String execute(Actor actor, String line)
void tick(long now)
void setEconomy(EconomyAccess economy)
void configure(GovernanceConfig config)
void login(Actor actor)
boolean mayAct(Actor actor, String chunkKey, AccessAction action, UUID nullableTargetPlayer)
boolean allowsExplosion(String chunkKey)
void recordImprovement(String chunkKey, int delta)
boolean autoClaimEnabled(UUID player)
void autoClaim(Actor actor)
boolean bypassEnabled(UUID player)
List<String> validationIssues()
CoreMenus.register()
```

The engine implements every `GovernanceAccess` method. Views contain defensive
immutable collection copies; account lists agree with account access checks.
UUID-only account/management methods deliberately do not infer operator authority.
Government accounts are `nation:`, `state:`, and `city:` plus UUID; company accounts
are `company:<UUID>` and player accounts are `player:<UUID>`.

Start with `EconomyAccess.UNAVAILABLE`, then replace it when the addon is ready.
All nonzero fees/payments must reach the real economy. The economy's trusted batch
interface must support the configured `system:` fee account and
`escrow:contract:<UUID>` accounts, neither of which is exposed through player
treasury access. Multi-party effects use `transferBatch`, never a sequence of
partially committed transfers.

`transferProperty` rechecks core commitments and `isClaimEncumbered`; economy
market/foreclosure adapters must clear or transactionally exclude their own settled
listing/collateral reference before the final core title update, while preserving
all other obligations. `maySellProperty` is an authority/core-workflow check, not
a substitute for the economy's own collateral/listing validation.

Trusted settlement destinations may be a valid `player:<UUID>`, `company:<UUID>`,
or correctly prefixed government account. Corporate/civic settlement (including
consented secured-loan repossession) does not require the bank/company owner's
citizenship: otherwise a foreign-property policy change could make an existing
security interest impossible to settle. Player destination eligibility is still
checked. All **purchases**, regardless of funding/destination account, must first
authorize the purchasing player with `mayBuyProperty`. These service-side title
changes never alter the claim's city, state, nation, coordinates, claimed time or
improvements; old access permits are cleared. Unrelated encumbrances and core
contract/treaty commitments always remain binding.

`GovernanceData` is a mutable, no-Minecraft-dependency, Gson-friendly POJO with
public nested records-as-POJOs, maps/lists/sets, String UUIDs and a schema version.
The supplied object is mutated **in place**. No domain command writes world files.
The runtime must serialize/save atomically on its coordinated server/store path.
The shared runtime owns the versioned `world.json` containing governance and
economy together, including preservation of an unknown economy section and
persistent `integration_locks` with core alone. Domain validation errors must not
be handled by replacing that store with an empty world.
Snapshot collections are complete for accounting; network adapters should select
their needed viewport/page rather than broadcasting every world's claim/member
snapshot. Domain creation limits and paginated command outputs remain enforced.

Registered menu IDs are:

```text
statecraft:main             statecraft:nations         statecraft:states
statecraft:cities           statecraft:members         statecraft:officers
statecraft:invitations      statecraft:claims          statecraft:map
statecraft:elections        statecraft:legislature     statecraft:laws
statecraft:executive        statecraft:diplomacy       statecraft:companies
statecraft:shareholders     statecraft:contracts       statecraft:mail
statecraft:official_mail    statecraft:profile         statecraft:admin
statecraft:help
```

Registration is idempotent. Each page has a query command and action templates
using `<field>` placeholders. Clients should quote substituted field values with
`CommandLine.quote`, then let the server execute and authorize the command. A page
being visible is not authorization to invoke its actions.

Discovery queries work for unaffiliated players: elections list nation IDs;
`bill list all`, `law list all`, `diplomacy proposals all`, and
`company proposals all` expose both the workflow ID and its parent organization
ID. The corresponding menus use these global, paginated queries and provide
organization-directory actions when creating the first workflow.

### Context-aware command forms

`GovernanceForms(GovernanceEngine)` implements the shared `FormProvider`. It
describes existing command placeholders without executing commands, advancing
timers, charging fees, refreshing names or changing saved data. Choices submit
canonical IDs with friendly labels; free-form names and narrative text remain
editable. Creation names start blank.

State creation lists managed nations with remaining structural capacity. Its
governor field depends on the resolved nation and includes only that nation's
citizens without a state assignment. City creation applies the corresponding
state/mayor rules. Eligible current membership is preferred; an ineligible
explicit parent/nominee selection is cleared, not silently moved elsewhere.
Revoked authority is checked using the current actor on every form request.

Other catalogs follow command roles and workflow phases for governments,
appointments, invitations, claims, elections, bills, diplomacy, companies and
contracts. Mail selectors expose only authorized message IDs and subjects, never
message bodies. Policy values are typed, existing editable settings are prefilled,
and votes/new payment amounts are not preselected. Financial affordability,
claim connectivity and entered multi-chunk terms remain command-time checks.

The builder owns search and 20-choice pagination; dependent selectors resolve
against the complete eligible catalog, including selections outside the displayed
page. For economy forms, core supplies conservative player, organization and
account catalogs without importing economy implementation types; the economy
provider may refine them. Form suggestions never replace execution authorization.
