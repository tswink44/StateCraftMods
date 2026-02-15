# StateCraft Mods

A comprehensive Minecraft 1.20.1 Forge mod suite for building nations, governments, and economies. StateCraft enables players to create nations with hierarchical governance, claim territory, hold elections, pass legislation, and manage economies.

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Loader-Forge-orange)

---

## 📦 Mod Components

This project contains two interconnected mods:

| Mod | Description |
|-----|-------------|
| **StateCraft** | Core nation building, territory claims, elections, and legislature |
| **StateCraft Economy** | Currency system, banking (ATM), trading, and treasury management |

Both mods work together but StateCraft Economy can function standalone for basic currency features.

---

## 🏛️ Features Overview

### Nation Hierarchy
```
Nation (top level)
├── States (subdivisions)
│   └── Cities (contain chunks)
│       └── Claimed Chunks (16x16 block areas)
```

### Core Features

- **🗺️ Territory System** - Claim chunks to protect your builds
- **🏛️ Democratic Elections** - Vote for nation leadership
- **📜 Legislature** - Propose and vote on policies and laws
- **💰 Economy** - Physical currency, banking, trading
- **🔒 Chunk Protection** - Automatic grief protection
- **🗺️ Visual Borders** - See claimed territory boundaries

---

## 🚀 Getting Started

### For Players New to a Server

1. **Join or create a nation** - You need to be in a nation to interact with the world
   ```
   /sc nation list          # See available nations
   /sc nation join <name>   # Join an open nation
   /sc nation create <name> # Create your own nation
   ```

2. **Open the GUI menu** - Press **N** (default) or use `/sc gui`

3. **View borders** - Press **B** (default) to cycle through border display modes

### Creating Your Own Nation

1. **Gather funds** - You'll need $100,000 (default) to create a nation
2. **Create the nation** - `/sc nation create <name>`
3. **Create a state** - `/sc state create <name>` ($50,000 default)
4. **Create a city** - `/sc city create <name>` ($10,000 default)
5. **Claim territory** - Stand in an unclaimed chunk and use `/sc chunk claim`

---

## 📋 Command Reference

> **Tip:** All commands can use `/statecraft` or the shorthand `/sc`

### Nation Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sc nation create <name>` | Create a new nation | Anyone (costs $100,000) |
| `/sc nation info [name]` | View nation information | Anyone |
| `/sc nation list` | List all nations | Anyone |
| `/sc nation invite <player>` | Invite a player | Admin+ |
| `/sc nation join <nation>` | Join an open nation | Anyone |
| `/sc nation leave` | Leave your nation | Anyone |
| `/sc nation kick <player>` | Remove a member | Admin+ |
| `/sc nation disband` | Delete the nation | Leader only |
| `/sc nation setopen <true/false>` | Toggle open joining | Admin+ |

### State Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sc state create <name>` | Create a state | Nation Admin+ |
| `/sc state list` | List states in your nation | Anyone |
| `/sc state info <name>` | View state information | Anyone |

### City Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sc city create <name>` | Create a city in your state | State Governor+ |
| `/sc city list` | List cities in your state | Anyone |
| `/sc city info <name>` | View city information | Anyone |

### Chunk Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/sc chunk claim` | Claim current chunk | City Mayor+ |
| `/sc chunk unclaim` | Unclaim current chunk | City Mayor+ |
| `/sc chunk info` | View chunk information | Anyone |

### GUI Commands

| Command | Description |
|---------|-------------|
| `/sc gui` | Open main StateCraft menu |
| `/sc gui nation` | Open nation info screen |
| `/sc gui state` | Open state info screen |
| `/sc gui city` | Open city info screen |
| `/sc gui chunk` | Open chunk info screen |

### Border Commands

| Command | Description |
|---------|-------------|
| `/sc borders` | Cycle border display modes |
| `/sc borders off` | Turn off border rendering |
| `/sc borders chunks` | Show all chunk borders |
| `/sc borders mychunks` | Show only your nation's chunks |
| `/sc borders myterritory` | Show your nation's territory outline |
| `/sc borders all` | Show all nations' territories |

### Admin Commands (OP Required)

| Command | Description |
|---------|-------------|
| `/sc admin bypass` | Toggle chunk protection bypass |
| `/sc admin unclaim` | Force unclaim any chunk |
| `/sc admin delete <nation>` | Force delete a nation |

---

## 🗳️ Elections System

Nations hold automatic elections for leadership. Configure timing in the server config.

### Election Flow

1. **Waiting Period** - Time between elections (default: 7 days)
2. **Registration** - Candidates register and pay fee ($5,000 default)
3. **Voting Period** - Nation members vote (default: 24 hours)
4. **Results** - Winner becomes the new nation leader

### Participating in Elections

**As a Voter:**
- Open the nation GUI (press **N** → click "Vote")
- Click on a candidate to cast your vote
- View election history through the GUI

**As a Candidate:**
- Have sufficient funds (registration fee)
- Click "Register as Candidate" in the election GUI
- Campaign to your fellow nation members!

---

## 📜 Legislature System

The legislature allows nation members to propose and vote on policies that change how the nation operates.

### Bill Lifecycle

1. **Draft** - A governor or officer proposes a bill
2. **Debate** - Discussion period (default: 24 hours)
3. **Voting** - Governors and officers vote (default: 24 hours)
4. **Passed/Failed** - Requires majority and quorum
5. **Leader Action** - Leader signs or vetoes
6. **Enacted/Vetoed** - Bill becomes law or is rejected

### Opening the Legislature GUI

