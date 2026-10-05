# Changelog

All notable changes to SoundTweaks are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Versioning

Versions are `MAJOR.MINOR.PATCH+MC`, where `MC` is the Minecraft version the build targets (for example `1.3.0+26.3`). This applies from the next release. Releases up to 1.2.3 use plain `MAJOR.MINOR.PATCH`.

## [Unreleased]

### Changed

- The project is split into `common` and `fabric` modules, with the shared code in `common`, to prepare for a NeoForge build from the same source. The Fabric jar is now named `fabric-soundtweaks-<version>.jar`.
- Loom is pinned to the 1.17.20 release.

## [1.2.3] - 2026-10-05

Port to Minecraft 26.3 (Fabric). Minecraft 26.3 replaced GLFW with SDL3 and removed the native file dialog library, so input handling, saved shortcuts and the import/export dialogs were migrated.

### Changed

- Bumped to Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Gradle 9.6.0.
- `GLFW` key constants and polling replaced by `InputConstants` (SDL scancodes); key mappings use `Type.KEYBOARD`.
- `TinyFileDialogs` replaced by SDL3 file dialogs. If the system dialog cannot open, the in-game path screen is used instead.

### Fixed

- Esc and Enter were compared against GLFW key codes, which are different keys under SDL3. They now use the SDL scancodes, and numpad Enter also confirms in the rename screen.
- Jump-to-letter in the sound lists follows the letter printed on the key, so it works on AZERTY and other layouts.

### Compatibility

- Saved preset shortcuts are migrated automatically. Earlier versions store GLFW key codes; they are converted once on first launch, and presets files and exports are now marked with `"keyCodes": "sdl"`. Older exports are converted when imported. A shortcut that uses a key with no SDL equivalent is removed and logged.

## [1.2.2] - 2026-08-12

Port to Minecraft 26.2 (Fabric). No mod logic was changed.

### Changed

- Bumped to Minecraft 26.2, Fabric Loader 0.19.3, Loom 1.17-SNAPSHOT, Fabric API 0.156.0+26.2 and Gradle 9.5.1.
- Migrated removed APIs: `Minecraft.screen` to `Minecraft.gui.screen()`, `getOverlay()` to `Minecraft.gui.overlay()`, `setScreen()` to `Minecraft.gui.setScreen()`.

## 1.2.1

Version bump only; no GitHub release was published for this version.

## [1.2.0] - 2026-07-21

### Added

- Sound deduplication: prevents sound pool exhaustion when the same sound is triggered rapidly (for example rain or fire), with a configurable cooldown per sound event. No overhead when deduplication is not triggered.
- Preset ID system: presets use stable integer IDs instead of names. Active and favourite presets are tracked by ID, so renaming a preset no longer resets its state.

### Changed

- Import detects preset ID conflicts and reassigns IDs automatically, with a warning shown to the user.
- Export and import preserve preset order and settings reliably.

[Unreleased]: https://github.com/scr0ols/SoundTweaks/compare/v1.2.3...HEAD
[1.2.3]: https://github.com/scr0ols/SoundTweaks/compare/v1.2.2...v1.2.3
[1.2.2]: https://github.com/scr0ols/SoundTweaks/compare/v1.2.0...v1.2.2
[1.2.0]: https://github.com/scr0ols/SoundTweaks/releases/tag/v1.2.0
