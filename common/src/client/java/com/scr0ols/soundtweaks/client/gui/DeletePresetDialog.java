package com.scr0ols.soundtweaks.client.gui;

import com.scr0ols.soundtweaks.PresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** Confirmation screen shown before a preset is deleted. */
final class DeletePresetDialog {

    private DeletePresetDialog() {}

    /** Returns to {@code back} after the choice; {@code onDeleted} runs first when confirmed. */
    static ConfirmScreen create(Minecraft minecraft, Screen back, PresetConfig.Preset preset, Runnable onDeleted) {
        return new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    PresetConfig.deletePreset(preset.id);
                    onDeleted.run();
                }
                minecraft.gui.setScreen(back);
            },
            Component.translatable("soundtweaks.presets.delete_title"),
            Component.empty()
                .append(Component.literal("\"" + preset.name + "\"").withStyle(s ->
                    s.withColor(TextColor.fromRgb(preset.argbColor() & 0x00FFFFFF))))
                .append(Component.literal(" — "))
                .append(Component.translatable("soundtweaks.presets.delete_warning"))
        );
    }
}
