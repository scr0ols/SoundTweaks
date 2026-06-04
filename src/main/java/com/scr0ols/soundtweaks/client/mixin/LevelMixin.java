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

    // Prevents infinite loop when re-invoking playLocalSound with a modified volume.
    // static final is safe here: the field lives for the entire game session and is
    // always cleaned up by the try/finally in onPlayLocalSound — no leak risk in prod.
    private static final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);

    // In 26.1.2 there is no playLocalSound(BlockPos, ...) — the method uses double coordinates.
    // We reconstruct the BlockPos from (x, y, z) to identify the block.
    @Inject(
        method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onPlayLocalSound(double x, double y, double z,
                                   SoundEvent sound, SoundSource source,
                                   float volume, float pitch, boolean distanceDelay,
                                   CallbackInfo ci) {
        if (applying.get()) return; // re-entrant call — let it pass unmodified

        ClientLevel self = (ClientLevel)(Object)this;
        BlockPos pos = BlockPos.containing(x, y, z);
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(
                self.getBlockState(pos).getBlock()
        );
        if (blockId == null) return;

        String id = blockId.toString();
        if (!MissingBlockRegistry.contains(id)) return;

        // Blocks with GROUP_PREFIX (note_block, jukebox) have their volume controlled
        // via cascade → SoundConfig → AbstractSoundInstanceMixin. Intercepting them
        // here as well would apply the multiplier twice. Let them pass.
        if (MissingBlockRegistry.GROUP_PREFIXES.containsKey(id)) return;

        float multiplier = VolumeResolver.getEffectiveBlockVolume(id);
        if (multiplier == 1.0f) return; // no change, let through

        ci.cancel(); // cancelar chamada original

        if (multiplier > 0.0f) {
            // Re-invoke with adjusted volume; the applying flag prevents recursion
            applying.set(true);
            try {
                self.playLocalSound(x, y, z, sound, source, volume * multiplier, pitch, distanceDelay);
            } finally {
                applying.set(false);
            }
        }
        // if multiplier == 0.0f, sound is muted — already cancelled above, nothing more to do
    }
}
