package com.scr0ols.soundtweaks.neoforge;

import com.scr0ols.soundtweaks.Platform;
import com.scr0ols.soundtweaks.SoundTweaks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

@Mod(SoundTweaks.MOD_ID)
public class SoundTweaksNeoForge {

	public SoundTweaksNeoForge(IEventBus modBus) {
		Platform.setConfigDir(FMLPaths.CONFIGDIR.get());

		String version = ModList.get()
				.getModContainerById(SoundTweaks.MOD_ID)
				.map(c -> c.getModInfo().getVersion().toString())
				.orElse("?");
		SoundTweaks.LOGGER.info("SoundTweaks {} loaded", version);

		if (FMLEnvironment.getDist() == Dist.CLIENT) {
			SoundTweaksNeoForgeClient.init(modBus);
		}
	}
}
