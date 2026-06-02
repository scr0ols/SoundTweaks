# SoundTweaks — Sessão: Fix SoundEngineMixin + Controlo Total de Sons

> Usa este ficheiro como contexto inicial. Não apagues — actualiza a secção "Estado" no final de cada sessão.

---

## Projecto

**Mod Fabric para Minecraft 26.1.2** — controlo granular de volume por som individual e por bloco.

- **Pasta:** `C:\Users\Lenovo\Claude_Environment\minecraft\desenvolvimento\mods\SoundTweaks\`
- **Package base:** `com.scr0ols.soundtweaks`
- **Build:** `./gradlew build` → `build/libs/SoundTweaks-1.0.0.jar`
- **Instalar:** copiar JAR para `.minecraft/mods/` de uma instância Fabric 26.1.2

---

## O que está funcional

- ✅ GUI principal (K abre) — lista sons, filtros por categoria, sliders de volume
- ✅ GUI presets — criar, editar, atalhos de teclado (até 3 teclas), favoritos, mute rápido
- ✅ GUI editor de preset — Simple/Detail view, overrides a laranja
- ✅ Import/Export de configs e presets
- ✅ **LevelMixin** — volume de blocos via `ClientLevel.playLocalSound(double,...)` funcional
- ✅ **AbstractSoundInstanceMixin** — injecta em `AbstractSoundInstance.getVolume()`, correcto em teoria
- ❌ **SoundEngineMixin** — sons de entidades não são afectados (ver secção abaixo)

---

## Arquitectura de volume

```
VolumeResolver.getEffectiveVolume(soundId):
  1. MUTED_SOUNDS set volátil (botão mute UI)        → 0.0f
  2. Presets activos (maior desvio de 1.0 ganha)     → valor do preset
  3. VolumeConfig.SOUNDS base                        → valor guardado
  4. Default                                         → 1.0f (sem alteração)

VolumeResolver.getEffectiveBlockVolume(blockId):
  (mesma lógica, usa VolumeConfig.BLOCKS e preset.blocks)
```

**LevelMixin** trata blocos em `MissingBlockRegistry` (blocos sem som dedicado, ex: piston, dispenser).
**AbstractSoundInstanceMixin** trata os restantes sons via `getVolume()` — funciona para `SimpleSoundInstance`.
**SoundEngineMixin** deveria ser o ponto de intercepção principal no `SoundEngine.play()`.

---

## 🔴 PROBLEMA PRINCIPAL — SoundEngineMixin

### Bytecode de `SoundEngine.play()` em 26.1.2

```
154-160: soundInstance.getVolume()  → fstore local_5
185-193: calculateVolume(local_5, SoundSource) → fstore local_8
303-307: if (local_8 != 0.0f) goto 356          ← skip só acontece se local_8 == 0.0f
310-315: if (canStartSilent()) goto 327
319-324: if (source == MUSIC) goto 327
333-355: LOGGER.debug("Skipped playing sound"); return NOT_STARTED
356+:    cria canal e toca o som
```

**Conclusão teórica:** se `getVolume()` retornar `volume * 0.0f = 0.0f`, então `calculateVolume` retorna 0.0f e o som é saltado. O `@Redirect` em `getVolume()` deveria funcionar.

### Dois overloads de `calculateVolume` — importante

```java
private float calculateVolume(SoundInstance si)           // wrapper — chama o de baixo
private float calculateVolume(float vol, SoundSource src) // lógica real
```

Descriptors completos para Mixin:
```
calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F
calculateVolume(FLnet/minecraft/sounds/SoundSource;)F
```

### Estado actual de SoundEngineMixin.java

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
        if (id != null) SoundRegistry.addDiscovered(id.toString());
        return soundInstance.getVolume();  // ← BUG: não multiplica pelo multiplicador
    }
}
```

### O que já foi testado e falhou

| Tentativa | Resultado |
|-----------|-----------|
| `@Inject` HEAD + `ci.cancel()` | Não silencia — cancel sem setReturnValue não funciona para void |
| `@ModifyArg` em `calculateVolume(F, SoundSource)` | Disparava com 0.0f mas sons de entidades continuavam |
| `@Inject` RETURN em `calculateVolume(SoundInstance)` | Entidades não passam por este overload directamente |
| `@Inject` RETURN em `calculateVolume(F, SoundSource)` com pendingMultiplier | Consumido pela primeira chamada; segunda chamada recebia 1.0f |
| `@Redirect` em `getVolume()` com multiplicação | **Compilou e foi testado — sons de entidades continuaram audíveis** |

### Hipótese principal

Sons de entidades ambientes (cow, pig, frog) **não passam pelo `play()` directamente**. Provavelmente usam `queueTickingSound(TickableSound)` que chama internamente `play()` num contexto diferente, ou a intercepção do `getVolume()` não acontece no momento certo.

