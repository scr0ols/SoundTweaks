package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportDialogLayoutTest {

    private static final int[][] SIZES = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}};

    private static List<Rect> interactive(ImportDialogLayout l) {
        return List.of(l.pathBox(), l.importButton(), l.exportButton(), l.cancelButton());
    }

    @Test
    void keepsTheOldPanelWhenTheScreenIsBigEnough() {
        ImportDialogLayout l = ImportDialogLayout.compute(640, 360);
        assertEquals(new Rect(110, 90, 420, 180), l.panel());
    }

    @Test
    void panelShrinksToScreenMinusSixteenAt320() {
        ImportDialogLayout l = ImportDialogLayout.compute(320, 240);
        assertEquals(304, l.panel().w());
        assertEquals(8, l.panel().x());
    }

    @Test
    void everythingStaysInsideThePanel() {
        for (int[] s : SIZES) {
            ImportDialogLayout l = ImportDialogLayout.compute(s[0], s[1]);
            Rect p = l.panel();
            for (Rect r : List.of(l.title(), l.pathLabel(), l.pathBox(), l.importButton(), l.exportButton(),
                    l.cancelButton(), l.folderLine(), l.feedback(), l.description())) {
                assertTrue(r.x() >= p.x() && r.right() <= p.right() && r.y() >= p.y() && r.bottom() <= p.bottom(),
                        r + " outside " + p + " at " + s[0] + "x" + s[1]);
            }
        }
    }

    @Test
    void interactiveRectsDoNotOverlapAndButtonsAreTwentyHigh() {
        for (int[] s : SIZES) {
            List<Rect> rects = interactive(ImportDialogLayout.compute(s[0], s[1]));
            for (int i = 0; i < rects.size(); i++) {
                assertTrue(rects.get(i).h() >= LayoutMode.BUTTON_H);
                for (int j = i + 1; j < rects.size(); j++) {
                    assertFalse(rects.get(i).intersects(rects.get(j)), s[0] + "x" + s[1]);
                }
            }
        }
    }

    @Test
    void textRowsDoNotOverlapEachOtherOrTheControls() {
        for (int[] s : SIZES) {
            ImportDialogLayout l = ImportDialogLayout.compute(s[0], s[1]);
            List<Rect> rows = List.of(l.title(), l.description(), l.pathLabel(), l.pathBox(),
                    l.folderLine(), l.feedback(), l.importButton());
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    assertFalse(rows.get(i).intersects(rows.get(j)), i + " vs " + j + " at " + s[0] + "x" + s[1]);
                }
            }
        }
    }

    @Test
    void panelIsCentredOnBothAxes() {
        Rect p = ImportDialogLayout.compute(427, 240).panel();
        assertEquals(427 - p.right(), p.x());
        assertEquals(240 - p.bottom(), p.y());
    }
}
