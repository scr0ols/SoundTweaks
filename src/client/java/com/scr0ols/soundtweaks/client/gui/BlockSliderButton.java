package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.BlockConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class BlockSliderButton extends AbstractSliderButton {

    private final String blockId;

    public BlockSliderButton(String blockId, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(), Math.min(BlockConfig.getVolume(blockId), 1.0));
        this.blockId = blockId;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        int percent = (int)(this.value * 100);
        this.setMessage(Component.literal(percent + "%"));
    }

    @Override
    protected void applyValue() {
        BlockConfig.setVolume(this.blockId, (float) this.value);
        // Jukebox: parar imediatamente o disco quando o volume vai a zero
        if ("minecraft:jukebox".equals(this.blockId) && this.value <= 0.0) {
            net.minecraft.client.Minecraft.getInstance()
                    .getSoundManager()
                    .stop(null, net.minecraft.sounds.SoundSource.RECORDS);
        }
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }
}
