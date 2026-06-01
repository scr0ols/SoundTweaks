# SoundTweaks — Contexto para nova sessão

> Projecto: Fabric mod Minecraft 26.1.2 — controlo granular de volume por som e por bloco.
> Pasta: `C:\Users\Lenovo\Claude_Environment\minecraft\desenvolvimento\mods\soundtweaks-template-26.1.2\`
> Package base: `com.scr0ols.soundtweaks`

---

## Ficheiros existentes

| Ficheiro | Package | Função |
|---|---|---|
| `SoundTweaks.java` | main | ModInitializer, logger |
| `SoundConfig.java` | main | Config de sons por ID → `soundtweaks.json` |
| `BlockConfig.java` | main | Config de volume por bloco → `soundtweaks_blocks.json` |
| `SoundRegistry.java` | main | Regista sons do BuiltInRegistries, exporta `soundtweaks_sounds.txt` |
| `SoundCategory.java` | main | Enum de categorias com prefixo + filtro extra (REDSTONE usa predicate) |
| `MissingBlockRegistry.java` | main | Lista de blocos sem eventos dedicados + display names + group controls |
| `PresetConfig.java` | main | Gestão de presets + persistência em `soundtweaks_presets.json` |
| `VolumeResolver.java` | main | Resolve volume efectivo: base config > presets activos > 1.0 |
| `SoundTweaksClient.java` | client | Keybind K, load configs, tick save, atalhos de preset |
| `SoundDisplayHelper.java` | client | Auto-formatação de nomes + overrides JSON opcionais |
| `SoundEngineMixin.java` | client/mixin | Interceta `SoundEngine.play()`, aplica volume por soundId |
| `LevelMixin.java` | client/mixin | Interceta `ClientLevel.playLocalSound(double,double,double,...)`, aplica volume por blockId |
| `SoundTweaksScreen.java` | client/gui | Ecrã principal (K abre) — filtros, lista, sidebar de presets |
| `SoundListWidget.java` | client/gui | Lista unificada: SoundEntry + BlockEntry + GroupEntry + ordenação |
| `SoundSliderButton.java` | client/gui | Slider → SoundConfig |
| `BlockSliderButton.java` | client/gui | Slider → BlockConfig (jukebox: stop RECORDS ao ir a 0) |
| `GroupSliderButton.java` | client/gui | Slider de grupo — cascata para todos os filhos em SoundConfig |
| `BlockListWidget.java` | client/gui | Widget legado — já não usado |
| `FilterDropdown.java` | client/gui | Dropdown reutilizável (Categoria + Objecto) |
| `PresetsScreen.java` | client/gui | Ecrã de gestão de presets |
| `PresetEditorScreen.java` | client/gui | Ecrã de edição dos sons de um preset |
| `PresetSoundSliderButton.java` | client/gui | Slider → `preset.sounds` (100% = remove override) |
| `PresetBlockSliderButton.java` | client/gui | Slider → `preset.blocks` (100% = remove override) |
| `RenameScreen.java` | client/gui | Ecrã dedicado para renomear — não usado activamente |

### Mixins registados
- `soundtweaks.client.mixins.json`: `SoundEngineMixin`, `LevelMixin`

---

## API crítica — Minecraft 26.1.2 (difere de 1.21.x)

| 1.21.x | 26.1.2 |
|---|---|
| `GuiGraphics` | `GuiGraphicsExtractor` |
| `render(GuiGraphics, int, int, float)` | `extractRenderState(GuiGraphicsExtractor, int, int, float)` |
| `render` em Entry | `extractContent(GuiGraphicsExtractor, int, int, boolean, float)` |
| `new ResourceLocation("a:b")` | `Identifier.parse("a:b")` |
| `getScrollAmount()` em AbstractSelectionList | **NÃO EXISTE** — usar `trackedScrollAmount` (ver nota) |
| `playLocalSound(BlockPos, ...)` em ClientLevel | **NÃO EXISTE** — usar `playLocalSound(double, double, double, ...)` |
| `MouseEvent` | `MouseButtonEvent` |
| `KeyBindingHelper` | não necessário — `KeyMapping` auto-regista |
| `updateNarration` | `updateWidgetNarration` |
| Cores: `0xFFFFFF` | **ARGB obrigatório**: `0xFFFFFFFF` (alfa 0 = invisível!) |

**ARMADILHA CRÍTICA — `AbstractSelectionList` altura vs Y:**
O construtor `new Widget(mc, width, HEIGHT, Y, itemHeight)` — o parâmetro `HEIGHT` é a **altura do widget em píxeis**, NÃO a coordenada Y do fundo. Para uma lista que vai de `listTop=28` a `listBottom=height-56`, a chamada correcta é:
```java
new PresetListWidget(mc, width, listBottom - listTop, listTop, itemH);
//                              ^^^^^^^^^^^^^^^^^^^^^ altura = diferença, não listBottom
```

**ARMADILHA — `AbstractSelectionList.Entry` tem acesso `protected`:**
`BaseRow` (ou qualquer Entry personalizada) tem de ser definida **dentro** da subclasse de `AbstractSelectionList`, não fora. Se definida no Screen exterior, o compilador dá erro de acesso.
```java
class SoundEntryList extends AbstractSelectionList<SoundEntryList.BaseRow> {
    abstract class BaseRow extends AbstractSelectionList.Entry<BaseRow> { ... }
}
```

**ARMADILHA DE RENDERING — overlays sobre widgets:**
`extractRenderState` em Screen: `super.extractRenderState(...)` renderiza TODOS os widgets registados. Para overlays modais ver `PresetsScreen.setOverlayWidgetsVisible()`.

---

## Estado actual completo das funcionalidades

### SoundTweaksScreen (ecrã principal — tecla K)
**Layout actual:**
- **Linha 1 (Y=4)**: `[speaker ícone 20px]` `[Simple/Detail View 78px]` `[Presets ▶/◄ 68px]` | título centrado
- **Linha 2 (Y=22)**: `[Categoria 120px]` `[Objecto 130px]` `[× 20px]` `[barra pesquisa — preenche restante]`
- **Lista começa em Y=46**
- **Footer**: linha separadora + contador de sons + botão Done

**Botão ícone speaker (mute toggle):**
- Ícone pixel-art desenhado com fills em `drawSpeakerIcon(g, bx, by, bw, bh, muted)` — método `static` em `SoundTweaksScreen`
- `muteSoundsActive: boolean` (instância) — toggle: muta todos visíveis → repõe todos visíveis
- Ícone: cone diamante + ondas (ON) ou cone diamante + X vermelho (OFF)

**Sidebar de presets favoritos:**
- `SIDE_W = 220` (≈35 caracteres de nome)
- `sidebarOpen: static boolean` — persiste entre aberturas
- **Botão "Presets ▶/◄"** no canto superior esquerdo (X=110, Y=4, 68px) — toggle abre/fecha sidebar, chama `rebuildWidgets()` (preserva scroll em `savedScroll` antes)
- **Quando aberta**: cabeçalho "Presets" clicável (+ seta `◄`) para fechar; botão Manage no fundo
- **Quando fechada**: sidebar invisível — `renderFavoritesSidebar` retorna imediatamente; `handleSidebarClick` retorna false
- Linha separadora vertical (`sepX = width - SIDE_W - 1`) só desenhada quando aberta

**Dropdowns Categoria + Objecto:**
- `FilterDropdown` reutilizável, renderizado **por último** em `extractRenderState` (fica sobre tudo)
- ESC fecha; letra salta para entrada
- Objecto: ordenação com dígitos depois do Z (`"~" + label` no comparador)

**Toggle Simple/Detail View** — persiste em `static detailedView`

### PresetsScreen (gestão de presets)
- Lista scrollable, cada linha tem:
  - `ON`/`OFF` + fundo colorido quando activo
  - Nome do preset
  - 18 quadrados de cor (2×9) — **a remover/mover para sub-menu Edit**
  - `★`/`☆` — toggle favorito
  - Botão de atalho (78px) — mostra tecla atribuída ou `---`; clique → modo captura — **a mover para sub-menu Edit**
  - `[Edit]` → abre `PresetEditorScreen`
  - `[R]` → overlay de renomear — **a mover para sub-menu Edit**
  - `[X]` → apaga preset
- **Overlay criar** (`[New Preset]`): EditBox + Confirm/Cancel
- **Overlay renomear** (`[R]`): mesmo mecanismo; `mouseClicked` intercepta cliques directamente
- **Overlay captura de atalho — 3 fases**:
  - Fase 1: qualquer tecla → `pendingHeldKey` (held key 1); BACKSPACE → limpar atalho; ESC → cancelar
  - Fase 2: qualquer tecla → `pendingHeldKey2`; Enter/KP_Enter → saltar (heldKey2=0); ESC → cancelar
  - Fase 3: qualquer tecla → trigger; grava `shortcutKey + shortcutHeldKey + shortcutHeldKey2`; ESC → cancelar

### Sistema de atalhos de preset (estado actual)
**Campos em `PresetConfig.Preset`:**
- `shortcutKey: int` — trigger: bits 0-15 = GLFW key, bits 16-31 = mods (só usado quando `shortcutHeldKey==0`)
- `shortcutHeldKey: int` — 1ª held key GLFW (0 = não usado)
- `shortcutHeldKey2: int` — 2ª held key GLFW opcional (0 = não usado); backwards-compatible (ausente no JSON antigo = 0)

**Deteção em `SoundTweaksClient`:**
- `shortcutKeysHeld: Set<String>` (preset IDs) — deteção de flanco por preset
- Se `shortcutHeldKey != 0`: modo key+key(+key) — verificar todas as held keys via `GLFW.glfwGetKey`; trigger = flanco ascendente
- Se `shortcutHeldKey == 0`: modo antigo — verificar mods nos bits superiores + trigger flanco ascendente

**Display (`PresetsScreen.keyDisplayLabel`):**
- `shortcutHeldKey != 0` → `"I+J+Y"` ou `"I+Y"` (com ou sem heldKey2)
- `shortcutHeldKey == 0` → `"Ctrl+Y"` (estilo antigo via `keyName`)

### PresetEditorScreen (editar sons de um preset)
- **Cabeçalho**: título + fundo tintado com cor do preset
- **Filtros**: dropdown de categoria + pesquisa + toggle "Overrides only / All sounds"
- **Lista unificada**: `SoundEntryList extends AbstractSelectionList<SoundEntryList.BaseRow>`
  - `BaseRow` definida **dentro** de `SoundEntryList` (ver armadilha acima)
  - `SoundRow` usa `PresetSoundSliderButton`; `BlockRow` usa `PresetBlockSliderButton`
- **Footer**: `[Import from config]` `[ícone speaker toggle]` `[Done]`
  - Import: copia sons/blocos ≠ 1.0 da config base para o preset
  - Speaker toggle: muta/repõe todos os sons/blocos visíveis no preset (usa mesmo `drawSpeakerIcon`)

### PresetConfig — estrutura de dados
```json
{
  "presets": [{
    "id": "uuid", "name": "Trading Hall",
    "colorIndex": 2,
    "shortcutKey": 89,
    "shortcutHeldKey": 73,
    "shortcutHeldKey2": 0,
    "sounds": {"minecraft:entity.villager.trade": 0.0},
    "blocks": {"minecraft:observer": 0.0}
  }],
  "activePresets": ["uuid"],
  "favoritePresets": ["uuid"]
}
```

**Hierarquia de prioridade** (`VolumeResolver`):
1. Config base (`SoundConfig` / `BlockConfig`) — ganha sempre
2. Presets activos — maior desvio de 1.0 vence conflitos
3. Default (1.0)

---

## Bugs resolvidos / concluídos

- ✅ Linha roxa separadora quando sidebar fechada — separador só desenhado quando `sidebarOpen == true`
- ✅ Aba lateral removida — substituída por botão "Presets ▶/◄" na linha 1
- ✅ Shortcuts key+key (até 3 teclas) — `shortcutHeldKey` + `shortcutHeldKey2` + captura 3 fases
- ✅ Números antes do A no dropdown objecto — sort com dígitos depois do Z

---

## Funcionalidades recentemente implementadas

- ✅ **editMode overlay** (Color/Rename/Shortcut/Edit Sounds) em PresetsScreen
- ✅ **Shortcut capture malilib-style** — tracking em tempo real (1/2/3 teclas)
- ✅ **Cor personalizada hex** — slot custom no Color tab com EditBox RRGGBB
- ✅ **Import/Export configs** — Import Presets, Import Config (JFileChooser), Open Config Folder
- ✅ **Tooltips** em todos os botões relevantes (SoundTweaksScreen, PresetEditorScreen, PresetsScreen)
- ✅ **Ordenação** — nomes numéricos após Z (PresetEditorScreen e SoundListWidget)
- ✅ **Cabeçalho PresetEditorScreen** — cor mais intensa e mais transparente
- ✅ **Filtros centrados** em PresetEditorScreen

---

## Bugs conhecidos / pendentes

### Sidebar — cores (a harmonizar)
- Tom roxo/azulado da sidebar diferente do fundo geral do ecrã (que é o background do Minecraft com blur)
- **Objectivo**: sidebar com o mesmo tom do outro background (mais neutro/escuro), não o `0xAA111122` actual
- **Botão [Manage Presets]**: visualmente inconsistente — deve usar `Button` widget nativo

### PresetsScreen — redesign planeado (PRÓXIMA FASE)
**Objectivo**: simplificar a lista removendo as paletes de cor inline e consolidar acções no Edit.

**Lista nova (por linha)**:
- `[ON/OFF toggle]` + nome do preset + `[★/☆]` + `[Edit ▼]` + `[X]`

**Sub-menu Edit** (overlay dentro de PresetsScreen, mesmo mecanismo create/rename):
- `editMode: enum { NONE, COLOR, RENAME, SHORTCUT }`
- `NONE` → lista normal
- `COLOR` → grid de 18 cores (3 linhas × 6 colunas)
- `RENAME` → EditBox (como overlay actual de rename)
- `SHORTCUT` → captura de 3 teclas (reutilizar código já implementado)

**Implementação**: o código do overlay de captura de atalho já está pronto — é só mover para o contexto do `editMode`.

---

## Notas de implementação importantes

### getScrollAmount — não existe em 26.1.2
```java
// Em SoundListWidget:
private double trackedScrollAmount = 0.0;
@Override public void setScrollAmount(double amount) {
    super.setScrollAmount(amount); this.trackedScrollAmount = amount;
}
public double getScrollAmount() { return trackedScrollAmount; }
```

### Jukebox stop imediato
```java
// Em BlockSliderButton.applyValue():
if ("minecraft:jukebox".equals(this.blockId) && this.value <= 0.0)
    Minecraft.getInstance().getSoundManager().stop(null, SoundSource.RECORDS);
