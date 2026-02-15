# StateCraft Mods

A comprehensive Minecraft 1.20.1 Forge mod suite for building nations, governments, and economies. StateCraft enables players to create nations with hierarchical governance, claim territory, hold democratic elections, pass legislation, and manage economies—all through intuitive GUIs.

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Loader-Forge-orange)

<!-- TODO: Add hero screenshot showing main GUI -->
<!-- ![StateCraft Main Menu](screenshots/main_menu.png) -->

---

## 📦 Mod Components

| Mod | Description |
|-----|-------------|
| **StateCraft** | Core nation building, territory claims, elections, and legislature |
| **StateCraft Economy** | Currency system, banking (ATM), trading, and treasury management |

Both mods work together but StateCraft Economy can function standalone for basic currency features.

---

## 🎮 Quick Start

### Opening the GUI

Press **N** (default keybind) to open the StateCraft main menu at any time.

<!-- TODO: Add screenshot of main menu -->
<!-- ![Main Menu](screenshots/main_menu_overview.png) -->

### First Steps

1. **Press N** to open the StateCraft GUI
2. **Browse nations** and click to join one, or create your own
3. **Press B** to toggle territory border visualization
4. Use the GUI to explore your nation, view territories, and participate in governance

> **Command equivalent:** `/sc gui` opens the main menu

---

## 🏛️ Nation Hierarchy

```
Nation (top level)
├── States (subdivisions)
│   └── Cities (contain chunks)
│       └── Claimed Chunks (16x16 block areas)
```

---

## 🗺️ Managing Your Nation (GUI)

### Viewing Nation Information

1. Press **N** to open the main menu
2. Click on **"Your Nation"** or select a nation from the list
3. View members, territories, treasury balance, and active legislation

<!-- TODO: Add screenshot of nation info screen -->
<!-- ![Nation Info](screenshots/nation_info.png) -->

> **Command equivalent:** `/sc nation info [name]`

### Creating a Nation

1. Press **N** → Click **"Create Nation"**
2. Enter your nation name in the text field
3. Confirm creation (costs $100,000 by default)

> **Command equivalent:** `/sc nation create <name>`

### Managing Members

From the Nation GUI:
- **Invite Players** - Click the invite button and enter a player name
- **View Members** - See all members and their roles
- **Kick Members** - Click on a member → Select "Kick" (Admin+ required)
- **Toggle Open Joining** - Allow anyone to join without invitation

<!-- TODO: Add screenshot of member management -->
<!-- ![Member Management](screenshots/member_list.png) -->

---

## 🏙️ Territory Management (GUI)

### Creating States and Cities

1. Press **N** → Navigate to your nation
2. Click **"Create State"** (costs $50,000) or navigate to a state
3. Within a state, click **"Create City"** (costs $10,000)

<!-- TODO: Add screenshot of territory creation -->
<!-- ![Create City](screenshots/create_city.png) -->

> **Command equivalents:**
> - `/sc state create <name>`
> - `/sc city create <name>`

### Claiming Chunks

1. Press **N** → Click **"Chunk Info"** (or stand in the chunk)
2. View current chunk status and ownership
3. Click **"Claim Chunk"** to claim for your city

Alternatively, use the keybind or command while standing in an unclaimed chunk.

<!-- TODO: Add screenshot of chunk claiming interface -->
<!-- ![Chunk Claim](screenshots/chunk_claim.png) -->

> **Command equivalent:** `/sc chunk claim`

### Visualizing Borders

Press **B** to cycle through border display modes:

| Mode | Description |
|------|-------------|
| **Off** | No borders shown |
| **Chunks** | Show all claimed chunk borders |
| **My Chunks** | Show only your nation's chunks |
| **My Territory** | Show your nation's territory outline |
| **All** | Show all nations' territory outlines |

<!-- TODO: Add screenshot showing border visualization -->
<!-- ![Border Visualization](screenshots/borders.png) -->

> **Command equivalent:** `/sc borders [mode]`

---

## 🗳️ Elections System (GUI)

Nations hold automatic democratic elections for leadership positions.

### Election Timeline

1. **Waiting Period** → Time between elections (default: 7 days)
2. **Registration** → Candidates register with fee ($5,000)
3. **Voting** → All members vote (default: 24 hours)
4. **Results** → Winner becomes nation leader

