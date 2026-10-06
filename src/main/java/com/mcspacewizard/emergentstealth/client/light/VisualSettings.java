package com.mcspacewizard.emergentstealth.client.light;

import com.mcspacewizard.emergentstealth.config.ESConfig;

/**
 * Immutable snapshot of the visual-lighting client config (doc 30). Taken once per client tick on the main
 * thread and published through a volatile field, so chunk-meshing worker threads read consistent values
 * without touching the config system.
 */
record VisualSettings(boolean dynamicLights, int dynamicLightRange, int dynamicLightInterval, boolean itemEntityLights,
        boolean shadowedBlockLight, float shadowBounce,
        boolean darkIsDark, float darkMaxBrightness, float darkAmbientScale, boolean darkIsDarkWithShaders) {

    static final VisualSettings OFF = new VisualSettings(false, 48, 2, false, false, 0.15F, false, 0.0F, 0.35F, false);

    static VisualSettings read() {
        if (!ESConfig.CLIENT_SPEC.isLoaded()) {
            return OFF;
        }
        return new VisualSettings(
                ESConfig.DYNAMIC_LIGHTS.get(),
                ESConfig.DYNAMIC_LIGHT_RANGE.get(),
                ESConfig.DYNAMIC_LIGHT_INTERVAL.get(),
                ESConfig.ITEM_ENTITY_LIGHTS.get(),
                ESConfig.SHADOWED_BLOCK_LIGHT.get(),
                ESConfig.SHADOW_BOUNCE.get().floatValue(),
                ESConfig.DARK_IS_DARK.get(),
                ESConfig.DARK_MAX_BRIGHTNESS.get().floatValue(),
                ESConfig.DARK_AMBIENT_SCALE.get().floatValue(),
                ESConfig.DARK_IS_DARK_WITH_SHADERS.get());
    }

    /** Whether anything changes the light baked into terrain. */
    boolean altersTerrain() {
        return dynamicLights || shadowedBlockLight;
    }

    /** Whether switching from {@code other} to this changes baked terrain light (so all chunks must be rebuilt). */
    boolean terrainDiffers(VisualSettings other) {
        return dynamicLights != other.dynamicLights || itemEntityLights != other.itemEntityLights
                || shadowedBlockLight != other.shadowedBlockLight || shadowBounce != other.shadowBounce;
    }
}
