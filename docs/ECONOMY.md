# StateCraft Economy

Minecraft **1.20.1**, Forge **47.3.22**, Java **17**. Mod ID:
`statecraft_economy`. StateCraft is required.

The economy runs on the server. Screens submit commands using the actual
connected player; neither GUI fields nor item NBT supply an actor, permission
level, balance, or government role. Common code does not reference
`net.minecraft.client`.

## Getting started

1. Craft an ATM, Trading Hub, Marketplace, Company Vault, Stock Market, bank card,
   or recipe guide using the bundled recipes.
2. Right-click a utility block or the recipe guide to open the shared StateCraft
   form-based screen. `/sce` opens the ATM screen.
3. Hold a priced item and use `/sce hub sell 16` near a Trading Hub to earn an
   electronic balance. Currency notes themselves cannot be sold there.
4. Use `/sce cash withdraw 12.34 me` near an ATM to receive $12 in default
   denominations; the remaining $0.34 stays electronic.
5. Use `/sce cash deposit me` to deposit **all untagged currency in the main
   inventory**. Armor and offhand slots are not scanned.

No currency recipes are supplied. Currency comes from operator minting, earned
Trading Hub proceeds, configured merchant trades, or previously funded cash
withdrawals. The initial player grant defaults to **zero**, and a nonzero
configured grant is applied only once per player UUID.

### Physical access policy

* Cash deposits/withdrawals, including cash-funded property purchases: within
  `utilityRadius` blocks of an **ATM or Company Vault**.
* Trading Hub item sales: within that radius of a **Trading Hub**.
* `company pay` and `company dividend`: within that radius of a **Company Vault**.
* Other commands are intentionally remote electronic services: account transfers,
  marketplace escrow, bank deposits/withdrawals, stock trades, taxes, and property
  purchases without cash. A bank withdrawal moves a bank deposit to the wallet;
  obtaining physical notes is a separate ATM withdrawal.

Checks use the player's server position and loaded server blocks, not a
client-supplied position. Utility proximity is not an account authorization:
standing beside somebody else's ATM never grants access to their money.
StateCraft separately protects direct block/entity interaction.

## Accounts, amounts, and cards

Economy forms now offer searchable, paginated dropdowns filtered by the current
sender's permissions, ownership, and listing/loan status. Authorized source
accounts show their balances; public recipients never show private balances or
internal escrow. Eligible saved accounts and associated banks are preselected,
and dependent fields refresh when their company, listing, bank, loan, or
collateral changes. Revoked or stale selections are cleared.

Search fields and partial quantities remain editable, with explicit `all`
choices where supported. New monetary amounts stay blank, purchases default to
one unit, and loan forms default to manual repayment and no recovery consent.
Secured applications require an explicit collateral-consent choice. Form
metadata is read-only: it does not grant money, accrue interest, calculate fresh
valuations, reserve assets, or execute commands. Final execution remains
authoritative and may reject a changed price, permission, balance, or commitment.

All amounts are stored as integer **cents** in `long` values. The maximum balance
or individual money amount is `9_000_000_000_000_000` cents. Command amounts are
**dollars**, with zero to two decimal places: `12.34` means 1,234 cents. Negative
amounts, excess precision, overflow, invalid identifiers, and invalid quantities
are rejected before financial mutation.

Account forms:

* `player:<full-lowercase-UUID>`
* `nation:<id>`, `state:<id>`, `city:<id>`, `company:<id>`
* `bank:<company-id>` — the bank's cash assets, **not a customer's deposit**
* `escrow:<id>` and `system:<id>` — trusted internal ledger accounts
* `escrow:contract:<contractId>` — StateCraft government-contract escrow using the core-generated identifier

Ordinary player commands cannot select, inspect, withdraw from, or directly
deposit into internal escrow/system accounts. Authorized core contract/service
operations use the trusted batch API instead. This includes
`system:statecraft-fees` and `escrow:contract:<contractId>`; operator recovery remains
available through the explicitly privileged administration paths.

Use canonical IDs in account strings. Company/bank management commands can also
look up names; an account named `company:SomeName` is not an alias for an account
whose actual ID is different. `me` resolves to the sender's wallet, and
`selected` resolves to their currently selected, newly reauthorized account.
Payments also accept a remembered player name or full UUID. Both raw UUIDs and
`player:` accounts require the complete lowercase UUID; shortened components
are rejected, never silently padded into another player's account. Remembered
names are matched case-insensitively. Quote names with spaces.

Every player-facing balance, activity, selection, cash, and transfer source path
requires the sender's own wallet, the matching StateCraft treasury permission,
or operator permission level 2. Ordinary company membership/share ownership does
not imply treasury access. Bank asset-account authorization follows the bank
company's treasury permission.

Hold a bank card and run:

```text
/sce card bind nation:<id>
/sce card clear
```

The card stores only an account **label**. Right-clicking rechecks current
permissions before selection. Copying a card, editing its NBT, receiving a former
official's card, or retaining a selection after losing a role cannot bypass
authorization. Cash still requires a nearby utility block.

