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
     * Interceta getVolume() em play() para discovery.
     * A modificação de volume já é aplicada por AbstractSoundInstanceMixin
     * quando este método chama soundInstance.getVolume() internamente.
     */
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

    /**
     * Discovery de TickableSoundInstances via queueTickingSound.
     * Em 26.1.2 entidades não usam este path, mas outros sons podem usar.
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
