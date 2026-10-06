package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenFrameTest {

    @Test
    void narrowScreensUseTheFullWidth() {
        ScreenFrame f = ScreenFrame.of(640, 360);
        assertEquals(new Rect(0, 0, 640, 360), f.bounds());
    }

    @Test
    void wideScreensAreCappedAndCentred() {
        ScreenFrame f = ScreenFrame.of(1280, 720);
        assertEquals(new Rect(160, 0, 960, 720), f.bounds());
    }

    @Test
    void exactlyMaxWidthIsNotShifted() {
        assertEquals(new Rect(0, 0, 960, 540), ScreenFrame.of(960, 540).bounds());
    }

    @Test
    void oddLeftoverRoundsTheOffsetDown() {
        assertEquals(new Rect(20, 0, 960, 540), ScreenFrame.of(1001, 540).bounds());
    }

    @Test
    void railReplacesSidebarBelow560() {
        assertTrue(ScreenFrame.of(559, 300).useRail());
        assertFalse(ScreenFrame.of(560, 300).useRail());
        assertTrue(ScreenFrame.of(427, 240).useRail());
    }

    @Test
    void presetsGoTwoStepBelow500() {
        assertTrue(ScreenFrame.of(499, 300).twoStepPresets());
        assertFalse(ScreenFrame.of(500, 300).twoStepPresets());
    }

    @Test
    void modeFollowsTheRailBreakpoint() {
        assertEquals(LayoutMode.COMPACT, ScreenFrame.of(320, 240).mode());
        assertEquals(LayoutMode.NORMAL, ScreenFrame.of(640, 360).mode());
    }

    @Test
    void widthIsLimitedBeyondMaxWidthEvenWhenWindowIsHuge() {
        assertEquals(960, ScreenFrame.of(1920, 1080).bounds().w());
    }
}