### Core commands

Both `/statecrafteconomy` and `/sce` accept:

```text
help
balance [account]
accounts
account list
account select <account>
pay <recipient> <amount> [fromAccount]
transfer <fromAccount> <toAccount> <amount>
cash deposit [account]
cash withdraw <amount> [account]
history [account] [page]
limits [account]
top [player|nation|state|city|company]
guide
gui economy:<page>
```

`history` and `activity` are aliases. Leaderboards show ledger cash, not a
double-counted sum of bank cash plus its customer liabilities.

Lists contain twelve entries per page. Prefix a paginated query with
`page <number>`, for example:

```text
/sce page 2 market own
/sce page 3 market search diamond
/sce page 2 property listings
/sce page 2 bank loans
/sce page 2 prices
```

The global prefix does not persist into the next request. In addition,
`history` and `tax report` accept an explicit trailing page.

### Guided views and transaction reviews

Economy sections now provide searchable, twenty-row pages of typed records.
Selecting an account, bank, loan, item/share listing, property, company,
obligation, or delivery opens authorized details and prefilled actions. IDs
come from server records, never from parsing the displayed text. Disabled actions
explain missing permissions, unavailable funds, closed records, or prerequisites.
Historical loan details retain their bank identity and original agreement even
after the bank and its company close.

`economy:dashboard` collects your pending loan applications, bank-manager
approvals, upcoming manual or overdue loans, unpaid obligations, deliveries,
and your outstanding listings. Search and paging remain role-scoped; a treasury
recipient cannot use the dashboard to inspect another payer's private debts.
`economy:loans` and `economy:deliveries` provide dedicated management lists.
The generic `economy:detail` page is hidden from navigation catalogs and opened
through typed links. The guide retains the server's loaded recipe/help content.

Guided mutations show a server-calculated review before submission. Monetary
reviews include parties, quantity, actual denomination issuance, applicable
fees/taxes/tariffs, total/net amounts, and informational available funds. Property
funding itemizes cash deposited, wallet contribution, bank withdrawal, and its
fee. Loan applications, future bank terms, listing offers, and autopay changes
explicitly distinguish future obligations from payments occurring now.

Reviews use the same quote/plan arithmetic as execution, without granting money,
booking interest, creating valuations, reserving assets, or changing inventory.
The shared runtime binds a thirty-second review to the exact selected action
and checks a fresh quote immediately before submission. Material price, party,
item metadata, or term changes require another review; a balance display or
clock advancing alone does not. Electronic marketplace reviews do not bind
unrelated held items or inventory contents. Physical-item reviews bind the
relevant inventory/held-item state.
Property actions using `here` bind the resolved dimension-qualified chunk, not
the word `here`: moving to an equally priced property still requires a new
review. Held-item sales/listings bind item identity, stack count, selected slot,
and exact metadata; cash reviews bind the inventory's note counts and metadata.
Item/share listings also bind the seller's recorded tax-origin chunk and nation.

Forms advertise integer, money, rate, text-length, repayment-period, listing-
quantity, available-share, and applicable live amount limits. `all` remains an
explicit supported alternative for purchases and repayment. Local validation
helps explain errors but never replaces server permission, reserve, inventory,
or settlement checks.

The one-time player grant is normally applied at login. If initialization is
still pending (for example after logging in while saving was paused), use the
offered **Initialize economy profile / My balance** query first. Mutating reviews
do not silently grant money or quote against a balance that execution would
immediately change through initialization.

## Trading Hub and configurable prices

```text
hub
hub settings
hub sell <quantity>
hub prices [itemSearch]
hub suggest <unitPrice> [reason]
hub suggestions                         # operator
prices [itemSearch]
price get <itemId>
price set <itemId> <unitPrice>           # operator
price remove <itemId>                   # operator
```

Sales consume the requested quantity of the **exact held item and SNBT** from
the main inventory. Tax and inventory/account/limit preflight happen before
anything is removed. Sales and income taxes are withheld and credited to their
respective governments. Gross sales and item quantities have separate UTC-day
quotas; taxes do not reduce the amount counted toward the gross limit.

At first startup, missing bundled defaults are copied to:

```text
config\statecraft_economy\item_values.json
```

The file contains positive integer cents:

```json
{
  "prices": {
    "minecraft:wheat": 25,
    "minecraft:diamond": 500
  },
  "currencyItems": {
    "statecraft_economy:currency_1": 100,
    "statecraft_economy:currency_10": 1000
  }
}
```

The actual bundled table includes seven denominations from $1 to $1,000,000.
The example above is abbreviated, not a recommendation to remove denominations.
All item IDs must be registered and cannot be air; values must be positive
integer cents, not strings, decimals, or negative numbers. At least one physical
denomination is required. Duplicate JSON keys and unknown schema fields are
rejected. Configured cash items and all built-in currency IDs are always denied
at the Hub, even if they appear in `prices`.
Currency items must also have plain default stacks: a configured item that
starts with persistent tags/capabilities is rejected during load/reload, rather
than issuing notes that cannot later be deposited.

