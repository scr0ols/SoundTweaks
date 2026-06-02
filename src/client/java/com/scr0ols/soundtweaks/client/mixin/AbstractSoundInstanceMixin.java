package com.scr0ols.soundtweaks.client.mixin;

import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Interceta getVolume() em AbstractSoundInstance (classe pai de SimpleSoundInstance,
 * EntityBoundSoundInstance, etc.) — afecta play() inicial, tick() de actualização,
 * e qualquer outro contexto que leia o volume.
 */
@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void modifyVolume(CallbackInfoReturnable<Float> cir) {
        SoundInstance self = (SoundInstance)(Object)this;
        var id = self.getIdentifier();
        if (id == null) return;
        String soundId = id.toString();
        SoundRegistry.addDiscovered(soundId);
        float mult = VolumeResolver.getEffectiveVolume(soundId);
        if (mult != 1.0f) cir.setReturnValue(cir.getReturnValue() * mult);
    }
}
