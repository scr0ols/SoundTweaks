package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.VolumeConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * Preset management screen — master-detail layout.
 * Left: simplified list. Right: inline configuration panel.
 * Tabs: Color | Rename | Shortcut | Edit Sounds | Delete
 */
public class PresetsScreen extends Screen {

    private final Screen parent;
    private PresetListWidget presetList;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int LIST_W          = 330;
    private static final int LIST_W_CENTERED = 400;
    private static final int PANEL_HDR_H     = 28;
    private static final int CONTENT_Y       = 56;

    // Sounds panel filters — positioned immediately below the tabs
    private static final int SOUNDS_FILTER_Y = 56;  // HDR + TAB + 8
    private static final int SOUNDS_LIST_Y   = 82;  // SOUNDS_FILTER_Y + 20 + 6

    private int panelX() { return LIST_W + 1; }
    private int panelW() { return this.width - LIST_W - 1; }

    // Footer buttons (kept for rebuildLayout)
    private Button newPresetBtn, doneBtn, importPresetsBtn, exportPresetsBtn, openConfigBtn;

    // ── Footer feedback (import/export result) ────────────────────────────────
    private final FooterMessage footerMessage = new FooterMessage();

    // ── Create overlay ─────────────────────────────────────────────────────────
    private boolean creating = false;
    private EditBox createBox;
    private Button  createConfirmBtn, createCancelBtn;

    // ── Detail panel ──────────────────────────────────────────────────────────
    private enum EditMode { NONE, COLOR, RENAME, SHORTCUT, SOUNDS }
    private EditMode editMode = EditMode.NONE;
    @Nullable private PresetConfig.Preset editingPreset = null;

    // Modes opened by the tabs in PresetTabs.LABELS order (null: Delete has no mode)
    private static final EditMode[] TAB_MODES = {EditMode.COLOR, EditMode.RENAME, EditMode.SHORTCUT, EditMode.SOUNDS, null};

    // Widgets de rename
    private EditBox renameBox;
    private Button  renameConfirmBtn, renameCancelBtn;

    // Color hex EditBox
    private EditBox colorHexBox;

    // Captura de atalho
    private final ShortcutCapture shortcutCapture = new ShortcutCapture();

    // Painel de sons (SOUNDS mode)
    @Nullable private PresetSoundList soundsWidget = null;
    private FilterDropdown soundsCatDrop;
    private FilterDropdown soundsObjDrop;
    private EditBox        soundsSearch;
    private Button         soundsClear, soundsViewToggle, soundsImport, soundsMute;
    @Nullable private SoundCategory soundsCat = null;
    @Nullable private String        soundsObj = null;
    private           String        soundsQuery = "";

    // ── Constructor ───────────────────────────────────────────────────────────

