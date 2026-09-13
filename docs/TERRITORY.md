# National territory

StateCraft separates **national sovereignty**, **optional state/city allocations**, and
**property title**. Only a nation claims land. A nation does not need a state or a city
to claim, and citizenship still follows the existing nation → state → city hierarchy.

## Commands and authority

| Command | Required authority |
| --- | --- |
| `chunk claim <nation> <chunks>` | That nation's current leader/officers |
| `chunk unclaim <nation> <chunks>` | That nation's current leader/officers, plus authority over each property title |
| `chunk assignstate <nation> <chunks> <state_or_none>` | That nation's current leader/officers |
| `chunk assigncity <state> <chunks> <city_or_none>` | That state's current leader/officers, or its national officials |

Current trusted operator permission (`Actor.admin`) also authorizes these actions.
Cached operator UUIDs never grant authority. A mayor or governor cannot claim land
merely because they manage an allocated city/state.

`chunks` is `here` or comma-separated `dimension|x|z` keys. Territory batches contain
**1–64 distinct chunks in the actor's current dimension**. Canonically equivalent
duplicates, mixed dimensions, malformed dimensions and out-of-bounds coordinates
are rejected. Territory batch fields accept up to `FormSchema.MAX_VALUE_LENGTH` (4,096 characters),
so 64 vanilla world-edge chunk keys fit. Combined form values and the full rendered
command must still fit the 4,096-character command limit; sufficiently long custom
dimension names may require fewer chunks.

The `claim` and `unclaim` aliases remain national operations. Bare `chunk claim`
uses the actor's nation and current chunk. Location-only claim/unclaim syntax
uses the actor's nation, not a city. An explicit city/state argument never implicitly
claims for an ancestor.

New claims have a required `nationId`, null `stateId` and `cityId`, and public
`nation:<id>` title. State assignments must select a state of that nation. City
assignments require land already assigned to exactly the selected state and a city
of that state. A different state clears an incompatible city; repeating the same
state preserves its valid city. `none` clears the selected allocation, including
the city when clearing its state. Allocations do not create claims.

## Public title and private ownership

Assignments use **transfer-public-title**:

| Resulting jurisdiction | Public property title / future sale proceeds |
| --- | --- |
| Nation only | Nation treasury |
| State, no city | State treasury |
| City | City treasury |

Existing treasury **balances do not move**. Clearing a city returns public title
to its state; clearing a state returns it to the nation. Any changed public title
clears the old public permits, following the ownership-transfer rule.

Player/company titles and their permits are preserved on administrative allocation,
even when the allocator cannot access that private account. Improvements and the
original claim time are preserved for every allocation. Private title holders still
control their buildings: government/diplomatic public-access settings do not open
private property. Ordinary `transferProperty` changes only financial title and clears
old permits; it never changes nation/state/city jurisdiction.

## Atomicity, fees and limits

Every batch validates its complete selection, authority, hierarchy, limits and
obligations before mutation. Claim fees retain the previous funding pattern:
the **acting player's account pays the claiming government's treasury**, now the
nation treasury rather than a city treasury. The total fee is checked for overflow,
then settled in one `EconomyAccess.transferBatch` before any claim is inserted.
A failed payment, overlap, duplicate or invalid selection creates no partial claim.

Assignments and unclaims have no claim fee and do not transfer balances. A failure
on any selected claim prevents the entire allocation/unclaim batch. Listings,
collateral/loans, government contracts and active treaty commitments prevent title
or jurisdiction changes. The persistent financial-lock adapter remains mandatory
after an economy-backed world has seen the addon. An actual no-op allocation does
not erase permits or invalidate a commitment.

Configuration:

- `maxClaimsPerNation`: **16,384** by default, validated in `1..100,000`.
- `maxTotalClaims`: existing world-wide limit, unchanged.
- `maxClaimsPerCity`: existing limit, now limits **city allocations**, not national
  claiming. There is no separate state allocation or allocation-adjacency setting.
- `requireAdjacentClaims`: new chunks must connect by an edge to national land
  in that dimension. A first batch in a dimension must itself be connected.
  Selection order does not matter; selected chunks can connect through each other.
- `requireConnectedClaims`: unclaim validates the **final** remaining national
  territory in affected dimensions, not intermediate removals.

State/city allocations can be nonadjacent. Reducing limits never evicts existing
claims/allocations or quarantines otherwise valid data; positive additions are
blocked at the new limit. Legacy disconnected national territory is preserved.
New claims can bridge it, while an unclaim that leaves disconnected territory may
require an explicit operator decision.

