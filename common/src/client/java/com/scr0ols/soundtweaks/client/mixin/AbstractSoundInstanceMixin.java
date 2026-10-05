package com.scr0ols.soundtweaks.client.mixin;

//import com.scr0ols.soundtweaks.PerfStats;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Interceta getVolume() em AbstractSoundInstance — cobre o path de play() inicial.
 * Em 26.1.2, sons de entidades são SimpleSoundInstance (não TickableSoundInstance)
 * e passam sempre por play() → este inject aplica-se a todos os sons.
 */
@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void modifyVolume(CallbackInfoReturnable<Float> cir) {
        //long t0 = System.nanoTime();
        SoundInstance self = (SoundInstance)(Object)this;
        var id = self.getIdentifier();
        if (id == null) return;
        String soundId = id.toString();
        SoundRegistry.addDiscovered(soundId);
        float mult = VolumeResolver.getEffectiveVolume(soundId);
        //boolean modified = false;
        if (mult != 1.0f) {
            Float original = cir.getReturnValue();
            if (original != null) { cir.setReturnValue(original * mult); /*modified = true;*/ }
        }
        //PerfStats.recordCall(System.nanoTime() - t0, modified);
    }
}
