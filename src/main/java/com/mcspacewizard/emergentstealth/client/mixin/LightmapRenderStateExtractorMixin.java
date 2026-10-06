package com.mcspacewizard.emergentstealth.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mcspacewizard.emergentstealth.client.light.DarkIsDark;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

/**
 * "Dark is dark" (L-07 b): adjusts the lightmap inputs (brightness slider, ambient floor) right after vanilla
 * computes them.
 * <p>
 * Why a mixin: NeoForge has no event around lightmap computation; the brightness option is read here directly.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void emergentstealth$darkIsDark(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        DarkIsDark.apply(renderState);
    }
}
