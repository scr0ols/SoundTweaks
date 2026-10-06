package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogLayoutTest {

    @Test
    void preferredSizeIsKeptWhenItFits() {
        assertEquals(new Rect(120, 70, 400, 220), DialogLayout.panel(640, 360, 400, 220));
    }

    @Test
    void widthShrinksToScreenMinusSixteen() {
        Rect r = DialogLayout.panel(320, 240, 400, 200);
        assertEquals(304, r.w());
        assertEquals(8, r.x());
    }

    @Test
    void heightShrinksToScreenMinusSixteen() {
        Rect r = DialogLayout.panel(640, 240, 300, 400);
        assertEquals(224, r.h());
        assertEquals(8, r.y());
    }

    @Test
    void panelIsCentredOnBothAxes() {
        Rect r = DialogLayout.panel(427, 240, 300, 100);
        assertEquals(63, r.x());
        assertEquals(70, r.y());
    }

    @Test
    void panelAlwaysStaysInsideTheScreenAtTheSupportedSizes() {
        int[][] sizes = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}};
        for (int[] s : sizes) {
            Rect r = DialogLayout.panel(s[0], s[1], 420, 260);
            String size = s[0] + "x" + s[1];
            assertTrue(r.x() >= 0 && r.y() >= 0, size);
            assertTrue(r.right() <= s[0] && r.bottom() <= s[1], size);
        }
    }

    @Test
    void frameOverloadCentresInsideTheFrameNotTheWindow() {
        Rect r = DialogLayout.panel(ScreenFrame.of(1920, 1080), 2000, 100);
        assertEquals(944, r.w());
        assertEquals(488, r.x());
        assertEquals(490, r.y());
    }
}
