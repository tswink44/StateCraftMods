# StateCraft Economy — TODO & Feature Suggestions

*Generated from codebase analysis — February 19, 2026*

---

## 🔴 BUGS (Must Fix)

### 1. `forceWithdraw` Never Actually Forces a Negative Balance
**Severity: Critical** — Breaks the entire tax repossession system.

`EconomyManager.forceWithdraw()` calls `BankAccount.subtract(amount)`, but `subtract()` silently returns `false` and does nothing when `balance < amount`. The player's balance stays at whatever it was — it never goes negative. This means:
- Tax collection silently fails for players who can't pay
- Negative balance tracking is never triggered
- Chunk repossession after 3 unpaid tax periods **never happens**

**Fix:** Add a `forceSubtract(double)` method to `BankAccount` that allows the balance to go negative, and call it from `forceWithdraw`:
```java
public void forceSubtract(double amount) {
    this.balance -= amount;  // No guard — allows negative
}
```

### 2. `BankAccount.setBalance()` Clamps to Zero
`setBalance()` calls `Math.max(0, balance)` — this prevents serialization from ever restoring a negative balance from saved data. If a player gets a forced negative balance, upon world reload it gets reset to `$0.00`. Needs a separate path or removal of the clamp for forced-negative scenarios.

### 3. Config-Defined Interest Never Runs
`EconomyConfig` defines `ENABLE_INTEREST` and `DAILY_INTEREST_RATE` but there is **no tick handler or scheduled job** that actually calculates or applies interest to accounts. `BankAccount.lastInterestTime` is tracked but never read by any system. The `Bank` class also has `interestRate` but it's unused.

### 4. Config-Defined Transfer Limits Not Enforced
`EconomyConfig.MAX_TRANSFER_PER_DAY` and `TRANSFER_FEE_PERCENT` are defined in config but **never checked** during transfers in `EconomyManager.transfer()` or the ATM packet handler.

### 5. Bank Withdrawal/Transfer Fees Not Applied
`Bank` class stores `withdrawalFee` and `transferFee` properties, but they are **never deducted** during ATM operations. The fee fields are persisted to NBT but have zero mechanical effect.

### 6. `TaxationManager` State Not Persisted
`TaxationManager.lastTaxCollection`, `taxPeriodTicks`, `enabled`, and `negativeBalanceCounts` are all in-memory only. On server restart:
- Tax timer resets (could double-tax or skip a cycle)
- Negative balance strike counts reset to 0 (repossession counter lost)
- Admin-configured tax period and enable/disable state is lost

---

## 🟡 INCOMPLETE / STUBBED FEATURES

### 7. Loans System Stubbed But Not Implemented
`Bank.allowsLoans` field exists (persisted in NBT) with a comment "Future feature" but there is no loan application, repayment tracking, interest accrual, or default handling. No UI or commands exist for loans.

### 8. Bank Card Only Shows Personal Balance
`BankCardItem` right-click only shows personal account balance. It doesn't show nation/state/city balances the player has access to. It also doesn't open the ATM — it's just a chat message.

### 9. Eminent Domain Payment Not Verified
`PolicyType.EMINENT_DOMAIN` says "Owner receives 10x the tax valuation from nation treasury" but the actual implementation should be audited to ensure the nation treasury is actually debited and the owner credited.

### 10. Import Tariff Has No Marketplace to Enforce Against
`PolicyType.IMPORT_TARIFF` exists in legislation but there is no cross-nation trading or marketplace system to apply tariffs to. Listed in `BugsAndFeaturesList` as needing a marketplace.

### 11. Minimum Wage Is Roleplay Only
`PolicyType.MINIMUM_WAGE` has no mechanical enforcement. Listed in `BugsAndFeaturesList` as roleplay-only.

---

## 🟢 SUGGESTED FEATURE ADDITIONS

### Economy Core

