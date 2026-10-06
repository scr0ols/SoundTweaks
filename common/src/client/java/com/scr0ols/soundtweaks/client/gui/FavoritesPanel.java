package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.layout.MainScreenLayout;
import com.scr0ols.soundtweaks.layout.Rect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Draws and hit-tests the favourites sidebar and the thin rail, using the rects of a {@link MainScreenLayout}. */
final class FavoritesPanel {

    private static final int PANEL_BG = 0x771A1A1E;
    private static final int HEADER_BG = 0xFF1A1A2E;
    private static final int HEADER_BG_HOVER = 0xFF222233;
    private static final int HEADER_LINE = 0xFF444466;
    private static final int ON_TEXT = 0xFF88FF88;

    private FavoritesPanel() {}

    static void render(GuiGraphicsExtractor g, Font font, MainScreenLayout layout,
                       List<PresetConfig.Preset> favs, int mx, int my) {
        switch (layout.panel()) {
            case SIDEBAR -> renderSidebar(g, font, layout, favs, mx, my);
            case RAIL -> renderRail(g, font, layout, favs, mx, my);
            case NONE -> { }
        }
    }

    /** The favourite under the mouse, or null. */
    @Nullable
    static PresetConfig.Preset presetAt(MainScreenLayout layout, List<PresetConfig.Preset> favs, double mx, double my) {
        List<Rect> rects = layout.favorites();
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            if (r.contains((int) mx, (int) my)) return favs.get(layout.firstFavorite() + i);
        }
        return null;
    }

    private static void renderSidebar(GuiGraphicsExtractor g, Font font, MainScreenLayout layout,
                                      List<PresetConfig.Preset> favs, int mx, int my) {
        Rect p = layout.panelBounds();
        Rect head = layout.panelHeader();
        int bottom = layout.list().bottom();
        g.fill(p.x(), 0, p.right(), bottom, PANEL_BG);
        g.fill(p.x() - 1, 0, p.x(), bottom, 0xFF333355);

        boolean hovHeader = head.contains(mx, my) && my < 22;
        g.fill(head.x(), 0, head.right(), head.h() + 1, hovHeader ? HEADER_BG_HOVER : HEADER_BG);
        g.centeredText(font, I18n.get("soundtweaks.presets.title"), head.x() + head.w() / 2, 8, 0xFFDDDDDD);
        g.text(font, "◄", head.x() + 4, 8, hovHeader ? 0xFFFFFFFF : 0xFF888899);
        g.fill(head.x(), head.h(), head.right(), head.h() + 1, HEADER_LINE);
        drawFooterLine(g, p.x(), p.right(), bottom);

        if (favs.isEmpty()) {
            int y = MainScreenLayout.FILTER_Y + 4;
            g.centeredText(font, GuiText.ellipsize(font, I18n.get("soundtweaks.gui.no_favorites"), p.w() - 8),
                    p.x() + p.w() / 2, y, 0xFF555566);
            g.centeredText(font, GuiText.ellipsize(font, I18n.get("soundtweaks.gui.add_via_manage"), p.w() - 8),
                    p.x() + p.w() / 2, y + 12, 0xFF444455);
            return;
        }

        List<Rect> rects = layout.favorites();
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            PresetConfig.Preset preset = favs.get(layout.firstFavorite() + i);
            boolean active = PresetConfig.isActive(preset.id);
            int color = preset.argbColor();
            g.fill(r.x(), r.y(), r.right(), r.bottom(), (color & 0x00FFFFFF) | (active ? 0x55000000 : 0x1A000000));
            if (active) g.fill(r.x(), r.y(), r.x() + 3, r.bottom(), color | 0xFF000000);
            if (r.contains(mx, my)) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x22FFFFFF);

            int textX = r.x() + 9;
            int onW = active ? font.width("ON") + 6 : 4;
            String name = GuiText.ellipsize(font, preset.name, r.right() - onW - textX);
            g.text(font, name, textX, r.y() + 7, active ? 0xFFFFFFFF : 0xFF888888);
            if (active) g.text(font, "ON", r.right() - font.width("ON") - 3, r.y() + 7, ON_TEXT);
        }
    }

    private static void renderRail(GuiGraphicsExtractor g, Font font, MainScreenLayout layout,
                                   List<PresetConfig.Preset> favs, int mx, int my) {
        Rect p = layout.panelBounds();
        int bottom = layout.list().bottom();
        g.fill(p.x(), 0, p.right(), bottom, PANEL_BG);
        g.fill(p.x() - 1, 0, p.x(), bottom, 0xFF333355);
        g.fill(p.x(), 0, p.right(), layout.panelHeader().h() + 1, HEADER_BG);
        g.fill(p.x(), layout.panelHeader().h(), p.right(), layout.panelHeader().h() + 1, HEADER_LINE);
        g.centeredText(font, "★", p.x() + p.w() / 2, 8, 0xFF8888AA);
        drawFooterLine(g, p.x(), p.right(), bottom);

        List<Rect> rects = layout.favorites();
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            PresetConfig.Preset preset = favs.get(layout.firstFavorite() + i);
            boolean active = PresetConfig.isActive(preset.id);
            int color = preset.argbColor() | 0xFF000000;
            if (active) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), color);
                g.outline(r.x(), r.y(), r.w(), r.h(), ON_TEXT);
            } else {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), (color & 0x00FFFFFF) | 0x55000000);
                g.outline(r.x(), r.y(), r.w(), r.h(), 0xFF555566);
            }
            String initial = preset.name.isEmpty() ? "?" : preset.name.substring(0, 1).toUpperCase();
            g.centeredText(font, initial, r.x() + r.w() / 2, r.y() + 6, active ? 0xFFFFFFFF : 0xFF999999);
            if (r.contains(mx, my)) {
                g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x22FFFFFF);
                g.setTooltipForNextFrame(Component.literal(preset.name), mx, my);
            }
        }

        Rect more = layout.moreIndicator();
        if (!more.isEmpty()) {
            g.centeredText(font, "+" + layout.hiddenFavorites(), more.x() + more.w() / 2, more.y() + 1, 0xFFFFB86B);
        }
    }

    static void drawFooterLine(GuiGraphicsExtractor g, int x0, int x1, int footerTop) {
        g.fill(x0, footerTop, x1, footerTop + 1, 0xFF111111);
        g.fill(x0, footerTop + 1, x1, footerTop + 2, 0xFF444444);
        g.fill(x0, footerTop + 2, x1, footerTop + 3, 0xFF888888);
    }
}
