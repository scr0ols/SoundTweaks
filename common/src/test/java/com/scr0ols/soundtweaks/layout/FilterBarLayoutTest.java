package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterBarLayoutTest {
    /** Widest window width the invariants are checked up to (a 2560 px wide screen at GUI scale 1). */
    private static final int WIDEST = 2560;

    private static final int[] SOUNDS_TRAILING = {20, 78};

    private static Rect area(int w) {
        return new Rect(0, 26, w, 24);
    }

    private static List<Rect> all(FilterBarLayout l) {
        List<Rect> rects = new ArrayList<>(List.of(l.category(), l.object(), l.clear(), l.search()));
        rects.addAll(l.trailing());
        return rects;
    }

    @Test
    void fitsOnOneLineAtTheBreakpoint() {
        var l = FilterBarLayout.compute(area(340), 0);
        assertEquals(1, l.lines());
        assertEquals(l.category().y(), l.search().y());
        assertEquals(l.category().y(), l.clear().y());
    }

    @Test
    void wrapsToTwoLinesBelowTheBreakpoint() {
        var l = FilterBarLayout.compute(area(339), 0);
        assertEquals(2, l.lines());
        assertEquals(l.category().y(), l.object().y());
        assertEquals(l.category().y(), l.clear().y());
        assertTrue(l.search().y() > l.category().bottom() - 1);
    }

    @Test
    void searchIsFullWidthOnTheSecondLine() {
        var l = FilterBarLayout.compute(area(320), 0);
        assertEquals(4, l.search().x());
        assertEquals(312, l.search().w());
    }

    @Test
    void searchNeverShrinksBelowSixtyWhenOnOneLine() {
        for (int w = 340; w <= WIDEST; w++) {
            var l = FilterBarLayout.compute(area(w), 0);
            assertEquals(1, l.lines(), "width " + w);
            assertTrue(l.search().w() >= 60, "width " + w + " search " + l.search().w());
        }
    }

    @Test
    void searchTakesTheRemainingWidthOnOneLine() {
        var l = FilterBarLayout.compute(area(640), 0);
        assertEquals(640 - 4, l.search().right());
    }

    @Test
    void dropdownsAreCappedWhenThereIsPlentyOfSpace() {
        var narrow = FilterBarLayout.compute(area(400), 0);
        var wide = FilterBarLayout.compute(area(960), 0);
        assertEquals(wide.category().w(), FilterBarLayout.compute(area(800), 0).category().w());
        assertTrue(wide.category().w() >= narrow.category().w());
        assertTrue(wide.category().w() + wide.object().w() <= 250);
    }

    @Test
    void trailingButtonsPushTheBreakpointTo456() {
        assertEquals(1, FilterBarLayout.compute(area(456), SOUNDS_TRAILING).lines());
        assertEquals(2, FilterBarLayout.compute(area(455), SOUNDS_TRAILING).lines());
    }

    @Test
    void trailingButtonsAreRightAlignedWithTheirOwnWidths() {
        for (int w : new int[] {320, 455, 456, 960}) {
            var l = FilterBarLayout.compute(area(w), SOUNDS_TRAILING);
            assertEquals(2, l.trailing().size());
            assertEquals(20, l.trailing().get(0).w());
            assertEquals(78, l.trailing().get(1).w());
            assertEquals(w - 4, l.trailing().get(1).right(), "width " + w);
            assertTrue(l.trailing().get(0).right() <= l.trailing().get(1).x(), "width " + w);
        }
    }

    @Test
    void trailingButtonsShareTheLineWithTheSearchBox() {
        for (int w : new int[] {320, 455, 456, 960}) {
            var l = FilterBarLayout.compute(area(w), SOUNDS_TRAILING);
            for (Rect t : l.trailing()) {
                assertEquals(l.search().y(), t.y(), "width " + w);
            }
            assertTrue(l.search().w() >= 60, "width " + w);
        }
    }

    @Test
    void singleTrailingButtonCountUsesSquareButtons() {
        var l = FilterBarLayout.compute(area(640), 1);
        assertEquals(1, l.trailing().size());
        assertEquals(LayoutMode.BUTTON_H, l.trailing().get(0).w());
    }

    @Test
    void noTrailingButtonsMeansEmptyList() {
        assertTrue(FilterBarLayout.compute(area(640), 0).trailing().isEmpty());
    }

    @Test
    void everyRectIsInsideTheAreaHorizontallyAndVerticallyForAllSizes() {
        for (int w = 320; w <= WIDEST; w += 7) {
            for (int[] trailing : new int[][] {{}, {20}, SOUNDS_TRAILING}) {
                var a = new Rect(12, 30, w, 24);
                var l = FilterBarLayout.compute(a, trailing);
                for (Rect r : all(l)) {
                    assertTrue(r.x() >= a.x() && r.right() <= a.right(), "width " + w + " " + r);
                    assertTrue(r.y() >= a.y() && r.bottom() <= a.y() + l.height(), "width " + w + " " + r);
                }
            }
        }
    }

    @Test
    void rectsNeverOverlapForAllSizes() {
        for (int w = 320; w <= WIDEST; w++) {
            for (int[] trailing : new int[][] {{}, SOUNDS_TRAILING}) {
                var rects = all(FilterBarLayout.compute(area(w), trailing));
                for (int i = 0; i < rects.size(); i++) {
                    for (int j = i + 1; j < rects.size(); j++) {
                        assertFalse(rects.get(i).intersects(rects.get(j)),
                                "width " + w + ": " + rects.get(i) + " overlaps " + rects.get(j));
                    }
                }
            }
        }
    }

    @Test
    void controlsAreAtLeastButtonHeightTall() {
        for (int w = 320; w <= WIDEST; w += 11) {
            for (Rect r : all(FilterBarLayout.compute(area(w), SOUNDS_TRAILING))) {
                assertTrue(r.h() >= LayoutMode.BUTTON_H, "width " + w);
            }
        }
    }

    @Test
    void heightCoversOneOrTwoLines() {
        assertEquals(24, FilterBarLayout.compute(area(640), 0).height());
        assertEquals(46, FilterBarLayout.compute(area(320), 0).height());
    }

    @Test
    void dropdownsKeepTheirOrderCategoryObjectClear() {
        for (int w : new int[] {320, 339, 340, 640, 960}) {
            var l = FilterBarLayout.compute(area(w), 0);
            assertTrue(l.category().right() <= l.object().x(), "width " + w);
            assertTrue(l.object().right() <= l.clear().x(), "width " + w);
            assertEquals(LayoutMode.BUTTON_H, l.clear().w());
        }
    }
}
