package com.scr0ols.soundtweaks.client.mixin;

import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.SoundTweaks;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    // Guarda o multiplicador entre o HEAD e a chamada a calculateVolume
    // É um campo da instância (o Mixin adiciona-o ao SoundEngine)
    private float pendingMultiplier = 1.0f;

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance soundInstance, CallbackInfoReturnable<?> ci) {
        var identifier = soundInstance.getIdentifier();
        if (identifier == null) return;
        String id = identifier.toString();
        float configVolume = VolumeResolver.getEffectiveVolume(id);

        SoundRegistry.addDiscovered(id);
        if (configVolume != 1.0f)
            SoundTweaks.LOGGER.info("SoundTweaks [Mixin]: {} → volume={}", id, configVolume);

        if (configVolume == 0.0f) {
            this.pendingMultiplier = 1.0f;
            ci.cancel(); // som completamente silenciado
        } else {
            this.pendingMultiplier = configVolume; // guarda para o ModifyArg
        }
    }

    // Interceta o primeiro argumento da chamada a calculateVolume dentro do play()
    // e multiplica pelo nosso valor antes de o jogo calcular o volume final
    @ModifyArg(
        method = "play",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"),
        index = 0  // index 0 = o argumento "float volume"
    )
    private float modifyInstanceVolume(float originalVolume) {
        float result = originalVolume * this.pendingMultiplier;
        this.pendingMultiplier = 1.0f; // reset para o próximo som
        return result;
    }
}
