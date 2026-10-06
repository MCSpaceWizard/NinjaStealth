package com.mcspacewizard.emergentstealth.ai.nav;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Opens wooden doors and fence gates on the NPC's path just before reaching them, and closes them again
 * once it has walked through (guards leave compounds as they found them). Uses no control flags, so it runs
 * alongside movement goals.
 */
public class PassageGoal extends Goal {
    private static final double OPEN_DISTANCE_SQ = 2.0 * 2.0;

    private final Mob mob;
    private @Nullable BlockPos passage;
    private float approachX;
    private float approachZ;
    private int forgetTicks;

    public PassageGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int end = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < end; i++) {
            Node node = path.getNode(i);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(node.x, node.y + dy, node.z);
                if (isClosedPassage(mob.level().getBlockState(pos))
                        && mob.distanceToSqr(pos.getX() + 0.5, mob.getY(), pos.getZ() + 0.5) <= OPEN_DISTANCE_SQ) {
                    passage = pos;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        if (passage == null) {
            return;
        }
        approachX = (float) (passage.getX() + 0.5 - mob.getX());
        approachZ = (float) (passage.getZ() + 0.5 - mob.getZ());
        forgetTicks = 60;
        setOpen(mob.level(), passage, true);
    }

    @Override
    public boolean canContinueToUse() {
        return passage != null && forgetTicks > 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        forgetTicks--;
        if (passage == null) {
            return;
        }
        float dx = (float) (passage.getX() + 0.5 - mob.getX());
        float dz = (float) (passage.getZ() + 0.5 - mob.getZ());
        boolean passed = approachX * dx + approachZ * dz < 0.0F && mob.distanceToSqr(passage.getX() + 0.5, mob.getY(), passage.getZ() + 0.5) > 1.2;
        if (passed) {
            forgetTicks = 0;
        }
    }

    @Override
    public void stop() {
        if (passage != null) {
            setOpen(mob.level(), passage, false);
        }
        passage = null;
    }

    static boolean isClosedPassage(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return DoorBlock.isWoodenDoor(state) && !state.getValue(DoorBlock.OPEN);
        }
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }

    private void setOpen(Level level, BlockPos pos, boolean open) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door && DoorBlock.isWoodenDoor(state)) {
            door.setOpen(mob, level, state, pos, open);
        } else if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) != open) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(mob, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }
}