Cash issuance uses descending configured denominations. Any unrepresentable
remainder stays electronic. It is not a general optimal-change solver for
arbitrary denomination systems. If the chosen notes cannot fit, the entire
withdrawal fails without debiting money. Tagged currency is not accepted as
cash; this avoids destroying custom data or valuing forged item metadata.

`price set/remove` validate the complete candidate tables, stage the new file
beside the original, fsync it, and use an atomic replacement. Unsupported atomic
replacement or validation failure leaves the live tables unchanged. Suggestions
are persistent, bounded, and replace an earlier suggestion from the same player
for the same item; they never change prices automatically.

## Player marketplace

```text
market list <quantity> <unitPrice>
market search [itemIdOrSellerNameOrListingId]
market own
market inspect <listingId>
market buy <listingId> <quantity|all>
market cancel <listingId>
market collect
market deliveries
```

Listing removes exact items from the seller's inventory into persistent escrow.
The full item SNBT, serialized Forge capabilities, item ID, stack-size rule, quantity, source location/nation,
price, and expiration survive reloads. `inspect` shows an SNBT preview; its
display truncation does not truncate escrow data.

Additional root stack metadata uses an explicit `fullStackData` marker in the
escrow model; old records without it remain ordinary vanilla item tags. A tag
containing a similarly named field cannot promote itself into authoritative
stack metadata. Unchanged inventory slots retain their original live stack
objects. Physical currency must be plain: stacks carrying nonempty serialized
capability data, like other tagged cash, are not accepted as deposits.

Full and partial purchases use one ledger batch for seller proceeds, commission,
buyer sales tax, seller income/corporate tax, and any import tariff. Self-buys,
overbuys, nonpositive quantities, overflow, expired listings, or insufficient
funds cannot partly settle a trade.

Purchased items go into the buyer's persistent delivery queue. The command also
attempts immediate collection. Offline sellers are paid electronically.
If automatic collection is refused with an expected inventory/domain error,
the command still confirms the **completed purchase**, explains why collection
was deferred, and directs the buyer to `market collect`. Do not repeat the
purchase to collect an existing delivery. Unexpected adapter failures are not
silently swallowed; the shared operation workflow handles uncertain outcomes.
Cancellation/expiration queues the unsold items for the seller and sends mail;
listing fees are nonrefundable. Full inventories leave items queued rather than
dropping them on the ground. Collection may partially fill available slots and
leaves the exact remainder queued.

An active seller listing reserves a refund-queue entry. New listings and
purchases cannot consume these reserved entries, so an offline/full-inventory
seller cannot lose an expiration refund because somebody filled a queue.
Missing item-providing mods or changed stack-size rules stop collection with an
explicit error instead of replacing items with air.

## Taxes and property

Governments supply these exact settings through StateCraft:

| Setting | Meaning |
|---|---|
| `incomeTaxBps` | Personal sale/Hub income withholding |
| `salesTaxBps` | Marketplace/property/stock buyer tax; Hub seller withholding |
| `propertyTaxBps` | Periodic property-value assessment |
| `corporateTaxBps` | Company sale/distribution withholding |
| `tariffBps` | Import tariff |
| `baseChunkValue` | Integer-cent property valuation base |

Missing rates are **zero**; the economy does not invent government tax rates.
Rates are integers from 0 through 10,000 basis points. Each tier's rounded-down
tax is recorded and paid separately. Invalid settings cause a visible error,
not an implicit zero. Combined withholding/fees greater than sale proceeds
reject that sale.

Every claim belongs to a **nation**; allocation to a state, then a city, is
optional. Tax quotes, withholding, and reports include only the tiers actually
assigned to that claim, once each. Missing states/cities neither copy a parent's
tax rate nor cause a fallback to the actor's nation. Only unclaimed locations
use the actor's nation as a fallback.

Market/property/stock buyer sales tax uses the buyer's current government tiers,
falling back to their nation outside claims. A foreign-origin purchase also
pays the buyer nation's tariff when seller origin and buyer nation differ.
Seller income or corporate withholding uses the seller's recorded listing
location/nation. Government-owned property proceeds are not treated as personal
income. General wallet transfers are not automatically classified as wages or
taxable sales.
Item/share listings record the source claim's nation even when it has no city
or state and the seller has different or no citizenship. Wilderness listings
use the seller's nation instead. That recorded origin does not change when the
seller moves, changes citizenship, or the source is later allocated locally.

```text
tax rates
tax quote <amount>
tax report [account] [page]
tax arrears [account]
tax pay <amount|all> [account]
```

Reports are scoped to an authorized payer or treasury. `quote` illustrates each
tax independently, not a claim that all illustrated taxes apply to every action.
Tax reports distinguish assessment, payment, and arrears payment.

### Property commands and funding

Chunk keys use `dimension|chunkX|chunkZ`, for example
`minecraft:overworld|12|34`. `here` means the sender's current chunk.

