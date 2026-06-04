# test-samples — Ficheiros de teste manual

Ficheiros para verificar os 4 bugs de severidade alta corrigidos no commit `4e80eeb`.

---

## Fix 1 — VolumeConfig.importFrom() — Corrupção de dados em import

**O que foi corrigido:** antes do fix, se o save falhasse após o `clear()`,
os volumes ficavam vazios em memória sem ficheiro actualizado. Agora escreve
para disco primeiro; se falhar, a memória fica intacta.

### Teste A — Import válido ✅
**Ficheiro:** `fix1_import_valid.json`
**Passos:**
1. Lança o Minecraft com o mod
2. Abre SoundTweaks (K) e ajusta alguns volumes manualmente
3. Vai a Presets → Import Presets... → selecciona este ficheiro
4. **Resultado esperado:** volumes substituídos pelos do ficheiro (cave=0.1, creeper=0.2, etc.)

### Teste B — Import inválido (não é JSON) ❌
**Ficheiro:** `fix1_import_invalid_not_json.txt`
**Passos:**
1. Abre SoundTweaks e ajusta alguns volumes
2. Vai a Presets → Import Presets... → selecciona este ficheiro
3. **Resultado esperado:** erro silencioso ou mensagem de falha; os volumes anteriores mantêm-se intactos

### Teste C — Import JSON com estrutura errada ❌
**Ficheiro:** `fix1_import_wrong_structure.json`
**Passos:**
1. Igual ao Teste B mas com este ficheiro
2. **Resultado esperado:** retorna 0 entradas importadas (ou -1); volumes intactos

---

## Fix 2 — PresetConfig.readFloatMap() — Valores JSON mal-formados

**O que foi corrigido:** `null`, arrays `[]` e objectos `{}` como valor de volume
já não causam NPE — são ignorados silenciosamente.

### Teste — Carregar preset com valores inválidos ✅
**Ficheiro:** `fix2_malformed_preset_values.json`
**Passos:**
1. Fecha o Minecraft (ou usa um mundo de teste)
2. Copia este ficheiro para `.minecraft/config/soundtweaks_presets.json`
   (faz backup do original primeiro!)
3. Lança o Minecraft
4. **Resultado esperado:**
   - Dois presets aparecem na lista: "Preset Mal-Formado" e "Preset Normal"
   - "Preset Mal-Formado" tem apenas 2 sons válidos (creeper=0.2 e piston=0.5);
     os 3 inválidos (null, array, object) são ignorados
   - "Preset Normal" aparece activo e funciona normalmente
   - Sem crash ao carregar

---

## Fix 3 — PresetConfig.load() — Referências órfãs em favoriteNames/activeNames

**O que foi corrigido:** se um preset foi apagado mas o nome ainda estava em
`activePresets` ou `favoritePresets` no ficheiro, o jogo carregava essas
referências inválidas. Agora são limpas automaticamente no load().

### Teste — Carregar config com órfãos ✅
**Ficheiro:** `fix3_orphan_references.json`
**Passos:**
1. Fecha o Minecraft
2. Copia este ficheiro para `.minecraft/config/soundtweaks_presets.json`
   (faz backup do original primeiro!)
3. Lança o Minecraft
4. **Resultado esperado:**
   - Apenas "Preset Existente" aparece na lista
   - Não aparece nenhum preset com nome "Preset Que Foi Apagado" nem "Outro Preset Inexistente"
   - "Preset Existente" aparece como activo (estava em activePresets)
   - "Preset Existente" aparece como favorito (estava em favoritePresets)
   - Os órfãos são removidos silenciosamente; se abrires o ficheiro de config
     após o primeiro save, já não aparecem em activePresets/favoritePresets

---

## Fix 4 — AbstractSoundInstanceMixin — NPE em volume nulo

Não há ficheiro de teste específico — este é um edge case de engine.

**Teste geral recomendado:**
1. Activa 2-3 presets em simultâneo
2. Entra num mundo com muita actividade sonora:
   - Área com muitos mobs (spawner ou slime chunks)
   - Chuva
   - Blocos de redstone activos (relógio de redstone, pistões, note blocks)
3. **Resultado esperado:** sem crashes NullPointerException nos logs durante o jogo

---

## Localização dos ficheiros de config

| SO | Caminho |
|----|---------|
| Windows | `%AppData%\.minecraft\config\` |
| Linux/Mac | `~/.minecraft/config/` |

> **Atenção:** faz sempre backup do `soundtweaks_presets.json` original antes de substituir.
