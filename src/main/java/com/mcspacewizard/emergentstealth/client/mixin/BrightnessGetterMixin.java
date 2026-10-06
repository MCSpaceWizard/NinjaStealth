package com.mcspacewizard.emergentstealth.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mcspacewizard.emergentstealth.client.light.VisualLighting;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;

/**
 * Feeds shadowed + dynamic block light into everything that bakes light: vanilla chunk meshing, Sodium's
 * meshing ({@code LightDataAccess} calls {@code LevelRenderer.getLightCoords(BrightnessGetter.DEFAULT, ...)}),
 * block entities and particles. {@code lambda$static$0} is the body of {@code BrightnessGetter.DEFAULT}.
 * <p>
 * Why a mixin: NeoForge has no event or hook for the per-block light used while meshing (doc 30 §1).
 */
@Mixin(LevelRenderer.BrightnessGetter.class)
public interface BrightnessGetterMixin {
    @ModifyReturnValue(method = "lambda$static$0", at = @At("RETURN"))
    private static int emergentstealth$visualBlockLight(int packed, BlockAndLightGetter level, BlockPos pos) {
        return VisualLighting.adjustPackedLight(level, pos, packed);
    }
}
