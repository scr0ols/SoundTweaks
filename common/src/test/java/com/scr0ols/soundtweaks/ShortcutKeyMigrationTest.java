package com.scr0ols.soundtweaks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutKeyMigrationTest {

    @Test
    void lettersMapToScancodes() {
        assertEquals(4, ShortcutKeyMigration.glfwToSdl(65));   // A
        assertEquals(14, ShortcutKeyMigration.glfwToSdl(75));  // K
        assertEquals(29, ShortcutKeyMigration.glfwToSdl(90));  // Z
    }

    @Test
    void digitsMapToScancodes() {
        assertEquals(30, ShortcutKeyMigration.glfwToSdl(49));  // 1
        assertEquals(38, ShortcutKeyMigration.glfwToSdl(57));  // 9
        assertEquals(39, ShortcutKeyMigration.glfwToSdl(48));  // 0
    }

    @Test
    void functionKeysMapToScancodes() {
        assertEquals(58, ShortcutKeyMigration.glfwToSdl(290));  // F1
        assertEquals(69, ShortcutKeyMigration.glfwToSdl(301));  // F12
        assertEquals(104, ShortcutKeyMigration.glfwToSdl(302)); // F13
        assertEquals(115, ShortcutKeyMigration.glfwToSdl(313)); // F24
    }

    @Test
    void numpadKeysMapToScancodes() {
        assertEquals(98, ShortcutKeyMigration.glfwToSdl(320));  // KP 0
        assertEquals(89, ShortcutKeyMigration.glfwToSdl(321));  // KP 1
        assertEquals(97, ShortcutKeyMigration.glfwToSdl(329));  // KP 9
        assertEquals(88, ShortcutKeyMigration.glfwToSdl(335));  // KP enter
    }

    @Test
    void modifiersAndNavigationMapToScancodes() {
        assertEquals(225, ShortcutKeyMigration.glfwToSdl(340)); // left shift
        assertEquals(224, ShortcutKeyMigration.glfwToSdl(341)); // left control
        assertEquals(226, ShortcutKeyMigration.glfwToSdl(342)); // left alt
        assertEquals(231, ShortcutKeyMigration.glfwToSdl(347)); // right super
        assertEquals(41, ShortcutKeyMigration.glfwToSdl(256));  // escape
        assertEquals(82, ShortcutKeyMigration.glfwToSdl(265));  // up
        assertEquals(44, ShortcutKeyMigration.glfwToSdl(32));   // space
    }

    @Test
    void unknownKeyIsUnmapped() {
        assertEquals(ShortcutKeyMigration.UNMAPPED, ShortcutKeyMigration.glfwToSdl(161)); // WORLD_1
        assertEquals(ShortcutKeyMigration.UNMAPPED, ShortcutKeyMigration.glfwToSdl(314)); // F25
        assertEquals(ShortcutKeyMigration.UNMAPPED, ShortcutKeyMigration.glfwToSdl(-1));
        assertEquals(ShortcutKeyMigration.UNMAPPED, ShortcutKeyMigration.glfwToSdl(0));
    }

    @Test
    void singleKeyShortcutIsConverted() {
        var s = ShortcutKeyMigration.convert(75, 0, 0);
        assertEquals(14, s.key());
        assertEquals(0, s.heldKey());
        assertEquals(0, s.heldKey2());
        assertFalse(s.dropped());
    }

    @Test
    void comboShortcutConvertsEveryKey() {
        var s = ShortcutKeyMigration.convert(88, 341, 342); // Ctrl+Alt+X
        assertEquals(27, s.key());
        assertEquals(224, s.heldKey());
        assertEquals(226, s.heldKey2());
        assertFalse(s.dropped());
    }

    @Test
    void highBitsOfTheTriggerAreDiscarded() {
        var s = ShortcutKeyMigration.convert((2 << 16) | 75, 0, 0); // legacy modifier bits above bit 15
        assertEquals(14, s.key());
        assertFalse(s.dropped());
    }

    @Test
    void unboundShortcutStaysUnbound() {
        var s = ShortcutKeyMigration.convert(0, 0, 0);
        assertEquals(0, s.key());
        assertEquals(0, s.heldKey());
        assertEquals(0, s.heldKey2());
        assertFalse(s.dropped());
    }

    @Test
    void unmappedTriggerUnbindsTheWholeShortcut() {
        var s = ShortcutKeyMigration.convert(161, 341, 0);
        assertEquals(0, s.key());
        assertEquals(0, s.heldKey());
        assertTrue(s.dropped());
    }

    @Test
    void unmappedHeldKeyUnbindsTheWholeShortcut() {
        // Dropping only the held key would turn Ctrl+X into a bare X.
        var s = ShortcutKeyMigration.convert(88, 161, 0);
        assertEquals(0, s.key());
        assertEquals(0, s.heldKey());
        assertTrue(s.dropped());
    }

    @Test
    void conversionCoversEveryGlfwKeyThatPresetsCanCapture() {
        // Keys the shortcut capture screen can record: all printable keys, navigation, F1-F24, numpad, modifiers.
        int[] captured = {32, 39, 44, 45, 46, 47, 59, 61, 91, 92, 93, 96, 256, 257, 258, 259, 260, 261, 262, 263,
                264, 265, 266, 267, 268, 269, 280, 281, 282, 283, 284, 330, 331, 332, 333, 334, 335, 336, 340,
                341, 342, 343, 344, 345, 346, 347, 348};
        for (int key : captured) {
            assertTrue(ShortcutKeyMigration.glfwToSdl(key) > 0, "GLFW key " + key + " should have an SDL scancode");
        }
    }
}
