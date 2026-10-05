package com.scr0ols.soundtweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
//import com.scr0ols.soundtweaks.PerfStats;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundDeduplicationConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.client.gui.PresetsScreen;
import com.scr0ols.soundtweaks.client.gui.SoundTweaksScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
//import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public class SoundTweaksClient implements ClientModInitializer {

    public static KeyMapping openMenuKey;
    public static KeyMapping openPresetsKey;
    //public static KeyMapping perfReportKey;

    // Preset ids whose trigger was held on the previous tick (rising-edge detection).
    // Only accessed from ClientTickEvents.END_CLIENT_TICK (render thread) — no synchronisation needed.
    private static final Set<Integer> shortcutKeysHeld = new HashSet<>();

    @Override
    public void onInitializeClient() {
        VolumeConfig.SOUNDS.load();
        VolumeConfig.BLOCKS.load();
        PresetConfig.load();
        SoundDeduplicationConfig.INSTANCE.load();
        SoundRegistry.populate();

        KeyMapping.Category soundTweaksCategory =
                KeyMapping.Category.register(Identifier.parse("soundtweaks:soundtweaks"));

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundtweaks.open_menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_K,
                soundTweaksCategory
        ));

        openPresetsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundtweaks.open_presets",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                soundTweaksCategory
        ));

        /*perfReportKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundtweaks.perf_report",
                InputConstants.Type.KEYBOARD,
                InputConstants.UNKNOWN.getValue(),
                soundTweaksCategory
        ));*/

        // Flush async saves before the client stops.
        // Executors are shut down first (drains any queued saves), then a final
        // synchronous save captures the very latest state with no risk of being
        // overwritten by a stale queued task.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            VolumeConfig.shutdownSaveExecutor();
            PresetConfig.shutdownSaveExecutor();
            VolumeConfig.SOUNDS.save();
            VolumeConfig.BLOCKS.save();
            PresetConfig.save();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VolumeConfig.SOUNDS.tickSave();
            VolumeConfig.BLOCKS.tickSave();
            PresetConfig.tickSave();

            // Preset shortcuts — only active when no screen is open
            if (client.gui.screen() == null && client.gui.overlay() == null) {
                for (PresetConfig.Preset preset : PresetConfig.getPresets()) {
                    if (preset.shortcutKey <= 0) continue;

                    int keyCode = preset.shortcutKey & 0xFFFF;
                    boolean triggerActive;

                    if (preset.shortcutHeldKey != 0) {
                        // 2 or 3 keys: verify held keys + trigger
                        if (!InputConstants.isKeyDown(preset.shortcutHeldKey)) {
                            shortcutKeysHeld.remove(preset.id); continue;
                        }
                        if (preset.shortcutHeldKey2 != 0
                                && !InputConstants.isKeyDown(preset.shortcutHeldKey2)) {
                            shortcutKeysHeld.remove(preset.id); continue;
                        }
                        triggerActive = InputConstants.isKeyDown(keyCode);
                    } else {
                        // 1 key: only check the trigger key (rising edge)
                        triggerActive = InputConstants.isKeyDown(keyCode);
                    }

                    boolean wasHeld = shortcutKeysHeld.contains(preset.id);
                    if (triggerActive && !wasHeld)
                        PresetConfig.setActive(preset.id, !PresetConfig.isActive(preset.id));
                    if (triggerActive) shortcutKeysHeld.add(preset.id);
                    else               shortcutKeysHeld.remove(preset.id);
                }
            } else {
                shortcutKeysHeld.clear();
            }

            while (openMenuKey.consumeClick()) {
                client.gui.setScreen(new SoundTweaksScreen(client.gui.screen()));
            }
            while (openPresetsKey.consumeClick()) {
                client.gui.setScreen(new PresetsScreen(client.gui.screen()));
            }
            /*while (perfReportKey.consumeClick()) {
                String report = PerfStats.reportAndReset();
                if (client.player != null)
                    client.player.sendSystemMessage(Component.literal(report));
            }*/
        });
    }
}
