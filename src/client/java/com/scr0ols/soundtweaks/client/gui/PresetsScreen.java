package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Ecrã de gestão de presets — layout master-detail.
 * Esquerda: lista simplificada. Direita: painel de configuração inline.
 * Tabs: Color | Rename | Shortcut | Edit Sounds | Delete
 */
public class PresetsScreen extends Screen {

    private final Screen parent;
    private PresetListWidget presetList;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int LIST_W          = 330;
    private static final int LIST_W_CENTERED = 400;
    private static final int PANEL_HDR_H     = 28;
    private static final int TAB_H           = 20;
    private static final int CONTENT_Y       = 56;

    // Filtros do painel Sounds — posicionados logo abaixo dos tabs
    private static final int SOUNDS_FILTER_Y = 56;  // HDR + TAB + 8
    private static final int SOUNDS_LIST_Y   = 82;  // SOUNDS_FILTER_Y + 20 + 6

    private int panelX() { return LIST_W + 1; }
    private int panelW() { return this.width - LIST_W - 1; }

    // Botões do footer (guardados para rebuildLayout)
    private Button newPresetBtn, doneBtn, importPresetsBtn, openConfigBtn;

    // ── Create overlay ────────────────────────────────────────────────────────
    private boolean creating = false;
    private EditBox createBox;
    private Button  createConfirmBtn, createCancelBtn;

    // ── Painel de detalhe ─────────────────────────────────────────────────────
    private enum EditMode { NONE, COLOR, RENAME, SHORTCUT, SOUNDS }
    private EditMode editMode = EditMode.NONE;
    @Nullable private PresetConfig.Preset editingPreset = null;

    // Tabs: Color | Rename | Shortcut | Edit Sounds | Delete
    private static final String[] TAB_LABELS = {"Color", "Rename", "Shortcut", "Edit Sounds", "Delete"};
    private static final int[]    TAB_W      = {64,       64,       72,          88,             60};
    private static final EditMode[] TAB_MODES = {EditMode.COLOR, EditMode.RENAME, EditMode.SHORTCUT, EditMode.SOUNDS, null};

    // Widgets de rename
    private EditBox renameBox;
    private Button  renameConfirmBtn, renameCancelBtn;

    // Color hex EditBox
    private EditBox colorHexBox;

    // Captura de atalho
    private final LinkedHashSet<Integer> captureHeldKeys   = new LinkedHashSet<>();
    private final List<Integer>          lastHeldAtTrigger = new ArrayList<>();
    private int lastCapturedTrigger = 0;

    // Painel de sons (SOUNDS mode)
    @Nullable private PresetSoundList soundsWidget = null;
    private FilterDropdown soundsCatDrop;
    private FilterDropdown soundsObjDrop;
    private EditBox        soundsSearch;
    private Button         soundsClear, soundsViewToggle, soundsImport, soundsMute;
    @Nullable private SoundCategory soundsCat = null;
    @Nullable private String        soundsObj = null;
    private           String        soundsQuery = "";

    // ── Construtor ────────────────────────────────────────────────────────────