    public PresetsScreen(Screen parent) {
        super(Component.translatable("soundtweaks.presets.title"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int listTop    = PANEL_HDR_H;
        int listBottom = this.height - 56;

        // presetList will be created by rebuildLayout() at the end of init

        // ── Footer ───────────────────────────────────────────────────────────
        this.newPresetBtn = Button.builder(
                Component.translatable("soundtweaks.presets.new"), btn -> enterCreateMode()
        ).bounds(4, this.height - 50, LIST_W - 8, 20).build();
        this.newPresetBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.new_preset")));
        this.addRenderableWidget(this.newPresetBtn);

        this.doneBtn = Button.builder(
                Component.translatable("soundtweaks.gui.done"), btn -> this.onClose()
        ).bounds(panelX() + panelW() / 2 - 60, this.height - 50, 120, 20).build();
        this.addRenderableWidget(this.doneBtn);

        this.importPresetsBtn = Button.builder(
                Component.translatable("soundtweaks.gui.import"),
                btn -> FileDialogs.openJson(this::importPresetsFrom, this::openPathFallback)
        ).bounds(4, this.height - 26, LIST_W / 2 - 6, 20).build();
        this.importPresetsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.import_presets")));
        this.addRenderableWidget(this.importPresetsBtn);

        this.exportPresetsBtn = Button.builder(
                Component.translatable("soundtweaks.gui.export"),
                btn -> FileDialogs.saveJson("soundtweaks_presets_export.json", this::exportPresetsTo, this::openPathFallback)
        // Provisional bounds — corrected by rebuildLayout() at the end of init().
        ).bounds(4, this.height - 26, LIST_W / 2 - 6, 20).build();
        this.exportPresetsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.export_presets")));
        this.addRenderableWidget(this.exportPresetsBtn);

        this.openConfigBtn = Button.builder(
                Component.translatable("soundtweaks.gui.open_folder"), btn -> ConfigFileUtil.openConfigFolder()
        ).bounds(LIST_W / 2 + 2, this.height - 26, LIST_W / 2 - 6, 20).build();
        this.openConfigBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.open_folder")));
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

        this.createCancelBtn = Button.builder(Component.translatable("soundtweaks.gui.cancel"),
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

        this.renameConfirmBtn = Button.builder(Component.translatable("soundtweaks.gui.save_name"),
                btn -> confirmRename()).bounds(renX, renY + 26, renBtnW, 20).build();
        this.renameConfirmBtn.visible = false;
        this.addRenderableWidget(this.renameConfirmBtn);

        this.renameCancelBtn = Button.builder(Component.translatable("soundtweaks.gui.clear"),
                btn -> { renameBox.setValue(""); this.setFocused(renameBox); renameBox.setFocused(true); }
        ).bounds(renX + renBtnW + 4, renY + 26, renBtnW, 20).build();
        this.renameCancelBtn.visible = false;
        this.addRenderableWidget(this.renameCancelBtn);

        // ── Color hex EditBox ─────────────────────────────────────────────────
        this.colorHexBox = new EditBox(this.font, PresetColorPicker.hexBoxX(panelX(), panelW()),
                PresetColorPicker.customY(), 110, 18, Component.empty());
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
        // rebuildLayout() was already called above (after the footer buttons)
    }

    private void initSoundsWidgets() {
        int px = panelX(), pw = panelW();
        int fy = SOUNDS_FILTER_Y, fh = 20;

        this.soundsCatDrop = new FilterDropdown(px + 4, fy, 100,
                I18n.get("soundtweaks.gui.category"), this::onSoundsCategorySelected);
        SoundFilterOptions.populateCategories(this.soundsCatDrop);

        this.soundsObjDrop = new FilterDropdown(px + 108, fy, 100,
                I18n.get("soundtweaks.gui.object"), this::onSoundsObjectSelected);
        this.soundsObjDrop.setActive(false);

        this.soundsClear = Button.builder(Component.literal("x"), btn -> clearSoundsFilters())
                .bounds(px + 212, fy, 18, fh).build();
        this.soundsClear.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.clear_filters")));
        this.soundsClear.visible = false;
        this.addRenderableWidget(this.soundsClear);

        // Mute in the header, to the left of viewToggle
        this.soundsMute = Button.builder(Component.empty(), btn -> {
            if (soundsWidget != null) {
                soundsWidget.toggleMute();
                refreshSoundsList();
            }
        }).bounds(px + pw - 110, fy, 24, fh).build();
        this.soundsMute.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.mute_preset")));
        this.soundsMute.visible = false;
        this.addRenderableWidget(this.soundsMute);

        this.soundsViewToggle = Button.builder(
                PresetSoundList.detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"),
                btn -> {
                    PresetSoundList.detailedView = !PresetSoundList.detailedView;
                    btn.setMessage(PresetSoundList.detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"));
                    refreshSoundsList();
                }
        ).bounds(px + pw - 82, fy, 78, fh).build();
        this.soundsViewToggle.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.view_toggle")));
        this.soundsViewToggle.visible = false;
        this.addRenderableWidget(this.soundsViewToggle);

        // Shorter search box to leave room for the mute button
        int searchX = px + 234, searchW = Math.max(40, pw - 352);
        this.soundsSearch = new EditBox(this.font, searchX, fy, searchW, fh,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setResponder(q -> { this.soundsQuery = q; refreshSoundsList(); });
        this.soundsSearch.visible = false;
        this.addRenderableWidget(this.soundsSearch);

        // Import in the footer, to the left of Done (doneBtn is at panelX+panelW/2-60)
        int importX = px + pw / 2 - 117;
        this.soundsImport = Button.builder(Component.translatable("soundtweaks.gui.import_from_config"), btn -> {
            if (editingPreset == null) return;
            VolumeConfig.SOUNDS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.sounds.put(id, vol); });
            VolumeConfig.BLOCKS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.blocks.put(id, vol); });
            PresetConfig.markDirty();
            refreshSoundsList();
        }).bounds(importX, this.height - 26, 110, 20).build();
        this.soundsImport.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.import_from_config")));
        this.soundsImport.visible = false;
        this.addRenderableWidget(this.soundsImport);
    }

    private void rebuildLayout() {
        boolean centered = (editingPreset == null);
        int listTop    = PANEL_HDR_H;
        int listHeight = this.height - 58 - listTop;

        // Recreate the list with the correct dimensions
        // (setWidth/setX on AbstractSelectionList does not update the internal clip)
        if (presetList != null) this.removeWidget(presetList);
        if (centered) {
            int lw = Math.min(LIST_W_CENTERED, this.width - 40);
            int lx = (this.width - lw) / 2;
            presetList = new PresetListWidget(this, this.minecraft, lw, listHeight, listTop, 24);
            presetList.setX(lx);
            // Row 1: Import | Export | Open Config
            // Left half (Import+Export) and right half (Open Config) align with Row 2 split
            int gap = 4;
            int halfW  = lw / 2 - 6;                      // same width as New Preset / Done
            int bwSmall = (halfW - gap) / 2;               // Import and Export share the left half
            int b1x = lx + 4, b2x = b1x + bwSmall + gap, b3x = lx + lw / 2 + 2;
            importPresetsBtn.setX(b1x);  importPresetsBtn.setWidth(bwSmall);  importPresetsBtn.setHeight(20);
            exportPresetsBtn.setX(b2x);  exportPresetsBtn.setWidth(bwSmall);  exportPresetsBtn.setHeight(20);
            openConfigBtn.setX(b3x);     openConfigBtn.setWidth(halfW);       openConfigBtn.setHeight(20);
            importPresetsBtn.setY(this.height - 50);
            exportPresetsBtn.setY(this.height - 50);
            openConfigBtn.setY(this.height - 50);
            // Row 2: New Preset | Done
            newPresetBtn.setX(lx + 4);             newPresetBtn.setWidth(halfW);
            doneBtn.setX(lx + lw / 2 + 2);        doneBtn.setWidth(halfW);
            newPresetBtn.setY(this.height - 26);   doneBtn.setY(this.height - 26);
        } else {
            presetList = new PresetListWidget(this, this.minecraft, LIST_W, listHeight, listTop, 24);
            // Row 1: Import | Export | Open Config — ratio 1:1:2
            int gap = 4, available = LIST_W - 8 - gap * 2;
            int bwSmall = available / 4;
            int bwLarge = available - bwSmall * 2;
            int b1x = 4, b2x = b1x + bwSmall + gap, b3x = b2x + bwSmall + gap;
            importPresetsBtn.setX(b1x);  importPresetsBtn.setWidth(bwSmall);  importPresetsBtn.setHeight(20);
            exportPresetsBtn.setX(b2x);  exportPresetsBtn.setWidth(bwSmall);  exportPresetsBtn.setHeight(20);
            openConfigBtn.setX(b3x);     openConfigBtn.setWidth(bwLarge);     openConfigBtn.setHeight(20);
            importPresetsBtn.setY(this.height - 50);
            exportPresetsBtn.setY(this.height - 50);
            openConfigBtn.setY(this.height - 50);
            // Row 2: New Preset (left) | Done (right, in the panel)
            newPresetBtn.setX(4);                  newPresetBtn.setWidth(LIST_W - 8);
            doneBtn.setX(panelX() + panelW() / 2 - 60); doneBtn.setWidth(120);
            newPresetBtn.setY(this.height - 26);   doneBtn.setY(this.height - 26);
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

        // ── Footer separator — starts at the vertical divider when the panel is open
        int footerSepX = (editingPreset != null) ? LIST_W + 1 : 8;
        g.fill(footerSepX, this.height - 58, this.width - 8, this.height - 57, 0xFF111111);
        g.fill(footerSepX, this.height - 57, this.width - 8, this.height - 56, 0xFF555555);

        // ── Import/Export feedback (timed) ────────────────────────────────────
        int msgCx = (editingPreset != null) ? (LIST_W + 1 + this.width) / 2 : this.width / 2;
        footerMessage.render(g, this.font, msgCx, this.height - 70);

        // ── Title ─────────────────────────────────────────────────────────────
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
            String draft = createBox.getValue().trim();
            boolean nameEmpty  = draft.isEmpty();
            createConfirmBtn.active = !nameEmpty;
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

    // ── Detail panel ──────────────────────────────────────────────────────────

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
        pc = PresetColorPicker.readableOnDark(pc);
        g.centeredText(this.font, titleText, cx2 + 1, 11, 0xCC000000);
        g.centeredText(this.font, titleText, cx2,     10, pc | 0xFF000000);
        g.fill(px, PANEL_HDR_H - 2, this.width, PANEL_HDR_H - 1, 0xFF444466); // azul/cinza
        g.fill(px, PANEL_HDR_H - 1, this.width, PANEL_HDR_H,     0xFF111111); // preto

        PresetTabs.render(g, this.font, mouseX, mouseY, px, PANEL_HDR_H, pc, activeTabIndex());

        switch (editMode) {
            case COLOR    -> renderColorContent(g, mouseX, mouseY, px, pw, editingPreset, a);
            case RENAME   -> renderRenameContent(g, mouseX, mouseY, a);
            case SHORTCUT -> renderShortcutContent(g, cx2);
            case SOUNDS   -> renderSoundsHint(g, cx2);
            default       -> {}
        }
    }

    private int activeTabIndex() {
        for (int i = 0; i < TAB_MODES.length; i++)
            if (TAB_MODES[i] != null && TAB_MODES[i] == editMode) return i;
        return -1;
    }

    private void renderColorContent(GuiGraphicsExtractor g, int mouseX, int mouseY,
                                    int px, int pw, PresetConfig.Preset preset, float a) {
        PresetColorPicker.render(g, this.font, mouseX, mouseY, px, pw, preset);
        if (preset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX) colorHexBox.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderRenameContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        renameBox.extractRenderState(g, mouseX, mouseY, a);
        renameConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
        renameCancelBtn.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderShortcutContent(GuiGraphicsExtractor g, int cx) {
        g.fill(cx - 170, CONTENT_Y + 2, cx + 170, CONTENT_Y + 66, 0xBB1A1A1A);

        g.centeredText(this.font, shortcutCapture.label(), cx, CONTENT_Y + 14, shortcutCapture.hasCapture() ? 0xFF88FF88 : 0xFF666677);
        String savedLabel = (editingPreset != null) ? PresetKeyNames.displayLabel(editingPreset) : PresetKeyNames.NONE;
        boolean hasSaved = !savedLabel.equals(PresetKeyNames.NONE);
        g.centeredText(this.font, hasSaved ? "[" + savedLabel + "]" : "[blank]", cx, CONTENT_Y + 34, hasSaved ? 0xFFCCCCFF : 0xFF888899);
        g.centeredText(this.font, "ENTER to confirm  ·  BACKSPACE to clear  ·  ESC to cancel", cx, CONTENT_Y + 50, 0xFF888899);
    }

    private void renderSoundsHint(GuiGraphicsExtractor g, int cx) {
        if (soundsMute != null && soundsMute.visible && soundsWidget != null)
            SoundTweaksScreen.drawSpeakerIcon(g, soundsMute.getX(), soundsMute.getY(),
                    soundsMute.getWidth(), soundsMute.getHeight(), soundsWidget.isMuteActive());
    }

    // ── Widget visibility ─────────────────────────────────────────────────────

    private void setCreateWidgetsVisible(boolean v) {
        createBox.visible = v; createConfirmBtn.visible = v; createCancelBtn.visible = v;
    }

    private void setRenameWidgetsVisible(boolean v) {
        renameBox.visible = v; renameConfirmBtn.visible = v; renameCancelBtn.visible = v;
    }

    // ── Sounds — filters and list ─────────────────────────────────────────────

    private void onSoundsCategorySelected(@Nullable String key) {
        this.soundsCat = SoundCategory.fromDropdownKey(key);
        this.soundsObj = null;
        if (this.soundsCat != null && this.soundsCat != SoundCategory.OTHERS) {
            SoundFilterOptions.populateObjects(this.soundsObjDrop, this.soundsCat);
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

        // The create overlay is modal — it takes priority over the edit panel.
        // Without this check first, when editingPreset != null and creating == true,
        // the block below would consume all keys without passing them to createBox.
        if (creating) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { confirmCreate(); return true; }
            if (key == InputConstants.KEY_ESCAPE) { exitCreateMode(); return true; }
            return super.keyPressed(event);
        }

        if (editingPreset != null) {
            if (editMode == EditMode.SHORTCUT) { handleShortcutKey(key); return true; }
            if (editMode == EditMode.RENAME) {
                if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { confirmRename(); return true; }
                if (key == InputConstants.KEY_ESCAPE) { setEditMode(EditMode.COLOR); return true; }
                return super.keyPressed(event);
            }
            if (editMode == EditMode.COLOR && this.getFocused() == colorHexBox) {
                if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_ESCAPE) {
                    this.setFocused(null); return true;
                }
                return super.keyPressed(event);
            }
            if (editMode == EditMode.SOUNDS) {
                if (key == InputConstants.KEY_ESCAPE) {
                    if (soundsCatDrop.isOpen()) { soundsCatDrop.close(); return true; }
                    if (soundsObjDrop.isOpen()) { soundsObjDrop.close(); return true; }
                }
                return super.keyPressed(event);
            }
            if (key == InputConstants.KEY_ESCAPE) { closeDetailPanel(); return true; }
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (editingPreset != null && editMode == EditMode.SHORTCUT) {
            shortcutCapture.onKeyReleased(event.key()); return true;
        }
        return super.keyReleased(event);
    }

    private void handleShortcutKey(int key) {
        if (key == InputConstants.KEY_ESCAPE) { shortcutCapture.reset(); setEditMode(EditMode.COLOR); return; }
        if (key == InputConstants.KEY_BACKSPACE) {
            shortcutCapture.reset();
            if (editingPreset != null) {
                editingPreset.shortcutKey = 0; editingPreset.shortcutHeldKey = 0; editingPreset.shortcutHeldKey2 = 0;
                PresetConfig.markDirty();
            }
            presetList.refresh(); return;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { if (shortcutCapture.hasCapture()) confirmShortcut(); return; }
        shortcutCapture.onKeyPressed(key);
    }

    private void confirmShortcut() {
        if (editingPreset == null) return;
        shortcutCapture.commitTo(editingPreset);
        shortcutCapture.reset(); presetList.refresh();
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

        // Sounds dropdowns — priority when in SOUNDS mode
        if (editingPreset != null && editMode == EditMode.SOUNDS) {
            if (soundsCatDrop.mouseClicked(event)) { if (soundsCatDrop.isOpen()) soundsObjDrop.close(); return true; }
            if (soundsObjDrop.mouseClicked(event)) { if (soundsObjDrop.isOpen()) soundsCatDrop.close(); return true; }
        }

        if (super.mouseClicked(event, consumed)) return true;

        // Right panel — manual areas
        if (editingPreset != null && mx >= panelX()) {
            int px = panelX();

            // Tabs
            int tabHit = PresetTabs.hit(mx, my, px, PANEL_HDR_H);
            if (tabHit >= 0) { handleTabClick(tabHit); return true; }

            // Content by mode
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
        if (tabIndex == PresetTabs.DELETE_INDEX) {
            openDeleteConfirm();
        } else {
            setEditMode(TAB_MODES[tabIndex]);
        }
    }

    private void openDeleteConfirm() {
        if (editingPreset == null) return;
        PresetConfig.Preset toDelete = editingPreset;
        setEditMode(EditMode.COLOR); // reset before opening overlay — ensures clean state on return
        this.minecraft.gui.setScreen(DeletePresetDialog.create(this.minecraft, this, toDelete, this::closeDetailPanel));
    }

    private void handleColorGridClick(double mx, double my, int px, int pw, PresetConfig.Preset preset) {
        if (preset == null) return;
        int hit = PresetColorPicker.hit(mx, my, px, pw);
        if (hit == PresetColorPicker.NO_HIT) return;
        if (hit == PresetConfig.CUSTOM_COLOR_INDEX) {
            preset.colorIndex = PresetConfig.CUSTOM_COLOR_INDEX;
            if (preset.customColor == 0) preset.customColor = 0xFF888888;
            PresetConfig.markDirty();
            colorHexBox.setValue(String.format("%06X", preset.customColor & 0xFFFFFF));
            colorHexBox.visible = true; this.setFocused(colorHexBox); colorHexBox.setFocused(true);
        } else {
            preset.colorIndex = hit; PresetConfig.markDirty(); this.setFocused(null);
        }
    }

    // ── Panel: open / close / switch mode ────────────────────────────────────

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
            // Shift Done to the right to sit next to Import from config
            doneBtn.setX(panelX() + panelW() / 2 - 3);
            rebuildSoundsWidget();
            this.setFocused(null);
        } else {
            doneBtn.setX(panelX() + panelW() / 2 - 60);
            this.setFocused(null);
        }

        if (mode == EditMode.SHORTCUT) shortcutCapture.reset();
    }

    @Nullable PresetConfig.Preset editingPreset() { return editingPreset; }
    boolean isCreating() { return creating; }

    void closeDetailPanel() {
        if (soundsWidget != null) { this.removeWidget(soundsWidget); soundsWidget = null; }
        this.editingPreset = null; this.editMode = EditMode.NONE;
        setRenameWidgetsVisible(false); this.setFocused(null);
        if (presetList != null) presetList.setSelected(null);
        presetList.refresh();
        rebuildLayout();
    }

    // ── Create preset ─────────────────────────────────────────────────────────

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
            if (!name.isEmpty()) { PresetConfig.renamePreset(editingPreset.id, name); presetList.refresh(); }
        }
    }

    private void importPresetsFrom(java.nio.file.Path file) {
        PresetConfig.ImportResult result = PresetConfig.importFrom(file);
        if (result == null) {
            footerMessage.show("Import failed. Check logs for details.", 0xFFFF6666);
        } else if (result.imported() == 0) {
            footerMessage.show("No presets found in this file.", 0xFFFFAA44);
        } else if (result.conflictsReassigned() > 0) {
            presetList.refresh();
            showImportConflictWarning(result.conflictsReassigned());
        } else {
            footerMessage.show("Imported " + result.imported() + " presets.", 0xFF88FF88);
            presetList.refresh();
        }
    }

    private void exportPresetsTo(java.nio.file.Path file) {
        int exported = PresetConfig.exportTo(file);
        if (exported < 0) footerMessage.show("Export failed. Check logs for details.", 0xFFFF6666);
        else footerMessage.show("Exported " + exported + " presets.", 0xFF88FF88);
    }

    /** The system file dialog could not be shown: fall back to typing the path in-game. */
    private void openPathFallback() {
        this.minecraft.gui.setScreen(new ImportConfigScreen(this,
                ImportConfigScreen.ImportType.PRESETS, () -> presetList.refresh()));
    }

    private void showImportConflictWarning(int count) {
        this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmLinkScreen(
            confirmed -> this.minecraft.gui.setScreen(PresetsScreen.this),
            Component.translatable("soundtweaks.presets.import_conflict_body", count),
            java.net.URI.create(PresetConfig.WIKI_PRESETS_URL),
            true
        ));
    }

    @Override
    public void onClose() { this.minecraft.gui.setScreen(parent); }
}
