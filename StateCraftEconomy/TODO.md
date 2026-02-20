# StateCraft Economy — TODO & Feature Suggestions

*Generated from codebase analysis — February 19, 2026*

---

## 🔴 BUGS (Must Fix)

### ~~1. `forceWithdraw` Never Actually Forces a Negative Balance~~ ✅ FIXED
~~**Severity: Critical** — Breaks the entire tax repossession system.~~

**Fixed:** Added `BankAccount.forceSubtract(double)` which subtracts without a balance guard, and updated `EconomyManager.forceWithdraw()` to call it. Tax collection now correctly creates negative balances, enabling the repossession system.

### ~~2. `BankAccount.setBalance()` Clamps to Zero~~ ✅ FIXED
~~`setBalance()` calls `Math.max(0, balance)` — this prevents serialization from ever restoring a negative balance from saved data.~~

**Fixed:** Removed `Math.max(0, balance)` clamp from `setBalance()`. Negative balances now persist correctly through save/load cycles.

### ~~3. Config-Defined Interest Never Runs~~ ✅ FIXED
~~`EconomyConfig` defines `ENABLE_INTEREST` and `DAILY_INTEREST_RATE` but there is **no tick handler or scheduled job** that actually calculates or applies interest to accounts. `BankAccount.lastInterestTime` is tracked but never read by any system. The `Bank` class also has `interestRate` but it's unused.~~

**Fixed:** Interest is a player-made bank feature only — the default Central Bank pays no interest (rate set to 0). The `ENABLE_INTEREST` and `DAILY_INTEREST_RATE` config options are deprecated and have no effect. Interest is handled by the `BankCompany`/`BankManager` system: player-created bank companies set their own deposit interest rate (default 2% per period, configurable via `/eco bank set interest`). `BankManager.tick()` → `payDepositorInterest()` processes interest for all player-made banks each period, paying depositors from the bank's treasury. The `BankAccount.lastInterestTime` field on personal accounts is retained for backward compatibility but is not used for interest processing (interest timing is tracked per-bank via `BankCompany.lastInterestTime`).

### ~~4. Config-Defined Transfer Limits Not Enforced~~ ✅ FIXED
~~`EconomyConfig.MAX_TRANSFER_PER_DAY` and `TRANSFER_FEE_PERCENT` are defined in config but **never checked** during transfers in `EconomyManager.transfer()` or the ATM packet handler.~~

**Fixed:** Removed both config options entirely. There is no cap on personal fund transfers, and while taxes may be levied on transfers via other mechanisms (sales tax, import tariffs), there is no universal fee on the transaction itself. The `MAX_TRANSFER_PER_DAY` field, `TRANSFER_FEE_PERCENT` field, their config definitions, and the fee calculation logic in `EconomyManager.transfer()` have all been deleted.

### ~~5. Bank Withdrawal/Transfer Fees Not Applied~~ ✅ FIXED
~~`Bank` class stores `withdrawalFee` and `transferFee` properties, but they are **never deducted** during ATM operations. The fee fields are persisted to NBT but have zero mechanical effect.~~

**Fixed:** Bank fees are now enforced in all withdrawal and transfer paths. The default Central Bank has 0 fees (no change for most players). Player-made banks can set their own withdrawal and transfer fee rates. Implementation:
- `EconomyManager.withdraw()` now calculates and deducts the bank's withdrawal fee on top of the withdrawal amount. Fee is recorded as a `FEE` transaction and credited to the bank's company treasury.
- `EconomyManager.transfer()` now calculates and deducts the bank's transfer fee from the sender. Fee is recorded and credited to the bank's company treasury.
- `ATMTransactionPacket` `BANK_DEPOSIT` withdrawal path applies the bank's withdrawal fee. Fee reduces the depositor's balance but stays in the bank treasury (improving reserve compliance).
- Helper methods `getWithdrawalFee()`, `getTransferFee()`, and `creditBankFee()` added to `EconomyManager` for centralized fee lookup and crediting.
- `BankCompany.subtractDepositorBalanceRaw()` added for fee deductions without reserve checks (since the money stays in the bank).

### ~~6. `TaxationManager` State Not Persisted~~ ✅ FIXED
~~`TaxationManager.lastTaxCollection`, `taxPeriodTicks`, `enabled`, and `negativeBalanceCounts` are all in-memory only.~~

**Fixed:** Added `save()`/`load()` NBT methods to `TaxationManager` that persist all four fields. Integrated into `EconomySavedData` save/load flow. Added `dirty` flag so saves are triggered when taxation state changes. `WorldLoadHandler` now checks both `EconomyManager.isDirty()` and `TaxationManager.isDirty()` to trigger saves.