```text
property value [here|chunkKey]
property list <here|chunkKey> <price>
property listings
property own
property delist <here|chunkKey>
property buy <here|chunkKey>
property buy <here|chunkKey> cash
property buy <here|chunkKey> bank <bankId> [cash]
```

StateCraft decides seller/official and buyer eligibility. Payment goes to the
claim's actual `ownerAccount`, including a private company or government
treasury. The private owner changes **only after successful payment**;
nation/state/city claim ownership does not change.
For public land, the title and sale proceeds belong to the most-specific
allocated treasury: nation, state, or city. Allocating public land changes that
public title; allocating privately held land never rewrites its player/company
title. A listing whose actual title changed must be delisted and relisted before
payment. Property/collateral choices show the assigned hierarchy or an explicit
unassigned region/city label, while retaining the original chunk key.

Funding uses the wallet first. `cash` deposits all eligible physical cash as
part of the same transaction, retaining unused value in the wallet. A specified
bank supplies only the remaining shortfall from the buyer's own deposit, with
its withdrawal fee. Deposit liabilities, bank reserves, wallet balances, physical
inventory, and governance transfer are preflighted together. Refusal by the
inventory/governance participant rolls back payment; a failure does not consume
cash or a bank deposit.

Sale listings, requested/active/defaulted secured loans, and unpaid property-tax
liens encumber their exact claims. StateCraft uses `EconomyAccess.isClaimEncumbered`
to block unclaiming, territorial allocation, and external ownership changes.
A seller cannot newly list or pledge a property carrying one of these holds.
An already-authorized economy sale or consented repossession can settle only
the title while leaving assessed taxes with their original payer; the lien
still blocks territorial reassignment until paid. Other account taxes do not
create a territorial lien.

### Valuation and recurring assessment

Valuation uses:

* The most specific **present** government's configured `baseChunkValue`,
  otherwise the configured default. No city or state is required.
* A spawn-distance bonus: `locationBonusBps / (1 + distanceChunks / 64)`.
* Loaded surface biome factors: plains 110%, forest 105%, swamp 95%,
  desert/badlands 90%, ocean 80%, other 100%.
* Nearby claimed chunks in a 5×5 neighborhood, excluding the subject:
  `demandBonusPerChunkBps` each, capped at +100%.
* Core-tracked improvements: `improvementBonusBps` each, capped at +200%.
* A discount equal to combined property-tax basis points, capped at 50%.

Multipliers use integer arithmetic; the final valuation is capped at the money
maximum. Looking up a biome never force-loads a chunk. When a chunk is unloaded,
its last known biome factor is preserved; a property with no observation starts
neutral. Values and next recalculation/assessment times are persistent. A changed
allocation invalidates the cached territorial base without resetting the
assessment schedule or the core's improvement count.

Property taxes become explicit obligations even when the owner cannot pay.
`mandatoryPropertyTaxes=true` automatically pays what is available without
overdrafting; the rest remains owed. Obligations use arbitrary-precision integer
cents, so repeated assessments cannot overflow and erase arrears. Destination
balance limits defer payment rather than discarding it.

Mandatory collection follows outstanding **property-tax obligations**, not
current claim owners. A former owner remains eligible after selling their last
claim or losing it to repossession, including after reload. Each obligation gets
its own rotating work entry, so one blocked debtor/recipient cannot prevent
other debts from being processed. A collection work unit handles only that
obligation; it does not scan all payers or automatically collect unrelated
income taxes, company fees, or other optional arrears. Mandatory property
payments bypass voluntary daily spending limits, but still respect available
funds, protected bank balances, and recipient capacity.

Catch-up is bounded per tick. Economy property sales/repossessions settle all
overdue assessment periods against the previous owner before changing the tax
owner; buying a claim does not inherit the seller's already-due tax. If another
trusted integration changes private ownership, the last recorded tax owner
remains responsible for unprocessed periods until the change is observed.
Within an unchanged allocation, assessment uses the government rates/value
available when processed, not a historical policy-price database. Valuations
also persist their last property-tax jurisdiction and per-period amounts.
When a territorial allocation change is observed, unprocessed overdue periods use
that previous basis and payer, never a newly assigned state's/city's rates or
treasury. Subsequent periods use the new allocation and actual owner; already
recorded obligations are not rewritten. Older valuations without this optional
snapshot field are initialized on observation (a retained former public title
identifies its old hierarchy). New economy records begin their assessment
schedule when first observed, not before the economy was installed.

## Companies, dividends, and shares

Create/manage the company through StateCraft first.

```text
company balance <company>
company pay <company> <recipient> <amount>
company dividend <company> <budget>
company fees <company>
stock list <company> <shares> <unitPrice>
stock search [companyOrListingId]
stock own
stock buy <listingId> <shares|all>
stock cancel <listingId>
```

Vault payments and dividends require treasury permission. Transfer fees are
separate from the transfer amount. Periodic company fees become persistent
obligations; they can be paid with `tax pay ... company:<id>`.

