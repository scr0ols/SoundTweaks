# SoundTweaks — Plano de Reestruturação Git

## Situação actual

```
main     ──● feat: initial release of SoundTweaks v1.0.0  (7d16f45)
             ↑
26.1.2   ──●  (mesmo commit)
```

Tudo foi commitado de uma vez. O objectivo é ter:
- **`main`** — branch estável, só recebe PRs de release
- **`26.1.2`** — branch de desenvolvimento para esta versão do Minecraft, com histórico de commits lógico
- **`feat/*`** — branches de features futuras, partem de `26.1.2`

---

## Estrutura alvo

```
main        ──●──────────────────────────────●  (releases via PR)
               \                            /
26.1.2          ●──●──●──●──●──●──●──●──●──  (desenvolvimento contínuo)
                                    \
feat/fix-soundengine                 ●──●──  (fix activo agora)
```

---

## Fase 1 — Preparar o repositório local

> Executar na pasta do projecto: `C:\Users\Lenovo\Claude_Environment\minecraft\desenvolvimento\mods\SoundTweaks\`

### 1.1 Verificar estado actual

```powershell
git log --oneline --all
git branch -a
git status
```

### 1.2 Garantir que `main` está limpo (o release actual)

`main` com o commit único já é o estado correcto — é o release v1.0.0. Não mexer.

Adicionar tag de release:

```powershell
git tag -a v1.0.0 -m "Initial release — SoundTweaks v1.0.0 for Minecraft 26.1.2"
```

### 1.3 Criar histórico lógico em `26.1.2`

Como tudo está num só commit, vamos **reescrever o histórico de `26.1.2`** para contar a história do desenvolvimento de forma organizada.

> ⚠️ Isto usa `git reset` e `git push --force` na branch `26.1.2` — só é seguro porque é um repositório pessoal sem outros colaboradores.

**Passo a passo:**

```powershell
# 1. Ir para a branch 26.1.2
git checkout 26.1.2

# 2. Guardar todos os ficheiros actuais (já estão committed, mas vamos usar como referência)
# Nada a fazer — o working tree está limpo

# 3. Voltar ao commit inicial vazio (antes do big commit)
git reset --soft HEAD~1
# Agora os ficheiros estão todos em staged, prontos para ser re-commitados aos poucos
```

**Criar commits lógicos por camadas:**

```powershell
# ── Commit 1: Scaffolding do projecto ────────────────────────────────────────
git add build.gradle gradle.properties settings.gradle gradlew gradlew.bat
git add gradle/
git add src/main/resources/fabric.mod.json
git add src/main/resources/soundtweaks.mixins.json
git add src/client/resources/
git commit -m "chore: scaffold Fabric mod project for Minecraft 26.1.2"

# ── Commit 2: Core — configuração e registo de sons ──────────────────────────
git add src/main/java/com/scr0ols/soundtweaks/SoundTweaks.java
git add src/main/java/com/scr0ols/soundtweaks/SoundRegistry.java
git add src/main/java/com/scr0ols/soundtweaks/SoundCategory.java
git add src/main/java/com/scr0ols/soundtweaks/VolumeConfig.java
git add src/main/java/com/scr0ols/soundtweaks/VolumeResolver.java
git add src/main/java/com/scr0ols/soundtweaks/MissingBlockRegistry.java
git commit -m "feat: core — sound registry, categories, volume config and resolver"

# ── Commit 3: Sistema de presets ─────────────────────────────────────────────
git add src/main/java/com/scr0ols/soundtweaks/PresetConfig.java
git commit -m "feat: preset system — named presets with per-sound and per-block overrides"

# ── Commit 4: Mixins de volume ────────────────────────────────────────────────
git add src/client/java/com/scr0ols/soundtweaks/client/mixin/
git add src/client/resources/soundtweaks.client.mixins.json
git commit -m "feat: mixins — AbstractSoundInstance volume injection and LevelMixin for blocks"

# ── Commit 5: Client — keybinds e inicialização ───────────────────────────────
git add src/client/java/com/scr0ols/soundtweaks/client/SoundTweaksClient.java
git add src/client/java/com/scr0ols/soundtweaks/client/SoundDisplayHelper.java
git commit -m "feat: client init — keybinds (K=menu, configurable preset shortcuts)"

# ── Commit 6: GUI — ecrã principal ───────────────────────────────────────────
git add src/client/java/com/scr0ols/soundtweaks/client/gui/SoundTweaksScreen.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/SoundListWidget.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/BlockListWidget.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/FilterDropdown.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/SoundSliderButton.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/BlockSliderButton.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/GroupSliderButton.java
git commit -m "feat: main screen — sound/block list with filters, sliders, and preset sidebar"

