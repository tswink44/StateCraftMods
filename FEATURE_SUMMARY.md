# StateCraft Mods Feature Summary

StateCraft is a Minecraft 1.20.1 Forge mod suite for player-run nations, territorial government, politics, and a connected economy. The suite contains two mods:

| Mod | Purpose |
| --- | --- |
| **StateCraft** | Nations, government hierarchy, territory, elections, legislation, diplomacy, companies, contracts, and communications |
| **StateCraft Economy** | Currency, banking, treasuries, taxation, commerce, property valuation, company finance, and stock trading |

The core StateCraft mod can provide non-economic governance features by itself. Features involving balances, fees, taxes, treasuries, paid contracts, or monetary treaty terms require StateCraft Economy.

## StateCraft

### Nations and Government

- Organizes territory as **Nation → State → City → Claimed Chunks**.
- Supports nation leaders, officers, state governors, city mayors, members, citizens, and role-based permissions.
- Allows players to create, join, leave, invite to, manage, rename, or disband governments.
- Supports open or invitation-only nation membership.
- Provides configurable limits and fees for nations, states, cities, and claims.
- Includes nation descriptions, tags, flags, leadership appointments, officer management, and government settings.

### Territory, Claims, and Protection

- Claims Minecraft chunks for cities and records their city, state, and nation ownership.
- Enforces adjacency rules, claim limits, permissions, and optional claim fees.
- Supports manual claiming, unclaiming, and automatic claiming while moving.
- Protects claimed land from unauthorized:
  - Block placement and breaking
  - Container and block interaction
  - Entity interaction and attacks
  - Explosion damage
- Supports chunk-specific access permits.
- Applies diplomatic rules to foreign access, allied access, enemy access, and PvP.
- Provides client-side chunk and territory border rendering.
- Includes chunk information, claim management, territory maps, valuation, and marketplace screens.

### Elections

- Runs scheduled nation leadership elections.
- Supports candidate registration, candidate fees, voting, result calculation, and automatic leadership transfer.
- Tracks recent election history.
- Allows configurable election intervals, voting duration, and candidate fees.
- Includes election commands and dedicated election GUIs.

### Legislature and Laws

- Supports bills moving through debate, voting, passage or failure, leader signature or veto, and enactment.
- Enforces quorum and configurable voting periods.
- Supports veto overrides.
- Maintains a national law codex and legislative history.
- Supports constitutional amendments and emergency executive powers with cooldowns.
- Allows officers or other configured government participants to vote.
- Can apply policies affecting government, territory, taxation, tariffs, and other nation settings.
- Also supports custom and roleplay laws that are recorded but do not mechanically change gameplay.

### Diplomacy and War

- Supports war declarations and war-aware access/PvP rules.
- Supports alliance proposals and acceptance.
- Supports negotiated peace treaties with proposal expiry and configurable limits.
- Peace terms can include currency and transfer of claimed chunks.
- Supports post-war truces.
- Can require both nations' legislatures to ratify peace terms.
- Delivers diplomatic notifications through government mail.

### Companies and Government Contracts

- Allows players to create and manage companies.
- Tracks company members, ownership shares, shareholders, proposals, and shareholder votes.
- Supports government contracts with descriptions, selected chunks, bidding, bid review, acceptance, and contract status workflows.
- Integrates company treasuries and paid contracts with StateCraft Economy when installed.

### Mail, Profiles, and User Interface

- Provides persistent player mail with compose, inbox, and message-view screens.
- Provides separate government mailboxes for official notifications.
- Includes player profiles showing nation and role information.
- Includes GUIs for nations, states, cities, members, invitations, claims, elections, laws, diplomacy, companies, contracts, mail, officers, and executive actions.
- Default keybinds:
  - **N** — Open the StateCraft menu
  - **B** — Cycle territory border modes
- Supports JourneyMap and Xaero-style minimap integration for territory data.

### Commands and Administration

- Provides `/statecraft` and `/sc` command aliases.
- Includes command families for nations, states, cities, chunks, information, maps, borders, elections, legislation, diplomacy, companies, contracts, mail, and GUIs.
- Operator tools include:
  - Protection bypass
  - Forced unclaims and government deletion
  - Ownership and leadership reassignment
  - Forced government renaming
  - Detailed claim diagnostics
  - Data integrity auditing and orphan cleanup
- Saves government data as human-readable world data, supports legacy migration, and includes backup and consistency-checking systems.

## StateCraft Economy

### Currency and Accounts

- Adds physical currency in the following denominations:
  - $1
  - $10
  - $100
  - $1,000
  - $10,000
  - $100,000
  - $1,000,000
- Supports configurable currency items and item values.
- Provides persistent accounts for:
  - Players
  - Nations
  - States
  - Cities
  - Companies
- Records transaction and account activity history.
- Supports administrative spending limits.
- Includes economy commands for balances, payments, account modification, leaderboards, item prices, and configuration reloads.

