package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.layout.FilterBarLayout;
import com.scr0ols.soundtweaks.layout.PresetTabLayout;
import com.scr0ols.soundtweaks.layout.PresetsScreenLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * Preset management screen — master-detail layout.
 * Left: simplified list. Right: inline configuration panel (two steps on narrow screens).
 * Tabs: Color | Rename | Shortcut | Edit Sounds | Delete
 *
 * <p>All geometry comes from {@link PresetsScreenLayout} and {@link PresetTabLayout}; {@link #applyLayout()}
 * recomputes it and moves the widgets, and rendering and click handling use the same rects.
 */
public class PresetsScreen extends Screen {

    private static final String SHORTCUT_HINT = "ENTER to confirm  ·  BACKSPACE to clear  ·  ESC to cancel";
    private static final int LINE_H = 10;

    private final Screen parent;
    private PresetListWidget presetList;
    private PresetsScreenLayout layout;
    @Nullable private PresetTabLayout tabLayout;
    @Nullable private Rect listRect = null;
    /** Two-step mode only: show the detail panel (step 2) instead of the list (step 1). */
    private boolean detailStep = false;

    private Button newPresetBtn, doneBtn, importPresetsBtn, exportPresetsBtn, openConfigBtn, backBtn;

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
        // The old widgets were cleared with the screen; applyLayout() creates the list again.
        this.presetList = null;
        this.listRect = null;

        // ── Footer (bounds are set by applyLayout) ───────────────────────────
        this.newPresetBtn = footerButton("soundtweaks.presets.new", "soundtweaks.tooltip.new_preset",
                btn -> enterCreateMode());
        this.doneBtn = footerButton("soundtweaks.gui.done", null, btn -> this.onClose());
        this.importPresetsBtn = footerButton("soundtweaks.gui.import", "soundtweaks.tooltip.import_presets",
                btn -> FileDialogs.openJson(this::importPresetsFrom, this::openPathFallback));
        this.exportPresetsBtn = footerButton("soundtweaks.gui.export", "soundtweaks.tooltip.export_presets",
                btn -> FileDialogs.saveJson("soundtweaks_presets_export.json", this::exportPresetsTo, this::openPathFallback));
        this.openConfigBtn = footerButton("soundtweaks.gui.open_folder", "soundtweaks.tooltip.open_folder",
                btn -> ConfigFileUtil.openConfigFolder());
        this.backBtn = footerButton("soundtweaks.gui.back", null, btn -> showList());

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
        this.renameBox = new EditBox(this.font, 0, 0, 20, 20, Component.empty());
        this.renameBox.setMaxLength(64);
        this.renameBox.visible = false;
        this.addRenderableWidget(this.renameBox);

        this.renameConfirmBtn = Button.builder(Component.translatable("soundtweaks.gui.save_name"),
                btn -> confirmRename()).bounds(0, 0, 20, 20).build();
        this.renameConfirmBtn.visible = false;
        this.addRenderableWidget(this.renameConfirmBtn);

        this.renameCancelBtn = Button.builder(Component.translatable("soundtweaks.gui.clear"),
                btn -> { renameBox.setValue(""); this.setFocused(renameBox); renameBox.setFocused(true); }
        ).bounds(0, 0, 20, 20).build();
        this.renameCancelBtn.visible = false;
        this.addRenderableWidget(this.renameCancelBtn);

        // ── Color hex EditBox ─────────────────────────────────────────────────
        this.colorHexBox = new EditBox(this.font, 0, 0, 20, 18, Component.empty());
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

        initSoundsWidgets();
        applyLayout();
    }

    private Button footerButton(String labelKey, @Nullable String tooltipKey, Button.OnPress onPress) {
        Button button = Button.builder(Component.translatable(labelKey), onPress).bounds(0, 0, 20, 20).build();
        if (tooltipKey != null) button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        this.addRenderableWidget(button);
        return button;
    }

    private void initSoundsWidgets() {
        this.soundsCatDrop = new FilterDropdown(0, 0, 100,
                I18n.get("soundtweaks.gui.category"), this::onSoundsCategorySelected);
        SoundFilterOptions.populateCategories(this.soundsCatDrop);

        this.soundsObjDrop = new FilterDropdown(0, 0, 100,
                I18n.get("soundtweaks.gui.object"), this::onSoundsObjectSelected);
        this.soundsObjDrop.setActive(false);

        this.soundsClear = Button.builder(Component.literal("x"), btn -> clearSoundsFilters())
                .bounds(0, 0, FilterBarLayout.CLEAR_W, 20).build();
        this.soundsClear.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.clear_filters")));
        this.soundsClear.visible = false;
        this.addRenderableWidget(this.soundsClear);

        this.soundsMute = Button.builder(Component.empty(), btn -> {
            if (soundsWidget != null) {
                soundsWidget.toggleMute();
                refreshSoundsList();
            }
        }).bounds(0, 0, PresetsScreenLayout.MUTE_W, 20).build();
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
        ).bounds(0, 0, PresetsScreenLayout.VIEW_TOGGLE_W, 20).build();
        this.soundsViewToggle.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.view_toggle")));
        this.soundsViewToggle.visible = false;
        this.addRenderableWidget(this.soundsViewToggle);

        this.soundsSearch = new EditBox(this.font, 0, 0, FilterBarLayout.MIN_SEARCH_W, 20,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.soundsSearch.setResponder(q -> { this.soundsQuery = q; refreshSoundsList(); });
        this.soundsSearch.visible = false;
        this.addRenderableWidget(this.soundsSearch);

        this.soundsImport = Button.builder(Component.translatable("soundtweaks.gui.import_from_config"), btn -> {
            if (editingPreset == null) return;
            VolumeConfig.SOUNDS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.sounds.put(id, vol); });
            VolumeConfig.BLOCKS.getAll().forEach((id, vol) -> { if (vol != 1.0f) editingPreset.blocks.put(id, vol); });
            PresetConfig.markDirty();
            refreshSoundsList();
        }).bounds(0, 0, PresetsScreenLayout.SOUNDS_IMPORT_W, 20).build();
        this.soundsImport.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.import_from_config")));
        this.soundsImport.visible = false;
        this.addRenderableWidget(this.soundsImport);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    /** Recomputes the layout for the current size and state and moves every widget to its rect. */
    private void applyLayout() {
        boolean selected = editingPreset != null;
        this.layout = PresetsScreenLayout.compute(this.width, this.height, selected, detailStep,
                selected && editMode == EditMode.SOUNDS, PresetTabs.textWidths(this.font));

        placePresetList(layout.list());
        WidgetBounds.placeOrHide(importPresetsBtn, layout.importButton());
        WidgetBounds.placeOrHide(exportPresetsBtn, layout.exportButton());
        WidgetBounds.placeOrHide(openConfigBtn, layout.openConfigButton());
        WidgetBounds.placeOrHide(newPresetBtn, layout.newButton());
        WidgetBounds.placeOrHide(backBtn, layout.backButton());
        WidgetBounds.place(doneBtn, layout.doneButton());

        this.tabLayout = layout.hasDetail() ? PresetTabLayout.compute(layout.content()) : null;
        if (tabLayout != null) {
            WidgetBounds.place(renameBox, tabLayout.renameBox());
            WidgetBounds.place(renameConfirmBtn, tabLayout.renameSave());
            WidgetBounds.place(renameCancelBtn, tabLayout.renameClear());
            WidgetBounds.place(colorHexBox, tabLayout.hexBox());
            placeSoundsFilter(layout.soundsFilter());
            WidgetBounds.place(soundsImport, layout.soundsImportButton());
            if (editMode == EditMode.SOUNDS) rebuildSoundsWidget();
        }
    }

    /** The list is recreated when its rect changes (setWidth/setX on the list do not update its clip). */
    private void placePresetList(Rect r) {
        if (r.isEmpty()) {
            if (presetList != null) this.removeWidget(presetList);
            presetList = null;
            listRect = r;
            return;
        }
        if (presetList != null && r.equals(listRect)) return;
        if (presetList != null) this.removeWidget(presetList);
        presetList = new PresetListWidget(this, this.minecraft, r.w(), r.h(), r.y(), 24);
        presetList.setX(r.x());
        this.addRenderableWidget(presetList);
        presetList.refresh();
        listRect = r;
    }

    private void placeSoundsFilter(FilterBarLayout f) {
        soundsCatDrop.setBounds(f.category().x(), f.category().y(), f.category().w());
        soundsObjDrop.setBounds(f.object().x(), f.object().y(), f.object().w());
        WidgetBounds.place(soundsClear, f.clear());
        WidgetBounds.place(soundsSearch, f.search());
        WidgetBounds.place(soundsMute, f.trailing().get(0));
        WidgetBounds.place(soundsViewToggle, f.trailing().get(1));
    }

    private void refreshPresets() {
        if (presetList != null) presetList.refresh();
    }

    /** True while the detail panel is on screen (side by side, or step 2 of the narrow layout). */
    private boolean detailVisible() {
        return editingPreset != null && layout.hasDetail();
    }

    private boolean soundsVisible() {
        return detailVisible() && editMode == EditMode.SOUNDS;
    }

    /** True when a preset is selected but only the list is shown (step 1 of the narrow layout). */
    boolean isListStep() { return layout.mode() == PresetsScreenLayout.Mode.LIST_STEP; }

    private void showList() {
        detailStep = false;
        this.setFocused(null);
        applyLayout();
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
        Rect r = layout.soundsList();
        soundsWidget = new PresetSoundList(this.minecraft, editingPreset, r.w(), r.h(), r.y(), 22);
        soundsWidget.setX(r.x());
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
        showSoundsWidgets(soundsVisible());

        super.extractRenderState(g, mouseX, mouseY, a);

        if (creating) setCreateWidgetsVisible(true);
        if (detailVisible() && editMode == EditMode.RENAME) setRenameWidgetsVisible(true);
        if (detailVisible() && editMode == EditMode.COLOR
                && editingPreset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX)
            colorHexBox.visible = true;

        Rect listTitle = layout.listTitle();
        if (!listTitle.isEmpty())
            g.centeredText(this.font, I18n.get("soundtweaks.presets.title"),
                    listTitle.x() + listTitle.w() / 2, listTitle.y(), 0xFFFFFFFF);

        Rect divider = layout.divider();
        if (!divider.isEmpty()) g.fill(divider.x(), divider.y(), divider.right(), divider.bottom(), 0xFF111111);

        Rect message = layout.message();
        if (!message.isEmpty())
            footerMessage.render(g, this.font, message.x() + message.w() / 2, message.y());

        if (detailVisible()) renderDetailPanel(g, mouseX, mouseY, a);

        // Dropdowns de sons por cima de tudo
        if (soundsVisible()) {
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
        Rect panel = layout.panel();
        Rect title = layout.detailTitle();
        int cx2 = title.x() + title.w() / 2;

        String titleText = GuiText.ellipsize(this.font, editingPreset.name, Math.max(0, title.w() - 20));
        int pc = PresetColorPicker.readableOnDark(editingPreset.argbColor() & 0x00FFFFFF);
        g.centeredText(this.font, titleText, cx2 + 1, title.y() + 3, 0xCC000000);
        g.centeredText(this.font, titleText, cx2,     title.y() + 2, pc | 0xFF000000);
        g.fill(panel.x(), PresetsScreenLayout.HEADER_H - 2, panel.right(), PresetsScreenLayout.HEADER_H - 1, 0xFF444466);
        g.fill(panel.x(), PresetsScreenLayout.HEADER_H - 1, panel.right(), PresetsScreenLayout.HEADER_H,     0xFF111111);

        Rect sep = layout.separator();
        g.fill(sep.x(), sep.y(), sep.right(), sep.y() + 1, 0xFF111111);
        g.fill(sep.x(), sep.y() + 1, sep.right(), sep.bottom(), 0xFF555555);

        PresetTabs.render(g, this.font, mouseX, mouseY, layout.tabs(), pc, activeTabIndex());

        switch (editMode) {
            case COLOR    -> renderColorContent(g, mouseX, mouseY, a);
            case RENAME   -> renderRenameContent(g, mouseX, mouseY, a);
            case SHORTCUT -> renderShortcutContent(g);
            case SOUNDS   -> renderSoundsHint(g);
            default       -> {}
        }
    }

    private int activeTabIndex() {
        for (int i = 0; i < TAB_MODES.length; i++)
            if (TAB_MODES[i] != null && TAB_MODES[i] == editMode) return i;
        return -1;
    }

    private void renderColorContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        PresetColorPicker.render(g, this.font, mouseX, mouseY, tabLayout, editingPreset);
        if (editingPreset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX) colorHexBox.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderRenameContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        renameBox.extractRenderState(g, mouseX, mouseY, a);
        renameConfirmBtn.extractRenderState(g, mouseX, mouseY, a);
        renameCancelBtn.extractRenderState(g, mouseX, mouseY, a);
    }

    private void renderShortcutContent(GuiGraphicsExtractor g) {
        Rect box = tabLayout.shortcutBox();
        int cx = tabLayout.centerX();
        g.fill(box.x(), box.y(), box.right(), box.bottom(), 0xBB1A1A1A);

        g.centeredText(this.font, shortcutCapture.label(), cx, tabLayout.shortcutCaptureY(),
                shortcutCapture.hasCapture() ? 0xFF88FF88 : 0xFF666677);
        String savedLabel = PresetKeyNames.displayLabel(editingPreset);
        boolean hasSaved = !savedLabel.equals(PresetKeyNames.NONE);
        g.centeredText(this.font, hasSaved ? "[" + savedLabel + "]" : "[blank]", cx, tabLayout.shortcutSavedY(),
                hasSaved ? 0xFFCCCCFF : 0xFF888899);

        Rect hint = tabLayout.shortcutHint();
        List<FormattedCharSequence> lines = this.font.split(Component.literal(SHORTCUT_HINT), hint.w());
        for (int i = 0; i < lines.size() && (i + 1) * LINE_H <= hint.h(); i++)
            g.centeredText(this.font, lines.get(i), cx, hint.y() + i * LINE_H, 0xFF888899);
    }

    private void renderSoundsHint(GuiGraphicsExtractor g) {
        if (soundsMute.visible && soundsWidget != null)
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

        if (detailVisible()) {
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
            if (key == InputConstants.KEY_ESCAPE) { leaveDetail(); return true; }
            return true;
        }

        if (editingPreset != null && key == InputConstants.KEY_ESCAPE) { closeDetailPanel(); return true; }
        return super.keyPressed(event);
    }

    /** Escape from the detail: back to the list in the narrow layout, otherwise close the panel. */
    private void leaveDetail() {
        if (layout.mode() == PresetsScreenLayout.Mode.DETAIL_STEP) showList();
        else closeDetailPanel();
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (detailVisible() && editMode == EditMode.SHORTCUT) {
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
            refreshPresets(); return;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { if (shortcutCapture.hasCapture()) confirmShortcut(); return; }
        shortcutCapture.onKeyPressed(key);
    }

    private void confirmShortcut() {
        if (editingPreset == null) return;
        shortcutCapture.commitTo(editingPreset);
        shortcutCapture.reset(); refreshPresets();
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
        if (soundsVisible()) {
            if (soundsCatDrop.mouseClicked(event)) { if (soundsCatDrop.isOpen()) soundsObjDrop.close(); return true; }
            if (soundsObjDrop.mouseClicked(event)) { if (soundsObjDrop.isOpen()) soundsCatDrop.close(); return true; }
        }

        if (super.mouseClicked(event, consumed)) return true;

        // Detail panel — manual areas
        if (detailVisible() && layout.panel().contains((int) mx, (int) my)) {
            int tabHit = PresetTabs.hit(mx, my, layout.tabs());
            if (tabHit >= 0) { handleTabClick(tabHit); return true; }

            // Content by mode
            if (editMode == EditMode.COLOR) {
                if (colorHexBox.visible
                        && mx >= colorHexBox.getX() && mx < colorHexBox.getX() + colorHexBox.getWidth()
                        && my >= colorHexBox.getY() && my < colorHexBox.getY() + colorHexBox.getHeight()) {
                    this.setFocused(colorHexBox); colorHexBox.setFocused(true);
                    colorHexBox.mouseClicked(event, false); return true;
                }
                handleColorGridClick(mx, my, editingPreset);
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
        if (soundsVisible()) {
            if (soundsCatDrop.mouseDragged(event.y())) return true;
            if (soundsObjDrop.mouseDragged(event.y())) return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (soundsVisible()) {
            soundsCatDrop.mouseReleased(); soundsObjDrop.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (soundsVisible()) {
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

    private void handleColorGridClick(double mx, double my, PresetConfig.Preset preset) {
        if (preset == null) return;
        int hit = PresetColorPicker.hit(mx, my, tabLayout);
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
        boolean samePreset = (this.editingPreset == preset);
        this.editingPreset = preset;
        this.detailStep = true;
        if (samePreset && editMode != EditMode.NONE) applyLayout();
        else setEditMode(EditMode.COLOR);
    }

    private void setEditMode(EditMode mode) {
        this.editMode = mode;
        setRenameWidgetsVisible(false);

        if (mode == EditMode.RENAME && editingPreset != null) {
            renameBox.setValue(editingPreset.name);
            setRenameWidgetsVisible(true);
            this.setFocused(renameBox); renameBox.setFocused(true);
        } else {
            this.setFocused(null);
        }

        if (mode == EditMode.SHORTCUT) shortcutCapture.reset();
        applyLayout();
    }

    @Nullable PresetConfig.Preset editingPreset() { return editingPreset; }
    boolean isCreating() { return creating; }

    void closeDetailPanel() {
        if (soundsWidget != null) { this.removeWidget(soundsWidget); soundsWidget = null; }
        this.editingPreset = null; this.editMode = EditMode.NONE; this.detailStep = false;
        setRenameWidgetsVisible(false); this.setFocused(null);
        if (presetList != null) presetList.setSelected(null);
        applyLayout();
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
            refreshPresets();
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
            if (!name.isEmpty()) { PresetConfig.renamePreset(editingPreset.id, name); refreshPresets(); }
        }
    }

    private void importPresetsFrom(java.nio.file.Path file) {
        PresetConfig.ImportResult result = PresetConfig.importFrom(file);
        if (result == null) {
            footerMessage.show("Import failed. Check logs for details.", 0xFFFF6666);
        } else if (result.imported() == 0) {
            footerMessage.show("No presets found in this file.", 0xFFFFAA44);
        } else if (result.conflictsReassigned() > 0) {
            refreshPresets();
            showImportConflictWarning(result.conflictsReassigned());
        } else {
            footerMessage.show("Imported " + result.imported() + " presets.", 0xFF88FF88);
            refreshPresets();
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
                ImportConfigScreen.ImportType.PRESETS, this::refreshPresets));
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