---

## 🟡 INCOMPLETE / STUBBED FEATURES

### ~~7. Loans System Stubbed But Not Implemented~~ ✅ IMPLEMENTED
~~`Bank.allowsLoans` field exists (persisted in NBT) with a comment "Future feature" but there is no loan application, repayment tracking, interest accrual, or default handling. No UI or commands exist for loans.~~

**Implemented:** Full banking system with the following features:
- **`BankCompany.java`** — Bank-specific data layer attached to `CompanyType.BANK` companies. Manages depositor balances, membership, reserve ratio enforcement, loan tracking, and interest configuration.
- **`Loan.java`** — Loan entity with principal, interest rate, repayment tracking, missed payment counter, and status lifecycle (`ACTIVE` → `REPAID`/`DEFAULTED`/`SEIZED`).
- **`BankManager.java`** — Singleton managing all bank companies. Three periodic tick loops: depositor interest distribution (pays from bank treasury), loan interest accrual, and loan default handling (seize balance or repossess property).
- **Depositor accounts** — Players open accounts at banks (`/eco bank open <name>`), deposit/withdraw funds. Deposits are tracked as depositor balances (bank liabilities) separate from the bank's company treasury.
- **Fractional reserve system** — Banks must maintain `reserveRatio` (default 20%, configurable) of total deposits in treasury. Withdrawals that would breach reserve are denied. New loans blocked when non-compliant.
- **Loan system** — Players apply for loans (`/eco bank loan apply <amount> <bank>`). Bank transfers principal from treasury to borrower. Interest accrues per period. Missed payments tracked; 3 consecutive misses triggers default.
- **Default strategies** — Per-bank configurable: `SEIZE_BALANCE` (force-withdraw from borrower, can go negative) or `REPOSSESS_PROPERTY` (repossess chunks via StateCraft integration).
- **Depositor insurance** — On bank dissolution, depositors are paid first from bank treasury before shareholders. Shortfalls are logged and depositors notified.
- **ATM integration** — `BANK_DEPOSIT` account type appears in the ATM account selector for bank members. Deposit/withdraw/transfer all supported with reserve ratio enforcement.
- **Reserve ratio as national policy** — Config sets global minimum; national legislature `RESERVE_RATIO` policy can set a higher floor per-nation.
- **Commands** — `/eco bank open|close|deposit|withdraw|balance|info|list|loan apply|loan repay|loan list|set interest|set loanrate|set reserve|set defaultstrategy`.
- **Config** — `[banking]` section with `defaultReserveRatio`, `maxLoanAmount`, `maxActiveLoansPerPlayer`, `loanDefaultThreshold`, `bankInterestPeriodTicks`, `loanInterestAccrualPeriodTicks`, `defaultLoanInterestRate`, `defaultDepositInterestRate`, `bankRegistrationFee`.

### 8. Bank Card Only Shows Personal Balance
`BankCardItem` right-click only shows personal account balance. It doesn't show nation/state/city balances the player has access to. It also doesn't open the ATM — it's just a chat message.

### ~~9. Eminent Domain Payment Not Verified~~ ✅ FIXED
~~`PolicyType.EMINENT_DOMAIN` says "Owner receives 10x the tax valuation from nation treasury" but the actual implementation should be audited to ensure the nation treasury is actually debited and the owner credited.~~

**Fixed:** Audit found 4 issues: (A) Player received zero compensation when nation treasury had insufficient funds — now the player is always paid regardless of treasury balance. (B) Zero valuation caused the entire payment block to be skipped — now falls back to `nation.getBaseChunkValue()` as a minimum. (C) No notification was sent to the property owner — now sends a detailed mail with valuation, compensation amount, and payment status. (D) Transaction wasn't recorded when bugs A/B prevented the deposit call — now the deposit always executes.

### 10. Import Tariff Has No Marketplace to Enforce Against
~~`PolicyType.IMPORT_TARIFF` exists in legislation but there is no cross-nation trading or marketplace system to apply tariffs to. Listed in `BugsAndFeaturesList` as needing a marketplace.~~

**Fixed:** Implemented global Marketplace system. Import tariffs are now enforced on cross-nation marketplace purchases. Tariff rate is read from the buyer's nation `IMPORT_TARIFF` legislature policy. Tariff revenue deposited to buyer's nation treasury. Same-nation trades are tariff-free.

