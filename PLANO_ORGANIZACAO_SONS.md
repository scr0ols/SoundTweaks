# SoundTweaks — Contexto para nova sessão

> Projecto: Fabric mod Minecraft 26.1.2 — controlo granular de volume por som e por bloco.
> Pasta: `C:\Users\Lenovo\Claude_Environment\minecraft\desenvolvimento\mods\SoundTweaks\`
> Package base: `com.scr0ols.soundtweaks`

---

## Ficheiros existentes

| Ficheiro | Package | Função |
|---|---|---|
| `SoundTweaks.java` | main | ModInitializer, logger |
| `SoundConfig.java` | main | Config de sons por ID → `soundtweaks.json` |
| `BlockConfig.java` | main | Config de volume por bloco → `soundtweaks_blocks.json` |
| `SoundRegistry.java` | main | Regista sons do BuiltInRegistries, exporta `soundtweaks_sounds.txt` |
| `SoundCategory.java` | main | Enum de categorias com prefixo + filtro extra |
| `MissingBlockRegistry.java` | main | Lista de blocos sem eventos dedicados + display names |
| `PresetConfig.java` | main | Gestão de presets — master JSON + ficheiros individuais por UUID |
| `VolumeResolver.java` | main | Resolve volume efectivo: presets activos > config base > 1.0 |
| `SoundTweaksClient.java` | client | Keybinds (K = menu, sem tecla = presets), load configs, tick save, atalhos de preset |
| `SoundDisplayHelper.java` | client | Auto-formatação de nomes |
| `SoundEngineMixin.java` | client/mixin | **🔴 PROBLEMA ACTUAL — ver secção abaixo** |
| `LevelMixin.java` | client/mixin | Interceta `ClientLevel.playLocalSound(double,...)`, aplica volume por blockId |
| `SoundTweaksScreen.java` | client/gui | Ecrã principal (K abre) — filtros, lista, sidebar de presets |
| `SoundListWidget.java` | client/gui | Lista unificada: SoundEntry + BlockEntry + GroupEntry |
| `SoundSliderButton.java` | client/gui | Slider → SoundConfig |
| `BlockSliderButton.java` | client/gui | Slider → BlockConfig |
| `GroupSliderButton.java` | client/gui | Slider de grupo — cascata para filhos em SoundConfig |
| `FilterDropdown.java` | client/gui | Dropdown reutilizável |
| `PresetsScreen.java` | client/gui | Ecrã de gestão de presets (lista, overlay de edição Color/Rename/Shortcut) |
| `PresetEditorScreen.java` | client/gui | Ecrã de edição dos sons de um preset (Simple/Detail view) |
| `PresetSoundSliderButton.java` | client/gui | Slider → `preset.sounds` (100% = remove override) |
| `PresetBlockSliderButton.java` | client/gui | Slider → `preset.blocks` |
| `PresetGroupSliderButton.java` | client/gui | Slider de grupo para presets |

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
| `KeyBindingHelper` | `KeyMappingHelper` (package `keymapping.v1`) |
| `KeyMapping.Category` (String) | `KeyMapping.Category.register(Identifier.parse(...))` |
| `MouseEvent` | `MouseButtonEvent` |
| `updateNarration` | `updateWidgetNarration` |
| Cores: `0xFFFFFF` | **ARGB obrigatório**: `0xFFFFFFFF` (alfa 0 = invisível!) |
| `playLocalSound(BlockPos,...)` | `playLocalSound(double, double, double, ...)` |

**ARMADILHA — `AbstractSelectionList.Entry` tem acesso `protected`:**
`BaseRow` tem de ser definida **dentro** da subclasse de `AbstractSelectionList`.

---

## 🔴 PROBLEMA PRINCIPAL (não resolvido) — SoundEngineMixin

### O que devia fazer
`SoundEngineMixin` deve interceptar a reprodução de sons e aplicar o multiplicador de volume do `VolumeResolver`. Para sons a 0% deve silenciá-los completamente (NOT_STARTED).

### Bytecode relevante de `SoundEngine.play()` (26.1.2)

```
154-160: soundInstance.getVolume() → fstore local_5
185-193: calculateVolume(local_5, SoundSource) → fstore local_8
303-307: if (local_8 != 0.0f) goto 356 (continua a tocar)
310-315: if (canStartSilent()) goto 327
319-324: if (source == MUSIC) goto 327
333-355: LOGGER.debug("Skipped playing sound, volume was zero."); return NOT_STARTED
356+:    cria canal e toca o som
```

**Conclusão**: se `calculateVolume` retorna 0.0f e `canStartSilent()==false` e `source != MUSIC`, o som é saltado. Portanto redirigir `getVolume()` para retornar `volume * mult` deve fazer chegar o valor correcto a `local_5` → `calculateVolume` → verificação de skip.

### Estado actual do SoundEngineMixin.java

```java
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Redirect(
        method = "play",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F")
    )
    private float redirectGetVolume(SoundInstance soundInstance) {
        var id = soundInstance.getIdentifier();
        if (id == null) return soundInstance.getVolume();
        String soundId = id.toString();
        SoundRegistry.addDiscovered(soundId);
        float mult = VolumeResolver.getEffectiveVolume(soundId);
        float result = soundInstance.getVolume() * mult;
        if (mult != 1.0f)
            SoundTweaks.LOGGER.info("SoundTweaks: {} → {}%", soundId, (int)(mult * 100));
        return result;
    }
}
```

### O que foi testado e falhou

- `@Inject` HEAD + `ci.cancel()` — não silencia (cancel sem setReturnValue não funciona para métodos void falsos)
- `@ModifyArg` em `calculateVolume(F, SoundSource)` — disparava com result=0.0 mas sons continuavam (linha 190 não é a única que importa)
- `@Inject` RETURN em `calculateVolume(SoundInstance)` — sons de entidades (cow, pig, frog) NÃO passam por este overload directamente
- `@Inject` RETURN em `calculateVolume(F, SoundSource)` com pendingMultiplier — consumido pela primeira chamada (dentro do wrapper), a segunda chamada directa recebia 1.0f
- `@Redirect` em `getVolume()` — compilou, foi testado, sons de entidades **continuaram audíveis**

### Próximo passo sugerido

O `@Redirect` em `getVolume()` é teoricamente correcto mas empiricamente não funciona. A hipótese mais provável: sons de entidades ambientes usam um caminho diferente no `SoundEngine` (talvez `playQueued`, `queueTickingSound`, ou outro método que não chama `play()` directamente).

**Investigar**: qual o método que realmente toca `entity.cow.ambient` em runtime. Adicionar `@Inject` em outros métodos do `SoundEngine` (ex: `queueTickingSound`, `tick`) com log para descobrir o caminho real.

---

## Arquitectura de volume — decisões tomadas

### VolumeResolver — prioridade actual
```
1. Presets activos  (maior desvio de 1.0 ganha conflitos entre presets)
2. Config base      (SoundConfig / BlockConfig) — fallback
3. Default 1.0
```

**Decisão**: presets têm sempre prioridade sobre a config base. A config base é o estado padrão permanente; presets são overrides situacionais (ex: "modo silêncio", "só música"). Faz sentido que um preset activo ganhe.

### PresetConfig — arquitectura de ficheiros
```
config/
  soundtweaks_presets.json          ← master: lista de UUIDs + activePresets + favoritePresets
  soundtweaks_presets/
    <uuid>.json                     ← um ficheiro por preset com os dados completos
