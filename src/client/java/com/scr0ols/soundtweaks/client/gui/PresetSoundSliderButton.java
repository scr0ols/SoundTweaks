package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Slider that writes directly to a specific preset's sound map
 * (instead of writing to SoundConfig like the regular SoundSliderButton).
 */
public class PresetSoundSliderButton extends AbstractSliderButton {

    private final PresetConfig.Preset preset;
    private final String soundId;

    public PresetSoundSliderButton(PresetConfig.Preset preset, String soundId,
                                   int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                Math.min(preset.sounds.getOrDefault(soundId, 1.0f), 1.0));
        this.preset  = preset;
        this.soundId = soundId;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        int percent = (int)(this.value * 100);
        this.setMessage(Component.literal(percent + "%"));
    }

    @Override
    protected void applyValue() {
        float vol = (float) this.value;
        if (vol >= 1.0f) {
            preset.sounds.remove(soundId); // 100% = no override = remove from preset
        } else {
            preset.sounds.put(soundId, vol);
        }
        PresetConfig.markDirty();
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    /** Syncs the display without writing to the preset (called every frame). */
    public void syncDisplay() {
        double current = Math.min(preset.sounds.getOrDefault(soundId, 1.0f), 1.0);
        if (Math.abs(this.value - current) > 0.001) {
            this.value = current;
            this.updateMessage();
        }
    }
}
