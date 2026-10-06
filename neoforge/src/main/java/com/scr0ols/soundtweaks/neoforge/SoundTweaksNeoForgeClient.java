package com.scr0ols.soundtweaks.neoforge;

import com.scr0ols.soundtweaks.client.ClientLogic;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Client-only wiring, kept out of the {@code @Mod} class so it is never loaded on a server. */
final class SoundTweaksNeoForgeClient {

	private SoundTweaksNeoForgeClient() {}

	static void init(IEventBus modBus) {
		ClientLogic.loadConfigs();

		modBus.addListener(SoundTweaksNeoForgeClient::onRegisterKeyMappings);
		NeoForge.EVENT_BUS.addListener(SoundTweaksNeoForgeClient::onClientTick);
		NeoForge.EVENT_BUS.addListener(SoundTweaksNeoForgeClient::onGameShuttingDown);
	}

	private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
		KeyMapping.Category category = new KeyMapping.Category(Identifier.parse("soundtweaks:soundtweaks"));
		event.registerCategory(category);

		ClientLogic.createKeyMappings(category);
		event.register(ClientLogic.openMenuKey);
		event.register(ClientLogic.openPresetsKey);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		ClientLogic.tick(Minecraft.getInstance());
	}

	private static void onGameShuttingDown(GameShuttingDownEvent event) {
		ClientLogic.shutdown();
	}
}
