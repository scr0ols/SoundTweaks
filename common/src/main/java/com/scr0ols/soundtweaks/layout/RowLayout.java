package com.scr0ols.soundtweaks.layout;

/** Horizontal geometry of one list row: where the name goes and where the slider sits. */
public record RowLayout(int nameX, int nameMaxW, int sliderX, int sliderW) {

    private static final int PAD = 4;
    private static final int SLIDER_MIN = 60;
    private static final int SLIDER_MAX = 90;
    private static final int SLIDER_PERCENT = 30;

    public static RowLayout compute(int rowX, int rowW, int indent) {
        int sliderW = Math.clamp(rowW * SLIDER_PERCENT / 100, SLIDER_MIN, SLIDER_MAX);
        int sliderX = rowX + rowW - PAD - sliderW;
        int nameX = rowX + PAD + indent;
        int nameMaxW = Math.max(0, sliderX - PAD - nameX);
        return new RowLayout(nameX, nameMaxW, sliderX, sliderW);
    }
}