A dividend budget first withholds corporate tax using the acting official's
location/nation, then distributes the remaining budget in proportion to all
registered shares. Each shareholder receives an integer-cent floor. Unallocated
rounding cents stay in the company treasury; nothing is minted or discarded.
Dividends are explicitly initiated, not automatically withdrawn on a timer.

Stock listings reserve shares with StateCraft using their persistent
`stock:<UUID>` listing reference. Reserved shares cannot be transferred or listed
again through another path. A purchase settles only the purchased quantity;
cancellation/expiration releases only the unsold reservation. Seller payment and
share settlement roll back together on a governance refusal. Self-purchases are
forbidden, and a missing company or insufficient funds does not release escrow.

## Company banks

Bank identity is tied to one company ID. Example setup:

```text
/sce pay company:<id> 1000.00
/sce bank create <id> "Community Bank" 500.00 25 100
/sce bank associate <id>
/sce bank deposit 100.00 <id>
```

The final two creation numbers are deposit and loan interest **basis points per
financial period**, not annual percentages.

```text
bank list
bank create <company> <name> <seedCapital> <depositBps> <loanBps>
bank associate <bank>
bank balance [bank] [customerUUID]
bank deposit <amount> [bank]
bank withdraw <amount> [bank]
bank capital <bank> <in|out> <amount>
bank brand <bank> <name> <branding>
bank terms <bank> <depositBps> <loanBps> <originationBps> <depositFee> <withdrawalFee>
bank report <bank>
bank loans [bank]
bank close <bank>
```

Without a bank argument, deposit/withdraw/balance use the sender's associated
bank. Association is only a preference; it does not transfer existing deposits.
Managers may inspect another customer's balance, but a customer cannot inspect
somebody else's deposit or withdraw it.

### Liabilities, reserves, and funded interest

* Depositing moves actual wallet money into `bank:<company-id>`. The credited
  liability is the amount less the deposit fee.
* Withdrawing sends the requested amount to the wallet and debits that amount
  **plus** the withdrawal fee from the customer's deposit. Fees remain bank
  equity.
* Customer principal, funded interest, unpaid interest, and the bank's ledger
  cash are distinct fields; a deposit is not a second minted ledger balance.
* Ordinary transfers/cash/capital withdrawals from a bank protect at least all
  customer liabilities and the configured minimum reserve.
* Reserve and spendable-balance decisions include interest earned through the
  decision time, even if scheduled maintenance has not booked it yet. They use
  every deposit's saved principal, rate, period, and fractional remainder.
  Validation and reserve reports calculate these liabilities without changing
  deposits or ledger balances.
* Approved lending and customer withdrawals use their specific post-operation
  reserve preflight. Lending may use fractional reserves, but a bank owner
  cannot exploit the generic transfer/ATM path to siphon customer deposits.
* Deposit interest accrues on principal using elapsed milliseconds and carried
  fractional cents. Interest does **not** earn more interest. Mid-period deposits
  cannot earn a full period's interest immediately.
* Interest is credited to the withdrawable deposit only from bank cash equity
  beyond existing deposits and the fixed minimum reserve. Unfunded earned
  interest remains a persistent liability, included in reserve/closure guards.
  Subsequent earnings or capital contributions can fund it; no currency is minted.

Existing deposit interest and loan contracts retain their original rates and
periods when a manager changes advertised rates. New principal added to an
already funded deposit uses its existing interest contract; emptying its
principal and depositing again opts into the current advertised rate. Current
deposit/withdrawal fees are checked when those voluntary operations occur.
Sub-cent residues are carried while the account remains usable; no fractional
cent can be withdrawn.

Banks cannot close with customer liabilities, unpaid interest, open loans, or
loan applications. The company cannot be disposed of while its bank is active.
Closure returns remaining cash equity to the company, removes active
associations, and permanently retires that company's bank identity.

## Loans and narrowly scoped recovery

```text
loan request <bank> <principal> <periods> <chunkKey|none> <none|balance|collateral|both> [auto|manual]
loan show <loanId>
loan approve <loanId>
loan cancel <loanId>
loan reject <loanId>
loan repay <loanId> <amount|all>
loan autopay <loanId> <true|false>
loan recover <loanId>
```

`bank loan ...` is an alias. `bank loans` lists the borrower's loans; specifying
a bank requires its manager role and lists that bank's applications/contracts.

`loan show` and the loan detail panel display **current automatic payments**
separately from the clearly labeled **original immutable agreement**. Enabling
or disabling autopay never rewrites that agreement or grants new recovery
consent. The panel also shows principal, current interest, outstanding/currently
due amounts, status, and the next unpaid installment date (which remains in the
past when overdue). Repayment, approval, cancellation, and enable/disable-autopay
actions use the same server authorization as commands.

