package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetTabLayoutTest {

    private static final int[][] PANELS = {{299, 240}, {320, 240}, {427, 240}, {499, 300}, {640, 360}, {1280, 720}};

    /** Content area under one row of tabs, as {@link PresetsScreenLayout} produces it. */
    private static Rect content(int panelW, int h) {
        return new Rect(0, 56, panelW, h - 38 - 56);
    }

    @Test
    void colorBoxIsMin340AndPanelMinus8() {
        assertEquals(340, PresetTabLayout.compute(content(640, 360)).colorBox().w());
        assertEquals(291, PresetTabLayout.compute(content(299, 240)).colorBox().w());
    }

    @Test
    void colorBoxAndGridAreCentred() {
        Rect c = content(640, 360);
        PresetTabLayout t = PresetTabLayout.compute(c);
        assertEquals(c.x() + c.w() / 2, t.colorBox().x() + t.colorBox().w() / 2);
        assertEquals(c.x() + c.w() / 2, t.colorGridX() + PresetTabLayout.GRID_W / 2);
    }

    @Test
    void hexBoxDoesNotOverlapTheCustomSwatchOrItsLabel() {
        for (int[] p : PANELS) {
            PresetTabLayout t = PresetTabLayout.compute(content(p[0], p[1]));
            assertTrue(t.hexBox().x() >= t.customLabel().right(), p[0] + " hex " + t.hexBox() + " label " + t.customLabel());
            assertFalse(t.hexBox().intersects(t.customSwatch()), "" + p[0]);
            assertTrue(t.hexBox().right() <= t.colorBox().right(), "" + p[0]);
        }
    }

    @Test
    void colorBoxFitsAbove240MinusFooter() {
        PresetTabLayout t = PresetTabLayout.compute(content(320, 240));
        assertTrue(t.colorBox().bottom() <= 240 - 38);
        assertTrue(t.hexBox().bottom() <= t.colorBox().bottom());
    }

    @Test
    void swatchesFitInsideTheColorBox() {
        for (int[] p : PANELS) {
            PresetTabLayout t = PresetTabLayout.compute(content(p[0], p[1]));
            Rect last = t.swatch(PresetTabLayout.PALETTE_SIZE - 1);
            assertTrue(last.bottom() <= t.customSwatch().y(), "" + p[0]);
            assertTrue(t.swatch(0).x() >= t.colorBox().x(), "" + p[0]);
            assertTrue(t.swatch(5).right() <= t.colorBox().right(), "" + p[0]);
            assertTrue(t.customSwatch().bottom() <= t.colorBox().bottom(), "" + p[0]);
        }
    }

    @Test
    void swatchesFollowTheGridOrder() {
        PresetTabLayout t = PresetTabLayout.compute(content(640, 360));
        assertEquals(t.swatch(0).y(), t.swatch(5).y());
        assertEquals(t.swatch(0).y() + PresetTabLayout.SQ + PresetTabLayout.GAP, t.swatch(6).y());
        assertEquals(t.swatch(0).x(), t.swatch(6).x());
        assertEquals(t.swatch(0).right() + PresetTabLayout.GAP, t.swatch(1).x());
    }

    @Test
    void renameBoxIsMin320AndPanelMinus16WithTwoHalfButtons() {
        PresetTabLayout wide = PresetTabLayout.compute(content(640, 360));
        assertEquals(320, wide.renameBox().w());
        PresetTabLayout narrow = PresetTabLayout.compute(content(299, 240));
        assertEquals(283, narrow.renameBox().w());
        for (PresetTabLayout t : List.of(wide, narrow)) {
            assertEquals(t.renameSave().right() + 4, t.renameClear().x());
            assertEquals(t.renameBox().x(), t.renameSave().x());
            assertTrue(t.renameClear().right() <= t.renameBox().right());
            assertFalse(t.renameSave().intersects(t.renameClear()));
            assertTrue(t.renameBox().bottom() <= t.renameSave().y());
            assertEquals(LayoutMode.BUTTON_H, t.renameSave().h());
        }
    }

    @Test
    void shortcutBoxIsMin340AndPanelMinus8AndHoldsTheHintArea() {
        for (int[] p : PANELS) {
            PresetTabLayout t = PresetTabLayout.compute(content(p[0], p[1]));
            assertEquals(Math.min(340, p[0] - 8), t.shortcutBox().w(), "" + p[0]);
            assertTrue(t.shortcutHint().x() >= t.shortcutBox().x(), "" + p[0]);
            assertTrue(t.shortcutHint().right() <= t.shortcutBox().right(), "" + p[0]);
            assertTrue(t.shortcutHint().bottom() <= t.shortcutBox().bottom(), "" + p[0]);
            assertTrue(t.shortcutBox().bottom() <= p[1] - 38, "" + p[0]);
        }
    }

    @Test
    void shortcutLabelsAreCentredInTheBoxAboveTheHint() {
        PresetTabLayout t = PresetTabLayout.compute(content(640, 360));
        assertTrue(t.shortcutCaptureY() < t.shortcutSavedY());
        assertTrue(t.shortcutSavedY() < t.shortcutHint().y());
        assertEquals(t.shortcutBox().x() + t.shortcutBox().w() / 2, t.centerX());
    }
}
