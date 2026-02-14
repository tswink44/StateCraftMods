# ATM GUI Texture - Creation Guide

## Option 1: AI Image Generation Prompt

Use this prompt with any AI image generator (DALL-E, Midjourney, Stable Diffusion, etc.):

```
Create a Minecraft-style GUI texture, 176x166 pixels.
Gray stone-textured background with darker borders.
At the top: a dark rectangular digital screen display (160x30 pixels) with cyan/teal glow.
Center: one item slot (18x18 pixels) for depositing currency.
Bottom: standard Minecraft inventory (3 rows of 9 slots) plus hotbar (1 row of 9 slots).
All slots are dark gray squares with lighter top/left edges.
Clean, pixel-perfect, GUI texture in Minecraft style.
Professional game UI design.
```

**Alternative shorter prompt:**
```
Minecraft GUI texture, 176x166px, ATM interface with digital screen at top, deposit slot in middle, inventory grid at bottom, gray stone texture, pixel art style
```

## Option 2: Manual Creation (Recommended)

### Using GIMP (Free):

1. **Create New Image**
   - File → New
   - Width: 176 pixels
   - Height: 166 pixels
   - Fill with: Transparency

2. **Background**
   - Fill with gray (#8B8B8B)
   - Add subtle texture or keep solid

3. **Add Borders**
   - Use pencil tool (1px)
   - Light gray (#C6C6C6) on top/left edges
   - Dark gray (#555555) on bottom/right edges

4. **Digital Screen (8, 8, 160x30)**
   - Rectangle: Dark blue-black (#1A1A2E)
   - Border: Cyan (#00C8C8)
   - Add text: "Balance: $0.00" in cyan

5. **Deposit Slot (80, 45, 18x18)**
   - Dark gray square (#373737)
   - Light border on top/left (#C6C6C6)
   - Dark border on bottom/right (#555555)

6. **Inventory Grid (starts at Y: 84)**
   - 3 rows of 9 slots
   - Each slot: 18x18 pixels
   - Spacing: 18 pixels between slots
   - Same style as deposit slot

7. **Hotbar (Y: 142)**
   - 1 row of 9 slots
   - Same style

8. **Export**
   - File → Export As
   - Name: atm.png
   - Format: PNG
   - Save to: `textures/gui/`

### Using Pixilart (Online, Free):

1. Go to https://www.pixilart.com/draw
2. Set canvas: 176x166
3. Follow the same layout as above
4. Use the rectangle and line tools
5. Export as PNG

### Using Paint.NET (Free):

1. New image: 176x166
2. Use Shapes tool (rectangle) with 1px border
3. Fill tool for backgrounds
4. Line tool for borders
5. Save as PNG

## Coordinates Reference

```
Screen Display:
  Position: (8, 8)
  Size: 160 x 30

Deposit Slot:
  Position: (80, 45)
  Size: 18 x 18

Inventory (3x9 grid):
  Start: (8, 84)
  Slot size: 18 x 18
  Spacing: 18 pixels

Hotbar (1x9 grid):
  Start: (8, 142)
  Slot size: 18 x 18
  Spacing: 18 pixels
```

## Color Palette

```css
Background Gray:    #8B8B8B  (139, 139, 139)
Dark Border:        #555555  (85, 85, 85)
Light Border:       #C6C6C6  (198, 198, 198)
Slot Background:    #373737  (55, 55, 55)
Screen Background:  #1A1A2E  (26, 26, 46)
Screen Glow:        #00C8C8  (0, 200, 200)
Screen Text:        #00FFC8  (0, 255, 200)
```

## Quick Template

You can also start with Minecraft's chest GUI and modify it:
1. Extract chest.png from Minecraft assets
2. Modify the top portion to add the digital screen
3. Keep the inventory grid as-is

## Verification

Once created, place at:
```
src/main/resources/assets/statecraft_economy/textures/gui/atm.png
```

Build and run to test!

## Current Status

✓ Models configured
✓ JSON files ready
✓ Code expects texture at correct path
⚠ Need to add atm.png texture file

Once you add the texture, rebuild and the GUI will display properly!

