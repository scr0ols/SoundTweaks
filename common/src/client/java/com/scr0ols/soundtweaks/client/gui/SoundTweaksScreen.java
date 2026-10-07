package com.scr0ols.soundtweaks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.VolumeResolver;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import com.scr0ols.soundtweaks.layout.MainScreenLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SoundTweaksScreen extends Screen {

    // ── Persistent state ──────────────────────────────────────────────────────
    @Nullable private static SoundCategory savedCategory = null;
    @Nullable private static String        savedObject   = null;
    private   static         String        savedSearch   = "";
    private   static         double        savedScroll   = 0.0;
    private   static         boolean       detailedView  = false;
    private   static         boolean       sidebarOpen   = true;

    @Nullable private final Screen parent;

    private SoundListWidget soundList;
    private FilterBar       filterBar;
    private Button          viewToggleButton;
    private Button          muteSoundsBtn;
    // Static to persist across opens — the real state lives in VolumeResolver,
    // but this flag tracks what the button "did" (what is queued to be unmuted)
    private static boolean  muteSoundsActive = false;

    @Nullable private SoundCategory selectedCategory = null;
    @Nullable private String        selectedObject   = null;
    private           String        searchQuery      = "";

    /** Geometry of every part of the screen; recomputed by init and when the thin rail scrolls. */
    private MainScreenLayout layout;
    private List<PresetConfig.Preset> favorites = List.of();
    /** Index of the first favourite shown by the thin rail. */
    private int railFirst = 0;

    public SoundTweaksScreen(@Nullable Screen parent) {
        super(Component.translatable("soundtweaks.gui.title"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // init runs again on every window resize and sidebar toggle: keep what the player chose
        if (this.soundList != null) saveState();

        this.favorites = PresetConfig.getFavoritePresets();
        computeLayout();
        MainScreenLayout l = this.layout;

        // ── Header: [speaker] [Simple/Detail View] [Presets ▶/◄] ... title ...
        this.muteSoundsBtn = Button.builder(Component.empty(), btn -> toggleMuteVisible())
                .bounds(l.mute().x(), l.mute().y(), l.mute().w(), l.mute().h()).build();
        this.muteSoundsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.mute_all")));
        this.addRenderableWidget(this.muteSoundsBtn);

        this.viewToggleButton = Button.builder(
                detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"),
                btn -> {
                    detailedView = !detailedView;
                    btn.setMessage(detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"));
                    refreshList();
                }
        ).bounds(l.viewToggle().x(), l.viewToggle().y(), l.viewToggle().w(), l.viewToggle().h()).build();
        this.viewToggleButton.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.view_toggle")));
        this.addRenderableWidget(this.viewToggleButton);

        if (!l.presets().isEmpty()) {
            Button presetsBtn = Button.builder(
                    Component.translatable("soundtweaks.presets.title"),
                    btn -> toggleSidebar()
            ).bounds(l.presets().x(), l.presets().y(), l.presets().w(), l.presets().h()).build();
            presetsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.presets_sidebar")));
            this.addRenderableWidget(presetsBtn);
        }

        // ── Filter bar, one or two lines depending on the width
        this.filterBar = new FilterBar(this.font, this::onCategorySelected, this::onObjectSelected,
                this::clearFilters, q -> { this.searchQuery = q; refreshList(); }, List.of());
        SoundFilterOptions.populateCategories(this.filterBar.category());
        this.filterBar.addWidgets(this::addRenderableWidget);
        this.filterBar.setLayout(l.filterBar());

        // ── Sound list (starts immediately below the filters)
        this.soundList = new SoundListWidget(this.minecraft, l.list().w(), l.list().h(), l.list().y(), 20);
        this.soundList.setX(l.list().x());
        refreshList();
        this.addRenderableWidget(this.soundList);

        // Done button
        this.addRenderableWidget(
                Button.builder(Component.translatable("soundtweaks.gui.done"), btn -> this.onClose())
                        .bounds(l.doneButton().x(), l.doneButton().y(), l.doneButton().w(), l.doneButton().h())
                        .build()
        );

        // Import config from another instance via native file dialog
        var importCfgBtn = Button.builder(
                Component.translatable("soundtweaks.gui.import_config"),
                btn -> FileDialogs.openJson(this::importConfigFrom, this::openPathFallback)
        ).bounds(l.importButton().x(), l.importButton().y(), l.importButton().w(), l.importButton().h()).build();
        importCfgBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.import_config")));
        this.addRenderableWidget(importCfgBtn);

        // Manage Presets: a text button in the sidebar, an icon button on the thin rail
        if (l.panel() != MainScreenLayout.Panel.NONE) {
            boolean icon = l.panel() == MainScreenLayout.Panel.RAIL;
            Button manage = Button.builder(
                    icon ? Component.literal("≡") : Component.translatable("soundtweaks.presets.manage"),
                    b -> this.minecraft.gui.setScreen(new PresetsScreen(this))
            ).bounds(l.manage().x(), l.manage().y(), l.manage().w(), l.manage().h()).build();
            if (icon) manage.setTooltip(Tooltip.create(Component.translatable("soundtweaks.presets.manage")));
            this.addRenderableWidget(manage);
        }

        restoreSavedState();
    }

    private void computeLayout() {
        this.layout = MainScreenLayout.compute(this.width, this.height, sidebarOpen, this.favorites.size(), this.railFirst);
        this.railFirst = this.layout.firstFavorite();
    }

    private void saveState() {
        savedCategory = this.selectedCategory;
        savedObject   = this.selectedObject;
        savedSearch   = this.searchQuery;
        savedScroll   = this.soundList != null ? this.soundList.getScrollAmount() : 0.0;
    }


    private void restoreSavedState() {
        if (savedCategory != null) {
            this.selectedCategory = savedCategory;
            this.filterBar.category().setSelectedValueSilently(savedCategory.getDropdownKey());
            if (savedCategory != SoundCategory.OTHERS && savedCategory.getPrefix() != null) {
                SoundFilterOptions.populateObjects(this.filterBar.object(), savedCategory);
                this.filterBar.object().setActive(true);
                if (savedObject != null) {
                    this.selectedObject = savedObject;
                    this.filterBar.object().setSelectedValueSilently(savedObject);
                }
            }
        }
        if (!savedSearch.isEmpty()) {
            this.searchQuery = savedSearch;
            this.filterBar.searchBox().setValue(savedSearch);
        } else {
            refreshList();
        }
        if (this.soundList != null) this.soundList.setScrollAmount(savedScroll);
        syncMuteState();
    }

    /** Syncs the mute button icon with the actual VolumeResolver state. */
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
        Rect content = this.layout.content();

        // Header background band and separator — before super so it does not cover the buttons
        graphics.fill(content.x(), 0, content.right(), MainScreenLayout.HEADER_H, 0xFF1A1A2E);
        graphics.fill(content.x(), MainScreenLayout.HEADER_H, content.right(), MainScreenLayout.HEADER_H + 1, 0xFF444466);

        // Sidebar / rail background before super — so the Manage button (widget) renders on top
        FavoritesPanel.render(graphics, this.font, this.layout, this.favorites, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, a);

        // Title centred, but only in the room left of the header buttons
        Rect titleArea = this.layout.title();
        if (!titleArea.isEmpty()) {
            String title = GuiText.ellipsize(this.font, I18n.get("soundtweaks.gui.title"), titleArea.w());
            int half = this.font.width(title) / 2;
            int centre = Math.max(titleArea.x() + half, Math.min(titleArea.right() - half, content.x() + content.w() / 2));
            graphics.centeredText(this.font, title, centre, 8, 0xFFFFFFFF);
        }

        // Speaker icon on the mute/restore button
        if (this.muteSoundsBtn != null)
            drawSpeakerIcon(graphics, this.muteSoundsBtn.getX(), this.muteSoundsBtn.getY(),
                    this.muteSoundsBtn.getWidth(), this.muteSoundsBtn.getHeight(), muteSoundsActive);

        // Footer — 3-pixel separator (the panel draws its own)
        FavoritesPanel.drawFooterLine(graphics, content.x(), content.right(), this.layout.list().bottom());
        Rect countArea = this.layout.count();
        if (!countArea.isEmpty()) {
            int total = SoundRegistry.count();
            boolean hasFilter = selectedCategory != null || selectedObject != null || !searchQuery.isBlank();
            String countText = hasFilter
                    ? I18n.get("soundtweaks.gui.sounds_filtered", getFilteredSounds().size(), total)
                    : I18n.get("soundtweaks.gui.sounds", total);
            graphics.text(this.font, GuiText.ellipsize(this.font, countText, countArea.w()),
                    countArea.x(), countArea.y(), 0xFFAAAAAA);
        }

        // Dropdowns — always last (render on top of everything)
        this.filterBar.render(graphics, mouseX, mouseY);
    }

    // ── Mouse events ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        // Dropdowns take priority: the popup overlaps the soundList (y=46+)
        // and super.mouseClicked would pass the click to soundList before the dropdown
        if (this.filterBar.mouseClicked(event)) return true;

        if (super.mouseClicked(event, consumed)) return true;
        return handlePanelClick(event);
    }

    private boolean handlePanelClick(MouseButtonEvent event) {
        if (this.layout.panel() == MainScreenLayout.Panel.NONE) return false;
        double mx = event.x();
        double my = event.y();
        if (!this.layout.panelBounds().contains((int) mx, (int) my)) return false;

        // Sidebar header → close
        if (this.layout.panel() == MainScreenLayout.Panel.SIDEBAR && my < 22) {
            toggleSidebar();
            return true;
        }

        PresetConfig.Preset preset = FavoritesPanel.presetAt(this.layout, this.favorites, mx, my);
        if (preset != null) PresetConfig.setActive(preset.id, !PresetConfig.isActive(preset.id));
        return true; // absorb remaining clicks on the panel
    }

    private void toggleSidebar() {
        sidebarOpen = !sidebarOpen;
        this.rebuildWidgets();
    }

    /** The mouse wheel over the thin rail scrolls the favourites one at a time. */
    private boolean scrollRail(double mouseX, double mouseY, double scrollY) {
        if (this.layout.panel() != MainScreenLayout.Panel.RAIL || scrollY == 0) return false;
        if (!this.layout.panelBounds().contains((int) mouseX, (int) mouseY)) return false;
        this.railFirst = this.layout.firstFavorite() - (int) Math.signum(scrollY);
        computeLayout();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.filterBar.mouseDragged(event.y())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.filterBar.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.filterBar.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (scrollRail(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (this.filterBar.closeDropdowns()) return true;
        }
        char letter = GuiKeys.jumpLetter(event);
        if (letter != 0) {
            if (this.filterBar.isDropdownOpen()) return this.filterBar.jumpToLetter(letter);
            if (!this.filterBar.searchBox().isFocused())    return this.soundList.jumpToLetter(letter);
        }
        return super.keyPressed(event);
    }

    // ── Dropdown callbacks ────────────────────────────────────────────────────

    private void onCategorySelected(@Nullable String key) {
        this.selectedCategory = SoundCategory.fromDropdownKey(key);
        this.selectedObject   = null;
        if (this.selectedCategory != null && this.selectedCategory != SoundCategory.OTHERS) {
            SoundFilterOptions.populateObjects(this.filterBar.object(), this.selectedCategory);
            this.filterBar.object().clearSelection();
            this.filterBar.object().setActive(true);
        } else {
            this.filterBar.object().clearSelection();
            this.filterBar.object().setActive(false);
        }
        refreshList();
    }

    private void onObjectSelected(@Nullable String object) {
        this.selectedObject = object;
        refreshList();
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

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
                int ci = id.indexOf(':');
                String p = ci >= 0 ? id.substring(ci + 1) : id;
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

    // ── Init helpers ──────────────────────────────────────────────────────────

    private void toggleMuteVisible() {
        muteSoundsActive = !muteSoundsActive;
        // Uses VolumeResolver's volatile mute layer — absolute priority over presets and
        // base config, without corrupting any persisted configuration.
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
     * Draws a pixel-art speaker icon centred on a button.
     * muted=false → speaker with waves (active); muted=true → speaker with red X.
     */
    static void drawSpeakerIcon(GuiGraphicsExtractor g, int bx, int by, int bw, int bh, boolean muted) {
        // Icon: 12×10 pixels, centred on the button
        int ox = bx + (bw - 12) / 2;
        int oy = by + (bh - 10) / 2;
        int col = 0xFFFFFFFF;

        // Speaker cone (diamond pointing right)
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
            // Near wave (arc ])
            g.fill(ox+5, oy+2, ox+6, oy+3,  col);
            g.fill(ox+6, oy+3, ox+7, oy+7,  col);
            g.fill(ox+5, oy+7, ox+6, oy+8,  col);
            // Far wave (larger arc ])
            g.fill(ox+7, oy+1, ox+8, oy+2,  col);
            g.fill(ox+8, oy+2, ox+9, oy+8,  col);
            g.fill(ox+7, oy+8, ox+8, oy+9,  col);
        } else {
            // Red X (muted)
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
        this.filterBar.searchBox().setValue("");
        savedCategory = null; savedObject = null; savedSearch = ""; savedScroll = 0.0;
        this.filterBar.category().clearSelection();
        this.filterBar.object().clearSelection();
        this.filterBar.object().setActive(false);
        refreshList();
    }

    // ── Import helpers ────────────────────────────────────────────────────────

    /**
     * Detects whether a JSON config file contains block IDs or sound IDs by
     * inspecting the first key in the map.
     * Sound IDs contain a dot after the namespace colon ("minecraft:block.piston.extend");
     * block IDs do not ("minecraft:piston"). Falls back to false (sounds) on any error.
     */
    private void importConfigFrom(java.nio.file.Path src) {
        if (isBlockConfig(src)) {
            VolumeConfig.BLOCKS.importFrom(src);
        } else {
            VolumeConfig.SOUNDS.importFrom(src);
        }
        refreshList();
    }

    /** The system file dialog could not be shown: fall back to typing the path in-game. */
    private void openPathFallback() {
        ImportConfigScreen.ImportType type = this.selectedCategory == SoundCategory.BLOCK
                ? ImportConfigScreen.ImportType.BLOCKS : ImportConfigScreen.ImportType.SOUNDS;
        this.minecraft.gui.setScreen(new ImportConfigScreen(this, type, this::refreshList));
    }

    private static boolean isBlockConfig(java.nio.file.Path file) {
        try {
            JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (obj.entrySet().isEmpty()) return false;
            String firstKey = obj.entrySet().iterator().next().getKey();
            int colon = firstKey.indexOf(':');
            String afterColon = colon >= 0 ? firstKey.substring(colon + 1) : firstKey;
            return !afterColon.contains(".");
        } catch (Exception e) {
            return false;
        }
    }

    // ── Close ─────────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        saveState();
        this.minecraft.gui.setScreen(this.parent);
    }
}
