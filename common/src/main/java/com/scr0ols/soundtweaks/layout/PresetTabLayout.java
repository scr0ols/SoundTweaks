package com.scr0ols.soundtweaks.layout;

/**
 * Geometry inside the content area of the Color, Rename and Shortcut tabs of the presets screen.
 * Everything is derived from the content rect so boxes shrink with the panel and stay centred.
 */
public record PresetTabLayout(
        Rect colorBox, int colorGridX, int colorGridY, Rect customSwatch, Rect customLabel, Rect hexBox,
        Rect renameBox, Rect renameSave, Rect renameClear,
        Rect shortcutBox, int shortcutCaptureY, int shortcutSavedY, Rect shortcutHint,
        int centerX) {

    public static final int SQ = 22;
    public static final int GAP = 3;
    public static final int COLS = 6;
    public static final int PALETTE_SIZE = 18;
    public static final int GRID_W = COLS * SQ + (COLS - 1) * GAP;

    private static final int COLOR_BOX_MAX_W = 340;
    private static final int COLOR_BOX_H = 138;
    private static final int GRID_TOP = 20;
    private static final int CUSTOM_TOP = GRID_TOP + 3 * (SQ + GAP) + 12;
    private static final int CUSTOM_LABEL_W = 36;
    private static final int HEX_MAX_W = 110;
    private static final int HEX_H = 18;

    private static final int RENAME_MAX_W = 320;
    private static final int RENAME_BOX_TOP = 18;
    private static final int RENAME_BUTTONS_TOP = 44;

    private static final int SHORTCUT_BOX_H = 96;
    private static final int SHORTCUT_CAPTURE_TOP = 14;
    private static final int SHORTCUT_SAVED_TOP = 34;
    private static final int SHORTCUT_HINT_TOP = 50;
    private static final int SHORTCUT_HINT_H = 44;

    public static PresetTabLayout compute(Rect content) {
        int cx = content.x() + content.w() / 2;
        int cy = content.y();

        int colorW = Math.min(COLOR_BOX_MAX_W, content.w() - 8);
        Rect colorBox = new Rect(cx - colorW / 2, cy + 2, colorW, COLOR_BOX_H);
        int gridX = cx - GRID_W / 2;
        int gridY = cy + GRID_TOP;
        int customY = cy + CUSTOM_TOP;
        Rect customSwatch = new Rect(gridX, customY, SQ, SQ);
        Rect customLabel = new Rect(gridX + SQ + 6, customY + (SQ - 8) / 2, CUSTOM_LABEL_W, 8);
        int hexX = customLabel.right() + 4;
        Rect hexBox = new Rect(hexX, customY + 2, Math.max(0, Math.min(HEX_MAX_W, colorBox.right() - 4 - hexX)), HEX_H);

        int renameW = Math.min(RENAME_MAX_W, content.w() - 16);
        int renameX = cx - renameW / 2;
        int half = renameW / 2 - 2;
        Rect renameBox = new Rect(renameX, cy + RENAME_BOX_TOP, renameW, LayoutMode.BUTTON_H);
        Rect renameSave = new Rect(renameX, cy + RENAME_BUTTONS_TOP, half, LayoutMode.BUTTON_H);
        Rect renameClear = new Rect(renameX + half + 4, cy + RENAME_BUTTONS_TOP, half, LayoutMode.BUTTON_H);

        int shortcutW = Math.min(COLOR_BOX_MAX_W, content.w() - 8);
        Rect shortcutBox = new Rect(cx - shortcutW / 2, cy + 2, shortcutW, SHORTCUT_BOX_H);
        Rect shortcutHint = new Rect(shortcutBox.x() + 8, cy + SHORTCUT_HINT_TOP,
                Math.max(0, shortcutW - 16), SHORTCUT_HINT_H);

        return new PresetTabLayout(colorBox, gridX, gridY, customSwatch, customLabel, hexBox,
                renameBox, renameSave, renameClear,
                shortcutBox, cy + SHORTCUT_CAPTURE_TOP, cy + SHORTCUT_SAVED_TOP, shortcutHint, cx);
    }

    /** Palette swatch {@code index} (row-major, {@link #COLS} per row). */
    public Rect swatch(int index) {
        int col = index % COLS;
        int row = index / COLS;
        return new Rect(colorGridX + col * (SQ + GAP), colorGridY + row * (SQ + GAP), SQ, SQ);
    }
}
