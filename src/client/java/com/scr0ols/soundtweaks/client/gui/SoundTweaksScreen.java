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

    // ── Favourites preset sidebar (right side) ────────────────────────────────
    /** Sidebar width — fits ~35 name characters. */
    private static final int SIDE_W   = 220;
    /** Tab width when the sidebar is closed. */
    private static final int TAB_W    = 18;
    /** Height of each preset button. */
    private static final int PRESET_H = 22;
    /** Y where preset buttons start (below the header). */
    private static final int SIDE_TOP = 26;
    /** Height of the Manage button at the bottom. */
    private static final int MANAGE_H = 20;

    @Nullable private final Screen parent;

    private SoundListWidget soundList;
    private EditBox         searchBox;
    private Button          clearButton;
    private Button          viewToggleButton;
    private Button          muteSoundsBtn;
    private Button          presetsBtn;
    // Static to persist across opens — the real state lives in VolumeResolver,
    // but this flag tracks what the button "did" (what is queued to be unmuted)
    private static boolean  muteSoundsActive = false;

    private FilterDropdown categoryDropdown;
    private FilterDropdown objectDropdown;

    @Nullable private SoundCategory selectedCategory = null;
    @Nullable private String        selectedObject   = null;
    private           String        searchQuery      = "";

    /** Width of the content area (accounting for sidebar open/closed). */
    private int contentW() { return sidebarOpen ? this.width - SIDE_W - 2 : this.width; }

    public SoundTweaksScreen(@Nullable Screen parent) {
        super(Component.translatable("soundtweaks.gui.title"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        int cw = contentW();

        // ── Row 1 (Y=4): [speaker] [Simple/Detail View] [Presets ▶/◄] ... title ...
        this.muteSoundsBtn = Button.builder(Component.empty(), btn -> toggleMuteVisible())
                .bounds(4, 2, 20, 20).build();
        this.muteSoundsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.mute_all")));
        this.addRenderableWidget(this.muteSoundsBtn);

        this.viewToggleButton = Button.builder(
                detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"),
                btn -> {
                    detailedView = !detailedView;
                    btn.setMessage(detailedView ? Component.translatable("soundtweaks.gui.view_detail") : Component.translatable("soundtweaks.gui.view_simple"));
                    refreshList();
                }
        ).bounds(28, 2, 78, 20).build();
        this.viewToggleButton.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.view_toggle")));
        this.addRenderableWidget(this.viewToggleButton);

        this.presetsBtn = Button.builder(
                Component.translatable("soundtweaks.presets.title"),
                btn -> toggleSidebar()
        ).bounds(110, 2, 68, 20).build();
        this.presetsBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.presets_sidebar")));
        this.addRenderableWidget(this.presetsBtn);

        // ── Row 2 (Y=22): [Category] [Object] [×] [search bar (fills remaining space)]
        this.categoryDropdown = new FilterDropdown(4, 26, 120,
                I18n.get("soundtweaks.gui.category"), this::onCategorySelected);
        populateCategoryDropdown();

        this.objectDropdown = new FilterDropdown(128, 26, 130,
                I18n.get("soundtweaks.gui.object"), this::onObjectSelected);
        this.objectDropdown.setActive(false);

        this.clearButton = Button.builder(Component.literal("x"), btn -> clearFilters())
                .bounds(262, 26, 20, 20).build();
        this.clearButton.setTooltip(Tooltip.create(Component.translatable("soundtweaks.gui.clear_filters")));
        this.addRenderableWidget(this.clearButton);

        int searchX = 286;
        int searchW = Math.max(60, cw - searchX - 4);
        this.searchBox = new EditBox(this.font, searchX, 26, searchW, 20,
                Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setHint(Component.translatable("soundtweaks.gui.search_hint"));
        this.searchBox.setResponder(q -> { this.searchQuery = q; refreshList(); });
        this.addRenderableWidget(this.searchBox);

        // ── Sound list (starts immediately below the filters)
        int listY = 50;
        this.soundList = new SoundListWidget(this.minecraft,
                cw, this.height - listY - 36, listY, 20);
        refreshList();
        this.addRenderableWidget(this.soundList);

        // Done button
        this.addRenderableWidget(
                Button.builder(Component.translatable("soundtweaks.gui.done"), btn -> this.onClose())
                        .bounds(cw / 2 + 5, this.height - 26, 120, 20)
                        .build()
        );

        // Import config from another instance via native file dialog
        var importCfgBtn = Button.builder(
                Component.translatable("soundtweaks.gui.import_config"),
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
                    if (isBlockConfig(src)) {
                        VolumeConfig.BLOCKS.importFrom(src);
                    } else {
                        VolumeConfig.SOUNDS.importFrom(src);
                    }
                    refreshList();
                }
        ).bounds(cw / 2 - 125, this.height - 26, 120, 20).build();
        importCfgBtn.setTooltip(Tooltip.create(Component.translatable("soundtweaks.tooltip.import_config")));
        this.addRenderableWidget(importCfgBtn);

        // Manage Presets button as a native widget (only when sidebar is open)
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
        // Header background band and separator — before super so it does not cover the buttons
        graphics.fill(0, 0, contentW(), 24, 0xFF1A1A2E);
        graphics.fill(0, 24, contentW(), 25, 0xFF444466);

        // Sidebar background before super — so the Manage button (widget) renders on top
        if (sidebarOpen) {
            graphics.fill(this.width - SIDE_W, 0, this.width, this.height - 36, 0x771A1A1E);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);

        // Title centred, but not overlapping the header buttons (which extend to x≈182)
        int titleMinX = 185;
        int titleCenterX = Math.max(titleMinX + this.font.width(I18n.get("soundtweaks.gui.title")) / 2,
                contentW() / 2);
        graphics.centeredText(this.font, I18n.get("soundtweaks.gui.title"),
                titleCenterX, 8, 0xFFFFFFFF);

        // Speaker icon on the mute/restore button
        if (this.muteSoundsBtn != null)
            drawSpeakerIcon(graphics, this.muteSoundsBtn.getX(), this.muteSoundsBtn.getY(),
                    this.muteSoundsBtn.getWidth(), this.muteSoundsBtn.getHeight(), muteSoundsActive);

        // Vertical separator line — only when sidebar is open (closed tab has its own separator)
        if (sidebarOpen) {
            int sepX = this.width - SIDE_W - 1;
            graphics.fill(sepX, 0, sepX + 1, this.height - 36, 0xFF333355);
        }

        // Footer — 3-pixel separator (stops before sidebar when open)
        int footerRight = sidebarOpen ? (this.width - SIDE_W) : this.width;
        graphics.fill(0, this.height - 36, footerRight, this.height - 35, 0xFF111111);
        graphics.fill(0, this.height - 35, footerRight, this.height - 34, 0xFF444444);
        graphics.fill(0, this.height - 34, footerRight, this.height - 33, 0xFF888888);
        int total = SoundRegistry.count();
        boolean hasFilter = selectedCategory != null || selectedObject != null || !searchQuery.isBlank();
        String countText = hasFilter
                ? I18n.get("soundtweaks.gui.sounds_filtered", getFilteredSounds().size(), total)
                : I18n.get("soundtweaks.gui.sounds", total);
        graphics.text(this.font, countText, 8, this.height - 22, 0xFFAAAAAA);

        // Sidebar
        renderFavoritesSidebar(graphics, mouseX, mouseY);

        // Dropdowns — always last (render on top of everything)
        this.categoryDropdown.render(graphics, mouseX, mouseY);
        this.objectDropdown.render(graphics, mouseX, mouseY);
    }

    // ── Favourites sidebar ────────────────────────────────────────────────────

    private void renderFavoritesSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!sidebarOpen) return;

        List<PresetConfig.Preset> favs = PresetConfig.getFavoritePresets();
        int sideX = this.width - SIDE_W;

        // Header — clickable to close
        boolean hovHeader = mouseX >= sideX && mouseY >= 0 && mouseY < 22;
        graphics.fill(sideX, 0, this.width, 24, hovHeader ? 0xFF222233 : 0xFF1A1A2E);
        graphics.centeredText(this.font, "Presets",
                sideX + SIDE_W / 2, 8, 0xFFDDDDDD);
        // Close arrow (◀) on the left corner of the header
        graphics.text(this.font, "◄", sideX + 4, 8, hovHeader ? 0xFFFFFFFF : 0xFF888899);
        graphics.fill(sideX, 24, this.width, 25, 0xFF444466);

        // Footer — 3-pixel separator to match the main footer
        graphics.fill(sideX, this.height - 36, this.width, this.height - 35, 0xFF111111);
        graphics.fill(sideX, this.height - 35, this.width, this.height - 34, 0xFF444444);
        graphics.fill(sideX, this.height - 34, this.width, this.height - 33, 0xFF888888);

        // Available area for presets (between header and Manage button — now a native widget)
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

            boolean active = PresetConfig.isActive(preset.id);
            int     color  = preset.argbColor();
            boolean hov    = mouseX >= sideX + 1 && mouseX < this.width - 1
                    && mouseY >= y && mouseY < y + PRESET_H;

            if (active) {
                graphics.fill(sideX + 1, y, this.width - 1, y + PRESET_H, (color & 0x00FFFFFF) | 0x55000000);
                graphics.fill(sideX + 1, y, sideX + 4, y + PRESET_H, color | 0xFF000000); // solid side accent
            } else {
                graphics.fill(sideX + 1, y, this.width - 1, y + PRESET_H, (color & 0x00FFFFFF) | 0x1A000000);
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

    // ── Mouse events ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        // Dropdowns take priority: the popup overlaps the soundList (y=46+)
        // and super.mouseClicked would pass the click to soundList before the dropdown
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

        // Sidebar header → close
        if (my < 22) {
            toggleSidebar();
            return true;
        }

        // Preset buttons
        List<PresetConfig.Preset> favs = PresetConfig.getFavoritePresets();
        int availableBottom = this.height - MANAGE_H - 8;
        int y = SIDE_TOP;
        for (PresetConfig.Preset preset : favs) {
            if (y + PRESET_H > availableBottom) break;
            if (my >= y && my < y + PRESET_H) {
                PresetConfig.setActive(preset.id, !PresetConfig.isActive(preset.id));
                return true;
            }
            y += PRESET_H + 1;
        }

        return true; // absorb remaining clicks on the sidebar
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

    // ── Dropdown callbacks ────────────────────────────────────────────────────

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
        // Sort by label — digits after Z (char '~' > 'Z' in ASCII)
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
        this.searchBox.setValue("");
        savedCategory = null; savedObject = null; savedSearch = ""; savedScroll = 0.0;
        this.categoryDropdown.clearSelection();
        this.objectDropdown.clearSelection();
        this.objectDropdown.setActive(false);
        refreshList();
    }

    // ── Import helpers ────────────────────────────────────────────────────────

    /**
     * Detects whether a JSON config file contains block IDs or sound IDs by
     * inspecting the first key in the map.
     * Sound IDs contain a dot after the namespace colon ("minecraft:block.piston.extend");
     * block IDs do not ("minecraft:piston"). Falls back to false (sounds) on any error.
     */
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
        savedCategory = this.selectedCategory;
        savedObject   = this.selectedObject;
        savedSearch   = this.searchQuery;
        savedScroll   = this.soundList != null ? this.soundList.getScrollAmount() : 0.0;
        this.minecraft.setScreen(this.parent);
    }
}
