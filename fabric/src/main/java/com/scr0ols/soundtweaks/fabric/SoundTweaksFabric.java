package com.scr0ols.soundtweaks.fabric;

import com.scr0ols.soundtweaks.Platform;
import com.scr0ols.soundtweaks.SoundTweaks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class SoundTweaksFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		Platform.setConfigDir(FabricLoader.getInstance().getConfigDir());

		String version = FabricLoader.getInstance()
				.getModContainer(SoundTweaks.MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
		SoundTweaks.LOGGER.info("SoundTweaks {} loaded", version);
	}
}
