# Contributing to SoundTweaks

Thank you for taking the time to contribute! Please read these guidelines before opening an issue or pull request.

> [!IMPORTANT]
> This project has a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold it.

---

## Ways to contribute

- **Bug reports** — open an issue describing what happened, what you expected, and your environment (mod version, Fabric Loader version, Minecraft version)
- **Translations** — add or improve a language file in `src/main/resources/assets/soundtweaks/lang/`. See the [Translations](https://github.com/scr0ols/SoundTweaks/wiki/Translations) wiki page for instructions. You can also open a [Discussion](https://github.com/scr0ols/SoundTweaks/discussions) to request a translation for a specific language
- **Code changes** — bug fixes, performance improvements, new features

---

## Branch workflow

| Branch | Purpose |
|---|---|
| `main` | The last released state. Changed only by a `release:` pull request from `dev`. |
| `dev` | Integration branch. It always builds, and every pull request targets it. |
| `feat/*`, `fix/*`, `port/<mc>`, `docs/*`, `chore/*` | Short-lived branches cut from `dev`, named after their purpose. |
| `release/<mc>` | Maintenance branch for an older Minecraft version that still needs a fix. Not created ahead of time. |

1. Branch off `dev`: `git switch -c fix/short-description origin/dev`.
2. Open the pull request against `dev`. Never open one against `main`.
3. Moving to a new Minecraft version is a normal pull request from a `port/<mc>` branch (for example `port/26.4`) into `dev`. The Minecraft version lives in `gradle.properties`, not in a branch name.

### Releases

1. Open a pull request from `dev` to `main` titled `release: vX.Y.Z - <summary>`.
2. After it is merged, tag the **merge commit on `main`** (`vX.Y.Z`) and create the GitHub release from that tag. Release notes come from `CHANGELOG.md`.
3. Past releases are tags, so any old state can be checked out with `git checkout v1.2.2`.

Versions are `MAJOR.MINOR.PATCH+MC`, for example `1.3.0+26.3`. This applies from the next release; earlier releases keep their plain `MAJOR.MINOR.PATCH` versions.

### Older Minecraft versions

If an older Minecraft version needs a fix after newer ones have shipped, cut `release/<mc>` (for example `release/26.2`) from that version's release tag and send the fix there. The maintainer does this only when needed.

---

## Before opening a pull request

> [!WARNING]
> Target the **`dev`** branch. Do **not** target `main` — it only holds released code and changes through release pull requests from `dev`. See [Branch workflow](#branch-workflow).

- One change per PR — keep scope focused
- Test your change locally before submitting (`./gradlew build` on Linux/macOS, `gradlew.bat build` on Windows)
- For significant changes, open an issue or [Discussion](https://github.com/scr0ols/SoundTweaks/discussions) first to discuss the approach before investing time in the implementation

---

## Commit message convention

Use a semantic prefix:

| Prefix | When to use |
|---|---|
| `feat:` | New feature |
| `fix:` | Bug fix |
| `perf:` | Performance improvement |
| `refactor:` | Code change with no behaviour change |
| `i18n:` | Translation additions or updates |
| `docs:` | Documentation only |
| `chore:` | Build, config, or tooling changes |
| `port:` | Changes specific to a loader port (NeoForge, Quilt) |

> [!NOTE]
> Example: `feat: add per-world preset config`

---

## Setting up locally

**Requirements:** Java 25+, Git

```bash
git clone https://github.com/scr0ols/SoundTweaks.git
cd SoundTweaks
git checkout dev
./gradlew build
```

> [!TIP]
> The output jar is in `<loader>/build/libs/`, named `<loader>-soundtweaks-<version>.jar`. Drop it into your `mods/` folder to test. Files ending in `-sources.jar` or `-dev.jar` are not needed for running the mod.

### Project layout

The project is split into modules:

| Module | Contents |
|---|---|
| `common/` | Code that does not depend on a mod loader: configuration, GUI, mixins, lang files and the unit tests. It is compiled into each loader's jar. |
| `fabric/` | Fabric entrypoints, `fabric.mod.json` and the Fabric build. |

Put a change in `common/` unless it needs a loader API. To start the game from the Fabric module, run `./gradlew :fabric:runClient`.

---

## Code style

- Follow the conventions already present in the file you're editing
- No commented-out code, no leftover debug prints
- Keep changes scoped — don't refactor unrelated code in the same PR

---

## Questions

> [!TIP]
> Open a [Discussion](https://github.com/scr0ols/SoundTweaks/discussions) if you're unsure about anything before starting work.
