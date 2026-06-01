package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.BlockConfig;
import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.SoundConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.client.SoundDisplayHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.*;

public class SoundListWidget extends AbstractSelectionList<SoundListWidget.BaseEntry> {

    private char   lastJumpLetter    = 0;
    private int    lastJumpIndex     = -1;
    private double trackedScrollAmount = 0.0;

    @Override
    public void setScrollAmount(double amount) {
        super.setScrollAmount(amount);
        this.trackedScrollAmount = amount;
    }

    public double getScrollAmount() { return trackedScrollAmount; }

    public SoundListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    /**
     * Repopula a lista com sons e blocos.
     * @param detailed  true = vista detalhada (grupos + filhos); false = vista simples (só grupos)
     */
    public void updateList(List<String> sounds, List<String> blockIds, boolean detailed) {
        this.clearEntries();

        // Calcular grupos entre os sons filtrados
        Map<String, List<String>> groupMap = SoundRegistry.getGroups(sounds);
        Set<String> groupedIds = new HashSet<>();
        groupMap.values().forEach(groupedIds::addAll);

        // Cada "slot" agrupa a entrada principal + eventuais filhos (só em detailed)
        // Object[]: {String sortKey, BaseEntry primary, List<BaseEntry> children}
        List<Object[]> slots = new ArrayList<>();

        // Grupos
        for (Map.Entry<String, List<String>> e : groupMap.entrySet()) {
            GroupEntry ge = new GroupEntry(e.getKey(), e.getValue());
            List<BaseEntry> children = new ArrayList<>();
            if (detailed) {
                List<String> sortedChildren = new ArrayList<>(e.getValue());
                sortedChildren.sort(Comparator.comparing(
                        SoundDisplayHelper::getDisplayName, String.CASE_INSENSITIVE_ORDER));
                for (String id : sortedChildren) children.add(new SoundEntry(id, true));
            }
            slots.add(new Object[]{ ge.getDisplayName().toLowerCase(), ge, children });
        }

        // Sons sem grupo
        for (String id : sounds) {
            if (!groupedIds.contains(id)) {
                SoundEntry se = new SoundEntry(id, false);
                slots.add(new Object[]{ se.getDisplayName().toLowerCase(), se, new ArrayList<>() });
            }
        }

        // Blocos
        for (String blockId : blockIds) {
            BlockEntry be = new BlockEntry(blockId);
            slots.add(new Object[]{ be.getDisplayName().toLowerCase(), be, new ArrayList<>() });
        }

        // Ordenar: entradas numéricas (ex: "11", "13") depois do Z
        slots.sort((a, b) -> {
            String ka = (String) a[0];
            String kb = (String) b[0];
            boolean aD = !ka.isEmpty() && Character.isDigit(ka.charAt(0));
            boolean bD = !kb.isEmpty() && Character.isDigit(kb.charAt(0));
            if (aD != bD) return aD ? 1 : -1;
            return ka.compareTo(kb);
        });

        for (Object[] slot : slots) {
            this.addEntry((BaseEntry) slot[1]);
            @SuppressWarnings("unchecked")
            List<BaseEntry> children = (List<BaseEntry>) slot[2];
            children.forEach(this::addEntry);
        }

        this.lastJumpLetter = 0;
        this.lastJumpIndex  = -1;
    }

    public void updateList(List<String> sounds, List<String> blockIds) {
        updateList(sounds, blockIds, false);
    }

    public void updateList(List<String> sounds) {
        updateList(sounds, List.of(), false);
    }

    public boolean jumpToLetter(char c) {
        char upper = Character.toUpperCase(c);
        var entries = this.children();

        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            BaseEntry entry = entries.get(i);
            if (!entry.isSelectable()) continue;
            String name = entry.getDisplayName();
            if (!name.isEmpty() && Character.toUpperCase(name.charAt(0)) == upper) matches.add(i);
        }
        if (matches.isEmpty()) return false;

        int nextMatchPos;
        if (upper != lastJumpLetter) {
            nextMatchPos = 0;
        } else {
            int cur = matches.indexOf(lastJumpIndex);
            nextMatchPos = (cur + 1) % matches.size();
        }

