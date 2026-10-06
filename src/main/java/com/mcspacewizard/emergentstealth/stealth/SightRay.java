package com.mcspacewizard.emergentstealth.stealth;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
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
    public static float transmittance(BlockGetter level, Vec3 from, Vec3 to, boolean targetCrawling) {
        return transmittance(level, from, to, targetCrawling, null, null);
    }

    /**
     * @param skip a block to ignore (e.g. a light source's own block when tracing light from it), or null
     */
    public static float transmittance(BlockGetter level, Vec3 from, Vec3 to, boolean targetCrawling, @Nullable BlockPos skip) {
        return transmittance(level, from, to, targetCrawling, skip, null);
    }

    /**
     * Takes any {@link BlockGetter} so the client renderer can trace the same shadows as the server (doc 30).
     *
     * @param skipEnd a second block to ignore, e.g. the cell being lit when baking light for rendering, or null
     */
    public static float transmittance(BlockGetter level, Vec3 from, Vec3 to, boolean targetCrawling,
            @Nullable BlockPos skip, @Nullable BlockPos skipEnd) {
        Accumulator acc = new Accumulator();
        BlockGetter.traverseBlocks(from, to, acc, (a, pos) -> {
            if ((skip != null && pos.equals(skip)) || (skipEnd != null && pos.equals(skipEnd))) {
                return null;
            }
            a.transmittance *= blockTransmittance(level, pos, from, to, targetCrawling, a);
            return a.transmittance <= 0.01F ? Boolean.TRUE : null;
        }, a -> Boolean.FALSE);
        return acc.transmittance <= 0.01F ? 0.0F : acc.transmittance;
    }

    private static float blockTransmittance(BlockGetter level, BlockPos pos, Vec3 from, Vec3 to, boolean targetCrawling, Accumulator acc) {
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