```

### Cor por volume (SoundListWidget)
```java
static int volumeColor(float volume) { return volume <= 0.0f ? 0xFFFF4444 : 0xFFFFFFFF; }
```

### Ícone speaker (SoundTweaksScreen.drawSpeakerIcon)
Método `static` reutilizado em `SoundTweaksScreen` e `PresetEditorScreen`.
```java
static void drawSpeakerIcon(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, boolean muted)
// Ícone 12×10 centrado no botão. Cone diamante + ondas (ON) ou X vermelho (OFF).
```

### Separador entre conteúdo e sidebar
```java
// Apenas quando sidebar aberta:
if (sidebarOpen) {
    int sepX = this.width - SIDE_W - 1;
    graphics.fill(sepX, 0, sepX + 1, this.height, 0xFF333355);
}
```

### Overlay de atalho — reutilização no editMode
O estado de captura em `PresetsScreen` está em:
- `capturingShortcutForId: String` (null = inactivo)
- `capturePhase: int` (1/2/3)
- `pendingHeldKey: int`, `pendingHeldKey2: int`

Ao mover para `editMode`, basta renomear `capturingShortcutForId` para o ID do preset em edição quando `editMode == SHORTCUT`.

---

## Próximas tarefas (por ordem de prioridade)

- [ ] **PresetsScreen redesign**: remover paletes inline, criar `editMode` overlay (Color/Rename/Shortcut)
- [ ] **Harmonizar cores sidebar**: mesmo tom de fundo + botão Manage como widget nativo
- [ ] **Categorias criativas**: mapear sons/blocos às tabs do inventário criativo
- [ ] **Tooltip**: nos dropdowns quando texto está truncado
- [ ] **FilterDropdown scrollbar**: highlight ao arrastar, hover
- [ ] **Indicador visual** no botão `×` quando há filtros activos
- [ ] **Debug a remover antes de release**: `SoundEngineMixin.java` — `LOGGER.debug(...)` (já é debug, não aparece em produção mas pode ser removida)
