package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.layout.PresetsScreenLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/** Tab strip of the preset detail panel: Color | Rename | Shortcut | Edit Sounds | Delete. */
final class PresetTabs {

    private static final String[] LABEL_KEYS = {"soundtweaks.presets.tab.color", "soundtweaks.presets.tab.rename",
            "soundtweaks.presets.tab.shortcut", "soundtweaks.presets.tab.sounds", "soundtweaks.presets.tab.delete"};
    static final int DELETE_INDEX = LABEL_KEYS.length - 1;

    private PresetTabs() {}

    private static String label(int index) { return I18n.get(LABEL_KEYS[index]); }

    /** Measured width of each label, as {@link PresetsScreenLayout#compute} expects. */
    static int[] textWidths(Font font) {
        int[] widths = new int[LABEL_KEYS.length];
        for (int i = 0; i < LABEL_KEYS.length; i++) widths[i] = font.width(label(i));
        return widths;
    }

    /**
     * @param tabs        one rect per tab, from {@link PresetsScreenLayout#tabs()}
     * @param activeIndex index of the highlighted tab, or -1 for none
     */
    static void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY,
                       List<Rect> tabs, int pc, int activeIndex) {
        for (int i = 0; i < tabs.size(); i++) {
            Rect r = tabs.get(i);
            boolean isDelete = (i == DELETE_INDEX);
            boolean active   = !isDelete && (i == activeIndex);
            boolean hov      = r.contains(mouseX, mouseY);

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

            g.fill(r.x(), r.y(), r.right(), r.bottom(), bg);
            g.fill(r.x(), r.y(), r.right(), r.y() + 1, accent);
            g.centeredText(font, label(i), r.x() + r.w() / 2, r.y() + 6, textCol);
        }
    }

    /** Index of the tab under the mouse, or -1. */
    static int hit(double mx, double my, List<Rect> tabs) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).contains((int) Math.floor(mx), (int) Math.floor(my))) return i;
        }
        return -1;
    }
}
