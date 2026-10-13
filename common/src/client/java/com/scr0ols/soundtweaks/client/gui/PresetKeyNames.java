package com.scr0ols.soundtweaks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.scr0ols.soundtweaks.PresetConfig;
import org.lwjgl.sdl.SDLScancode;

/** Display names for preset shortcut keys. */
final class PresetKeyNames {

    /** Shown (and compared with {@code equals}) when no key is set. */
    static final String NONE = "---";

    private PresetKeyNames() {}

    static String displayLabel(PresetConfig.Preset preset) {
        if (preset.shortcutKey <= 0 && preset.shortcutHeldKey <= 0) return NONE;
        if (preset.shortcutHeldKey != 0) {
            String s = rawName(preset.shortcutHeldKey);
            if (preset.shortcutHeldKey2 != 0) s += "+" + rawName(preset.shortcutHeldKey2);
            return s + "+" + rawName(preset.shortcutKey & 0xFFFF);
        }
        return rawName(preset.shortcutKey & 0xFFFF);
    }

    static String rawName(int keyCode) {
        if (keyCode <= 0) return NONE;
        return switch (keyCode) {
            case InputConstants.KEY_F1  -> "F1";   case InputConstants.KEY_F2  -> "F2";
            case InputConstants.KEY_F3  -> "F3";   case InputConstants.KEY_F4  -> "F4";
            case InputConstants.KEY_F5  -> "F5";   case InputConstants.KEY_F6  -> "F6";
            case InputConstants.KEY_F7  -> "F7";   case InputConstants.KEY_F8  -> "F8";
            case InputConstants.KEY_F9  -> "F9";   case InputConstants.KEY_F10 -> "F10";
            case InputConstants.KEY_F11 -> "F11";  case InputConstants.KEY_F12 -> "F12";
            case InputConstants.KEY_UP    -> "UP";    case InputConstants.KEY_DOWN  -> "DOWN";
            case InputConstants.KEY_LEFT  -> "LEFT";  case InputConstants.KEY_RIGHT -> "RIGHT";
            case InputConstants.KEY_INSERT -> "INS";  case InputConstants.KEY_DELETE -> "DEL";
            case InputConstants.KEY_HOME   -> "HOME"; case InputConstants.KEY_END    -> "END";
            case InputConstants.KEY_PAGEUP -> "PgUp"; case InputConstants.KEY_PAGEDOWN -> "PgDn";
            case InputConstants.KEY_SPACE      -> "Space"; case InputConstants.KEY_RETURN -> "Enter";
            case InputConstants.KEY_NUMPADENTER   -> "Num Enter";
            case InputConstants.KEY_TAB        -> "Tab";  case InputConstants.KEY_CAPSLOCK -> "Caps";
            case InputConstants.KEY_ESCAPE     -> "Esc";  case InputConstants.KEY_BACKSPACE -> "Bksp";
            case InputConstants.KEY_PRINTSCREEN -> "Print"; case InputConstants.KEY_PAUSE -> "Pause";
            case InputConstants.KEY_NUMLOCK -> "Num Lock"; case InputConstants.KEY_SCROLLLOCK -> "Scroll";
            case InputConstants.KEY_LSHIFT,  InputConstants.KEY_RSHIFT   -> "Shift";
            case InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL -> "Ctrl";
            case InputConstants.KEY_LALT,    InputConstants.KEY_RALT     -> "Alt";
            case InputConstants.KEY_LGUI,  InputConstants.KEY_RGUI   -> "Super";
            case InputConstants.KEY_NUMPAD0 -> "Num0"; case InputConstants.KEY_NUMPAD1 -> "Num1";
            case InputConstants.KEY_NUMPAD2 -> "Num2"; case InputConstants.KEY_NUMPAD3 -> "Num3";
            case InputConstants.KEY_NUMPAD4 -> "Num4"; case InputConstants.KEY_NUMPAD5 -> "Num5";
            case InputConstants.KEY_NUMPAD6 -> "Num6"; case InputConstants.KEY_NUMPAD7 -> "Num7";
            case InputConstants.KEY_NUMPAD8 -> "Num8"; case InputConstants.KEY_NUMPAD9 -> "Num9";
            case InputConstants.KEY_ADD -> "Num+"; case SDLScancode.SDL_SCANCODE_KP_MINUS -> "Num-";
            case InputConstants.KEY_MULTIPLY -> "Num*"; case SDLScancode.SDL_SCANCODE_KP_DIVIDE -> "Num/";
            case SDLScancode.SDL_SCANCODE_KP_PERIOD  -> "Num.";
            default -> InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString().toUpperCase();
        };
    }
}
