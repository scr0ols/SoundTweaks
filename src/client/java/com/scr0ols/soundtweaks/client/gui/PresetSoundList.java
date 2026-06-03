package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundCategory;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Lista de sons/blocos editável para um preset.
 * Reutilizável em PresetEditorScreen e no painel inline da PresetsScreen.
 */
class PresetSoundList extends AbstractSelectionList<PresetSoundList.BaseRow> {

    final PresetConfig.Preset preset;
    static boolean detailedView = false;

    private final int rowItemHeight;
    private char   lastJumpLetter = 0;
    private int    lastJumpIndex  = -1;
    private double trackedScroll  = 0.0;
    private boolean muteActive    = false;

    @Override
    public void setScrollAmount(double amount) { super.setScrollAmount(amount); trackedScroll = amount; }
    public double getScrollAmount() { return trackedScroll; }

    public PresetSoundList(Minecraft mc, PresetConfig.Preset preset,
                           int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
        this.preset        = preset;
        this.rowItemHeight = itemHeight;
    }

    // ── Toggle mute de todos os itens visíveis ────────────────────────────────

    public void toggleMute() {
        muteActive = !muteActive;
        for (var entry : children()) {
            if (entry instanceof SoundRow sr) {
                if (muteActive) preset.sounds.put(sr.soundId, 0.0f);
                else            preset.sounds.remove(sr.soundId);
            } else if (entry instanceof BlockRow br) {
                if (muteActive) preset.blocks.put(br.blockId, 0.0f);
                else            preset.blocks.remove(br.blockId);
            } else if (entry instanceof GroupRow gr) {
                for (String id : gr.childIds) {
                    if (muteActive) preset.sounds.put(id, 0.0f);
                    else            preset.sounds.remove(id);
                }
            }
        }
        PresetConfig.markDirty();
    }

    public boolean isMuteActive() { return muteActive; }

    // ── Jump to letter ────────────────────────────────────────────────────────