## Protection, contracts, treaties and deletion

Protection and property-eligibility policies use the most-specific valid allocation:
city, otherwise state, otherwise nation. Existing inheritance, foreign/allied/enemy
access, PvP, explosions, friendly-fire protection and private-account permissions
continue to apply. Missing optional allocations are not wilderness. An invalid
non-null allocation, missing required nation or retained null claim entry remains
protected and repair-required, never treated as wilderness.

Public government contracts can select an issuer's national territory or explicit
state/city allocation, including nation-only and state-without-city claims. Existing
subordinate public titles remain eligible within the issuing government's scope.
Private titles and financial/core commitments retain their existing guards.

Peace terms transfer **existing public claims between signatory nations**. The
`chunkKey=destinationGovernment` syntax accepts a nation, state or city. A nation
destination leaves allocations empty; a state/city destination explicitly negotiates
those allocations. The legacy destination-city syntax remains valid. National
topology/claim limits and city allocation limits are checked for the whole treaty.
All monetary terms settle atomically before land or war state changes. Treaties
still cannot confiscate private property.

Deletion is conservative:

- A city/state with allocated land cannot be deleted, even with operator/cascade
  authority. Explicitly clear its allocations first; deletion never silently
  unclaims the nation's land.
- Only an explicit **nation cascade** can remove that nation's unencumbered public
  claims. Private or externally owned titles block it, including operator deletion.
- Empty-treasury, account-use, company-property and active-workflow guards still apply.
- `admin reassign <chunk> <government>` is an explicit repair/sovereignty operation.
  Nation/state/city destinations set the complete resulting hierarchy. It bypasses
  adjacency, not obligations or capacity; private owners and permits are preserved.
  A mismatched claim record key must be repaired first.
- `admin unclaim <chunk>` remains the explicit forced removal path, including
  remotely addressed dimensions for operator cleanup. Financial, contract and
  treaty locks still block it.

## Persistence migration and compatibility

The **governance section** schema upgrades from `1` to `2`. The outer WorldStore
schema and economy schema are unchanged. `GovernanceData.CURRENT_SCHEMA` is `2`;
the POJO's version default intentionally stays `1` so Gson can identify older JSON
that omitted the section version. Initialization upgrades and marks the section
dirty once; reloading schema `2` is idempotent.

For a legacy city-only claim, initialization adds `stateId` and `nationId` only
when its indexed city → state → nation chain is valid, has matching kinds/canonical
IDs, and ends at a root nation. It does not overwrite explicit contradictory
fields, infer from a property account, or guess an orphan city's nation.

Migration preserves record keys, private/public owner accounts, permits, claim
times, improvements, policy values, memberships, escrow, financial locks and other
obligations. It performs no payments, allocations, title transfers or claim removal.
Malformed records are retained and normal mutations/timers remain paused for repair.
Safe repair does not erase orphan titles or invent national ownership; use explicit
reassignment or guarded forced removal. Unsupported schema versions and explicit
null structural containers fail without resetting data.

Legacy treaty `fromCity`/`toCity` terms are retained. Valid city chains add required
`fromNation`/`toNation` snapshots and optional `fromState`/`toState` allocations.
Invalid active terms stay preserved and blocked; historical concluded references
are not discarded when former governments no longer exist.

`GovernanceAccess` public methods and the `ClaimView` record component signature
remain source-compatible:
`key, dimension, x, z, cityId, stateId, nationId, ownerAccount, improvements, claimedAt`.
Consumers must now handle null `cityId`/`stateId`; a valid view always has a nation.
Core helpers `claimGovernment`, `claimInGovernment`, `nationClaims`, `stateClaims`
and `cityClaims` project the explicit model. Review fingerprints include national
ownership, both optional allocations, title, permits, improvements and claim time.
All selected claims contribute to those fingerprints even when their readable
preview lines are abbreviated.

## Focused coverage

`GovernanceTerritoryTest` covers authority, optional jurisdiction, batch validation
and fees, connectivity, title transfers, private permits, scoped allocations,
limits, obligations, protection, contracts, treaty settlement, deletion, auto-claim,
forms and review invalidation.

`GovernanceTerritoryMigrationTest` reads actual city-only JSON shapes and verifies
lossless title/permit/value migration, escrow/collateral retention, legacy treaty
settlement, reload idempotence and fail-closed malformed/unsupported data.
Existing core fixtures claim for nations and then explicitly allocate state/city
land; no compatibility helper permits illegal city claiming.
