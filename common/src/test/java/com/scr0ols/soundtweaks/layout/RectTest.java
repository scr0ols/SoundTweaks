package com.scr0ols.soundtweaks.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RectTest {

    @Test
    void edgesAreExclusive() {
        Rect r = new Rect(10, 20, 30, 40);
        assertEquals(40, r.right());
        assertEquals(60, r.bottom());
    }

    @Test
    void containsPointInsideButNotOnFarEdge() {
        Rect r = new Rect(10, 20, 30, 40);
        assertTrue(r.contains(10, 20));
        assertTrue(r.contains(39, 59));
        assertFalse(r.contains(40, 30));
        assertFalse(r.contains(20, 60));
        assertFalse(r.contains(9, 30));
    }

    @Test
    void intersectsOnlyWhenAreasOverlap() {
        Rect a = new Rect(0, 0, 10, 10);
        assertTrue(a.intersects(new Rect(5, 5, 10, 10)));
        assertFalse(a.intersects(new Rect(10, 0, 10, 10)));
        assertFalse(a.intersects(new Rect(0, 10, 10, 10)));
        assertFalse(a.intersects(new Rect(20, 20, 5, 5)));
    }

    @Test
    void emptyRectNeverIntersects() {
        assertFalse(new Rect(0, 0, 0, 10).intersects(new Rect(0, 0, 10, 10)));
    }

    @Test
    void insetShrinksOnEverySideAndReturnsNewRect() {
        Rect r = new Rect(10, 20, 30, 40);
        assertEquals(new Rect(14, 24, 22, 32), r.inset(4));
        assertEquals(new Rect(10, 20, 30, 40), r);
    }

    @Test
    void insetNeverGoesNegative() {
        assertEquals(new Rect(15, 15, 0, 0), new Rect(0, 0, 10, 10).inset(15));
    }
}
