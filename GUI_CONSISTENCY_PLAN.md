# SoundTweaks — Plano de Consistência Visual da GUI

## Sistema Visual (Design System)

### Paleta de Cores

```
BG_DEEP          = 0xFF1A1A2E  ← fundo de todos os overlays/diálogos
BG_MID           = 0xFF222233  ← fundo de painéis secundários
BG_HOVER         = 0xFF2A2A44  ← hover em tabs e itens

BORDER_STRONG    = 0xFF444466  ← bordas de diálogos, separador de header
BORDER_MID       = 0xFF333355  ← separadores secundários (verticais, tabs)
BORDER_SUBTLE    = 0xFF555555  ← separadores de footer

TEXT_PRIMARY     = 0xFFFFFFFF  ← títulos de ecrãs principais
TEXT_DIALOG      = 0xFFCCCCFF  ← títulos de diálogos (overlays)
TEXT_MUTED       = 0xFFAAAAAA  ← hints, contadores, descrições
TEXT_DIM         = 0xFF888899  ← labels menores
TEXT_GHOST       = 0xFF555566  ← texto muito discreto
```

### Layout Standards

```
Header height    = 24px (todos os ecrãs)
Header Y título  = 8px do topo
Separador header = BORDER_STRONG (0xFF444466), full-width, Y=24
Botões           = 20px altura (sempre, sem excepções)
Footer sep Y     = this.height - 34, margem 8px laterais, BORDER_SUBTLE
Footer botões Y  = this.height - 26
```

### Regras para Overlays/Diálogos

```
Fundo overlay    = 0xBB000000  (escurece o ecrã anterior)
Fundo caixa      = BG_DEEP (0xFF1A1A2E)
Bordas           = BORDER_STRONG (0xFF444466) nos 4 lados
Separador título = BORDER_MID (0xFF333355), após linha 22 do topo da caixa
Título           = TEXT_DIALOG (0xFFCCCCFF)
```

---

## Mudanças por Ficheiro

### 🔴 ALTA PRIORIDADE

#### `RenameScreen.java` — 3 mudanças
- [x] **Overlay de fundo**: adicionar `g.fill(0, 0, this.width, this.height, 0xBB000000)` no início de `extractRenderState`, ANTES do `super.extractRenderState()`
- [x] **Bordas laterais da caixa**: adicionar as 2 linhas laterais em `0xFF444466` (actualmente só tem topo e baixo)
- [x] **Separador após título**: adicionar `g.fill(cx - 14, cy - 14, cx + 234, cy - 13, 0xFF333355)` entre o título e o EditBox

---

### 🟡 MÉDIA PRIORIDADE

#### `PresetsScreen.java` — 5 mudanças
- [x] **Divisor vertical** lista/painel: `0xFF111111` → `0xFF333355` (linha ~352: `g.fill(LIST_W, 0, LIST_W + 1, ...)`)
- [x] **Separador painel header**: `0xFF333344` → `0xFF444466` (linha ~398: `g.fill(px, PANEL_HDR_H - 1, ...)`)
- [x] **Create overlay fundo**: `0xFF222233` → `0xFF1A1A2E` (linha ~371: `g.fill(cx - 10, cy - 26, ...)`)
- [x] **Create overlay bordas**: adicionar bordas laterais `0xFF444466` ao rect do create overlay
- [x] **Título Y**: `10` → `8` (linha ~358: `g.centeredText(..., this.width / 2, 10, ...)` no bloco `else`)

#### `SoundTweaksScreen.java` — 3 mudanças
- [x] **Botões do header** (muteSoundsBtn, viewToggleButton, presetsBtn): alturas de `14` → `20`; ajustar Y de `4` → `2`; adicionar banda de fundo `g.fill(0, 0, contentW(), 24, 0xFF1A1A2E)` + separador `0xFF444466` no `extractRenderState`
- [x] **Filtros**: mover de Y=`22` → Y=`26` para acomodar o header mais alto; lista começa em Y=`46` → Y=`50`
- [x] **MANAGE_H** (botão Manage na sidebar): `18` → `20` (linha ~46)

---

### 🟢 BAIXA PRIORIDADE

#### `PresetsScreen.java` (cont.)
- [x] **importPresetsBtn e openConfigBtn**: altura de `18` → `20` (linhas ~136 e ~143)

---

### ✅ SEM MUDANÇAS NECESSÁRIAS

#### `PresetEditorScreen.java`
- Header 24px, separador `0xFF444466`, footer correto — conforme o design system.

#### `ImportConfigScreen.java`
- Overlay, bordas nos 4 lados, separador após título, `0xFF1A1A2E` — é o template de referência.

---

## Melhorias adicionais aplicadas na sessão de GUI refinement (após checklist inicial)

### `PresetsScreen.java` — toques visuais
- [x] **Preset header**: fundo transparente; título na cor do preset, 1.15× escalado, outline 4 direcções
- [x] **Tab ativa**: acento superior na cor do preset; fundo neutro (`0xFF2A2A3A`)
- [x] **Header border**: 2px — `0xFF444466` + `0xFF111111`
- [x] **Footer**: separador 2px (`0xFF111111` + `0xFF555555`); Done move para `height-26`
- [x] **Divisor vertical** painel: removido
- [x] **Color tab**: painel escuro 340×138px (`0xBB1A1A1A`)
- [x] **Shortcut tab**: painel escuro 340×64px (`0xBB1A1A1A`), garante legibilidade
- [x] **Rename widget**: max 320px, centrado, botões a full-width do campo
- [x] **Create overlay**: bordas nos 4 lados completas
- [x] **Sounds panel**: mute→header, Import from config ao lado de Done no footer
- [x] **Override hint**: texto removido

### `PresetSoundList.java`
- [x] **Orange accent bar**: removida; override indicado só pela cor do texto + tint subtil

### `PresetConfig.java`
- [x] **Paleta de cores**: 18 Jewel Tones em 3 linhas (Rubi→Jade / Coral→Menta / Borgonha→Ouro)

### `SoundTweaksScreen.java`
- [x] Botões mute/view/presets visíveis (banda desenhada antes do super)

---

## Próxima tarefa — Toques finais de UI

Branch: `feat/ui-improvements` (continuar) ou novo `feat/ui-polish`

Pendentes identificados e a explorar:
- [ ] **PresetsScreen — título escalado**: validar centramento em nomes longos
- [ ] **SoundTweaksScreen**: rever consistência do header da sidebar com o novo design
- [ ] **Geral**: rever todos os ecrãs após mudanças para garantir que nada ficou desalinhado
- [ ] **Possível**: adicionar tooltips / feedback visual nos overlays (create, delete)
- [ ] **Possível**: animação/transição subtil ao mudar de tab (se API suportar)

---

## Ordem de Implementação Recomendada

~~1. `RenameScreen.java` (crítico — ecrã visualmente quebrado)~~  ✅  
~~2. `PresetsScreen.java` — divisor e separadores~~  ✅  
~~3. `SoundTweaksScreen.java` — header e botões~~  ✅  
~~4. `PresetsScreen.java` — botões footer e título Y~~  ✅  

---

## Ficheiros de Referência

- GUI: `src/client/java/com/scr0ols/soundtweaks/client/gui/`
- Ecrã de referência (mais completo): `ImportConfigScreen.java`
- Plano Git: `GIT_LOG.md`
