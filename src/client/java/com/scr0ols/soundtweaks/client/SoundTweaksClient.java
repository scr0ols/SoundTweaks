package com.scr0ols.soundtweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.client.gui.PresetsScreen;
import com.scr0ols.soundtweaks.client.gui.SoundTweaksScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public class SoundTweaksClient implements ClientModInitializer {

    public static KeyMapping openMenuKey;
    public static KeyMapping openPresetsKey;

    // IDs de presets cujo trigger estava pressionado no tick anterior (deteção de flanco)
    private static final Set<String> shortcutKeysHeld = new HashSet<>();

    @Override
    public void onInitializeClient() {
        VolumeConfig.SOUNDS.load();
        VolumeConfig.BLOCKS.load();
        PresetConfig.load();
        SoundRegistry.populate();

        KeyMapping.Category soundTweaksCategory =
                KeyMapping.Category.register(Identifier.parse("soundtweaks:soundtweaks"));

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundtweaks.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                soundTweaksCategory
        ));

        openPresetsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundtweaks.open_presets",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                soundTweaksCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VolumeConfig.SOUNDS.tickSave();
            VolumeConfig.BLOCKS.tickSave();
            PresetConfig.tickSave();

            // Atalhos de presets — só activos quando não há nenhum ecrã aberto
            if (client.screen == null && client.getOverlay() == null) {
                long win = GLFW.glfwGetCurrentContext();

                for (PresetConfig.Preset preset : PresetConfig.getPresets()) {
                    if (preset.shortcutKey <= 0) continue;

                    int glfwKey = preset.shortcutKey & 0xFFFF;
                    boolean triggerActive;

                    if (preset.shortcutHeldKey != 0) {
                        // 2 ou 3 teclas: verificar held keys + trigger
                        if (GLFW.glfwGetKey(win, preset.shortcutHeldKey) != GLFW.GLFW_PRESS) {
                            shortcutKeysHeld.remove(preset.name); continue;
                        }
                        if (preset.shortcutHeldKey2 != 0
                                && GLFW.glfwGetKey(win, preset.shortcutHeldKey2) != GLFW.GLFW_PRESS) {
                            shortcutKeysHeld.remove(preset.name); continue;
                        }
                        triggerActive = GLFW.glfwGetKey(win, glfwKey) == GLFW.GLFW_PRESS;
                    } else {
                        // 1 tecla: apenas verificar a trigger key (rising edge)
                        triggerActive = GLFW.glfwGetKey(win, glfwKey) == GLFW.GLFW_PRESS;
                    }

                    boolean wasHeld = shortcutKeysHeld.contains(preset.name);
                    if (triggerActive && !wasHeld)
                        PresetConfig.setActive(preset.name, !PresetConfig.isActive(preset.name));
                    if (triggerActive) shortcutKeysHeld.add(preset.name);
                    else               shortcutKeysHeld.remove(preset.name);
                }
            } else {
                shortcutKeysHeld.clear();
            }

            while (openMenuKey.consumeClick()) {
                client.setScreen(new SoundTweaksScreen(client.screen));
            }
            while (openPresetsKey.consumeClick()) {
                client.setScreen(new PresetsScreen(client.screen));
            }
        });
    }
}
