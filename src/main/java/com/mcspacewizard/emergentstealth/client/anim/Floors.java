package com.mcspacewizard.emergentstealth.client.anim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Floor heights for fitting bodies and verlet points to the ground. Sim-side only (client tick). */
public final class Floors {
    private Floors() {}

    /** How far below the query point to look, blocks. */
    private static final int DEPTH = 3;

    /**
     * Top of the highest collision surface at or below {@code y + 0.25} within {@link #DEPTH} blocks, or
     * {@code Double.NEGATIVE_INFINITY} if there is none (a drop).
     */
    public static double below(BlockGetter level, double x, double y, double z) {
        double limit = y + 0.25;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(limit);
        for (int by = top; by >= top - DEPTH; by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double surface = by + shape.max(Direction.Axis.Y);
            if (surface <= limit + 1.0E-6) {
                return surface;
            }
            // The point is inside this block's shape (e.g. a slab top above it): its surface is the floor.
            return Math.min(surface, limit);
        }
        return Double.NEGATIVE_INFINITY;
    }
}