1. Press **N** to open the StateCraft menu
2. Click on your nation
3. Click "Laws" button
4. View active bills, vote, or propose new legislation

### Voting on Bills

- Voting members (governors and officers) can vote YES, NO, or ABSTAIN
- Click on an active bill to see details
- Use the voting buttons to cast your vote

### Policy Types

Bills can modify various nation policies:

| Category | Policies |
|----------|----------|
| **Taxation** | Nation Tax Rate, State Pass-Through Rate, Import Tariff, Base Chunk Value |
| **Territory** | Max States, Max Cities per State, Max Chunks per City, Open Borders |
| **Membership** | Open Nation, Citizenship Requirements |
| **Diplomacy** | Declare War, Declare Peace, Form/Break Alliance |
| **Economy** | Minimum Wage, Chunk Claim Fee |
| **Custom** | Custom roleplay laws |

### Leader Powers

The nation leader can:
- **Sign** - Approve a passed bill, making it law
- **Veto** - Reject a passed bill (can be overridden by 67% vote)

---

## 💰 Economy System

StateCraft Economy provides a physical currency system with banking.

### Currency

The mod includes paper currency bills:
- $1, $10, $100, $1,000, $10,000, $100,000, $1,000,000

Currency items can be configured in the config file to include any Minecraft items.

### ATM Block

The ATM block allows players to:
- **Check Balance** - View personal or treasury balances
- **Deposit** - Insert currency items to add to your balance
- **Withdraw** - Convert balance to physical currency items
- **Transfer** - Send money to other players

**Obtaining an ATM:**
```
/give @p statecraft_economy:atm
```

**Using the ATM:**
1. Place the ATM block
2. Right-click to open the GUI
3. Select account type (Personal, Nation, State, or City treasury)
4. Enter amount and click the appropriate action

### Trading Hub

The Trading Hub allows players to sell items for currency based on configured values.

**Obtaining a Trading Hub:**
```
/give @p statecraft_economy:trading_hub
```

### Treasuries

Each level of government can have its own treasury:
- **Nation Treasury** - Accessible by nation leader
- **State Treasury** - Accessible by state governors
- **City Treasury** - Accessible by city mayors

Access treasury accounts through the ATM by selecting the appropriate account type.

---

## 🔒 Protection System

StateCraft automatically protects claimed territory:

### Protection Rules

| Action | Wilderness | Your Claims | Other Claims |
|--------|------------|-------------|--------------|
| Build/Break | ❌ (need nation) | ✅ Based on role | ❌ No access |
| Interact | ❌ (need nation) | ✅ Based on role | ❌ No access |
| Containers | ❌ (need nation) | ✅ Based on role | ❌ No access |

### Permission Levels

| Level | Description |
|-------|-------------|
| **Leader** | Nation founder - full access |
| **Admin** | Nation administrators |
| **Governor** | State leaders |
| **Mayor** | City leaders |
| **Officer** | Appointed officials |
| **Member** | Regular nation members |
| **Outsider** | Non-members |

---

## ⌨️ Default Keybindings

| Key | Action |
|-----|--------|
| **N** | Open StateCraft Menu |
| **B** | Cycle Border Display Modes |

Keybindings can be customized in Minecraft's Controls menu.

---

## ⚙️ Configuration

### StateCraft Config (`statecraft.toml`)

```toml
[territory]
maxStatesPerNation = 10
maxCitiesPerState = 10
maxChunksPerCity = 100

[economy]
enableNationTreasury = true
nationTaxRate = 0.0
chunkClaimFeeEnabled = true
chunkClaimFee = 100.0

[creationFees]
nationCreationFee = 100000.0
stateCreationFee = 50000.0
cityCreationFee = 10000.0

[elections]
enableNationElections = true
electionIntervalDays = 7
electionDurationHours = 24
electionCandidateFee = 5000.0

[legislature]
enableLegislature = true
debatePeriodHours = 24
votingPeriodHours = 24
quorumPercent = 50
vetoOverridePercent = 67
```

### Economy Config (`statecraft-economy.toml`)

```toml
[economy]
# Custom currency items (format: "modid:item=value")
currencyItems = [
    "statecraft_economy:bill_1=1",
    "statecraft_economy:bill_10=10",
    "statecraft_economy:bill_100=100",
    "statecraft_economy:bill_1000=1000",
    "statecraft_economy:bill_10000=10000",
    "statecraft_economy:bill_100000=100000",
    "statecraft_economy:bill_1000000=1000000"
]
startingBalance = 100.0

[atm]
requiresPower = false
range = 5
```

---

## 💡 Tips for Server Admins

1. **Adjust creation fees** to match your server's economy
2. **Set election intervals** appropriate for your player activity
3. **Configure max territories** to prevent excessive land claiming
4. **Add custom currency items** like gold ingots for familiarity
5. **Use bypass mode** (`/sc admin bypass`) for building spawn areas

---

## 🐛 Troubleshooting

**"You cannot interact here"**
- Join a nation using `/sc nation list` and `/sc nation join <name>`
- Or create your own nation with `/sc nation create <name>`

**"Not enough funds"**
- Use an ATM to check your balance
- Obtain currency through trading or from other players
- Admins can give currency: `/give @p statecraft_economy:bill_1000 10`

**"Cannot claim this chunk"**
- Make sure you're in a city (create one if needed)
- Check chunk limits haven't been reached
- Ensure the chunk isn't already claimed

---

## 📄 License

This project is provided for educational and entertainment purposes.

---

*StateCraft Mods v1.0.0 for Minecraft 1.20.1 with Forge*

