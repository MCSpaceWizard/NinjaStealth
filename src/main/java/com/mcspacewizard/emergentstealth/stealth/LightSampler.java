package com.mcspacewizard.emergentstealth.stealth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * How lit a point is, as a 0-1 visibility multiplier for perception. Stage 2 uses vanilla light levels as a
 * placeholder; Stage 3 swaps in the ray-traced exposure model behind this same interface.
 */
@FunctionalInterface
public interface LightSampler {
    /** Visibility multiplier at full darkness: you're hard to see, not invisible. */
    float DARKNESS_FLOOR = 0.15F;

    float lightFactor(ServerLevel level, Vec3 point);

    /** Placeholder: vanilla combined block/sky light (with time of day) mapped to 0.15-1. */
    LightSampler VANILLA = (level, point) -> {
        int light = level.getMaxLocalRawBrightness(BlockPos.containing(point));
        return DARKNESS_FLOOR + (1.0F - DARKNESS_FLOOR) * (light / 15.0F);
    };
}
