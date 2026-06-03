# Plano de Publicação — SoundTweaks

## 1. Preparação técnica

- [ ] **Limpar debug logs** — remover / comentar `LOGGER.debug(...)` em `SoundEngineMixin.java`
- [ ] **Verificar `fabric.mod.json`** — `version`, `description`, `authors`, `contact` (adicionar GitHub/Modrinth)
- [ ] **Rever `gradle.properties`** — confirmar `mod_version`, `maven_group`, `archives_base_name`
- [ ] **Testar build limpo** — `./gradlew clean build`, confirmar que o `.jar` funciona numa instância vanilla + Fabric API
- [ ] **Confirmar compatibilidade** — testar com Sodium, ModMenu, e outros mods comuns
- [ ] **Definir versão de lançamento** — ex: `1.0.0` ou `0.1.0` (pré-release se ainda tiver bugs abertos)

---

## 2. Assets visuais

- [ ] **Ícone do mod** — verificar `icon.png` (tem de ser 128×128 ou 512×512 para Modrinth)
- [ ] **Banner/Header image** — imagem de capa para a página Modrinth (recomendado: 900×300px)
- [ ] **Screenshots** — pelo menos 3 para a aba Gallery:
  - [ ] Ecrã principal (SoundTweaksScreen) com lista de sons
  - [ ] Sidebar de presets aberta
  - [ ] Ecrã de gestão de presets (PresetsScreen)
  - [ ] PresetEditorScreen com sliders
- [ ] **GIF opcional** — demonstração do workflow básico (abrir K → ajustar som → fechar)

---

## 3. Documentação

- [x] **README.md** atualizado (GitHub)
- [x] **MODRINTH_DESCRIPTION.md** — descrição completa para a aba Description
- [ ] **Rever FAQ** na descrição Modrinth — adicionar perguntas reais que surjam
- [ ] **Short description** (até 255 carateres) — já existe em `fabric.mod.json`, rever para EN:
  > "Per-sound and per-block volume control. Silence, boost, or fine-tune any Minecraft sound individually. Create presets and bind shortcuts."
- [ ] **Tags/Categories no Modrinth** — sugerir: `utility`, `audio`, `client-side`, `fabric`

---

## 4. Publicação no Modrinth

- [ ] Criar conta em [modrinth.com](https://modrinth.com) (ou usar existente)
- [ ] Criar novo projeto → tipo **Mod**
- [ ] Preencher:
  - [ ] Nome: `SoundTweaks`
  - [ ] Slug: `soundtweaks` (verificar disponibilidade)
  - [ ] Short description (EN)
  - [ ] Ícone
  - [ ] Banner
  - [ ] Categorias / tags
  - [ ] Links (GitHub, Issues)
  - [ ] Licença: CC0
- [ ] Colar conteúdo de `MODRINTH_DESCRIPTION.md` na aba Description
- [ ] Upload das screenshots para a aba Gallery (com títulos descritivos)
- [ ] Upload do `.jar` como primeira versão:
  - [ ] Version number: `1.0.0`
  - [ ] Game versions: `26.1.2`
  - [ ] Loader: `Fabric`
  - [ ] Dependencies: `Fabric API` (required)
  - [ ] Release channel: `Release` (ou `Beta` se preferires)
  - [ ] Changelog da versão

---

## 5. Publicação no CurseForge *(opcional, depois do Modrinth)*

- [ ] Criar conta / projeto em CurseForge
- [ ] Adaptar descrição (CurseForge usa BBCode em alguns campos, mas aceita markdown na descrição rich text)
- [ ] Repetir upload do `.jar`
- [ ] Adicionar link cruzado entre Modrinth e CurseForge

---

## 6. Divulgação

- [ ] **GitHub** — criar repositório público com o código fonte
  - [ ] Push do código
  - [ ] Adicionar tópicos: `minecraft`, `fabric-mod`, `minecraft-mod`, `audio`, `java`
  - [ ] Criar primeiro Release no GitHub com o `.jar` como asset
- [ ] **Reddit** — post em r/feedthebeast e/ou r/fabricmc
  - [ ] Título sugerido: *"SoundTweaks — per-sound volume control mod for Fabric 26.1.2"*
  - [ ] Incluir screenshots e link Modrinth
- [ ] **Fóruns Modrinth** / Discord da comunidade Fabric — anunciar
- [ ] **YouTube / TikTok** *(opcional)* — vídeo curto a demonstrar o mod

---

## 7. Pós-publicação

- [ ] Monitorizar comentários/issues na primeira semana
- [ ] Responder a bug reports — criar template de issue no GitHub
- [ ] Planear próximas versões com base no feedback (ver bugs pendentes em `PLANO_ORGANIZACAO_SONS.md`)
- [ ] Atualizar para versão de Minecraft seguinte quando sair

---

## Bugs a resolver ANTES de publicar (recomendado)

- [ ] Harmonizar cores da sidebar (tom roxo/azulado vs. fundo geral)
- [ ] Botão `[Manage Presets]` usar widget `Button` nativo
- Opcionais (podem ir para v1.1):
  - [ ] PresetsScreen redesign (editMode overlay)
  - [ ] Tooltip nos dropdowns com texto truncado

---

## Melhorias UI — sessão de revisão (2026-06-03)

> Nota: o botão "Clear" no rename overlay é intencional (limpa o campo, não fecha).

- [x] `DividerEntry` (existe mas nunca usada) — usar para separar sons de blocos na lista principal e no editor de preset
- [x] Footer do `PresetEditorScreen` — reposicionar speaker button para ficar mais equilibrado
- [x] `PresetRow` — quadrado de cor maior (14×14) com borda branca quando inactivo
- [x] Color picker — label "Custom" ao lado do slot personalizado
- [x] Header `SoundTweaksScreen` — protecção de colisão título/botões
- [x] Legenda footer `PresetEditorScreen` — ellipsis se a string for cortada
