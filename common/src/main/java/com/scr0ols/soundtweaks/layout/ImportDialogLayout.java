package com.scr0ols.soundtweaks.layout;

/** Geometry of the import / export dialog: one panel centred on the screen and the rows inside it. */
public record ImportDialogLayout(Rect panel, Rect title, Rect description, Rect pathLabel, Rect pathBox,
                                 Rect folderLine, Rect feedback,
                                 Rect importButton, Rect exportButton, Rect cancelButton) {

    public static final int PREF_W = 420;
    public static final int PREF_H = 180;
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int MAX_BUTTON_W = 90;

    public static ImportDialogLayout compute(int screenW, int screenH) {
        Rect p = DialogLayout.panel(screenW, screenH, PREF_W, PREF_H);
        int innerX = p.x() + PAD;
        int innerW = Math.max(0, p.w() - 2 * PAD);

        int buttonW = Math.min(MAX_BUTTON_W, Math.max(0, (innerW - 2 * GAP) / 3));
        int rowW = buttonW * 3 + GAP * 2;
        int bx = p.x() + (p.w() - rowW) / 2;
        int by = p.bottom() - 30;

        return new ImportDialogLayout(
                p,
                new Rect(innerX, p.y() + 8, innerW, 10),
                new Rect(innerX, p.y() + 30, innerW, 28),
                new Rect(innerX, p.y() + 68, innerW, 10),
                new Rect(innerX, p.y() + 80, innerW, LayoutMode.BUTTON_H),
                new Rect(innerX, p.y() + 106, innerW, 10),
                new Rect(innerX, p.y() + 118, innerW, 24),
                new Rect(bx, by, buttonW, LayoutMode.BUTTON_H),
                new Rect(bx + buttonW + GAP, by, buttonW, LayoutMode.BUTTON_H),
                new Rect(bx + (buttonW + GAP) * 2, by, buttonW, LayoutMode.BUTTON_H));
    }
}
