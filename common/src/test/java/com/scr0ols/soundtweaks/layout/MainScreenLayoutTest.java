package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainScreenLayoutTest {

    private static final int[][] SIZES = {
            {320, 240}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {960, 540}, {1280, 720}, {1920, 1080}, {2560, 1440}};
    private static final int[] FAVORITES = {0, 3, 12};

    private interface Check {
        void run(String where, int w, int h, boolean sidebar, int favs, MainScreenLayout l);
    }

    private static void forEveryCase(Check check) {
        for (int[] size : SIZES) {
            for (boolean sidebar : new boolean[] {true, false}) {
                for (int favs : FAVORITES) {
                    String where = size[0] + "x" + size[1] + " sidebar=" + sidebar + " favs=" + favs;
                    check.run(where, size[0], size[1], sidebar, favs,
                            MainScreenLayout.compute(size[0], size[1], sidebar, favs));
                }
            }
        }
    }

    private static List<Rect> placed(MainScreenLayout l) {
        List<Rect> rects = new ArrayList<>(List.of(l.mute(), l.viewToggle(), l.presets(), l.title(),
                l.list(), l.count(), l.importButton(), l.doneButton(), l.panelHeader(), l.manage(), l.moreIndicator()));
        rects.addAll(l.filterBar().trailing());
        rects.addAll(List.of(l.filterBar().category(), l.filterBar().object(), l.filterBar().clear(),
                l.filterBar().search()));
        rects.addAll(l.favorites());
        rects.removeIf(Rect::isEmpty);
        return rects;
    }

    // ── Frame, mode, panel ────────────────────────────────────────────────────

    @Test
    void railReplacesSidebarBelow560() {
        assertEquals(MainScreenLayout.Panel.RAIL, MainScreenLayout.compute(559, 300, true, 3).panel());
        assertEquals(MainScreenLayout.Panel.SIDEBAR, MainScreenLayout.compute(560, 300, true, 3).panel());
        assertEquals(LayoutMode.COMPACT, MainScreenLayout.compute(559, 300, true, 3).mode());
        assertEquals(LayoutMode.NORMAL, MainScreenLayout.compute(560, 300, true, 3).mode());
    }

    @Test
    void railIsAlwaysShownEvenWhenTheSidebarIsClosed() {
        assertEquals(MainScreenLayout.Panel.RAIL, MainScreenLayout.compute(320, 240, false, 3).panel());
    }

    @Test
    void closedSidebarGivesTheWholeFrameToTheContent() {
        var l = MainScreenLayout.compute(640, 360, false, 3);
        assertEquals(MainScreenLayout.Panel.NONE, l.panel());
        assertEquals(new Rect(0, 0, 640, 360), l.content());
        assertTrue(l.panelBounds().isEmpty());
        assertTrue(l.manage().isEmpty());
        assertTrue(l.favorites().isEmpty());
    }

    @Test
    void sidebarWidthIsFrameMinus420ClampedTo140And220() {
        assertEquals(140, MainScreenLayout.compute(560, 300, true, 0).panelBounds().w());
        assertEquals(180, MainScreenLayout.compute(600, 300, true, 0).panelBounds().w());
        assertEquals(220, MainScreenLayout.compute(640, 300, true, 0).panelBounds().w());
        assertEquals(220, MainScreenLayout.compute(960, 300, true, 0).panelBounds().w());
    }

    @Test
    void contentIsAtLeast420WideBesideTheSidebar() {
        for (int w = 560; w <= 1920; w++) {
            var l = MainScreenLayout.compute(w, 300, true, 3);
            assertTrue(l.content().w() >= 420, "width " + w + " content " + l.content().w());
        }
    }

    @Test
    void sidebarSitsOnTheRightEdgeOfTheFrame() {
        var l = MainScreenLayout.compute(640, 360, true, 3);
        assertEquals(new Rect(420, 0, 220, 360), l.panelBounds());
        assertEquals(new Rect(0, 0, 420, 360), l.content());
    }

    @Test
    void railIs24WideAndTakesItsWidthFromTheContent() {
        var l = MainScreenLayout.compute(427, 240, true, 3);
        assertEquals(new Rect(403, 0, 24, 240), l.panelBounds());
        assertEquals(403, l.content().w());
    }

    @Test
    void wideScreensUseTheFullWidth() {
        var l = MainScreenLayout.compute(1920, 1080, true, 3);
        assertEquals(new Rect(0, 0, 1920, 1080), l.frame().bounds());
        assertEquals(0, l.content().x());
        assertEquals(1700, l.content().w());
        assertEquals(1920, l.panelBounds().right());
        assertEquals(4, l.mute().x());
    }

    // ── Header ────────────────────────────────────────────────────────────────

    @Test
    void headerButtonsKeepTheirOriginalPlaces() {
        var l = MainScreenLayout.compute(640, 360, true, 3);
        assertEquals(new Rect(4, 2, 20, 20), l.mute());
        assertEquals(new Rect(28, 2, 78, 20), l.viewToggle());
        assertEquals(new Rect(110, 2, 68, 20), l.presets());
        assertEquals(new Rect(0, 0, 420, 24), l.header());
    }

    @Test
    void presetsButtonIsHiddenInCompactMode() {
        assertTrue(MainScreenLayout.compute(480, 270, true, 3).presets().isEmpty());
        assertFalse(MainScreenLayout.compute(560, 270, true, 3).presets().isEmpty());
    }

    @Test
    void titleNeedsEightyPixelsAfterTheHeaderButtons() {
        // compact: buttons end at 106, content = width - 24
        assertTrue(MainScreenLayout.compute(209, 240, true, 0).title().isEmpty());   // 185 - 106 = 79
        assertFalse(MainScreenLayout.compute(210, 240, true, 0).title().isEmpty());  // 186 - 106 = 80
    }

    @Test
    void titleAreaLiesBetweenTheButtonsAndTheContentEdge() {
        var l = MainScreenLayout.compute(640, 360, false, 0);
        assertFalse(l.title().isEmpty());
        assertTrue(l.title().x() >= l.presets().right());
        assertTrue(l.title().right() <= l.content().right());
    }

    // ── Filter bar, list, footer ──────────────────────────────────────────────

    @Test
    void filterBarUsesTheContentWidthAndStartsBelowTheHeader() {
        var l = MainScreenLayout.compute(640, 360, true, 3);   // cw 420 -> one line
        assertEquals(1, l.filterBar().lines());
        assertEquals(26, l.filterBar().category().y());
        assertEquals(50, l.list().y());
    }

    @Test
    void filterBarWrapsWhenTheContentIsNarrow() {
        var l = MainScreenLayout.compute(320, 240, true, 3);   // cw 296 -> two lines
        assertEquals(2, l.filterBar().lines());
        assertEquals(72, l.list().y());
    }

    @Test
    void listFillsTheSpaceBetweenFiltersAndFooter() {
        var l = MainScreenLayout.compute(640, 360, false, 0);
        assertEquals(new Rect(0, 50, 640, 360 - 36 - 50), l.list());
    }

    @Test
    void footerShowsCountAndTwoCentredButtonsFrom400() {
        var l = MainScreenLayout.compute(640, 360, false, 0);
        assertEquals(new Rect(320 - 125, 334, 120, 20), l.importButton());
        assertEquals(new Rect(320 + 5, 334, 120, 20), l.doneButton());
        assertFalse(l.count().isEmpty());
        assertTrue(l.count().right() <= l.importButton().x());
    }

    @Test
    void countAppearsExactlyAtContentWidth400() {
        // compact content = width - 24
        assertTrue(MainScreenLayout.compute(423, 300, true, 0).count().isEmpty());
        assertFalse(MainScreenLayout.compute(424, 300, true, 0).count().isEmpty());
    }

    @Test
    void narrowFooterSplitsTheWidthBetweenImportAndDone() {
        var l = MainScreenLayout.compute(320, 240, true, 0);   // cw 296
        assertTrue(l.count().isEmpty());
        assertEquals(new Rect(4, 214, 142, 20), l.importButton());
        assertEquals(new Rect(150, 214, 142, 20), l.doneButton());
    }

    // ── Sidebar favourites ────────────────────────────────────────────────────

    @Test
    void sidebarHasHeaderAndManageButton() {
        var l = MainScreenLayout.compute(640, 360, true, 3);
        assertEquals(new Rect(420, 0, 220, 24), l.panelHeader());
        assertEquals(new Rect(422, 336, 216, 20), l.manage());
    }

    @Test
    void sidebarFavouritesAre22TallWithA23Pitch() {
        var l = MainScreenLayout.compute(640, 360, true, 3);
        assertEquals(3, l.favorites().size());
        assertEquals(new Rect(421, 26, 218, 22), l.favorites().get(0));
        assertEquals(new Rect(421, 49, 218, 22), l.favorites().get(1));
        assertEquals(new Rect(421, 72, 218, 22), l.favorites().get(2));
        assertEquals(0, l.hiddenFavorites());
    }

    @Test
    void sidebarCutsFavouritesThatDoNotFitAboveManage() {
        var l = MainScreenLayout.compute(640, 240, true, 12);
        assertEquals(8, l.favorites().size());
        assertEquals(4, l.hiddenFavorites());
        for (Rect r : l.favorites()) assertTrue(r.bottom() <= l.manage().y() - 4);
    }

    // ── Thin rail ─────────────────────────────────────────────────────────────

    @Test
    void railFavouritesAre20SquaresWithA22Pitch() {
        var l = MainScreenLayout.compute(427, 240, true, 3);
        assertEquals(new Rect(405, 28, 20, 20), l.favorites().get(0));
        assertEquals(new Rect(405, 50, 20, 20), l.favorites().get(1));
        assertEquals(new Rect(405, 72, 20, 20), l.favorites().get(2));
    }

    @Test
    void railManageIsAnIconButtonAtTheBottom() {
        var l = MainScreenLayout.compute(427, 240, true, 3);
        assertEquals(new Rect(405, 216, 20, 20), l.manage());
    }

    @Test
    void railShowsAsManyFavouritesAsFitAndCountsTheRest() {
        var l = MainScreenLayout.compute(320, 240, true, 12);
        assertEquals(8, l.favorites().size());
        assertEquals(4, l.hiddenFavorites());
        assertEquals(0, l.firstFavorite());
        assertFalse(l.moreIndicator().isEmpty());
    }

    @Test
    void railScrollMovesTheVisibleWindow() {
        var l = MainScreenLayout.compute(320, 240, true, 12, 3);
        assertEquals(3, l.firstFavorite());
        assertEquals(8, l.favorites().size());
        assertEquals(new Rect(l.favorites().get(0).x(), 28, 20, 20), l.favorites().get(0));
    }

    @Test
    void railScrollIsClampedToTheLastPage() {
        assertEquals(4, MainScreenLayout.compute(320, 240, true, 12, 99).firstFavorite());
        assertEquals(0, MainScreenLayout.compute(320, 240, true, 12, -5).firstFavorite());
        assertEquals(0, MainScreenLayout.compute(320, 240, true, 3, 2).firstFavorite());
    }

    @Test
    void railHasNoIndicatorWhenEverythingFits() {
        var l = MainScreenLayout.compute(320, 240, true, 3);
        assertTrue(l.moreIndicator().isEmpty());
        assertEquals(0, l.hiddenFavorites());
    }

    @Test
    void railFavouritesStayAboveTheManageButtonAndTheIndicator() {
        var l = MainScreenLayout.compute(320, 240, true, 12);
        for (Rect r : l.favorites()) {
            assertFalse(r.intersects(l.manage()));
            assertFalse(r.intersects(l.moreIndicator()));
        }
    }

    @Test
    void scrollOffsetIsIgnoredBySidebarAndWideLayouts() {
        assertEquals(0, MainScreenLayout.compute(640, 360, true, 12, 5).firstFavorite());
    }

    // ── Invariants over every approved size ───────────────────────────────────

    @Test
    void everyRectIsInsideTheScreenAndTheFrame() {
        forEveryCase((where, w, h, sidebar, favs, l) -> {
            Rect frame = l.frame().bounds();
            for (Rect r : placed(l)) {
                assertTrue(r.x() >= frame.x() && r.right() <= frame.right(), where + " " + r);
                assertTrue(r.y() >= 0 && r.bottom() <= h, where + " " + r);
                assertTrue(frame.right() <= w, where);
            }
        });
    }

    @Test
    void noRectsOverlap() {
        forEveryCase((where, w, h, sidebar, favs, l) -> {
            List<Rect> rects = placed(l);
            for (int i = 0; i < rects.size(); i++) {
                for (int j = i + 1; j < rects.size(); j++) {
                    assertFalse(rects.get(i).intersects(rects.get(j)),
                            where + ": " + rects.get(i) + " overlaps " + rects.get(j));
                }
            }
        });
    }

    @Test
    void contentAndPanelDoNotOverlap() {
        forEveryCase((where, w, h, sidebar, favs, l) ->
                assertFalse(l.content().intersects(l.panelBounds()), where));
    }

    @Test
    void buttonsAreAtLeastTwentyTall() {
        forEveryCase((where, w, h, sidebar, favs, l) -> {
            for (Rect r : List.of(l.mute(), l.viewToggle(), l.importButton(), l.doneButton())) {
                assertTrue(r.h() >= LayoutMode.BUTTON_H, where + " " + r);
            }
            if (!l.presets().isEmpty()) assertTrue(l.presets().h() >= LayoutMode.BUTTON_H, where);
            if (!l.manage().isEmpty()) assertTrue(l.manage().h() >= LayoutMode.BUTTON_H, where);
            for (Rect r : l.favorites()) assertTrue(r.h() >= LayoutMode.BUTTON_H, where + " " + r);
        });
    }

    @Test
    void searchBoxIsAtLeastSixtyWide() {
        forEveryCase((where, w, h, sidebar, favs, l) ->
                assertTrue(l.filterBar().search().w() >= FilterBarLayout.MIN_SEARCH_W, where));
    }

    @Test
    void listShowsAtLeastFiveRows() {
        forEveryCase((where, w, h, sidebar, favs, l) ->
                assertTrue(l.list().h() >= 5 * LayoutMode.BUTTON_H, where + " list " + l.list().h()));
    }

    @Test
    void layoutModeFollowsTheFrameWidth() {
        forEveryCase((where, w, h, sidebar, favs, l) -> {
            boolean compact = w < LayoutMode.RAIL_BP;
            assertEquals(compact ? LayoutMode.COMPACT : LayoutMode.NORMAL, l.mode(), where);
        });
    }

    @Test
    void favouritesNeverExceedTheCountGiven() {
        forEveryCase((where, w, h, sidebar, favs, l) -> {
            assertTrue(l.favorites().size() <= favs, where);
            assertEquals(l.panel() == MainScreenLayout.Panel.NONE ? 0 : favs - l.favorites().size(),
                    l.hiddenFavorites(), where);
        });
    }

    @Test
    void favouriteListIsImmutable() {
        var l = MainScreenLayout.compute(640, 360, true, 3);
        try {
            l.favorites().add(Rect.EMPTY);
            throw new AssertionError("list should be immutable");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}
