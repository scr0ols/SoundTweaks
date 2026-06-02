package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ecrã de edição dos sons de um preset.
 * Mostra todos os sons com barra de pesquisa e filtro de categoria.
 * Sons com override ficam destacados; 100% = sem override = remove do preset.
 */
public class PresetEditorScreen extends Screen {

    // ── Estado persistente (partilhado entre aberturas do mesmo ecrã) ─────────
    @Nullable private static SoundCategory savedCategory = null;
    @Nullable private static String        savedObject   = null;
    private   static         String        savedSearch   = "";
    private   static         double        savedScroll   = 0.0;
    // Toggle Simple/Detail view — static para persistir entre aberturas
    static boolean detailedView = false;

    private final Screen parent;
    final PresetConfig.Preset preset;

    private SoundEntryList soundList;
    private EditBox        searchBox;
    private FilterDropdown categoryDropdown;
    private FilterDropdown objectDropdown;
    private Button         viewToggleBtn;

    @Nullable private SoundCategory selectedCategory = null;
    @Nullable private String        selectedObject   = null;
    private           String        searchQuery      = "";

    // Botão de silenciar/repor overrides visíveis
    private Button  mutePresetsBtn;
    private boolean mutePresetsActive = false;

    public PresetEditorScreen(Screen parent, PresetConfig.Preset preset) {
        super(Component.literal("Edit: " + preset.name));
        this.parent = parent;
        this.preset = preset;
    }

