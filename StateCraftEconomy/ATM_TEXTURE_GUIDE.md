# ATM Block Texture Guide

## Current Status

✅ **ATM Side Texture**: Already created!
- File: `atm_side.png` (16x16, solid color #6c7382)
- Location: `src/main/resources/assets/statecrafteconomy/textures/block/atm_side.png`
- Used for: All sides except the front

## What You Need to Add

### ATM Front Texture (Required)

**File Name**: `atm_front.png`

**Location**: `src/main/resources/assets/statecrafteconomy/textures/block/atm_front.png`

**Recommended Size**: 16x16 or higher (32x32, 64x64, 128x128)

**Design Suggestions**:

1. **Simple ATM Design**:
   - Screen area (darker rectangle in the upper portion)
   - Keypad (grid of buttons below screen)
   - Card slot (horizontal line)
   - Cash dispenser slot (horizontal line at bottom)

2. **Modern ATM**:
   - Large touchscreen display
   - Card reader with LED indicator
   - Sleek, minimal design
   - Maybe a bank logo or icon

3. **Retro ATM**:
   - Small LCD screen
   - Physical button grid
   - Status LEDs
   - More mechanical looking

## Quick Design Template (ASCII)

Here's what a simple 16x16 ATM front might look like:

```
┌──────────────┐
│ ╔══════════╗ │  <- Screen (dark gray/black)
│ ║          ║ │
│ ╚══════════╝ │
│              │
│  ⊡  ⊡  ⊡  ⊡  │  <- Keypad buttons
│  ⊡  ⊡  ⊡  ⊡  │
│  ⊡  ⊡  ⊡  ⊡  │
│              │
│ ════════════ │  <- Card slot
│ ════════════ │  <- Cash slot
└──────────────┘
```

## Design Elements & Colors

**Recommended Color Palette**:
- Background: #6c7382 (matches sides)
- Screen: #1a1a2e (dark blue-black)
- Buttons: #d4d4d4 (light gray)
- Slots: #2a2a3e (dark gray)
- Highlights: #4a90e2 (blue accents)
- LED indicators: #00ff00 (green) or #ff0000 (red)

## How to Create the Texture

### Method 1: Using Piskel (Online, Free)
1. Go to https://www.piskelapp.com/
2. Create new sprite (16x16 or 32x32)
3. Draw the ATM design
4. Export as PNG
5. Name it `atm_front.png`
6. Place in `textures/block/` folder

### Method 2: Using GIMP/Photoshop
1. Create new image (16x16, 32x32, or 64x64)
2. Fill background with #6c7382
3. Add screen rectangle (dark)
4. Add button grid
5. Add card/cash slots
6. Export as PNG: `atm_front.png`

### Method 3: Using Minecraft Texture Editor
- Nova Skin Editor: https://minecraft.novaskin.me/
- Planet Minecraft Skin Editor
- These are optimized for Minecraft textures!

### Method 4: AI Generation
Prompt example:
"Create a 16x16 pixel art ATM machine front view with a screen, keypad, and card slot, gray background #6c7382"

## File Structure After Adding atm_front.png

```
StateCraftEconomy/
└── src/main/resources/
    └── assets/statecrafteconomy/
        ├── textures/
        │   └── block/
        │       ├── atm_side.png ✅ DONE
        │       └── atm_front.png ⚠️ ADD THIS
        ├── models/
        │   └── block/
        │       └── atm.json ✅ DONE
        ├── blockstates/
        │   └── atm.json ✅ DONE
        └── lang/
            └── en_us.json ✅ DONE
```

## Testing

Once you add `atm_front.png`:

1. **Build**: `./gradlew build`
2. **Run**: `./gradlew runClient`
3. **In-game**:
   - Place the ATM block
   - Walk around it - the front should show your custom texture
   - All other sides should be solid gray (#6c7382)
   - The front should face the direction you're looking when placing

## Block Rotation

The ATM block automatically rotates to face you when placed:
- **North** (0°): Default orientation
- **East** (90°): Rotated right
- **South** (180°): Rotated 180°
- **West** (270°): Rotated left

The front texture (atm_front.png) will always face the player!

## Temporary Solution

If you want to test NOW before creating the custom texture:
- Copy `atm_side.png` to `atm_front.png`
- This will make all sides the same gray color
- Replace with your custom design later

Command to copy:
```powershell
Copy-Item "src/main/resources/assets/statecrafteconomy/textures/block/atm_side.png" "src/main/resources/assets/statecrafteconomy/textures/block/atm_front.png"
```

## Sizes & Resolution

**Minimum**: 16x16 pixels (classic Minecraft)
**Recommended**: 32x32 or 64x64 (HD look)
**Maximum**: 512x512 or higher (for ultra HD packs)

Remember: Higher resolution = more detail but larger file size!

## Need Help?

If you want me to:
1. Generate a simple placeholder texture
2. Create a specific design
3. Help with image editing software

Just let me know what you need!

