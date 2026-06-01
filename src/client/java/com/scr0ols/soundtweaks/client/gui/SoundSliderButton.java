package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.SoundConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SoundSliderButton extends AbstractSliderButton {

    private final String soundId;

    public SoundSliderButton(String soundId, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), Math.min(SoundConfig.getVolume(soundId), 1.0));
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
        SoundConfig.setVolume(this.soundId, (float) this.value);
    }

    /**
     * Atualiza o valor do slider programaticamente.
     * Usado pelo scroll do rato e pelo duplo clique da entrada.
     */
    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    /**
     * Sincroniza o valor visual do slider com o que está em SoundConfig.
     * Chamado no render para manter coerência quando o grupo sobrescreve o filho.
     */
    public void syncFromConfig() {
        float configVal = Math.min(SoundConfig.getVolume(this.soundId), 1.0f);
        if (Math.abs((float) this.value - configVal) > 0.001f) {
            this.value = configVal;
            this.updateMessage();
        }
    }
}
