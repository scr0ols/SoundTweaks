package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public class SoundSliderButton extends AbstractSliderButton {

    private final String soundId;

    public SoundSliderButton(String soundId, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
              Math.min(VolumeResolver.getEffectiveVolume(soundId), 1.0));
        this.soundId = soundId;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal((int)(this.value * 100) + "%"));
    }

    @Override
    protected void applyValue() {
        float vol = (float) this.value;
        VolumeConfig.SOUNDS.setVolume(this.soundId, vol);
        List<PresetConfig.Preset> actives = PresetConfig.getActivePresets();
        for (PresetConfig.Preset p : actives) {
            if (vol >= 1.0f) p.sounds.remove(this.soundId);
            else             p.sounds.put(this.soundId, vol);
        }
        if (!actives.isEmpty()) PresetConfig.markDirty();
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    public void syncFromConfig() {
        float effective = Math.min(VolumeResolver.getEffectiveVolume(this.soundId), 1.0f);
        if (Math.abs((float) this.value - effective) > 0.001f) {
            this.value = effective;
            this.updateMessage();
        }
    }
}
