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
    private static final int LIST_W      = 330;
    private static final int PANEL_HDR_H = 28;
    private static final int TAB_H       = 20;
    private static final int CONTENT_Y   = 56;   // HDR + TAB + 8

    // Filtros do painel Sounds — posicionados logo abaixo dos tabs
    private static final int SOUNDS_FILTER_Y = 50;  // HDR + TAB + 2
    private static final int SOUNDS_LIST_Y   = 72;  // SOUNDS_FILTER_Y + 22

    private int panelX() { return LIST_W + 1; }
    private int panelW() { return this.width - LIST_W - 1; }

    // ── Create overlay ────────────────────────────────────────────────────────
    private boolean creating = false;
    private EditBox createBox;
    private Button  createConfirmBtn, createCancelBtn;

    // ── Painel de detalhe ─────────────────────────────────────────────────────
    private enum EditMode { NONE, COLOR, RENAME, SHORTCUT, SOUNDS, DELETE }
    private EditMode editMode = EditMode.NONE;
    @Nullable private PresetConfig.Preset editingPreset = null;

    // Tabs: Color | Rename | Shortcut | Edit Sounds | Delete
    private static final String[] TAB_LABELS = {"Color", "Rename", "Shortcut", "Edit Sounds", "Delete"};
    private static final int[]    TAB_W      = {64,       64,       72,          88,             60};
    private static final EditMode[] TAB_MODES = {EditMode.COLOR, EditMode.RENAME, EditMode.SHORTCUT, EditMode.SOUNDS, EditMode.DELETE};

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

        this.presetList = new PresetListWidget(this.minecraft, LIST_W,
                listBottom - listTop, listTop, 24);
        this.addRenderableWidget(this.presetList);
        this.presetList.refresh();

        // ── Footer ───────────────────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.presets.new"), btn -> enterCreateMode()
        ).bounds(4, this.height - 50, LIST_W - 8, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.gui.done"), btn -> this.onClose()
        ).bounds(panelX() + panelW() / 2 - 60, this.height - 50, 120, 20).build());

        var importPresetsBtn = Button.builder(
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
        ).bounds(4, this.height - 26, LIST_W / 2 - 6, 18).build();
        importPresetsBtn.setTooltip(Tooltip.create(Component.literal(
                "Import presets from a shared file.\nA new ID is always generated — no conflicts.")));
        this.addRenderableWidget(importPresetsBtn);

        this.addRenderableWidget(Button.builder(
                Component.literal("Open Config Folder"), btn -> ConfigFileUtil.openConfigFolder()
        ).bounds(LIST_W / 2 + 2, this.height - 26, LIST_W / 2 - 6, 18).build());

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
        int renX = panelX() + 40, renY = CONTENT_Y + 18;
        this.renameBox = new EditBox(this.font, renX, renY, panelW() - 80, 20, Component.empty());
        this.renameBox.setMaxLength(64);
        this.renameBox.visible = false;
        this.addRenderableWidget(this.renameBox);

        this.renameConfirmBtn = Button.builder(Component.literal("Save name"),
                btn -> confirmRename()).bounds(renX, renY + 26, 115, 18).build();
        this.renameConfirmBtn.visible = false;
        this.addRenderableWidget(this.renameConfirmBtn);

        this.renameCancelBtn = Button.builder(Component.literal("Clear"),
                btn -> { renameBox.setValue(""); this.setFocused(renameBox); renameBox.setFocused(true); }
        ).bounds(renX + 125, renY + 26, 115, 18).build();
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

        int searchX = px + 234, searchW = Math.max(40, px + pw - 82 - searchX - 4);
        this.soundsSearch = new EditBox(this.font, searchX, fy, searchW, fh,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setResponder(q -> { this.soundsQuery = q; refreshSoundsList(); });
        this.soundsSearch.visible = false;
        this.addRenderableWidget(this.soundsSearch);

        // Mute e Import no footer do painel de sons
        int sfooterY = this.height - 75;
        this.soundsMute = Button.builder(Component.empty(), btn -> {
            if (soundsWidget != null) {
                soundsWidget.toggleMute();
                refreshSoundsList();
            }
        }).bounds(px + pw / 2 - 14, sfooterY, 24, 20).build();
        this.soundsMute.setTooltip(Tooltip.create(Component.literal(
                "Mute / restore all visible sounds in this preset.")));
        this.soundsMute.visible = false;
        this.addRenderableWidget(this.soundsMute);

        this.soundsImport = Button.builder(Component.literal("Import from config"), btn -> {
            if (editingPreset == null) return;
            VolumeConfig.SOUNDS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.sounds.put(id, vol); });
            VolumeConfig.BLOCKS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.blocks.put(id, vol); });
            PresetConfig.markDirty();
            refreshSoundsList();
        }).bounds(px + pw / 2 - 130, sfooterY, 110, 20).build();
        this.soundsImport.setTooltip(Tooltip.create(Component.literal(
                "Copies all sounds/blocks with volume ≠ 100%\nfrom the base config into this preset.")));
        this.soundsImport.visible = false;
        this.addRenderableWidget(this.soundsImport);
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
        int listH = this.height - 56 - SOUNDS_LIST_Y - 26; // deixa espaço para footer de sons
        soundsWidget = new PresetSoundList(this.minecraft, editingPreset,
                pw, listH, SOUNDS_LIST_Y, 20);
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
        showSoundsWidgets(false);

        super.extractRenderState(g, mouseX, mouseY, a);

        if (creating) setCreateWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.RENAME) setRenameWidgetsVisible(true);
        if (editingPreset != null && editMode == EditMode.COLOR
                && editingPreset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX)
            colorHexBox.visible = true;
        if (editingPreset != null && editMode == EditMode.SOUNDS) showSoundsWidgets(true);

        // ── Painel esquerdo ───────────────────────────────────────────────────
        g.centeredText(this.font, I18n.get("soundtweaks.presets.title"), LIST_W / 2, 10, 0xFFFFFFFF);
        g.fill(4, PANEL_HDR_H - 2, LIST_W - 4, PANEL_HDR_H - 1, 0xFF555555);

        // ── Divisor ───────────────────────────────────────────────────────────
        g.fill(LIST_W, 0, LIST_W + 1, this.height - 30, 0xFF111111);
        g.fill(4, this.height - 54, this.width - 4, this.height - 53, 0xFF555555);

        // ── Painel direito ────────────────────────────────────────────────────
        renderDetailPanel(g, mouseX, mouseY, a);

        // Dropdowns de sons por cima de tudo
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            soundsCatDrop.render(g, mouseX, mouseY);
            soundsObjDrop.render(g, mouseX, mouseY);
        }

        // ── Create overlay ────────────────────────────────────────────────────
        if (creating) {
            g.fill(0, 0, this.width, this.height, 0xBB000000);
            int cx = this.width / 2 - 130, cy = this.height / 2 - 22;
            g.fill(cx - 10, cy - 26, cx + 232, cy + 46, 0xFF222233);
            g.fill(cx - 10, cy - 26, cx + 232, cy - 25, 0xFF444466);
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
        g.fill(px, 0, this.width, PANEL_HDR_H, pc | 0x99000000);
        g.centeredText(this.font, editingPreset.name, cx2, 10, 0xFFFFFFFF);
        g.fill(px, PANEL_HDR_H - 1, this.width, PANEL_HDR_H, 0xFF333344);

        renderTabs(g, mouseX, mouseY, px);
        g.fill(px + 4, PANEL_HDR_H + TAB_H + 2, this.width - 4, PANEL_HDR_H + TAB_H + 3, 0xFF333355);

        switch (editMode) {
            case COLOR    -> renderColorContent(g, mouseX, mouseY, px, pw, editingPreset, a);
            case RENAME   -> renderRenameContent(g, mouseX, mouseY, a);
            case SHORTCUT -> renderShortcutContent(g, cx2);
            case SOUNDS   -> renderSoundsHint(g, cx2);
            case DELETE   -> renderDeleteContent(g, mouseX, mouseY, cx2);
            default       -> {}
        }
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY, int px) {
        int tabX = px + 4, tabY = PANEL_HDR_H;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            boolean active  = (editMode == TAB_MODES[i]);
            boolean isDelete = (i == TAB_LABELS.length - 1);
            boolean hov     = mouseX >= tabX && mouseX < tabX + TAB_W[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_H;

            int bg, accent, textCol;
            if (isDelete) {
                bg      = active ? 0xFF442222 : hov ? 0xFF331111 : 0xFF221111;
                accent  = active ? 0xFFFF4444 : 0xFF664444;
                textCol = active ? 0xFFFF8888 : hov ? 0xFFFF6666 : 0xFFAA4444;
            } else {
                bg      = active ? 0xFF334466 : hov ? 0xFF2A2A44 : 0xFF222233;
                accent  = active ? 0xFF8888FF : 0xFF444466;
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
        String captureLabel;
        if (lastCapturedTrigger != 0) {
            StringBuilder sb = new StringBuilder();
            for (int k : lastHeldAtTrigger) sb.append(rawKeyName(k)).append(" + ");
            sb.append(rawKeyName(lastCapturedTrigger));
            captureLabel = sb.toString();
        } else { captureLabel = "---"; }
        g.centeredText(this.font, captureLabel, cx, CONTENT_Y + 14, lastCapturedTrigger != 0 ? 0xFF88FF88 : 0xFF555555);
        String savedLabel = (editingPreset != null) ? keyDisplayLabel(editingPreset) : "---";
        boolean hasSaved = !savedLabel.equals("---");
        g.centeredText(this.font, hasSaved ? "[" + savedLabel + "]" : "[blank]", cx, CONTENT_Y + 34, hasSaved ? 0xFFAABBAA : 0xFF666666);
        g.centeredText(this.font, "ENTER to confirm  ·  BACKSPACE to clear  ·  ESC to cancel", cx, CONTENT_Y + 50, 0xFF777777);
    }

    private void renderSoundsHint(GuiGraphicsExtractor g, int cx) {
        // O conteúdo principal é a lista de sons (widget), aqui apenas mostramos o footer de contagem
        if (editingPreset == null) return;
        int overrideCount = editingPreset.sounds.size() + editingPreset.blocks.size();
        String hint = overrideCount > 0
                ? overrideCount + " override(s) — orange = has override  |  100% = remove override"
                : "No overrides yet.";
        g.centeredText(this.font, hint, cx, this.height - 82, 0xFFAAAAAA);
        // Speaker icon no botão mute
        if (soundsMute != null && soundsMute.visible && soundsWidget != null)
            SoundTweaksScreen.drawSpeakerIcon(g, soundsMute.getX(), soundsMute.getY(),
                    soundsMute.getWidth(), soundsMute.getHeight(), soundsWidget.isMuteActive());
    }

    private void renderDeleteContent(GuiGraphicsExtractor g, int mouseX, int mouseY, int cx) {
        if (editingPreset == null) return;
        g.centeredText(this.font, "Delete this preset?", cx, CONTENT_Y + 16, 0xFFFFAAAA);
        g.centeredText(this.font, "\"" + editingPreset.name + "\"", cx, CONTENT_Y + 30, 0xFFFFFFFF);
        g.centeredText(this.font, "This action cannot be undone.", cx, CONTENT_Y + 44, 0xFF888888);

        // [Cancel] [Delete]
        int btnY = CONTENT_Y + 62;
        int cancelX = cx - 106, confirmX = cx + 6;
        boolean hovCancel  = mouseX >= cancelX  && mouseX < cancelX  + 100 && mouseY >= btnY && mouseY < btnY + 20;
        boolean hovConfirm = mouseX >= confirmX && mouseX < confirmX + 100 && mouseY >= btnY && mouseY < btnY + 20;

        g.fill(cancelX - 1, btnY - 1, cancelX + 101, btnY + 21, 0xFF111111);
        g.fill(cancelX, btnY, cancelX + 100, btnY + 20, hovCancel ? 0xFF3A3A3A : 0xFF2A2A2A);
        g.centeredText(this.font, "Cancel", cancelX + 50, btnY + 6, hovCancel ? 0xFFFFFFFF : 0xFFCCCCCC);

        g.fill(confirmX - 1, btnY - 1, confirmX + 101, btnY + 21, 0xFF111111);
        g.fill(confirmX, btnY, confirmX + 100, btnY + 20, hovConfirm ? 0xFF4A1A1A : 0xFF3A1010);
        g.centeredText(this.font, "Delete", confirmX + 50, btnY + 6, hovConfirm ? 0xFFFF8888 : 0xFFCC4444);
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
            } else if (editMode == EditMode.DELETE) {
                int cx2 = px + panelW() / 2;
                int btnY = CONTENT_Y + 62;
                int cancelX = cx2 - 106, confirmX = cx2 + 6;
                if (my >= btnY && my < btnY + 20) {
                    if (mx >= cancelX && mx < cancelX + 100) {
                        setEditMode(EditMode.COLOR); return true;
                    }
                    if (mx >= confirmX && mx < confirmX + 100) {
                        PresetConfig.deletePreset(editingPreset.name);
                        closeDetailPanel(); return true;
                    }
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
        setEditMode(TAB_MODES[tabIndex]);
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
    }

    private void setEditMode(EditMode mode) {
        this.editMode = mode;
        setRenameWidgetsVisible(false);

        if (mode == EditMode.RENAME && editingPreset != null) {
            renameBox.setValue(editingPreset.name);
            setRenameWidgetsVisible(true);
            this.setFocused(renameBox); renameBox.setFocused(true);
        } else if (mode == EditMode.SOUNDS) {
            rebuildSoundsWidget();
            this.setFocused(null);
        } else {
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

        @Override public int getRowWidth() { return LIST_W - 20; }
        @Override protected int scrollBarX() { return this.getX() + LIST_W - 6; }
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
                PresetListWidget.this.setSelected(this);
                PresetsScreen.this.openEditOverlay(preset);
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
