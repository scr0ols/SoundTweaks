package com.scr0ols.soundtweaks.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class SoundTweaksClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientLogic.loadConfigs();

        KeyMapping.Category soundTweaksCategory =
                KeyMapping.Category.register(Identifier.parse("soundtweaks:soundtweaks"));

        ClientLogic.createKeyMappings(soundTweaksCategory);
        KeyMappingHelper.registerKeyMapping(ClientLogic.openMenuKey);
        KeyMappingHelper.registerKeyMapping(ClientLogic.openPresetsKey);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientLogic.shutdown());
        ClientTickEvents.END_CLIENT_TICK.register(ClientLogic::tick);
    }
}
