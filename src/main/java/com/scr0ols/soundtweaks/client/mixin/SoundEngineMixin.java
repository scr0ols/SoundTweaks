package com.scr0ols.soundtweaks.client.mixin;

import com.scr0ols.soundtweaks.SoundDeduplicationConfig;
import com.scr0ols.soundtweaks.SoundRegistry;
import com.scr0ols.soundtweaks.SoundTweaks;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Map;

/**
 * Interceta SoundEngine para discovery de sons.
 * A modificação de volume é feita em AbstractSoundInstanceMixin (getVolume()).
 *
 * Confirmado em 26.1.2: todos os sons de entidade são SimpleSoundInstance e
 * passam por play() → getVolume(). Não usam queueTickingSound.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(
        method = "play",
        at = @At("HEAD"),
        cancellable = true
    )
    private void checkDuplication(SoundInstance sound, CallbackInfo ci) {
        SoundSource source = sound.getSource();
        if (source == SoundSource.MASTER || source == SoundSource.UI) return;

        SoundDeduplicationConfig cfg = SoundDeduplicationConfig.INSTANCE;
        int maxPos = cfg.getMaxPerPosition();
        int maxId  = cfg.getMaxPerSoundId();

        var id = sound.getIdentifier();
        if (id == null) return;
        BlockPos here = BlockPos.containing(sound.getX(), sound.getY(), sound.getZ());

        Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel =
                ((SoundEngineAccessor) (Object) this).getInstanceToChannel();

        var snapshot = new ArrayList<>(instanceToChannel.entrySet());

        int positionCount = 0;
        int identifierCount = 0;

        for (var entry : snapshot) {
            SoundInstance instance = entry.getKey();
            ChannelAccess.ChannelHandle channel = entry.getValue();
            if (channel.isStopped()) continue;
            if (!id.equals(instance.getIdentifier())) continue;

            identifierCount++;
            if (here.equals(BlockPos.containing(instance.getX(), instance.getY(), instance.getZ())))
                positionCount++;
        }

        if (positionCount >= maxPos) {
            SoundTweaks.LOGGER.debug("SoundTweaks: dedup cancelou '{}' em {} (pos={}/{})",
                    id.toString(), here, positionCount, maxPos);
            ci.cancel();
            return;
        }
        if (identifierCount >= maxId) {
            SoundTweaks.LOGGER.debug("SoundTweaks: dedup cancelou '{}' globalmente (id={}/{})",
                    id.toString(), identifierCount, maxId);
            ci.cancel();
        }
    }

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
