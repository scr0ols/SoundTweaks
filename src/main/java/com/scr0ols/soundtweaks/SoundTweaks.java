package com.scr0ols.soundtweaks;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoundTweaks implements ModInitializer {
	public static final String MOD_ID = "soundtweaks";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("SoundTweaks {} loaded", "1.0.0");
	}
}