### Participating via GUI

#### Viewing Elections
1. Press **N** → Select your nation
2. Click **"Elections"** button
3. View current election status, candidates, and timeline

<!-- TODO: Add screenshot of election overview -->
<!-- ![Election Overview](screenshots/election_overview.png) -->

#### Registering as a Candidate
1. Open the Elections GUI
2. Click **"Register as Candidate"**
3. Confirm the registration fee payment

<!-- TODO: Add screenshot of candidate registration -->
<!-- ![Register Candidate](screenshots/register_candidate.png) -->

#### Casting Your Vote
1. Open the Elections GUI during voting period
2. View candidate list with their platforms
3. Click on a candidate to select them
4. Click **"Vote"** to confirm

<!-- TODO: Add screenshot of voting interface -->
<!-- ![Voting Interface](screenshots/vote_screen.png) -->

#### Viewing Election History
1. Open the Elections GUI
2. Click **"View History"** button
3. Browse past election results

---

## 📜 Legislature System (GUI)

The legislature allows members to propose and vote on policies that shape your nation.

### Bill Lifecycle

```
Draft → Debate → Voting → Passed/Failed → Leader Signs/Vetoes → Enacted
```

### Accessing the Legislature

1. Press **N** → Select your nation
2. Click **"Laws"** button
3. Browse active bills, passed laws, and the national codex

<!-- TODO: Add screenshot of legislature main screen -->
<!-- ![Legislature Overview](screenshots/legislature_overview.png) -->

### Proposing a Bill

1. Open the Legislature GUI
2. Click **"Propose Bill"**
3. Enter bill title and description
4. Select policy type and value (if applicable)
5. Submit for debate

<!-- TODO: Add screenshot of bill proposal form -->
<!-- ![Propose Bill](screenshots/propose_bill.png) -->

### Voting on Bills

1. Open the Legislature GUI
2. Click on an active bill in the **"Voting"** phase
3. Read the bill details and discussion
4. Click **YES**, **NO**, or **ABSTAIN**

<!-- TODO: Add screenshot of bill voting interface -->
<!-- ![Vote on Bill](screenshots/bill_voting.png) -->

> **Note:** Only Governors and Officers can vote on bills

### Leader Actions (Sign/Veto)

When a bill passes, the nation leader can:

1. Open the Legislature GUI
2. Find bills in **"Awaiting Signature"** status
3. Click to view details
4. Choose **"Sign into Law"** or **"Veto"**

<!-- TODO: Add screenshot of leader signature screen -->
<!-- ![Leader Signature](screenshots/leader_sign.png) -->

> **Note:** A vetoed bill can be overridden by a 67% supermajority vote

### Policy Types

| Category | Available Policies |
|----------|-------------------|
| **Taxation** | Nation Tax Rate, State Pass-Through, Import Tariff, Base Chunk Value |
| **Territory** | Max States, Max Cities per State, Max Chunks per City |
| **Membership** | Open Nation, Open Borders |
| **Diplomacy** | Declare War, Declare Peace, Form/Break Alliance |
| **Economy** | Minimum Wage, Chunk Claim Fee |
| **Custom** | Roleplay laws with custom text |

---

## 💰 Economy System (GUI)

### Currency Items

Physical currency bills in various denominations:
- $1, $10, $100, $1,000, $10,000, $100,000, $1,000,000

### Using the ATM

The ATM block provides a GUI for all banking operations.

<!-- TODO: Add screenshot of ATM GUI -->
<!-- ![ATM Interface](screenshots/atm_gui.png) -->

#### Placing and Opening
1. Place an ATM block in the world
2. Right-click to open the banking GUI

#### Account Types
Select which account to manage:
- **Personal** - Your player balance
- **Nation Treasury** - Nation funds (Leader only)
- **State Treasury** - State funds (Governor only)
- **City Treasury** - City funds (Mayor only)

#### Operations

| Action | How to Use |
|--------|------------|
| **Check Balance** | View displays automatically when opened |
| **Deposit** | Hold currency items, enter amount, click "Deposit" |
| **Withdraw** | Enter amount, click "Withdraw" to receive currency items |
| **Transfer** | Enter player name and amount, click "Transfer" |

