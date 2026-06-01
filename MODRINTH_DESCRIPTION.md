# SoundTweaks

> **Take full control of Minecraft's audio — one slider at a time.**

Ever wished you could silence the click of a button without touching the game's master volume? Or kill the sound of a specific block in your farm without affecting everything else?  
SoundTweaks gives you a per-sound and per-block volume mixer, right inside the game.

---

## Features

### 🎚️ Per-sound volume control
Every individual sound in the game — from villager trades to ender dragon screams — gets its own slider. Crank up the sounds you love, silence the ones you don't.

### 🧱 Per-block volume control
Some blocks are just too loud in farms. Pistons, dispensers, observers — control the volume of specific blocks independently from the rest of the game.

### 🗂️ Sound groups
Use category-level sliders (Redstone, Ambient, Hostile, Players, etc.) to adjust entire groups at once. Individual sounds within the group still keep their relative levels.

### 💾 Presets
Create named sound profiles for different situations:
- **Trading Hall** — mute villagers and bells
- **AFK Farm** — silence the block that's driving you crazy
- **Boss Fight** — boost ambient and combat sounds

Activate multiple presets at once. The preset with the biggest deviation from 100% always wins.

### ⌨️ Preset shortcuts
Assign keyboard shortcuts (up to 3-key combos, e.g. `I + J + Y`) to toggle any preset instantly — no need to open the UI.

### ⭐ Favorites sidebar
Pin your most-used presets to a quick-access sidebar. One click to toggle.

### 🔇 Mute all
One-click button to silence all currently visible sounds. Click again to restore.

### 📁 Import / Export
- Import and export your full sound config
- Import presets from other players
- Open the config folder directly from the UI

---

## How to use

1. Press **K** (default keybind) to open the SoundTweaks screen
2. Use the **Category** and **Object** dropdowns to filter the list
3. Drag a slider to adjust volume — **0% = silent**, **100% = default**, values above 100% amplify
4. Changes are saved automatically when you close the screen

To use presets:
1. Press **K**, then click **Presets**
2. Click **New Preset**, give it a name
3. Click **Edit** to assign sounds to that preset
4. Toggle the preset **ON/OFF** from the presets screen or via shortcut

---

## Compatibility

- **Client-side only** — no server installation needed
- **Fabric** only (for now)
- Minecraft **26.1.2**
- Works alongside other audio mods (does not replace the sound engine, only intercepts playback)

---

## FAQ

**Does this work on servers?**  
Yes — it's purely client-side. The server never knows it's installed.

**Do my settings carry over between worlds?**  
The base config applies globally. Presets are global too, but you can activate/deactivate them per session.

**Can I set volume above 100%?**  
Yes. The slider goes up to 200% for amplification.

**Is there a config file I can edit manually?**  
Yes — `soundtweaks.json` and `soundtweaks_blocks.json` in your `.minecraft/config/` folder are plain JSON.

---

*Made with ❤️ by scr0ols — CC0, do whatever you want with it.*
