package com.scr0ols.soundtweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.scr0ols.soundtweaks.PresetConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.SoundTweaks;
import com.scr0ols.soundtweaks.VolumeConfig;
import com.scr0ols.soundtweaks.client.gui.PresetsScreen;
import com.scr0ols.soundtweaks.client.gui.SoundTweaksScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = SoundTweaks.MOD_ID, value = Dist.CLIENT)
public class SoundTweaksClient {

    public static KeyMapping openMenuKey;
    public static KeyMapping openPresetsKey;

    // Preset names whose trigger was held on the previous tick (rising-edge detection).
    // Only accessed from ClientTickEvent.Post (render thread) — no synchronisation needed.
    private static final Set<String> shortcutKeysHeld = new HashSet<>();

    // Called from SoundTweaks constructor on the client dist
    public static void init(IEventBus modBus) {
        VolumeConfig.SOUNDS.load();
        VolumeConfig.BLOCKS.load();
        PresetConfig.load();
        SoundRegistry.populate();

        NeoForge.EVENT_BUS.addListener(SoundTweaksClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SoundTweaksClient::onGameShuttingDown);
    }

    // MOD_BUS — safe phase to register KeyMappings
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = new KeyMapping.Category(Identifier.parse("soundtweaks:soundtweaks"));
        event.registerCategory(category);

        openMenuKey = new KeyMapping(
                "key.soundtweaks.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        );
        openPresetsKey = new KeyMapping(
                "key.soundtweaks.open_presets",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                category
        );
        event.register(openMenuKey);
        event.register(openPresetsKey);
    }

    // GAME_BUS — flush saves before the game exits
    private static void onGameShuttingDown(GameShuttingDownEvent event) {
        VolumeConfig.shutdownSaveExecutor();
        PresetConfig.shutdownSaveExecutor();
        VolumeConfig.SOUNDS.save();
        VolumeConfig.BLOCKS.save();
        PresetConfig.save();
    }

    // GAME_BUS — runs every client tick
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();

        VolumeConfig.SOUNDS.tickSave();
        VolumeConfig.BLOCKS.tickSave();
        PresetConfig.tickSave();

        // Preset shortcuts — only active when no screen is open
        if (client.screen == null && client.getOverlay() == null) {
            long win = GLFW.glfwGetCurrentContext();
            if (win == 0L) return; // invalid GLFW context — skip

            for (PresetConfig.Preset preset : PresetConfig.getPresets()) {
                if (preset.shortcutKey <= 0) continue;

                int glfwKey = preset.shortcutKey & 0xFFFF;
                boolean triggerActive;

                if (preset.shortcutHeldKey != 0) {
                    // 2 or 3 keys: verify held keys + trigger
                    if (GLFW.glfwGetKey(win, preset.shortcutHeldKey) != GLFW.GLFW_PRESS) {
                        shortcutKeysHeld.remove(preset.name); continue;
                    }
                    if (preset.shortcutHeldKey2 != 0
                            && GLFW.glfwGetKey(win, preset.shortcutHeldKey2) != GLFW.GLFW_PRESS) {
                        shortcutKeysHeld.remove(preset.name); continue;
                    }
                    triggerActive = GLFW.glfwGetKey(win, glfwKey) == GLFW.GLFW_PRESS;
                } else {
                    // 1 key: only check the trigger key (rising edge)
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

        if (openMenuKey != null) {
            while (openMenuKey.consumeClick()) {
                client.setScreen(new SoundTweaksScreen(client.screen));
            }
        }
        if (openPresetsKey != null) {
            while (openPresetsKey.consumeClick()) {
                client.setScreen(new PresetsScreen(client.screen));
            }
        }
    }
}