---

## Objectivo desta sessão

### Fase 1 — Diagnóstico (não modificar lógica ainda)

Adicionar logging temporário para descobrir o caminho real de execução de sons de entidades:

1. Adicionar `@Inject` em `SoundEngine.queueTickingSound()` com log do soundId
2. Adicionar `@Inject` em `SoundEngine.tick()` com log dos sons activos
3. Lançar o jogo, aproximar de uma vaca, observar logs — confirmar se `entity.cow.ambient` passa por `play()` ou por outro método

Se passar por `queueTickingSound`, o `@Redirect` em `play()` nunca é chamado para entidades.

### Fase 2 — Fix

Dependendo do diagnóstico:

- **Se via `queueTickingSound`:** adicionar `@Redirect` nesse método também (ou modificar o `TickableSound` antes de o enfileirar)
- **Se via `play()` mas `@Redirect` não dispara:** investigar se o target descriptor está correcto para 26.1.2 (pode ter mudado de `invokeinterface` para `invokevirtual`)
- **Fallback:** usar `@ModifyVariable` em `play()` para capturar e modificar `local_5` (o float de volume) directamente após `getVolume()` ser chamado

### Fase 3 — Verificação completa

Testar os seguintes cenários antes de fechar a sessão:

| Cenário | Como testar | Resultado esperado |
|---------|-------------|-------------------|
| Som de entidade (vaca) a 0% | Config base: `minecraft:entity.cow.ambient = 0.0` | Silêncio total |
| Som de entidade a 50% | Config base: `minecraft:entity.cow.ambient = 0.5` | Volume reduzido |
| Preset activo overrides entidade | Preset com `entity.cow.ambient = 0.0`, activar | Silêncio |
| Som de bloco (pistons) a 0% | Config base: bloco piston = 0.0 | Silêncio |
| Mute rápido via UI | Abrir menu, mute visible sounds | Sons visíveis silenciados |
| Keybinds funcionam | Tecla K → menu, atalho de preset | Menus abrem, preset activa |
| LevelMixin não quebra | Blocos de pistons com volume modificado | Continua a funcionar |

### Fase 4 — Limpeza antes de release

- [ ] Remover logs de debug adicionados na Fase 1
- [ ] `SoundTweaks.java:22` — substituir `"Hello Fabric world!"` por `"SoundTweaks " + MOD_VERSION + " loaded"`
- [ ] `RenameScreen.java` — adicionar validação: nome não vazio, máximo 50 caracteres
- [ ] `PresetsScreen.java` — adicionar confirmação antes de apagar preset
- [ ] Build final: `./gradlew clean build`
- [ ] Testar JAR directamente na instância Minecraft

---

## API Minecraft 26.1.2 — diferenças críticas de 1.21.x

| 1.21.x | 26.1.2 |
|--------|--------|
| `GuiGraphics` | `GuiGraphicsExtractor` |
| `render(GuiGraphics, int, int, float)` | `extractRenderState(GuiGraphicsExtractor, int, int, float)` |
| `new ResourceLocation("a:b")` | `Identifier.parse("a:b")` |
| `KeyBindingHelper` | `KeyMappingHelper` (package `keymapping.v1`) |
| `KeyMapping.Category` (String) | `KeyMapping.Category.register(Identifier.parse(...))` |
| `MouseEvent` | `MouseButtonEvent` |
| `updateNarration` | `updateWidgetNarration` |
| Cores: `0xFFFFFF` | **ARGB obrigatório**: `0xFFFFFFFF` (alfa 0 = invisível!) |
| `playLocalSound(BlockPos,...)` | `playLocalSound(double, double, double, ...)` |

**ARMADILHA:** `AbstractSelectionList.Entry` tem acesso `protected` — `BaseRow` tem de ser definida **dentro** da subclasse de `AbstractSelectionList`.

---

## Ficheiros relevantes

| Ficheiro | Linhas | Notas |
|----------|--------|-------|
| `client/mixin/SoundEngineMixin.java` | 27 | 🔴 FIX AQUI |
| `client/mixin/AbstractSoundInstanceMixin.java` | 30 | Injected em `getVolume()` — provavelmente correcto |
| `client/mixin/LevelMixin.java` | 67 | Funcional — não tocar |
| `main/VolumeResolver.java` | 67 | Lógica de prioridade — não tocar |
| `main/SoundRegistry.java` | 155 | Registo de sons descobertos |
| `resources/soundtweaks.client.mixins.json` | — | Lista de mixins registados — actualizar se adicionares novos |

---

## Estado da sessão

> **Actualizar aqui no fim de cada sessão.**

- **Última sessão:** —
- **Resultado:** Não iniciado
- **Próximo passo:** Fase 1 — Diagnóstico com logging
