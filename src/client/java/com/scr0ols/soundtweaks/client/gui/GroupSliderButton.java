package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.SoundConfig;
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
        int percent = (int)(this.value * 100);
        this.setMessage(Component.literal(percent + "%"));
    }

    @Override
    protected void applyValue() {
        float vol = (float) this.value;
        for (String id : childSoundIds) {
            SoundConfig.setVolume(id, vol);
        }
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    /** Recalcula o valor mostrado com base no mínimo dos filhos actuais. */
    public void refreshFromChildren() {
        float min = 1.0f;
        for (String id : childSoundIds) {
            float v = SoundConfig.getVolume(id);
            if (v < min) min = v;
        }
        this.value = min;
        this.updateMessage();
    }
}
