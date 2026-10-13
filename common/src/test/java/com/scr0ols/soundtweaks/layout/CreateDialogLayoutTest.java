package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateDialogLayoutTest {

    private static final int[][] SIZES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}};

    @Test
    void keepsThe242WidePanelWhenItFits() {
        assertEquals(242, CreateDialogLayout.compute(640, 360).panel().w());
    }

    @Test
    void panelNarrowsToScreenMinusSixteen() {
        Rect p = CreateDialogLayout.compute(200, 240).panel();
        assertEquals(184, p.w());
        assertEquals(8, p.x());
    }

    @Test
    void panelIsCentredOnBothAxes() {
        for (int[] s : SIZES) {
            Rect p = CreateDialogLayout.compute(s[0], s[1]).panel();
            assertTrue(Math.abs(p.x() - (s[0] - p.right())) <= 1, "x at " + s[0]);
            assertTrue(Math.abs(p.y() - (s[1] - p.bottom())) <= 1, "y at " + s[1]);
        }
    }

    @Test
    void controlsStayInsideThePanelWithoutOverlap() {
        for (int[] s : SIZES) {
            CreateDialogLayout l = CreateDialogLayout.compute(s[0], s[1]);
            Rect p = l.panel();
            List<Rect> controls = List.of(l.nameBox(), l.confirmButton(), l.cancelButton());
            for (Rect r : controls) {
                assertTrue(r.x() >= p.x() && r.right() <= p.right() && r.y() >= p.y() && r.bottom() <= p.bottom());
                assertTrue(r.h() >= 18);
            }
            assertFalse(l.nameBox().intersects(l.confirmButton()));
            assertFalse(l.confirmButton().intersects(l.cancelButton()));
            assertFalse(l.label().intersects(l.nameBox()));
        }
    }
}
