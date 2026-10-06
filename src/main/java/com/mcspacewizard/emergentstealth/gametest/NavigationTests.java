package com.mcspacewizard.emergentstealth.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mcspacewizard.emergentstealth.EmergentStealth;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoute;
import com.mcspacewizard.emergentstealth.ai.routine.PatrolRoutes;
import com.mcspacewizard.emergentstealth.ai.routine.Schedule;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.registry.ESBlocks;
import com.mcspacewizard.emergentstealth.registry.ESEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Navigation, patrol and routine GameTests (design doc 15 §5). NPCs run their real AI here. */
final class NavigationTests {
    private NavigationTests() {}

    /** Spawns a guard at {@code start} walking a route through the given relative waypoints, all day. */
    static StealthNpc patroller(GameTestHelper helper, BlockPos start, PatrolRoute.Mode mode, PatrolRoute.Waypoint... relativeWaypoints) {
        List<PatrolRoute.Waypoint> absolute = new ArrayList<>();
        for (PatrolRoute.Waypoint waypoint : relativeWaypoints) {
            absolute.add(new PatrolRoute.Waypoint(helper.absolutePos(waypoint.pos()), waypoint.waitTicks(), waypoint.lookYaw(), waypoint.relight()));
        }
        String name = "test_" + UUID.randomUUID();
        PatrolRoutes.get(helper.getLevel()).put(new PatrolRoute(name, absolute, mode));
        StealthNpc npc = helper.spawn(ESEntities.STEALTH_NPC.get(), Vec3.atBottomCenterOf(start));
        npc.setArchetype(EmergentStealth.id("ashigaru"), true);
        npc.setHome(helper.absolutePos(start), 0.0F);
        npc.setSchedule(new Schedule(List.of(new Schedule.Entry(0, 0, new Schedule.Route(name)))));
        return npc;
    }

    static String describe(GameTestHelper helper, StealthNpc npc) {
        // GameTestHelper.relativePos treats Rotation.NONE as 180 degrees in 26.1.2; subtract the origin directly.
        BlockPos rel = npc.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
        var path = npc.getNavigation().getPath();
        return " [rel pos " + rel.toShortString() + ", waypoint " + npc.getCurrentWaypointIndex() + ", state "
                + npc.stealthBrain().state() + ", path " + (path == null ? "none" : path.getNextNodeIndex() + "/" + path.getNodeCount()
                + (path.getNodeCount() > 0 ? " end " + path.getEndNode().asBlockPos().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " reaches=" + path.canReach() : "")) + "]";
    }

    static PatrolRoute.Waypoint at(int x, int y, int z) {
        return PatrolRoute.Waypoint.at(new BlockPos(x, y, z));
    }

    static void wallAcross(GameTestHelper helper, int z, int height) {
        for (int x = 0; x <= 8; x++) {
            for (int y = 1; y <= height; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    static void walksThroughDoorAndClosesIt(GameTestHelper helper) {
        wallAcross(helper, 10, 3);
        BlockPos door = new BlockPos(4, 1, 10);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        StealthNpc npc = patroller(helper, new BlockPos(4, 1, 4), PatrolRoute.Mode.PINGPONG, at(4, 1, 4), at(4, 1, 17));
        double goalZ = helper.absoluteVec(new Vec3(0, 0, 15)).z;
        helper.succeedWhen(() -> {
            helper.assertTrue(npc.getZ() > goalZ, "NPC should get through the door" + describe(helper, npc));
            BlockState state = helper.getBlockState(door);
            helper.assertTrue(state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN), "Door should be closed again");
        });
    }

    static void walksThroughFenceGate(GameTestHelper helper) {
        wallAcross(helper, 10, 3);
        BlockPos gate = new BlockPos(4, 1, 10);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
        helper.setBlock(gate.above(), Blocks.AIR);
        StealthNpc npc = patroller(helper, new BlockPos(4, 1, 4), PatrolRoute.Mode.PINGPONG, at(4, 1, 4), at(4, 1, 17));
        double goalZ = helper.absoluteVec(new Vec3(0, 0, 15)).z;
        helper.succeedWhen(() -> {
            helper.assertTrue(npc.getZ() > goalZ, "NPC should get through the gate" + describe(helper, npc));
            BlockState state = helper.getBlockState(gate);
            helper.assertTrue(state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN), "Gate should be closed again");
        });
    }

    static void climbsLadder(GameTestHelper helper) {
        // Raised platform from z=12 to z=20, top surface at y=4; ladder up its north face at z=11.
        for (int x = 0; x <= 8; x++) {
            for (int z = 12; z <= 20; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(4, y, 11), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        StealthNpc npc = patroller(helper, new BlockPos(4, 1, 5), PatrolRoute.Mode.PINGPONG, at(4, 1, 5), at(4, 4, 16));
        double goalY = helper.absoluteVec(new Vec3(0, 4, 0)).y;
        double goalZ = helper.absoluteVec(new Vec3(0, 0, 14)).z;
        helper.succeedWhen(() -> helper.assertTrue(npc.getY() >= goalY - 0.01 && npc.getZ() > goalZ,
                "NPC should climb the ladder onto the platform" + describe(helper, npc)));
    }

    static void patrolsInOrder(GameTestHelper helper) {
        StealthNpc npc = patroller(helper, new BlockPos(2, 1, 3), PatrolRoute.Mode.LOOP, at(2, 1, 3), at(6, 1, 3), at(6, 1, 11));
        List<Integer> seen = new ArrayList<>();
        helper.onEachTick(() -> {
            int index = npc.getCurrentWaypointIndex();
            if (index >= 0 && (seen.isEmpty() || seen.getLast() != index)) {
                seen.add(index);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(seen.size() >= 4 && seen.subList(0, 4).equals(List.of(0, 1, 2, 0)),
                "Waypoint order should be 0,1,2,0 but was " + seen));
    }

    static void lamplighterRelights(GameTestHelper helper) {
        BlockPos torch = new BlockPos(5, 1, 9);
        helper.setBlock(torch, ESBlocks.UNLIT_TORCH.get());
        StealthNpc npc = patroller(helper, new BlockPos(4, 1, 3), PatrolRoute.Mode.PINGPONG,
                at(4, 1, 3), new PatrolRoute.Waypoint(new BlockPos(4, 1, 9), 20, Optional.empty(), true));
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(torch).is(Blocks.TORCH), "Torch not relit" + describe(helper, npc)));
    }

    static void scheduleSelection(GameTestHelper helper) {
        Schedule schedule = new Schedule(List.of(
                new Schedule.Entry(6, 18, new Schedule.Route("day")),
                new Schedule.Entry(18, 6, new Schedule.Route("night"))));
        helper.assertTrue(schedule.activeAt(12).orElseThrow().equals(new Schedule.Route("day")), "Noon should be the day route");
        helper.assertTrue(schedule.activeAt(20).orElseThrow().equals(new Schedule.Route("night")), "20:00 should be the night route");
        helper.assertTrue(schedule.activeAt(3).orElseThrow().equals(new Schedule.Route("night")), "03:00 wraps past midnight");
        helper.assertTrue(Schedule.hourOf(0) == 6 && Schedule.hourOf(18000) == 0, "Day time 0 is 06:00, 18000 is midnight");
        helper.succeed();
    }
}
