package com.mcspacewizard.emergentstealth.ai.routine;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.mcspacewizard.emergentstealth.ai.brain.AlertState;
import com.mcspacewizard.emergentstealth.entity.StealthNpc;
import com.mcspacewizard.emergentstealth.stealth.light.Snuffing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Calm-time behaviour (design doc 15): walk the scheduled patrol route, hold a post, or wander near home.
 * Runs only while the NPC is unaware or on heightened alert; the alert brain takes over otherwise and the
 * routine resumes at the nearest waypoint afterwards.
 */
public class RoutineGoal extends Goal {
    private static final double PATROL_SPEED = 0.6;
    private static final double HEIGHTENED_SPEED = 0.7;
    private static final double ARRIVE_DISTANCE_SQ = 1.5 * 1.5;
    /** When the path has finished, this close counts as arrived (the last node may be next to the target). */
    private static final double ARRIVE_DONE_DISTANCE_SQ = 2.5 * 2.5;
    private static final int MIN_WAIT = 10;
    /** Ticks without getting closer before a waypoint is considered unreachable and skipped. */
    private static final int STUCK_TICKS = 160;
    private static final int RELIGHT_RADIUS = 4;

    private final StealthNpc npc;
    private Schedule.@Nullable Activity activity;
    private @Nullable PatrolRoute route;
    private int index = -1;
    private int direction = 1;
    private long waitUntil = -1;
    private boolean arrived;
    private int stuckTicks;
    private double bestDistanceSq = Double.MAX_VALUE;
    private float glanceYaw;
    private int glanceTicks;
    private long nextWanderTick;