    @Override
    protected void init() {
        int listTop    = 70;
        int listBottom = this.height - 70;

        this.soundList = new SoundEntryList(this.minecraft, this.width, listBottom - listTop, listTop, 20);
        this.soundList.refresh();
        this.addRenderableWidget(this.soundList);

        // ── Linha de filtros (Y=28): [Category 120] [Object 130] [×] [Search preenche] [View 78]
        int fY  = 28;
        int fH  = 20;

        this.categoryDropdown = new FilterDropdown(4, fY, 120,
                I18n.get("soundtweaks.gui.category"), this::onCategorySelected);
        populateCategoryDropdown();

        this.objectDropdown = new FilterDropdown(128, fY, 130,
                I18n.get("soundtweaks.gui.object"), this::onObjectSelected);
        this.objectDropdown.setActive(false);

        var clearBtn = Button.builder(Component.literal("x"), btn -> clearFilters())
                .bounds(262, fY, 20, fH).build();
        clearBtn.setTooltip(Tooltip.create(Component.literal("Clear all filters")));
        this.addRenderableWidget(clearBtn);

        this.viewToggleBtn = Button.builder(
                Component.literal(detailedView ? "Detail View" : "Simple View"),
                btn -> {
                    detailedView = !detailedView;
                    btn.setMessage(Component.literal(detailedView ? "Detail View" : "Simple View"));
                    refreshList();
                }
        ).bounds(this.width - 82, fY, 78, fH).build();
        this.viewToggleBtn.setTooltip(Tooltip.create(Component.literal(
                "Simple View: grouped by sound event\n" +
                "Detail View: all individual sound files")));
        this.addRenderableWidget(this.viewToggleBtn);

        int searchX = 286;
        int searchW = Math.max(60, this.width - 82 - searchX - 4);
        this.searchBox = new EditBox(this.font, searchX, fY, searchW, fH,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setResponder(q -> { this.searchQuery = q; refreshList(); });
        this.addRenderableWidget(this.searchBox);

        // ── Footer ────────────────────────────────────────────────────────────
        int btnY   = this.height - 56;
        int totalW = 130 + 4 + 24 + 4 + 100;
        int startX = (this.width - totalW) / 2;

        var importBtn = Button.builder(
                Component.literal("Import from config"),
                btn -> importFromBase()
        ).bounds(startX, btnY, 130, 20).build();
        importBtn.setTooltip(Tooltip.create(Component.literal(
                "Copies all sounds/blocks with volume ≠ 100%\n" +
                "from the base config into this preset.\n" +
                "Useful to snapshot your current global settings.")));
        this.addRenderableWidget(importBtn);

        this.mutePresetsActive = false;
        this.mutePresetsBtn = Button.builder(Component.empty(), btn -> toggleMuteVisible())
                .bounds(startX + 134, btnY, 24, 20).build();
        this.mutePresetsBtn.setTooltip(Tooltip.create(Component.literal(
                "Mute / restore all currently visible sounds\n" +
                "in this preset (sets them to 0% / removes override).")));
        this.addRenderableWidget(this.mutePresetsBtn);

        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.gui.done"),
                btn -> this.onClose()
        ).bounds(startX + 162, btnY, 100, 20).build());

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
    }

    private void populateCategoryDropdown() {
        List<String[]> pairs = new ArrayList<>();
        for (SoundCategory cat : SoundCategory.visibleCategories()) {
            pairs.add(new String[]{ cat.getDropdownKey(), I18n.get(cat.getLabelKey()) });
        }
        pairs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        List<String> opts = new ArrayList<>(), labels = new ArrayList<>();
        for (String[] p : pairs) { opts.add(p[0]); labels.add(p[1]); }
        this.categoryDropdown.setOptions(opts, labels);
    }

    private void populateObjectDropdown(SoundCategory category) {
        List<String> raw    = new ArrayList<>(SoundRegistry.getObjectsByCategory(category));
        List<String> labels = new ArrayList<>();
        for (String obj : raw)
            labels.add(SoundDisplayHelper.getObjectName("minecraft:" + category.getPrefix() + "." + obj));
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

    private void clearFilters() {
        this.selectedCategory = null; this.selectedObject = null; this.searchQuery = "";
        this.searchBox.setValue("");
        savedCategory = null; savedObject = null; savedSearch = ""; savedScroll = 0.0;
        this.categoryDropdown.clearSelection();
        this.objectDropdown.clearSelection();
        this.objectDropdown.setActive(false);
        refreshList();
    }

    private void refreshList() {
        if (this.soundList == null) return;
        this.soundList.refresh(selectedCategory, selectedObject, searchQuery);
    }

    private void importFromBase() {
        VolumeConfig.SOUNDS.getAll().forEach((id, vol) -> { if (vol != 1.0f) preset.sounds.put(id, vol); });
        VolumeConfig.BLOCKS.getAll().forEach((id, vol) -> { if (vol != 1.0f) preset.blocks.put(id, vol); });
        PresetConfig.markDirty();
        refreshList();
    }

    private void toggleMuteVisible() {
        mutePresetsActive = !mutePresetsActive;
        for (var entry : soundList.children()) {
            if (entry instanceof SoundEntryList.SoundRow sr) {
                if (mutePresetsActive) preset.sounds.put(sr.soundId, 0.0f);
                else                  preset.sounds.remove(sr.soundId);
            } else if (entry instanceof SoundEntryList.BlockRow br) {
                if (mutePresetsActive) preset.blocks.put(br.blockId, 0.0f);
                else                  preset.blocks.remove(br.blockId);
            } else if (entry instanceof SoundEntryList.GroupRow gr) {
                // Simple View: mute/unmute todos os filhos do grupo
                for (String id : gr.childIds) {
                    if (mutePresetsActive) preset.sounds.put(id, 0.0f);
                    else                  preset.sounds.remove(id);
                }
            }
        }
        PresetConfig.markDirty();
        refreshList();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        int rgb = preset.argbColor() & 0x00FFFFFF;
        graphics.fill(0, 0, this.width, this.height, rgb | 0x30000000);
        // Cabeçalho: cor do preset mais intensa (canais +40, clampados) e mais transparente
        int rr = Math.min(0xFF, ((rgb >> 16) & 0xFF) + 40);
        int gg = Math.min(0xFF, ((rgb >>  8) & 0xFF) + 40);
        int bb = Math.min(0xFF, ( rgb        & 0xFF) + 40);
        graphics.fill(0, 0, this.width, 24, (rr << 16 | gg << 8 | bb) | 0x55000000);
        graphics.fill(0, 24, this.width, 25, 0xFF444466);
        graphics.fill(0, 66, this.width, 67, 0xFF333355);

        graphics.centeredText(this.font, "Editing: " + preset.name, this.width / 2, 8, 0xFFFFFFFF);

        graphics.fill(8, this.height - 72, this.width - 8, this.height - 71, 0xFF555555);
        int overrideCount = preset.sounds.size() + preset.blocks.size();
        String hint = overrideCount > 0
                ? overrideCount + " override(s) — orange = has override  |  100% = remove override"
                : "No overrides. Drag any slider below 100% to add an override.";
        graphics.text(this.font, hint, 8, this.height - 18, 0xFFAAAAAA);

        // Ícone de speaker no botão de silenciar/repor
        if (this.mutePresetsBtn != null)
            SoundTweaksScreen.drawSpeakerIcon(graphics,
                    this.mutePresetsBtn.getX(), this.mutePresetsBtn.getY(),
                    this.mutePresetsBtn.getWidth(), this.mutePresetsBtn.getHeight(),
                    mutePresetsActive);

        // Dropdowns — por último para ficarem sobre tudo
        this.categoryDropdown.render(graphics, mouseX, mouseY);
        this.objectDropdown.render(graphics, mouseX, mouseY);
    }

    // ── Eventos ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (this.categoryDropdown.mouseClicked(event)) {
            if (this.categoryDropdown.isOpen()) this.objectDropdown.close();
            return true;
        }
        if (this.objectDropdown.mouseClicked(event)) {
            if (this.objectDropdown.isOpen()) this.categoryDropdown.close();
            return true;
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.categoryDropdown.mouseDragged(event.y())) return true;
        if (this.objectDropdown.mouseDragged(event.y()))   return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.categoryDropdown.mouseReleased();
        this.objectDropdown.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (this.categoryDropdown.mouseScrolled(mx, my, sy)) return true;
        if (this.objectDropdown.mouseScrolled(mx, my, sy))   return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == 256) {
            if (this.categoryDropdown.isOpen()) { this.categoryDropdown.close(); return true; }
            if (this.objectDropdown.isOpen())   { this.objectDropdown.close();   return true; }
        }
        if (key >= 65 && key <= 90 && !this.searchBox.isFocused()) {
            char letter = (char) key;
            if (this.categoryDropdown.isOpen()) return this.categoryDropdown.jumpToLetter(letter);
            if (this.objectDropdown.isOpen())   return this.objectDropdown.jumpToLetter(letter);
            return this.soundList.jumpToLetter(letter);
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        savedCategory = this.selectedCategory;
        savedObject   = this.selectedObject;
        savedSearch   = this.searchQuery;
        savedScroll   = this.soundList != null ? this.soundList.getScrollAmount() : 0.0;
        this.minecraft.setScreen(parent);
    }

    // =========================================================================
    // Lista unificada sons + blocos
    // =========================================================================

    class SoundEntryList extends AbstractSelectionList<SoundEntryList.BaseRow> {

        private char   lastJumpLetter = 0;
        private int    lastJumpIndex  = -1;
        private double trackedScroll  = 0.0;

        @Override
        public void setScrollAmount(double amount) {
            super.setScrollAmount(amount);
            this.trackedScroll = amount;
        }

        public double getScrollAmount() { return trackedScroll; }

        public boolean jumpToLetter(char c) {
            char upper = Character.toUpperCase(c);
            var entries = this.children();
            List<Integer> matches = new ArrayList<>();
            for (int i = 0; i < entries.size(); i++) {
                String id = entries.get(i).getId();
                if (id.isEmpty()) continue;
                // Usar display name para jump
                String name;
                if (entries.get(i) instanceof GroupRow gr)        name = gr.groupName;
                else if (entries.get(i) instanceof SoundRow sr)   name = SoundDisplayHelper.getDisplayName(sr.soundId);
                else if (entries.get(i) instanceof BlockRow br)   name = MissingBlockRegistry.getDisplayName(br.blockId);
                else continue;
                if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == upper) matches.add(i);
            }
            if (matches.isEmpty()) return false;
            int nextPos;
            if (upper != lastJumpLetter) nextPos = 0;
            else { int cur = matches.indexOf(lastJumpIndex); nextPos = (cur + 1) % matches.size(); }
            int targetIdx = matches.get(nextPos);
            lastJumpLetter = upper;
            lastJumpIndex  = targetIdx;
            this.setSelected(entries.get(targetIdx));
            this.setScrollAmount((double) targetIdx * this.entryHeight);
            return true;
        }

        abstract class BaseRow extends AbstractSelectionList.Entry<BaseRow> {
            abstract String  getId();
            abstract boolean hasOverride();
            public void updateNarration(NarrationElementOutput o) {}
        }

        private final int entryHeight;

        public SoundEntryList(net.minecraft.client.Minecraft mc,
                              int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
            this.entryHeight = itemHeight;
        }

        public void refresh() { refresh(null, null, ""); }

        public void refresh(@Nullable SoundCategory category, @Nullable String object, String query) {
            this.clearEntries();
            this.lastJumpLetter = 0;
            this.lastJumpIndex  = -1;

            // ── Sons ──────────────────────────────────────────────────────────
            List<String> sounds = new ArrayList<>(
                    category != null
                            ? SoundRegistry.getByCategory(category)
                            : SoundRegistry.getAll());

            if (object != null) {
                String f = "." + object + ".";
                String s = "." + object;
                sounds = sounds.stream().filter(id -> {
                    String p = id.contains(":") ? id.split(":")[1] : id;
                    return p.contains(f) || p.endsWith(s);
                }).toList();
            }

            if (!query.isBlank()) {
                String q = query.toLowerCase();
                sounds = sounds.stream().filter(id ->
                        id.contains(q) || SoundDisplayHelper.getDisplayName(id).toLowerCase().contains(q)
                ).toList();
            }

            sounds = sounds.stream()
                    .filter(id -> !SoundCategory.isSilent(id))
                    .filter(id -> SoundCategory.fromPrefix(SoundDisplayHelper.getCategoryPrefix(id)) != SoundCategory.HIDDEN)
                    .sorted((a, b) -> {
                        String ka = SoundDisplayHelper.getDisplayName(a).toLowerCase();
                        String kb = SoundDisplayHelper.getDisplayName(b).toLowerCase();
                        boolean aD = !ka.isEmpty() && Character.isDigit(ka.charAt(0));
                        boolean bD = !kb.isEmpty() && Character.isDigit(kb.charAt(0));
                        if (aD != bD) return aD ? 1 : -1;
                        return ka.compareTo(kb);
                    })
                    .toList();

            // ── Blocos (MissingBlockRegistry) ─────────────────────────────────
            boolean showBlocks = (category == null || category == SoundCategory.BLOCK
                    || category == SoundCategory.REDSTONE) && object == null;

            List<String> blocks = new ArrayList<>();
            if (showBlocks) {
                blocks = new ArrayList<>(
                        category == SoundCategory.REDSTONE
                                ? MissingBlockRegistry.BLOCK_IDS.stream()
                                    .filter(MissingBlockRegistry.REDSTONE_BLOCK_IDS::contains).toList()
                                : MissingBlockRegistry.BLOCK_IDS);

                if (!query.isBlank()) {
                    String q = query.toLowerCase();
                    blocks = blocks.stream().filter(id ->
                            MissingBlockRegistry.getDisplayName(id).toLowerCase().contains(q)
                            || id.contains(q)).toList();
                }
            }

            if (!detailedView) {
                // ── Simple View: agrupa sons por evento (igual à SoundListWidget) ──
                Map<String, List<String>> groupMap = SoundRegistry.getGroups(sounds);
                Set<String> groupedIds = new HashSet<>();
                groupMap.values().forEach(groupedIds::addAll);

                // Recolher entradas: [sortKey, entry]
                List<Object[]> slots = new ArrayList<>();
                for (Map.Entry<String, List<String>> e : groupMap.entrySet()) {
                    String displayName = SoundDisplayHelper.getObjectName("x:" + e.getKey() + ".x");
                    slots.add(new Object[]{ displayName.toLowerCase(), new GroupRow(e.getKey(), e.getValue()) });
                }
                for (String id : sounds) {
                    if (!groupedIds.contains(id)) {
                        String dn = SoundDisplayHelper.getDisplayName(id).toLowerCase();
                        slots.add(new Object[]{ dn, new SoundRow(id) });
                    }
                }
                slots.sort((a, b) -> {
                    String ka = (String) a[0], kb = (String) b[0];
                    boolean aD = !ka.isEmpty() && Character.isDigit(ka.charAt(0));
                    boolean bD = !kb.isEmpty() && Character.isDigit(kb.charAt(0));
                    if (aD != bD) return aD ? 1 : -1;
                    return ka.compareTo(kb);
                });
                for (Object[] slot : slots) this.addEntry((BaseRow) slot[1]);
            } else {
                // ── Detail View: todos os sons individualmente ─────────────────
                for (String id : sounds) this.addEntry(new SoundRow(id));
            }

            // Blocos: sempre individuais (sem grupos)
            for (String id : blocks) this.addEntry(new BlockRow(id));
        }

        @Override public int getRowWidth() { return Math.min(500, this.width - 20); }
        @Override protected int scrollBarX() { return this.getX() + this.width / 2 + getRowWidth() / 2 + 4; }
        @Override public void updateWidgetNarration(NarrationElementOutput o) {}

        // ── Linha de grupo (Simple View) ──────────────────────────────────────

        class GroupRow extends BaseRow {
            final List<String> childIds;
            private final String groupName;
            private final PresetGroupSliderButton slider;
            private long lastClick = 0;

            GroupRow(String groupKey, List<String> childIds) {
                this.childIds  = childIds;
                this.groupName = SoundDisplayHelper.getObjectName("x:" + groupKey + ".x");
                this.slider    = new PresetGroupSliderButton(preset, childIds, 0, 0, 90, 14);
            }

            @Override public String  getId()       { return "group:" + groupName; }
            @Override public boolean hasOverride() { return childIds.stream().anyMatch(id -> preset.sounds.containsKey(id)); }

            private float minChildVol() {
                float min = 1.0f;
                for (String id : childIds) { Float v = preset.sounds.get(id); if (v != null) min = Math.min(min, v); }
                return min;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float a) {
                int rowW = SoundEntryList.this.getRowWidth();
                if (hasOverride())                                  g.fill(getX(), getY(), getX()+rowW, getY()+20, 0x33FFAA44);
                else if (SoundEntryList.this.getSelected() == this) g.fill(getX(), getY(), getX()+rowW, getY()+20, 0x44AAAAFF);
                else                                                g.fill(getX(), getY(), getX()+rowW, getY()+20, 0x22AAAAFF);

                float vol = minChildVol();
                int col = vol <= 0f ? 0xFFFF4444 : hasOverride() ? 0xFFFFCC88 : 0xFFAAAAAA;
                g.text(SoundEntryList.this.minecraft.font, "* " + groupName, getX()+4, getY()+5, col);
                slider.setX(getX()+rowW-94); slider.setY(getY()+3);
                slider.extractRenderState(g, mx, my, a);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent e, boolean consumed) {
                if (consumed) return false;
                SoundEntryList.this.setSelected(this);
                long now = Util.getMillis();
                if (now - lastClick < 300) { slider.setSliderValue(minChildVol() >= 1f ? 0f : 1f); return true; }
                lastClick = now;
                return slider.mouseClicked(e, consumed);
            }
            @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return slider.mouseDragged(e,dx,dy); }
            @Override public boolean mouseReleased(MouseButtonEvent e) { return slider.mouseReleased(e); }
        }

        // ── Linha de som ──────────────────────────────────────────────────────

        class SoundRow extends BaseRow {
            final String soundId;
            private final PresetSoundSliderButton slider;
            private long lastClick = 0;

            SoundRow(String id) {
                this.soundId = id;
                this.slider  = new PresetSoundSliderButton(preset, id, 0, 0, 90, 14);
            }

            @Override public String  getId()         { return soundId; }
            @Override public boolean hasOverride()   { return preset.sounds.containsKey(soundId); }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float a) {
                int rowW = SoundEntryList.this.getRowWidth();
                if (hasOverride()) g.fill(getX(), getY(), getX()+rowW, getY()+20, 0x33FFAA44);
                else if (SoundEntryList.this.getSelected()==this) g.fill(getX(),getY(),getX()+rowW,getY()+20,0x44FFFFFF);

                float vol  = preset.sounds.getOrDefault(soundId, 1.0f);
                int   col  = vol <= 0f ? 0xFFFF4444 : hasOverride() ? 0xFFFFCC88 : 0xFFAAAAAA;
                g.text(SoundEntryList.this.minecraft.font, SoundDisplayHelper.getDisplayName(soundId), getX()+4, getY()+5, col);
                slider.setX(getX()+rowW-94); slider.setY(getY()+3);
                slider.extractRenderState(g, mx, my, a);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent e, boolean consumed) {
                if (consumed) return false;
                SoundEntryList.this.setSelected(this);
                long now = Util.getMillis();
                if (now - lastClick < 300) { slider.setSliderValue(preset.sounds.getOrDefault(soundId,1f)>=1f?0f:1f); return true; }
                lastClick = now;
                return slider.mouseClicked(e, consumed);
            }
            @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return slider.mouseDragged(e,dx,dy); }
            @Override public boolean mouseReleased(MouseButtonEvent e) { return slider.mouseReleased(e); }
        }

        // ── Linha de bloco ────────────────────────────────────────────────────

        class BlockRow extends BaseRow {
            final String blockId;
            private final PresetBlockSliderButton slider;
            private long lastClick = 0;

            BlockRow(String id) {
                this.blockId = id;
                this.slider  = new PresetBlockSliderButton(preset, id, 0, 0, 90, 14);
            }

            @Override public String  getId()       { return blockId; }
            @Override public boolean hasOverride() { return preset.blocks.containsKey(blockId); }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float a) {
                int rowW = SoundEntryList.this.getRowWidth();
                if (hasOverride()) g.fill(getX(), getY(), getX()+rowW, getY()+20, 0x33FFAA44);
                else if (SoundEntryList.this.getSelected()==this) g.fill(getX(),getY(),getX()+rowW,getY()+20,0x44FFFFFF);

                float vol = preset.blocks.getOrDefault(blockId, 1.0f);
                int   col = vol<=0f ? 0xFFFF4444 : hasOverride() ? 0xFFFFCC88 : 0xFFAAAAAA;
                String name = MissingBlockRegistry.getDisplayName(blockId) + " [block]";
                g.text(SoundEntryList.this.minecraft.font, name, getX()+4, getY()+5, col);
                slider.setX(getX()+rowW-94); slider.setY(getY()+3);
                slider.extractRenderState(g, mx, my, a);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent e, boolean consumed) {
                if (consumed) return false;
                SoundEntryList.this.setSelected(this);
                long now = Util.getMillis();
                if (now - lastClick < 300) { slider.setSliderValue(preset.blocks.getOrDefault(blockId,1f)>=1f?0f:1f); return true; }
                lastClick = now;
                return slider.mouseClicked(e, consumed);
            }
            @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return slider.mouseDragged(e,dx,dy); }
            @Override public boolean mouseReleased(MouseButtonEvent e) { return slider.mouseReleased(e); }
        }
    }
}