### ATM and Banking

- Adds an ATM block with a networked banking GUI.
- Supports:
  - Balance checks
  - Currency deposits
  - Cash withdrawals
  - Player and account transfers
  - Account selection
  - Transaction activity
  - Tax reports
- Adds bank cards for account access.
- Provides a multi-bank framework with bank identity, branding, rates, fees, and persistent account associations.

### Government Treasuries and Taxation

- Provides separate nation, state, and city treasuries.
- Supports tax rates, tax collection, treasury distribution, transaction records, and tax reporting.
- Supports marketplace sales taxes and import tariffs.
- Supports periodic property tax based on chunk value.
- Allows configured mandatory deductions for taxes and other government obligations.
- Integrates treasury access with StateCraft leadership roles.

### Trading Hub and Item Values

- Adds a Trading Hub block for converting configured items into currency.
- Uses a configurable item-value registry with runtime reload and administrative price management.
- Supports sales tax, sell limits, item suggestions, and Trading Hub settings.
- Includes a recipe guide item and screen.
- Loads custom villager trades from JSON.
- The repository includes trade and shop definitions for standard professions and custom merchants such as bankers, bards, barkeepers, botanists, markets, bakers, winemakers, storage smiths, and Ribbit merchants.

### Player Marketplace

- Adds a Marketplace block, GUI, packets, and commands.
- Allows players to:
  - Create item listings
  - Search listings
  - Buy full or partial quantities
  - View their own listings
  - Cancel listings
- Supports listing fees, listing expiration, seller proceeds, buyer and seller taxes, and import tariffs.
- Persists listing state across world restarts.

### Chunk Market and Property Valuation

- Allows eligible owners or government officials to list and delist claimed chunks.
- Allows players to buy listed chunks and transfers ownership after payment.
- Can fund purchases from bank balances and physical currency.
- Sends sale proceeds to private sellers or the appropriate government treasury.
- Calculates chunk value using factors including:
  - Base government value
  - Location and distance
  - Biome
  - Nearby claim demand
  - Government and tax conditions
  - Tracked improvements
- Periodically recalculates and persists valuation data.

### Company Finance and Vaults

- Provides company treasury accounts and corporate taxation.
- Adds a Company Vault block and management GUI.
- Supports company fees, company financial administration, and integration with StateCraft company ownership.
- Supports configurable dividends and shareholder payouts.

### Banks and Loans

- Allows eligible companies to operate as banks.
- Supports:
  - Customer deposits and withdrawals
  - Bank treasury and reserve requirements
  - Depositor interest
  - Loan issuance
  - Loan interest accrual
  - Repayment schedules
  - Missed-payment and default handling
  - Configurable balance seizure or chunk repossession
- Provides commands for bank creation, closure, management, deposits, withdrawals, balances, loans, and bank listings.

### Stock Market

- Adds a Stock Market block and GUI.
- Allows shareholders to list company shares for sale.
- Supports full and partial purchases, listing cancellation, seller payment, and share transfer.
- Validates ownership and prevents sellers from purchasing their own listings.
- Persists active stock listings.

## Configuration and Extensibility

- Both mods expose Forge configuration for gameplay limits, fees, election timing, legislative timing, diplomacy, PvP, taxation, companies, banking, loans, marketplace behavior, Trading Hub behavior, and property valuation.
- Item values, villager trades, and shop inventories are data-driven.
- Client/server actions are validated and synchronized through dedicated network packets.
- Government, economy, market, banking, stock, and valuation data persist with the world.

## StateCraft 2.2 UI and Workflow Improvements

- Typed, server-authorized entity lists and detail panels with contextual, prefilled actions.
- A role-aware dashboard for invitations, political decisions, contracts, loans, arrears, deliveries, mail, and recent operations.
- Persistent per-world/player navigation context, retained drafts, stable search/caret behavior, and responsive forms with field-specific constraints.
- Server review cards for mutations, explicit action intent, material-term revalidation, and separate action feedback/read-only section content.
- Durable reviewed-operation IDs, uncertainty-aware status/retry flows, private local recovery references, and audited operator reconciliation without replaying commands.
- A loan-management panel with current autopay and repayment state, and clearer completed-purchase/queued-delivery feedback.
- Keyboard/narration support, translatable UI/presentation metadata, and safe selection at world-coordinate boundaries.

See the UI guide and world-data documentation for controls, bounds, protocol requirements, and recovery semantics.

## Current Limitations

- Some legislature policy types are intentionally **roleplay-only** and are recorded without mechanical enforcement, including custom laws and certain employment or citizenship policies.
- State and city invitation handling is not as complete as nation-level invitations.
- Economy-backed StateCraft features do not operate fully unless both mods are installed.
- Minecraft player inventories and the suite snapshot are saved separately; interrupted cash/item operations may require manual reconciliation.
- Unkeyed server-domain prose remains literal even when framework controls and keyed presentation metadata are localized.
