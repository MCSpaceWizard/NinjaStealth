package com.mcspacewizard.emergentstealth.tool;

import com.mcspacewizard.emergentstealth.entity.ThrownItem;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Caltrops (design doc 21 §2): scatter a 2×2 patch where they land, for 60 s. See
 * {@link com.mcspacewizard.emergentstealth.block.CaltropsBlock} for what they do.
 */
public class CaltropsItem extends ThrowableToolItem {
    public CaltropsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onImpact(ServerLevel level, ThrownItem thrown, Vec3 at, HitResult hit) {
        int placed = scatter(level, at);
        if (placed == 0) {
            // Nowhere to lie (deep water, a ledge...): the bag drops and can be picked up again.
            ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, thrown.getItem().copy());
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
    }

    /**
     * Places up to four caltrops blocks in the 2×2 patch nearest to {@code at}, each on the nearest floor within a
     * block up or down. Returns how many were placed.
     */
    public static int scatter(ServerLevel level, Vec3 at) {
        BlockPos base = floorCell(level, BlockPos.containing(at));
        if (base == null) {
            return 0;
        }
        int dx = at.x - Mth.floor(at.x) >= 0.5 ? 1 : -1;
        int dz = at.z - Mth.floor(at.z) >= 0.5 ? 1 : -1;
        BlockState caltrops = ESBlocks.CALTROPS.get().defaultBlockState();
        int placed = 0;
        int[][] offsets = {{0, 0}, {dx, 0}, {0, dz}, {dx, dz}};
        for (int[] offset : offsets) {
            BlockPos cell = base.offset(offset[0], 0, offset[1]);
            for (int dy : new int[] {0, 1, -1}) {
                BlockPos pos = cell.above(dy);
                BlockState there = level.getBlockState(pos);
                if (there.is(caltrops.getBlock())) {
                    break;
                }
                if (there.canBeReplaced() && there.getFluidState().isEmpty() && caltrops.canSurvive(level, pos)) {
                    level.setBlock(pos, caltrops, 3);
                    placed++;
                    break;
                }
            }
        }
        if (placed > 0) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.0F, 1.4F);
        }
        return placed;
    }

    /** The open cell resting on a floor at or just below {@code start}, or null. */
    private static BlockPos floorCell(ServerLevel level, BlockPos start) {
        BlockState caltrops = ESBlocks.CALTROPS.get().defaultBlockState();
        for (int dy = 0; dy >= -3; dy--) {
            BlockPos pos = start.above(dy);
            BlockState there = level.getBlockState(pos);
            if (!there.canBeReplaced() && !there.is(caltrops.getBlock())) {
                BlockPos above = pos.above();
                return caltrops.canSurvive(level, above) && level.getBlockState(above).canBeReplaced() ? above : null;
            }
            if (caltrops.canSurvive(level, pos)) {
                return pos;
            }
        }
        return null;
    }
}
