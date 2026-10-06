package com.mcspacewizard.emergentstealth.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mcspacewizard.emergentstealth.client.light.VisualLighting;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Lights entities (and the first-person hand) with the same shadowed + dynamic block light as the terrain:
 * {@code getBlockLightLevel} reads {@code level.getBrightness} directly, bypassing the brightness getter.
 * <p>
 * Why a mixin: NeoForge has no event for an entity's light probe (doc 30 §1).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @ModifyReturnValue(method = "getBlockLightLevel", at = @At("RETURN"))
    private int emergentstealth$visualBlockLight(int light, T entity, BlockPos pos) {
        return VisualLighting.entityBlockLight(entity, pos, light);
    }

    /** Same for sky light, so an entity standing in a sun or moon shadow is shaded like the ground (doc 30 §10). */
    @ModifyReturnValue(method = "getSkyLightLevel", at = @At("RETURN"))
    private int emergentstealth$visualSkyLight(int light, T entity, BlockPos pos) {
        return VisualLighting.entitySkyLight(entity, pos, light);
    }
}
