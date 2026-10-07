package com.scr0ols.soundtweaks.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Tab strip of the preset detail panel: Color | Rename | Shortcut | Edit Sounds | Delete. */
final class PresetTabs {

    static final String[] LABELS = {"Color", "Rename", "Shortcut", "Edit Sounds", "Delete"};
    private static final int[] WIDTHS = {64, 64, 72, 88, 60};
    static final int DELETE_INDEX = LABELS.length - 1;
    static final int HEIGHT = 20;
    private static final int GAP = 4;

    private PresetTabs() {}

    /**
     * @param activeIndex index of the highlighted tab, or -1 for none
     * @param y           top edge of the strip
     */
    static void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY,
                       int px, int y, int pc, int activeIndex) {
        int tabX = px + 4;
        for (int i = 0; i < LABELS.length; i++) {
            boolean isDelete = (i == DELETE_INDEX);
            boolean active   = !isDelete && (i == activeIndex);
            boolean hov      = mouseX >= tabX && mouseX < tabX + WIDTHS[i]
                    && mouseY >= y && mouseY < y + HEIGHT;

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

            g.fill(tabX, y, tabX + WIDTHS[i], y + HEIGHT, bg);
            g.fill(tabX, y, tabX + WIDTHS[i], y + 1, accent);
            g.centeredText(font, LABELS[i], tabX + WIDTHS[i] / 2, y + 6, textCol);
            tabX += WIDTHS[i] + GAP;
        }
    }

    /** Index of the tab under the mouse, or -1. */
    static int hit(double mx, double my, int px, int y) {
        int tabX = px + 4;
        for (int i = 0; i < LABELS.length; i++) {
            if (mx >= tabX && mx < tabX + WIDTHS[i] && my >= y && my < y + HEIGHT) return i;
            tabX += WIDTHS[i] + GAP;
        }
        return -1;
    }
}