#### 12. Interest System Implementation
Wire up the existing `ENABLE_INTEREST` config, `DAILY_INTEREST_RATE`, and `Bank.interestRate` to a periodic tick handler that:
- Calculates interest per-account based on their bank's rate
- Records interest payments as transactions (visible in Account Activity)
- Respects `lastInterestTime` to avoid double-payments on restart

#### 13. Transfer Fee & Daily Limit Enforcement
Wire up `MAX_TRANSFER_PER_DAY` (needs a per-player daily counter) and `TRANSFER_FEE_PERCENT` (deduct fee, record as FEE transaction).

#### 14. Inflation / Money Supply Dashboard
Admin command or GUI showing total money supply (sum of all player + government accounts), money velocity (total transaction volume per tax period), and average balance. Useful for server admins to tune the economy.

#### 15. Income Tax (in addition to Property Tax)
Tax a percentage of player income (deposits, trades, transfers received) to government treasuries. Could be configured per-nation via legislation.

### Account Activity Enhancements

#### 16. Filterable/Searchable Transaction History
Add filter buttons to `AccountActivityScreen` for transaction type (Tax, Transfer, Deposit, etc.) and a date range picker. Currently all 100 transactions are shown in one flat list.

#### 17. Export Account Activity
Add a button to export transaction history to a CSV or text file. Useful for government accountability in roleplay scenarios.

#### 18. Running Balance Column
Show the account's running balance after each transaction in the activity screen (not just the amount in/out). Helps track how balance changed over time.

### ATM & GUI

#### 19. Transaction Confirmation Dialog
For large transactions (configurable threshold, e.g., >$10,000), show a confirmation dialog before executing. Prevents accidental large withdrawals from government accounts.

#### 20. ATM Favorites / Quick Transfer
Allow players to mark frequent transfer recipients as favorites for faster access in the transfer screen.

#### 21. Bank Card Opens ATM Remotely
Let the Bank Card item open the SimpleATMScreen without needing to be near a physical ATM block (could be limited by config — e.g., balance-check only without physical ATM, or full access).

### Government & Shared Accounts

#### 22. Budget / Spending Limits per Official
Allow nations to set per-role spending limits (e.g., mayor can withdraw max $5,000/day from city treasury, governor max $20,000/day from state). Currently any admin can withdraw unlimited amounts.

#### 23. Shared Accounts (Companies/Organizations)
Support for player-created shared accounts (not tied to government). Multiple players can be authorized with configurable permissions (view-only, deposit-only, full access). The user specifically mentioned this for "banks and companies."

#### 24. Government Budget Allocation
Let government officials allocate portions of treasury to named sub-budgets (e.g., "Infrastructure", "Defense", "Education") for organizational/roleplay purposes. Transactions can be tagged to a budget.

#### 25. Treasury Report / Statement
Auto-generated periodic summary (like the tax mail) showing total income, total expenses, net change, and top transaction categories for each government account.

### Marketplace & Trading

#### 26. Player-to-Player Marketplace
A global or per-city marketplace where players can list items for sale at a set price. Other players can browse and buy. Trading Hub currently only supports selling items to the server for a configured value — not to other players.

#### 27. Cross-Nation Trading with Tariffs
Once a marketplace exists, enforce `IMPORT_TARIFF` on trades between players of different nations. Tariff revenue goes to the buyer's nation treasury.

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

---

## 📋 PRIORITY ORDER (Recommended)

1. **Bug #1** — `forceWithdraw` broken (Critical — tax system non-functional)
2. **Bug #2** — `setBalance` clamp (Critical — related to #1)
3. **Bug #6** — TaxationManager state not persisted (High — data loss on restart)
4. **Bug #3** — Interest never runs (Medium — advertised feature doesn't work)
5. **Bug #4** — Transfer limits not enforced (Medium)
6. **Bug #5** — Bank fees not applied (Low — no banks use fees yet)
7. **Feature #23** — Shared accounts (High — user requested)
8. **Feature #22** — Spending limits (High — security for government accounts)
9. **Feature #12** — Interest system (Medium — config exists, just needs wiring)
10. **Feature #19** — Confirmation dialog (Medium — UX safety)

