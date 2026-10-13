package com.scr0ols.soundtweaks.layout;

/** The usable area of a screen: the whole window. Wide windows are stretched, not capped. */
public record ScreenFrame(Rect bounds) {

    public static ScreenFrame of(int width, int height) {
        return new ScreenFrame(new Rect(0, 0, width, height));
    }

    public boolean useRail() { return bounds.w() < LayoutMode.RAIL_BP; }

    public boolean twoStepPresets() { return bounds.w() < LayoutMode.TWO_STEP_BP; }

    public LayoutMode mode() { return useRail() ? LayoutMode.COMPACT : LayoutMode.NORMAL; }
}
