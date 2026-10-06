package com.scr0ols.soundtweaks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** Keyboard helpers shared by the GUI screens. */
final class GuiKeys {

    private GuiKeys() {}

    /**
     * The letter on the pressed key, or 0 if it is not a plain letter. Uses the layout-aware SDL keycode
     * because SDL only sends typed characters while a text field is focused, and scancodes are physical positions.
     */
    static char jumpLetter(KeyEvent event) {
        int mods = event.modifiers() & (InputConstants.MOD_CONTROL | InputConstants.MOD_ALT | InputConstants.MOD_SUPER);
        int code = event.keycode();
        return mods == 0 && code >= 'a' && code <= 'z' ? (char) code : 0;
    }
}
