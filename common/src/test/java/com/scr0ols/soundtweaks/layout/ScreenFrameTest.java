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
    void wideScreensUseTheFullWidth() {
        assertEquals(new Rect(0, 0, 1280, 720), ScreenFrame.of(1280, 720).bounds());
        assertEquals(new Rect(0, 0, 1920, 1080), ScreenFrame.of(1920, 1080).bounds());
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
    void hugeWindowsAreNotLimited() {
        assertEquals(new Rect(0, 0, 5120, 1440), ScreenFrame.of(5120, 1440).bounds());
    }
}
