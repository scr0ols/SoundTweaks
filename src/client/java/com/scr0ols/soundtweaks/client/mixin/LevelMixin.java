package com.scr0ols.soundtweaks.client.mixin;

import com.scr0ols.soundtweaks.MissingBlockRegistry;
import com.scr0ols.soundtweaks.VolumeResolver;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class LevelMixin {

    // Previne loop infinito quando re-invocamos playLocalSound com volume modificado
    private static final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);

    // Em 26.1.2 não existe playLocalSound(BlockPos, ...) — o método usa coordenadas double.
    // Reconstruímos o BlockPos a partir de (x, y, z) para identificar o bloco.
    @Inject(
        method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlayLocalSound(double x, double y, double z,
                                   SoundEvent sound, SoundSource source,
                                   float volume, float pitch, boolean distanceDelay,
                                   CallbackInfo ci) {
        if (applying.get()) return; // chamada re-entrante — deixar passar sem modificar

        ClientLevel self = (ClientLevel)(Object)this;
        BlockPos pos = BlockPos.containing(x, y, z);
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(
                self.getBlockState(pos).getBlock()
        );
        if (blockId == null) return;

        String id = blockId.toString();
        if (!MissingBlockRegistry.contains(id)) return;

        // Blocos com GROUP_PREFIX (note_block, jukebox) têm o seu volume controlado
        // via cascade → SoundConfig → AbstractSoundInstanceMixin. Se os interceptarmos
        // aqui também, o multiplicador seria aplicado duas vezes. Deixamos passar.
        if (MissingBlockRegistry.GROUP_PREFIXES.containsKey(id)) return;

        float multiplier = VolumeResolver.getEffectiveBlockVolume(id);
        if (multiplier == 1.0f) return; // sem alteração, deixar passar

        ci.cancel(); // cancelar chamada original

        if (multiplier > 0.0f) {
            // Re-invocar com volume ajustado; o flag applying previne recursão
            applying.set(true);
            try {
                self.playLocalSound(x, y, z, sound, source, volume * multiplier, pitch, distanceDelay);
            } finally {
                applying.set(false);
            }
        }
        // se multiplier == 0.0f, som silenciado — cancelado acima, nada mais a fazer
    }
}
