package com.scr0ols.soundtweaks;

import com.scr0ols.soundtweaks.client.SoundTweaksClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(SoundTweaks.MOD_ID)
public class SoundTweaks {
	public static final String MOD_ID = "soundtweaks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public SoundTweaks(IEventBus modBus) {
		String version = ModList.get()
				.getModContainerById(MOD_ID)
				.map(c -> c.getModInfo().getVersion().toString())
				.orElse("?");
		LOGGER.info("SoundTweaks {} loaded", version);

		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			SoundTweaksClient.init(modBus);
		}
	}
}
