package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Key-combination capture state of the Shortcut tab. */
final class ShortcutCapture {

    private final LinkedHashSet<Integer> heldKeys = new LinkedHashSet<>();
    private final List<Integer> heldAtTrigger = new ArrayList<>();
    private int trigger = 0;

    void reset() {
        heldKeys.clear(); heldAtTrigger.clear(); trigger = 0;
    }

    boolean hasCapture() { return trigger != 0; }

    /** A key other than ESC, BACKSPACE and ENTER was pressed. */
    void onKeyPressed(int key) {
        heldAtTrigger.clear(); heldAtTrigger.addAll(heldKeys);
        while (heldAtTrigger.size() > 2) heldAtTrigger.remove(0);
        trigger = key; heldKeys.add(key);
    }

    void onKeyReleased(int key) { heldKeys.remove(key); }

    /** "Held + Held + Trigger", or {@link PresetKeyNames#NONE} before anything is captured. */
    String label() {
        if (trigger == 0) return PresetKeyNames.NONE;
        StringBuilder sb = new StringBuilder();
        for (int k : heldAtTrigger) sb.append(PresetKeyNames.rawName(k)).append(" + ");
        sb.append(PresetKeyNames.rawName(trigger));
        return sb.toString();
    }

    /** Writes the captured combination to the preset and marks the config dirty. */
    void commitTo(PresetConfig.Preset preset) {
        int h1 = 0, h2 = 0;
        if (heldAtTrigger.size() == 1) h1 = heldAtTrigger.get(0);
        else if (heldAtTrigger.size() >= 2) { h1 = heldAtTrigger.get(heldAtTrigger.size() - 2); h2 = heldAtTrigger.get(heldAtTrigger.size() - 1); }
        preset.shortcutKey = trigger & 0xFFFF;
        preset.shortcutHeldKey = h1; preset.shortcutHeldKey2 = h2;
        PresetConfig.markDirty();
    }
}