A borrower must associate with the bank, satisfy debt limits, and have no
unresolved default. Unsecured lending is disabled by default and has a separate
maximum when enabled. Secured lending requires a specifically named private
claim the borrower may sell, explicit repossession consent, and the configured
loan-to-value limit. The collateral is reserved at **application**, preventing
simultaneous applications, sales, or transfers from double pledging it.
Unapproved applications expire; rejection/cancellation releases their hold.

Only an authorized bank manager approves funding. Approval rechecks collateral,
eligibility, aggregate borrower exposure, balance, reserve requirements, and the saved terms. The borrower
receives principal less the saved origination fee; debt principal remains the
contracted principal.

Both application and funding check the borrower limit across all banks,
including outstanding principal, interest earned through the decision time
under each saved contract and lifetime cap, and other unexpired applications.
The application being approved counts once, not twice. Expired applications
do not reserve debt capacity while waiting for maintenance, and unresolved
defaults still prevent borrowing. These checks do not depend on first running
`loan show` or a maintenance pass to book interest.

Interest is simple on outstanding principal, never compounded on unpaid
interest, and cannot exceed the saved lifetime interest cap. Repayment applies
to interest first, then principal. The schedule uses cumulative equal-principal
installments with integer rounding. Optional automatic payments use only the
borrower's wallet and respect spending limits. Missed installments and default
are recorded and mailed. Interest, schedule, grace, recovery consent, collateral,
and fee terms are persisted at origination.

**Recovery authority cannot be added by a manager or operator later.**
`loan autopay` can only be changed by the borrower and does not change recovery
consent. After default, server recovery settings must also permit the originally
consented action:

* `balance` authorizes recovery only from that borrower's personal wallet, never
  a government/company treasury. It takes no more than the remaining debt and
  records the payment. It may collect future wallet funds while default remains.
* `collateral` authorizes only the specifically pledged claim. Repossession
  transfers private ownership to the bank company, not political territory.
* Debt is credited by the current property valuation. If valuation exceeds
  debt, the bank must first fund the borrower's surplus equity; otherwise
  repossession is deferred without changing ownership or extinguishing debt.
* A valuation shortfall remains a recorded loan debt. Other chunks or balances
  cannot be substituted. A repaid loan releases its remaining collateral hold.

Closed loan history is bounded independently of live loans. Active/defaulted
debts and their collateral are never trimmed to satisfy a history limit.

## Operator commands and configuration

```text
admin mint <account> <amount>
admin take <account> <amount>
admin set <account> <amount>
admin limit <account> <amount|unlimited>
admin cash <amount>
admin suggestions
admin audit
admin notices
admin reload
```

`reload` is also an operator-only root alias. Account adjustments are recorded,
cannot create negative/overflow balances, and cannot take protected bank
liabilities. `admin cash` mints physical notes into the operator's inventory
after inventory preflight. Console commands use trusted command-source
permissions; inventory/spawn actions require a player.

The server TOML is normally:
`<world>\serverconfig\statecraft_economy-server.toml`.
Defaults are below. `Cents` fields are integer cents, `Bps` fields are basis
points, and `Millis` fields are wall-clock milliseconds.

| Configuration | Default |
|---|---:|
| `initialPlayerBalanceCents` | 0 |
| `historyLimit`, `taxHistoryLimit` | 1000 each |
| `workPerTick` | 16 |
| `discoveryIntervalMillis` | 60000 |
| `catchUpPeriodsPerTick` | 4 |
| `utilityRadius` | 8 |
| `marketListingFeeCents` | 0 |
| `marketCommissionBps` | 100 |
| `marketLifetimeMillis` | 604800000 |
| `maximumListingsPerPlayer` | 32 |
| `maximumActiveListings` | 10000 |
| `maximumDeliveryEntries` | 256 |
| `hubDailyLimitCents` | 1000000 |
| `hubDailyItemLimit` | 4096 |
| `maximumSuggestions` | 200 |
| `stockListingFeeCents`, `stockCommissionBps` | 0 |
| `stockLifetimeMillis` | 604800000 |
| `propertyListingLifetimeMillis` | 604800000 |
| `defaultChunkValueCents` | 1000000 |
| `locationBonusBps` | 5000 |
| `demandBonusPerChunkBps` | 250 |
| `improvementBonusBps` | 500 |
| `valuationIntervalMillis` | 3600000 |
| `propertyTaxPeriodMillis` | 86400000 |
| `mandatoryPropertyTaxes` | false |
| `companyTransferFeeCents`, `companyPeriodicFeeCents` | 0 |
| `companyFeePeriodMillis` | 604800000 |
| `bankCreationFeeCents`, `minimumBankReserveCents` | 10000 |
| `minimumBankReserveBps` | 2000 |
| `maximumBankCustomers`, `maximumBankLoans` | 1000 each |
| `maximumDepositInterestBps` | 200 |
| `maximumLoanInterestBps`, `maximumOriginationFeeBps` | 1000 |
| `financialPeriodMillis` | 86400000 |
| `allowUnsecuredLoans` | false |
| `maximumUnsecuredLoanCents` | 100000 |
| `maximumBorrowerDebtCents` | 100000000 |
| `maximumLoanToValueBps` | 7000 |
| `maximumLoanPeriods` | 365 |
| `loanLifetimeInterestCapBps` | 10000 |
| `defaultAfterMissedPayments` | 3 |
| `loanGraceMillis` | 86400000 |
| `loanApplicationLifetimeMillis` | 604800000 |
| `allowConsentedBalanceSeizure`, `allowConsentedRepossession` | true |

