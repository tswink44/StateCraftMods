# StateCraft Economy - Multi-Bank System Implementation

## Overview
The economy mod has been updated to support multiple banks with a flexible framework. Each ATM can be associated with a specific bank, and the system allows for future expansion with custom banks.

## New Features

### 1. Multi-Bank System
- **Bank Class** (`Bank.java`): Represents individual banks with properties like:
  - Display name and internal name
  - Interest rates
  - Withdrawal and transfer fees
  - Custom color for UI
  - Loan support (future feature)

- **BankRegistry** (`BankRegistry.java`): Manages all banks in the system
  - Maintains a default "Central Bank"
  - Allows registration of custom banks
  - Persists bank data to disk

### 2. Enhanced Bank Accounts
- **BankAccount** now includes:
  - `bankId` field to track which bank the account belongs to
  - All accounts default to the Central Bank initially
  - Full NBT serialization support for bank associations

### 3. ATM GUI System
- **ATMScreen** (`client/screen/ATMScreen.java`): Full-featured client-side GUI with:
  - **Check Balance**: View current account balance
  - **Deposit**: Place currency items in the slot to deposit
  - **Withdraw**: Enter amount and withdraw cash
  - **Transfer**: Send money to other players by username
  - Real-time balance updates
  - Status messages for transaction feedback
  - Bank branding (displays bank name in title)

### 4. ATM Block Enhancements
- **ATMBlockEntity** now stores:
  - Bank ID association (which bank the ATM belongs to)
  - NBT persistence for bank data
  - Dynamic title display based on bank

### 5. Network Communication
- **ATMTransactionPacket**: New packet handling all ATM operations
  - CHECK_BALANCE: Request current balance
  - DEPOSIT: Process currency item deposits
  - WITHDRAW: Withdraw funds to player account
  - TRANSFER: Transfer funds to another player

## File Structure

```
StateCraftEconomy/
├── src/main/java/com/statecraft/economy/
│   ├── core/
│   │   ├── Bank.java                    [NEW]
│   │   ├── BankRegistry.java            [NEW]
│   │   ├── BankAccount.java             [MODIFIED]
│   │   └── EconomyManager.java          [MODIFIED]
│   ├── client/
│   │   ├── ClientSetup.java             [NEW]
│   │   └── screen/
│   │       └── ATMScreen.java           [NEW]
│   ├── block/
│   │   ├── ATMBlock.java                [MODIFIED]
│   │   └── entity/
│   │       └── ATMBlockEntity.java      [MODIFIED]
│   ├── gui/
│   │   ├── ATMMenu.java                 [MODIFIED]
│   │   └── ModMenuTypes.java            [MODIFIED]
│   ├── network/
│   │   ├── NetworkHandler.java          [MODIFIED]
│   │   ├── ClientPacketHandler.java     [MODIFIED]
│   │   └── packets/
│   │       └── ATMTransactionPacket.java [NEW]
│   └── data/
│       └── EconomySavedData.java        [MODIFIED]
└── src/main/resources/
    └── assets/statecrafteconomy/
        └── textures/gui/
            ├── atm.png                  [NEEDED - Create this!]
            └── README_ATM_TEXTURE.txt   [Guide]
```

## How It Works

### Player Interactions
1. **Player right-clicks ATM block**
   → Opens ATM GUI showing bank name and interface

2. **Check Balance**
   → Sends packet to server
   → Server responds with current balance
   → GUI updates to show balance

3. **Deposit Money**
   → Player places currency items in the deposit slot
   → Clicks "Deposit (Use Slot)" button
   → Server calculates value and adds to player account
   → Items are consumed from slot
   → Balance updates in GUI

4. **Withdraw Money**
   → Player enters amount
   → Clicks "Withdraw" button
   → Server checks balance
   → If sufficient, subtracts from account
   → Player notified of success/failure
   → Balance updates in GUI

5. **Transfer Money**
   → Player enters recipient username and amount
   → Clicks "Transfer" button
   → Server validates recipient exists
   → Checks sender has sufficient funds (including fees)
   → Performs transfer
   → Both players notified
   → Balance updates in GUI

### Data Persistence
- Banks are saved to world data via `EconomySavedData`
- Each account stores its associated bank ID
- Bank registry is loaded on server start
- Default "Central Bank" is always available

## Future Expansion

### Adding New Banks
```java
// In a future bank registration system:
Bank customBank = new Bank(
    UUID.randomUUID(),
    "mybank",
    "My Custom Bank"
);
customBank.setInterestRate(0.05); // 5% annual interest
customBank.setWithdrawalFee(1.0); // $1 per withdrawal
customBank.setColor(0xFF5722); // Orange color
EconomyManager.getInstance().getBankRegistry().registerBank(customBank);
```

### Setting ATM Bank Association
```java
// ATMs can be associated with specific banks:
atmBlockEntity.setBankId(customBank.getId());
```

### Planned Features
- [ ] Interest calculation and payouts
- [ ] Loan system
- [ ] Bank-specific benefits/perks
- [ ] Nation/State/City bank accounts
- [ ] Cross-bank transfers with fees
- [ ] Bank UI themes/branding

## TODO Before Testing

1. **Create ATM Texture**
   - Create a 176x166 pixel PNG image
   - Save as: `src/main/resources/assets/statecrafteconomy/textures/gui/atm.png`
   - You can start with Minecraft's chest GUI as a template

2. **Test In-Game**
   - Build the mod: `./gradlew build`
   - Place ATM block
   - Interact with ATM
   - Test deposit/withdraw/transfer
   - Verify balance updates correctly

3. **Known Issues to Check**
   - Ensure texture loads correctly (or you'll see missing texture)
   - Test with multiple players for transfers
   - Verify NBT data saves/loads correctly
   - Check for any race conditions in balance updates

## Configuration
Banks can be configured via the BankRegistry, but currently, there's one default bank:
- **Name**: Central Bank
- **ID**: 00000000-0000-0000-0000-000000000001
- **Interest Rate**: 2% annual
- **Fees**: None
- **Color**: Blue (#2196F3)

## Integration with StateCraft
The existing nation treasury integration will automatically use the Central Bank by default. Future updates can allow nations to have accounts at different banks or even own their own banks!

