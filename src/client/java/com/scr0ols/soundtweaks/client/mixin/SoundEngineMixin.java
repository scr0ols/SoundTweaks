package com.scr0ols.soundtweaks.client.mixin;

import com.scr0ols.soundtweaks.SoundRegistry;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Interceta play() para garantir que sons novos são registados no SoundRegistry.
 * A lógica de volume é tratada em AbstractSoundInstanceMixin (inject no getVolume()).
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Redirect(
        method = "play",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F")
    )
    private float redirectGetVolume(SoundInstance soundInstance) {
        var id = soundInstance.getIdentifier();
        if (id != null) SoundRegistry.addDiscovered(id.toString());
        return soundInstance.getVolume();
    }
}