        int targetIndex = matches.get(nextMatchPos);
        lastJumpLetter = upper;
        lastJumpIndex  = targetIndex;
        this.setSelected(entries.get(targetIndex));
        this.setScrollAmount((double) targetIndex * 20);
        return true;
    }

    @Override public int getRowWidth() { return this.width - 20; }

    @Override
    protected int scrollBarX() { return this.getX() + this.width - 6; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        BaseEntry selected = this.getSelected();
        if (selected != null && selected.isSelectable()) {
            selected.adjustVolume((float)(scrollY * 0.05));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {}

    // =========================================================================
    // Utilitário de cor por volume
    // =========================================================================

    static int volumeColor(float volume) {
        return volume <= 0.0f ? 0xFFFF4444 : 0xFFFFFFFF;
    }

    // =========================================================================
    // BaseEntry
    // =========================================================================

    public abstract static class BaseEntry extends AbstractSelectionList.Entry<BaseEntry> {
        public abstract String getDisplayName();
        public abstract void   adjustVolume(float delta);
        public boolean isSelectable() { return true; }
        public void updateNarration(NarrationElementOutput o) {}
    }

    // =========================================================================
    // GroupEntry — entrada mestre que controla um grupo de sons
    // =========================================================================

    public class GroupEntry extends BaseEntry {

        private final List<String> children;
        private final String       displayName;
        private final GroupSliderButton slider;
        private long lastClickTime = 0;
        private static final long DOUBLE_CLICK_MS = 300;

        public GroupEntry(String groupKey, List<String> children) {
            this.children    = children;
            this.displayName = SoundDisplayHelper.getObjectName("x:" + groupKey + ".x");
            float minVol = children.stream().map(SoundConfig::getVolume)
                    .min(Float::compare).orElse(1.0f);
            this.slider = new GroupSliderButton(children, 0, 0, 90, 14, minVol);
        }

        @Override public String getDisplayName() { return displayName; }

        @Override
        public void adjustVolume(float delta) {
            float cur = children.stream().map(SoundConfig::getVolume)
                    .min(Float::compare).orElse(1.0f);
            slider.setSliderValue(Mth.clamp(cur + delta, 0.0f, 1.0f));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float a) {
            int rowW = SoundListWidget.this.getRowWidth();
            // Fundo subtil para distinguir grupos
            if (SoundListWidget.this.getSelected() == this) {
                graphics.fill(getX(), getY(), getX() + rowW, getY() + 20, 0x556688FF);
            } else {
                graphics.fill(getX(), getY(), getX() + rowW, getY() + 20, 0x22AAAAFF);
            }

            float vol = children.stream().map(SoundConfig::getVolume)
                    .min(Float::compare).orElse(1.0f);
            // Prefixo "* " indica que é um grupo
            graphics.text(SoundListWidget.this.minecraft.font,
                    "* " + displayName, getX() + 4, getY() + 5, volumeColor(vol));

            slider.setX(getX() + rowW - 94);
            slider.setY(getY() + 3);
            slider.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
            SoundListWidget.this.setSelected(this);
            long now = Util.getMillis();
            boolean dbl = (now - lastClickTime) < DOUBLE_CLICK_MS;
            lastClickTime = now;
            if (dbl) {
                float cur = children.stream().map(SoundConfig::getVolume)
                        .min(Float::compare).orElse(1.0f);
                slider.setSliderValue(cur >= 1.0f ? 0.0f : 1.0f);
                return true;
            }
            return slider.mouseClicked(event, consumed);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dX, double dY) {
            return slider.mouseDragged(event, dX, dY);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            return slider.mouseReleased(event);
        }
    }

    // =========================================================================
    // SoundEntry — linha de som individual
    // =========================================================================

    public class SoundEntry extends BaseEntry {

        private final String soundId;
        private final String displayName;
        private final SoundSliderButton slider;
        private final boolean indented;
        private long lastClickTime = 0;
        private static final long DOUBLE_CLICK_MS = 300;

        public SoundEntry(String soundId) { this(soundId, false); }

        public SoundEntry(String soundId, boolean indented) {
            this.soundId     = soundId;
            this.displayName = SoundDisplayHelper.getDisplayName(soundId);
            this.slider      = new SoundSliderButton(soundId, 0, 0, 90, 14);
            this.indented    = indented;
        }

        @Override public String getDisplayName() { return displayName; }

        @Override
        public void adjustVolume(float delta) {
            float newVal = Mth.clamp(SoundConfig.getVolume(soundId) + delta, 0.0f, 1.0f);
            slider.setSliderValue(newVal);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float a) {
            int indent = indented ? 12 : 0;
            int rowW   = SoundListWidget.this.getRowWidth();
            if (SoundListWidget.this.getSelected() == this) {
                graphics.fill(getX() + indent, getY(),
                        getX() + rowW, getY() + 20, 0x44FFFFFF);
            }
            slider.syncFromConfig(); // mantém coerência quando o grupo sobrescreve o filho
            graphics.text(SoundListWidget.this.minecraft.font, displayName,
                    getX() + 4 + indent, getY() + 5, volumeColor(SoundConfig.getVolume(soundId)));
            slider.setX(getX() + rowW - 94);
            slider.setY(getY() + 3);
            slider.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
            SoundListWidget.this.setSelected(this);
            long now = Util.getMillis();
            boolean dbl = (now - lastClickTime) < DOUBLE_CLICK_MS;
            lastClickTime = now;
            if (dbl) {
                float newVal = (SoundConfig.getVolume(soundId) >= 1.0f) ? 0.0f : 1.0f;
                slider.setSliderValue(newVal);
                return true;
            }
            return slider.mouseClicked(event, consumed);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dX, double dY) {
            return slider.mouseDragged(event, dX, dY);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) { return slider.mouseReleased(event); }
    }

    // =========================================================================
    // BlockEntry — linha de bloco sem evento (BlockConfig)
    // =========================================================================

    public class BlockEntry extends BaseEntry {

        private final String blockId;
        private final String displayName;
        private final BlockSliderButton slider;
        private long lastClickTime = 0;
        private static final long DOUBLE_CLICK_MS = 300;

        public BlockEntry(String blockId) {
            this.blockId     = blockId;
            this.displayName = MissingBlockRegistry.getDisplayName(blockId);
            this.slider      = new BlockSliderButton(blockId, 0, 0, 90, 14);
        }

        @Override public String getDisplayName() { return displayName; }

        @Override
        public void adjustVolume(float delta) {
            float newVal = Mth.clamp(BlockConfig.getVolume(blockId) + delta, 0.0f, 1.0f);
            slider.setSliderValue(newVal);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float a) {
            int rowW = SoundListWidget.this.getRowWidth();
            if (SoundListWidget.this.getSelected() == this) {
                graphics.fill(getX(), getY(), getX() + rowW, getY() + 20, 0x44FFFFFF);
            }
            graphics.text(SoundListWidget.this.minecraft.font, displayName,
                    getX() + 4, getY() + 5, volumeColor(BlockConfig.getVolume(blockId)));
            slider.setX(getX() + rowW - 94);
            slider.setY(getY() + 3);
            slider.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
            SoundListWidget.this.setSelected(this);
            long now = Util.getMillis();
            boolean dbl = (now - lastClickTime) < DOUBLE_CLICK_MS;
            lastClickTime = now;
            if (dbl) {
                float newVal = (BlockConfig.getVolume(blockId) >= 1.0f) ? 0.0f : 1.0f;
                slider.setSliderValue(newVal);
                return true;
            }
            return slider.mouseClicked(event, consumed);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dX, double dY) {
            return slider.mouseDragged(event, dX, dY);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) { return slider.mouseReleased(event); }
    }

    // =========================================================================
    // DividerEntry — separador visual não seleccionável
    // =========================================================================

    public static class DividerEntry extends BaseEntry {
        @Override public String  getDisplayName()     { return ""; }
        @Override public void    adjustVolume(float d) {}
        @Override public boolean isSelectable()        { return false; }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float a) {
            int midY = getY() + 10;
            graphics.fill(getX() + 4, midY, getX() + 180, midY + 1, 0xFF555555);
        }

        @Override public boolean mouseClicked(MouseButtonEvent e, boolean c)  { return false; }
        @Override public boolean mouseDragged(MouseButtonEvent e, double x, double y) { return false; }
        @Override public boolean mouseReleased(MouseButtonEvent e) { return false; }
    }
}