### 11. Minimum Wage Is Roleplay Only
`PolicyType.MINIMUM_WAGE` has no mechanical enforcement. Listed in `BugsAndFeaturesList` as roleplay-only.

### ~~12. Economic Emergency Not Enforced in ATM~~ ✅ FIXED
~~When `ECONOMIC_EMERGENCY` is active in StateCraft, the economy mod's ATM never checks for it, allowing non-leaders to withdraw from government treasuries.~~

**Fixed:** (Cross-mod fix — see StateCraft TODO Bug #2 for the full implementation.)
- Added `StateCraftIntegration.isEconomicEmergencyActive(nationId)` and `StateCraftIntegration.isNationLeader(playerId, nationId)` — reflection-based queries against `EmergencyPowerManager` in StateCraft.
- `ATMTransactionPacket` WITHDRAW handler now checks for economic emergency on NATION/STATE/CITY withdrawals. If active and the player is not the nation leader, the withdrawal is rejected with `§c[ECONOMIC EMERGENCY] Treasury withdrawals are frozen`.
- `ATMTransactionPacket.transferFromEntity()` performs the same check for transfers from government accounts during economic emergency.

---

## 🟢 SUGGESTED FEATURE ADDITIONS

### Economy Core

#### ~~12. Interest System Implementation~~ ✅ SUPERSEDED
~~Wire up the existing `ENABLE_INTEREST` config, `DAILY_INTEREST_RATE`, and `Bank.interestRate` to a periodic tick handler.~~

**Superseded:** Interest is now handled by the `BankCompany`/`BankManager` system. Player-created bank companies set their own deposit interest rate and pay depositors from the bank's treasury each period. The default Central Bank pays no interest. The deprecated `ENABLE_INTEREST` and `DAILY_INTEREST_RATE` config options have no effect.

#### ~~13. Transfer Fee & Daily Limit Enforcement~~ ✅ REMOVED
~~Wire up `MAX_TRANSFER_PER_DAY` (needs a per-player daily counter) and `TRANSFER_FEE_PERCENT` (deduct fee, record as FEE transaction).~~

**Removed:** Both config options deleted. No universal transfer fee or daily transfer cap exists — transfers of personal funds are unrestricted. Taxes on economic activity are handled via sales tax and import tariff systems instead.

#### 14. Inflation / Money Supply Dashboard
Admin command or GUI showing total money supply (sum of all player + government accounts), money velocity (total transaction volume per tax period), and average balance. Useful for server admins to tune the economy.

#### 15. Income Tax (in addition to Property Tax)
Tax a percentage of player income (deposits, trades, transfers received) to government treasuries. Could be configured per-nation via legislation.

### Account Activity Enhancements

#### ~~16. Filterable/Searchable Transaction History~~ ✅ IMPLEMENTED
~~Add filter buttons to `AccountActivityScreen` for transaction type (Tax, Transfer, Deposit, etc.) and a date range picker. Currently all 100 transactions are shown in one flat list.~~

**Implemented:** Added 7 filter category buttons (All, Deposits, Withdrawals, Transfers, Tax, Trades, Fees) rendered as a toggle bar below the title. Added a text search box that filters by description, initiator name, type label, or amount. Entry count shows "X of Y transactions" when filtered. Scroll resets on filter change. Empty state shows context-aware message.

#### 17. Export Account Activity
Add a button to export transaction history to a CSV or text file. Useful for government accountability in roleplay scenarios.

#### ~~18. Running Balance Column~~ ✅ IMPLEMENTED
~~Show the account's running balance after each transaction in the activity screen (not just the amount in/out). Helps track how balance changed over time.~~

**Implemented:** Added `runningBalance` field to `ActivityEntry` record and packet serialization. Server computes running balance by starting from the current account balance and walking backwards through the newest-first transaction list, undoing each transaction to reconstruct the historical balance at each point. New "Balance" column displayed between Amount and Details in the GUI, with negative balances shown in red.

### ATM & GUI

#### ~~19. Transaction Confirmation Dialog~~ ✅ IMPLEMENTED
~~For large transactions (configurable threshold, e.g., >$10,000), show a confirmation dialog before executing. Prevents accidental large withdrawals from government accounts.~~

**Implemented:** Added `largeTransactionThreshold` config option under `[atm]` (default $10,000, 0 = disabled). Created `ConfirmTransactionScreen` — a modal overlay with a red warning header ("⚠ Confirm Large Withdrawal"), transaction details (amount, account, recipient for transfers), and Confirm/Cancel buttons. `SimpleATMScreen` perform methods split into validate → confirm → execute: `performDeposit()`/`performWithdraw()`/`performTransfer()` validate inputs then check the threshold; if exceeded, open the confirmation dialog passing the corresponding `executeDeposit()`/`executeWithdraw()`/`executeTransfer()` as a callback. Cancel or Escape returns to the ATM without executing. The ATM screen is passed as `parentScreen` so all input state (amount, note, recipient) is preserved through the confirmation flow.

#### ~~20. ATM Favorites / Quick Transfer~~ ✅ IMPLEMENTED
~~Allow players to mark frequent transfer recipients as favorites for faster access in the transfer screen.~~

**Implemented:** Created `TransferFavoritesManager` — a client-side singleton that persists favorites to `config/statecraft_economy_favorites.json` per game directory. In the transfer recipient dropdown, each entry now shows a clickable star icon (★/☆) on the left. Clicking the star toggles favorite status. Favorites are automatically sorted to the top of the recipient list (both when showing all and when filtering by search). A thin divider line separates favorites from non-favorites in the dropdown. Also fixed a pre-existing bug where `performTransfer()` used `filteredRecipients` with an index from `transferRecipients`.

#### ~~21. Bank Card Opens ATM Remotely~~ ✅ IMPLEMENTED
~~Let the Bank Card item open the SimpleATMScreen without needing to be near a physical ATM block (could be limited by config — e.g., balance-check only without physical ATM, or full access).~~

**Implemented:** Added `bankCardMode` config option under the `[atm]` section with two modes: `"full"` (default) opens the full ATM interface remotely via `OpenATMScreenPacket`, and `"balance_only"` shows balance in chat (original behavior). `BankCardItem` updated to check the config on right-click and send the appropriate response. Tooltip dynamically updates based on the config mode.

### Government & Shared Accounts

#### ~~22. Budget / Spending Limits per Official~~ ✅ IMPLEMENTED
~~Allow nations to set per-role spending limits (e.g., mayor can withdraw max $5,000/day from city treasury, governor max $20,000/day from state). Currently any admin can withdraw unlimited amounts.~~

**Implemented:** Created `SpendingLimitManager` — a server-side singleton that tracks daily cumulative spending per player per government account, with automatic UTC-midnight day rollover. Configurable default limits per role in `EconomyConfig` under `[spendingLimits]`: `nationLeaderDailyLimit` (default 0/unlimited), `nationAdminDailyLimit` (default $50,000), `stateGovernorDailyLimit` (default $20,000), `cityMayorDailyLimit` (default $5,000). Nation leaders can have their limit overridden per-nation via a new `LEADER_SPENDING_LIMIT` legislature policy. Role detection via `StateCraftIntegration.getPlayerGovernmentRole()` maps each player to their highest role (NATION_LEADER > NATION_ADMIN > STATE_GOVERNOR > CITY_MAYOR) for the specific account being accessed. Limits enforced in all withdrawal/transfer paths: ATM withdrawals, ATM transfers from government accounts, `/eco nation withdraw` command, and nation treasury packet handler. Daily spending and nation leader overrides are persisted via `EconomySavedData`. Error messages show limit, amount spent today, and remaining allowance.

#### ~~23. Shared Accounts (Companies/Organizations)~~ ✅ IMPLEMENTED
~~Support for player-created shared accounts (not tied to government). Multiple players can be authorized with configurable permissions (view-only, deposit-only, full access). The user specifically mentioned this for "banks and companies."~~

**Implemented:** Full company system with the following features:
- **`Company.java`** — Core entity with UUID, name, founder, officers, shareholders (share counts), total shares, dividend config, HQ city, and full NBT persistence.
- **`CompanyManager.java`** — Singleton registry with CRUD, dividend tick distribution, corporate tax collection, name uniqueness, and configurable max companies per player (default 3).
- **`CompanyVaultBlock` / `CompanyVaultBlockEntity`** — Physical shared storage block (54 slots, double-chest equivalent) linked to a company UUID. Only officers can access. Auto-assigns to placer's first company on placement.
- **`CompanyVaultMenu` / `CompanyVaultScreen`** — Server container and client screen using vanilla generic_54 texture.
- **Treasury integration** — `COMPANY` added to `BankAccount.AccountType`, `EconomyManager` has `getOrCreateCompanyTreasury()`, `getCompanyBalance()`, and `getCompanyTreasuries()`. Full ATM support: company accounts appear in account selector for officers, deposits/withdrawals/transfers all support COMPANY type.
- **Dividend system** — Configurable rate (% of balance) and period (ticks). Automatically distributes to shareholders proportionally. Skips cycle with mail notification if insufficient funds.
- **Corporate taxation** — Flat rate on company balance (default 2%, configurable) collected each tax period. Tax deposited to HQ city's state treasury.
- **Registration fee** — Configurable fee (default $1,000) paid to the state treasury on company creation.
- **Spending limits** — `COMPANY_OFFICER` role added to `SpendingLimitManager` with configurable daily limit (default $20,000).
- **Commands** — `/eco company create|info|list|officer|shares|dividend|dissolve|rename|hq` full command tree.
- **Config** — `[companies]` section with `registrationFee`, `maxCompaniesPerPlayer`, `companyTaxRate`, `companyOfficerDailyLimit`.
- **Persistence** — Company treasuries in `EconomySavedData`, `CompanyManager` state in `CompanyManager` NBT tag, all integrated into save/load flow and dirty-flag checking.

#### 24. Government Budget Allocation
Let government officials allocate portions of treasury to named sub-budgets (e.g., "Infrastructure", "Defense", "Education") for organizational/roleplay purposes. Transactions can be tagged to a budget.

#### 25. Treasury Report / Statement
Auto-generated periodic summary (like the tax mail) showing total income, total expenses, net change, and top transaction categories for each government account.

### Marketplace & Trading

#### 26. Player-to-Player Marketplace ✅ IMPLEMENTED
~~A global or per-city marketplace where players can list items for sale at a set price. Other players can browse and buy. Trading Hub currently only supports selling items to the server for a configured value — not to other players.~~

**Implemented:** Full global marketplace system:
- **`MarketListing.java`** — Data class with UUID, seller info, item template, quantity, price, status lifecycle, and NBT persistence.
- **`MarketplaceManager.java`** — Server-side singleton managing all listings. `createListing()` validates, charges listing fee (configurable %, deposited to seller's city treasury), removes items from seller inventory, creates listing. `purchaseListing()` calculates sales tax (city/state/nation cascading rates matching TradingHub), calculates import tariff (buyer's nation `IMPORT_TARIFF` policy, 0 if same nation), deducts total from buyer, pays seller net proceeds, distributes taxes to treasuries, delivers items, records transactions, sends mail notifications. `cancelListing()` returns items to seller. `tick()` expires old listings (configurable hours, default 1 week). Full NBT save/load.
- **`MarketplaceBlock` / `MarketplaceBlockEntity`** — Directional block, tracks owner and city. On interact sends `OpenMarketplaceScreenPacket`. Config option `requireCityPlacement` (default true) enforces placement in claimed city chunks only.
- **`MarketplaceScreen`** — Three-tab GUI: **Browse** (scrollable list with item icon, name, price, qty, seller, search bar, sort buttons: newest/price↑↓/A-Z, buy button per row → quantity input dialog → total cost preview → confirm), **My Listings** (player's active listings with cancel button), **Sell** (clickable inventory grid, price/quantity inputs, list button).
- **Network packets** — `OpenMarketplaceScreenPacket` (S→C), `RequestMarketListingsPacket` (C→S, with search/sort/filter), `SyncMarketListingsPacket` (S→C, paginated entries with item data), `MarketplaceActionPacket` (C→S, LIST/BUY/CANCEL actions).
- **Transaction types** — `MARKETPLACE_PURCHASE`, `MARKETPLACE_SALE`, `IMPORT_TARIFF` added to `Transaction.Type`. `MARKETPLACE` filter category added to `AccountActivityScreen`. Short labels: "Mkt Buy", "Mkt Sell", "Import".
- **Commands** — `/eco market list|search|sell|cancel|my|open` full command tree.
- **Config** — `[marketplace]` section: `enabled`, `maxListingsPerPlayer` (20), `listingFeePct` (1%), `listingExpirationHours` (168), `maxPricePerItem` (0=unlimited), `requireCityPlacement` (true).
- **Persistence** — `MarketplaceManager` save/load in `EconomySavedData`, dirty flag in `WorldLoadHandler`, expiration tick in `StateCraftEconomy.onServerTick()`.

#### 27. Cross-Nation Trading with Tariffs ✅ IMPLEMENTED
~~Once a marketplace exists, enforce `IMPORT_TARIFF` on trades between players of different nations. Tariff revenue goes to the buyer's nation treasury.~~

**Implemented:** Import tariffs enforced in `MarketplaceManager.purchaseListing()`. Tariff rate looked up via `StateCraftIntegration.getImportTariffRate(nationId)` which reads the nation's passed `IMPORT_TARIFF` legislature policy. Same-nation trades have zero tariff. Tariff revenue deposited to buyer's nation treasury with `IMPORT_TARIFF` transaction type.

#### 28. Auction System
Time-limited auctions for rare items. Players bid, highest bidder wins. Could integrate with the contract system for government procurement.

#### 29. Trading Hub Sales Tax per City
Allow each city to set its own sales tax on Trading Hub transactions within its territory (on top of nation sales tax). Revenue goes to the city treasury.

### Tax System

#### 30. Tax Rate by Zone / Chunk Type
Allow cities to set different tax rates for different areas (commercial district vs. residential vs. undeveloped). Currently all chunks in a city use the same rate.

#### 31. Tax Exemption Period for New Claims
Give newly purchased chunks a grace period (configurable) before property tax kicks in, to encourage development.

#### 32. Tax History Summary Command
Command like `/eco tax history` that shows a player their tax payment history across all owned chunks.

### Persistence & Data

#### 33. Periodic Auto-Backup of Economy Data
Auto-save economy data to a timestamped backup file periodically (e.g., every hour). If something goes wrong, admins can restore from a backup.

#### 34. Admin Transaction Log File
Write all transactions above a configurable threshold to a separate log file for admin auditing, independent of the in-game transaction history.

### Company System Enhancements

#### 35. Share Transfer Market
Add a share listing system where shareholders can list shares at a price and other players can buy them. Could be command-based (`/eco company shares list <count> <price>`, `/eco company shares buy <company> <count>`) or a GUI in the ATM. The `Company.transferShares()` method already supports both direct and market transfers.

#### 36. Company Chunk Ownership
Allow companies to claim and own chunks (land) via StateCraft integration. Would require a new owner type in `ClaimedChunk` and integration hooks for company-owned property tax collection. The `headquartersCityId` field already establishes the jurisdiction link.

#### 37. Company Tax Rate via Legislation
Allow states to set their own corporate tax rate via legislature policy, overriding the global config default. Similar to how `LEADER_SPENDING_LIMIT` overrides per-nation.

#### 38. Expandable Company Vault
Allow companies to have multiple vault blocks or expand vault capacity beyond 54 slots. Could use a tiered system (Small Vault = 27 slots, Medium = 54, Large = 108) or allow multiple linked vaults per company.

#### 39. Loan Collateral System
For larger loans, banks should be able to require collateral (specific chunks or items) upfront. Collateral is automatically seized on default without needing to go through the general repossession system. Could include: chunk liens (chunk cannot be sold while used as collateral), item escrow (items held in a locked vault slot until loan repaid).

#### 40. Credit Score / Loan Eligibility
Track a per-player credit score based on loan repayment history. On-time payments improve the score, missed payments reduce it. Banks could use credit scores to gate loan amounts, set variable interest rates, or automatically approve/deny applications. Score components: payment history (40%), outstanding debt ratio (30%), credit history length (20%), recent applications (10%).

---

## 📋 PRIORITY ORDER (Recommended)

1. ~~**Bug #1** — `forceWithdraw` broken~~ ✅ FIXED
2. ~~**Bug #2** — `setBalance` clamp~~ ✅ FIXED
3. ~~**Bug #6** — TaxationManager state not persisted~~ ✅ FIXED
4. ~~**Bug #3** — Interest never runs~~ ✅ FIXED (interest is per-bank via BankCompany system)
5. ~~**Bug #4** — Transfer limits not enforced~~ ✅ FIXED (configs removed — no universal transfer fee)
6. ~~**Bug #5** — Bank fees not applied~~ ✅ FIXED (fees enforced in withdraw/transfer, default bank has 0 fees)
7. ~~**Bug #12** — Economic emergency not enforced in ATM~~ ✅ FIXED (cross-mod check via StateCraftIntegration reflection)
8. ~~**Feature #23** — Shared accounts / Companies~~ ✅ IMPLEMENTED
9. ~~**Feature #22** — Spending limits~~ ✅ IMPLEMENTED
10. **Feature #14** — Inflation / money supply dashboard (Low — admin tooling)
11. ~~**Feature #19** — Confirmation dialog~~ ✅ IMPLEMENTED
12. **Feature #35** — Share transfer market (Medium — user requested for future)
13. **Feature #36** — Company chunk ownership (Medium — user requested for future)
14. **Feature #37** — Company tax rate via legislation (Low — enhancement)

