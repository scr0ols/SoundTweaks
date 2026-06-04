package com.scr0ols.soundtweaks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundTweaks implements ModInitializer {
	public static final String MOD_ID = "soundtweaks";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		String version = FabricLoader.getInstance()
				.getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
		LOGGER.info("SoundTweaks {} loaded", version);
	}
}