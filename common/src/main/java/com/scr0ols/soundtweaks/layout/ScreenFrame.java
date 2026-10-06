package com.scr0ols.soundtweaks.layout;

/** The usable area of a screen: at most {@link LayoutMode#MAX_W} wide and centred horizontally. */
public record ScreenFrame(Rect bounds) {

    public static ScreenFrame of(int width, int height) {
        int fw = Math.min(width, LayoutMode.MAX_W);
        return new ScreenFrame(new Rect((width - fw) / 2, 0, fw, height));
    }

    public boolean useRail() { return bounds.w() < LayoutMode.RAIL_BP; }

    public boolean twoStepPresets() { return bounds.w() < LayoutMode.TWO_STEP_BP; }

    public LayoutMode mode() { return useRail() ? LayoutMode.COMPACT : LayoutMode.NORMAL; }
}
