package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.VolumeResolver;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SoundTweaksScreen extends Screen {

    // ── Estado persistente ────────────────────────────────────────────────────
    @Nullable private static SoundCategory savedCategory = null;
    @Nullable private static String        savedObject   = null;
    private   static         String        savedSearch   = "";
    private   static         double        savedScroll   = 0.0;
    private   static         boolean       detailedView  = false;
    private   static         boolean       sidebarOpen   = true;

    // ── Sidebar de presets favoritos (lado direito) ───────────────────────────
    /** Largura da sidebar — permite ~35 caracteres de nome. */
    private static final int SIDE_W   = 220;
    /** Largura da aba de abertura quando a sidebar está fechada. */
    private static final int TAB_W    = 18;
    /** Altura do botão de cada preset. */
    private static final int PRESET_H = 22;
    /** Y onde os botões de preset começam (abaixo do cabeçalho). */
    private static final int SIDE_TOP = 26;
    /** Altura do botão Manage no fundo. */
    private static final int MANAGE_H = 18;

    @Nullable private final Screen parent;

    private SoundListWidget soundList;
    private EditBox         searchBox;
    private Button          clearButton;
    private Button          viewToggleButton;
    private Button          muteSoundsBtn;
    private Button          presetsBtn;
    // Static para persistir entre aberturas — o estado real está no VolumeResolver,
    // mas usamos este flag para saber o que o botão "fez" (o que está para desmutar)
    private static boolean  muteSoundsActive = false;

    private FilterDropdown categoryDropdown;
    private FilterDropdown objectDropdown;

    @Nullable private SoundCategory selectedCategory = null;
    @Nullable private String        selectedObject   = null;
    private           String        searchQuery      = "";

    /** Largura da área de conteúdo (respeitando sidebar aberta/fechada). */
    private int contentW() { return sidebarOpen ? this.width - SIDE_W - 2 : this.width; }

    public SoundTweaksScreen(@Nullable Screen parent) {
        super(Component.translatable("soundtweaks.gui.title"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int cw = contentW();

        // ── Linha 1 (Y=4): [speaker] [Simple/Detail View] [Presets ▶/◄] ... título ...
        this.muteSoundsBtn = Button.builder(Component.empty(), btn -> toggleMuteVisible())
                .bounds(4, 4, 20, 14).build();
        this.muteSoundsBtn.setTooltip(Tooltip.create(Component.literal(
                "Mute / restore all currently visible sounds")));
        this.addRenderableWidget(this.muteSoundsBtn);

        this.viewToggleButton = Button.builder(
                Component.literal(detailedView ? "Detail View" : "Simple View"),
                btn -> {
                    detailedView = !detailedView;
                    btn.setMessage(Component.literal(detailedView ? "Detail View" : "Simple View"));
                    refreshList();
                }
        ).bounds(28, 4, 78, 14).build();
        this.viewToggleButton.setTooltip(Tooltip.create(Component.literal(
                "Simple View: grouped by sound event\n" +
                "Detail View: individual sound files")));
        this.addRenderableWidget(this.viewToggleButton);

        this.presetsBtn = Button.builder(
                Component.literal("Presets"),
                btn -> toggleSidebar()
        ).bounds(110, 4, 68, 14).build();
        this.presetsBtn.setTooltip(Tooltip.create(Component.literal(
                "Toggle the presets sidebar.\n" +
                "Presets let you save and quickly switch\n" +
                "between different volume configurations.")));
        this.addRenderableWidget(this.presetsBtn);

        // ── Linha 2 (Y=22): [Categoria] [Objecto] [×] [barra de pesquisa (preenche resto)]
        this.categoryDropdown = new FilterDropdown(4, 22, 120,
                I18n.get("soundtweaks.gui.category"), this::onCategorySelected);
        populateCategoryDropdown();

        this.objectDropdown = new FilterDropdown(128, 22, 130,
                I18n.get("soundtweaks.gui.object"), this::onObjectSelected);
        this.objectDropdown.setActive(false);

        this.clearButton = Button.builder(Component.literal("x"), btn -> clearFilters())
                .bounds(262, 22, 20, 20).build();
        this.clearButton.setTooltip(Tooltip.create(Component.literal("Clear all filters")));
        this.addRenderableWidget(this.clearButton);

        int searchX = 286;
        int searchW = Math.max(60, cw - searchX - 4);
        this.searchBox = new EditBox(this.font, searchX, 22, searchW, 20,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setResponder(q -> { this.searchQuery = q; refreshList(); });
        this.addRenderableWidget(this.searchBox);

        // ── Lista de sons (começa imediatamente abaixo dos filtros)
        int listY = 46;
        this.soundList = new SoundListWidget(this.minecraft,
                cw, this.height - listY - 36, listY, 20);
        refreshList();
        this.addRenderableWidget(this.soundList);

        // Botão Feito
        this.addRenderableWidget(
                Button.builder(Component.translatable("soundtweaks.gui.done"), btn -> this.onClose())
                        .bounds(cw / 2 + 5, this.height - 26, 120, 20)
                        .build()
        );

        // Importar config de outra instância via file dialog nativo
        var importCfgBtn = Button.builder(
                Component.literal("Import Config..."),
                btn -> {
                    String selected;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer filters = stack.mallocPointer(1);
                        filters.put(stack.UTF8("*.json")).flip();
                        selected = TinyFileDialogs.tinyfd_openFileDialog(
                                "Select soundtweaks config file",
                                "",
                                filters,
                                "JSON config files (*.json)",
                                false);
                    }
                    if (selected == null) return;
                    java.nio.file.Path src = java.nio.file.Path.of(selected);
                    String fname = src.getFileName().toString().toLowerCase();
                    if (fname.contains("block")) {
                        VolumeConfig.BLOCKS.importFrom(src);
                    } else {
                        VolumeConfig.SOUNDS.importFrom(src);
                    }
                    refreshList();
                }
        ).bounds(cw / 2 - 125, this.height - 26, 120, 20).build();
        importCfgBtn.setTooltip(Tooltip.create(Component.literal(
                "Import sound volumes from another instance.\n" +
                "Select soundtweaks.json for sounds,\n" +
                "or soundtweaks_blocks.json for blocks.\n" +
                "Replaces current configuration.")));
        this.addRenderableWidget(importCfgBtn);

        // Botão Manage Presets como widget nativo (só quando sidebar aberta)
        if (sidebarOpen) {
            int sideX   = this.width - SIDE_W;
            int manageY = this.height - MANAGE_H - 4;
            this.addRenderableWidget(Button.builder(
                    Component.translatable("soundtweaks.presets.manage"),
                    b -> this.minecraft.setScreen(new PresetsScreen(this))
            ).bounds(sideX + 2, manageY, SIDE_W - 4, MANAGE_H).build());
        }

        restoreSavedState();
    }

    private void restoreSavedState() {
        if (savedCategory != null) {
            this.selectedCategory = savedCategory;
            this.categoryDropdown.setSelectedValueSilently(savedCategory.getDropdownKey());
            if (savedCategory != SoundCategory.OTHERS && savedCategory.getPrefix() != null) {
                populateObjectDropdown(savedCategory);
                this.objectDropdown.setActive(true);
                if (savedObject != null) {
                    this.selectedObject = savedObject;
                    this.objectDropdown.setSelectedValueSilently(savedObject);
                }
            }
        }
        if (!savedSearch.isEmpty()) {
            this.searchQuery = savedSearch;
            this.searchBox.setValue(savedSearch);
        } else {
            refreshList();
        }
        if (this.soundList != null) this.soundList.setScrollAmount(savedScroll);
        syncMuteState();
    }

    /** Sincroniza o ícone do botão mute com o estado real do VolumeResolver. */
    private void syncMuteState() {
        List<String> sounds = getFilteredSounds();
        List<String> blocks = getFilteredBlocks();
        muteSoundsActive = (!sounds.isEmpty() || !blocks.isEmpty())
                && sounds.stream().allMatch(VolumeResolver::isSoundMuted)
                && blocks.stream().allMatch(VolumeResolver::isBlockMuted);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        // Título centrado, mas sem invadir os botões do header (ocupam até x≈182)
        int titleMinX = 185;
        int titleCenterX = Math.max(titleMinX + this.font.width(I18n.get("soundtweaks.gui.title")) / 2,
                contentW() / 2);
        graphics.centeredText(this.font, I18n.get("soundtweaks.gui.title"),
                titleCenterX, 8, 0xFFFFFFFF);

        // Ícone de speaker no botão de silenciar/repor
        if (this.muteSoundsBtn != null)
            drawSpeakerIcon(graphics, this.muteSoundsBtn.getX(), this.muteSoundsBtn.getY(),
                    this.muteSoundsBtn.getWidth(), this.muteSoundsBtn.getHeight(), muteSoundsActive);

        // Linha separadora vertical — só quando sidebar aberta (aba fechada tem o seu próprio separador)
        if (sidebarOpen) {
            int sepX = this.width - SIDE_W - 1;
            graphics.fill(sepX, 0, sepX + 1, this.height, 0xFF333355);
        }

        // Footer
        graphics.fill(8, this.height - 34, this.width - 8, this.height - 33, 0xFF555555);
        int total   = SoundRegistry.count();
        int visible = this.soundList != null ? this.soundList.children().size() : 0;
        String countText = visible == total
                ? I18n.get("soundtweaks.gui.sounds", total)
                : I18n.get("soundtweaks.gui.sounds_filtered", visible, total);
        graphics.text(this.font, countText, 8, this.height - 22, 0xFFAAAAAA);

        // Sidebar
        renderFavoritesSidebar(graphics, mouseX, mouseY);

        // Dropdowns — sempre por último (renderizam sobre tudo)
        this.categoryDropdown.render(graphics, mouseX, mouseY);
        this.objectDropdown.render(graphics, mouseX, mouseY);
    }

    // ── Sidebar de favoritos ──────────────────────────────────────────────────

    private void renderFavoritesSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!sidebarOpen) return;

        List<PresetConfig.Preset> favs = PresetConfig.getFavoritePresets();
        int sideX = this.width - SIDE_W;

        // Fundo da sidebar — neutro semi-transparente, mesmo tom do fundo geral
        graphics.fill(sideX, 0, this.width, this.height, 0x771A1A1E);

        // Cabeçalho — clicável para fechar
        boolean hovHeader = mouseX >= sideX && mouseY >= 0 && mouseY < 22;
        graphics.fill(sideX, 0, this.width, 22, hovHeader ? 0x88282830 : 0x88202028);
        graphics.centeredText(this.font, "Presets",
                sideX + SIDE_W / 2, 7, 0xFFDDDDDD);
        // Seta de fechar (◀) no canto esquerdo do cabeçalho
        graphics.text(this.font, "◄", sideX + 4, 7, hovHeader ? 0xFFFFFFFF : 0xFF999999);
        graphics.fill(sideX + 4, 20, this.width - 4, 21, 0xFF555555);

        // Área disponível para presets (entre cabeçalho e botão Manage — agora widget nativo)
        int manageY        = this.height - MANAGE_H - 4;
        int availableBottom = manageY - 4;

        if (favs.isEmpty()) {
            graphics.centeredText(this.font, "No favorites",
                    sideX + SIDE_W / 2, SIDE_TOP + 4, 0xFF555566);
            graphics.centeredText(this.font, "Add via Manage",
                    sideX + SIDE_W / 2, SIDE_TOP + 16, 0xFF444455);
            return;
        }

        int y = SIDE_TOP;
        for (PresetConfig.Preset preset : favs) {
            if (y + PRESET_H > availableBottom) break;

            boolean active = PresetConfig.isActive(preset.name);
            int     color  = preset.argbColor();
            boolean hov    = mouseX >= sideX + 1 && mouseX < this.width - 1
                    && mouseY >= y && mouseY < y + PRESET_H;

            if (active) {
                graphics.fill(sideX + 1, y, this.width - 1, y + PRESET_H, color | 0xFF000000);
                graphics.fill(sideX + 1, y, this.width - 1, y + 1,        0xCCFFFFFF);
                graphics.fill(sideX + 1, y + PRESET_H - 1, this.width - 1, y + PRESET_H, 0x44FFFFFF);
                graphics.fill(sideX + 1, y, sideX + 6, y + PRESET_H, 0xFFFFFFFF);
            } else {
                graphics.fill(sideX + 1, y, this.width - 1, y + PRESET_H,
                        (color & 0x00FFFFFF) | 0x44000000);
            }
            if (hov) graphics.fill(sideX + 1, y, this.width - 1, y + PRESET_H, 0x22FFFFFF);

            String name = preset.name;
            int textStartX = sideX + 10;
            int maxNameW   = this.width - textStartX - (active ? 22 : 4);
            while (name.length() > 1 && this.font.width(name) > maxNameW)
                name = name.substring(0, name.length() - 1);
            if (!name.equals(preset.name)) name += "..";

            if (active) {
                graphics.text(this.font, name, textStartX, y + 7, 0xFFFFFFFF);
                graphics.text(this.font, "ON", this.width - 18, y + 7, 0xFF88FF88);
            } else {
                graphics.text(this.font, name, textStartX, y + 7, 0xFF888888);
            }
            y += PRESET_H + 1;
        }
    }

    // ── Eventos de rato ───────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        // Dropdowns têm prioridade: a popup sobrepõe-se ao soundList (y=46+)
        // e o super.mouseClicked passaria o clique ao soundList antes do dropdown
        if (this.categoryDropdown.mouseClicked(event)) {
            if (this.categoryDropdown.isOpen()) this.objectDropdown.close();
            return true;
        }
        if (this.objectDropdown.mouseClicked(event)) {
            if (this.objectDropdown.isOpen()) this.categoryDropdown.close();
            return true;
        }

        if (super.mouseClicked(event, consumed)) return true;
        if (handleSidebarClick(event)) return true;

        return false;
    }

    private boolean handleSidebarClick(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();

        if (!sidebarOpen) return false;

        int sideX = this.width - SIDE_W;
        if (mx < sideX) return false;

        // Cabeçalho da sidebar → fechar
        if (my < 22) {
            toggleSidebar();
            return true;
        }

        // Botões de preset
        List<PresetConfig.Preset> favs = PresetConfig.getFavoritePresets();
        int availableBottom = this.height - MANAGE_H - 8;
        int y = SIDE_TOP;
        for (PresetConfig.Preset preset : favs) {
            if (y + PRESET_H > availableBottom) break;
            if (my >= y && my < y + PRESET_H) {
                PresetConfig.setActive(preset.name, !PresetConfig.isActive(preset.name));
                return true;
            }
            y += PRESET_H + 1;
        }

        return true; // absorver restantes cliques na sidebar
    }

    private void toggleSidebar() {
        savedScroll = this.soundList != null ? this.soundList.getScrollAmount() : 0.0;
        sidebarOpen = !sidebarOpen;
        this.rebuildWidgets();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.categoryDropdown.mouseDragged(event.y())) return true;
        if (this.objectDropdown.mouseDragged(event.y()))   return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.categoryDropdown.mouseReleased();
        this.objectDropdown.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.categoryDropdown.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (this.objectDropdown.mouseScrolled(mouseX, mouseY, scrollY))   return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) {
            if (this.categoryDropdown.isOpen()) { this.categoryDropdown.close(); return true; }
            if (this.objectDropdown.isOpen())   { this.objectDropdown.close();   return true; }
        }
        if (key >= 65 && key <= 90) {
            char letter = (char) key;
            if (this.categoryDropdown.isOpen()) return this.categoryDropdown.jumpToLetter(letter);
            if (this.objectDropdown.isOpen())   return this.objectDropdown.jumpToLetter(letter);
            if (!this.searchBox.isFocused())    return this.soundList.jumpToLetter(letter);
        }
        return super.keyPressed(event);
    }

    // ── Callbacks dos dropdowns ───────────────────────────────────────────────

    private void onCategorySelected(@Nullable String key) {
        this.selectedCategory = SoundCategory.fromDropdownKey(key);
        this.selectedObject   = null;
        if (this.selectedCategory != null && this.selectedCategory != SoundCategory.OTHERS) {
            populateObjectDropdown(this.selectedCategory);
            this.objectDropdown.clearSelection();
            this.objectDropdown.setActive(true);
        } else {
            this.objectDropdown.clearSelection();
            this.objectDropdown.setActive(false);
        }
        refreshList();
    }

    private void onObjectSelected(@Nullable String object) {
        this.selectedObject = object;
        refreshList();
    }

    // ── Filtragem ─────────────────────────────────────────────────────────────

    private void refreshList() {
        if (this.soundList == null) return;
        this.soundList.updateList(getFilteredSounds(), getFilteredBlocks(), detailedView);
    }

    private List<String> getFilteredSounds() {
        List<String> base = (selectedCategory != null)
                ? SoundRegistry.getByCategory(selectedCategory)
                : SoundRegistry.getAll();

        if (selectedObject != null) {
            String f = "." + selectedObject + ".";
            String s = "." + selectedObject;
            base = base.stream().filter(id -> {
                String p = id.contains(":") ? id.split(":")[1] : id;
                return p.contains(f) || p.endsWith(s);
            }).toList();
        }

        if (!searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase();
            base = base.stream().filter(id -> id.contains(q)).toList();
        }

        return new ArrayList<>(base.stream()
                .filter(s -> SoundCategory.fromPrefix(
                        SoundDisplayHelper.getCategoryPrefix(s)) != SoundCategory.HIDDEN)
                .filter(s -> !SoundCategory.isSilent(s))
                .toList());
    }

    private List<String> getFilteredBlocks() {
        List<String> candidates;
        if (selectedCategory == null || selectedCategory == SoundCategory.BLOCK) {
            candidates = MissingBlockRegistry.BLOCK_IDS;
        } else if (selectedCategory == SoundCategory.REDSTONE) {
            candidates = MissingBlockRegistry.BLOCK_IDS.stream()
                    .filter(MissingBlockRegistry.REDSTONE_BLOCK_IDS::contains).toList();
        } else {
            return List.of();
        }
        if (selectedObject != null) return List.of();
        if (!searchQuery.isBlank()) {
            String q = searchQuery.toLowerCase();
            candidates = candidates.stream()
                    .filter(id -> MissingBlockRegistry.getDisplayName(id).toLowerCase().contains(q)
                               || id.contains(q)).toList();
        }
        return candidates;
    }

    // ── Helpers de inicialização ──────────────────────────────────────────────

    private void populateCategoryDropdown() {
        List<String[]> pairs = new ArrayList<>();
        for (SoundCategory cat : SoundCategory.visibleCategories()) {
            pairs.add(new String[]{ cat.getDropdownKey(), I18n.get(cat.getLabelKey()) });
        }
        pairs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        List<String> options = new ArrayList<>(), labels = new ArrayList<>();
        for (String[] p : pairs) { options.add(p[0]); labels.add(p[1]); }
        this.categoryDropdown.setOptions(options, labels);
    }

    private void populateObjectDropdown(SoundCategory category) {
        List<String> raw    = new ArrayList<>(SoundRegistry.getObjectsByCategory(category));
        List<String> labels = new ArrayList<>();
        for (String obj : raw)
            labels.add(SoundDisplayHelper.getObjectName("minecraft:" + category.getPrefix() + "." + obj));
        // Ordenar por label — dígitos depois do Z (char '~' > 'Z' em ASCII)
        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) order.add(new int[]{i});
        order.sort((a, b) -> {
            String la = labels.get(a[0]), lb = labels.get(b[0]);
            String ka = (!la.isEmpty() && Character.isDigit(la.charAt(0))) ? "~" + la : la;
            String kb = (!lb.isEmpty() && Character.isDigit(lb.charAt(0))) ? "~" + lb : lb;
            return ka.compareToIgnoreCase(kb);
        });
        List<String> sortedRaw = new ArrayList<>(), sortedLabels = new ArrayList<>();
        for (int[] idx : order) { sortedRaw.add(raw.get(idx[0])); sortedLabels.add(labels.get(idx[0])); }
        this.objectDropdown.setOptions(sortedRaw, sortedLabels);
    }

    private void toggleMuteVisible() {
        muteSoundsActive = !muteSoundsActive;
        // Usa o mute layer volátil do VolumeResolver — prioridade absoluta sobre presets e
        // config base, sem corromper nenhuma configuração persistida.
        if (muteSoundsActive) {
            for (String id : getFilteredSounds()) VolumeResolver.muteSound(id);
            for (String id : getFilteredBlocks()) VolumeResolver.muteBlock(id);
        } else {
            for (String id : getFilteredSounds()) VolumeResolver.unmuteSound(id);
            for (String id : getFilteredBlocks()) VolumeResolver.unmuteBlock(id);
        }
        refreshList();
    }

    /**
     * Desenha um ícone de speaker pixel-art centrado num botão.
     * muted=false → speaker com ondas (sons activos); muted=true → speaker com X vermelho.
     */
    static void drawSpeakerIcon(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, boolean muted) {
        // Ícone: 12×10 pixels, centrado no botão
        int ox = bx + (bw - 12) / 2;
        int oy = by + (bh - 10) / 2;
        int col = 0xFFFFFFFF;

        // Cone do speaker (diamante a apontar para a direita)
        g.fill(ox+3, oy+0, ox+4, oy+1,  col);
        g.fill(ox+2, oy+1, ox+4, oy+2,  col);
        g.fill(ox+1, oy+2, ox+4, oy+3,  col);
        g.fill(ox+0, oy+3, ox+4, oy+4,  col);
        g.fill(ox+0, oy+4, ox+4, oy+5,  col);
        g.fill(ox+0, oy+5, ox+4, oy+6,  col);
        g.fill(ox+0, oy+6, ox+4, oy+7,  col);
        g.fill(ox+1, oy+7, ox+4, oy+8,  col);
        g.fill(ox+2, oy+8, ox+4, oy+9,  col);
        g.fill(ox+3, oy+9, ox+4, oy+10, col);

        if (!muted) {
            // Onda próxima (arco ])
            g.fill(ox+5, oy+2, ox+6, oy+3,  col);
            g.fill(ox+6, oy+3, ox+7, oy+7,  col);
            g.fill(ox+5, oy+7, ox+6, oy+8,  col);
            // Onda afastada (arco ] maior)
            g.fill(ox+7, oy+1, ox+8, oy+2,  col);
            g.fill(ox+8, oy+2, ox+9, oy+8,  col);
            g.fill(ox+7, oy+8, ox+8, oy+9,  col);
        } else {
            // X vermelho (muted)
            int r = 0xFFFF4444;
            g.fill(ox+5, oy+3, ox+6, oy+4,  r);
            g.fill(ox+8, oy+3, ox+9, oy+4,  r);
            g.fill(ox+6, oy+4, ox+7, oy+5,  r);
            g.fill(ox+7, oy+4, ox+8, oy+5,  r);
            g.fill(ox+6, oy+5, ox+7, oy+6,  r);
            g.fill(ox+7, oy+5, ox+8, oy+6,  r);
            g.fill(ox+5, oy+6, ox+6, oy+7,  r);
            g.fill(ox+8, oy+6, ox+9, oy+7,  r);
        }
    }

    private void clearFilters() {
        this.selectedCategory = null; this.selectedObject = null; this.searchQuery = "";
        this.searchBox.setValue("");
        savedCategory = null; savedObject = null; savedSearch = ""; savedScroll = 0.0;
        this.categoryDropdown.clearSelection();
        this.objectDropdown.clearSelection();
        this.objectDropdown.setActive(false);
        refreshList();
    }

    // ── Fechar ────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        savedCategory = this.selectedCategory;
        savedObject   = this.selectedObject;
        savedSearch   = this.searchQuery;
        savedScroll   = this.soundList != null ? this.soundList.getScrollAmount() : 0.0;
        this.minecraft.setScreen(this.parent);
    }
}
