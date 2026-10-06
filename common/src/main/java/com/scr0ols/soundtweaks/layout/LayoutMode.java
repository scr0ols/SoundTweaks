package com.scr0ols.soundtweaks.layout;

/** Named layout constants and the two layout modes. Values mirror the approved wireframe breakpoints. */
public enum LayoutMode {
    NORMAL,
    COMPACT;

    public static final int MAX_W = 960;
    public static final int MIN_W = 320;
    public static final int MIN_H = 240;
    public static final int RAIL_W = 24;
    public static final int BUTTON_H = 20;

    /** Below this frame width the favourites sidebar becomes the thin rail. */
    public static final int RAIL_BP = 560;
    /** Below this frame width the presets screen shows list and detail as two steps. */
    public static final int TWO_STEP_BP = 500;
    /** Margin kept free around dialog panels. */
    public static final int DIALOG_MARGIN = 8;
}
