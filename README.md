# SoundTweaks

**Granular per-sound and per-block volume control for Minecraft.**  
Fabric mod — Minecraft 26.1.2 · Java 25+

---

## What it does

SoundTweaks lets you control the volume of every individual sound in the game — from the click of a button to the rumble of an iron golem. You can also control volume per block, so a noisy piston farm doesn't have to ruin your experience.

Everything is saved per-world-session and persists across restarts.

### Features

- **Per-sound volume sliders** — adjust any of the 800+ Minecraft sounds individually
- **Per-block volume control** — silence or boost sounds caused by specific blocks
- **Sound groups** — category-level sliders that cascade to all child sounds (Redstone, Ambient, Hostile, etc.)
- **Presets** — create named sound profiles (e.g. "Trading Hall", "AFK Farm") and switch between them instantly
- **Preset shortcuts** — bind up to 3-key combos to toggle presets without opening the UI
- **Favorites sidebar** — pin presets for one-click access
- **Mute toggle** — silence all currently visible sounds at once
- **Import / Export** — share config files or presets between instances
- **Simple / Detail view** — hide technical sound IDs for a cleaner look

### UI

Press **K** (default) to open the sound control screen.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | ≥ 0.19.2 |
| Fabric API | any |
| Java | 25+ |

---

## Config files

All files are saved in `.minecraft/config/`:

| File | Contents |
|---|---|
| `soundtweaks.json` | Per-sound volume overrides |
| `soundtweaks_blocks.json` | Per-block volume overrides |
| `soundtweaks_presets.json` | Presets + active state + favourites |
| `soundtweaks_sounds.txt` | Auto-generated list of all registered sounds (for reference) |

---

## Building

```bash
./gradlew build
```

Output: `build/libs/soundtweaks-1.0.0.jar`

---

## License

[MIT](LICENSE) — free to use, modify and redistribute with credit.

**Author:** scr0ols