```

### Formato de import (simples, sem UUID)
```json
[
  {
    "name": "Animais Passivos 0%",
    "colorIndex": 3,
    "sounds": { "minecraft:entity.cow.ambient": 0.0 },
    "blocks": {}
  }
]
```
Valores são multiplicadores (0.0–1.0), não percentagens. UUID gerado automaticamente no import. Atalhos não importados (específicos de cada máquina).

---

## Funcionalidades completas e funcionais

- ✅ GUI principal (SoundTweaksScreen) — filtros, lista sons+blocos, sidebar presets favoritos
- ✅ GUI presets (PresetsScreen) — lista, overlay Color/Rename/Shortcut/Edit Sounds
- ✅ GUI editor de preset (PresetEditorScreen) — Simple/Detail view, todos os sons visíveis, overrides destacados a laranja
- ✅ Import de config base para preset
- ✅ Import de ficheiro de preset (TinyFileDialogs, formato simples)
- ✅ Import de config de sons (TinyFileDialogs)
- ✅ Keybinds: K = abrir menu principal, sem tecla = abrir presets (configurável em Controls → SoundTweaks)
- ✅ Atalhos de preset (até 3 teclas: held1 + held2 + trigger)
- ✅ Botão mute/unmute visible sounds (SoundTweaksScreen e PresetEditorScreen)
- ✅ LevelMixin — volume de blocos funcional
- 🔴 **SoundEngineMixin — volume de sons NÃO funcional** (ver acima)

---

## API crítica Mixin para 26.1.2

Dois `calculateVolume` no `SoundEngine`:
```java
private float calculateVolume(SoundInstance si)           // wrapper — chama o de baixo
private float calculateVolume(float vol, SoundSource src) // lógica real
```

Para diferenciar em targets Mixin, usar o descriptor completo:
```
calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F
calculateVolume(FLnet/minecraft/sounds/SoundSource;)F
```

`SoundInstance.getVolume()` é um método de interface — `invokeinterface` no bytecode, mas `@Redirect` e `@At(value="INVOKE")` funcionam igual.

---

## Notas gerais de implementação

### getScrollAmount — não existe em 26.1.2
```java
private double trackedScrollAmount = 0.0;
@Override public void setScrollAmount(double amount) {
    super.setScrollAmount(amount); this.trackedScrollAmount = amount;
}
public double getScrollAmount() { return trackedScrollAmount; }
```

### Jukebox stop imediato
```java
if ("minecraft:jukebox".equals(this.blockId) && this.value <= 0.0)
    Minecraft.getInstance().getSoundManager().stop(null, SoundSource.RECORDS);
```

### Ícone speaker reutilizável
```java
static void drawSpeakerIcon(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, boolean muted)
```
Método `static` em `SoundTweaksScreen`, reutilizado em `PresetEditorScreen`.