The Forge adapter calls bounded scheduled processing once per second.
`workPerTick` is the maximum number of work records in that invocation; individual
property/company catch-up is additionally capped by `catchUpPeriodsPerTick`.
Discovery periodically reconciles core claims/companies; scheduled processing
does not repeatedly compound elapsed interest. Larger worlds may increase the
budget. Clock rollback does not generate negative interest or reset paid debt.

Reload validates the TOML, complete item-value file, **all indexed trade files**,
registry IDs, and trade SNBT before replacing live configuration. Malformed
files are explicitly rejected/logged; valid running configuration is retained.
It does not reload or roll back a live world's financial snapshot.

## Villager trades and custom merchants

Missing defaults are copied without replacing operator edits:

```text
config\statecraft_economy\trades\index.json
config\statecraft_economy\trades\<filename>.json
```

The index contains `{"files":["farmer.json","banker.json"]}`. Only unique local
JSON basenames are accepted; path traversal is rejected. The bundled index
includes all **13 standard professions** and these **9 custom merchants**:
banker, bard, barkeeper, botanist, market, baker, winemaker, storage_smith, ribbit.

A trade file has exactly one non-null `profession` or `merchant`:

```json
{
  "profession": "minecraft:farmer",
  "merchant": null,
  "trades": [
    {
      "level": 1,
      "buy": {"item": "minecraft:wheat", "count": 20},
      "sell": {"item": "statecraft_economy:currency_1", "count": 1},
      "maxUses": 16,
      "xp": 2,
      "priceMultiplier": 0.05
    }
  ]
}
```

`secondBuy` is optional. Stack definitions optionally accept `nbt` as valid
SNBT, except currency stacks must be untagged. Counts must fit the item's stack
limit; levels are 1–5; maximum uses are 1–100000; XP is 0–10000; price multipliers
are finite numbers in [0,1]. A cash-only exchange cannot pay more currency value
than it takes. Operators remain responsible for balancing goods/merchant/Hub
prices and ordinary Minecraft trade mechanics.

Standard profession definitions are appended to the existing vanilla/Forge
trade pools. Reload reconstructs those pools from a saved baseline, so offers
do not accumulate duplicates and **newly generated offers really change**.
Existing villagers keep already generated offers.

```text
/sce merchant list
/sce merchant spawn banker       # operator, at the player's location
```

Custom merchants are persistent, stationary vanilla wandering-trader entities
with all configured offers; they require no other mod or client entity
renderer. Their persistent merchant ID selects the current definition. They
restock on interaction after 24000 world ticks, or when a changed definition's
revision is encountered. Removed definitions disable that merchant's offers.
Their normal trade use limits are not reset by every click.

The recipe guide enumerates actual loaded server recipes in this mod's
namespace, their ingredients/alternatives, and their outputs. It is not a
hardcoded list of imaginary recipes. Shaped recipes include grid dimensions,
row-by-row layout, and a symbol legend; shapeless recipes are labeled explicitly.
The displayed material totals and layout therefore follow the actual recipes,
including operator datapack changes.

## Persistence, integration, and limits

The economy registers **one `economy` section** of StateCraft's shared,
versioned, human-readable atomic world snapshot, using `EconomyData` schema 1.
Accounts, transaction/tax/bank activity, arrears, marketplace SNBT escrow and
delivery queues, property listings/valuation schedules, stock listings, bank
liabilities, loans, associations, selections, quotas, and suggestions are
persistent. StateCraft share reservations and private ownership live in the
same shared snapshot, not separately committed economy files.

Domain mutations mark the shared runtime dirty; the core owns flushing and
backups. This includes accrual from `loan show`, assessments before a refused
purchase, expired-listing refunds, and scheduled financial changes, even when
the surrounding command fails. Established-player balance/history/menu queries
that do not change persistent state do not request a snapshot. Fractional
interest changes remain persistent; advancing only an idle interest clock
(zero-rate deposits or capped loans) can wait for periodic/explicit saves.
Player and console commands use the core's shared invocation/persistence
gateway, including its disk-error pause behavior.
Scheduled work also pauses after a core disk-write failure. Unexpected scheduled
errors are logged and pause that scheduler until an operator fixes the cause
and successfully reloads.

Normal transactions are server-thread atomic and preflight inventory, limits,
funds, overflow, ownership, and reserve constraints. This is **not** a distributed
transaction with Minecraft's separately saved player/chunk files: back up and
restore the **entire world**, and do not promise cash/inventory crash atomicity
by restoring only a StateCraft JSON file. See [WORLD_DATA.md](WORLD_DATA.md).

