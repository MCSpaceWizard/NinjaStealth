package com.mcspacewizard.emergentstealth.stealth;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walks the voxel grid between an observer's eye and a point and multiplies how much sight each block lets
 * through. 1 = clear view, 0 = fully blocked. Solid blocks block exactly where their collision shape is
 * (slabs, stairs and fences work); tags make glass, foliage and cover behave like the design says.
 */
public final class SightRay {
    private SightRay() {}

    /** Water along the ray beyond this many blocks blocks sight (P-12). */
    public static final int MAX_WATER_BLOCKS = 5;

    public static final float PARTIAL_COVER = 0.35F;
    public static final float LIGHT_COVER = 0.7F;

    private static final class Accumulator {
        float transmittance = 1.0F;
        int waterBlocks;
    }

    /**
     * @param targetCrawling whether the target is crawling, which makes concealing foliage block sight
     */
    public static float transmittance(Level level, Vec3 from, Vec3 to, boolean targetCrawling) {
        Accumulator acc = new Accumulator();
        BlockGetter.traverseBlocks(from, to, acc, (a, pos) -> {
            a.transmittance *= blockTransmittance(level, pos, from, to, targetCrawling, a);
            return a.transmittance <= 0.01F ? Boolean.TRUE : null;
        }, a -> Boolean.FALSE);
        return acc.transmittance <= 0.01F ? 0.0F : acc.transmittance;
    }

    private static float blockTransmittance(Level level, BlockPos pos, Vec3 from, Vec3 to, boolean targetCrawling, Accumulator acc) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER) && ++acc.waterBlocks > MAX_WATER_BLOCKS) {
            return 0.0F;
        }
        if (state.isAir()) {
            return 1.0F;
        }
        if (state.is(StealthTags.SEE_THROUGH)) {
            return 1.0F;
        }
        if (state.is(StealthTags.CONCEALING_FOLIAGE)) {
            return targetCrawling ? 0.0F : 1.0F;
        }
        if (state.is(StealthTags.PARTIAL_COVER)) {
            return PARTIAL_COVER;
        }
        if (state.is(StealthTags.LIGHT_COVER)) {
            return LIGHT_COVER;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return 1.0F;
        }
        return shape.clip(from, to, pos) != null ? 0.0F : 1.0F;
    }
}
