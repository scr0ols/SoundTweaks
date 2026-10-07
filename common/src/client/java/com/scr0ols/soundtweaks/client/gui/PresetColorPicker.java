package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colour swatch grid of the Color tab: 18 preset colours plus the "Custom" swatch. */
final class PresetColorPicker {

    /** Result of {@link #hit} when nothing was clicked. */
    static final int NO_HIT = -2;

    private static final int SQ = 22, GAP = 3, COLS = 6, CONTENT_Y = 56;
    private static final int GRID_Y = CONTENT_Y + 20;
    private static final int CUSTOM_Y = GRID_Y + 3 * (SQ + GAP) + 12;
    private static final int GRID_W = COLS * SQ + (COLS - 1) * GAP;

    private PresetColorPicker() {}

    static int gridX(int px, int pw) { return px + pw / 2 - GRID_W / 2; }

    static int customY() { return CUSTOM_Y; }

    /** Left edge of the custom-colour hex box, next to the "Custom" swatch. */
    static int hexBoxX(int px, int pw) { return gridX(px, pw) + 26; }

    static void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY,
                       int px, int pw, PresetConfig.Preset preset) {
        int cx2 = px + pw / 2;
        g.fill(cx2 - 170, CONTENT_Y + 2, cx2 + 170, CONTENT_Y + 140, 0xBB1A1A1A);
        int sq = SQ, gap = GAP, cols = COLS;
        int gridX = gridX(px, pw);
        int gridY = GRID_Y;

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
        int customY = CUSTOM_Y;
        boolean customSel = (preset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX);
        boolean customHov = mouseX >= gridX && mouseX < gridX + sq && mouseY >= customY && mouseY < customY + sq;
        if (preset.customColor != 0) {
            g.fill(gridX, customY, gridX + sq, customY + sq, preset.customColor | 0xFF000000);
        } else {
            g.fill(gridX, customY, gridX + sq, customY + sq, 0xFF1A1A2E);
            g.fill(gridX, customY, gridX+sq, customY+1, 0xFF556677); g.fill(gridX, customY+sq-1, gridX+sq, customY+sq, 0xFF556677);
            g.fill(gridX, customY, gridX+1, customY+sq, 0xFF556677); g.fill(gridX+sq-1, customY, gridX+sq, customY+sq, 0xFF556677);
            if (!customSel) g.centeredText(font, "+", gridX + sq / 2, customY + (sq - 8) / 2, 0xFF556677);
        }
        if (customSel) {
            g.fill(gridX-2, customY-2, gridX+sq+2, customY, 0xFFFFFFFF); g.fill(gridX-2, customY+sq, gridX+sq+2, customY+sq+2, 0xFFFFFFFF);
            g.fill(gridX-2, customY, gridX, customY+sq, 0xFFFFFFFF);      g.fill(gridX+sq, customY, gridX+sq+2, customY+sq, 0xFFFFFFFF);
        } else if (customHov) {
            g.fill(gridX-1, customY-1, gridX+sq+1, customY, 0xFF888888); g.fill(gridX-1, customY+sq, gridX+sq+1, customY+sq+1, 0xFF888888);
            g.fill(gridX-1, customY, gridX, customY+sq, 0xFF888888);      g.fill(gridX+sq, customY, gridX+sq+1, customY+sq, 0xFF888888);
        }
        g.text(font, "Custom", gridX + sq + 6, customY + (sq - 8) / 2, customSel ? 0xFFCCCCFF : 0xFF666688);
    }

    /**
     * Swatch under the mouse: a palette index, {@link PresetConfig#CUSTOM_COLOR_INDEX} for the
     * custom swatch, or {@link #NO_HIT}.
     */
    static int hit(double mx, double my, int px, int pw) {
        int gridX = gridX(px, pw);
        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            int col = i % COLS, row = i / COLS;
            int qx = gridX + col * (SQ + GAP), qy = GRID_Y + row * (SQ + GAP);
            if (mx >= qx && mx < qx + SQ && my >= qy && my < qy + SQ) return i;
        }
        if (mx >= gridX && mx < gridX + SQ && my >= CUSTOM_Y && my < CUSTOM_Y + SQ)
            return PresetConfig.CUSTOM_COLOR_INDEX;
        return NO_HIT;
    }

    /** Lightens a dark 0xRRGGBB colour in proportion to its darkness so text stays readable. */
    static int readableOnDark(int rgb) {
        int r = (rgb >> 16) & 0xFF, gc = (rgb >> 8) & 0xFF, bc = rgb & 0xFF;
        int lum = (r * 299 + gc * 587 + bc * 114) / 1000;
        if (lum >= 100) return rgb;
        float boost = (1f - lum / 100f) * 0.6f;
        r  = (int)(r  + (255 - r)  * boost);
        gc = (int)(gc + (255 - gc) * boost);
        bc = (int)(bc + (255 - bc) * boost);
        return (r << 16) | (gc << 8) | bc;
    }
}
