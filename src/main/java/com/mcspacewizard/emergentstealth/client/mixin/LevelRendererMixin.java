package com.mcspacewizard.emergentstealth.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mcspacewizard.emergentstealth.client.light.VisualLighting;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * With shadow-casting light, a block placed or broken can move a shadow up to 15 blocks away, beyond the
 * sections vanilla re-meshes. {@code blockChanged} sees every client-side block change (packets and
 * prediction); Sodium redirects the dirty-marking inside it but leaves the method itself alone.
 * <p>
 * Why a mixin: NeoForge's block events are server-side only; nothing fires for client block changes.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "blockChanged", at = @At("TAIL"))
    private void emergentstealth$onBlockChanged(BlockGetter level, BlockPos pos, BlockState oldState, BlockState newState,
            int updateFlags, CallbackInfo ci) {
        VisualLighting.onBlockChanged(pos, oldState, newState);
    }
}
