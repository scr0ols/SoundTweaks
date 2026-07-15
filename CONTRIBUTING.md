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

## Before opening a pull request

> [!WARNING]
> Target the **active development branch** for the version you're fixing or improving. Development branches follow the naming pattern `<minecraft-version>` (e.g. `26.1.2`, `26.2`, etc.) — check the repository's branch list to find the right one. Do **not** target `main` — it is the release branch only and pull requests against it will not be merged.

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
git checkout 26.1.2        # or whichever development branch you're targeting
./gradlew build
```

> [!TIP]
> The output jar is in `build/libs/`. Drop it into your `mods/` folder to test. Files ending in `-sources.jar` or `-dev.jar` are not needed for running the mod.

---

## Code style

- Follow the conventions already present in the file you're editing
- No commented-out code, no leftover debug prints
- Keep changes scoped — don't refactor unrelated code in the same PR

---

## Questions

> [!TIP]
> Open a [Discussion](https://github.com/scr0ols/SoundTweaks/discussions) if you're unsure about anything before starting work.
