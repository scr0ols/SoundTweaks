package com.scr0ols.soundtweaks;

/**
 * One-time conversion of saved preset shortcuts from GLFW key codes (Minecraft up to 26.2)
 * to SDL scancodes (Minecraft 26.3 and later).
 *
 * <p>Presets written before 26.3 store GLFW codes in {@code shortcutKey}, {@code shortcutHeldKey}
 * and {@code shortcutHeldKey2}. Files written by this version carry {@link #FORMAT_KEY} with the value
 * {@link #FORMAT_SDL}; a file without it is converted on load or import.
 *
 * <p>This class has no Minecraft dependency so it can live in the common source set and be unit tested.
 */
public final class ShortcutKeyMigration {

    /** JSON property that marks a file as already using SDL scancodes. */
    public static final String FORMAT_KEY = "keyCodes";
    public static final String FORMAT_SDL = "sdl";

    /** Returned by {@link #glfwToSdl(int)} for a key with no SDL equivalent. */
    public static final int UNMAPPED = 0;

    /**
     * Old versions stored GLFW modifier bits above bit 15 of {@code shortcutKey}. The runtime
     * has always read only the low 16 bits, so the rest is dropped.
     */
    private static final int KEY_MASK = 0xFFFF;

    private ShortcutKeyMigration() {}

    /** Result of converting one shortcut. A shortcut that cannot be converted fully becomes unbound. */
    public record Shortcut(int key, int heldKey, int heldKey2, boolean dropped) {}

    /**
     * Converts a whole shortcut. If any key in the combination has no SDL equivalent, the shortcut is
     * unbound entirely: dropping only a held key would turn "Ctrl+X" into a bare "X" that fires by accident.
     */
    public static Shortcut convert(int shortcutKey, int heldKey, int heldKey2) {
        if (shortcutKey <= 0 && heldKey == 0 && heldKey2 == 0) {
            return new Shortcut(0, 0, 0, false);
        }
        int key = shortcutKey > 0 ? glfwToSdl(shortcutKey & KEY_MASK) : 0;
        int held = heldKey != 0 ? glfwToSdl(heldKey) : 0;
        int held2 = heldKey2 != 0 ? glfwToSdl(heldKey2) : 0;

        boolean failed = (shortcutKey > 0 && key == UNMAPPED)
                || (heldKey != 0 && held == UNMAPPED)
                || (heldKey2 != 0 && held2 == UNMAPPED);
        if (failed) return new Shortcut(0, 0, 0, true);
        return new Shortcut(key, held, held2, false);
    }

    /** Maps one GLFW key code to its SDL scancode, or {@link #UNMAPPED}. */
    public static int glfwToSdl(int glfw) {
        if (glfw >= 65 && glfw <= 90)   return 4 + (glfw - 65);    // A-Z
        if (glfw >= 49 && glfw <= 57)   return 30 + (glfw - 49);   // 1-9
        if (glfw == 48)                 return 39;                 // 0
        if (glfw >= 290 && glfw <= 301) return 58 + (glfw - 290);  // F1-F12
        if (glfw >= 302 && glfw <= 313) return 104 + (glfw - 302); // F13-F24
        if (glfw >= 321 && glfw <= 329) return 89 + (glfw - 321);  // KP 1-9
        return switch (glfw) {
            case 32  -> 44;  // space
            case 39  -> 52;  // apostrophe
            case 44  -> 54;  // comma
            case 45  -> 45;  // minus
            case 46  -> 55;  // period
            case 47  -> 56;  // slash
            case 59  -> 51;  // semicolon
            case 61  -> 46;  // equal
            case 91  -> 47;  // left bracket
            case 92  -> 49;  // backslash
            case 93  -> 48;  // right bracket
            case 96  -> 53;  // grave accent
            case 256 -> 41;  // escape
            case 257 -> 40;  // enter
            case 258 -> 43;  // tab
            case 259 -> 42;  // backspace
            case 260 -> 73;  // insert
            case 261 -> 76;  // delete
            case 262 -> 79;  // right
            case 263 -> 80;  // left
            case 264 -> 81;  // down
            case 265 -> 82;  // up
            case 266 -> 75;  // page up
            case 267 -> 78;  // page down
            case 268 -> 74;  // home
            case 269 -> 77;  // end
            case 280 -> 57;  // caps lock
            case 281 -> 71;  // scroll lock
            case 282 -> 83;  // num lock
            case 283 -> 70;  // print screen
            case 284 -> 72;  // pause
            case 320 -> 98;  // KP 0
            case 330 -> 99;  // KP decimal
            case 331 -> 84;  // KP divide
            case 332 -> 85;  // KP multiply
            case 333 -> 86;  // KP subtract
            case 334 -> 87;  // KP add
            case 335 -> 88;  // KP enter
            case 336 -> 103; // KP equal
            case 340 -> 225; // left shift
            case 341 -> 224; // left control
            case 342 -> 226; // left alt
            case 343 -> 227; // left super
            case 344 -> 229; // right shift
            case 345 -> 228; // right control
            case 346 -> 230; // right alt
            case 347 -> 231; // right super
            case 348 -> 101; // menu
            default  -> UNMAPPED; // includes WORLD_1/2, F25 and unknown codes
        };
    }
}
