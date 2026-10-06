package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Slider that writes to preset.blocks (for blocks without a dedicated sound event).
 * 100% = no override = removes from preset.
 */
public class PresetBlockSliderButton extends AbstractSliderButton {

    private final PresetConfig.Preset preset;
    private final String blockId;

    public PresetBlockSliderButton(PresetConfig.Preset preset, String blockId,
                                   int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty(),
                Math.min(preset.blocks.getOrDefault(blockId, 1.0f), 1.0));
        this.preset  = preset;
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
        if (vol >= 1.0f) preset.blocks.remove(blockId);
        else             preset.blocks.put(blockId, vol);
        PresetConfig.markDirty();
    }

    public void setSliderValue(double newValue) {
        this.value = Mth.clamp(newValue, 0.0, 1.0);
        this.applyValue();
        this.updateMessage();
    }

    /** Syncs the display without writing to the preset (called every frame). */
    public void syncDisplay() {
        double current = Math.min(preset.blocks.getOrDefault(blockId, 1.0f), 1.0);
        if (Math.abs(this.value - current) > 0.001) {
            this.value = current;
            this.updateMessage();
        }
    }
}