<!-- TODO: Add screenshot of ATM deposit flow -->
<!-- ![ATM Deposit](screenshots/atm_deposit.png) -->

### Trading Hub

Sell items for currency based on configured values.

1. Place a Trading Hub block
2. Right-click to open the interface
3. Insert items into the input slots
4. Click "Sell" to convert to currency

<!-- TODO: Add screenshot of Trading Hub -->
<!-- ![Trading Hub](screenshots/trading_hub.png) -->

---

## 🔒 Protection System

StateCraft automatically protects claimed territory.

| Your Role | Can Build | Can Use Containers | Can Interact |
|-----------|-----------|-------------------|--------------|
| Member+ of owning nation | ✅ | ✅ | ✅ |
| Outsider | ❌ | ❌ | ❌ |
| No nation | ❌ (anywhere) | ❌ | ❌ |

> **Tip:** Server admins can use `/sc admin bypass` to toggle protection bypass

---

## ⌨️ Keybindings

| Key | Action |
|-----|--------|
| **N** | Open StateCraft Menu |
| **B** | Cycle Border Display Modes |

*Customize in Options → Controls → StateCraft*

---

## 📋 Command Reference

While the GUI handles most tasks, commands are available for quick access:

### Essential Commands

| Command | Description |
|---------|-------------|
| `/sc gui` | Open main StateCraft menu |
| `/sc borders` | Cycle border display |
| `/sc nation info` | View your nation info |
| `/sc chunk info` | View current chunk info |

### Nation Management

| Command | Description |
|---------|-------------|
| `/sc nation create <name>` | Create a nation |
| `/sc nation list` | List all nations |
| `/sc nation join <name>` | Join a nation |
| `/sc nation leave` | Leave your nation |
| `/sc nation invite <player>` | Invite a player |

### Territory Commands

| Command | Description |
|---------|-------------|
| `/sc state create <name>` | Create a state |
| `/sc city create <name>` | Create a city |
| `/sc chunk claim` | Claim current chunk |
| `/sc chunk unclaim` | Unclaim current chunk |

### Admin Commands (OP Only)

| Command | Description |
|---------|-------------|
| `/sc admin bypass` | Toggle protection bypass |
| `/sc admin bill enddebate <id>` | End bill debate early |
| `/sc admin bill pass <id>` | Force pass a bill |
| `/sc admin bill fail <id>` | Force fail a bill |
| `/sc admin bill enact <id>` | Force enact a bill |

---

## ⚙️ Configuration

### StateCraft (`config/statecraft.toml`)

```toml
[territory]
maxStatesPerNation = 10
maxCitiesPerState = 10
maxChunksPerCity = 100

[economy]
nationTaxRate = 0.0
chunkClaimFee = 100.0

[creationFees]
nationCreationFee = 100000.0
stateCreationFee = 50000.0
cityCreationFee = 10000.0

[elections]
electionIntervalDays = 7
electionDurationHours = 24
electionCandidateFee = 5000.0

[legislature]
debatePeriodHours = 24
votingPeriodHours = 24
quorumPercent = 50
vetoOverridePercent = 67
```

### Economy (`config/statecraft-economy.toml`)

```toml
[economy]
currencyItems = [
    "statecraft_economy:bill_1=1",
    "statecraft_economy:bill_10=10",
    # ... additional denominations
]
startingBalance = 100.0
```

---

## 💡 Server Admin Tips

1. **Adjust creation fees** to balance your economy
2. **Configure election timing** based on server activity
3. **Set territory limits** to prevent excessive claiming
4. **Use admin bill commands** to resolve stuck legislation
5. **Enable bypass mode** for building spawn/public areas

---

## 🐛 Troubleshooting

**"You cannot interact here"**
- Press **N** and join or create a nation

**"Not enough funds"**
- Find an ATM to check your balance
- Earn currency through trading or other players

**"Cannot claim this chunk"**
- Ensure you're in a city and have Mayor+ permissions
- Check if territory limits have been reached

**GUI not opening?**
- Check keybindings in Options → Controls
- Try `/sc gui` as an alternative

---

*StateCraft Mods v1.0.0 for Minecraft 1.20.1 with Forge*
