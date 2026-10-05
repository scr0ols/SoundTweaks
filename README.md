<div align="center">
  
# SoundTweaks

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2%2B-62B47A?logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAMAAAAoLQ9TAAAAeFBMVEWcy2yXxmeTwmOSwWKQv2CNvF2KuVp/v1V+vlSDslOBsFF2tkx1tUt0tEpzs0lxsUdwsEa5hVxvr0VtrUNsrEJrq0FqqkBpqT9oqD5npz2Hh4dmpjxkpDpiojhhoTdgoDZfnzVXly2WbEpQkCZsbGx0WER5VTpZPSnN78OwAAAAnElEQVR42jWNCw7CMAxDw/8/CBuMbTBGGPb9b4hbQRtZT3Gfaodd0XXnoVrvrk3dv0vbV8vthrfHMJ0XfdVcbEG71zzxsJpN+HrSOJJHkgNLgoQFwhkQc0QQBkYISEADN1e2Ak+jyhKHlGDOsJS6FCNZhkg2oZMlLbIfdJfUwrKoZpRCjSnzBj8pKfgg1U6ZYYlAheP/ratuocjvvsNMH5BFYTKgAAAAAElFTkSuQmCC&logoColor=white)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.19.2%2B-C8A87A)](https://fabricmc.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.3%20beta-D7722C)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-25%2B-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)
[![Modrinth](https://img.shields.io/modrinth/dt/sound-tweaks?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/mod/sound-tweaks)

**Granular per-sound and per-block volume control for Minecraft.**

</div>

---

## What it does

SoundTweaks lets you control the volume of every individual sound in the game — from the click of a button to the rumble of an iron golem. You can also control volume per block, so a noisy piston farm doesn't have to ruin your experience.

Everything is saved per-world-session and persists across restarts.

> [!NOTE]
> SoundTweaks is a **client-side only** mod. It does not need to be installed on the server.

---

## Features

| Feature | Description |
|---|---|
| **Per-sound sliders** | Adjust any of the 800+ Minecraft sounds individually |
| **Per-block control** | Silence or boost sounds caused by specific blocks |
| **Sound groups** | Category-level sliders that cascade to all child sounds (Redstone, Ambient, Hostile…) |
| **Presets** | Create named sound profiles (e.g. "Trading Hall", "AFK Farm") and switch instantly |
| **Preset shortcuts** | Bind up to 3-key combos to toggle presets without opening the UI |
| **Favorites sidebar** | Pin presets for one-click access |
| **Mute toggle** | Silence all currently visible sounds at once |
| **Import / Export** | Share config files or presets between instances |
| **Simple / Detail view** | Hide technical sound IDs for a cleaner look |

> [!TIP]
> Press **K** to open the sound control screen. The keybind can be changed in **Options → Controls → Key Binds**.

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 26.1.2+ |
| Fabric Loader | ≥ 0.19.2 |
| Fabric API | any |
| NeoForge (26.3 build only) | 26.3.0.51-beta or newer 26.3 build |
| Java | 25+ |

Versions are `MAJOR.MINOR.PATCH+MC`, where `MC` is the Minecraft version the build targets (for example `1.3.0+26.3`). This applies from the next release. See the [changelog](CHANGELOG.md).

---

## Languages

SoundTweaks ships with translations for **19 languages**:

| Flag | Language | Locale |
|:---:|---|:---:|
| ![US](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/us.png) | English (US) | `en_us` |
| ![DE](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/de.png) | German | `de_de` |
| ![ES](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/es.png) | Spanish (Spain) | `es_es` |
| ![MX](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/mx.png) | Spanish (Mexico) | `es_mx` |
| ![FR](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/fr.png) | French | `fr_fr` |
| ![ID](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/id.png) | Indonesian | `id_id` |
| ![IT](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/it.png) | Italian | `it_it` |
| ![JP](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/jp.png) | Japanese | `ja_jp` |
| ![KR](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/kr.png) | Korean | `ko_kr` |
| ![NL](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/nl.png) | Dutch | `nl_nl` |
| ![PL](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/pl.png) | Polish | `pl_pl` |
| ![BR](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/br.png) | Portuguese (Brazil) | `pt_br` |
| ![PT](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/pt.png) | Portuguese (Portugal) | `pt_pt` |
| ![RU](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/ru.png) | Russian | `ru_ru` |
| ![TR](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/tr.png) | Turkish | `tr_tr` |
| ![UA](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/ua.png) | Ukrainian | `uk_ua` |
| ![CN](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/cn.png) | Chinese (Simplified) | `zh_cn` |
| ![TW](https://raw.githubusercontent.com/stevenrskelton/flag-icon/master/png/16/country-4x3/tw.png) | Chinese (Traditional) | `zh_tw` |
| 🏴‍☠️ | Pirate English | `en_pt` |

> [!NOTE]
> Want to add or improve a translation? Contribute via pull request — translation files live in `src/main/resources/assets/soundtweaks/lang/`. You can also open a [Discussion](https://github.com/scr0ols/SoundTweaks/discussions) to request a translation for a specific language.

---

## Config files

All files are saved in `.minecraft/config/`:

| File | Contents |
|---|---|
| `soundtweaks.json` | Per-sound volume overrides |
| `soundtweaks_blocks.json` | Per-block volume overrides |
| `soundtweaks_presets.json` | Presets + active state + favourites |
| `soundtweaks_sounds.txt` | Auto-generated list of all registered sounds (for reference) |

> [!WARNING]
> Config files are stored locally per Minecraft installation. To move your settings to another machine or instance, use the **Import / Export** feature inside the mod.

---

## Building

**Linux / macOS**
```bash
./gradlew build
```

**Windows**
```bat
gradlew.bat build
```

Output: `fabric/build/libs/fabric-soundtweaks-<version>.jar` and `neoforge/build/libs/neoforge-soundtweaks-<version>.jar` (one jar per loader module).

> [!TIP]
> The mod jars are in `fabric/build/libs/` and `neoforge/build/libs/`. Drop the one for your loader into your `mods/` folder to test locally. Files ending in `-sources.jar` or `-dev.jar` are not needed for running the mod.
