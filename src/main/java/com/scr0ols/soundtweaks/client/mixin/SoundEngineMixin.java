package com.scr0ols.soundtweaks.client.mixin;

import com.scr0ols.soundtweaks.SoundRegistry;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Interceta SoundEngine para discovery de sons.
 * A modificação de volume é feita em AbstractSoundInstanceMixin (getVolume()).
 *
 * Confirmado em 26.1.2: todos os sons de entidade são SimpleSoundInstance e
 * passam por play() → getVolume(). Não usam queueTickingSound.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /**
     * Redirect in play() — required so AbstractSoundInstanceMixin can apply
     * the volume multiplier. Discovery is done there, not here,
     * to avoid a double addDiscovered() call per sound played.
     */
    @Redirect(
        method = "play",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F")
    )
    private float redirectGetVolume(SoundInstance soundInstance) {
        return soundInstance.getVolume();
    }

    /**
     * Discovery of TickableSoundInstances via queueTickingSound.
     * In 26.1.2 entities do not use this path, but other sounds may.
     */
    @Inject(
        method = "queueTickingSound",
        at = @At("HEAD")
    )
    private void onQueueTickingSound(TickableSoundInstance ts, CallbackInfo ci) {
        var id = ts.getIdentifier();
        if (id != null) SoundRegistry.addDiscovered(id.toString());
    }
}
