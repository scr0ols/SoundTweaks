package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Slider que controla um grupo de sons em simultâneo dentro de um preset.
 * Ao chegar a 100% remove todos os overrides do grupo.
 */
public class PresetGroupSliderButton extends AbstractSliderButton {

    private final PresetConfig.Preset preset;
    private final List<String> children;

    public PresetGroupSliderButton(PresetConfig.Preset preset, List<String> children,
                                   int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), computeInitialValue(preset, children));
        this.preset   = preset;
        this.children = children;
        this.updateMessage();
    }

    private static double computeInitialValue(PresetConfig.Preset preset, List<String> children) {
        float min = 1.0f;
        for (String id : children) {
            Float v = preset.sounds.get(id);
            if (v != null) min = Math.min(min, v);
        }
        return min;
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal((int)(this.value * 100) + "%"));
    }

    @Override
    protected void applyValue() {
        float vol = (float) this.value;
        for (String id : children) {
            if (vol >= 1.0f) preset.sounds.remove(id);
            else             preset.sounds.put(id, vol);
        }
        PresetConfig.markDirty();
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    /** Sincroniza o display sem escrever no preset (chamado em cada frame). */
    public void syncDisplay() {
        float min = 1.0f;
        for (String id : children) { Float v = preset.sounds.get(id); if (v != null) min = Math.min(min, v); }
        double current = min;
        if (Math.abs(this.value - current) > 0.001) {
            this.value = current;
            this.updateMessage();
        }
    }
}