    public PresetsScreen(Screen parent) {
        super(Component.translatable("soundtweaks.presets.title"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int listTop    = PANEL_HDR_H;
        int listBottom = this.height - 56;

        // presetList será criada por rebuildLayout() no final do init

        // ── Footer ───────────────────────────────────────────────────────────
        this.newPresetBtn = Button.builder(
                Component.translatable("soundtweaks.presets.new"), btn -> enterCreateMode()
        ).bounds(4, this.height - 50, LIST_W - 8, 20).build();
        this.addRenderableWidget(this.newPresetBtn);

        this.doneBtn = Button.builder(
                Component.translatable("soundtweaks.gui.done"), btn -> this.onClose()
        ).bounds(panelX() + panelW() / 2 - 60, this.height - 50, 120, 20).build();
        this.addRenderableWidget(this.doneBtn);

        this.importPresetsBtn = Button.builder(
                Component.literal("Import Presets..."),
                btn -> {
                    String selected;
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        PointerBuffer filters = stack.mallocPointer(1);
                        filters.put(stack.UTF8("*.json")).flip();
                        selected = TinyFileDialogs.tinyfd_openFileDialog(
                                "Select soundtweaks_presets.json", "", filters,
                                "JSON preset file (*.json)", false);
                    }
                    if (selected == null) return;
                    int result = PresetConfig.importFrom(java.nio.file.Path.of(selected));
                    if (result >= 0) presetList.refresh();
                }
        ).bounds(4, this.height - 26, LIST_W / 2 - 6, 20).build();
        this.importPresetsBtn.setTooltip(Tooltip.create(Component.literal(
                "Import presets from a shared file.\nA new ID is always generated — no conflicts.")));
        this.addRenderableWidget(this.importPresetsBtn);

        this.openConfigBtn = Button.builder(
                Component.literal("Open Config Folder"), btn -> ConfigFileUtil.openConfigFolder()
        ).bounds(LIST_W / 2 + 2, this.height - 26, LIST_W / 2 - 6, 20).build();
        this.addRenderableWidget(this.openConfigBtn);

        rebuildLayout();

        // ── Create overlay ────────────────────────────────────────────────────
        int cx = this.width / 2 - 130, cy = this.height / 2 - 22;
        this.createBox = new EditBox(this.font, cx, cy, 220, 20, Component.empty());
        this.createBox.setHint(Component.translatable("soundtweaks.presets.name_hint"));
        this.createBox.setMaxLength(64);
        this.createBox.visible = false;
        this.addRenderableWidget(this.createBox);

        this.createConfirmBtn = Button.builder(
                Component.translatable("soundtweaks.presets.confirm"), btn -> confirmCreate()
        ).bounds(cx, cy + 24, 106, 18).build();
        this.createConfirmBtn.visible = false;
        this.addRenderableWidget(this.createConfirmBtn);

        this.createCancelBtn = Button.builder(Component.literal("Cancel"),
                btn -> exitCreateMode()
        ).bounds(cx + 110, cy + 24, 106, 18).build();
        this.createCancelBtn.visible = false;
        this.addRenderableWidget(this.createCancelBtn);

        // ── Rename widgets (painel direito) ──────────────────────────────────
        int renW = Math.min(panelW() - 80, 320);
        int renX = panelX() + (panelW() - renW) / 2;
        int renY = CONTENT_Y + 18;
        int renBtnW = renW / 2 - 2;
        this.renameBox = new EditBox(this.font, renX, renY, renW, 20, Component.empty());
        this.renameBox.setMaxLength(64);
        this.renameBox.visible = false;
        this.addRenderableWidget(this.renameBox);

        this.renameConfirmBtn = Button.builder(Component.literal("Save name"),
                btn -> confirmRename()).bounds(renX, renY + 26, renBtnW, 20).build();
        this.renameConfirmBtn.visible = false;
        this.addRenderableWidget(this.renameConfirmBtn);

        this.renameCancelBtn = Button.builder(Component.literal("Clear"),
                btn -> { renameBox.setValue(""); this.setFocused(renameBox); renameBox.setFocused(true); }
        ).bounds(renX + renBtnW + 4, renY + 26, renBtnW, 20).build();
        this.renameCancelBtn.visible = false;
        this.addRenderableWidget(this.renameCancelBtn);

        // ── Color hex EditBox ─────────────────────────────────────────────────
        int cgridW = 6 * 22 + 5 * 3;
        int cgridX = panelX() + panelW() / 2 - cgridW / 2;
        int gridY   = CONTENT_Y + 20;
        int customY = gridY + 3 * (22 + 3) + 12;
        this.colorHexBox = new EditBox(this.font, cgridX + 26, customY, 110, 18, Component.empty());
        this.colorHexBox.setMaxLength(6);
        this.colorHexBox.setTextColor(0xFFFFFFFF);
        this.colorHexBox.visible = false;
        this.colorHexBox.setResponder(hex -> {
            if (editingPreset == null) return;
            try {
                int rgb = Integer.parseUnsignedInt(hex.trim(), 16);
                editingPreset.customColor = 0xFF000000 | (rgb & 0xFFFFFF);
                PresetConfig.markDirty();
            } catch (NumberFormatException ignored) {}
        });
        this.addRenderableWidget(this.colorHexBox);

        // ── Widgets do painel de sons (SOUNDS mode) ───────────────────────────
        initSoundsWidgets();
        // rebuildLayout() já foi chamado acima (após os botões footer)
    }

    private void initSoundsWidgets() {
        int px = panelX(), pw = panelW();
        int fy = SOUNDS_FILTER_Y, fh = 20;

        this.soundsCatDrop = new FilterDropdown(px + 4, fy, 100,
                I18n.get("soundtweaks.gui.category"), this::onSoundsCategorySelected);
        populateSoundsCategoryDropdown();

        this.soundsObjDrop = new FilterDropdown(px + 108, fy, 100,
                I18n.get("soundtweaks.gui.object"), this::onSoundsObjectSelected);
        this.soundsObjDrop.setActive(false);

        this.soundsClear = Button.builder(Component.literal("x"), btn -> clearSoundsFilters())
                .bounds(px + 212, fy, 18, fh).build();
        this.soundsClear.visible = false;
        this.addRenderableWidget(this.soundsClear);

        // Mute no header, à esquerda do viewToggle
        this.soundsMute = Button.builder(Component.empty(), btn -> {
            if (soundsWidget != null) {
                soundsWidget.toggleMute();
                refreshSoundsList();
            }
        }).bounds(px + pw - 110, fy, 24, fh).build();
        this.soundsMute.setTooltip(Tooltip.create(Component.literal(
                "Mute / restore all visible sounds in this preset.")));
        this.soundsMute.visible = false;
        this.addRenderableWidget(this.soundsMute);

        this.soundsViewToggle = Button.builder(
                Component.literal(PresetSoundList.detailedView ? "Detail View" : "Simple View"),
                btn -> {
                    PresetSoundList.detailedView = !PresetSoundList.detailedView;
                    btn.setMessage(Component.literal(PresetSoundList.detailedView ? "Detail View" : "Simple View"));
                    refreshSoundsList();
                }
        ).bounds(px + pw - 82, fy, 78, fh).build();
        this.soundsViewToggle.visible = false;
        this.addRenderableWidget(this.soundsViewToggle);

        // Search box mais curta para dar espaço ao botão mute
        int searchX = px + 234, searchW = Math.max(40, pw - 352);
        this.soundsSearch = new EditBox(this.font, searchX, fy, searchW, fh,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setResponder(q -> { this.soundsQuery = q; refreshSoundsList(); });
        this.soundsSearch.visible = false;
        this.addRenderableWidget(this.soundsSearch);

        // Import no footer, à esquerda do Done (doneBtn fica em panelX+panelW/2-60)
        int importX = px + pw / 2 - 117;
        this.soundsImport = Button.builder(Component.literal("Import from config"), btn -> {
            if (editingPreset == null) return;
            VolumeConfig.SOUNDS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.sounds.put(id, vol); });
            VolumeConfig.BLOCKS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.blocks.put(id, vol); });
            PresetConfig.markDirty();
            refreshSoundsList();
        }).bounds(importX, this.height - 26, 110, 20).build();
        this.soundsImport.setTooltip(Tooltip.create(Component.literal(
                "Copies all sounds/blocks with volume ≠ 100%\nfrom the base config into this preset.")));
        this.soundsImport.visible = false;
        this.addRenderableWidget(this.soundsImport);
    }

    private void rebuildLayout() {
        boolean centered = (editingPreset == null);
        int listTop    = PANEL_HDR_H;
        int listHeight = this.height - 58 - listTop;

        // Recriar a lista com as dimensões correctas
        // (setWidth/setX do AbstractSelectionList não actualiza o clip interno)
        if (presetList != null) this.removeWidget(presetList);
        if (centered) {
            int lw = Math.min(LIST_W_CENTERED, this.width - 40);
            int lx = (this.width - lw) / 2;
            presetList = new PresetListWidget(this.minecraft, lw, listHeight, listTop, 24);
            presetList.setX(lx);
            // Linha 1: New Preset (2/3) | Done (1/3)
            int newW = lw - 134;
            newPresetBtn.setX(lx + 4);             newPresetBtn.setWidth(newW);
            doneBtn.setX(lx + newW + 8);           doneBtn.setWidth(lw - newW - 16);
            newPresetBtn.setY(this.height - 50);   doneBtn.setY(this.height - 50);
            // Linha 2: Import | Open Config
            importPresetsBtn.setX(lx + 4);         importPresetsBtn.setWidth(lw / 2 - 6);  importPresetsBtn.setHeight(20);
            openConfigBtn.setX(lx + lw / 2 + 2);  openConfigBtn.setWidth(lw / 2 - 6);    openConfigBtn.setHeight(20);
            importPresetsBtn.setY(this.height - 26); openConfigBtn.setY(this.height - 26);
        } else {
            presetList = new PresetListWidget(this.minecraft, LIST_W, listHeight, listTop, 24);
            // Linha 1: New Preset (esquerda) | Done (direita, no painel)
            newPresetBtn.setX(4);                  newPresetBtn.setWidth(LIST_W - 8);
            doneBtn.setX(panelX() + panelW() / 2 - 60); doneBtn.setWidth(120);
            newPresetBtn.setY(this.height - 50);   doneBtn.setY(this.height - 26);
            // Linha 2: Import | Open Config (esquerda)
            importPresetsBtn.setX(4);              importPresetsBtn.setWidth(LIST_W / 2 - 6);  importPresetsBtn.setHeight(20);
            openConfigBtn.setX(LIST_W / 2 + 2);   openConfigBtn.setWidth(LIST_W / 2 - 6);    openConfigBtn.setHeight(20);
            importPresetsBtn.setY(this.height - 26); openConfigBtn.setY(this.height - 26);
        }
        this.addRenderableWidget(presetList);
        presetList.refresh();
    }

    private void showSoundsWidgets(boolean visible) {
        soundsClear.visible      = visible;
        soundsViewToggle.visible = visible;
        soundsSearch.visible     = visible;
        soundsMute.visible       = visible;
        soundsImport.visible     = visible;
        if (soundsWidget != null) soundsWidget.visible = visible;
    }

    private void rebuildSoundsWidget() {
        if (editingPreset == null) return;
        if (soundsWidget != null) this.removeWidget(soundsWidget);
        int px = panelX(), pw = panelW();
        int listH = this.height - 58 - SOUNDS_LIST_Y;
        soundsWidget = new PresetSoundList(this.minecraft, editingPreset,
                pw, listH, SOUNDS_LIST_Y, 22);
        soundsWidget.setX(px);
        soundsWidget.refresh(soundsCat, soundsObj, soundsQuery);
        this.addRenderableWidget(soundsWidget);
        soundsWidget.visible = true;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        setCreateWidgetsVisible(false);
        setRenameWidgetsVisible(false);
        colorHexBox.visible = false;
        showSoundsWidgets(editingPreset != null && editMode == EditMode.SOUNDS);

        super.extractRenderState(g, mouseX, mouseY, a);

        if (creating) setCreateWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.RENAME) setRenameWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.COLOR
                && editingPreset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX)
            colorHexBox.visible = true;

        // ── Separador de footer — começa no divisor vertical quando painel aberto
        int footerSepX = (editingPreset != null) ? LIST_W + 1 : 8;
        g.fill(footerSepX, this.height - 58, this.width - 8, this.height - 57, 0xFF111111);
        g.fill(footerSepX, this.height - 57, this.width - 8, this.height - 56, 0xFF555555);

        // ── Título ────────────────────────────────────────────────────────────
        if (editingPreset != null) {
            g.centeredText(this.font, I18n.get("soundtweaks.presets.title"), LIST_W / 2, 10, 0xFFFFFFFF);
            // ── Divisor ───────────────────────────────────────────────────────
            // ── Painel direito ────────────────────────────────────────────────
            renderDetailPanel(g, mouseX, mouseY, a);
        } else {
            int lw = LIST_W_CENTERED;
            int lx = (this.width - lw) / 2;
            g.centeredText(this.font, I18n.get("soundtweaks.presets.title"), this.width / 2, 8, 0xFFFFFFFF);
        }

        // Dropdowns de sons por cima de tudo
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            soundsCatDrop.render(g, mouseX, mouseY);
            soundsObjDrop.render(g, mouseX, mouseY);
        }

        // ── Create overlay ────────────────────────────────────────────────────
        if (creating) {
            g.fill(0, 0, this.width, this.height, 0xBB000000);
            int cx = this.width / 2 - 130, cy = this.height / 2 - 22;
            g.fill(cx - 10, cy - 26, cx + 232, cy + 46, 0xFF1A1A2E);
            g.fill(cx - 10, cy - 26, cx + 232, cy - 25, 0xFF444466); // topo
            g.fill(cx - 10, cy + 45, cx + 232, cy + 46, 0xFF444466); // baixo
            g.fill(cx - 10, cy - 26, cx - 9,   cy + 46, 0xFF444466); // esquerda
            g.fill(cx + 231, cy - 26, cx + 232, cy + 46, 0xFF444466); // direita
            g.text(this.font, "New preset name:", cx, cy - 18, 0xFFCCCCFF);
            createBox.extractRenderState(g, mouseX, mouseY, a);
            createConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
            createCancelBtn.extractRenderState(g, mouseX, mouseY, a);
        }

    }

    // ── Painel de detalhe ─────────────────────────────────────────────────────

    private void renderDetailPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int px = panelX(), pw = panelW(), cx2 = px + pw / 2;

        if (editingPreset == null) {
            g.centeredText(this.font, "Select a preset to configure", cx2, this.height / 2 - 8, 0xFF555566);
            g.centeredText(this.font, "or create a new one below", cx2, this.height / 2 + 8, 0xFF444455);
            return;
        }

        int pc = editingPreset.argbColor() & 0x00FFFFFF;
        int maxTitleW = panelW() - 20;
        String titleText = editingPreset.name;
        while (titleText.length() > 1 && this.font.width(titleText) > maxTitleW)
            titleText = titleText.substring(0, titleText.length() - 1);
        if (!titleText.equals(editingPreset.name)) titleText += "..";
        int r = (pc >> 16) & 0xFF, gc = (pc >> 8) & 0xFF, bc = pc & 0xFF;
        int lum = (r * 299 + gc * 587 + bc * 114) / 1000;
        if (lum < 100) {
            // clarear a cor do texto proporcionalmente à sua escuridão
            float boost = (1f - lum / 100f) * 0.6f;
            r  = (int)(r  + (255 - r)  * boost);
            gc = (int)(gc + (255 - gc) * boost);
            bc = (int)(bc + (255 - bc) * boost);
            pc = (r << 16) | (gc << 8) | bc;
        }
        g.centeredText(this.font, titleText, cx2 + 1, 11, 0xCC000000);
        g.centeredText(this.font, titleText, cx2,     10, pc | 0xFF000000);
        g.fill(px, PANEL_HDR_H - 2, this.width, PANEL_HDR_H - 1, 0xFF444466); // azul/cinza
        g.fill(px, PANEL_HDR_H - 1, this.width, PANEL_HDR_H,     0xFF111111); // preto

        renderTabs(g, mouseX, mouseY, px, pc);

        switch (editMode) {
            case COLOR    -> renderColorContent(g, mouseX, mouseY, px, pw, editingPreset, a);
            case RENAME   -> renderRenameContent(g, mouseX, mouseY, a);
            case SHORTCUT -> renderShortcutContent(g, cx2);
            case SOUNDS   -> renderSoundsHint(g, cx2);
            default       -> {}
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, int px, int pc) {
        int tabX = px + 4, tabY = PANEL_HDR_H;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            boolean isDelete = (i == TAB_LABELS.length - 1);
            boolean active   = !isDelete && (editMode == TAB_MODES[i]);
            boolean hov      = mouseX >= tabX && mouseX < tabX + TAB_W[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_H;

            int bg, accent, textCol;
            if (isDelete) {
                bg      = hov ? 0xFF331111 : 0xFF221111;
                accent  = 0xFF664444;
                textCol = hov ? 0xFFFF6666 : 0xFFAA4444;
            } else {
                bg      = active ? 0xFF2A2A3A : hov ? 0xFF2A2A44 : 0xFF222233;
                accent  = active ? (pc | 0xFF000000) : 0xFF444466;
                textCol = active ? 0xFFFFFFFF : hov ? 0xFFCCCCCC : 0xFF888899;
            }

            g.fill(tabX, tabY, tabX + TAB_W[i], tabY + TAB_H, bg);
            g.fill(tabX, tabY, tabX + TAB_W[i], tabY + 1, accent);
            g.centeredText(this.font, TAB_LABELS[i], tabX + TAB_W[i] / 2, tabY + 6, textCol);
            tabX += TAB_W[i] + 4;
        }
    }

    private void renderColorContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                    int px, int pw, PresetConfig.Preset preset, float a) {
        int cx2 = px + pw / 2;
        g.fill(cx2 - 170, CONTENT_Y + 2, cx2 + 170, CONTENT_Y + 140, 0xBB1A1A1A);
        int sq = 22, gap = 3, cols = 6;
        int gridW = cols * sq + (cols - 1) * gap;
        int gridX = px + pw / 2 - gridW / 2;
        int gridY = CONTENT_Y + 20;

        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % cols, row = i / cols;
            int qx = gridX + col * (sq + gap), qy = gridY + row * (sq + gap);
            g.fill(qx, qy, qx + sq, qy + sq, PresetConfig.PRESET_COLORS[i] | 0xFF000000);
            boolean selected = (i == preset.colorIndex);
            boolean hov = mouseX >= qx && mouseX < qx + sq && mouseY >= qy && mouseY < qy + sq;
            if (selected) {
                g.fill(qx-2, qy-2, qx+sq+2, qy,       0xFFFFFFFF); g.fill(qx-2, qy+sq, qx+sq+2, qy+sq+2, 0xFFFFFFFF);
                g.fill(qx-2, qy,   qx,       qy+sq,    0xFFFFFFFF); g.fill(qx+sq, qy,   qx+sq+2, qy+sq,   0xFFFFFFFF);
            } else if (hov) {
                g.fill(qx-1, qy-1, qx+sq+1, qy,       0xFF888888); g.fill(qx-1, qy+sq, qx+sq+1, qy+sq+1, 0xFF888888);
                g.fill(qx-1, qy,   qx,       qy+sq,    0xFF888888); g.fill(qx+sq, qy,   qx+sq+1, qy+sq,   0xFF888888);
            }
        }
        int customY = gridY + 3 * (sq + gap) + 12;
        boolean customSel = (preset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX);
        boolean customHov = mouseX >= gridX && mouseX < gridX + sq && mouseY >= customY && mouseY < customY + sq;
        if (preset.customColor != 0) {
            g.fill(gridX, customY, gridX + sq, customY + sq, preset.customColor | 0xFF000000);
        } else {
            g.fill(gridX, customY, gridX + sq, customY + sq, 0xFF1A1A2E);
            g.fill(gridX, customY, gridX+sq, customY+1, 0xFF556677); g.fill(gridX, customY+sq-1, gridX+sq, customY+sq, 0xFF556677);
            g.fill(gridX, customY, gridX+1, customY+sq, 0xFF556677); g.fill(gridX+sq-1, customY, gridX+sq, customY+sq, 0xFF556677);
            if (!customSel) g.centeredText(this.font, "+", gridX + sq / 2, customY + (sq - 8) / 2, 0xFF556677);
        }
        if (customSel) {
            g.fill(gridX-2, customY-2, gridX+sq+2, customY, 0xFFFFFFFF); g.fill(gridX-2, customY+sq, gridX+sq+2, customY+sq+2, 0xFFFFFFFF);
            g.fill(gridX-2, customY, gridX, customY+sq, 0xFFFFFFFF);      g.fill(gridX+sq, customY, gridX+sq+2, customY+sq, 0xFFFFFFFF);
        } else if (customHov) {
            g.fill(gridX-1, customY-1, gridX+sq+1, customY, 0xFF888888); g.fill(gridX-1, customY+sq, gridX+sq+1, customY+sq+1, 0xFF888888);
            g.fill(gridX-1, customY, gridX, customY+sq, 0xFF888888);      g.fill(gridX+sq, customY, gridX+sq+1, customY+sq, 0xFF888888);
        }
        g.text(this.font, "Custom", gridX + sq + 6, customY + (sq - 8) / 2, customSel ? 0xFFCCCCFF : 0xFF666688);
        if (customSel) colorHexBox.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderRenameContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        renameBox.extractRenderState(g, mouseX, mouseY, a);
        renameConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
        renameCancelBtn.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderShortcutContent(GuiGraphicsExtractor g, int cx) {
        g.fill(cx - 170, CONTENT_Y + 2, cx + 170, CONTENT_Y + 66, 0xBB1A1A1A);

        String captureLabel;
        if (lastCapturedTrigger != 0) {
            StringBuilder sb = new StringBuilder();
            for (int k : lastHeldAtTrigger) sb.append(rawKeyName(k)).append(" + ");
            sb.append(rawKeyName(lastCapturedTrigger));
            captureLabel = sb.toString();
        } else { captureLabel = "---"; }
        g.centeredText(this.font, captureLabel, cx, CONTENT_Y + 14, lastCapturedTrigger != 0 ? 0xFF88FF88 : 0xFF666677);
        String savedLabel = (editingPreset != null) ? keyDisplayLabel(editingPreset) : "---";
        boolean hasSaved = !savedLabel.equals("---");
        g.centeredText(this.font, hasSaved ? "[" + savedLabel + "]" : "[blank]", cx, CONTENT_Y + 34, hasSaved ? 0xFFCCCCFF : 0xFF888899);
        g.centeredText(this.font, "ENTER to confirm  ·  BACKSPACE to clear  ·  ESC to cancel", cx, CONTENT_Y + 50, 0xFF888899);
    }

    private void renderSoundsHint(GuiGraphicsExtractor g, int cx) {
        if (soundsMute != null && soundsMute.visible && soundsWidget != null)
            SoundTweaksScreen.drawSpeakerIcon(g, soundsMute.getX(), soundsMute.getY(),
                    soundsMute.getWidth(), soundsMute.getHeight(), soundsWidget.isMuteActive());
    }

    // ── Visibilidade de widgets ───────────────────────────────────────────────

    private void setCreateWidgetsVisible(boolean v) {
        createBox.visible = v; createConfirmBtn.visible = v; createCancelBtn.visible = v;
    }

    private void setRenameWidgetsVisible(boolean v) {
        renameBox.visible = v; renameConfirmBtn.visible = v; renameCancelBtn.visible = v;
    }

    // ── Sons — filtros e lista ────────────────────────────────────────────────

    private void populateSoundsCategoryDropdown() {
        List<String[]> pairs = new ArrayList<>();
        for (SoundCategory cat : SoundCategory.visibleCategories())
            pairs.add(new String[]{ cat.getDropdownKey(), I18n.get(cat.getLabelKey()) });
        pairs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        List<String> opts = new ArrayList<>(), labels = new ArrayList<>();
        for (String[] p : pairs) { opts.add(p[0]); labels.add(p[1]); }
        this.soundsCatDrop.setOptions(opts, labels);
    }

    private void populateSoundsObjectDropdown(SoundCategory category) {
        List<String> raw = new ArrayList<>(SoundRegistry.getObjectsByCategory(category));
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
        this.soundsObjDrop.setOptions(sortedRaw, sortedLabels);
    }

    private void onSoundsCategorySelected(@Nullable String key) {
        this.soundsCat = SoundCategory.fromDropdownKey(key);
        this.soundsObj = null;
        if (this.soundsCat != null && this.soundsCat != SoundCategory.OTHERS) {
            populateSoundsObjectDropdown(this.soundsCat);
            this.soundsObjDrop.clearSelection(); this.soundsObjDrop.setActive(true);
        } else {
            this.soundsObjDrop.clearSelection(); this.soundsObjDrop.setActive(false);
        }
        refreshSoundsList();
    }

    private void onSoundsObjectSelected(@Nullable String obj) {
        this.soundsObj = obj; refreshSoundsList();
    }

    private void clearSoundsFilters() {
        soundsCat = null; soundsObj = null; soundsQuery = "";
        soundsSearch.setValue("");
        soundsCatDrop.clearSelection();
        soundsObjDrop.clearSelection(); soundsObjDrop.setActive(false);
        refreshSoundsList();
    }

    private void refreshSoundsList() {
        if (soundsWidget != null) soundsWidget.refresh(soundsCat, soundsObj, soundsQuery);
    }

    // ── Teclado ───────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (editingPreset != null) {
            if (editMode == EditMode.SHORTCUT) { handleShortcutKey(key); return true; }
            if (editMode == EditMode.RENAME) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirmRename(); return true; }
                if (key == GLFW.GLFW_KEY_ESCAPE) { setEditMode(EditMode.COLOR); return true; }
                return super.keyPressed(event);
            }
            if (editMode == EditMode.COLOR && this.getFocused() == colorHexBox) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                    this.setFocused(null); return true;
                }
                return super.keyPressed(event);
            }
            if (editMode == EditMode.SOUNDS) {
                if (key == 256) {
                    if (soundsCatDrop.isOpen()) { soundsCatDrop.close(); return true; }
                    if (soundsObjDrop.isOpen()) { soundsObjDrop.close(); return true; }
                }
                return super.keyPressed(event);
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { closeDetailPanel(); return true; }
            return true;
        }

        if (creating) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { confirmCreate(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE) { exitCreateMode(); return true; }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (editingPreset != null && editMode == EditMode.SHORTCUT) {
            captureHeldKeys.remove(event.key()); return true;
        }
        return super.keyReleased(event);
    }

    private void handleShortcutKey(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { resetShortcutCapture(); setEditMode(EditMode.COLOR); return; }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            resetShortcutCapture();
            if (editingPreset != null) {
                editingPreset.shortcutKey = 0; editingPreset.shortcutHeldKey = 0; editingPreset.shortcutHeldKey2 = 0;
                PresetConfig.markDirty();
            }
            presetList.refresh(); return;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { if (lastCapturedTrigger != 0) confirmShortcut(); return; }
        lastHeldAtTrigger.clear(); lastHeldAtTrigger.addAll(captureHeldKeys);
        while (lastHeldAtTrigger.size() > 2) lastHeldAtTrigger.remove(0);
        lastCapturedTrigger = key; captureHeldKeys.add(key);
    }

    private void confirmShortcut() {
        if (editingPreset == null) return;
        int h1 = 0, h2 = 0;
        if (lastHeldAtTrigger.size() == 1) h1 = lastHeldAtTrigger.get(0);
        else if (lastHeldAtTrigger.size() >= 2) { h1 = lastHeldAtTrigger.get(lastHeldAtTrigger.size() - 2); h2 = lastHeldAtTrigger.get(lastHeldAtTrigger.size() - 1); }
        editingPreset.shortcutKey = lastCapturedTrigger & 0xFFFF;
        editingPreset.shortcutHeldKey = h1; editingPreset.shortcutHeldKey2 = h2;
        PresetConfig.markDirty(); resetShortcutCapture(); presetList.refresh();
    }

    private void resetShortcutCapture() {
        captureHeldKeys.clear(); lastHeldAtTrigger.clear(); lastCapturedTrigger = 0;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        double mx = event.x(), my = event.y();

        if (creating) {
            if (createConfirmBtn.mouseClicked(event, consumed)) return true;
            if (createCancelBtn.mouseClicked(event, consumed))  return true;
            if (mx >= createBox.getX() && mx < createBox.getX() + createBox.getWidth()
                    && my >= createBox.getY() && my < createBox.getY() + createBox.getHeight()) {
                this.setFocused(createBox); createBox.setFocused(true); createBox.mouseClicked(event, consumed);
            }
            return true;
        }

        // Dropdowns de sons — prioridade quando SOUNDS mode
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            if (soundsCatDrop.mouseClicked(event)) { if (soundsCatDrop.isOpen()) soundsObjDrop.close(); return true; }
            if (soundsObjDrop.mouseClicked(event)) { if (soundsObjDrop.isOpen()) soundsCatDrop.close(); return true; }
        }

        if (super.mouseClicked(event, consumed)) return true;

        // Painel direito — áreas manuais
        if (editingPreset != null && mx >= panelX()) {
            int px = panelX();

            // Tabs
            int tabX = px + 4, tabY = PANEL_HDR_H;
            for (int i = 0; i < TAB_LABELS.length; i++) {
                if (mx >= tabX && mx < tabX + TAB_W[i] && my >= tabY && my < tabY + TAB_H) {
                    handleTabClick(i); return true;
                }
                tabX += TAB_W[i] + 4;
            }

            // Conteúdo por modo
            if (editMode == EditMode.COLOR) {
                if (colorHexBox.visible
                        && mx >= colorHexBox.getX() && mx < colorHexBox.getX() + colorHexBox.getWidth()
                        && my >= colorHexBox.getY() && my < colorHexBox.getY() + colorHexBox.getHeight()) {
                    this.setFocused(colorHexBox); colorHexBox.setFocused(true);
                    colorHexBox.mouseClicked(event, false); return true;
                }
                handleColorGridClick(mx, my, px, panelW(), editingPreset);
            } else if (editMode == EditMode.RENAME) {
                if (renameConfirmBtn.mouseClicked(event, false)) return true;
                if (renameCancelBtn.mouseClicked(event, false))  return true;
                if (mx >= renameBox.getX() && mx < renameBox.getX() + renameBox.getWidth()
                        && my >= renameBox.getY() && my < renameBox.getY() + renameBox.getHeight()) {
                    this.setFocused(renameBox); renameBox.setFocused(true); renameBox.mouseClicked(event, false);
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            if (soundsCatDrop.mouseDragged(event.y())) return true;
            if (soundsObjDrop.mouseDragged(event.y())) return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            soundsCatDrop.mouseReleased(); soundsObjDrop.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            if (soundsCatDrop.mouseScrolled(mx, my, sy)) return true;
            if (soundsObjDrop.mouseScrolled(mx, my, sy)) return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void handleTabClick(int tabIndex) {
        if (tabIndex == TAB_LABELS.length - 1) {
            openDeleteConfirm();
        } else {
            setEditMode(TAB_MODES[tabIndex]);
        }
    }

    private void openDeleteConfirm() {
        if (editingPreset == null) return;
        PresetConfig.Preset toDelete = editingPreset;
        this.minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    PresetConfig.deletePreset(toDelete.name);
                    closeDetailPanel();
                }
                this.minecraft.setScreen(PresetsScreen.this);
            },
            Component.literal("Delete preset?"),
            Component.literal("\"" + toDelete.name + "\" — This action cannot be undone.")
        ));
    }

    private void handleColorGridClick(double mx, double my, int px, int pw, PresetConfig.Preset preset) {
        if (preset == null) return;
        int sq = 22, gap = 3, cols = 6;
        int gridW = cols * sq + (cols - 1) * gap;
        int gridX = px + pw / 2 - gridW / 2;
        int gridY = CONTENT_Y + 20;
        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % cols, row = i / cols;
            int qx = gridX + col * (sq + gap), qy = gridY + row * (sq + gap);
            if (mx >= qx && mx < qx + sq && my >= qy && my < qy + sq) {
                preset.colorIndex = i; PresetConfig.markDirty(); this.setFocused(null); return;
            }
        }
        int customY = gridY + 3 * (sq + gap) + 12;
        if (mx >= gridX && mx < gridX + sq && my >= customY && my < customY + sq) {
            preset.colorIndex = PresetConfig.CUSTOM_COLOR_INDEX;
            if (preset.customColor == 0) preset.customColor = 0xFF888888;
            PresetConfig.markDirty();
            colorHexBox.setValue(String.format("%06X", preset.customColor & 0xFFFFFF));
            colorHexBox.visible = true; this.setFocused(colorHexBox); colorHexBox.setFocused(true);
        }
    }

    // ── Painel: abrir / fechar / trocar modo ──────────────────────────────────

    void openEditOverlay(PresetConfig.Preset preset) {
        this.editingPreset = preset;
        setEditMode(EditMode.COLOR);
        rebuildLayout();
    }

    private void setEditMode(EditMode mode) {
        this.editMode = mode;
        setRenameWidgetsVisible(false);

        if (mode == EditMode.RENAME && editingPreset != null) {
            renameBox.setValue(editingPreset.name);
            setRenameWidgetsVisible(true);
            this.setFocused(renameBox); renameBox.setFocused(true);
            doneBtn.setX(panelX() + panelW() / 2 - 60);
        } else if (mode == EditMode.SOUNDS) {
            // Desloca Done para a direita para ficar lado a lado com Import from config
            doneBtn.setX(panelX() + panelW() / 2 - 3);
            rebuildSoundsWidget();
            this.setFocused(null);
        } else {
            doneBtn.setX(panelX() + panelW() / 2 - 60);
            this.setFocused(null);
        }

        if (mode == EditMode.SHORTCUT) resetShortcutCapture();
    }

    private void closeDetailPanel() {
        if (soundsWidget != null) { this.removeWidget(soundsWidget); soundsWidget = null; }
        this.editingPreset = null; this.editMode = EditMode.NONE;
        setRenameWidgetsVisible(false); this.setFocused(null);
        if (presetList != null) presetList.setSelected(null);
        presetList.refresh();
        rebuildLayout();
    }

    // ── Criar preset ──────────────────────────────────────────────────────────

    private void enterCreateMode() {
        creating = true; createBox.setValue("");
        setCreateWidgetsVisible(true); this.setFocused(createBox); createBox.setFocused(true);
    }

    private void confirmCreate() {
        String name = createBox.getValue().trim();
        if (!name.isEmpty()) {
            PresetConfig.createFromCurrentConfig(name);
            presetList.refresh();
            List<PresetConfig.Preset> all = PresetConfig.getPresets();
            if (!all.isEmpty()) openEditOverlay(all.get(all.size() - 1));
        }
        exitCreateMode();
    }

    private void exitCreateMode() {
        creating = false; setCreateWidgetsVisible(false); this.setFocused(null);
    }

    private void confirmRename() {
        if (editingPreset != null) {
            String name = renameBox.getValue().trim();
            if (!name.isEmpty()) { PresetConfig.renamePreset(editingPreset.name, name); presetList.refresh(); }
        }
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    // =========================================================================
    // Lista de presets (painel esquerdo)
    // =========================================================================

    class PresetListWidget extends AbstractSelectionList<PresetListWidget.PresetRow> {

        public PresetListWidget(net.minecraft.client.Minecraft mc,
                                int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        public void refresh() {
            this.clearEntries();
            for (PresetConfig.Preset preset : PresetConfig.getPresets())
                this.addEntry(new PresetRow(preset));
        }

        @Override public int getRowWidth() { return this.width - 20; }
        @Override protected int scrollBarX() { return this.getX() + this.width - 6; }
        @Override public void updateWidgetNarration(NarrationElementOutput o) {}

        class PresetRow extends AbstractSelectionList.Entry<PresetRow> {

            private final PresetConfig.Preset preset;
            PresetRow(PresetConfig.Preset preset) { this.preset = preset; }

            private int rowW()  { return PresetListWidget.this.getRowWidth(); }
            private int starX() { return getX() + rowW() - 22; }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                boolean active   = PresetConfig.isActive(preset.name);
                boolean fav      = PresetConfig.isFavorite(preset.name);
                boolean selected = (PresetsScreen.this.editingPreset == preset);
                int rW = rowW(), pc = preset.argbColor();

                int rowBg = selected ? 0xFF383838 : hovered ? 0xFF282828 : 0xFF222222;
                g.fill(getX(), getY(), getX() + rW, getY() + 24, rowBg);
                g.fill(getX(), getY() + 23, getX() + rW, getY() + 24, 0xFF111111);
                g.fill(getX(), getY(), getX() + (selected || active ? 6 : 4), getY() + 23, pc | 0xFF000000);

                int badgeX = getX() + 10, badgeY = getY() + 6;
                g.fill(badgeX-1, badgeY-1, badgeX+23, badgeY+12, active ? 0xFF336633 : 0xFF444444);
                g.fill(badgeX, badgeY, badgeX+22, badgeY+11, active ? 0xFF1A3A1A : 0xFF2A2A2A);
                g.centeredText(PresetListWidget.this.minecraft.font, active ? "ON" : "OFF", badgeX+11, badgeY+2, active ? 0xFF55FF55 : 0xFF888888);

                int sq = 12, sqX = getX() + 38, sqY = getY() + 6;
                g.fill(sqX-1, sqY-1, sqX+sq+1, sqY+sq+1, 0xFF000000);
                g.fill(sqX, sqY, sqX+sq, sqY+sq, pc | 0xFF000000);

                int nameCol = selected ? 0xFFFFFFFF : active ? 0xFFDDDDDD : 0xFF999999;
                g.text(PresetListWidget.this.minecraft.font, preset.name, getX()+58, getY()+8, nameCol);
                String sc = keyDisplayLabel(preset);
                if (!sc.equals("---"))
                    g.text(PresetListWidget.this.minecraft.font, " [" + sc + "]",
                            getX()+58+PresetListWidget.this.minecraft.font.width(preset.name), getY()+8, 0xFF556655);

                int sx = starX();
                boolean hovStar = mouseX >= sx && mouseX < sx+18 && mouseY >= getY()+4 && mouseY < getY()+20;
                g.fill(sx-1, getY()+3, sx+19, getY()+21, 0xFF111111);
                g.fill(sx, getY()+4, sx+18, getY()+20, hovStar ? 0xFF4A4A4A : 0xFF3A3A3A);
                g.centeredText(PresetListWidget.this.minecraft.font, fav ? "★" : "☆", sx+9, getY()+7, fav ? 0xFFFFDD44 : 0xFF777777);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
                if (consumed) return false;
                if (PresetsScreen.this.creating) return false;
                double mx = event.x(), my = event.y();

                int sx = starX();
                if (mx >= sx && mx < sx+18 && my >= getY()+4 && my < getY()+20) {
                    PresetConfig.setFavorite(preset.name, !PresetConfig.isFavorite(preset.name)); return true;
                }
                int badgeX = getX()+10, badgeY = getY()+6;
                if (mx >= badgeX && mx < badgeX+22 && my >= badgeY && my < badgeY+11) {
                    PresetConfig.setActive(preset.name, !PresetConfig.isActive(preset.name)); return true;
                }
                if (PresetsScreen.this.editingPreset == preset) {
                    PresetListWidget.this.setSelected(null);
                    PresetsScreen.this.closeDetailPanel();
                } else {
                    PresetListWidget.this.setSelected(this);
                    PresetsScreen.this.openEditOverlay(preset);
                }
                return true;
            }

            @Override public boolean mouseDragged(MouseButtonEvent e, double dX, double dY) { return false; }
            @Override public boolean mouseReleased(MouseButtonEvent e) { return false; }
            public void updateNarration(NarrationElementOutput o) {}
        }
    }

    // ── Utilitário: teclas ────────────────────────────────────────────────────

    static String keyDisplayLabel(PresetConfig.Preset preset) {
        if (preset.shortcutKey <= 0 && preset.shortcutHeldKey <= 0) return "---";
        if (preset.shortcutHeldKey != 0) {
            String s = rawKeyName(preset.shortcutHeldKey);
            if (preset.shortcutHeldKey2 != 0) s += "+" + rawKeyName(preset.shortcutHeldKey2);
            return s + "+" + rawKeyName(preset.shortcutKey & 0xFFFF);
        }
        return rawKeyName(preset.shortcutKey & 0xFFFF);
    }

    static String rawKeyName(int glfwKey) {
        if (glfwKey <= 0) return "---";
        String s = GLFW.glfwGetKeyName(glfwKey, 0);
        if (s != null && !s.isBlank()) return s.toUpperCase();
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_F1  -> "F1";   case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";   case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";   case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";   case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";   case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";  case GLFW.GLFW_KEY_F12 -> "F12";
            case GLFW.GLFW_KEY_UP    -> "UP";    case GLFW.GLFW_KEY_DOWN  -> "DOWN";
            case GLFW.GLFW_KEY_LEFT  -> "LEFT";  case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_INSERT -> "INS";  case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_HOME   -> "HOME"; case GLFW.GLFW_KEY_END    -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp"; case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_SPACE      -> "Space"; case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_KP_ENTER   -> "Num Enter";
            case GLFW.GLFW_KEY_TAB        -> "Tab";  case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            case GLFW.GLFW_KEY_ESCAPE     -> "Esc";  case GLFW.GLFW_KEY_BACKSPACE -> "Bksp";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Print"; case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num Lock"; case GLFW.GLFW_KEY_SCROLL_LOCK -> "Scroll";
            case GLFW.GLFW_KEY_LEFT_SHIFT,  GLFW.GLFW_KEY_RIGHT_SHIFT   -> "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT,    GLFW.GLFW_KEY_RIGHT_ALT     -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SUPER,  GLFW.GLFW_KEY_RIGHT_SUPER   -> "Super";
            case GLFW.GLFW_KEY_KP_0 -> "Num0"; case GLFW.GLFW_KEY_KP_1 -> "Num1";
            case GLFW.GLFW_KEY_KP_2 -> "Num2"; case GLFW.GLFW_KEY_KP_3 -> "Num3";
            case GLFW.GLFW_KEY_KP_4 -> "Num4"; case GLFW.GLFW_KEY_KP_5 -> "Num5";
            case GLFW.GLFW_KEY_KP_6 -> "Num6"; case GLFW.GLFW_KEY_KP_7 -> "Num7";
            case GLFW.GLFW_KEY_KP_8 -> "Num8"; case GLFW.GLFW_KEY_KP_9 -> "Num9";
            case GLFW.GLFW_KEY_KP_ADD -> "Num+"; case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num-";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num*"; case GLFW.GLFW_KEY_KP_DIVIDE -> "Num/";
            case GLFW.GLFW_KEY_KP_DECIMAL  -> "Num.";
            default -> "Key" + glfwKey;
        };
    }
}
