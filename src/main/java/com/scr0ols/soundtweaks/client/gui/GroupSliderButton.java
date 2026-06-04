package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public class GroupSliderButton extends AbstractSliderButton {

    private final List<String> childSoundIds;

    public GroupSliderButton(List<String> childSoundIds, int x, int y, int width, int height, float initialValue) {
        super(x, y, width, height, Component.empty(), Math.min(initialValue, 1.0));
        this.childSoundIds = childSoundIds;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal((int)(this.value * 100) + "%"));
    }

    @Override
    protected void applyValue() {
        float vol = (float) this.value;
        for (String id : childSoundIds) VolumeConfig.SOUNDS.setVolume(id, vol);
        List<PresetConfig.Preset> actives = PresetConfig.getActivePresets();
        for (PresetConfig.Preset p : actives) {
            for (String id : childSoundIds) {
                if (vol >= 1.0f) p.sounds.remove(id);
                else             p.sounds.put(id, vol);
            }
        }
        if (!actives.isEmpty()) PresetConfig.markDirty();
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    public void refreshFromChildren() {
        float min = 1.0f;
        for (String id : childSoundIds) {
            float v = Math.min(VolumeConfig.SOUNDS.getVolume(id), 1.0f);
            if (v < min) min = v;
        }
        this.value = min;
        this.updateMessage();
    }
}
