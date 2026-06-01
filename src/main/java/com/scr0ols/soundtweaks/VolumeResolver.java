package com.scr0ols.soundtweaks;

/**
 * Resolve o volume efectivo de um som ou bloco, combinando:
 *   1. Config base (SoundConfig / BlockConfig) — ganha sempre se valor foi explicitamente definido
 *   2. Presets activos — maior desvio de 1.0 ganha conflitos entre presets
 *   3. Default 1.0 (sem alteração)
 */
public class VolumeResolver {

    public static float getEffectiveVolume(String soundId) {
        // Config base só ganha se o valor foi explicitamente alterado (≠ 1.0)
        // Se estiver em 1.0, assume-se que não foi tocado e os presets têm prioridade
        Float baseVal = SoundConfig.getAll().get(soundId);
        if (baseVal != null && baseVal != 1.0f) return baseVal;

        // Presets activos: vence o maior desvio de 1.0 (= volume mais extremo)
        float bestValue     = 1.0f;
        float bestDeviation = 0f;
        for (PresetConfig.Preset preset : PresetConfig.getActivePresets()) {
            Float val = preset.sounds.get(soundId);
            if (val != null) {
                float deviation = Math.abs(val - 1.0f);
                if (deviation > bestDeviation) {
                    bestDeviation = deviation;
                    bestValue     = val;
                }
            }
        }
        return bestValue;
    }

    public static float getEffectiveBlockVolume(String blockId) {
        Float baseVal = BlockConfig.getAll().get(blockId);
        if (baseVal != null && baseVal != 1.0f) return baseVal;

        float bestValue     = 1.0f;
        float bestDeviation = 0f;
        for (PresetConfig.Preset preset : PresetConfig.getActivePresets()) {
            Float val = preset.blocks.get(blockId);
            if (val != null) {
                float deviation = Math.abs(val - 1.0f);
                if (deviation > bestDeviation) {
                    bestDeviation = deviation;
                    bestValue     = val;
                }
            }
        }
        return bestValue;
    }
}
