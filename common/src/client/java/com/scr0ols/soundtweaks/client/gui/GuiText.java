package com.scr0ols.soundtweaks.client.gui;

import net.minecraft.client.gui.Font;

/** Text helpers shared by the GUI components. */
public final class GuiText {

    private static final String ELLIPSIS = "...";

    private GuiText() {}

    /** Returns {@code text} cut to fit {@code maxW} pixels, with "..." appended when it had to be cut. */
    public static String ellipsize(Font font, String text, int maxW) {
        if (font.width(text) <= maxW) return text;
        int ellipsisW = font.width(ELLIPSIS);
        String cut = text;
        while (!cut.isEmpty() && font.width(cut) + ellipsisW > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ELLIPSIS;
    }
}
