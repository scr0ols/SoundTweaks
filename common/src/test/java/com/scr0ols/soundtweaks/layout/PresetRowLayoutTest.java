package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetRowLayoutTest {

    @Test
    void starIsRightAlignedWithinTheRow() {
        PresetRowLayout r = PresetRowLayout.compute(10, 50, 300, 40, 0);
        assertEquals(10 + 300 - 22, r.star().x());
        assertEquals(50 + 4, r.star().y());
        assertEquals(18, r.star().w());
    }

    @Test
    void badgeSitsAtTheLeftOfTheRow() {
        PresetRowLayout r = PresetRowLayout.compute(10, 50, 300, 40, 0);
        assertEquals(20, r.badge().x());
        assertEquals(56, r.badge().y());
        assertEquals(22, r.badge().w());
        assertEquals(11, r.badge().h());
    }

    @Test
    void nameStartsAfterTheBadgeAndEndsBeforeTheStar() {
        PresetRowLayout r = PresetRowLayout.compute(0, 0, 300, 40, 0);
        assertEquals(38, r.nameX());
        assertEquals(r.star().x() - 4, r.nameX() + r.nameMaxW());
    }

    @Test
    void shortcutLabelFollowsTheNameAndTheNameLeavesRoomForIt() {
        PresetRowLayout r = PresetRowLayout.compute(0, 0, 300, 40, 30);
        assertEquals(30, r.shortcutW());
        assertEquals(r.nameX() + 40, r.shortcutX());
        assertTrue(r.shortcutX() + r.shortcutW() <= r.star().x() - 4);
    }

    @Test
    void longNameIsCutBeforeTheShortcut() {
        PresetRowLayout r = PresetRowLayout.compute(0, 0, 300, 500, 30);
        assertEquals(r.star().x() - 4 - 30, r.nameX() + r.nameMaxW());
        assertEquals(r.nameX() + r.nameMaxW(), r.shortcutX());
        assertTrue(r.shortcutX() + r.shortcutW() <= r.star().x() - 4);
    }

    @Test
    void shortcutIsDroppedWhenTheNameWouldGetTooNarrow() {
        PresetRowLayout r = PresetRowLayout.compute(0, 0, 100, 80, 60);
        assertEquals(0, r.shortcutW());
        assertFalse(r.hasShortcut());
        assertEquals(r.star().x() - 4, r.nameX() + r.nameMaxW());
    }

    @Test
    void noShortcutGivesTheNameTheWholeSpace() {
        PresetRowLayout r = PresetRowLayout.compute(0, 0, 300, 500, 0);
        assertFalse(r.hasShortcut());
        assertEquals(r.star().x() - 4, r.nameX() + r.nameMaxW());
    }

    @Test
    void nameWidthIsNeverNegative() {
        assertEquals(0, PresetRowLayout.compute(0, 0, 40, 100, 20).nameMaxW());
    }

    @Test
    void contentStaysInsideTheRowForNarrowRows() {
        for (int rowW = 60; rowW <= 400; rowW += 20) {
            PresetRowLayout r = PresetRowLayout.compute(0, 0, rowW, 200, 40);
            assertTrue(r.star().right() <= rowW, "star at rowW=" + rowW);
            assertTrue(r.nameX() + r.nameMaxW() <= r.star().x(), "name at rowW=" + rowW);
            if (r.hasShortcut()) assertTrue(r.shortcutX() + r.shortcutW() <= r.star().x(), "shortcut at rowW=" + rowW);
        }
    }
}
