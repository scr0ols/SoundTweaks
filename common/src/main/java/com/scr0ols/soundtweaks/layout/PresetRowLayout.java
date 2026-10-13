package com.scr0ols.soundtweaks.layout;

/**
 * Geometry of one row of the preset list: ON/OFF badge, name, optional shortcut label and favourite star.
 * The name is cut first; the shortcut label is dropped when the name would get too narrow.
 */
public record PresetRowLayout(Rect badge, int nameX, int nameMaxW, int shortcutX, int shortcutW, Rect star) {

    public static final int ROW_H = 24;

    private static final int BADGE_X = 10;
    private static final int BADGE_Y = 6;
    private static final int BADGE_W = 22;
    private static final int BADGE_H = 11;
    private static final int NAME_X = 38;
    private static final int STAR_FROM_RIGHT = 22;
    private static final int STAR_Y = 4;
    private static final int STAR_W = 18;
    private static final int STAR_H = 16;
    private static final int GAP = 4;
    private static final int MIN_NAME_W = 30;

    /**
     * @param nameTextW     full width of the name
     * @param shortcutTextW full width of the shortcut label, 0 when the preset has none
     */
    public static PresetRowLayout compute(int rowX, int rowY, int rowW, int nameTextW, int shortcutTextW) {
        Rect badge = new Rect(rowX + BADGE_X, rowY + BADGE_Y, BADGE_W, BADGE_H);
        Rect star = new Rect(rowX + rowW - STAR_FROM_RIGHT, rowY + STAR_Y, STAR_W, STAR_H);
        int nameX = rowX + NAME_X;
        int avail = Math.max(0, star.x() - GAP - nameX);

        if (shortcutTextW > 0 && avail - shortcutTextW >= MIN_NAME_W) {
            int nameMaxW = avail - shortcutTextW;
            return new PresetRowLayout(badge, nameX, nameMaxW,
                    nameX + Math.min(nameTextW, nameMaxW), shortcutTextW, star);
        }
        return new PresetRowLayout(badge, nameX, avail, nameX, 0, star);
    }

    public boolean hasShortcut() {
        return shortcutW > 0;
    }
}
