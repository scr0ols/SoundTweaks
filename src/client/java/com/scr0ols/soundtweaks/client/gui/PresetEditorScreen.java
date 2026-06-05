package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
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

/**
 * Fullscreen preset sound editor (accessible via PresetsScreen).
 * The sound list uses PresetSoundList, also reused in the inline panel of PresetsScreen.
 */
public class PresetEditorScreen extends Screen {

    @Nullable private static SoundCategory savedCategory = null;
    @Nullable private static String        savedObject   = null;
    private   static         String        savedSearch   = "";
    private   static         double        savedScroll   = 0.0;

    private final Screen parent;
    final PresetConfig.Preset preset;

    private PresetSoundList soundList;
    private EditBox         searchBox;
    private FilterDropdown  categoryDropdown;
    private FilterDropdown  objectDropdown;
    private Button          viewToggleBtn;

    @Nullable private SoundCategory selectedCategory = null;
    @Nullable private String        selectedObject   = null;
    private           String        searchQuery      = "";

    private Button  mutePresetsBtn;
    private boolean mutePresetsActive = false;

    public PresetEditorScreen(Screen parent, PresetConfig.Preset preset) {
        super(Component.translatable("soundtweaks.presets.edit_title", preset.name));
        this.parent = parent;
        this.preset = preset;
    }

    @Override
    protected void init() {
        int listTop    = 70;
        int listBottom = this.height - 70;

        this.soundList = new PresetSoundList(this.minecraft, preset,
                this.width, listBottom - listTop, listTop, 20);
        this.soundList.refresh();
        this.addRenderableWidget(this.soundList);

        // ── Filters ──────────────────────────────────────────────────────────
        int fY = 28, fH = 20;

        this.categoryDropdown = new FilterDropdown(4, fY, 120,
                I18n.get("soundtweaks.gui.category"), this::onCategorySelected);
        populateCategoryDropdown();

        this.objectDropdown = new FilterDropdown(128, fY, 130,
                I18n.get("soundtweaks.gui.object"), this::onObjectSelected);
        this.objectDropdown.setActive(false);

        var clearBtn = Button.builder(Component.literal("x"), btn -> clearFilters())
                .bounds(262, fY, 20, fH).build();
        clearBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.clear_filters")));
        this.addRenderableWidget(clearBtn);

        this.viewToggleBtn = Button.builder(
                PresetSoundList.detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"),
                btn -> {
                    PresetSoundList.detailedView = !PresetSoundList.detailedView;
                    btn.setMessage(PresetSoundList.detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"));
                    refreshList();
                }
        ).bounds(this.width - 82, fY, 78, fH).build();
        this.viewToggleBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.view_toggle")));
        this.addRenderableWidget(this.viewToggleBtn);

        int searchX = 286;
        int searchW = Math.max(60, this.width - 82 - searchX - 4);
        this.searchBox = new EditBox(this.font, searchX, fY, searchW, fH,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setResponder(q -> { this.searchQuery = q; refreshList(); });
        this.addRenderableWidget(this.searchBox);

        // ── Footer ───────────────────────────────────────────────────────────
        int btnY    = this.height - 56;
        int centerX = this.width / 2;

        var importBtn = Button.builder(
                Component.translatable("soundtweaks.gui.import_from_config"), btn -> importFromBase()
        ).bounds(centerX - 135, btnY, 130, 20).build();
        importBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.import_from_config")));
        this.addRenderableWidget(importBtn);

        this.mutePresetsActive = false;
        this.mutePresetsBtn = Button.builder(Component.empty(), btn -> toggleMuteVisible())
                .bounds(centerX + 2, btnY, 24, 20).build();
        this.mutePresetsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.mute_preset")));
        this.addRenderableWidget(this.mutePresetsBtn);

        this.addRenderableWidget(Button.builder(
                Component.translatable("soundtweaks.gui.done"), btn -> this.onClose()
        ).bounds(centerX + 30, btnY, 100, 20).build());

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
        for (SoundCategory cat : SoundCategory.visibleCategories())
            pairs.add(new String[]{ cat.getDropdownKey(), I18n.get(cat.getLabelKey()) });
        pairs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        List<String> opts = new ArrayList<>(), labels = new ArrayList<>();
        for (String[] p : pairs) { opts.add(p[0]); labels.add(p[1]); }
        this.categoryDropdown.setOptions(opts, labels);
    }

    private void populateObjectDropdown(SoundCategory category) {
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
        this.selectedObject = object; refreshList();
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
        soundList.toggleMute();
        mutePresetsActive = soundList.isMuteActive();
        refreshList();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int rgb = preset.argbColor() & 0x00FFFFFF;
        int tintR = (rgb >> 16) & 0xFF, tintG = (rgb >> 8) & 0xFF, tintB = rgb & 0xFF;
        int dampedRgb = ((tintR / 2) << 16) | ((tintG / 2) << 8) | (tintB / 2);
        graphics.fill(0, 0, this.width, this.height, dampedRgb | 0x15000000);
        graphics.fill(0, 0, this.width, 24, rgb | 0xCC000000);

        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(0, 24, this.width, 25, 0xFF444466);
        graphics.fill(0, 66, this.width, 67, 0xFF333355);
        graphics.centeredText(this.font, "Editing: " + preset.name, this.width / 2, 8, 0xFFFFFFFF);

        graphics.fill(8, this.height - 72, this.width - 8, this.height - 71, 0xFF555555);
        int overrideCount = preset.sounds.size() + preset.blocks.size();
        String hint = overrideCount > 0
                ? overrideCount + " override(s) — orange = has override  |  100% = remove override"
                : "No overrides. Drag any slider below 100% to add an override.";
        int maxHintW = this.width - 16;
        while (hint.length() > 1 && this.font.width(hint) > maxHintW)
            hint = hint.substring(0, hint.length() - 1);
        if (!hint.endsWith("override") && !hint.endsWith("override."))
            hint = hint.stripTrailing() + "…";
        graphics.text(this.font, hint, 8, this.height - 18, 0xFFAAAAAA);

        if (this.mutePresetsBtn != null)
            SoundTweaksScreen.drawSpeakerIcon(graphics,
                    this.mutePresetsBtn.getX(), this.mutePresetsBtn.getY(),
                    this.mutePresetsBtn.getWidth(), this.mutePresetsBtn.getHeight(),
                    mutePresetsActive);

        this.categoryDropdown.render(graphics, mouseX, mouseY);
        this.objectDropdown.render(graphics, mouseX, mouseY);
    }

    // ── Eventos ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (this.categoryDropdown.mouseClicked(event)) {
            if (this.categoryDropdown.isOpen()) this.objectDropdown.close(); return true;
        }
        if (this.objectDropdown.mouseClicked(event)) {
            if (this.objectDropdown.isOpen()) this.categoryDropdown.close(); return true;
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
}
