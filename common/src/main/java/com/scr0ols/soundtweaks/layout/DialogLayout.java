package com.scr0ols.soundtweaks.layout;

/** Sizes and centres dialog panels so they always fit the screen. */
public final class DialogLayout {

    private DialogLayout() {}

    /** Panel of at most {@code prefW x prefH}, kept {@link LayoutMode#DIALOG_MARGIN} from the edges, centred in the screen. */
    public static Rect panel(int screenW, int screenH, int prefW, int prefH) {
        return panel(new Rect(0, 0, screenW, screenH), prefW, prefH);
    }

    /** Same, but centred inside the frame instead of the whole window. */
    public static Rect panel(ScreenFrame frame, int prefW, int prefH) {
        return panel(frame.bounds(), prefW, prefH);
    }

    private static Rect panel(Rect area, int prefW, int prefH) {
        int margin = 2 * LayoutMode.DIALOG_MARGIN;
        int w = Math.max(0, Math.min(prefW, area.w() - margin));
        int h = Math.max(0, Math.min(prefH, area.h() - margin));
        return new Rect(area.x() + (area.w() - w) / 2, area.y() + (area.h() - h) / 2, w, h);
    }
}
