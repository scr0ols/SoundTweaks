package com.scr0ols.soundtweaks.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Language-independent on/off indicator: the standard power symbol, drawn pixel by pixel. */
final class PowerIcon {

    static final int SIZE = 12;

    private static final String[] PIXELS = {
            ".....##.....",
            "..##.##.##..",
            ".###.##.###.",
            ".##..##..##.",
            "##...##...##",
            "##...##...##",
            "##...##...##",
            "##........##",
            ".##......##.",
            ".###....###.",
            "..########..",
            "....####....",
    };

    private PowerIcon() {}

    static void draw(GuiGraphicsExtractor g, int x, int y, int argb) {
        for (int row = 0; row < SIZE; row++)
            for (int col = 0; col < SIZE; col++)
                if (PIXELS[row].charAt(col) == '#') g.fill(x + col, y + row, x + col + 1, y + row + 1, argb);
    }
}
