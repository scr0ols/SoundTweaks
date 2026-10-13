package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetsScreenLayoutTest {

    private static final int[][] SIZES = {
            {320, 240}, {427, 240}, {480, 270}, {499, 300}, {500, 300}, {640, 360}, {683, 384},
            {960, 540}, {1280, 720}, {1920, 1080}, {2560, 1440}};
    /** Roughly Color, Rename, Shortcut, Edit Sounds, Delete in the Minecraft font, plus the 14 px padding. */
    private static final int[] TAB_TEXT = {28, 36, 44, 60, 31};
    private static final int[] WIDE_TAB_TEXT = {56, 72, 88, 120, 62};

    private interface Check {
        void run(String where, int w, int h, PresetsScreenLayout l);
    }

    private static void forEveryCase(int[] tabText, Check check) {
        for (int[] size : SIZES) {
            for (boolean selection : new boolean[] {false, true}) {
                for (boolean detail : new boolean[] {false, true}) {
                    for (boolean sounds : new boolean[] {false, true}) {
                        String where = size[0] + "x" + size[1] + " selection=" + selection
                                + " detail=" + detail + " sounds=" + sounds;
                        check.run(where, size[0], size[1],
                                PresetsScreenLayout.compute(size[0], size[1], selection, detail, sounds, tabText));
                    }
                }
            }
        }
    }

    private static List<Rect> placed(PresetsScreenLayout l) {
        List<Rect> rects = new ArrayList<>(List.of(l.list(), l.importButton(), l.exportButton(), l.openConfigButton(),
                l.newButton(), l.doneButton(), l.message(), l.listTitle(), l.backButton(), l.detailTitle(),
                l.soundsImportButton()));
        rects.addAll(l.tabs());
        if (l.hasDetail() && l.soundsTab()) {
            rects.add(l.soundsList());
            FilterBarLayout f = l.soundsFilter();
            rects.addAll(List.of(f.category(), f.object(), f.clear(), f.search()));
            rects.addAll(f.trailing());
        }
        rects.removeIf(Rect::isEmpty);
        return rects;
    }

    // ── Modes ─────────────────────────────────────────────────────────────────

    @Test
    void withoutSelectionTheListIsCentred() {
        for (int[] size : SIZES) {
            PresetsScreenLayout l = PresetsScreenLayout.compute(size[0], size[1], false, false, false, TAB_TEXT);
            assertEquals(PresetsScreenLayout.Mode.CENTERED, l.mode(), size[0] + "x" + size[1]);
            assertEquals(Math.min(400, size[0] - 16), l.list().w());
            assertEquals((size[0] - l.list().w()) / 2, l.list().x());
            assertFalse(l.hasDetail());
        }
    }

    @Test
    void twoStepsBelow500AndSideBySideFrom500() {
        assertEquals(PresetsScreenLayout.Mode.LIST_STEP,
                PresetsScreenLayout.compute(499, 300, true, false, false, TAB_TEXT).mode());
        assertEquals(PresetsScreenLayout.Mode.DETAIL_STEP,
                PresetsScreenLayout.compute(499, 300, true, true, false, TAB_TEXT).mode());
        assertEquals(PresetsScreenLayout.Mode.SIDE_BY_SIDE,
                PresetsScreenLayout.compute(500, 300, true, false, false, TAB_TEXT).mode());
        assertEquals(PresetsScreenLayout.Mode.SIDE_BY_SIDE,
                PresetsScreenLayout.compute(500, 300, true, true, false, TAB_TEXT).mode());
    }

    @Test
    void listStepFillsTheWidthMinusMargins() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(320, 240, true, false, false, TAB_TEXT);
        assertEquals(new Rect(4, 28, 312, l.list().h()), l.list());
        assertFalse(l.hasDetail());
        assertTrue(l.tabs().isEmpty());
    }

    @Test
    void detailStepHasBackButtonAndTheWholeWidthForThePanel() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(320, 240, true, true, false, TAB_TEXT);
        assertTrue(l.hasDetail());
        assertEquals(new Rect(4, 4, 56, LayoutMode.BUTTON_H), l.backButton());
        assertEquals(0, l.panel().x());
        assertEquals(320, l.panel().w());
        assertTrue(l.list().isEmpty());
        assertTrue(l.importButton().isEmpty());
        assertTrue(l.newButton().isEmpty());
    }

    @Test
    void sideBySideListIs38PercentClampedTo200And330() {
        assertEquals(200, PresetsScreenLayout.compute(500, 300, true, false, false, TAB_TEXT).list().w());
        assertEquals(243, PresetsScreenLayout.compute(640, 360, true, false, false, TAB_TEXT).list().w());
        assertEquals(330, PresetsScreenLayout.compute(1920, 1080, true, false, false, TAB_TEXT).list().w());
        assertEquals(330, PresetsScreenLayout.compute(2560, 1440, true, false, false, TAB_TEXT).list().w());
    }

    @Test
    void sideBySidePanelStartsAfterTheDivider() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(640, 360, true, false, false, TAB_TEXT);
        assertEquals(l.list().right(), l.divider().x());
        assertEquals(l.divider().right(), l.panel().x());
        assertEquals(640, l.panel().right());
    }

    @Test
    void onlyTheBackButtonIsShownInDetailStep() {
        assertTrue(PresetsScreenLayout.compute(640, 360, true, true, false, TAB_TEXT).backButton().isEmpty());
        assertTrue(PresetsScreenLayout.compute(320, 240, true, false, false, TAB_TEXT).backButton().isEmpty());
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    @Test
    void tabWidthIsTextPlus14() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(960, 540, true, false, false, TAB_TEXT);
        assertEquals(TAB_TEXT.length, l.tabs().size());
        for (int i = 0; i < TAB_TEXT.length; i++) {
            assertEquals(TAB_TEXT[i] + 14, l.tabs().get(i).w(), "tab " + i);
            assertEquals(LayoutMode.BUTTON_H, l.tabs().get(i).h());
        }
        assertEquals(1, l.tabLines());
    }

    @Test
    void tabsWrapToTwoLinesWhenTheSumExceedsThePanelMinus8() {
        // widths 70+86+102+134+76 = 468, plus 4 gaps of 4 = 484
        PresetsScreenLayout fits = PresetsScreenLayout.compute(960, 540, true, false, false, WIDE_TAB_TEXT);
        assertEquals(1, fits.tabLines());
        PresetsScreenLayout wraps = PresetsScreenLayout.compute(500, 300, true, false, false, WIDE_TAB_TEXT);
        assertEquals(2, wraps.tabLines());
        assertEquals(wraps.tabs().get(0).y() + 22, wraps.tabs().get(wraps.tabs().size() - 1).y());
    }

    @Test
    void tabsStartFourPixelsInsideThePanelAndKeepAFourPixelGap() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(960, 540, true, false, false, TAB_TEXT);
        assertEquals(l.panel().x() + 4, l.tabs().get(0).x());
        for (int i = 1; i < l.tabs().size(); i++) {
            assertEquals(l.tabs().get(i - 1).right() + 4, l.tabs().get(i).x());
        }
    }

    @Test
    void contentStartsBelowTheTabs() {
        PresetsScreenLayout one = PresetsScreenLayout.compute(960, 540, true, false, false, TAB_TEXT);
        assertEquals(28 + 22 + 6, one.content().y());
        PresetsScreenLayout two = PresetsScreenLayout.compute(500, 300, true, false, false, WIDE_TAB_TEXT);
        assertEquals(28 + 44 + 6, two.content().y());
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    @Test
    void doneSitsInTheListFooterWhenOnlyTheListIsShown() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(640, 360, false, false, false, TAB_TEXT);
        assertEquals(l.list().y() + l.list().h(), l.list().bottom());
        assertTrue(l.doneButton().x() > l.newButton().x());
        assertEquals(l.newButton().y(), l.doneButton().y());
    }

    @Test
    void doneSitsCentredInThePanelFooterWhenTheDetailIsShown() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(960, 540, true, false, false, TAB_TEXT);
        assertEquals(120, l.doneButton().w());
        assertEquals(l.panel().x() + l.panel().w() / 2 - 60, l.doneButton().x());
        assertEquals(540 - 26, l.doneButton().y());
    }

    @Test
    void soundsFooterHoldsImportAndDoneSideBySide() {
        PresetsScreenLayout wide = PresetsScreenLayout.compute(960, 540, true, false, true, TAB_TEXT);
        assertEquals(110, wide.soundsImportButton().w());
        assertEquals(120, wide.doneButton().w());
        assertTrue(wide.soundsImportButton().right() < wide.doneButton().x());

        PresetsScreenLayout narrow = PresetsScreenLayout.compute(320, 240, true, true, true, TAB_TEXT);
        assertEquals(narrow.soundsImportButton().w(), narrow.doneButton().w());
        assertEquals(narrow.panel().w() - 12, narrow.soundsImportButton().w() + narrow.doneButton().w());
    }

    @Test
    void messageStripSitsBetweenTheListAndTheButtons() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(640, 360, true, false, false, TAB_TEXT);
        assertTrue(l.message().y() >= l.list().bottom());
        assertTrue(l.message().bottom() <= l.importButton().y());
        assertFalse(l.message().intersects(l.list()));
    }

    // ── Sounds tab ────────────────────────────────────────────────────────────

    @Test
    void soundsFilterIsOneLineFromPanel456AndTwoLinesBelow() {
        // 737 wide: list 280 + divider 1 leaves a 456 panel; 736 leaves 455
        assertEquals(1, PresetsScreenLayout.compute(737, 500, true, false, true, TAB_TEXT).soundsFilter().lines());
        assertEquals(2, PresetsScreenLayout.compute(736, 500, true, false, true, TAB_TEXT).soundsFilter().lines());
        assertEquals(2, PresetsScreenLayout.compute(320, 240, true, true, true, TAB_TEXT).soundsFilter().lines());
    }

    @Test
    void soundsListStartsBelowTheFilterBar() {
        PresetsScreenLayout l = PresetsScreenLayout.compute(320, 240, true, true, true, TAB_TEXT);
        assertEquals(l.content().y() + l.soundsFilter().height(), l.soundsList().y());
        assertEquals(l.panel().x(), l.soundsList().x());
        assertEquals(l.panel().w(), l.soundsList().w());
    }

    @Test
    void soundsListHasAtLeastFiveRowsAt240() {
        for (int w : new int[] {320, 427, 480, 499}) {
            PresetsScreenLayout l = PresetsScreenLayout.compute(w, 240, true, true, true, TAB_TEXT);
            assertTrue(l.soundsList().h() >= 5 * 20, w + " -> " + l.soundsList().h());
        }
        for (int w : new int[] {500, 640, 683}) {
            PresetsScreenLayout l = PresetsScreenLayout.compute(w, 240, true, false, true, TAB_TEXT);
            assertTrue(l.soundsList().h() >= 5 * 20, w + " -> " + l.soundsList().h());
        }
    }

    // ── Invariants over every supported size ──────────────────────────────────

    @Test
    void everyRectIsInsideTheScreen() {
        forEveryCase(TAB_TEXT, (where, w, h, l) -> {
            Rect screen = new Rect(0, 0, w, h);
            for (Rect r : placed(l)) {
                assertTrue(r.x() >= screen.x() && r.y() >= screen.y()
                        && r.right() <= screen.right() && r.bottom() <= screen.bottom(), where + " " + r);
            }
        });
    }

    @Test
    void noPlacedRectsOverlap() {
        forEveryCase(TAB_TEXT, (where, w, h, l) -> {
            List<Rect> rects = placed(l);
            for (int i = 0; i < rects.size(); i++) {
                for (int j = i + 1; j < rects.size(); j++) {
                    assertFalse(rects.get(i).intersects(rects.get(j)),
                            where + " " + rects.get(i) + " x " + rects.get(j));
                }
            }
        });
    }

    @Test
    void noPlacedRectsOverlapWhenTheTabsWrap() {
        forEveryCase(WIDE_TAB_TEXT, (where, w, h, l) -> {
            List<Rect> rects = placed(l);
            for (int i = 0; i < rects.size(); i++) {
                for (int j = i + 1; j < rects.size(); j++) {
                    assertFalse(rects.get(i).intersects(rects.get(j)),
                            where + " " + rects.get(i) + " x " + rects.get(j));
                }
            }
        });
    }

    @Test
    void buttonsAreAtLeast20HighAndSearchAtLeast60Wide() {
        forEveryCase(TAB_TEXT, (where, w, h, l) -> {
            for (Rect b : List.of(l.importButton(), l.exportButton(), l.openConfigButton(), l.newButton(),
                    l.doneButton(), l.backButton(), l.soundsImportButton())) {
                if (!b.isEmpty()) assertTrue(b.h() >= LayoutMode.BUTTON_H, where + " " + b);
            }
            if (l.hasDetail() && l.soundsTab()) {
                assertTrue(l.soundsFilter().search().w() >= FilterBarLayout.MIN_SEARCH_W, where);
            }
        });
    }

    @Test
    void listHasAtLeastFiveRowsAt240() {
        for (int[] size : SIZES) {
            if (size[1] != 240) continue;
            PresetsScreenLayout l = PresetsScreenLayout.compute(size[0], 240, true, false, false, TAB_TEXT);
            if (l.mode() != PresetsScreenLayout.Mode.DETAIL_STEP) {
                assertTrue(l.list().h() >= 5 * 24, size[0] + " -> " + l.list().h());
            }
        }
    }

    @Test
    void tabsStayInsideThePanel() {
        forEveryCase(WIDE_TAB_TEXT, (where, w, h, l) -> {
            if (!l.hasDetail()) return;
            for (Rect t : l.tabs()) {
                assertTrue(t.x() >= l.panel().x() + 4 && t.right() <= l.panel().right() - 4, where + " " + t);
            }
        });
    }
}
