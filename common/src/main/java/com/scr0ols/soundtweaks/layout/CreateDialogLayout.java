package com.scr0ols.soundtweaks.layout;

/** Geometry of the "new preset" overlay: a panel centred on both axes, narrowed to fit the screen. */
public record CreateDialogLayout(Rect panel, Rect label, Rect nameBox, Rect confirmButton, Rect cancelButton) {

    public static final int PREF_W = 242;
    public static final int PREF_H = 72;
    private static final int PAD = 10;
    private static final int GAP = 4;
    private static final int BUTTON_H = 18;

    public static CreateDialogLayout compute(int screenW, int screenH) {
        Rect p = DialogLayout.panel(screenW, screenH, PREF_W, PREF_H);
        int innerX = p.x() + PAD;
        int innerW = Math.max(0, p.w() - 2 * PAD);
        int buttonW = Math.max(0, (innerW - GAP) / 2);
        int by = p.y() + PREF_H - PAD - BUTTON_H;

        return new CreateDialogLayout(
                p,
                new Rect(innerX, p.y() + 8, innerW, 10),
                new Rect(innerX, p.y() + 22, innerW, LayoutMode.BUTTON_H),
                new Rect(innerX, by, buttonW, BUTTON_H),
                new Rect(innerX + buttonW + GAP, by, buttonW, BUTTON_H));
    }
}
