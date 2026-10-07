package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.layout.PresetTabLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colour swatch grid of the Color tab: the preset colours plus the "Custom" swatch. */
final class PresetColorPicker {

    /** Result of {@link #hit} when nothing was clicked. */
    static final int NO_HIT = -2;

    private PresetColorPicker() {}

    static void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY,
                       PresetTabLayout t, PresetConfig.Preset preset) {
        Rect box = t.colorBox();
        g.fill(box.x(), box.y(), box.right(), box.bottom(), 0xBB1A1A1A);

        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            Rect s = t.swatch(i);
            g.fill(s.x(), s.y(), s.right(), s.bottom(), PresetConfig.PRESET_COLORS[i] | 0xFF000000);
            if (i == preset.colorIndex) outline(g, s, 2, 0xFFFFFFFF);
            else if (s.contains(mouseX, mouseY)) outline(g, s, 1, 0xFF888888);
        }

        Rect custom = t.customSwatch();
        boolean customSel = (preset.colorIndex == PresetConfig.CUSTOM_COLOR_INDEX);
        if (preset.customColor != 0) {
            g.fill(custom.x(), custom.y(), custom.right(), custom.bottom(), preset.customColor | 0xFF000000);
        } else {
            g.fill(custom.x(), custom.y(), custom.right(), custom.bottom(), 0xFF1A1A2E);
            outline(g, custom, -1, 0xFF556677);
            if (!customSel) g.centeredText(font, "+", custom.x() + custom.w() / 2,
                    custom.y() + (custom.h() - 8) / 2, 0xFF556677);
        }
        if (customSel) outline(g, custom, 2, 0xFFFFFFFF);
        else if (custom.contains(mouseX, mouseY)) outline(g, custom, 1, 0xFF888888);

        Rect label = t.customLabel();
        g.text(font, "Custom", label.x(), label.y(), customSel ? 0xFFCCCCFF : 0xFF666688);
    }

    /**
     * Swatch under the mouse: a palette index, {@link PresetConfig#CUSTOM_COLOR_INDEX} for the
     * custom swatch, or {@link #NO_HIT}.
     */
    static int hit(double mx, double my, PresetTabLayout t) {
        int px = (int) Math.floor(mx), py = (int) Math.floor(my);
        for (int i = 0; i < PresetConfig.PRESET_COLORS.length; i++) {
            if (t.swatch(i).contains(px, py)) return i;
        }
        if (t.customSwatch().contains(px, py)) return PresetConfig.CUSTOM_COLOR_INDEX;
        return NO_HIT;
    }

    /** Border of {@code thickness} px drawn outside {@code r}; a negative thickness draws inside. */
    private static void outline(GuiGraphicsExtractor g, Rect r, int thickness, int argb) {
        int t = Math.abs(thickness);
        int o = thickness > 0 ? t : 0;
        int in = thickness > 0 ? 0 : t;
        int x0 = r.x() - o, y0 = r.y() - o, x1 = r.right() + o, y1 = r.bottom() + o;
        g.fill(x0, y0, x1, y0 + t, argb);
        g.fill(x0, y1 - t, x1, y1, argb);
        g.fill(x0, y0 + in, x0 + t, y1 - in, argb);
        g.fill(x1 - t, y0 + in, x1, y1 - in, argb);
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
