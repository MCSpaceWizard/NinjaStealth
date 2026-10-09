package com.mcspacewizard.emergentstealth.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Vanilla walking, plus (design doc 15 §1):
 * <ul>
 *   <li>Closed fence gates plan like closed wooden doors (passable for mobs that open doors).</li>
 *   <li>Climbable blocks (ladders, vines, scaffolding: {@code #minecraft:climbable}) add up/down steps,
 *       including stepping onto the top of a ladder from the floor above.</li>
 * </ul>
 */
public class StealthNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockState state = context.getBlockState(new BlockPos(x, y, z));
        // Locked doors and gates are walls for mobs without the key (design doc 21 §3).
        if (com.mcspacewizard.emergentstealth.world.lock.Locks.blocksMob(this.mob, new BlockPos(x, y, z), state)) {
            return PathType.BLOCKED;
        }
        if (this.canOpenDoors() && state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
            return PathType.DOOR_WOOD_CLOSED;
        }
        return super.getPathType(context, x, y, z);
    }

    @Override
    public int getNeighbors(Node[] neighbors, Node node) {
        int count = super.getNeighbors(neighbors, node);
        // Climb up: inside a climbable block (or standing at the foot of one), the block above is reachable.
        if (isClimbable(node.x, node.y, node.z) || isClimbable(node.x, node.y + 1, node.z)) {
            count = addClimbNode(neighbors, count, node.x, node.y + 1, node.z);
        }
        // Climb down: a climbable block below.
        if (isClimbable(node.x, node.y - 1, node.z)) {
            count = addClimbNode(neighbors, count, node.x, node.y - 1, node.z);
        }
        // Step from a floor onto the top of a ladder next to it (the ladder's head is open air).
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int nx = node.x + direction.getStepX();
            int nz = node.z + direction.getStepZ();
            if (isClimbable(nx, node.y - 1, nz) && isOpen(nx, node.y, nz) && isOpen(nx, node.y + 1, nz)) {
                count = addClimbNode(neighbors, count, nx, node.y, nz);
            }
        }
        return count;
    }

    private int addClimbNode(Node[] neighbors, int count, int x, int y, int z) {
        if (count >= neighbors.length || (!isClimbable(x, y, z) && !isOpen(x, y, z)) || !isOpen(x, y + 1, z) && !isClimbable(x, y + 1, z)) {
            return count;
        }
        Node climb = this.getNode(x, y, z);
        if (climb.closed) {
            return count;
        }
        climb.type = PathType.WALKABLE;
        climb.costMalus = Math.max(climb.costMalus, 0.5F);
        neighbors[count] = climb;
        return count + 1;
    }

    private boolean isClimbable(int x, int y, int z) {
        return this.currentContext.getBlockState(new BlockPos(x, y, z)).is(BlockTags.CLIMBABLE);
    }

    /** No collision (air, open doors, plants...). */
    private boolean isOpen(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return this.currentContext.getBlockState(pos).getCollisionShape(this.currentContext.level(), pos).isEmpty();
    }
}
