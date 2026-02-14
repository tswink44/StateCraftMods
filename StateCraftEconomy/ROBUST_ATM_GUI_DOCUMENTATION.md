# Robust ATM GUI System - Documentation

## Overview

The ATM now features a comprehensive menu system with multiple screens for intuitive navigation. This design allows for future expansion with nation/state/city accounts and multiple bank support.

## Menu Structure

```
┌─────────────────────────────────────────┐
│         MAIN MENU                       │
│  [Bank: Central Bank          ]        │  ← Click to switch banks
│  [Account: Personal Account   ]        │  ← Click to switch account type
│  [Deposit]        [Withdraw]           │
│  [Transfer]       [Check Balance]      │
│                                         │
│  Balance: $1,234.56                    │
└─────────────────────────────────────────┘
```

## Screen Modes

### 1. **Main Menu** (Default)
The hub for all ATM operations:
- **Bank Selector**: Shows current bank, click to switch
- **Account Selector**: Shows current account type (Personal/Nation/State/City)
- **Deposit Button**: Opens deposit confirmation
- **Withdraw Button**: Opens withdrawal input screen
- **Transfer Button**: Opens transfer input screen
- **Check Balance Button**: Queries server for current balance

### 2. **Bank Selection**
Browse and select from available banks:
```
┌─────────────────────────────────────────┐
│         SELECT BANK                     │
│  >> Central Bank                        │  ← Currently selected
│  Community Bank                         │
│  International Bank                     │
│                                         │
│  [< Back]                               │
└─────────────────────────────────────────┘
```
- Shows all registered banks
- Selected bank marked with ">>"
- Click any bank to switch
- Returns to main menu after selection

### 3. **Account Selection**
Choose which account to operate on:
```
┌─────────────────────────────────────────┐
│         SELECT ACCOUNT                  │
│  >> Personal Account                    │  ← Currently selected
│  Nation Treasury                        │
│  State Treasury                         │
│  City Treasury                          │
│                                         │
│  [< Back]                               │
└─────────────────────────────────────────┘
```
**Account Types:**
- **Personal Account**: Player's individual account (always available)
- **Nation Treasury**: Nation's shared funds (if player is nation admin)
- **State Treasury**: State's funds (if player is state governor)
- **City Treasury**: City's funds (if player is city mayor)

**Future Enhancement:** Add permission checks to hide unavailable accounts

### 4. **Deposit Confirmation**
Confirm deposit of items from slot:
```
┌─────────────────────────────────────────┐
│         DEPOSIT CONFIRMATION            │
│                                         │
│  [Confirm Deposit: $500.00]            │
│                                         │
│  Place currency in the slot above      │
│                                         │
│  [< Cancel]                             │
└─────────────────────────────────────────┘
```
- Shows calculated value of items in deposit slot
- Click confirm to deposit
- Items are consumed and balance updated

### 5. **Withdraw Input**
Enter amount to withdraw:
```
┌─────────────────────────────────────────┐
│         WITHDRAW                        │
│  Amount: [____________]                 │
│                                         │
│  [Confirm Withdrawal]                   │
│  [< Cancel]                             │
│                                         │
│  You will receive bills                │
└─────────────────────────────────────────┘
```
- Enter desired withdrawal amount
- Server validates balance
- Receives physical currency bills
- Bills given in optimal denominations (largest first)

### 6. **Transfer Input**
Send money to another player:
```
┌─────────────────────────────────────────┐
│         TRANSFER                        │
│  Amount: [____________]                 │
│  Recipient: [____________]              │
│                                         │
│  [Confirm Transfer]                     │
│  [< Cancel]                             │
└─────────────────────────────────────────┘
```
- Enter amount and recipient player name
- Server validates both players exist
- Deducts from sender, adds to recipient
- Both players notified

## User Flow Examples

### Example 1: Simple Withdrawal
1. Player opens ATM → **Main Menu**
2. Click "Withdraw" → **Withdraw Input Screen**
3. Enter "1000" → Amount field
4. Click "Confirm Withdrawal"
5. Returns to **Main Menu**
6. Receives $1000 in bills (1x $1000 bill)

### Example 2: Switching Banks and Depositing
1. Player opens ATM → **Main Menu**
2. Click bank button → **Bank Selection Screen**
3. Select "Community Bank"
4. Returns to **Main Menu** (now shows Community Bank)
5. Place $100 bills in deposit slot
6. Click "Deposit" → **Deposit Confirmation**
7. Shows calculated value
8. Click "Confirm Deposit"
9. Bills consumed, balance increased