    public boolean jumpToLetter(char c) {
        char upper = Character.toUpperCase(c);
        var entries = this.children();
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String id = entries.get(i).getId();
            if (id.isEmpty()) continue;
            String name;
            if (entries.get(i) instanceof GroupRow gr)      name = gr.groupName;
            else if (entries.get(i) instanceof SoundRow sr) name = SoundDisplayHelper.getDisplayName(sr.soundId);
            else if (entries.get(i) instanceof BlockRow br) name = MissingBlockRegistry.getDisplayName(br.blockId);
            else continue;
            if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == upper) matches.add(i);
        }
        if (matches.isEmpty()) return false;
        int nextPos;
        if (upper != lastJumpLetter) nextPos = 0;
        else { int cur = matches.indexOf(lastJumpIndex); nextPos = (cur + 1) % matches.size(); }
        int idx = matches.get(nextPos);
        lastJumpLetter = upper; lastJumpIndex = idx;
        this.setSelected(entries.get(idx));
        this.setScrollAmount((double) idx * this.rowItemHeight);
        return true;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public void refresh() { refresh(null, null, ""); }

    public void refresh(@Nullable SoundCategory category, @Nullable String object, String query) {
        this.clearEntries();
        lastJumpLetter = 0; lastJumpIndex = -1;

        // Sons
        List<String> sounds = new ArrayList<>(
                category != null ? SoundRegistry.getByCategory(category) : SoundRegistry.getAll());

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
                }).toList();

        // Blocos
        boolean showBlocks = (category == null || category == SoundCategory.BLOCK
                || category == SoundCategory.REDSTONE) && object == null;
        List<String> blocks = new ArrayList<>();
        if (showBlocks) {
            blocks = new ArrayList<>(category == SoundCategory.REDSTONE
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
            Map<String, List<String>> groupMap = SoundRegistry.getGroups(sounds);
            Set<String> groupedIds = new HashSet<>();
            groupMap.values().forEach(groupedIds::addAll);

            List<Object[]> slots = new ArrayList<>();
            for (Map.Entry<String, List<String>> e : groupMap.entrySet()) {
                String dn = SoundDisplayHelper.getObjectName("x:" + e.getKey() + ".x");
                slots.add(new Object[]{ dn.toLowerCase(), new GroupRow(e.getKey(), e.getValue()) });
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
            for (String id : sounds) this.addEntry(new SoundRow(id));
        }

        if (!blocks.isEmpty()) {
            this.addEntry(new DividerRow());
            for (String id : blocks) this.addEntry(new BlockRow(id));
        }
    }

    @Override public int getRowWidth()      { return this.width - 20; }
    @Override protected int scrollBarX()    { return this.getX() + this.width - 6; }
    @Override public void updateWidgetNarration(NarrationElementOutput o) {}

    // ── Divisor ───────────────────────────────────────────────────────────────

    class DividerRow extends BaseRow {
        @Override public String  getId()       { return ""; }
        @Override public boolean hasOverride() { return false; }
        @Override public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float a) {
            int midY = getY() + 10;
            g.fill(getX() + 4, midY, getX() + PresetSoundList.this.getRowWidth() - 4, midY + 1, 0xFF555555);
        }
        @Override public boolean mouseClicked(MouseButtonEvent e, boolean c)          { return false; }
        @Override public boolean mouseDragged(MouseButtonEvent e, double x, double y) { return false; }
        @Override public boolean mouseReleased(MouseButtonEvent e)                    { return false; }
    }

    // ── GroupRow ──────────────────────────────────────────────────────────────

    class GroupRow extends BaseRow {
        final List<String> childIds;
        final String groupName;
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
            int rowW = PresetSoundList.this.getRowWidth();
            boolean sel = PresetSoundList.this.getSelected() == this;
            int pc = preset.argbColor() & 0x00FFFFFF;

            if (hasOverride()) {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, 0x18FFAA44);
            } else if (sel) {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, pc | 0x55000000);
            } else {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, pc | 0x22000000);
            }

            slider.syncDisplay();
            float vol = minChildVol();
            int col = vol <= 0f ? 0xFFFF4444 : hasOverride() ? 0xFFFFCC88 : 0xFFCCCCCC;
            g.text(PresetSoundList.this.minecraft.font, "* " + groupName, getX() + 4, getY() + 5, col);
            slider.setX(getX() + rowW - 94); slider.setY(getY() + 3);
            slider.extractRenderState(g, mx, my, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean consumed) {
            if (consumed) return false;
            PresetSoundList.this.setSelected(this);
            long now = Util.getMillis();
            if (now - lastClick < 300) { slider.setSliderValue(minChildVol() >= 1f ? 0f : 1f); return true; }
            lastClick = now;
            return slider.mouseClicked(e, consumed);
        }
        @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return slider.mouseDragged(e, dx, dy); }
        @Override public boolean mouseReleased(MouseButtonEvent e) { return slider.mouseReleased(e); }
    }

    // ── SoundRow ──────────────────────────────────────────────────────────────

    class SoundRow extends BaseRow {
        final String soundId;
        private final PresetSoundSliderButton slider;
        private long lastClick = 0;

        SoundRow(String id) {
            this.soundId = id;
            this.slider  = new PresetSoundSliderButton(preset, id, 0, 0, 90, 14);
        }

        @Override public String  getId()       { return soundId; }
        @Override public boolean hasOverride() { return preset.sounds.containsKey(soundId); }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float a) {
            int rowW = PresetSoundList.this.getRowWidth();
            boolean sel = PresetSoundList.this.getSelected() == this;
            int pc = preset.argbColor() & 0x00FFFFFF;

            if (hasOverride()) {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, 0x18FFAA44);
            } else if (sel) {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, pc | 0x55000000);
            }

            slider.syncDisplay();
            float vol = preset.sounds.getOrDefault(soundId, 1.0f);
            int col = vol <= 0f ? 0xFFFF4444 : hasOverride() ? 0xFFFFCC88 : 0xFFCCCCCC;
            g.text(PresetSoundList.this.minecraft.font,
                    SoundDisplayHelper.getDisplayName(soundId), getX() + 4, getY() + 5, col);
            slider.setX(getX() + rowW - 94); slider.setY(getY() + 3);
            slider.extractRenderState(g, mx, my, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean consumed) {
            if (consumed) return false;
            PresetSoundList.this.setSelected(this);
            long now = Util.getMillis();
            if (now - lastClick < 300) { slider.setSliderValue(preset.sounds.getOrDefault(soundId, 1f) >= 1f ? 0f : 1f); return true; }
            lastClick = now;
            return slider.mouseClicked(e, consumed);
        }
        @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return slider.mouseDragged(e, dx, dy); }
        @Override public boolean mouseReleased(MouseButtonEvent e) { return slider.mouseReleased(e); }
    }

    // ── BlockRow ──────────────────────────────────────────────────────────────

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
            int rowW = PresetSoundList.this.getRowWidth();
            boolean sel = PresetSoundList.this.getSelected() == this;
            int pc = preset.argbColor() & 0x00FFFFFF;

            if (hasOverride()) {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, 0x18FFAA44);
            } else if (sel) {
                g.fill(getX(), getY(), getX() + rowW, getY() + PresetSoundList.this.rowItemHeight, pc | 0x55000000);
            }

            slider.syncDisplay();
            float vol = preset.blocks.getOrDefault(blockId, 1.0f);
            int col = vol <= 0f ? 0xFFFF4444 : hasOverride() ? 0xFFFFCC88 : 0xFFCCCCCC;
            String name = MissingBlockRegistry.getDisplayName(blockId) + " [block]";
            g.text(PresetSoundList.this.minecraft.font, name, getX() + 4, getY() + 5, col);
            slider.setX(getX() + rowW - 94); slider.setY(getY() + 3);
            slider.extractRenderState(g, mx, my, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean consumed) {
            if (consumed) return false;
            PresetSoundList.this.setSelected(this);
            long now = Util.getMillis();
            if (now - lastClick < 300) { slider.setSliderValue(preset.blocks.getOrDefault(blockId, 1f) >= 1f ? 0f : 1f); return true; }
            lastClick = now;
            return slider.mouseClicked(e, consumed);
        }
        @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) { return slider.mouseDragged(e, dx, dy); }
        @Override public boolean mouseReleased(MouseButtonEvent e) { return slider.mouseReleased(e); }
    }

    // ── BaseRow ───────────────────────────────────────────────────────────────

    abstract class BaseRow extends AbstractSelectionList.Entry<BaseRow> {
        abstract String  getId();
        abstract boolean hasOverride();
        public void updateNarration(NarrationElementOutput o) {}
    }
}
