# StateCraft Command Reference

A comprehensive guide to all commands in the StateCraft mod for Minecraft 1.20.1.

> **Aliases:** All commands can use `/statecraft` or `/sc`

---

## 📍 Quick Reference

| Category | Command | Description |
|----------|---------|-------------|
| Nation | `/sc nation create <name>` | Create a new nation |
| Nation | `/sc nation info` | View your nation's info |
| Nation | `/sc nation list` | List all nations |
| Nation | `/sc nation invite <player>` | Invite a player |
| Nation | `/sc nation join <nation>` | Join an open nation |
| Nation | `/sc nation leave` | Leave your nation |
| State | `/sc state create <name>` | Create a state |
| City | `/sc city create <name>` | Create a city |
| Chunk | `/sc chunk claim` | Claim current chunk |
| Chunk | `/sc chunk unclaim` | Unclaim current chunk |
| GUI | `/sc gui` | Open the StateCraft menu |
| GUI | `/sc gui nation` | Open nation GUI for current chunk |
| GUI | `/sc gui state` | Open state GUI for current chunk |
| GUI | `/sc gui city` | Open city GUI for current chunk |
| GUI | `/sc gui chunk` | Open chunk info for current location |
| Borders | `/sc borders` | Cycle border display modes |
| Admin | `/sc admin bypass` | Toggle protection bypass (OP) |

---

## 🏛️ Nation Commands

### Creating & Managing Nations

```
/sc nation create <name>
```
Create a new nation. You become the leader.
- Name must be 3-24 characters
- You cannot be in another nation

```
/sc nation info [name]
```
View information about a nation.
- Without name: shows your nation
- With name: shows specified nation

```
/sc nation list
```
List all nations on the server with member counts.

```
/sc nation disband
```
**[Leader only]** Permanently delete your nation.
- Removes all claims
- Removes all members

```
/sc nation setopen <true|false>
```
**[Admin+]** Toggle whether players can join without invitation.

---

### Membership & Invitations

```
/sc nation invite <player>
```
**[Admin+]** Invite a player to your nation.
- Invitation expires in 5 minutes

```
/sc nation accept [nation]
```
Accept a pending invitation.
- Without nation: accepts most recent
- With nation: accepts specific invite

```
/sc nation deny [nation]
```
Decline a pending invitation.

```
/sc nation invites
```
List all your pending invitations.

```
/sc nation join <nation>
```
Join an open nation (no invitation needed).

```
/sc nation leave
```
Leave your current nation.
- Leaders cannot leave (must disband or transfer)

```
/sc nation kick <player>
```
**[Admin+]** Remove a player from the nation.

---

## 🗺️ State Commands

States are subdivisions within a nation.

```
/sc state create <name>
```
**[Nation Admin+]** Create a new state in your nation.

```
/sc state list
```
List all states in your nation.

```
/sc state info <name>
```
View information about a state.

---

## 🏘️ City Commands

Cities belong to states and contain claimed chunks.

```
/sc city create <name>
```
**[State Governor+]** Create a new city in your state.

```
/sc city list
```
List all cities in your state.

```
/sc city info <name>
```
View information about a city.

---

## 📦 Chunk Commands

Chunks are 16x16 block areas that can be claimed.

```
/sc chunk claim
```
**[City Mayor+]** Claim the chunk you're standing in.
- Chunk must be unclaimed (wilderness)
- Must be adjacent to existing claims or first claim

```
/sc chunk unclaim
```
**[City Mayor+]** Unclaim the chunk you're standing in.

```
/sc chunk info
```
View information about the current chunk.
- Shows owner, city, permissions

---

## ℹ️ Info Commands

```
/sc info here
```
Show information about your current location.
- Chunk coordinates
- Claim status
- Nation/State/City if claimed

```
/sc info player <name>
```
Show information about a player.
- Their nation membership
- Their role/rank

---

## 🖥️ GUI Commands

```
/sc gui
```
Open the StateCraft main menu.
- Alternative: Press **N** key (default)

```
/sc gui nation
```
Open the nation info screen for the chunk you're standing in.
- If in wilderness: shows your nation (if you have one)
- If in claimed chunk: shows that chunk's nation

```
/sc gui state
```
Open the state info screen for the chunk you're standing in.
- Only works in claimed chunks

```
/sc gui city
```
Open the city info screen for the chunk you're standing in.
- Only works in claimed chunks

```
/sc gui chunk
```
Open chunk info for where you're standing.
- If claimed: shows city info
- If wilderness: opens claims management

---

## 🗺️ Border Commands

```
/sc borders
```
Cycle through border display modes.
- Alternative: Press **B** key (default)

```
/sc borders off
```
Turn off border rendering.

```
/sc borders chunks
```
Show individual chunk borders for all nations.

```
/sc borders mychunks
```
Show individual chunk borders for your nation only.

```
/sc borders myterritory
```
Show aggregate territory border for your nation.

```
/sc borders all
```
Show aggregate territory borders for all nations.

---

## ⚙️ Admin Commands

> **Requires:** Operator permission level 2+

```
/sc admin bypass
```
Toggle protection bypass mode.
- When enabled: can interact in ALL chunks
- When disabled: normal protection rules apply

```
/sc admin unclaim
```
Force unclaim the chunk you're standing in.
- Works on any nation's claims

```
/sc admin unclaim <x> <y> <z>
```
Force unclaim chunk at specific coordinates.

```
/sc admin delete <nation>
```
Force delete an entire nation.
- Removes all claims, states, cities, members

```
/sc admin info
```
Show detailed admin info about current chunk.
- UUIDs, ownership details, bypass status

```
/sc admin setowner <player>
```
Set a player as the owner of the current chunk.

```
/sc admin reload
```
Reload configuration (future feature).

---

## ⌨️ Keybindings

| Key | Action | Configurable |
|-----|--------|--------------|
| **N** | Open StateCraft Menu | Yes (Controls) |
| **B** | Cycle Border Modes | Yes (Controls) |

---

## 🔐 Permission Levels

| Level | Description | Typical Permissions |
|-------|-------------|---------------------|
| **Owner** | Nation leader | Everything |
| **Admin** | Nation administrators | Manage members, claims, settings |
| **Member** | Regular nation member | Build, break, use containers |
| **Ally** | Allied nation members | Interact, use containers |
| **Outsider** | No affiliation | Nothing (protected) |

---

## 🌍 Protection Rules

| Location | Not in Nation | In Nation (Own) | In Nation (Other) |
|----------|---------------|-----------------|-------------------|
| **Wilderness** | ❌ Cannot interact | ✅ Can interact | ✅ Can interact |
| **Your Claims** | N/A | ✅ Based on role | ❌ No access |
| **Other Claims** | ❌ No access | ❌ Based on role | ❌ Based on role |

**Note:** Players must join or create a nation to interact with the world!

---

## 💡 Tips

1. **New to the server?** Use `/sc nation list` to find nations to join
2. **Want your own nation?** Use `/sc nation create <name>`
3. **Can't build?** Make sure you're in a nation with `/sc nation info`
4. **See claim borders** Press **B** to toggle visual borders
5. **Quick menu access** Press **N** to open the GUI

---

*StateCraft Mod v1.0.0 for Minecraft 1.20.1*

