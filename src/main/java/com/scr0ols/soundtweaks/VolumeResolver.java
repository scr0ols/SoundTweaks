package com.scr0ols.soundtweaks;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve o volume efectivo de um som ou bloco.
 *
 * Prioridade (maior → menor):
 *   1. Mute layer volátil — silêncio absoluto aplicado pelo botão mute do UI.
 *   2. Presets activos — maior desvio de 1.0 ganha conflitos entre presets.
 *   3. VolumeConfig base (SOUNDS / BLOCKS) — fallback permanente.
 *   4. Default 1.0 (sem alteração).
 */
public class VolumeResolver {

    // ── Mute layer volátil ────────────────────────────────────────────────────

    private static final Set<String> MUTED_SOUNDS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> MUTED_BLOCKS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void muteSound(String id)       { MUTED_SOUNDS.add(id); }
    public static void unmuteSound(String id)     { MUTED_SOUNDS.remove(id); }
    public static boolean isSoundMuted(String id) { return MUTED_SOUNDS.contains(id); }

    public static void muteBlock(String id)       { MUTED_BLOCKS.add(id); }
    public static void unmuteBlock(String id)     { MUTED_BLOCKS.remove(id); }
    public static boolean isBlockMuted(String id) { return MUTED_BLOCKS.contains(id); }

    // ── Resolução de volume ───────────────────────────────────────────────────

    public static float getEffectiveVolume(String soundId) {
        if (MUTED_SOUNDS.contains(soundId)) return 0.0f;

        float bestValue = 1.0f, bestDeviation = 0f;
        for (PresetConfig.Preset preset : PresetConfig.getActivePresets()) {
            Float val = preset.sounds.get(soundId);
            if (val != null) {
                float dev = Math.abs(val - 1.0f);
                if (dev > bestDeviation) { bestDeviation = dev; bestValue = val; }
            }
        }
        if (bestDeviation > 0f) return bestValue;

        float base = VolumeConfig.SOUNDS.getVolume(soundId);
        return base != 1.0f ? base : 1.0f;
    }

    public static float getEffectiveBlockVolume(String blockId) {
        if (MUTED_BLOCKS.contains(blockId)) return 0.0f;

        float bestValue = 1.0f, bestDeviation = 0f;
        for (PresetConfig.Preset preset : PresetConfig.getActivePresets()) {
            Float val = preset.blocks.get(blockId);
            if (val != null) {
                float dev = Math.abs(val - 1.0f);
                if (dev > bestDeviation) { bestDeviation = dev; bestValue = val; }
            }
        }
        if (bestDeviation > 0f) return bestValue;

        float base = VolumeConfig.BLOCKS.getVolume(blockId);
        return base != 1.0f ? base : 1.0f;
    }
}
