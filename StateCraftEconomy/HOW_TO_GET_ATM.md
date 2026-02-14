# How to Get the ATM Block

## Option 1: Creative Menu (UPDATED - Now Available!)
1. Open your inventory in Creative Mode
2. Go to the **Functional Blocks** tab
3. Look for the **ATM** block
4. Add it to your inventory

## Option 2: Command (Works in Survival/Creative)
```
/give @s statecraft_economy:atm
```

Or give to a specific player:
```
/give <playername> statecraft_economy:atm
```

## How to Use the ATM

1. **Place the ATM block** in the world (right-click to place)
2. **Right-click the ATM** to open the GUI
3. **Available Options:**
   - **Check Balance** - See your current money
   - **Deposit (Use Slot)** - Place currency items in the slot and click deposit
   - **Withdraw** - Enter an amount and withdraw cash
   - **Transfer** - Enter a player name and amount to send money

## Testing the Economy System

### 1. Give yourself some starting money:
```
/economy give @s 1000
```

### 2. Check your balance:
```
/economy balance
```

### 3. Get currency items:
```
/give @s statecrafteconomy:bill_1 64
/give @s statecrafteconomy:bill_10 64
/give @s statecrafteconomy:bill_100 64
/give @s statecrafteconomy:bill_1000 64
```

Or find them in **Creative Menu → Tools & Utilities** tab

Available bills: $1, $10, $100, $1,000, $10,000, $100,000, $1,000,000

### 4. Test the ATM:
- Place the ATM block
- Right-click it
- Try depositing bills (place them in the deposit slot)
- Check your balance
- Try withdrawing money (you'll receive bills back!)
- Transfer to another player (needs 2 players online)

## Current Status

✅ ATM block now appears in Creative Menu - Functional Blocks tab
✅ Can also use `/give` command
✅ GUI is functional (missing texture but works)
✅ All transactions working (deposit, withdraw, transfer)

## Note About Missing Texture

The GUI will show a purple/black checkerboard pattern where the texture should be. This is normal - the texture file needs to be created. The GUI is fully functional despite the missing texture!

To fix: Create a 176x166 PNG file at:
`src/main/resources/assets/statecrafteconomy/textures/gui/atm.png`