    public RoutineGoal(StealthNpc npc) {
        this.npc = npc;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** For tests and the debug view: the waypoint index being walked to (or waited at). */
    public int currentIndex() {
        return index;
    }

    @Override
    public boolean canUse() {
        AlertState state = npc.stealthBrain().state();
        return !state.isActive() && npc.level() instanceof ServerLevel && currentActivity() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        index = -1;
        arrived = false;
        waitUntil = -1;
        stuckTicks = 0;
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }

    private Schedule.@Nullable Activity currentActivity() {
        return npc.activeActivity();
    }

    @Override
    public void tick() {
        ServerLevel level = (ServerLevel) npc.level();
        Schedule.Activity now = currentActivity();
        if (now == null) {
            return;
        }
        if (!now.equals(activity)) {
            activity = now;
            route = null;
            index = -1;
            arrived = false;
        }
        double speed = npc.stealthBrain().state() == AlertState.HEIGHTENED ? HEIGHTENED_SPEED : PATROL_SPEED;
        switch (now) {
            case Schedule.Route r -> tickRoute(level, r.route(), speed);
            case Schedule.Post p -> tickPost(p, speed);
            case Schedule.Wander w -> tickWander(level, w.radius(), speed);
        }
        npc.setCurrentWaypointIndex(index);
    }

    // ------------------------------------------------------------------------------------------------
    // Route

    private void tickRoute(ServerLevel level, String name, double speed) {
        route = PatrolRoutes.get(level).get(name).orElse(null);
        if (route == null || route.waypoints().isEmpty()) {
            glance();
            return;
        }
        if (index < 0 || index >= route.waypoints().size()) {
            index = nearestWaypoint(route);
            arrived = false;
            bestDistanceSq = Double.MAX_VALUE;
        }
        PatrolRoute.Waypoint waypoint = route.waypoints().get(index);
        Vec3 target = Vec3.atBottomCenterOf(waypoint.pos());

        if (!arrived) {
            double distanceSq = horizontalDistanceSq(target);
            boolean close = distanceSq <= ARRIVE_DISTANCE_SQ
                    || (npc.getNavigation().isDone() && distanceSq <= ARRIVE_DONE_DISTANCE_SQ);
            if (close && Math.abs(npc.getY() - target.y) < 1.5) {
                arrived = true;
                stuckTicks = 0;
                npc.getNavigation().stop();
                waitUntil = level.getGameTime() + Math.max(MIN_WAIT, waypoint.waitTicks());
                if (waypoint.relight()) {
                    relightAround(level, waypoint.pos());
                }
            } else {
                moveTo(target, speed);
                return;
            }
        }

        // Waiting at the waypoint.
        if (waypoint.lookYaw().isPresent()) {
            lookAtYaw(waypoint.lookYaw().get());
        } else {
            glance();
        }
        if (level.getGameTime() >= waitUntil) {
            int next = route.next(index, direction);
            if (route.mode() == PatrolRoute.Mode.PINGPONG && (next - index) != direction) {
                direction = -direction;
            }
            index = next;
            arrived = false;
            stuckTicks = 0;
            bestDistanceSq = Double.MAX_VALUE;
        }
    }

    private int nearestWaypoint(PatrolRoute route) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < route.waypoints().size(); i++) {
            double d = npc.position().distanceToSqr(Vec3.atBottomCenterOf(route.waypoints().get(i).pos()));
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private void relightAround(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RELIGHT_RADIUS, -1, -RELIGHT_RADIUS), center.offset(RELIGHT_RADIUS, 3, RELIGHT_RADIUS))) {
            if (Snuffing.canRelight(level.getBlockState(pos))) {
                Snuffing.relight(level, pos.immutable(), null);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Post & wander

    private void tickPost(Schedule.Post post, double speed) {
        Vec3 target = Vec3.atBottomCenterOf(post.pos());
        if (horizontalDistanceSq(target) > ARRIVE_DISTANCE_SQ) {
            moveTo(target, speed);
            return;
        }
        npc.getNavigation().stop();
        if (glanceTicks > 0) {
            glanceTicks--;
            lookAtYaw(post.yaw() + glanceYaw);
        } else {
            lookAtYaw(post.yaw());
            if (npc.getRandom().nextInt(120) == 0) {
                glanceYaw = Mth.nextFloat(npc.getRandom(), -70.0F, 70.0F);
                glanceTicks = 30 + npc.getRandom().nextInt(40);
            }
        }
    }

    private void tickWander(ServerLevel level, int radius, double speed) {
        if (!npc.getNavigation().isDone()) {
            return;
        }
        glance();
        if (level.getGameTime() < nextWanderTick) {
            return;
        }
        nextWanderTick = level.getGameTime() + 100 + npc.getRandom().nextInt(140);
        BlockPos home = npc.getHome();
        double angle = npc.getRandom().nextDouble() * Math.PI * 2.0;
        double distance = npc.getRandom().nextDouble() * radius;
        npc.getNavigation().moveTo(home.getX() + 0.5 + Math.cos(angle) * distance, home.getY(),
                home.getZ() + 0.5 + Math.sin(angle) * distance, speed);
    }

    // ------------------------------------------------------------------------------------------------
    // Helpers

    private void moveTo(Vec3 target, double speed) {
        if (npc.getNavigation().isDone()) {
            // Exact accuracy: vanilla's default stops up to a block short of the target.
            Path path = npc.getNavigation().createPath(BlockPos.containing(target), 0);
            if (path == null || !npc.getNavigation().moveTo(path, speed)) {
                stuckTicks += 20;
            }
        }
        double distanceSq = npc.position().distanceToSqr(target);
        if (distanceSq < bestDistanceSq - 0.25) {
            bestDistanceSq = distanceSq;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
        if (stuckTicks > STUCK_TICKS && route != null) {
            // Unreachable waypoint: skip it rather than standing there forever.
            index = route.next(index, direction);
            arrived = false;
            stuckTicks = 0;
            bestDistanceSq = Double.MAX_VALUE;
            npc.getNavigation().stop();
        }
    }

    private double horizontalDistanceSq(Vec3 target) {
        double dx = npc.getX() - target.x;
        double dz = npc.getZ() - target.z;
        return dx * dx + dz * dz;
    }

    private void lookAtYaw(float yaw) {
        double rad = Math.toRadians(yaw);
        Vec3 eye = npc.getEyePosition();
        npc.getLookControl().setLookAt(eye.x - Math.sin(rad) * 5.0, eye.y, eye.z + Math.cos(rad) * 5.0);
    }

    private void glance() {
        if (glanceTicks > 0) {
            glanceTicks--;
            lookAtYaw(glanceYaw);
        } else if (npc.getRandom().nextInt(60) == 0) {
            glanceYaw = npc.getYRot() + Mth.nextFloat(npc.getRandom(), -90.0F, 90.0F);
            glanceTicks = 25 + npc.getRandom().nextInt(40);
        }
    }
}
