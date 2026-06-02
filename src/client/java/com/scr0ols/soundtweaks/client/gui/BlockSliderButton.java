package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public class BlockSliderButton extends AbstractSliderButton {

    private final String blockId;

    public BlockSliderButton(String blockId, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
              Math.min(VolumeResolver.getEffectiveBlockVolume(blockId), 1.0));
        this.blockId = blockId;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal((int)(this.value * 100) + "%"));
    }

    @Override
    protected void applyValue() {
        float vol = (float) this.value;
        VolumeConfig.BLOCKS.setVolume(this.blockId, vol);
        List<PresetConfig.Preset> actives = PresetConfig.getActivePresets();
        for (PresetConfig.Preset p : actives) {
            if (vol >= 1.0f) p.blocks.remove(this.blockId);
            else             p.blocks.put(this.blockId, vol);
        }
        if (!actives.isEmpty()) PresetConfig.markDirty();
        if ("minecraft:jukebox".equals(this.blockId) && vol <= 0.0f) {
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
