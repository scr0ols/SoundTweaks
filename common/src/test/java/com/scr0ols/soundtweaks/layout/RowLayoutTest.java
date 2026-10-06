package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowLayoutTest {

    @Test
    void sliderWidthIsThirtyPercentOfRow() {
        assertEquals(75, RowLayout.compute(0, 250, 0).sliderW());
    }

    @Test
    void sliderWidthIsClampedBetween60And90() {
        assertEquals(60, RowLayout.compute(0, 100, 0).sliderW());
        assertEquals(60, RowLayout.compute(0, 10, 0).sliderW());
        assertEquals(90, RowLayout.compute(0, 900, 0).sliderW());
    }

    @Test
    void sliderIsRightAlignedWithAFourPixelMargin() {
        RowLayout r = RowLayout.compute(20, 300, 0);
        assertEquals(20 + 300 - 4, r.sliderX() + r.sliderW());
    }

    @Test
    void nameStartsAfterIndentAndPadding() {
        assertEquals(24, RowLayout.compute(20, 300, 0).nameX());
        assertEquals(36, RowLayout.compute(20, 300, 12).nameX());
    }

    @Test
    void nameEndsFourPixelsBeforeTheSlider() {
        RowLayout r = RowLayout.compute(0, 300, 12);
        assertEquals(r.sliderX() - 4, r.nameX() + r.nameMaxW());
    }

    @Test
    void nameWidthNeverGoesNegative() {
        assertTrue(RowLayout.compute(0, 60, 12).nameMaxW() >= 0);
        assertEquals(0, RowLayout.compute(0, 40, 12).nameMaxW());
    }

    @Test
    void rowFitsInsideItsBoundsAtEveryRealisticWidth() {
        for (int w = 160; w <= 960; w += 7) {
            for (int indent : new int[] {0, 12}) {
                RowLayout r = RowLayout.compute(10, w, indent);
                assertTrue(r.sliderX() >= 10 && r.sliderX() + r.sliderW() <= 10 + w, "w=" + w);
                assertTrue(r.nameX() + r.nameMaxW() <= r.sliderX(), "w=" + w);
            }
        }
    }
}
