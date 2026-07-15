# Pull Request

## Summary

<!-- What does this PR do and why? Be concise but complete. -->
<!-- Example: Fixes a crash when opening the sound screen with more than 900 sound events loaded. -->

## Type of change

- [ ] 🐛 Bug fix
- [ ] ✨ New feature
- [ ] 🌐 Translation (new language or correction)
- [ ] 📝 Documentation
- [ ] ♻️ Refactor / code cleanup
- [ ] ⚡ Performance improvement
- [ ] 🔧 Build / config / tooling

---

## What was changed

<!-- Briefly describe the files or areas affected and what you changed in each. -->
<!-- Example:
- `SoundListRenderer.java` — fixed unchecked index causing ArrayIndexOutOfBounds
- `de_de.json` — corrected 3 mistranslated strings
-->

-

---

## How to test

<!-- Step-by-step instructions to verify this works correctly. -->
<!-- Example:
1. Load a world with a large number of mods installed
2. Press K to open the SoundTweaks screen
3. Scroll to the bottom of the list
- Expected: screen opens without crashing, all sounds listed correctly
-->

1.
2.
3.

Expected result:

---

## Screenshots / evidence

<!-- Add screenshots, recordings, or log output if relevant. Delete this section otherwise. -->

---

## Checklist

> [!IMPORTANT]
> All boxes must be checked before this PR will be reviewed.

**Scope**
- [ ] I targeted the correct development branch — not `main`
- [ ] This PR contains a single focused change — no unrelated modifications

**Quality**
- [ ] The mod builds without errors (`./gradlew build` / `gradlew.bat build`)
- [ ] I tested the change in-game and it works as expected
- [ ] No commented-out code or leftover debug prints

**Conventions**
- [ ] My commits follow the [semantic prefix convention](https://github.com/scr0ols/SoundTweaks/blob/main/CONTRIBUTING.md)
- [ ] I have not modified files outside the scope of this PR

**Translations only** *(skip if not applicable)*
- [ ] All keys from `en_us.json` are present — none missing
- [ ] Only values were translated — keys are unchanged
- [ ] The file is valid JSON (no trailing commas, correct encoding)

---

## Related issues

<!-- Example: Closes #42 — remove if there is no related issue -->

Closes #