# ── Commit 7: GUI — ecrã de presets ──────────────────────────────────────────
git add src/client/java/com/scr0ols/soundtweaks/client/gui/PresetsScreen.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/PresetEditorScreen.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/PresetSoundSliderButton.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/PresetBlockSliderButton.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/PresetGroupSliderButton.java
git commit -m "feat: presets screen — create, edit, color, rename, shortcut, Simple/Detail view"

# ── Commit 8: GUI — utilitários e diálogos ───────────────────────────────────
git add src/client/java/com/scr0ols/soundtweaks/client/gui/ImportConfigScreen.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/RenameScreen.java
git add src/client/java/com/scr0ols/soundtweaks/client/gui/ConfigFileUtil.java
git commit -m "feat: import/export dialogs and rename screen"

# ── Commit 9: Assets e i18n ───────────────────────────────────────────────────
git add src/main/resources/assets/
git commit -m "feat: icon and en_us translations"

# ── Commit 10: Documentação ───────────────────────────────────────────────────
git add README.md MODRINTH_DESCRIPTION.md
git add PLANO_ORGANIZACAO_SONS.md PLANO_PUBLICACAO.md
git add remove_sounds.txt
git commit -m "docs: README, Modrinth description, technical notes, and sound list"
```

### 1.4 Verificar que não ficou nada por committar

```powershell
git status
git diff --staged
```

Se houver ficheiros que não sabes onde encaixar, adiciona-os ao commit mais próximo logicamente.

---

## Fase 2 — Sincronizar com GitHub

```powershell
# Push da branch 26.1.2 com force (reescrevemos o histórico)
git push origin 26.1.2 --force-with-lease

# Push da tag de release para main
git push origin v1.0.0
```

---

## Fase 3 — Criar PR de 26.1.2 → main no GitHub

1. Ir a `https://github.com/joaoosilva/soundtweaks`
2. Criar Pull Request: `26.1.2` → `main`
   - **Título:** `feat: SoundTweaks v1.0.0 — initial release for Minecraft 26.1.2`
   - **Descrição:**
     ```
     Initial release of SoundTweaks for Minecraft 26.1.2.

     **Features:**
     - Per-sound volume control (0–200%) for all ~800+ sounds
     - Per-block volume control with block-specific overrides
     - Named preset system with keyboard shortcuts (up to 3-key combos)
     - Preset favorites sidebar, quick mute/unmute
     - Simple/Detail view, import/export configs and presets
     - Client-side only — works on vanilla servers

     **Known issue:**
     - Entity sound volume control not yet working (SoundEngineMixin investigation ongoing)
     ```
3. Fazer merge via **Squash and merge** ou **Merge commit** — o histórico detalhado já está em `26.1.2`

---

## Fase 4 — Workflow para iterações futuras

### Para cada nova feature/fix:

```powershell
# 1. Partir sempre de 26.1.2 actualizado
git checkout 26.1.2
git pull origin 26.1.2

# 2. Criar branch de feature
git checkout -b feat/fix-soundengine
# ou: feat/preset-confirmation, fix/rename-validation, etc.

# 3. Desenvolver com commits pequenos e descritivos
git add <ficheiros>
git commit -m "fix: investigate SoundEngine.queueTickingSound path for entity sounds"

git add <ficheiros>
git commit -m "fix: redirect volume in queueTickingSound for entity ambient sounds"

git add <ficheiros>
git commit -m "test: verify entity sound muting works for cow, pig, frog"

# 4. Push e criar PR para 26.1.2
git push origin feat/fix-soundengine
# → criar PR no GitHub: feat/fix-soundengine → 26.1.2

# 5. Depois de merge em 26.1.2, quando for release:
# → criar PR no GitHub: 26.1.2 → main
# → tag: git tag -a v1.0.1 -m "fix: entity sound volume control"
```

### Convenção de commits (Conventional Commits)

| Prefixo | Uso |
|---------|-----|
| `feat:` | Nova funcionalidade |
| `fix:` | Correcção de bug |
| `chore:` | Build, dependências, scaffolding |
| `docs:` | Documentação |
| `refactor:` | Refactoring sem mudança de comportamento |
| `test:` | Testes (quando adicionares) |

---

## Próxima branch a criar

```powershell
git checkout 26.1.2
git checkout -b feat/fix-soundengine
```

Esta branch é para o trabalho descrito em `PROMPT_SOUNDENGINE_FIX.md`.

---

## Checklist de execução

- [ ] Fase 1.1 — Verificar estado actual
- [ ] Fase 1.2 — Tag v1.0.0 em main
- [ ] Fase 1.3 — Reescrever histórico em 26.1.2 (10 commits)
- [ ] Fase 1.4 — Confirmar que não há ficheiros por committar
- [ ] Fase 2 — Push com force para GitHub
- [ ] Fase 3 — Criar PR 26.1.2 → main no GitHub
- [ ] Fase 4 — Criar branch `feat/fix-soundengine` e começar trabalho