`EconomyAccess.isAccountInUse` considers balances, unpaid obligations, marketplace/
property/share listings and their national origins, queued item escrow, bank
identities/liabilities, live loans, and outstanding saved property-tax bases. Core
deletion/unclaim/transfer guards must honor these checks; there is no operator
"forget debt" button in this module.

### Exact API integration

Governance retains its final title-commitment, acquisition-eligibility, and
shareholder-limit checks. A domain refusal restores the ledger balances,
spending counters, history, and any staged cash inventory. Cash-funded title
settlement never relies on transferring the title back to a former owner who
might no longer qualify for a new acquisition.

No shared API signature was changed. `EconomyEngine` implements the supplied
`EconomyAccess`; governance is accessed through the supplied immutable views and
permission, mail, property, and share-reservation methods. `transferBatch` is a
**trusted server integration API**, since its shared signature has no `Actor`.
Untrusted client/command routes always use the actor-authorized services instead.

The Forge adapter uses the shared boot/load/install/register/dirty hooks,
runtime invocation and writable-state checks, and
`ForgeConfigBinding.loaded(...)` / `reload()` methods. It does not add a
competing save file.

GUI namespace is `economy`. Registered page IDs are:
`economy:atm`, `economy:hub`, `economy:market`, `economy:property`,
`economy:company`, `economy:bank`, `economy:stock`, `economy:guide`,
`economy:tax`, `economy:dashboard`, `economy:loans`, `economy:deliveries`,
and the unlisted `economy:detail`. The Forge blocks/items use exactly the IDs
in the resource pack.

### Presentation integration API

`EconomyPresentation` implements the shared `UiProvider`:

```java
public EconomyPresentation(EconomyEngine engine);
public UiView view(UiContext context);
public UiView dashboard(Actor actor, String search, int offset);
public ActionPreview preview(Actor actor, ActionSelection selection, InventoryPort inventory);
```

The Forge runtime supplies the authoritative actor and inventory on both review
and final verification. The presentation uses typed `ACCOUNT`, `BANK`, `LOAN`,
`MARKET_LISTING`, `STOCK_LISTING`, `CLAIM`, `COMPANY`, `ARREARS`, `DELIVERY`, and
section-navigation `DASHBOARD` entity references. Arrear reference IDs are
stable SHA-256 digests of persistent debt IDs to stay within the shared ID limit.
Every contextual action names its registered source page, exact template, and
seed map. Every economy menu action explicitly declares query, navigation, or
mutation intent; the financial flag denotes actions that may move money now.
Future-term/consent mutations still receive a full parameter/obligation review.

Views return at most twenty rows, with search capped at eighty characters and
offsets bounded by the shared 100,000 limit. Review payloads stay within forty
lines. Large tax/dividend recipient groups display up to twenty account totals
and an explicit remainder count, while the material fingerprint binds **all**
actual payment recipients and amounts. Inventory collection examines at most
thirty-two delivery entries per attempt; remaining entries persist.

New presentation text uses `ui.statecraft.economy.*` translation keys. English
templates live in `tools\translations\economy.json` and are merged into the
generated core language resources. Record names, IDs, item metadata, and original
contract text remain data, not executable UI instructions.

## Tests

The JUnit 5 `EconomyEngineTest` suite executes deterministic financial and
JSON/persistence scenarios against the real engine, with a pure clock, inventory,
and governance fake. Coverage includes negative/overflow batch rollback,
authorization on every account route, cash precision/full-inventory failures,
taxes/quotas, partial SNBT escrow/refunds/expiry/offline delivery, valuation and
arrears, combined wallet/bank/cash property funding, dividends, bank liabilities/
reserve protection including unprocessed contractual interest, borrower limits
at application and funding, loan repayment/default/consented repossession,
former-owner mandatory property collections, strict UUID recipients, dirty
callbacks for ordinary/read/scheduled/error paths, share reservations, bounded
histories, pagination, atomic price edits, malformed reloads, and financial JSON
round trips.

`EconomyPresentationScenarios`, also wired into `EconomyEngineTest`, covers
purchase-confirmation/collection failures, current autopay, typed-view privacy,
forged references, retained history, dashboard role scoping/paging, every
registered mutation's pure review, quote-to-commit amounts, cash/Hub/trade taxes,
all four property funding modes, interest projection, material fingerprints,
registered contextual seeds, and field constraints.

Run the suite with the repository's existing Gradle test runner:

```text
gradlew.bat :statecraft-economy:test --tests dev.statecraft.economy.EconomyEngineTest
```

The same scenario bodies also have plain Java entry points:
`dev.statecraft.economy.EconomyRegressionScenarios` and
`dev.statecraft.economy.EconomyPresentationScenarios`, plus
`dev.statecraft.economy.EconomyDataScenarios`. The latter requires Gson and the
economy resource directory on the classpath. File fixtures are created under the
current project's `build` directory and removed after each scenario, never in
an operating-system temporary directory.