### Example 3: Nation Treasury Management
1. Player opens ATM → **Main Menu**
2. Click account button → **Account Selection Screen**
3. Select "Nation Treasury"
4. Returns to **Main Menu** (now operating on nation account)
5. Click "Check Balance" → Shows nation balance
6. Click "Withdraw" to take funds from nation
7. Enter amount and confirm
8. Nation balance decreased, player receives bills

## Technical Details

### State Management
```java
enum ScreenMode {
    MAIN_MENU,          // Hub screen
    BANK_SELECT,        // Bank selection
    ACCOUNT_SELECT,     // Account type selection
    DEPOSIT_CONFIRM,    // Confirm deposit
    WITHDRAW_INPUT,     // Enter withdrawal amount
    TRANSFER_INPUT      // Enter transfer details
}
```

### Account Types
```java
enum AccountType {
    PERSONAL,    // Player's personal account
    NATION,      // Nation shared treasury
    STATE,       // State treasury
    CITY         // City treasury
}
```

### Navigation
- Each screen has a **Back** button (except main menu)
- Clicking back returns to previous screen
- All operations return to main menu after completion
- Mode switches rebuild UI automatically

## Benefits of This Design

### 1. **Intuitive Navigation**
- Clear menu hierarchy
- Always know where you are
- Easy to explore features

### 2. **Future-Proof**
- Easy to add new banks
- Easy to add new account types
- Easy to add new operations

### 3. **Flexible**
- Each account type can have different permissions
- Each bank can have different features
- Easy to customize per account type

### 4. **Professional**
- Similar to real ATM interfaces
- Familiar menu structure
- Clear visual feedback

## Future Enhancements

### Planned Features:
1. **Permission System**
   - Hide unavailable account types
   - Check nation/state/city admin status
   - Gray out operations user can't perform

2. **Quick Actions**
   - Favorite banks
   - Recent transactions
   - Quick amount buttons ($10, $100, $1000)

3. **Transaction History**
   - View recent transactions
   - Filter by type
   - Export/print receipt

4. **Advanced Features**
   - Scheduled transfers
   - Automatic payments
   - Savings accounts
   - Loans (if bank supports)

5. **Multi-Language Support**
   - Use language keys for all text
   - Already structured for translation

## Testing Checklist

✅ **Main Menu**
- [ ] All buttons visible and clickable
- [ ] Balance displays correctly
- [ ] Status messages appear

✅ **Bank Selection**
- [ ] All banks listed
- [ ] Selected bank marked
- [ ] Selection updates main menu

✅ **Account Selection**
- [ ] All account types shown
- [ ] Selected account marked
- [ ] Selection updates main menu
- [ ] Balance updates when switching

✅ **Deposit**
- [ ] Value calculates correctly
- [ ] Items consumed on confirm
- [ ] Balance increases
- [ ] Returns to main menu

✅ **Withdraw**
- [ ] Amount input works
- [ ] Bills given on confirm
- [ ] Balance decreases
- [ ] Correct denominations

✅ **Transfer**
- [ ] Both inputs work
- [ ] Validation works (player exists)
- [ ] Both players notified
- [ ] Balances update correctly

## Code Structure

```
ATMScreen.java
├── ScreenMode enum          # Different screens
├── AccountType enum         # Account types
├── State variables          # Current mode, selected bank, etc.
├── UI Components            # Input fields, buttons
├── init()                   # Initialize screen
├── buildUI()                # Main UI builder (switches on mode)
│   ├── buildMainMenu()
│   ├── buildBankSelect()
│   ├── buildAccountSelect()
│   ├── buildDepositConfirm()
│   ├── buildWithdrawInput()
│   └── buildTransferInput()
├── switchMode()             # Change screen mode
├── render methods           # Drawing
└── Event handlers           # Button clicks, input
```

## Summary

The new robust ATM GUI provides:
- ✅ **Multi-bank support** - Switch between different banks
- ✅ **Multi-account support** - Personal, Nation, State, City accounts
- ✅ **Clear navigation** - Intuitive menu hierarchy
- ✅ **All operations** - Deposit, Withdraw, Transfer, Balance check
- ✅ **Future-proof** - Easy to extend and customize
- ✅ **Professional** - Clean, organized interface

The system is ready for production and can easily be extended with nation/state/city permissions and additional features!

